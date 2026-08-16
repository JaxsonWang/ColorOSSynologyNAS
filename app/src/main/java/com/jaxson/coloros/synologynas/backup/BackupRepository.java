package com.jaxson.coloros.synologynas.backup;

import java.io.IOException;
import java.util.Collection;
import java.util.Set;

/** 为 ColorOS 适配层提供备份开关、查重和单张上传业务边界 */
public interface BackupRepository {
    /** @return 当前配置是否允许照片备份 */
    boolean isEnabled();

    /**
     * 查询当前备份作用域内已处理的 ColorOS 原生哈希
     *
     * @param hashes ColorOS 请求查询的原生哈希集合
     * @return 已存在于备份索引的哈希集合
     * @throws IOException 配置或哈希索引读取失败
     */
    Set<String> findExistingHashes(Collection<String> hashes) throws IOException;

    /**
     * 按路径冲突规则上传单张照片，并只在远端成功后记录哈希
     *
     * @param request ColorOS 相册提供的照片备份请求
     * @return 可直接映射回 ColorOS 私有合约的备份结果
     */
    BackupUploadResult upload(BackupUploadRequest request);
}
