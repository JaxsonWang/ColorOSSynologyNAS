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
    /** File Station 列表合同固定使用 v2 */
    private static final int LIST_API_VERSION = 2;
    /** 单次 DSM 列表请求的原有最大条数 */
    private static final int LIST_LIMIT = 1_000;
    /** 相册业务认可的图片扩展名与唯一 MIME 映射 */
    private static final Map<String, String> IMAGE_MIME_TYPES = Map.ofEntries(
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("png", "image/png"),
            Map.entry("gif", "image/gif"),
            Map.entry("webp", "image/webp"),
            Map.entry("heic", "image/heic"),
            Map.entry("heif", "image/heif"),
            Map.entry("bmp", "image/bmp"),
            Map.entry("avif", "image/avif"),
            Map.entry("dng", "image/x-adobe-dng")
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
        // listApi 必须来自 SYNO.API.Info 且明确覆盖 v2
        DsmApiInfo listApi = requireApi(catalog);
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
        // listApi 必须来自发现结果且明确覆盖 v2
        DsmApiInfo listApi = requireApi(catalog);
        // data 是用于验证列表响应合同的首个目录分页
        JSONObject data = requestFolderPage(listApi, sid, folder, 0, 1);
        // folders 仅承接验证分页中可能存在的目录项
        ArrayDeque<String> folders = new ArrayDeque<>();
        // media 仅承接验证分页中可能存在的图片项
        List<RemoteMedia> media = new ArrayList<>();
        parseFolderPage(listApi.name(), data, 0, folders, media);
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
            /* listApi 是动态发现的列表 API */ DsmApiInfo listApi,
            /* sid 是当前内存会话标识 */ String sid,
            /* folder 是当前远端目录 */ String folder,
            /* folders 是待扫描子目录队列 */ ArrayDeque<String> folders,
            /* media 是已收集图片列表 */ List<RemoteMedia> media
    ) throws IOException {
        // offset 是当前目录下一页起点
        int offset = 0;
        while (true) {
            // data 是当前分页的 File Station 数据对象
            JSONObject data = requestFolderPage(listApi, sid, folder, offset, LIST_LIMIT);
            // page 是经过严格解析的当前分页边界
            FolderPage page = parseFolderPage(listApi.name(), data, offset, folders, media);
            if (page.finished(offset)) {
                return;
            }
            offset += page.fileCount();
        }
    }

    /**
     * 严格解析一个 File Station 列表分页并写入目录与图片集合
     *
     * @param apiName 当前动态列表 API 名称
     * @param data File Station 响应中的 data 对象
     * @param offset 当前分页起点
     * @param folders 待扫描子目录队列
     * @param media 已收集图片列表
     * @return 当前分页条目数和目录总条目数
     * @throws DsmException 列表分页缺少字段或字段类型错误
     */
    static FolderPage parseFolderPage(
            /* apiName 是当前动态列表 API 名称 */ String apiName,
            /* data 是 File Station 响应中的 data 对象 */ JSONObject data,
            /* offset 是当前分页起点 */ int offset,
            /* folders 是待扫描子目录队列 */ ArrayDeque<String> folders,
            /* media 是已收集图片列表 */ List<RemoteMedia> media
    ) throws DsmException {
        try {
            // files 保存当前分页且类型明确的目录项
            JSONArray files = data.getJSONArray("files");
            // responseOffset 是 DSM 明确返回的当前分页起点
            long responseOffset = requireInteger(data, "offset");
            if (responseOffset != offset) {
                throw new JSONException("响应 offset 与请求不一致");
            }
            // totalValue 是尚未缩窄为分页整数的远端条目总数
            long totalValue = requireInteger(data, "total");
            if (totalValue < 0L || totalValue > Integer.MAX_VALUE) {
                throw new JSONException("total 超出整数范围");
            }
            // total 是 DSM 报告的当前目录总条目数
            int total = (int) totalValue;
            if (files.length() == 0 && offset < total) {
                throw new JSONException("分页为空但 total 表示仍有未读取条目");
            }
            // index 指向当前处理的目录项
            for (int index = 0; index < files.length(); index++) {
                // file 是当前 DSM 目录项
                JSONObject file = files.getJSONObject(index);
                // path 是 DSM 返回的完整远端路径
                String path = DsmHttpTransport.requiredString(file, "path");
                // name 是 DSM 对目录和文件都必须返回的名称
                String name = DsmHttpTransport.requiredString(file, "name");
                // directory 是 DSM 明确返回的目录标志
                boolean directory = requireBoolean(file, "isdir");
                // additional 承载请求的文件大小和时间元数据
                JSONObject additional = file.getJSONObject("additional");
                // size 是 DSM 明确返回的目录项字节数
                long size = requireInteger(additional, "size");
                // time 承载远端修改时间
                JSONObject time = additional.getJSONObject("time");
                // modified 是 DSM 明确返回的修改时间戳
                long modified = requireInteger(time, "mtime");
                if (directory) {
                    folders.addLast(path);
                    continue;
                }
                // extension 用于筛选图片并读取唯一 MIME 映射
                String extension = extensionOf(name);
                // mimeType 是当前图片扩展名对应的 MIME 类型
                String mimeType = IMAGE_MIME_TYPES.get(extension);
                if (mimeType == null) {
                    continue;
                }
                media.add(new RemoteMedia(path, name, size, modified, mimeType));
            }
            return new FolderPage(files.length(), total);
        } catch (/* 当前分页的 JSON 字段或类型错误 */ JSONException error) {
            throw new DsmException(apiName + " 响应格式错误", error);
        }
    }

    /** 保存严格解析后的 File Station 分页边界 */
    static final class FolderPage {
        /** 当前分页实际条目数 */
        private final int fileCount;
        /** 当前目录总条目数 */
        private final int total;

        /**
         * 创建不可变分页边界
         *
         * @param fileCount 当前分页实际条目数
         * @param total 当前目录总条目数
         */
        private FolderPage(int fileCount, int total) {
            this.fileCount = fileCount;
            this.total = total;
        }

        /**
         * 判断当前分页是否到达目录末尾
         *
         * @param offset 当前分页起点
         * @return 是否已经读取完当前目录
         */
        boolean finished(int offset) {
            return offset + fileCount >= total;
        }

        /** @return 当前分页实际条目数 */
        int fileCount() {
            return fileCount;
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
            /* listApi 是动态发现的列表 API */ DsmApiInfo listApi,
            /* sid 是当前内存会话标识 */ String sid,
            /* folder 是远端目录路径 */ String folder,
            /* offset 是分页起点 */ int offset,
            /* limit 是本页最大条数 */ int limit
    ) throws IOException {
        // parameters 完整表达 File Station 列表分页合同
        Map<String, String> parameters = requestParameters(
                listApi.name(),
                sid,
                folder,
                offset,
                limit
        );
        // url 使用 DSM 发现路径与严格 HTTPS 基础地址构建
        String url = DsmUrlBuilder.build(config.serverUrl(), listApi.path(), parameters);
        // response 是当前列表分页的 DSM JSON 响应
        JSONObject response = DsmHttpTransport.executeJson("GET", url, null);
        if (!DsmHttpTransport.readSuccess(listApi.name(), response)) {
            throw DsmException.fromFileStationListResponse(folder, response);
        }
        try {
            return response.getJSONObject("data");
        } catch (/* data 对象缺失或类型错误 */ JSONException error) {
            throw new DsmException(listApi.name() + " 响应格式错误", error);
        }
    }

    /**
     * 校验动态发现的列表 API 明确覆盖 v2
     *
     * @param catalog DSM 动态发现的 API 目录
     * @return 明确支持 v2 的列表 API
     * @throws DsmException DSM 未提供 File Station List v2
     */
    static DsmApiInfo requireApi(DsmApiCatalog catalog) throws DsmException {
        // listApi 来源于 SYNO.API.Info，路径与版本不在调用点硬编码
        DsmApiInfo listApi = catalog.require("SYNO.FileStation.List");
        if (listApi.minVersion() > LIST_API_VERSION
                || listApi.maxVersion() < LIST_API_VERSION) {
            throw new DsmException("DSM 未提供 SYNO.FileStation.List v2");
        }
        return listApi;
    }

    /**
     * 构建固定 v2 的 File Station 列表分页参数
     *
     * @param apiName 动态发现的列表 API 名称
     * @param sid 当前内存会话标识
     * @param folder 远端目录路径
     * @param offset 分页起点
     * @param limit 本页最大条数
     * @return 保持协议顺序的列表分页参数
     */
    static Map<String, String> requestParameters(
            /* apiName 是动态发现的列表 API 名称 */ String apiName,
            /* sid 是当前内存会话标识 */ String sid,
            /* folder 是远端目录路径 */ String folder,
            /* offset 是分页起点 */ int offset,
            /* limit 是本页最大条数 */ int limit
    ) {
        return DsmParameters.of(
                "api", apiName,
                "version", Integer.toString(LIST_API_VERSION),
                "method", "list",
                "folder_path", folder,
                "offset", Integer.toString(offset),
                "limit", Integer.toString(limit),
                "sort_by", "name",
                "sort_direction", "asc",
                "additional", "[\"size\",\"time\"]",
                "_sid", sid
        );
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
     * 严格读取 JSON 布尔字段
     *
     * @param object 当前协议对象
     * @param field 必需的布尔字段名
     * @return 字段中的布尔值
     * @throws JSONException 字段缺失或实际类型不是布尔值
     */
    private static boolean requireBoolean(JSONObject object, String field) throws JSONException {
        // value 是尚未转换的协议字段值
        Object value = object.get(field);
        if (!(value instanceof Boolean)) {
            throw new JSONException(field + " 不是布尔值");
        }
        return (Boolean) value;
    }

    /**
     * 严格读取 JSON 整数字段且拒绝字符串和小数转换
     *
     * @param object 当前协议对象
     * @param field 必需的整数字段名
     * @return 字段中的长整数
     * @throws JSONException 字段缺失或实际类型不是整数
     */
    private static long requireInteger(JSONObject object, String field) throws JSONException {
        // value 是尚未转换的协议字段值
        Object value = object.get(field);
        if (!(value instanceof Integer) && !(value instanceof Long)) {
            throw new JSONException(field + " 不是整数");
        }
        return ((Number) value).longValue();
    }
}
