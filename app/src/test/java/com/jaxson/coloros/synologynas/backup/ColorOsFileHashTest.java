package com.jaxson.coloros.synologynas.backup;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public final class ColorOsFileHashTest {
    private static final String NATIVE_HASH = "0123456789abcdef0123456789abcdef"
            + "fedcba9876543210fedcba9876543210";

    @Test
    public void preservesCurrentColorOsSha256HashAsOpaqueValue() {
        ColorOsFileHash hash = ColorOsFileHash.parse(NATIVE_HASH);

        assertEquals(NATIVE_HASH, hash.value());
        assertEquals(NATIVE_HASH, hash.stableSuffix());
    }

    @Test
    public void rejectsLegacyMd5Shape() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ColorOsFileHash.parse("0123456789abcdef0123456789abcdef")
        );
    }
}
