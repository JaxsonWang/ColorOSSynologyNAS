package com.jaxson.coloros.synologynas;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * 验证 Hook 目标门和设备分流策略始终保持精确匹配
 */
public final class HookPolicyTest {
    /**
     * 验证只有固定相册包、主进程和构建版本的完整组合可安装 Hook
     */
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

    /**
     * 验证模块只强制开启相册既有的飞牛 NAS 页面能力开关
     */
    @Test
    public void forcesOnlyFeiniuFeatureFlag() {
        assertTrue(HookPolicy.shouldForceFeature("feature_is_support_feiniu_nas"));
        assertFalse(HookPolicy.shouldForceFeature("feature_is_support_cloud_sync"));
        assertFalse(HookPolicy.shouldForceFeature(null));
    }

    /**
     * 验证只有精确飞牛 NAS 品牌短文案会被替换
     */
    @Test
    public void rewritesOnlyExactFeiniuNasLabels() {
        assertEquals("群晖 NAS", HookPolicy.rewriteNasLabel("飞牛 NAS"));
        assertEquals("群晖 NAS", HookPolicy.rewriteNasLabel("FeiNiu NAS"));
        assertEquals("相机", HookPolicy.rewriteNasLabel("相机"));
        assertEquals(null, HookPolicy.rewriteNasLabel(null));
    }

    /**
     * 验证只有包含飞牛品牌的 NAS 状态说明会被替换
     */
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

    /**
     * 验证只有当前版本确认的设备管理动作标记会打开模块配置页
     */
    @Test
    public void opensOnlyDeviceManagementActionInModuleActivity() {
        assertTrue(HookPolicy.shouldOpenSynologyDeviceManager(20));
        assertFalse(HookPolicy.shouldOpenSynologyDeviceManager(16));
        assertFalse(HookPolicy.shouldOpenSynologyDeviceManager(0));
    }

    /**
     * 验证只有群晖删除请求会抑制不适用的飞牛回收站正文
     */
    @Test
    public void suppressesDeleteMessageOnlyForSynologyDevice() {
        assertTrue(HookPolicy.shouldSuppressSynologyDeleteMessage("synology-dsm7"));
        assertFalse(HookPolicy.shouldSuppressSynologyDeleteMessage("feiniu-nas"));
        assertFalse(HookPolicy.shouldSuppressSynologyDeleteMessage(null));
    }

    /**
     * 验证其他 NAS 卡片的 availability 不会改写群晖全局连接状态
     */
    @Test
    public void appliesAvailabilityOnlyForSynologyCard() {
        assertTrue(HookPolicy.shouldApplySynologyAvailability("synology-dsm7"));
        assertFalse(HookPolicy.shouldApplySynologyAvailability("feiniu-nas"));
        assertFalse(HookPolicy.shouldApplySynologyAvailability(null));
    }
}
