package com.jaxson.coloros.synologynas.backup;

public final class ColorOsFileHash {
    private static final String NATIVE_HASH_PATTERN = "[0-9a-f]{64}";

    private final String value;

    private ColorOsFileHash(String value) {
        this.value = value;
    }

    public static ColorOsFileHash parse(String value) {
        String nativeValue = value == null ? "" : value.trim();
        if (nativeValue.isEmpty()) {
            throw new IllegalArgumentException("ColorOS 照片哈希不能为空");
        }
        if (!nativeValue.matches(NATIVE_HASH_PATTERN)) {
            throw new IllegalArgumentException("ColorOS 照片哈希格式错误");
        }
        return new ColorOsFileHash(nativeValue);
    }

    public String value() {
        return value;
    }

    public String stableSuffix() {
        return value;
    }
}
