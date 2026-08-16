package com.jaxson.coloros.synologynas.dsm;

import com.jaxson.coloros.synologynas.SynologyConfig;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 负责 DSM 目录遍历、分页与远端图片元数据转换 */
final class DsmMediaListing {
    /** 单次 DSM 列表请求的原有最大条数 */
    private static final int LIST_LIMIT = 1_000;
    /** 相册业务认可的图片扩展名集合 */
    private static final Set<String> IMAGE_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp", "avif", "dng"
    );

    /** 当前 DSM 地址与远端根目录配置 */
    private final SynologyConfig config;

    /**
     * 创建绑定单份 DSM 配置的列表读取器
     *
     * @param config 当前已发布的 DSM 配置
     */
    DsmMediaListing(SynologyConfig config) {
        this.config = config;
    }

    /**
     * 从配置根目录开始按广度优先顺序收集所有远端图片
     *
     * @param catalog DSM 动态发现的 API 目录
     * @param sid 仅由调用链持有的内存会话标识
     * @return 保持 DSM 遍历顺序的远端图片列表
     * @throws IOException DSM 列表请求或响应解析失败
     */
    List<RemoteMedia> listImages(DsmApiCatalog catalog, String sid) throws IOException {
        // listApi 必须来自 SYNO.API.Info 动态发现结果
        DsmApiInfo listApi = catalog.require("SYNO.FileStation.List");
        // folders 保存尚未读取的远端目录
        ArrayDeque<String> folders = new ArrayDeque<>();
        // visited 防止 DSM 返回重复目录时重复扫描
        Set<String> visited = new HashSet<>();
        // media 累积所有符合图片扩展名策略的远端文件
        List<RemoteMedia> media = new ArrayList<>();
        folders.add(config.remoteRoot());

        while (!folders.isEmpty()) {
            // folder 是当前从队列取出的远端目录
            String folder = folders.removeFirst();
            if (!visited.add(folder)) {
                continue;
            }
            listFolder(listApi, sid, folder, folders, media);
        }
        return media;
    }

    /**
     * 验证指定目录能够读取至少一页，供“保存并连接”链路使用
     *
     * @param catalog DSM 动态发现的 API 目录
     * @param sid 仅由当前连接测试持有的会话标识
     * @param folder 待验证的远端图片根目录
     * @throws IOException DSM 列表请求或响应解析失败
     */
    void verifyFolder(DsmApiCatalog catalog, String sid, String folder) throws IOException {
        // listApi 必须来自发现结果，不能硬编码 API 路径
        DsmApiInfo listApi = catalog.require("SYNO.FileStation.List");
        requestFolderPage(listApi, sid, folder, 0, 1);
    }

    /**
     * 读取一个目录的全部分页，并将子目录与图片分别写入调用方集合
     *
     * @param listApi 动态发现的列表 API
     * @param sid 当前内存会话标识
     * @param folder 当前远端目录
     * @param folders 待扫描子目录队列
     * @param media 已收集图片列表
     * @throws IOException DSM 请求或响应格式失败
     */
    private void listFolder(
            DsmApiInfo listApi,
            String sid,
            String folder,
            ArrayDeque<String> folders,
            List<RemoteMedia> media
    ) throws IOException {
        // offset 是当前目录下一页起点
        int offset = 0;
        while (true) {
            try {
                // data 是当前分页的 File Station 数据对象
                JSONObject data = requestFolderPage(listApi, sid, folder, offset, LIST_LIMIT);
                // files 保存当前分页的目录项
                JSONArray files = data.getJSONArray("files");
                // index 指向当前处理的目录项
                for (int index = 0; index < files.length(); index++) {
                    // file 是当前 DSM 目录项
                    JSONObject file = files.getJSONObject(index);
                    // path 是 DSM 返回的完整远端路径
                    String path = file.getString("path");
                    if (file.optBoolean("isdir", false)) {
                        folders.addLast(path);
                        continue;
                    }
                    // name 是当前远端文件名
                    String name = file.getString("name");
                    // extension 用于筛选图片并推导 MIME 类型
                    String extension = extensionOf(name);
                    if (!IMAGE_EXTENSIONS.contains(extension)) {
                        continue;
                    }
                    // additional 承载请求的文件大小和时间元数据
                    JSONObject additional = file.optJSONObject("additional");
                    // size 保留 DSM 缺失大小时的原有 -1 语义
                    long size = additional == null ? -1L : additional.optLong("size", -1L);
                    // time 承载远端修改时间
                    JSONObject time = additional == null ? null : additional.optJSONObject("time");
                    // modified 保留 DSM 缺失时间时的原有 0 语义
                    long modified = time == null ? 0L : time.optLong("mtime", 0L);
                    media.add(new RemoteMedia(path, name, size, modified, mimeType(extension)));
                }
                offset += files.length();
                // total 是 DSM 报告的当前目录总条目数
                int total = data.optInt("total", offset);
                if (files.length() == 0 || offset >= total) {
                    return;
                }
            } catch (/* 当前分页的 JSON 格式错误 */ JSONException error) {
                throw new DsmException(listApi.name() + " 响应格式错误", error);
            }
        }
    }

    /**
     * 请求一个远端目录分页并返回 data 对象
     *
     * @param listApi 动态发现的列表 API
     * @param sid 当前内存会话标识
     * @param folder 远端目录路径
     * @param offset 分页起点
     * @param limit 本页最大条数
     * @return File Station 响应中的 data 对象
     * @throws IOException DSM 请求、业务错误或格式错误
     */
    private JSONObject requestFolderPage(
            DsmApiInfo listApi,
            String sid,
            String folder,
            int offset,
            int limit
    ) throws IOException {
        // parameters 完整表达 File Station 列表分页合同
        Map<String, String> parameters = DsmParameters.of(
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
        // url 使用 DSM 发现路径与严格 HTTPS 基础地址构建
        String url = DsmUrlBuilder.build(config.serverUrl(), listApi.path(), parameters);
        // response 是当前列表分页的 DSM JSON 响应
        JSONObject response = DsmHttpTransport.executeJson("GET", url, null);
        if (!response.optBoolean("success", false)) {
            throw DsmException.fromFileStationListResponse(folder, response);
        }
        try {
            return response.getJSONObject("data");
        } catch (/* data 对象缺失或类型错误 */ JSONException error) {
            throw new DsmException(listApi.name() + " 响应格式错误", error);
        }
    }

    /**
     * 提取小写文件扩展名供图片策略使用
     *
     * @param name 远端文件名
     * @return 不含点号的小写扩展名；无扩展名时为空字符串
     */
    private static String extensionOf(String name) {
        // dot 是文件名最后一个扩展名分隔符位置
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * 将已认可的图片扩展名映射为远端媒体 MIME 类型
     *
     * @param extension 小写文件扩展名
     * @return 对应 MIME 类型
     */
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
