package com.jaxson.coloros.synologynas.gallery;

public final class RemoteAlbum {
    // DSM 目录生成且在相同路径下保持稳定的 long 字符串标识
    private final String id;
    // ColorOS 私有 DTO 使用的稳定正 int 标识
    private final int galleryId;
    // ColorOS 展示的相对目录或 ALL_PROJECT 名称
    private final String name;
    // 当前清单快照中属于该相册的图片数量
    private final int imageCount;
    // 当前相册最新图片的稳定 long 字符串标识
    private final String coverPhotoId;
    // 当前相册最新图片的 ColorOS 正 int 标识
    private final int coverGalleryId;
    // 当前相册所有图片的最近修改时间，单位为毫秒
    private final long updateTimeMillis;

    // 保存一次清单快照中构建完成的不可变远端相册模型
    public RemoteAlbum(
            String id, // 稳定 long 字符串相册标识
            int galleryId, // ColorOS 使用的稳定正 int 标识
            String name, // 相对目录或 ALL_PROJECT 展示名称
            int imageCount, // 相册图片数量
            String coverPhotoId, // 封面照片稳定字符串标识
            int coverGalleryId, // 封面照片 ColorOS int 标识
            long updateTimeMillis // 相册最近修改时间毫秒值
    ) {
        this.id = id;
        this.galleryId = galleryId;
        this.name = name;
        this.imageCount = imageCount;
        this.coverPhotoId = coverPhotoId;
        this.coverGalleryId = coverGalleryId;
        this.updateTimeMillis = updateTimeMillis;
    }

    // 返回稳定 long 字符串相册标识
    public String id() {
        return id;
    }

    // 返回 ColorOS 私有 DTO 使用的稳定正 int 标识
    public int galleryId() {
        return galleryId;
    }

    // 返回相册相对目录或 ALL_PROJECT 展示名称
    public String name() {
        return name;
    }

    // 返回当前清单快照中的相册图片数量
    public int imageCount() {
        return imageCount;
    }

    // 返回封面照片稳定字符串标识
    public String coverPhotoId() {
        return coverPhotoId;
    }

    // 返回封面照片 ColorOS 正 int 标识
    public int coverGalleryId() {
        return coverGalleryId;
    }

    // 返回相册最近修改时间毫秒值
    public long updateTimeMillis() {
        return updateTimeMillis;
    }
}
