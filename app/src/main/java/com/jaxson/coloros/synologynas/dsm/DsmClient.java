package com.jaxson.coloros.synologynas.dsm;

import android.util.Log;

import com.jaxson.coloros.synologynas.SynologyConfig;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class DsmClient implements DsmGateway {
    public static final String SESSION_NAME = "ColorOSSynologyNAS";
    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 60_000;
    private static final int LIST_LIMIT = 1_000;
    private static final int DELETE_API_VERSION = 2;
    private static final long DELETE_POLL_INTERVAL_MS = 250L;
    private static final long DELETE_TASK_TIMEOUT_MS = 60_000L;
    private static final int SMALL_THUMBNAIL_MAX_DIMENSION = 512;
    private static final int LARGE_THUMBNAIL_MAX_DIMENSION = 1_280;
    private static final String TAG = "ColorOSSynologyNAS";
    private static final Set<String> IMAGE_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp", "avif", "dng"
    );
    private static final Set<String> LOCAL_THUMBNAIL_EXTENSIONS = Set.of(
            "webp", "heic", "heif", "avif"
    );

    private final SynologyConfig config;

    public DsmClient(SynologyConfig config) {
        this.config = config;
    }

    public String testConnection() throws IOException {
        DsmApiCatalog catalog = discoverApis();
        String sid = login(catalog);
        Throwable primaryFailure = null;
        try {
            catalog.require("SYNO.FileStation.Download");
            catalog.require("SYNO.FileStation.Thumb");
            requireDeleteApi(catalog);
            String deviceModel = getDeviceModel(catalog, sid);
            DsmApiInfo listApi = catalog.require("SYNO.FileStation.List");
            requestFolderPage(listApi, sid, config.remoteRoot(), 0, 1);
            return deviceModel;
        } catch (IOException | RuntimeException | Error error) {
            primaryFailure = error;
            throw error;
        } finally {
            try {
                logout(catalog, sid);
            } catch (IOException logoutError) {
                if (primaryFailure != null) {
                    primaryFailure.addSuppressed(logoutError);
                } else {
                    throw logoutError;
                }
            }
        }
    }

    @Override
    public DsmApiCatalog discoverApis() throws IOException {
        Map<String, String> parameters = parameters(
                "api", "SYNO.API.Info",
                "version", "1",
                "method", "query",
                "query", "SYNO.API.Auth,SYNO.FileStation.List,"
                        + "SYNO.FileStation.Download,SYNO.FileStation.Thumb,"
                        + "SYNO.FileStation.Delete,SYNO.Core.System"
        );
        String url = DsmUrlBuilder.build(config.serverUrl(), "query.cgi", parameters);
        return DsmApiInfoParser.parse(executeJson("GET", url, null).toString());
    }

    @Override
    public String login(DsmApiCatalog catalog) throws IOException {
        DsmApiInfo auth = catalog.require("SYNO.API.Auth");
        Map<String, String> parameters = parameters(
                "api", auth.name(),
                "version", Integer.toString(auth.maxVersion()),
                "method", "login",
                "account", config.username(),
                "passwd", config.password(),
                "session", SESSION_NAME,
                "format", "sid"
        );
        if (!config.otp().isEmpty()) {
            parameters.put("otp_code", config.otp());
        }
        String url = DsmUrlBuilder.build(config.serverUrl(), auth.path(), Map.of());
        JSONObject response = executeJson(
                "POST",
                url,
                DsmUrlBuilder.encodeParameters(parameters)
        );
        requireSuccess(auth.name(), response);
        try {
            String sid = response.getJSONObject("data").getString("sid");
            if (sid.isBlank()) {
                throw new DsmException("DSM 登录成功响应缺少 SID");
            }
            return sid;
        } catch (JSONException error) {
            throw new DsmException("DSM 登录响应格式错误", error);
        }
    }

    @Override
    public String getDeviceModel(DsmApiCatalog catalog, String sid) throws IOException {
        DsmApiInfo systemApi = catalog.require("SYNO.Core.System");
        Map<String, String> parameters = parameters(
                "api", systemApi.name(),
                "version", Integer.toString(systemApi.maxVersion()),
                "method", "info",
                "_sid", sid
        );
        String url = DsmUrlBuilder.build(config.serverUrl(), systemApi.path(), parameters);
        JSONObject response = executeJson("GET", url, null);
        requireSuccess(systemApi.name(), response);
        return parseDeviceModel(response);
    }

    static String parseDeviceModel(JSONObject response) throws DsmException {
        requireSuccess("SYNO.Core.System", response);
        JSONObject data = response.optJSONObject("data");
        String model = data == null ? "" : data.optString("model", "").trim();
        if (model.isEmpty()) {
            throw new DsmException("SYNO.Core.System 响应缺少 NAS 型号");
        }
        return model;
    }

    public void logout(DsmApiCatalog catalog, String sid) throws IOException {
        DsmApiInfo auth = catalog.require("SYNO.API.Auth");
        Map<String, String> parameters = parameters(
                "api", auth.name(),
                "version", Integer.toString(auth.maxVersion()),
                "method", "logout",
                "session", SESSION_NAME,
                "_sid", sid
        );
        String url = DsmUrlBuilder.build(config.serverUrl(), auth.path(), Map.of());
        JSONObject response = executeJson(
                "POST",
                url,
                DsmUrlBuilder.encodeParameters(parameters)
        );
        requireSuccess(auth.name(), response);
    }

    @Override
    public List<RemoteMedia> listImages(DsmApiCatalog catalog, String sid) throws IOException {
        DsmApiInfo listApi = catalog.require("SYNO.FileStation.List");
        ArrayDeque<String> folders = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        List<RemoteMedia> media = new ArrayList<>();
        folders.add(config.remoteRoot());

        while (!folders.isEmpty()) {
            String folder = folders.removeFirst();
            if (!visited.add(folder)) {
                continue;
            }
            listFolder(listApi, sid, folder, folders, media);
        }
        return media;
    }

    @Override
    public void download(
            DsmApiCatalog catalog,
            String sid,
            RemoteMedia media,
            OutputStream output
    ) throws IOException {
        DsmApiInfo downloadApi = catalog.require("SYNO.FileStation.Download");
        Map<String, String> parameters = parameters(
                "api", downloadApi.name(),
                "version", Integer.toString(downloadApi.maxVersion()),
                "method", "download",
                "path", media.remotePath(),
                "mode", "download",
                "_sid", sid
        );
        String url = DsmUrlBuilder.build(config.serverUrl(), downloadApi.path(), parameters);
        streamFileResponse(downloadApi.name(), url, output);
    }

    @Override
    public void downloadThumbnail(
            DsmApiCatalog catalog,
            String sid,
            RemoteMedia media,
            String size,
            OutputStream output
    ) throws IOException {
        if (!"small".equals(size) && !"large".equals(size)) {
            throw new IllegalArgumentException("不支持的缩略图尺寸: " + size);
        }
        if (requiresLocalThumbnail(media)) {
            generateLocalThumbnail(catalog, sid, media, size, output);
            return;
        }
        DsmApiInfo thumbnailApi = catalog.require("SYNO.FileStation.Thumb");
        Map<String, String> parameters = parameters(
                "api", thumbnailApi.name(),
                "version", Integer.toString(thumbnailApi.maxVersion()),
                "method", "get",
                "path", media.remotePath(),
                "size", size,
                "_sid", sid
        );
        String url = DsmUrlBuilder.build(config.serverUrl(), thumbnailApi.path(), parameters);
        streamFileResponse(thumbnailApi.name(), url, output);
    }

    static boolean requiresLocalThumbnail(RemoteMedia media) {
        return LOCAL_THUMBNAIL_EXTENSIONS.contains(extensionOf(media.name()));
    }

    static int localThumbnailMaxDimension(String size) {
        return "large".equals(size)
                ? LARGE_THUMBNAIL_MAX_DIMENSION
                : SMALL_THUMBNAIL_MAX_DIMENSION;
    }

    private void generateLocalThumbnail(
            DsmApiCatalog catalog,
            String sid,
            RemoteMedia media,
            String size,
            OutputStream output
    ) throws IOException {
        String extension = extensionOf(media.name());
        File source = File.createTempFile("synology-thumbnail-", ".source");
        try {
            Log.i(
                    TAG,
                    "DSM generate local thumbnail: extension=" + extension + ", size=" + size
            );
            try (OutputStream fileOutput = new BufferedOutputStream(
                    new FileOutputStream(source)
            )) {
                download(catalog, sid, media, fileOutput);
            }
            LocalThumbnailGenerator.generate(
                    source,
                    localThumbnailMaxDimension(size),
                    output
            );
        } catch (IOException | RuntimeException error) {
            Log.e(
                    TAG,
                    "DSM local thumbnail failed: extension=" + extension
                            + ", size=" + size
                            + ", error=" + error.getClass().getSimpleName()
                            + ": " + String.valueOf(error.getMessage())
            );
            throw error;
        } finally {
            if (!source.delete() && source.exists()) {
                Log.w(TAG, "DSM local thumbnail temporary file cleanup failed");
            }
        }
    }

    @Override
    public void delete(
            DsmApiCatalog catalog,
            String sid,
            List<RemoteMedia> media
    ) throws IOException {
        if (media.isEmpty()) {
            throw new IllegalArgumentException("待删除的群晖照片为空");
        }

        DsmApiInfo deleteApi = requireDeleteApi(catalog);
        JSONArray paths = new JSONArray();
        for (RemoteMedia item : media) {
            paths.put(item.remotePath());
        }
        Map<String, String> startParameters = parameters(
                "api", deleteApi.name(),
                "version", Integer.toString(DELETE_API_VERSION),
                "method", "start",
                "path", paths.toString(),
                "accurate_progress", "true",
                "recursive", "true",
                "_sid", sid
        );
        String endpoint = DsmUrlBuilder.build(
                config.serverUrl(),
                deleteApi.path(),
                Map.of()
        );
        JSONObject startResponse = executeJson(
                "POST",
                endpoint,
                DsmUrlBuilder.encodeParameters(startParameters)
        );
        String taskId = parseDeleteTaskId(startResponse);

        long deadlineNanos = System.nanoTime()
                + DELETE_TASK_TIMEOUT_MS * 1_000_000L;
        while (true) {
            Map<String, String> statusParameters = parameters(
                    "api", deleteApi.name(),
                    "version", Integer.toString(DELETE_API_VERSION),
                    "method", "status",
                    "taskid", taskId,
                    "_sid", sid
            );
            String statusUrl = DsmUrlBuilder.build(
                    config.serverUrl(),
                    deleteApi.path(),
                    statusParameters
            );
            if (parseDeleteFinished(executeJson("GET", statusUrl, null))) {
                return;
            }
            if (System.nanoTime() >= deadlineNanos) {
                throw new DsmException("群晖删除任务等待超时");
            }
            try {
                Thread.sleep(DELETE_POLL_INTERVAL_MS);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new DsmException("群晖删除任务被中断", error);
            }
        }
    }

    static String parseDeleteTaskId(JSONObject response) throws DsmException {
        requireSuccess("SYNO.FileStation.Delete", response);
        JSONObject data = response.optJSONObject("data");
        String taskId = data == null ? "" : data.optString("taskid", "").trim();
        if (taskId.isEmpty()) {
            throw new DsmException("SYNO.FileStation.Delete 启动响应缺少 taskid");
        }
        return taskId;
    }

    static boolean parseDeleteFinished(JSONObject response) throws DsmException {
        requireSuccess("SYNO.FileStation.Delete", response);
        JSONObject data = response.optJSONObject("data");
        if (data == null || !data.has("finished")) {
            throw new DsmException("SYNO.FileStation.Delete 状态响应缺少 finished");
        }
        return data.optBoolean("finished", false);
    }

    private static DsmApiInfo requireDeleteApi(DsmApiCatalog catalog) throws DsmException {
        DsmApiInfo deleteApi = catalog.require("SYNO.FileStation.Delete");
        if (deleteApi.minVersion() > DELETE_API_VERSION
                || deleteApi.maxVersion() < DELETE_API_VERSION) {
            throw new DsmException("DSM 未提供 SYNO.FileStation.Delete v2");
        }
        return deleteApi;
    }

    private void streamFileResponse(String apiName, String url, OutputStream output)
            throws IOException {
        HttpURLConnection connection = openConnection("GET", url);
        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw httpError(status, connection);
            }
            String contentType = connection.getContentType();
            if (contentType != null && contentType.toLowerCase(Locale.ROOT).contains("json")) {
                String body = readText(connection.getInputStream());
                JSONObject response = parseJson(apiName, body);
                requireSuccess(apiName, response);
                throw new DsmException(apiName + " 未返回文件内容");
            }
            try (InputStream input = new BufferedInputStream(connection.getInputStream())) {
                input.transferTo(output);
            }
        } finally {
            connection.disconnect();
        }
    }

    private void listFolder(
            DsmApiInfo listApi,
            String sid,
            String folder,
            ArrayDeque<String> folders,
            List<RemoteMedia> media
    ) throws IOException {
        int offset = 0;
        while (true) {
            try {
                JSONObject data = requestFolderPage(
                        listApi,
                        sid,
                        folder,
                        offset,
                        LIST_LIMIT
                );
                JSONArray files = data.getJSONArray("files");
                for (int index = 0; index < files.length(); index++) {
                    JSONObject file = files.getJSONObject(index);
                    String path = file.getString("path");
                    if (file.optBoolean("isdir", false)) {
                        folders.addLast(path);
                        continue;
                    }
                    String name = file.getString("name");
                    String extension = extensionOf(name);
                    if (!IMAGE_EXTENSIONS.contains(extension)) {
                        continue;
                    }
                    JSONObject additional = file.optJSONObject("additional");
                    long size = additional == null ? -1L : additional.optLong("size", -1L);
                    JSONObject time = additional == null ? null : additional.optJSONObject("time");
                    long modified = time == null ? 0L : time.optLong("mtime", 0L);
                    media.add(new RemoteMedia(path, name, size, modified, mimeType(extension)));
                }
                offset += files.length();
                int total = data.optInt("total", offset);
                if (files.length() == 0 || offset >= total) {
                    return;
                }
            } catch (JSONException error) {
                throw new DsmException(listApi.name() + " 响应格式错误", error);
            }
        }
    }

    private JSONObject requestFolderPage(
            DsmApiInfo listApi,
            String sid,
            String folder,
            int offset,
            int limit
    ) throws IOException {
        Map<String, String> parameters = parameters(
                "api", listApi.name(),
                "version", Integer.toString(listApi.maxVersion()),
                "method", "list",
                "folder_path", folder,
                "offset", Integer.toString(offset),
                "limit", Integer.toString(limit),
                "sort_by", "name",
                "sort_direction", "asc",
                "additional", "[\"size\",\"time\"]",
                "_sid", sid
        );
        String url = DsmUrlBuilder.build(config.serverUrl(), listApi.path(), parameters);
        JSONObject response = executeJson("GET", url, null);
        if (!response.optBoolean("success", false)) {
            throw DsmException.fromFileStationListResponse(folder, response);
        }
        try {
            return response.getJSONObject("data");
        } catch (JSONException error) {
            throw new DsmException(listApi.name() + " 响应格式错误", error);
        }
    }

    private JSONObject executeJson(String method, String url, String formBody) throws IOException {
        HttpURLConnection connection = openConnection(method, url);
        try {
            if (formBody != null) {
                byte[] body = formBody.getBytes(StandardCharsets.UTF_8);
                connection.setDoOutput(true);
                connection.setRequestProperty(
                        "Content-Type",
                        "application/x-www-form-urlencoded; charset=UTF-8"
                );
                connection.setFixedLengthStreamingMode(body.length);
                try (OutputStream output = new BufferedOutputStream(connection.getOutputStream())) {
                    output.write(body);
                }
            }
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw httpError(status, connection);
            }
            return parseJson("DSM", readText(connection.getInputStream()));
        } finally {
            connection.disconnect();
        }
    }

    private HttpURLConnection openConnection(String method, String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Accept", "application/json");
        return connection;
    }

    private DsmException httpError(int status, HttpURLConnection connection) throws IOException {
        InputStream errorStream = connection.getErrorStream();
        String body = errorStream == null ? "" : readText(errorStream);
        return new DsmException("DSM HTTP 请求失败: " + status + (body.isBlank() ? "" : ", " + body));
    }

    private static JSONObject parseJson(String apiName, String body) throws DsmException {
        try {
            return new JSONObject(body);
        } catch (JSONException error) {
            throw new DsmException(apiName + " 未返回有效 JSON", error);
        }
    }

    private static void requireSuccess(String apiName, JSONObject response) throws DsmException {
        if (!response.optBoolean("success", false)) {
            throw DsmException.fromApiResponse(apiName, response);
        }
    }

    private static String readText(InputStream input) throws IOException {
        try (InputStream closeable = input) {
            return new String(closeable.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static LinkedHashMap<String, String> parameters(String... pairs) {
        LinkedHashMap<String, String> parameters = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            parameters.put(pairs[index], pairs[index + 1]);
        }
        return parameters;
    }

    private static String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String mimeType(String extension) {
        return switch (extension) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "heic" -> "image/heic";
            case "heif" -> "image/heif";
            case "bmp" -> "image/bmp";
            case "avif" -> "image/avif";
            case "dng" -> "image/x-adobe-dng";
            default -> "application/octet-stream";
        };
    }
}
