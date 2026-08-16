package com.jaxson.coloros.synologynas.dsm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class DsmThumbnailPolicyTest {
    @Test
    public void locallyGeneratesFormatsUnsupportedByFileStationThumb() {
        assertTrue(DsmClient.requiresLocalThumbnail(media("photo.HEIC", "image/heic")));
        assertTrue(DsmClient.requiresLocalThumbnail(media("photo.heif", "image/heif")));
        assertTrue(DsmClient.requiresLocalThumbnail(media("photo.avif", "image/avif")));
        assertTrue(DsmClient.requiresLocalThumbnail(media("photo.webp", "image/webp")));
    }

    @Test
    public void keepsDsmThumbnailApiForDocumentedFormats() {
        assertFalse(DsmClient.requiresLocalThumbnail(media("photo.jpg", "image/jpeg")));
        assertFalse(DsmClient.requiresLocalThumbnail(media("photo.jpeg", "image/jpeg")));
        assertFalse(DsmClient.requiresLocalThumbnail(media("photo.png", "image/png")));
        assertFalse(DsmClient.requiresLocalThumbnail(media("photo.gif", "image/gif")));
        assertFalse(DsmClient.requiresLocalThumbnail(media("photo.bmp", "image/bmp")));
        assertFalse(DsmClient.requiresLocalThumbnail(media("photo.dng", "image/x-adobe-dng")));
    }

    @Test
    public void mapsColorOsThumbnailSizesToBoundedDecodeDimensions() {
        assertEquals(512, DsmClient.localThumbnailMaxDimension("small"));
        assertEquals(1_280, DsmClient.localThumbnailMaxDimension("large"));
    }

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
