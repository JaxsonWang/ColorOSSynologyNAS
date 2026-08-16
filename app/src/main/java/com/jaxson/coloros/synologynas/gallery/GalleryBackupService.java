package com.jaxson.coloros.synologynas.gallery;

import com.jaxson.coloros.synologynas.backup.BackupUploadResult;

import java.io.IOException;
import java.util.Collection;
import java.util.Set;

// 定义 ColorOS Provider 可调用的群晖备份业务边界
public interface GalleryBackupService {
    // 返回用户是否开启 ColorOS 原生 NAS 备份入口
    boolean isEnabled();

    // 查询指定内容 hash 中已经成功上传到当前备份目录的集合
    Set<String> findExistingHashes(
            Collection<String> hashes // ColorOS 请求确认的照片内容 hash
    ) throws IOException;

    // 执行一条 ColorOS 原生 NAS 备份请求并返回真实领域结果
    BackupUploadResult upload(Object colorOsRequest /* ColorOS 的 seq 请求 DTO */);
}
