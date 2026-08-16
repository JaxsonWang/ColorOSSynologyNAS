package com.jaxson.coloros.synologynas.dsm;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class DsmUrlBuilder {
    private DsmUrlBuilder() {
    }

    public static String normalizeBaseUrl(String input) {
        try {
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
            String path = normalizeBasePath(uri.getPath());
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
        } catch (URISyntaxException error) {
            throw new IllegalArgumentException("DSM 地址格式错误", error);
        }
    }

    public static String build(String baseUrl, String apiPath, Map<String, String> parameters) {
        String normalizedBase = normalizeBaseUrl(baseUrl);
        String normalizedApiPath = normalizeApiPath(apiPath);
        try {
            URI base = new URI(normalizedBase);
            String basePath = normalizeBasePath(base.getPath());
            String endpointPath = (basePath.isEmpty() ? "" : basePath)
                    + "/webapi/"
                    + normalizedApiPath;
            URI endpoint = new URI(
                    "https",
                    null,
                    base.getHost(),
                    base.getPort(),
                    endpointPath,
                    null,
                    null
            );
            String query = encodeParameters(parameters);
            return endpoint.toASCIIString() + (query.isEmpty() ? "" : "?" + query);
        } catch (URISyntaxException error) {
            throw new IllegalArgumentException("DSM API 地址构建失败", error);
        }
    }

    public static String encodeParameters(Map<String, String> parameters) {
        StringBuilder result = new StringBuilder();
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            if (result.length() > 0) {
                result.append('&');
            }
            result.append(encode(entry.getKey()));
            result.append('=');
            result.append(encode(entry.getValue()));
        }
        return result.toString();
    }

    private static String normalizeBasePath(String path) {
        if (path == null || path.isEmpty() || "/".equals(path)) {
            return "";
        }
        URI normalized = URI.create(path).normalize();
        if (!normalized.getPath().equals(path)) {
            throw new IllegalArgumentException("DSM 地址路径不能包含 . 或 ..");
        }
        String result = path.startsWith("/") ? path : "/" + path;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private static String normalizeApiPath(String apiPath) {
        String result = apiPath == null ? "" : apiPath.trim();
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

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
