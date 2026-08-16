package com.jaxson.coloros.synologynas.dsm;

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
import java.util.Locale;

/** 统一承载 DSM 客户端重复的 HTTP 连接、响应读取与 JSON 校验 */
final class DsmHttpTransport {
    /** DSM 连接建立超时，保持原客户端的 15 秒约束 */
    private static final int CONNECT_TIMEOUT_MS = 15_000;
    /** DSM 响应读取超时，保持原客户端的 60 秒约束 */
    private static final int READ_TIMEOUT_MS = 60_000;

    /** 工具类不允许实例化 */
    private DsmHttpTransport() {
    }

    /**
     * 执行返回 JSON 的 DSM 请求，并保持非 2xx 与格式错误的原有失败语义
     *
     * @param method HTTP 方法
     * @param url 已编码的 DSM API 地址
     * @param formBody 可空的表单请求体
     * @return DSM JSON 响应
     * @throws IOException 网络或协议响应失败
     */
    static JSONObject executeJson(String method, String url, String formBody) throws IOException {
        // 当前请求使用的 HTTPS 连接，退出时必须断开
        HttpURLConnection connection = openConnection(method, url);
        try {
            if (formBody != null) {
                // UTF-8 表单字节用于固定长度写入，避免改变既有请求编码
                byte[] body = formBody.getBytes(StandardCharsets.UTF_8);
                connection.setDoOutput(true);
                connection.setRequestProperty(
                        "Content-Type",
                        "application/x-www-form-urlencoded; charset=UTF-8"
                );
                connection.setFixedLengthStreamingMode(body.length);
                // 缓冲输出流负责完整写出并关闭请求体
                try (OutputStream output = new BufferedOutputStream(connection.getOutputStream())) {
                    output.write(body);
                }
            }
            // HTTP 状态码决定读取正常响应或抛出包含错误体的异常
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw httpError(status, connection);
            }
            return parseJson("DSM", readText(connection.getInputStream()));
        } finally {
            connection.disconnect();
        }
    }

    /**
     * 将 DSM 文件响应直接流入调用方输出流，不在本地复制原图
     *
     * @param apiName 当前文件 API 名称，用于准确报告协议错误
     * @param url 已编码的 DSM 文件 API 地址
     * @param output 调用方持有的目标输出流
     * @throws IOException 网络、DSM API 或流复制失败
     */
    static void streamFileResponse(String apiName, String url, OutputStream output)
            throws IOException {
        // 文件下载固定使用 GET，并沿用严格 HTTPS 连接配置
        HttpURLConnection connection = openConnection("GET", url);
        try {
            // HTTP 状态码用于拒绝非成功文件响应
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw httpError(status, connection);
            }
            // JSON 内容类型表示 DSM 返回了 API 结果而不是文件字节
            String contentType = connection.getContentType();
            if (contentType != null && contentType.toLowerCase(Locale.ROOT).contains("json")) {
                // JSON 响应体用于保留 DSM 错误码和缺少文件内容的原有判断
                String body = readText(connection.getInputStream());
                // 已解析响应交给统一成功校验
                JSONObject response = parseJson(apiName, body);
                requireSuccess(apiName, response);
                throw new DsmException(apiName + " 未返回文件内容");
            }
            // 缓冲输入流直接复制到调用方输出流，避免远端原图落盘
            try (InputStream input = new BufferedInputStream(connection.getInputStream())) {
                input.transferTo(output);
            }
        } finally {
            connection.disconnect();
        }
    }

    /**
     * 创建使用 Android 默认 TLS 校验的 DSM HTTP 连接
     *
     * @param method HTTP 方法
     * @param url 由 {@link DsmUrlBuilder} 生成的 HTTPS 地址
     * @return 尚未发起请求的连接
     * @throws IOException 连接对象创建失败
     */
    static HttpURLConnection openConnection(String method, String url) throws IOException {
        // URLConnection 保持平台默认 TLS 证书与主机名校验
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Accept", "application/json");
        return connection;
    }

    /**
     * 按 HTTP 状态选择正常流或错误流并完整读取响应
     *
     * @param connection 已收到响应的 DSM 连接
     * @param status HTTP 状态码
     * @return UTF-8 响应正文；没有响应流时返回空字符串
     * @throws IOException 读取响应失败
     */
    static String readResponseBody(HttpURLConnection connection, int status) throws IOException {
        // 成功状态读取输入流，失败状态读取错误流
        InputStream stream = status >= 200 && status < 300
                ? connection.getInputStream()
                : connection.getErrorStream();
        if (stream == null) {
            return "";
        }
        // 缓冲输入流确保响应资源在读取后关闭
        try (InputStream input = new BufferedInputStream(stream)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * 将响应正文解析为 JSON，并把格式问题转换为明确 DSM 异常
     *
     * @param apiName 当前 DSM API 名称
     * @param body UTF-8 响应正文
     * @return JSON 响应对象
     * @throws DsmException 响应不是有效 JSON
     */
    static JSONObject parseJson(String apiName, String body) throws DsmException {
        try {
            return new JSONObject(body);
        } catch (/* JSON 解析失败原因 */ JSONException error) {
            throw new DsmException(apiName + " 未返回有效 JSON", error);
        }
    }

    /**
     * 校验 DSM JSON 成功标志，并保留原始错误码映射
     *
     * @param apiName 当前 DSM API 名称
     * @param response DSM JSON 响应
     * @throws DsmException DSM 明确返回失败
     */
    static void requireSuccess(String apiName, JSONObject response) throws DsmException {
        if (!response.optBoolean("success", false)) {
            throw DsmException.fromApiResponse(apiName, response);
        }
    }

    /**
     * 完整读取并关闭输入流
     *
     * @param input 待读取的响应流
     * @return UTF-8 文本
     * @throws IOException 读取失败
     */
    private static String readText(InputStream input) throws IOException {
        // closeable 通过 try-with-resources 保证正常与异常路径均关闭
        try (InputStream closeable = input) {
            return new String(closeable.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * 构造携带 HTTP 状态及错误正文的 DSM 异常
     *
     * @param status HTTP 状态码
     * @param connection 可读取错误流的 DSM 连接
     * @return 可直接抛出的 DSM 异常
     * @throws IOException 读取错误响应失败
     */
    private static DsmException httpError(int status, HttpURLConnection connection)
            throws IOException {
        // DSM 错误流可能为空，需保留原有空正文处理
        InputStream errorStream = connection.getErrorStream();
        // 错误正文仅在非空时追加到异常消息
        String body = errorStream == null ? "" : readText(errorStream);
        return new DsmException(
                "DSM HTTP 请求失败: " + status + (body.isBlank() ? "" : ", " + body)
        );
    }
}
