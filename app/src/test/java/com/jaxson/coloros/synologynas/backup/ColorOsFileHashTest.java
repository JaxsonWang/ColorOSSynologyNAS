package com.jaxson.coloros.synologynas.backup;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public final class ColorOsFileHashTest {
    // 提供覆盖当前 ColorOS 64 位小写十六进制 SHA-256 合约的固定样本
    private static final String NATIVE_HASH = "0123456789abcdef0123456789abcdef"
            + "fedcba9876543210fedcba9876543210";

    /** 验证 ColorOS SHA-256 原生哈希作为不透明值保持不变 */
    @Test
    public void preservesCurrentColorOsSha256HashAsOpaqueValue() {
        // 解析当前 ColorOS 私有备份请求使用的原生哈希样本
        ColorOsFileHash hash = ColorOsFileHash.parse(NATIVE_HASH);

        assertEquals(NATIVE_HASH, hash.value());
        assertEquals(NATIVE_HASH, hash.stableSuffix());
    }

    /** 验证旧 32 位 MD5 形状不会被误当成 ColorOS 当前原生哈希 */
    @Test
    public void rejectsLegacyMd5Shape() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ColorOsFileHash.parse("0123456789abcdef0123456789abcdef")
        );
    }
}
