package com.jaxson.coloros.synologynas.dsm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** 锁定真机验收过的 DSM 与本地缩略图格式分流策略 */
public final class DsmThumbnailPolicyTest {
    /** 验证 File Station Thumb 不支持的格式继续本地生成 JPEG */
    @Test
    public void locallyGeneratesFormatsUnsupportedByFileStationThumb() {
        assertTrue(DsmClient.requiresLocalThumbnail(media("photo.HEIC", "image/heic")));
        assertTrue(DsmClient.requiresLocalThumbnail(media("photo.heif", "image/heif")));
        assertTrue(DsmClient.requiresLocalThumbnail(media("photo.avif", "image/avif")));
        assertTrue(DsmClient.requiresLocalThumbnail(media("photo.webp", "image/webp")));
    }

    /** 验证已由 File Station Thumb 支持的格式不转入本地解码 */
    @Test
    public void keepsDsmThumbnailApiForDocumentedFormats() {
        assertFalse(DsmClient.requiresLocalThumbnail(media("photo.jpg", "image/jpeg")));
        assertFalse(DsmClient.requiresLocalThumbnail(media("photo.jpeg", "image/jpeg")));
        assertFalse(DsmClient.requiresLocalThumbnail(media("photo.png", "image/png")));
        assertFalse(DsmClient.requiresLocalThumbnail(media("photo.gif", "image/gif")));
        assertFalse(DsmClient.requiresLocalThumbnail(media("photo.bmp", "image/bmp")));
        assertFalse(DsmClient.requiresLocalThumbnail(media("photo.dng", "image/x-adobe-dng")));
    }

    /** 验证 ColorOS small 与 large 规格保持已验收的有界解码尺寸 */
    @Test
    public void mapsColorOsThumbnailSizesToBoundedDecodeDimensions() {
        assertEquals(512, DsmClient.localThumbnailMaxDimension("small"));
        assertEquals(1_280, DsmClient.localThumbnailMaxDimension("large"));
    }

    /**
     * 创建仅供格式分流测试使用的远端图片
     *
     * @param name 带目标扩展名的文件名
     * @param mimeType 与文件名对应的 MIME 类型
     * @return 最小远端图片样本
     */
    private static RemoteMedia media(String name, String mimeType) {
        return new RemoteMedia(
                "/home/Photos/" + name,
                name,
                1024L,
                100L,
                mimeType
        );
    }
}
