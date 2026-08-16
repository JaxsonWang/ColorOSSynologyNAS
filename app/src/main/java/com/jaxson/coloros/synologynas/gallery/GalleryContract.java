package com.jaxson.coloros.synologynas.gallery;

public final class GalleryContract {
    // ColorOS 相册资源查找和 Hook 版本门使用的目标包名
    public static final String GALLERY_PACKAGE = "com.coloros.gallery3d";
    // 跨首页、设备、Provider、状态、删除和备份共享的唯一群晖设备标识
    public static final String DEVICE_ID = "synology-dsm7";
    // ColorOS 私有云图集首页和设备卡片展示的固定品牌名称
    public static final String DEVICE_NAME = "群晖 NAS";
    // 尚未读取保存型号时展示的 DSM 基线名称
    public static final String DEFAULT_DEVICE_MODEL = "DSM 7";

    // GalleryRemoteClient 请求群晖大尺寸缩略图的固定标识
    public static final String THUMBNAIL_LARGE = "large";
    // GalleryRemoteClient 请求群晖小尺寸缩略图的固定标识
    public static final String THUMBNAIL_SMALL = "small";

    // 禁止实例化只承载跨 Hook 合约常量的工具类
    private GalleryContract() {
    }

}
