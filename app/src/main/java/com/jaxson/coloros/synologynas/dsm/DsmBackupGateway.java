package com.jaxson.coloros.synologynas.dsm;

import com.jaxson.coloros.synologynas.backup.BackupPath;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

/** 隔离备份仓储与 DSM 认证、MD5、上传实现的网关合同 */
public interface DsmBackupGateway {
    /**
     * 动态发现备份所需 DSM API
     *
     * @return DSM API 目录
     * @throws IOException 发现失败
     */
    DsmApiCatalog discoverApis() throws IOException;

    /**
     * 登录 DSM 备份会话
     *
     * @param catalog DSM API 目录
     * @return 内存 SID
     * @throws IOException 登录失败
     */
    String login(DsmApiCatalog catalog) throws IOException;

    /**
     * 注销当前 DSM 备份会话
     *
     * @param catalog DSM API 目录
     * @param sid 当前内存 SID
     * @throws IOException 注销失败
     */
    void logout(DsmApiCatalog catalog, String sid) throws IOException;

    /**
     * 查询远端文件 MD5
     *
     * @param catalog DSM API 目录
     * @param sid 当前内存 SID
     * @param remotePath 完整远端路径
     * @return 远端 MD5；目标不存在时为空
     * @throws IOException 查询失败
     */
    Optional<String> md5(DsmApiCatalog catalog, String sid, String remotePath)
            throws IOException;

    /**
     * 流式上传一张本机照片
     *
     * @param catalog DSM API 目录
     * @param sid 当前内存 SID
     * @param path 远端备份路径
     * @param fileSize 预期照片字节数
     * @param input 本机照片输入流
     * @return DSM 明确成功后的已上传字节数
     * @throws IOException 上传失败
     */
    long upload(
            /* catalog 是 DSM API 目录 */ DsmApiCatalog catalog,
            /* sid 是当前内存 SID */ String sid,
            /* path 是远端备份路径 */ BackupPath path,
            /* fileSize 是预期照片字节数 */ long fileSize,
            /* input 是本机照片输入流 */ InputStream input
    ) throws IOException;
}
