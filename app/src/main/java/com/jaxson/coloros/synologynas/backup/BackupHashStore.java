package com.jaxson.coloros.synologynas.backup;

import com.jaxson.coloros.synologynas.SynologyConfig;

import java.io.IOException;
import java.util.Collection;
import java.util.Set;

/** 隔离照片备份事务与本地 ColorOS 原生哈希索引存储 */
public interface BackupHashStore {
    /**
     * 查询当前配置作用域内已完成备份的 ColorOS 原生哈希
     *
     * @param config 决定哈希索引作用域的群晖配置
     * @param hashes 待查询的 ColorOS 原生哈希集合
     * @return 已存在于当前作用域索引中的哈希集合
     * @throws IOException 索引读取失败
     */
    Set<String> findExisting(SynologyConfig config, Collection<String> hashes) throws IOException;

    /**
     * 在 DSM 上传或远端内容验证成功后记录 ColorOS 原生哈希
     *
     * @param config 决定哈希索引作用域的群晖配置
     * @param hash 已确认存在于 DSM 的 ColorOS 原生哈希
     * @throws IOException 索引持久化失败
     */
    void recordUploaded(SynologyConfig config, String hash) throws IOException;
}
