package com.oplus.gallery.business_lib.nas;

// 模拟 ColorOS NAS 照片 DTO 的精确构造合同
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
    // 模拟远端照片 DTO 的可空宽度字段
    public final Integer width;
    // 模拟远端照片 DTO 的可空高度字段
    public final Integer height;
    // 模拟远端照片 DTO 的 MIME 类型字段
    public final String mimeType;
    // 模拟远端照片 DTO 的修改时间字段
    public final Long modifiedAtMillis;
    // 模拟远端照片 DTO 的媒体类型字段
    public final MediaType mediaType;
    // 模拟远端照片 DTO 的实况照片类型字段
    public final LivePhotoType livePhotoType;
    // 模拟远端照片 DTO 的 OPPO 实况照片标记
    public final boolean oppoLivePhoto;
    // 模拟远端照片 DTO 的媒体时长字段
    public final int mediaDuration;

    // 按 ColorOS 当前十三参数合同创建 NAS 照片 DTO 夹具
    public NasPhotoInfo(
            int galleryId, // ColorOS 使用的稳定正 int 照片标识
            String id, // 稳定 long 字符串照片标识
            String deviceUserId, // NAS 设备唯一标识
            String name, // 远端照片文件名
            long size, // 远端照片字节数
            Integer width, // 当前群晖映射不提供的宽度值
            Integer height, // 当前群晖映射不提供的高度值
            String mimeType, // 远端照片 MIME 类型
            Long modifiedAtMillis, // 远端照片修改时间毫秒值
            MediaType mediaType, // ColorOS 图片媒体类型
            LivePhotoType livePhotoType, // ColorOS 实况照片类型
            boolean oppoLivePhoto, // OPPO 实况照片标记
            int mediaDuration // 媒体时长
    ) {
        this.galleryId = galleryId;
        this.id = id;
        this.deviceUserId = deviceUserId;
        this.name = name;
        this.size = size;
        this.width = width;
        this.height = height;
        this.mimeType = mimeType;
        this.modifiedAtMillis = modifiedAtMillis;
        this.mediaType = mediaType;
        this.livePhotoType = livePhotoType;
        this.oppoLivePhoto = oppoLivePhoto;
        this.mediaDuration = mediaDuration;
    }

    // 模拟 ColorOS NAS 照片媒体类型枚举合同
    public enum MediaType {
        IMAGE, // 模拟 ColorOS 图片媒体类型
        VIDEO // 模拟 ColorOS 视频媒体类型
    }

    // 模拟 ColorOS NAS 实况照片类型枚举合同
    public enum LivePhotoType {
        NONE, // 模拟 ColorOS 非实况照片类型
        IOS, // 模拟 iOS 实况照片类型
        MOTION_MP42, // 模拟 MP42 实况照片类型
        MOTION_ISOM, // 模拟 ISOM 实况照片类型
        MOTION_LIVP, // 模拟 LIVP 实况照片类型
        VIVO // 模拟 vivo 实况照片类型
    }
}
