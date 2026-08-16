package com.jaxson.coloros.synologynas;

import com.jaxson.coloros.synologynas.gallery.GalleryContract;

/**
 * 集中表达不依赖 Android 运行时的 Hook 目标门与分流规则
 */
public final class HookPolicy {
    // 限定模块唯一允许安装目标 Hook 的 ColorOS 相册包与主进程
    public static final String TARGET_PACKAGE = "com.coloros.gallery3d";
    // 限定私有反射合约已验证的 ColorOS 相册精确构建版本
    public static final long TARGET_VERSION_CODE = 16050008L;
    // 限定只强制开启相册原有飞牛 NAS 页面体系的能力开关
    public static final String FEINIU_FEATURE_FLAG = "feature_is_support_feiniu_nas";

    /**
     * 阻止创建无状态策略类实例
     */
    private HookPolicy() {
    }

    /**
     * 要求包名、进程名和相册构建版本同时精确匹配当前私有合约
     *
     * @param packageName 当前加载的 Android 包名
     * @param processName 当前模块实例所在进程名
     * @param versionCode 当前安装的相册 longVersionCode
     * @return 三项目标信息完全匹配时返回 true
     */
    public static boolean isSupportedTarget(
            String packageName,
            String processName,
            long versionCode
    ) {
        return TARGET_PACKAGE.equals(packageName)
                && TARGET_PACKAGE.equals(processName)
                && versionCode == TARGET_VERSION_CODE;
    }

    /**
     * 仅强制开启相册既有的飞牛 NAS 页面能力开关
     *
     * @param configId 相册正在读取的能力配置标识
     * @return 当前配置正是 NAS 页面能力开关时返回 true
     */
    public static boolean shouldForceFeature(String configId) {
        return FEINIU_FEATURE_FLAG.equals(configId);
    }

    /**
     * 仅将当前版本确认的设备管理动作导向模块配置页
     *
     * @param actionFlags 相册 NAS 入口方法传入的动作标记
     * @return 动作标记等于当前设备管理值 20 时返回 true
     */
    public static boolean shouldOpenSynologyDeviceManager(int actionFlags) {
        return actionFlags == 20;
    }

    /**
     * 仅在群晖删除请求中抑制不适用的飞牛回收站正文
     *
     * @param deviceUserId 当前删除请求恢复出的 NAS 设备标识
     * @return 当前请求属于唯一群晖合成设备时返回 true
     */
    public static boolean shouldSuppressSynologyDeleteMessage(String deviceUserId) {
        return isSynologyDevice(deviceUserId);
    }

    /**
     * 仅允许群晖卡片的 availability 回调更新群晖全局连接状态
     *
     * @param deviceUserId 当前卡片绑定实例保存的 NAS 设备标识
     * @return 当前卡片属于唯一群晖合成设备时返回 true
     */
    public static boolean shouldApplySynologyAvailability(String deviceUserId) {
        return isSynologyDevice(deviceUserId);
    }

    /**
     * 只替换精确匹配飞牛 NAS 品牌名的短文案
     *
     * @param value 相册原方法返回的文案
     * @return 群晖品牌名或保持不变的原文案
     */
    public static String rewriteNasLabel(String value) {
        if ("飞牛 NAS".equals(value) || "FeiNiu NAS".equals(value)) {
            return "群晖 NAS";
        }
        return value;
    }

    /**
     * 仅将包含飞牛品牌的 NAS 状态说明替换为群晖实际能力描述
     *
     * @param value 相册原方法返回的状态说明
     * @return 群晖状态说明或保持不变的原文案
     */
    public static String rewriteNasStateMessage(String value) {
        if (value != null && (value.contains("飞牛") || value.contains("FeiNiu"))) {
            return "群晖 NAS 图片已接入私有云图集，可直接浏览和下载";
        }
        return value;
    }

    /**
     * 判断设备标识是否为跨全部 Hook 共用的唯一群晖设备标识
     *
     * @param deviceUserId 当前业务路径携带的 NAS 设备标识
     * @return 精确匹配群晖设备标识时返回 true
     */
    private static boolean isSynologyDevice(String deviceUserId) {
        return GalleryContract.DEVICE_ID.equals(deviceUserId);
    }
}
