package com.oplus.gallery.business_lib.nas;

public final class NasPhotoInfo {
    // 模拟远端照片 DTO 的 ColorOS int 标识字段
    public final int galleryId;
    // 模拟远端照片 DTO 的稳定字符串标识字段
    public final String id;
    // 模拟远端照片 DTO 的设备唯一标识字段
    public final String deviceUserId;
    // 模拟远端照片 DTO 的文件名字段
    public final String name;
    // 模拟远端照片 DTO 的文件字节数字段
    public final long size;
    // 模拟远端照片 DTO 的 MIME 类型字段
    public final String mimeType;
    // 模拟远端照片 DTO 的修改时间字段
    public final long modifiedAtMillis;
    // 模拟远端照片 DTO 的媒体类型字段
    public final MediaType mediaType;
    // 模拟远端照片 DTO 的实况照片类型字段
    public final LivePhotoType livePhotoType;

    // 按 ColorOS 当前十三参数合同创建 NAS 照片 DTO 夹具
    public NasPhotoInfo(
            int galleryId, // ColorOS 使用的稳定正 int 照片标识
            String id, // 稳定 long 字符串照片标识
            String deviceUserId, // NAS 设备唯一标识
            String name, // 远端照片文件名
            long size, // 远端照片字节数
            Object width, // 当前群晖映射不提供的宽度值
            Object height, // 当前群晖映射不提供的高度值
            String mimeType, // 远端照片 MIME 类型
            long modifiedAtMillis, // 远端照片修改时间毫秒值
            MediaType mediaType, // ColorOS 图片媒体类型
            LivePhotoType livePhotoType, // ColorOS 实况照片类型
            boolean favorite, // ColorOS 收藏状态测试值
            int orientation // ColorOS 图片方向测试值
    ) {
        this.galleryId = galleryId;
        this.id = id;
        this.deviceUserId = deviceUserId;
        this.name = name;
        this.size = size;
        this.mimeType = mimeType;
        this.modifiedAtMillis = modifiedAtMillis;
        this.mediaType = mediaType;
        this.livePhotoType = livePhotoType;
    }

    public enum MediaType {
        IMAGE // 模拟 ColorOS 图片媒体类型
    }

    public enum LivePhotoType {
        NONE // 模拟 ColorOS 非实况照片类型
    }
}
