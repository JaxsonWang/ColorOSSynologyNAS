package com.jaxson.coloros.synologynas.gallery;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

// 定义群晖图库浏览、媒体读取和删除数据源边界
public interface RemoteGalleryDataSource {
    // 返回相册进程当前是否已经取得完整群晖配置
    boolean isConfigured();

    // 读取配置中上次连接确认的 NAS 型号，不执行网络请求
    String configuredDeviceModel() throws IOException;

    // 实时连接 DSM 并读取 NAS 型号
    String probeDeviceModel() throws IOException;

    // 分页读取群晖相册列表
    List<RemoteAlbum> listAlbums(
            int offset, // ColorOS 请求的相册起始偏移
            int limit // ColorOS 请求的相册最大数量
    ) throws IOException;

    // 按稳定相册标识读取一个群晖相册
    RemoteAlbum getAlbum(String albumId /* 远端相册稳定标识 */) throws IOException;

    // 分页读取指定群晖相册的照片
    List<RemotePhoto> listPhotos(
            String albumId, // 远端相册稳定标识
            int offset, // ColorOS 请求的照片起始偏移
            int limit // ColorOS 请求的照片最大数量
    ) throws IOException;

    // 将指定远端照片缩略图直接写入调用方输出流
    void downloadThumbnail(
            String photoId, // 远端照片稳定标识
            String size, // 群晖客户端支持的缩略图尺寸标识
            OutputStream output // 接收缩略图字节的调用方输出流
    ) throws IOException;

    // 将指定远端照片原文件直接写入调用方输出流
    void downloadOriginal(
            String photoId, // 远端照片稳定标识
            OutputStream output // 接收原图字节的调用方输出流
    ) throws IOException;

    // 删除指定远端照片，并仅在成功后失效清单缓存
    boolean deletePhotos(List<String> photoIds /* 待删除的远端照片标识 */)
            throws IOException;
}
