package com.jaxson.coloros.synologynas;

import com.jaxson.coloros.synologynas.gallery.GalleryContract;

public final class HookPolicy {
    public static final String TARGET_PACKAGE = "com.coloros.gallery3d";
    public static final long TARGET_VERSION_CODE = 16050008L;
    public static final String FEINIU_FEATURE_FLAG = "feature_is_support_feiniu_nas";

    private HookPolicy() {
    }

    public static boolean isSupportedTarget(
            String packageName,
            String processName,
            long versionCode
    ) {
        return TARGET_PACKAGE.equals(packageName)
                && TARGET_PACKAGE.equals(processName)
                && versionCode == TARGET_VERSION_CODE;
    }

    public static boolean shouldForceFeature(String configId) {
        return FEINIU_FEATURE_FLAG.equals(configId);
    }

    public static boolean shouldOpenSynologyDeviceManager(int actionFlags) {
        return actionFlags == 20;
    }

    public static boolean shouldSuppressSynologyDeleteMessage(String deviceUserId) {
        return GalleryContract.DEVICE_ID.equals(deviceUserId);
    }

    public static String rewriteNasLabel(String value) {
        if ("飞牛 NAS".equals(value) || "FeiNiu NAS".equals(value)) {
            return "群晖 NAS";
        }
        return value;
    }

    public static String rewriteNasStateMessage(String value) {
        if (value != null && (value.contains("飞牛") || value.contains("FeiNiu"))) {
            return "群晖 NAS 图片已接入私有云图集，可直接浏览和下载";
        }
        return value;
    }
}
