package com.jaxson.coloros.synologynas.dsm;

import android.util.Log;

import com.jaxson.coloros.synologynas.SynologyConfig;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** DSM 浏览客户端，协调 API 发现、认证、媒体读取、缩略图和删除职责 */
public final class DsmClient implements DsmGateway {
    /** DSM 会话名保持与备份客户端一致，SID 本身不写入字段或持久化 */
    public static final String SESSION_NAME = "ColorOSSynologyNAS";
    /** ColorOS 小缩略图对应的本地解码最大边长 */
    private static final int SMALL_THUMBNAIL_MAX_DIMENSION = 512;
    /** ColorOS 大缩略图对应的本地解码最大边长 */
    private static final int LARGE_THUMBNAIL_MAX_DIMENSION = 1_280;
    /** DSM 文件缩略图能力不足而必须本地解码的格式 */
    private static final Set<String> LOCAL_THUMBNAIL_EXTENSIONS = Set.of(
            "webp", "heic", "heif", "avif"
    );
    /** Android 日志标签，用于定位本地缩略图处理结果 */
    private static final String TAG = "ColorOSSynologyNAS";

    /** 当前 DSM 地址、账号和远端根目录配置 */
    private final SynologyConfig config;
    /** 远端图片目录遍历职责 */
    private final DsmMediaListing mediaListing;
    /** Delete v2 任务执行职责 */
    private final DsmDeleteOperation deleteOperation;

    /**
     * 创建绑定当前配置的 DSM 浏览客户端
     *
     * @param config 当前已发布的 DSM 配置
     */
    public DsmClient(SynologyConfig config) {
        this.config = config;
        this.mediaListing = new DsmMediaListing(config);
        this.deleteOperation = new DsmDeleteOperation(config);
    }

    /**
     * 完整验证登录、必需 API、设备型号和配置根目录，并始终注销测试会话
     *
     * @return DSM 返回的设备型号
     * @throws IOException API 发现、认证、目录读取或注销失败
     */
    public String testConnection() throws IOException {
        // catalog 是本次连接测试动态发现的 API 目录
        DsmApiCatalog catalog = discoverApis();
        // sid 仅存在于当前调用栈，并在 finally 中注销
        String sid = login(catalog);
        // sessionLogout 通过 try-with-resources 保证注销并自动保留主体失败原因
        Closeable sessionLogout = () -> logout(catalog, sid);
        try (sessionLogout) {
            requireTestConnectionApis(catalog, config.backupEnabled());
            // deviceModel 是连接成功后发布给相册卡片的真实型号
            String deviceModel = getDeviceModel(catalog, sid);
            mediaListing.verifyFolder(catalog, sid, config.remoteRoot());
            return deviceModel;
        }
    }

    /**
     * 通过 SYNO.API.Info 动态发现浏览链路所需的 API 路径和版本
     *
     * @return DSM API 目录
     * @throws IOException 请求或响应解析失败
     */
    @Override
    public DsmApiCatalog discoverApis() throws IOException {
        // parameters 明确列出当前配置需要发现的全部 API
        Map<String, String> parameters = DsmParameters.of(
                "api", "SYNO.API.Info",
                "version", "1",
                "method", "query",
                "query", discoveryQuery(config.backupEnabled())
        );
        // url 指向 DSM 固定的 API 发现入口，后续业务路径均来自响应
        String url = DsmUrlBuilder.build(config.serverUrl(), "query.cgi", parameters);
        return DsmApiInfoParser.parse(
                DsmHttpTransport.executeJson("GET", url, null).toString()
        );
    }

    /**
     * 使用账号、密码和可选 OTP 登录 DSM，并返回仅供内存调用链使用的 SID
     *
     * @param catalog DSM 动态发现的 API 目录
     * @return 非空 DSM SID
     * @throws IOException 登录请求、业务错误或响应格式失败
     */
    @Override
    public String login(
            /* catalog 是 DSM 动态发现的 API 目录 */ DsmApiCatalog catalog
    ) throws IOException {
        // auth 是动态发现的认证 API 描述
        DsmApiInfo auth = catalog.require("SYNO.API.Auth");
        // parameters 完整表达 DSM SID 登录合同
        Map<String, String> parameters = DsmParameters.of(
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
        // url 使用发现的认证路径，表单参数不进入查询字符串
        String url = DsmUrlBuilder.build(config.serverUrl(), auth.path(), Map.of());
        // response 是 DSM 登录 JSON 响应
        JSONObject response = DsmHttpTransport.executeJson(
                "POST",
                url,
                DsmUrlBuilder.encodeParameters(parameters)
        );
        DsmHttpTransport.requireSuccess(auth.name(), response);
        return DsmHttpTransport.parseSid(response);
    }

    /**
     * 读取 DSM 系统信息中的真实设备型号
     *
     * @param catalog DSM 动态发现的 API 目录
     * @param sid 当前内存会话标识
     * @return 非空 NAS 型号
     * @throws IOException 请求、业务错误或响应格式失败
     */
    @Override
    public String getDeviceModel(
            /* catalog 是 DSM 动态发现的 API 目录 */ DsmApiCatalog catalog,
            /* sid 是当前内存会话标识 */ String sid
    ) throws IOException {
        // systemApi 是动态发现的系统信息 API
        DsmApiInfo systemApi = catalog.require("SYNO.Core.System");
        // parameters 表达系统信息查询合同
        Map<String, String> parameters = DsmParameters.of(
                "api", systemApi.name(),
                "version", Integer.toString(systemApi.maxVersion()),
                "method", "info",
                "_sid", sid
        );
        // url 使用发现路径与当前 SID 构建
        String url = DsmUrlBuilder.build(config.serverUrl(), systemApi.path(), parameters);
        // response 是 DSM 系统信息响应
        JSONObject response = DsmHttpTransport.executeJson("GET", url, null);
        return parseDeviceModel(response);
    }

    /**
     * 解析成功系统信息响应中的型号字段
     *
     * @param response DSM 系统信息响应
     * @return 去除首尾空白后的设备型号
     * @throws DsmException DSM 失败或型号缺失
     */
    static String parseDeviceModel(JSONObject response) throws DsmException {
        DsmHttpTransport.requireSuccess("SYNO.Core.System", response);
        try {
            if (!response.getJSONObject("data").has("model")) {
                throw new DsmException("SYNO.Core.System 响应缺少 NAS 型号");
            }
            // model 是严格读取并规范化后的 NAS 型号
            String model = DsmHttpTransport.requiredString(
                    response.getJSONObject("data"),
                    "model"
            ).trim();
            if (model.isEmpty()) {
                throw new DsmException("SYNO.Core.System 响应缺少 NAS 型号");
            }
            return model;
        } catch (/* 系统信息 data 或 model 字段格式错误 */ JSONException error) {
            throw new DsmException("SYNO.Core.System 响应格式错误", error);
        }
    }

    /**
     * 注销当前内存 SID，不在客户端字段或持久化层保留会话
     *
     * @param catalog DSM 动态发现的 API 目录
     * @param sid 待注销的内存会话标识
     * @throws IOException 注销请求或 DSM 业务失败
     */
    @Override
    public void logout(
            /* catalog 是 DSM 动态发现的 API 目录 */ DsmApiCatalog catalog,
            /* sid 是待注销的内存会话标识 */ String sid
    ) throws IOException {
        // auth 是动态发现的认证 API 描述
        DsmApiInfo auth = catalog.require("SYNO.API.Auth");
        // parameters 完整表达指定会话的注销合同
        Map<String, String> parameters = logoutParameters(auth, sid);
        // url 使用发现的认证路径，注销参数保留在表单体
        String url = DsmUrlBuilder.build(config.serverUrl(), auth.path(), Map.of());
        // response 是 DSM 注销响应
        JSONObject response = DsmHttpTransport.executeJson(
                "POST",
                url,
                DsmUrlBuilder.encodeParameters(parameters)
        );
        DsmHttpTransport.requireSuccess(auth.name(), response);
    }

    /**
     * 构建两个 DSM 客户端共享的 Auth logout 表单参数
     *
     * @param auth 动态发现的认证 API 描述
     * @param sid 待注销的内存会话标识
     * @return 保持协议顺序且不含凭据的注销参数
     */
    static Map<String, String> logoutParameters(DsmApiInfo auth, String sid) {
        return DsmParameters.of(
                "api", auth.name(),
                "version", Integer.toString(auth.maxVersion()),
                "method", "logout",
                "session", SESSION_NAME,
                "_sid", sid
        );
    }

    /**
     * 遍历配置根目录并返回所有远端图片
     *
     * @param catalog DSM 动态发现的 API 目录
     * @param sid 当前内存会话标识
     * @return 远端图片元数据列表
     * @throws IOException DSM 列表请求或解析失败
     */
    @Override
    public List<RemoteMedia> listImages(
            /* catalog 是 DSM 动态发现的 API 目录 */ DsmApiCatalog catalog,
            /* sid 是当前内存会话标识 */ String sid
    ) throws IOException {
        return mediaListing.listImages(catalog, sid);
    }

    /**
     * 将远端原图按流直接写入调用方目标
     *
     * @param catalog DSM 动态发现的 API 目录
     * @param sid 当前内存会话标识
     * @param media 待读取的远端图片
     * @param output ColorOS 调用链提供的目标输出流
     * @throws IOException 下载请求或流复制失败
     */
    @Override
    public void download(
            /* catalog 是 DSM 动态发现的 API 目录 */ DsmApiCatalog catalog,
            /* sid 是当前内存会话标识 */ String sid,
            /* media 是待读取的远端图片 */ RemoteMedia media,
            /* output 是 ColorOS 调用链提供的目标输出流 */ OutputStream output
    ) throws IOException {
        // downloadApi 是动态发现的原图下载 API
        DsmApiInfo downloadApi = catalog.require("SYNO.FileStation.Download");
        // parameters 完整表达 File Station 原图下载合同
        Map<String, String> parameters = DsmParameters.of(
                "api", downloadApi.name(),
                "version", Integer.toString(downloadApi.maxVersion()),
                "method", "download",
                "path", media.remotePath(),
                "mode", "download",
                "_sid", sid
        );
        // url 使用发现的下载路径与远端原始路径构建
        String url = DsmUrlBuilder.build(config.serverUrl(), downloadApi.path(), parameters);
        DsmHttpTransport.streamFileResponse(downloadApi.name(), url, output);
    }

    /**
     * 按 ColorOS 尺寸策略返回 DSM 缩略图或特殊格式的本地 JPEG 缩略图
     *
     * @param catalog DSM 动态发现的 API 目录
     * @param sid 当前内存会话标识
     * @param media 待读取缩略图的远端图片
     * @param size ColorOS 请求的 small 或 large 尺寸
     * @param output ColorOS 调用链提供的目标输出流
     * @throws IOException 远端读取或本地缩略图生成失败
     */
    @Override
    public void downloadThumbnail(
            /* catalog 是 DSM 动态发现的 API 目录 */ DsmApiCatalog catalog,
            /* sid 是当前内存会话标识 */ String sid,
            /* media 是待读取缩略图的远端图片 */ RemoteMedia media,
            /* size 是 ColorOS 请求的缩略图尺寸 */ String size,
            /* output 是 ColorOS 调用链提供的目标输出流 */ OutputStream output
    ) throws IOException {
        if (!"small".equals(size) && !"large".equals(size)) {
            throw new IllegalArgumentException("不支持的缩略图尺寸: " + size);
        }
        if (requiresLocalThumbnail(media)) {
            generateLocalThumbnail(catalog, sid, media, size, output);
            return;
        }
        // thumbnailApi 是动态发现的 File Station 缩略图 API
        DsmApiInfo thumbnailApi = catalog.require("SYNO.FileStation.Thumb");
        // parameters 完整表达 DSM 缩略图读取合同
        Map<String, String> parameters = DsmParameters.of(
                "api", thumbnailApi.name(),
                "version", Integer.toString(thumbnailApi.maxVersion()),
                "method", "get",
                "path", media.remotePath(),
                "size", size,
                "_sid", sid
        );
        // url 使用发现的缩略图路径和原始远端路径构建
        String url = DsmUrlBuilder.build(config.serverUrl(), thumbnailApi.path(), parameters);
        DsmHttpTransport.streamFileResponse(thumbnailApi.name(), url, output);
    }

    /**
     * 判断远端图片格式是否必须下载原文件后由 Android 本地解码
     *
     * @param media 待判断的远端图片
     * @return 是否需要本地生成缩略图
     */
    static boolean requiresLocalThumbnail(RemoteMedia media) {
        return LOCAL_THUMBNAIL_EXTENSIONS.contains(extensionOf(media.name()));
    }

    /**
     * 将 ColorOS 缩略图规格映射为本地解码最大边长
     *
     * @param size ColorOS 请求的 small 或 large 尺寸
     * @return 本地解码最大边长
     */
    static int localThumbnailMaxDimension(String size) {
        return "large".equals(size)
                ? LARGE_THUMBNAIL_MAX_DIMENSION
                : SMALL_THUMBNAIL_MAX_DIMENSION;
    }

    /**
     * 下载特殊格式原文件到临时文件并生成 JPEG 缩略图
     *
     * @param catalog DSM 动态发现的 API 目录
     * @param sid 当前内存会话标识
     * @param media 待处理的远端图片
     * @param size ColorOS 请求的缩略图尺寸
     * @param output ColorOS 调用链提供的目标输出流
     * @throws IOException 下载或本地编码失败
     */
    private void generateLocalThumbnail(
            /* catalog 是 DSM 动态发现的 API 目录 */ DsmApiCatalog catalog,
            /* sid 是当前内存会话标识 */ String sid,
            /* media 是待处理的远端图片 */ RemoteMedia media,
            /* size 是 ColorOS 请求的缩略图尺寸 */ String size,
            /* output 是 ColorOS 调用链提供的目标输出流 */ OutputStream output
    ) throws IOException {
        // extension 用于不暴露远端路径的诊断日志
        String extension = extensionOf(media.name());
        // source 是仅在本次缩略图调用期间存在的临时源文件
        File source = File.createTempFile("synology-thumbnail-", ".source");
        try {
            Log.i(TAG, "DSM generate local thumbnail: extension=" + extension + ", size=" + size);
            // fileOutput 接收远端原图并在解码前关闭
            try (OutputStream fileOutput = new BufferedOutputStream(new FileOutputStream(source))) {
                download(catalog, sid, media, fileOutput);
            }
            LocalThumbnailGenerator.generate(source, localThumbnailMaxDimension(size), output);
        } finally {
            if (!source.delete() && source.exists()) {
                Log.w(TAG, "DSM local thumbnail temporary file cleanup failed");
            }
        }
    }

    /**
     * 删除指定远端图片并等待 DSM Delete v2 任务完成
     *
     * @param catalog DSM 动态发现的 API 目录
     * @param sid 当前内存会话标识
     * @param media 待删除的远端图片
     * @throws IOException 删除请求或任务等待失败
     */
    @Override
    public void delete(
            /* catalog 是 DSM 动态发现的 API 目录 */ DsmApiCatalog catalog,
            /* sid 是当前内存会话标识 */ String sid,
            /* media 是待删除的远端图片 */ List<RemoteMedia> media
    ) throws IOException {
        deleteOperation.delete(catalog, sid, media);
    }

    /**
     * 保留测试可见入口并解析删除启动任务标识
     *
     * @param response DSM 删除启动响应
     * @return 非空任务标识
     * @throws DsmException DSM 返回失败或缺少任务标识
     */
    static String parseDeleteTaskId(JSONObject response) throws DsmException {
        return DsmDeleteOperation.parseTaskId(response);
    }

    /**
     * 保留测试可见入口并解析删除任务完成标志
     *
     * @param response DSM 删除状态响应
     * @return 删除任务是否完成
     * @throws DsmException DSM 返回失败或缺少完成标志
     */
    static boolean parseDeleteFinished(JSONObject response) throws DsmException {
        return DsmDeleteOperation.parseFinished(response);
    }

    /**
     * 构建当前连接配置需要动态发现的 API 名称列表
     *
     * @param backupEnabled 当前是否开启照片备份
     * @return 逗号分隔的 SYNO.API.Info 查询值
     */
    static String discoveryQuery(boolean backupEnabled) {
        // query 是浏览、删除和系统信息链路必需的 API 名称
        String query = "SYNO.API.Auth,SYNO.FileStation.List,"
                + "SYNO.FileStation.Download,SYNO.FileStation.Thumb,"
                + "SYNO.FileStation.Delete,SYNO.Core.System";
        if (!backupEnabled) {
            return query;
        }
        return query + ",SYNO.FileStation.Upload,SYNO.FileStation.MD5";
    }

    /**
     * 校验保存并连接链路需要的浏览 API 与可选备份 API
     *
     * @param catalog 当前连接动态发现的 API 目录
     * @param backupEnabled 当前是否开启照片备份
     * @throws DsmException 必需 API 缺失或备份 API 不支持 v2
     */
    static void requireTestConnectionApis(
            /* catalog 是当前连接动态发现的 API 目录 */ DsmApiCatalog catalog,
            /* backupEnabled 表示当前是否开启照片备份 */ boolean backupEnabled
    ) throws DsmException {
        DsmMediaListing.requireApi(catalog);
        catalog.require("SYNO.FileStation.Download");
        catalog.require("SYNO.FileStation.Thumb");
        DsmDeleteOperation.requireApi(catalog);
        if (!backupEnabled) {
            return;
        }
        DsmBackupClient.requireVersion(catalog.require("SYNO.FileStation.Upload"));
        DsmBackupClient.requireVersion(catalog.require("SYNO.FileStation.MD5"));
    }

    /**
     * 提取小写扩展名供缩略图格式策略使用
     *
     * @param name 远端文件名
     * @return 不含点号的小写扩展名；无扩展名时为空字符串
     */
    private static String extensionOf(String name) {
        // dot 是文件名最后一个扩展名分隔符位置
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
