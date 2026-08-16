package com.jaxson.coloros.synologynas.gallery;

import com.jaxson.coloros.synologynas.dsm.RemoteMedia;

// 保存映射到 ColorOS 的不可变群晖远端照片
public final class RemotePhoto {
    // DSM 路径生成且在相同路径下保持稳定的 long 字符串标识
    private final String id;
    // ColorOS 私有 DTO 使用的稳定正 int 标识
    private final int galleryId;
    // 保存 DSM 路径、文件名、大小、时间和 MIME 类型的远端媒体
    private final RemoteMedia media;

    // 绑定两类稳定标识与一个不可变 DSM 远端媒体模型
    public RemotePhoto(
            String id, // 稳定 long 字符串照片标识
            int galleryId, // ColorOS 使用的稳定正 int 标识
            RemoteMedia media // DSM 远端媒体元数据
    ) {
        this.id = id;
        this.galleryId = galleryId;
        this.media = media;
    }

    // 返回稳定 long 字符串照片标识
    public String id() {
        return id;
    }

    // 返回 ColorOS 私有 DTO 使用的稳定正 int 标识
    public int galleryId() {
        return galleryId;
    }

    // 返回绑定的 DSM 远端媒体元数据
    public RemoteMedia media() {
        return media;
    }
}
