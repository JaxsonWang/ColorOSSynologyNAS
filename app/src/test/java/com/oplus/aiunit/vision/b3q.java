package com.oplus.aiunit.vision;

import com.oplus.gallery.business_lib.nas.NasProvider;

public final class b3q {
    // 模拟 NAS 相册 DTO 的 Provider 归属字段
    public final NasProvider provider;
    // 模拟 NAS 相册 DTO 的 ColorOS int 标识字段
    public final int galleryId;
    // 模拟 NAS 相册 DTO 的稳定字符串标识字段
    public final String id;
    // 模拟 NAS 相册 DTO 的展示名称字段
    public final String name;
    // 模拟 NAS 相册 DTO 的设备唯一标识字段
    public final String deviceUserId;
    // 模拟 NAS 相册 DTO 的照片数量字段
    public final int imageCount;
    // 模拟 NAS 相册 DTO 的视频数量字段
    public final int videoCount;
    // 模拟 NAS 相册 DTO 的封面照片字符串标识字段
    public final String coverPhotoId;
    // 模拟 NAS 相册 DTO 的封面照片 int 标识字段
    public final int coverGalleryId;
    // 模拟 NAS 相册 DTO 的最近更新时间字段
    public final long updateTimeMillis;

    // 按 ColorOS 当前十参数合同创建 NAS 相册 DTO 夹具
    public b3q(
            NasProvider provider, // NAS 相册归属的 Provider
            int galleryId, // ColorOS 使用的稳定正 int 相册标识
            String id, // 稳定 long 字符串相册标识
            String name, // 相册展示名称
            String deviceUserId, // NAS 设备唯一标识
            int imageCount, // 相册照片数量
            int videoCount, // 相册视频数量
            String coverPhotoId, // 封面照片稳定字符串标识
            int coverGalleryId, // 封面照片 ColorOS int 标识
            long updateTimeMillis // 相册最近更新时间毫秒值
    ) {
        this.provider = provider;
        this.galleryId = galleryId;
        this.id = id;
        this.name = name;
        this.deviceUserId = deviceUserId;
        this.imageCount = imageCount;
        this.videoCount = videoCount;
        this.coverPhotoId = coverPhotoId;
        this.coverGalleryId = coverGalleryId;
        this.updateTimeMillis = updateTimeMillis;
    }
}
