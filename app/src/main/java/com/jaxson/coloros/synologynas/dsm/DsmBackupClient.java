package com.jaxson.coloros.synologynas.dsm;

import com.jaxson.coloros.synologynas.SynologyConfig;
import com.jaxson.coloros.synologynas.backup.BackupPath;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class DsmBackupClient implements DsmBackupGateway {
    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 60_000;
    private static final int FILE_STATION_API_VERSION = 2;
    private static final long MD5_POLL_INTERVAL_MS = 250L;
    private static final long MD5_TASK_TIMEOUT_MS = 60_000L;

    private final SynologyConfig config;

    public DsmBackupClient(SynologyConfig config) {
        this.config = config;
    }

    @Override
    public DsmApiCatalog discoverApis() throws IOException {
        Map<String, String> parameters = parameters(
                "api", "SYNO.API.Info",
                "version", "1",
                "method", "query",
                "query", "SYNO.API.Auth,SYNO.FileStation.Upload,SYNO.FileStation.MD5"
        );
        String url = DsmUrlBuilder.build(config.serverUrl(), "query.cgi", parameters);
        DsmApiCatalog catalog = DsmApiInfoParser.parse(
                executeJson("GET", url, null).toString()
        );
        requireVersion(catalog.require("SYNO.FileStation.Upload"));
        requireVersion(catalog.require("SYNO.FileStation.MD5"));
        return catalog;
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
                "session", DsmClient.SESSION_NAME,
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
        JSONObject data = response.optJSONObject("data");
        String sid = data == null ? "" : data.optString("sid", "").trim();
        if (sid.isEmpty()) {
            throw new DsmException("DSM 登录成功响应缺少 SID");
        }
        return sid;
    }

    @Override
    public Optional<String> md5(
            DsmApiCatalog catalog,
            String sid,
            String remotePath
    ) throws IOException {
        DsmApiInfo md5Api = catalog.require("SYNO.FileStation.MD5");
        requireVersion(md5Api);
        Map<String, String> startParameters = parameters(
                "api", md5Api.name(),
                "version", Integer.toString(FILE_STATION_API_VERSION),
                "method", "start",
                "file_path", fileStationStringParameter(remotePath),
                "_sid", sid
        );
        String startUrl = DsmUrlBuilder.build(
                config.serverUrl(),
                md5Api.path(),
                startParameters
        );
        JSONObject startResponse = executeJson("GET", startUrl, null);
        if (!startResponse.optBoolean("success", false)) {
            if (isMissingMd5TargetError(errorCode(startResponse))) {
                return Optional.empty();
            }
            throw DsmException.fromApiResponse(md5Api.name(), startResponse);
        }
        JSONObject startData = startResponse.optJSONObject("data");
        String taskId = startData == null
                ? ""
                : startData.optString("taskid", "").trim();
        if (taskId.isEmpty()) {
            throw new DsmException(md5Api.name() + " 启动响应缺少 taskid");
        }

        long deadlineNanos = System.nanoTime() + MD5_TASK_TIMEOUT_MS * 1_000_000L;
        while (true) {
            Map<String, String> statusParameters = parameters(
                    "api", md5Api.name(),
                    "version", Integer.toString(FILE_STATION_API_VERSION),
                    "method", "status",
                    "taskid", fileStationStringParameter(taskId),
                    "_sid", sid
            );
            String statusUrl = DsmUrlBuilder.build(
                    config.serverUrl(),
                    md5Api.path(),
                    statusParameters
            );
            JSONObject statusResponse = executeJson("GET", statusUrl, null);
            requireSuccess(md5Api.name(), statusResponse);
            JSONObject data = statusResponse.optJSONObject("data");
            if (data == null || !data.has("finished")) {
                throw new DsmException(md5Api.name() + " 状态响应缺少 finished");
            }
            if (data.optBoolean("finished", false)) {
                String hash = data.optString("md5", "").trim().toLowerCase(Locale.ROOT);
                if (!hash.matches("[0-9a-f]{32}")) {
                    throw new DsmException(md5Api.name() + " 状态响应缺少有效 MD5");
                }
                return Optional.of(hash);
            }
            if (System.nanoTime() >= deadlineNanos) {
                throw new DsmException("群晖 MD5 任务等待超时");
            }
            sleepForMd5Poll();
        }
    }

    @Override
    public long upload(
            DsmApiCatalog catalog,
            String sid,
            BackupPath path,
            long fileSize,
            InputStream input
    ) throws IOException {
        DsmApiInfo uploadApi = catalog.require("SYNO.FileStation.Upload");
        requireVersion(uploadApi);
        String url = DsmUrlBuilder.build(
                config.serverUrl(),
                uploadApi.path(),
                Map.of("_sid", sid)
        );
        String boundary = "ColorOSSynologyNAS-" + UUID.randomUUID();
        byte[] prefix = multipartPrefix(boundary, path.folder(), path.fileName());
        byte[] suffix = ("\r\n--" + boundary + "--\r\n")
                .getBytes(StandardCharsets.UTF_8);
        long contentLength;
        try {
            contentLength = Math.addExact(Math.addExact(prefix.length, fileSize), suffix.length);
        } catch (ArithmeticException error) {
            throw new DsmBackupReadException("照片大小超出 DSM 上传范围", error);
        }

        HttpURLConnection connection = openConnection("POST", url);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        connection.setFixedLengthStreamingMode(contentLength);
        try {
            try (OutputStream output = new BufferedOutputStream(connection.getOutputStream())) {
                output.write(prefix);
                copyExact(input, output, fileSize);
                output.write(suffix);
            }
            int status = connection.getResponseCode();
            String body = readResponseBody(connection, status);
            if (status < 200 || status >= 300) {
                throw new DsmException(
                        "DSM HTTP 请求失败: " + status
                                + (body.isBlank() ? "" : ", " + body)
                );
            }
            if (body.isBlank()) {
                return fileSize;
            }
            JSONObject response = parseJson(uploadApi.name(), body);
            if (!response.optBoolean("success", false)) {
                int code = errorCode(response);
                if (code == 414 || code == 1805) {
                    throw new DsmRemoteFileAlreadyExistsException(
                            "群晖备份文件已存在: " + path.remotePath()
                    );
                }
                throw DsmException.fromApiResponse(uploadApi.name(), response);
            }
            return fileSize;
        } finally {
            connection.disconnect();
        }
    }

    static String fileStationStringParameter(String value) {
        return JSONObject.quote(value);
    }

    static boolean isMissingMd5TargetError(int code) {
        // DSM 7 returns 418 while create_parents upload folders do not exist yet.
        // The upload call remains authoritative for genuinely illegal paths.
        return code == 408 || code == 418;
    }

    static byte[] multipartPrefix(String boundary, String folder, String fileName) {
        StringBuilder body = new StringBuilder();
        appendPart(body, boundary, "api", "SYNO.FileStation.Upload");
        appendPart(body, boundary, "version", Integer.toString(FILE_STATION_API_VERSION));
        appendPart(body, boundary, "method", "upload");
        appendPart(body, boundary, "path", folder);
        appendPart(body, boundary, "create_parents", "true");
        appendPart(body, boundary, "overwrite", "false");
        body.append("--").append(boundary).append("\r\n")
                .append("Content-Disposition: form-data; name=\"file\"; filename=\"")
                .append(escapeHeaderValue(fileName))
                .append("\"\r\n")
                .append("Content-Type: application/octet-stream\r\n\r\n");
        return body.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void appendPart(
            StringBuilder body,
            String boundary,
            String name,
            String value
    ) {
        body.append("--").append(boundary).append("\r\n")
                .append("Content-Disposition: form-data; name=\"")
                .append(name)
                .append("\"\r\n\r\n")
                .append(value)
                .append("\r\n");
    }

    private static String escapeHeaderValue(String value) {
        return value.replace("\\", "_").replace("\"", "_");
    }

    private static void copyExact(InputStream input, OutputStream output, long expectedBytes)
            throws IOException {
        byte[] buffer = new byte[64 * 1024];
        long remaining = expectedBytes;
        while (remaining > 0L) {
            int count;
            try {
                count = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
            } catch (IOException error) {
                throw new DsmBackupReadException("读取本机照片失败", error);
            }
            if (count < 0) {
                throw new DsmBackupReadException(
                        "本机照片数据提前结束，缺少 " + remaining + " 字节"
                );
            }
            output.write(buffer, 0, count);
            remaining -= count;
        }
        int trailing;
        try {
            trailing = input.read();
        } catch (IOException error) {
            throw new DsmBackupReadException("校验本机照片长度失败", error);
        }
        if (trailing >= 0) {
            throw new DsmBackupReadException("本机照片实际大小大于 MediaStore 记录");
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
            String body = readResponseBody(connection, status);
            if (status < 200 || status >= 300) {
                throw new DsmException(
                        "DSM HTTP 请求失败: " + status
                                + (body.isBlank() ? "" : ", " + body)
                );
            }
            return parseJson("DSM", body);
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

    private static String readResponseBody(HttpURLConnection connection, int status)
            throws IOException {
        InputStream stream = status >= 200 && status < 300
                ? connection.getInputStream()
                : connection.getErrorStream();
        if (stream == null) {
            return "";
        }
        try (InputStream input = new BufferedInputStream(stream)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
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

    private static int errorCode(JSONObject response) {
        JSONObject error = response.optJSONObject("error");
        return error == null ? -1 : error.optInt("code", -1);
    }

    private static void requireVersion(DsmApiInfo api) throws DsmException {
        if (api.minVersion() > FILE_STATION_API_VERSION
                || api.maxVersion() < FILE_STATION_API_VERSION) {
            throw new DsmException(api.name() + " 未提供 v" + FILE_STATION_API_VERSION);
        }
    }

    private static void sleepForMd5Poll() throws DsmException {
        try {
            Thread.sleep(MD5_POLL_INTERVAL_MS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new DsmException("群晖 MD5 任务被中断", error);
        }
    }

    private static LinkedHashMap<String, String> parameters(String... pairs) {
        LinkedHashMap<String, String> parameters = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            parameters.put(pairs[index], pairs[index + 1]);
        }
        return parameters;
    }
}
