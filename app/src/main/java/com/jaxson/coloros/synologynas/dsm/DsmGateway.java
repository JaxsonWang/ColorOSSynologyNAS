package com.jaxson.coloros.synologynas.dsm;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

/** 隔离相册仓储与 DSM 浏览实现的网关合同 */
public interface DsmGateway {
    /**
     * 动态发现浏览所需 DSM API
     *
     * @return DSM API 目录
     * @throws IOException 发现失败
     */
    DsmApiCatalog discoverApis() throws IOException;

    /**
     * 登录 DSM 浏览会话
     *
     * @param catalog DSM API 目录
     * @return 内存 SID
     * @throws IOException 登录失败
     */
    String login(DsmApiCatalog catalog) throws IOException;

    /**
     * 获取 DSM 设备型号
     *
     * @param catalog DSM API 目录
     * @param sid 当前内存 SID
     * @return NAS 型号
     * @throws IOException 查询失败
     */
    String getDeviceModel(DsmApiCatalog catalog, String sid) throws IOException;

    /**
     * 列出配置根目录下所有图片
     *
     * @param catalog DSM API 目录
     * @param sid 当前内存 SID
     * @return 远端图片列表
     * @throws IOException 列表读取失败
     */
    List<RemoteMedia> listImages(DsmApiCatalog catalog, String sid) throws IOException;

    /**
     * 流式下载远端原图
     *
     * @param catalog DSM API 目录
     * @param sid 当前内存 SID
     * @param media 远端图片
     * @param output 调用方输出流
     * @throws IOException 下载失败
     */
    void download(
            DsmApiCatalog catalog,
            String sid,
            RemoteMedia media,
            OutputStream output
    ) throws IOException;

    /**
     * 下载或生成远端图片缩略图
     *
     * @param catalog DSM API 目录
     * @param sid 当前内存 SID
     * @param media 远端图片
     * @param size ColorOS 缩略图尺寸
     * @param output 调用方输出流
     * @throws IOException 缩略图读取失败
     */
    void downloadThumbnail(
            DsmApiCatalog catalog,
            String sid,
            RemoteMedia media,
            String size,
            OutputStream output
    ) throws IOException;

    /**
     * 删除指定远端图片并等待 DSM 任务完成
     *
     * @param catalog DSM API 目录
     * @param sid 当前内存 SID
     * @param media 待删除图片
     * @throws IOException 删除失败
     */
    void delete(DsmApiCatalog catalog, String sid, List<RemoteMedia> media) throws IOException;
}
