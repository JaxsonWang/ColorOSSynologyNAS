package com.jaxson.coloros.synologynas.backup;

public final class ColorOsFileHash {
    // 固定 ColorOS 当前原生照片哈希为 64 位小写十六进制 SHA-256
    private static final String NATIVE_HASH_PATTERN = "[0-9a-f]{64}";

    // 保存已经通过 ColorOS 原生格式校验的不透明哈希值
    private final String value;

    /**
     * 保存已经完成格式校验的 ColorOS 原生哈希
     *
     * @param value 64 位小写十六进制原生哈希
     */
    private ColorOsFileHash(String value) {
        this.value = value;
    }

    /**
     * 校验并封装 ColorOS 当前提供的 SHA-256 原生哈希
     *
     * @param value ColorOS 私有请求中的原生哈希文本
     * @return 保持原值的不透明哈希对象
     */
    public static ColorOsFileHash parse(String value) {
        // 保存去除首尾空白后的 ColorOS 原生哈希
        String nativeValue = value == null ? "" : value.trim();
        if (nativeValue.isEmpty()) {
            throw new IllegalArgumentException("ColorOS 照片哈希不能为空");
        }
        if (!nativeValue.matches(NATIVE_HASH_PATTERN)) {
            throw new IllegalArgumentException("ColorOS 照片哈希格式错误");
        }
        return new ColorOsFileHash(nativeValue);
    }

    /** @return 经过格式校验且保持原样的 ColorOS 原生哈希 */
    public String value() {
        return value;
    }

    /** @return 用于同名文件冲突路径的稳定完整哈希后缀 */
    public String stableSuffix() {
        return value;
    }
}
