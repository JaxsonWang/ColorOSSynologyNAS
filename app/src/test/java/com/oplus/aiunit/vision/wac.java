package com.oplus.aiunit.vision;

// 模拟 ColorOS 原图下载进度 DTO 的构造器合同
public final class wac {
    // 模拟下载进度绑定的照片稳定标识
    public final String photoId;
    // 模拟已经下载的字节数
    public final long downloadedSize;
    // 模拟原图总字节数
    public final long totalSize;
    // 模拟整数下载进度
    public final int progress;
    // 模拟下载是否已经完成
    public final boolean completed;

    // 按 ColorOS 当前五参数合同创建原图下载进度夹具
    public wac(
            String photoId, // 目标照片稳定标识
            long downloadedSize, // 已下载字节数
            long totalSize, // 原图总字节数
            int progress, // 整数下载进度
            boolean completed // 下载完成标记
    ) {
        this.photoId = photoId;
        this.downloadedSize = downloadedSize;
        this.totalSize = totalSize;
        this.progress = progress;
        this.completed = completed;
    }
}
