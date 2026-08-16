package com.jaxson.coloros.synologynas.dsm;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** 构建仅允许 HTTPS 且路径不可逃逸的 DSM webapi 地址 */
public final class DsmUrlBuilder {
    /** 工具类不允许实例化 */
    private DsmUrlBuilder() {
    }

    /**
     * 校验并规范化用户配置的 DSM HTTPS 基础地址
     *
     * @param input 用户输入的 DSM 地址
     * @return 去除根路径末尾斜杠后的 ASCII HTTPS 地址
     */
    public static String normalizeBaseUrl(String input) {
        try {
            // uri 是去除首尾空白后的用户 DSM 地址
            URI uri = new URI(input.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme())) {
                throw new IllegalArgumentException("DSM 地址必须使用 HTTPS");
            }
            if (uri.getHost() == null) {
                throw new IllegalArgumentException("DSM 地址缺少主机名");
            }
            if (uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException("DSM 地址不能包含凭据、查询参数或片段");
            }
            // path 是经过逃逸检查和末尾斜杠清理的反向代理基础路径
            String path = normalizeBasePath(uri.getPath());
            // normalized 强制方案为 HTTPS 并移除用户信息、查询和片段
            URI normalized = new URI(
                    "https",
                    null,
                    uri.getHost(),
                    uri.getPort(),
                    path,
                    null,
                    null
            );
            return normalized.toASCIIString();
        } catch (/* 用户地址不是合法 URI */ URISyntaxException error) {
            throw new IllegalArgumentException("DSM 地址格式错误", error);
        }
    }

    /**
     * 在可选反向代理基础路径下构建 DSM webapi 请求地址
     *
     * @param baseUrl 用户配置的 DSM HTTPS 基础地址
     * @param apiPath SYNO.API.Info 返回的 API 路径
     * @param parameters 待编码的查询参数
     * @return 完整 DSM API 地址
     */
    public static String build(
            /* baseUrl 是用户配置的 DSM HTTPS 基础地址 */ String baseUrl,
            /* apiPath 是 SYNO.API.Info 返回的 API 路径 */ String apiPath,
            /* parameters 是待编码的查询参数 */ Map<String, String> parameters
    ) {
        // normalizedBase 是已验证的 HTTPS 基础地址
        String normalizedBase = normalizeBaseUrl(baseUrl);
        // normalizedApiPath 是移除 webapi 前缀且不可逃逸的动态 API 路径
        String normalizedApiPath = normalizeApiPath(apiPath);
        try {
            // base 提供规范化主机、端口和反向代理路径
            URI base = new URI(normalizedBase);
            // basePath 是规范化后的可选反向代理路径
            String basePath = normalizeBasePath(base.getPath());
            // endpointPath 在基础路径下仅追加一次 webapi 段
            String endpointPath = (basePath.isEmpty() ? "" : basePath)
                    + "/webapi/"
                    + normalizedApiPath;
            // endpoint 是不含查询参数的严格 HTTPS API 地址
            URI endpoint = new URI(
                    "https",
                    null,
                    base.getHost(),
                    base.getPort(),
                    endpointPath,
                    null,
                    null
            );
            // query 是保持参数遍历顺序的百分号编码查询串
            String query = encodeParameters(parameters);
            return endpoint.toASCIIString() + (query.isEmpty() ? "" : "?" + query);
        } catch (/* 规范化地址无法重建为 URI */ URISyntaxException error) {
            throw new IllegalArgumentException("DSM API 地址构建失败", error);
        }
    }

    /**
     * 将请求参数按遍历顺序编码为 application/x-www-form-urlencoded 文本
     *
     * @param parameters 请求参数映射
     * @return 百分号编码的参数串
     */
    public static String encodeParameters(Map<String, String> parameters) {
        // result 按调用方映射迭代顺序累积参数
        StringBuilder result = new StringBuilder();
        for (/* 当前待编码的参数 */ Map.Entry<String, String> entry : parameters.entrySet()) {
            if (result.length() > 0) {
                result.append('&');
            }
            result.append(encode(entry.getKey()));
            result.append('=');
            result.append(encode(entry.getValue()));
        }
        return result.toString();
    }

    /**
     * 校验可选反向代理基础路径不可通过点段改变层级
     *
     * @param path URI 基础路径
     * @return 空路径或去除末尾斜杠后的绝对路径
     */
    private static String normalizeBasePath(String path) {
        if (path == null || path.isEmpty() || "/".equals(path)) {
            return "";
        }
        // normalized 用于检测原路径中的点段变化
        URI normalized = URI.create(path).normalize();
        if (!normalized.getPath().equals(path)) {
            throw new IllegalArgumentException("DSM 地址路径不能包含 . 或 ..");
        }
        // result 确保基础路径以斜杠开头并逐步移除末尾斜杠
        String result = path.startsWith("/") ? path : "/" + path;
        // 循环移除基础路径末尾的全部斜杠
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    /**
     * 规范化 DSM 发现路径并拒绝逃逸、绝对 URL、查询或片段
     *
     * @param apiPath SYNO.API.Info 返回的路径
     * @return 相对于 webapi 的路径
     */
    private static String normalizeApiPath(String apiPath) {
        // result 是去除首尾空白和前导斜杠的动态路径
        String result = apiPath == null ? "" : apiPath.trim();
        // 循环移除动态 API 路径的全部前导斜杠
        while (result.startsWith("/")) {
            result = result.substring(1);
        }
        if (result.startsWith("webapi/")) {
            result = result.substring("webapi/".length());
        }
        if (result.isEmpty()
                || result.contains("..")
                || result.indexOf(':') >= 0
                || result.indexOf('?') >= 0
                || result.indexOf('#') >= 0) {
            throw new IllegalArgumentException("DSM 返回了非法 API 路径");
        }
        return result;
    }

    /**
     * 以 UTF-8 编码单个查询参数并把空格固定为百分号形式
     *
     * @param value 原始参数文本
     * @return 百分号编码文本
     */
    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
