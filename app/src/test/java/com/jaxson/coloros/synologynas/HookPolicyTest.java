package com.jaxson.coloros.synologynas;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class HookPolicyTest {
    @Test
    public void supportsOnlyExactGalleryMainProcessAndVersion() {
        assertTrue(HookPolicy.isSupportedTarget(
                "com.coloros.gallery3d",
                "com.coloros.gallery3d",
                16050008L
        ));
        assertFalse(HookPolicy.isSupportedTarget(
                "com.coloros.gallery3d",
                "com.coloros.gallery3d:remote",
                16050008L
        ));
        assertFalse(HookPolicy.isSupportedTarget(
                "com.coloros.gallery3d",
                "com.coloros.gallery3d",
                16050009L
        ));
        assertFalse(HookPolicy.isSupportedTarget(
                "com.android.systemui",
                "com.android.systemui",
                16050008L
        ));
    }

    @Test
    public void forcesOnlyFeiniuFeatureFlag() {
        assertTrue(HookPolicy.shouldForceFeature("feature_is_support_feiniu_nas"));
        assertFalse(HookPolicy.shouldForceFeature("feature_is_support_cloud_sync"));
        assertFalse(HookPolicy.shouldForceFeature(null));
    }

    @Test
    public void rewritesOnlyExactFeiniuNasLabels() {
        assertEquals("群晖 NAS", HookPolicy.rewriteNasLabel("飞牛 NAS"));
        assertEquals("群晖 NAS", HookPolicy.rewriteNasLabel("FeiNiu NAS"));
        assertEquals("相机", HookPolicy.rewriteNasLabel("相机"));
        assertEquals(null, HookPolicy.rewriteNasLabel(null));
    }

    @Test
    public void rewritesOnlyFeiniuNasStateMessages() {
        assertEquals(
                "群晖 NAS 图片已接入私有云图集，可直接浏览和下载",
                HookPolicy.rewriteNasStateMessage(
                        "升级飞牛设备系统后，可开启自动备份，节省手机空间"
                )
        );
        assertEquals(
                "群晖 NAS 图片已接入私有云图集，可直接浏览和下载",
                HookPolicy.rewriteNasStateMessage("Update FeiNiu NAS to enable backup")
        );
        assertEquals("网络不可用", HookPolicy.rewriteNasStateMessage("网络不可用"));
        assertEquals(null, HookPolicy.rewriteNasStateMessage(null));
    }

    @Test
    public void opensOnlyDeviceManagementActionInModuleActivity() {
        assertTrue(HookPolicy.shouldOpenSynologyDeviceManager(20));
        assertFalse(HookPolicy.shouldOpenSynologyDeviceManager(16));
        assertFalse(HookPolicy.shouldOpenSynologyDeviceManager(0));
    }

    @Test
    public void suppressesDeleteMessageOnlyForSynologyDevice() {
        assertTrue(HookPolicy.shouldSuppressSynologyDeleteMessage("synology-dsm7"));
        assertFalse(HookPolicy.shouldSuppressSynologyDeleteMessage("feiniu-nas"));
        assertFalse(HookPolicy.shouldSuppressSynologyDeleteMessage(null));
    }
}
