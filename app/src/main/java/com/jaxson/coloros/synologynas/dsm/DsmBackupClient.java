package com.jaxson.coloros.synologynas.dsm;

import com.jaxson.coloros.synologynas.SynologyConfig;
import com.jaxson.coloros.synologynas.backup.BackupPath;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** DSM 备份客户端，负责远端 MD5 判断与 File Station 流式上传 */
public final class DsmBackupClient implements DsmBackupGateway {
    /** File Station MD5 与 Upload 合同固定使用 v2 */
    private static final int FILE_STATION_API_VERSION = 2;
    /** MD5 异步任务状态轮询间隔，保持原有 250 毫秒 */
    private static final long MD5_POLL_INTERVAL_MS = 250L;
    /** MD5 异步任务最长等待时间，保持原有 60 秒 */
    private static final long MD5_TASK_TIMEOUT_MS = 60_000L;

    /** 当前 DSM 地址与认证配置 */
    private final SynologyConfig config;

    /**
     * 创建绑定当前配置的 DSM 备份客户端
     *
     * @param config 当前已发布的 DSM 配置
     */
    public DsmBackupClient(SynologyConfig config) {
        this.config = config;
    }

    /**
     * 动态发现备份链路所需的认证、上传和 MD5 API，并验证 v2 范围
     *
     * @return DSM API 目录
     * @throws IOException 请求、响应解析或版本校验失败
     */
    @Override
    public DsmApiCatalog discoverApis() throws IOException {
        // parameters 明确列出备份链路需要发现的全部 API
        Map<String, String> parameters = DsmParameters.of(
                "api", "SYNO.API.Info",
                "version", "1",
                "method", "query",
                "query", "SYNO.API.Auth,SYNO.FileStation.Upload,SYNO.FileStation.MD5"
        );
        // url 指向 DSM 固定的 API 发现入口
        String url = DsmUrlBuilder.build(config.serverUrl(), "query.cgi", parameters);
        // catalog 保存 DSM 返回的动态路径与版本范围
        DsmApiCatalog catalog = DsmApiInfoParser.parse(
                DsmHttpTransport.executeJson("GET", url, null).toString()
        );
        requireVersion(catalog.require("SYNO.FileStation.Upload"));
        requireVersion(catalog.require("SYNO.FileStation.MD5"));
        return catalog;
    }

    /**
     * 使用账号、密码和可选 OTP 登录备份会话
     *
     * @param catalog DSM 动态发现的 API 目录
     * @return 仅供当前备份调用链持有的 SID
     * @throws IOException 登录请求或响应失败
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
                "session", DsmClient.SESSION_NAME,
                "format", "sid"
        );
        if (!config.otp().isEmpty()) {
            parameters.put("otp_code", config.otp());
        }
        // url 使用发现的认证路径，凭据只进入 POST 表单体
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
     * 注销当前备份 SID 并严格校验 Auth logout 响应
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
        // url 使用发现的认证路径且不把 SID 写入查询字符串
        String url = DsmUrlBuilder.build(config.serverUrl(), auth.path(), Map.of());
        // response 是 DSM 注销响应
        JSONObject response = DsmHttpTransport.executeJson(
                "POST",
                url,
                DsmUrlBuilder.encodeParameters(DsmClient.logoutParameters(auth, sid))
        );
        DsmHttpTransport.requireSuccess(auth.name(), response);
    }

    /**
     * 查询远端文件 MD5；远端文件或父目录不存在时返回空结果
     *
     * @param catalog DSM 动态发现的 API 目录
     * @param sid 当前内存会话标识
     * @param remotePath 待查询的完整远端路径
     * @return 规范化小写 MD5；目标不存在时为空
     * @throws IOException DSM 请求、任务状态或响应格式失败
     */
    @Override
    public Optional<String> md5(
            /* catalog 是 DSM 动态发现的 API 目录 */ DsmApiCatalog catalog,
            /* sid 是当前内存会话标识 */ String sid,
            /* remotePath 是待查询的完整远端路径 */ String remotePath
    ) throws IOException {
        // md5Api 是动态发现且明确支持 v2 的 MD5 API
        DsmApiInfo md5Api = catalog.require("SYNO.FileStation.MD5");
        requireVersion(md5Api);
        // startParameters 完整表达 MD5 任务启动合同
        Map<String, String> startParameters = DsmParameters.of(
                "api", md5Api.name(),
                "version", Integer.toString(FILE_STATION_API_VERSION),
                "method", "start",
                "file_path", fileStationStringParameter(remotePath),
                "_sid", sid
        );
        // startUrl 是带 JSON 字符串路径参数的任务启动地址
        String startUrl = DsmUrlBuilder.build(config.serverUrl(), md5Api.path(), startParameters);
        // startResponse 是 MD5 任务启动响应
        JSONObject startResponse = DsmHttpTransport.executeJson("GET", startUrl, null);
        if (!DsmHttpTransport.readSuccess(md5Api.name(), startResponse)) {
            if (isMissingMd5TargetError(
                    DsmException.requireApiErrorCode(md5Api.name(), startResponse)
            )) {
                return Optional.empty();
            }
            throw DsmException.fromApiResponse(md5Api.name(), startResponse);
        }
        // taskId 是严格读取并规范化后的 MD5 任务标识
        String taskId = parseTaskId(md5Api.name(), startResponse);

        // deadlineNanos 使用单调时钟限定 MD5 任务等待时间
        long deadlineNanos = System.nanoTime() + MD5_TASK_TIMEOUT_MS * 1_000_000L;
        while (true) {
            // statusParameters 完整表达 MD5 状态查询合同
            Map<String, String> statusParameters = DsmParameters.of(
                    "api", md5Api.name(),
                    "version", Integer.toString(FILE_STATION_API_VERSION),
                    "method", "status",
                    "taskid", fileStationStringParameter(taskId),
                    "_sid", sid
            );
            // statusUrl 是当前 MD5 任务状态查询地址
            String statusUrl = DsmUrlBuilder.build(
                    config.serverUrl(),
                    md5Api.path(),
                    statusParameters
            );
            // statusResponse 是 MD5 任务状态响应
            JSONObject statusResponse = DsmHttpTransport.executeJson("GET", statusUrl, null);
            DsmHttpTransport.requireSuccess(md5Api.name(), statusResponse);
            // data 是 MD5 状态响应的数据对象
            JSONObject data = statusResponse.optJSONObject("data");
            if (parseMd5Finished(md5Api.name(), data)) {
                return Optional.of(parseMd5Hash(md5Api.name(), data));
            }
            if (System.nanoTime() >= deadlineNanos) {
                throw new DsmException("群晖 MD5 任务等待超时");
            }
            sleepForMd5Poll();
        }
    }

    /**
     * 以固定长度 multipart 请求流式上传本机照片，并仅接受 DSM success JSON
     *
     * @param catalog DSM 动态发现的 API 目录
     * @param sid 当前内存会话标识
     * @param path 远端备份目录与文件名
     * @param fileSize MediaStore 声明的照片字节数
     * @param input 本机照片输入流
     * @return DSM 明确成功后的已上传字节数
     * @throws IOException 本机读取、网络、DSM 协议或业务失败
     */
    @Override
    public long upload(
            /* catalog 是 DSM 动态发现的 API 目录 */ DsmApiCatalog catalog,
            /* sid 是当前内存会话标识 */ String sid,
            /* path 是远端备份目录与文件名 */ BackupPath path,
            /* fileSize 是 MediaStore 声明的照片字节数 */ long fileSize,
            /* input 是本机照片输入流 */ InputStream input
    ) throws IOException {
        // uploadApi 是动态发现且明确支持 v2 的上传 API
        DsmApiInfo uploadApi = catalog.require("SYNO.FileStation.Upload");
        requireVersion(uploadApi);
        // url 只携带 SID，其他上传字段放在 multipart 请求体中
        String url = DsmUrlBuilder.build(
                config.serverUrl(),
                uploadApi.path(),
                Map.of("_sid", sid)
        );
        // boundary 是本次 multipart 请求的唯一边界
        String boundary = "ColorOSSynologyNAS-" + UUID.randomUUID();
        // prefix 是文件内容之前的所有 multipart 字段
        byte[] prefix = multipartPrefix(boundary, path.folder(), path.fileName());
        // suffix 是文件内容之后的 multipart 结束边界
        byte[] suffix = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
        // contentLength 是固定长度上传所需的完整请求体字节数
        long contentLength;
        try {
            contentLength = Math.addExact(Math.addExact(prefix.length, fileSize), suffix.length);
        } catch (/* 上传长度整数溢出 */ ArithmeticException error) {
            throw new DsmBackupReadException("照片大小超出 DSM 上传范围", error);
        }

        // connection 使用平台默认 TLS 严格校验并在退出时断开
        HttpURLConnection connection = DsmHttpTransport.openConnection("POST", url);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        connection.setFixedLengthStreamingMode(contentLength);
        try {
            // output 按“前缀、精确文件字节、后缀”顺序写出请求体
            try (OutputStream output = new BufferedOutputStream(connection.getOutputStream())) {
                output.write(prefix);
                DsmUploadBody.copyExact(input, output, fileSize);
                output.write(suffix);
            }
            // status 是 DSM 上传 HTTP 状态码
            int status = connection.getResponseCode();
            // body 是成功输入流或失败错误流的完整正文
            String body = DsmHttpTransport.readResponseBody(connection, status);
            if (status < 200 || status >= 300) {
                throw new DsmException(
                        "DSM HTTP 请求失败: " + status
                                + (body.isBlank() ? "" : ", " + body)
                );
            }
            return parseUploadResponse(uploadApi.name(), body, path, fileSize);
        } finally {
            connection.disconnect();
        }
    }

    /**
     * 把 File Station 字符串参数编码为官方要求的 JSON 字符串
     *
     * @param value 原始文件路径或任务标识
     * @return 带 JSON 双引号和转义的字符串
     */
    static String fileStationStringParameter(String value) {
        return JSONObject.quote(value);
    }

    /**
     * 判断 MD5 目标确实因文件或 create_parents 父目录不存在而缺失
     *
     * @param code DSM File Station 错误码
     * @return 是否应表达为远端 MD5 目标不存在
     */
    static boolean isMissingMd5TargetError(int code) {
        // 408 表示文件不存在，418 表示 create_parents 尚未创建父目录
        return code == 408 || code == 418;
    }

    /**
     * 保留测试可见入口并生成上传文件内容之前的 multipart 合同
     *
     * @param boundary 当前上传边界
     * @param folder 远端目标目录
     * @param fileName 远端文件名
     * @return 文件内容之前的 multipart 字节
     */
    static byte[] multipartPrefix(String boundary, String folder, String fileName) {
        return DsmUploadBody.prefix(boundary, folder, fileName);
    }

    /**
     * 解析上传响应；HTTP 2xx 仍必须含有 DSM success JSON 才能确认成功
     *
     * @param apiName 动态发现的上传 API 名称
     * @param body DSM 上传响应正文
     * @param path 当前远端备份路径
     * @param fileSize 已完整写入请求体的照片字节数
     * @return DSM 明确成功后的已上传字节数
     * @throws IOException 响应为空、格式错误、冲突或 DSM 业务失败
     */
    static long parseUploadResponse(
            /* apiName 是动态发现的上传 API 名称 */ String apiName,
            /* body 是 DSM 上传响应正文 */ String body,
            /* path 是当前远端备份路径 */ BackupPath path,
            /* fileSize 是已完整写入请求体的照片字节数 */ long fileSize
    ) throws IOException {
        // response 必须是有效 DSM JSON，空正文不得伪造上传成功
        JSONObject response = DsmHttpTransport.parseJson(apiName, body);
        if (!DsmHttpTransport.readSuccess(apiName, response)) {
            // code 是 DSM 上传业务错误码
            int code = DsmException.requireApiErrorCode(apiName, response);
            if (code == 414 || code == 1805) {
                throw new DsmRemoteFileAlreadyExistsException(
                        "群晖备份文件已存在: " + path.remotePath()
                );
            }
            throw DsmException.fromApiResponse(apiName, response);
        }
        return fileSize;
    }

    /**
     * 严格解析 MD5 启动响应中的任务标识
     *
     * @param apiName 动态发现的 MD5 API 名称
     * @param response MD5 启动响应
     * @return 规范化后的非空任务标识
     * @throws DsmException data 或 taskid 缺失、类型错误或值为空
     */
    static String parseTaskId(String apiName, JSONObject response) throws DsmException {
        try {
            // data 是 MD5 启动响应中必须保存任务标识的对象
            JSONObject data = response.getJSONObject("data");
            if (!data.has("taskid")) {
                throw new DsmException(apiName + " 启动响应缺少 taskid");
            }
            // value 是未经 JSONObject 字符串转换的任务标识原始值
            Object value = data.get("taskid");
            if (!(value instanceof String taskIdValue /* 已确认的字符串任务标识 */)) {
                throw new DsmException(apiName + " 启动响应 taskid 类型错误");
            }
            // taskId 是严格读取并规范化后的任务标识
            String taskId = taskIdValue.trim();
            if (taskId.isEmpty()) {
                throw new DsmException(apiName + " 启动响应缺少 taskid");
            }
            return taskId;
        } catch (/* MD5 data 或 taskid 字段格式错误 */ JSONException error) {
            throw new DsmException(apiName + " 启动响应格式错误", error);
        }
    }

    /**
     * 严格解析已完成 MD5 状态中的摘要
     *
     * @param apiName 动态发现的 MD5 API 名称
     * @param data MD5 状态响应中的 data 对象
     * @return 规范化小写 MD5
     * @throws DsmException md5 缺失、类型错误或格式非法
     */
    static String parseMd5Hash(String apiName, JSONObject data) throws DsmException {
        try {
            if (!data.has("md5")) {
                throw new DsmException(apiName + " 状态响应缺少有效 MD5");
            }
            // value 是未经 JSONObject 字符串转换的 MD5 原始值
            Object value = data.get("md5");
            if (!(value instanceof String hashValue /* 已确认的字符串 MD5 */)) {
                throw new DsmException(apiName + " 状态响应 MD5 类型错误");
            }
            // hash 是 DSM 返回并规范化的小写 MD5
            String hash = hashValue.trim().toLowerCase(Locale.ROOT);
            if (!hash.matches("[0-9a-f]{32}")) {
                throw new DsmException(apiName + " 状态响应缺少有效 MD5");
            }
            return hash;
        } catch (/* MD5 摘要字段缺失或类型错误 */ JSONException error) {
            throw new DsmException(apiName + " 状态响应格式错误", error);
        }
    }

    /**
     * 严格解析 MD5 状态响应的完成标志
     *
     * @param apiName 动态发现的 MD5 API 名称
     * @param data MD5 状态响应中的 data 对象
     * @return MD5 任务是否完成
     * @throws DsmException data 缺失、finished 缺失或类型错误
     */
    static boolean parseMd5Finished(String apiName, JSONObject data) throws DsmException {
        if (data == null || !data.has("finished")) {
            throw new DsmException(apiName + " 状态响应缺少 finished");
        }
        // finished 是尚未转换的任务完成标志
        Object finished = data.opt("finished");
        if (!(finished instanceof Boolean)) {
            throw new DsmException(apiName + " 状态响应 finished 类型错误");
        }
        return (Boolean) finished;
    }

    /**
     * 校验动态发现的 File Station API 明确覆盖 v2
     *
     * @param api 待校验的动态 API 描述
     * @throws DsmException API 不支持 v2
     */
    static void requireVersion(DsmApiInfo api) throws DsmException {
        if (api.minVersion() > FILE_STATION_API_VERSION
                || api.maxVersion() < FILE_STATION_API_VERSION) {
            throw new DsmException(api.name() + " 未提供 v" + FILE_STATION_API_VERSION);
        }
    }

    /**
     * 等待下一次 MD5 状态查询，并把线程中断转换为明确失败
     *
     * @throws DsmException 当前线程在等待期间被中断
     */
    private static void sleepForMd5Poll() throws DsmException {
        try {
            Thread.sleep(MD5_POLL_INTERVAL_MS);
        } catch (/* MD5 任务等待期间的线程中断 */ InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new DsmException("群晖 MD5 任务被中断", error);
        }
    }
}
