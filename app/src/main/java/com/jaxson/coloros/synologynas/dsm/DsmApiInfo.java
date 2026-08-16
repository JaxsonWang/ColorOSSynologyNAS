package com.jaxson.coloros.synologynas.dsm;

/** 描述 DSM 动态发现的单个 API 路径与版本范围 */
public final class DsmApiInfo {
    /** DSM API 名称 */
    private final String name;
    /** DSM 返回的相对 webapi 路径 */
    private final String path;
    /** DSM 支持的最小 API 版本 */
    private final int minVersion;
    /** DSM 支持的最大 API 版本 */
    private final int maxVersion;

    /**
     * 创建经过基本合同校验的 DSM API 描述
     *
     * @param name DSM API 名称
     * @param path DSM 返回的相对 API 路径
     * @param minVersion 最小支持版本
     * @param maxVersion 最大支持版本
     */
    public DsmApiInfo(String name, String path, int minVersion, int maxVersion) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("DSM API name is empty");
        }
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("DSM API path is empty: " + name);
        }
        if (minVersion <= 0 || maxVersion < minVersion) {
            throw new IllegalArgumentException("Invalid DSM API version range: " + name);
        }
        this.name = name;
        this.path = path;
        this.minVersion = minVersion;
        this.maxVersion = maxVersion;
    }

    /** @return DSM API 名称 */
    public String name() {
        return name;
    }

    /** @return DSM 返回的相对 API 路径 */
    public String path() {
        return path;
    }

    /** @return DSM 支持的最小 API 版本 */
    public int minVersion() {
        return minVersion;
    }

    /** @return DSM 支持的最大 API 版本 */
    public int maxVersion() {
        return maxVersion;
    }
}
