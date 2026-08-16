package com.jaxson.coloros.synologynas.backup;

import android.content.SharedPreferences;

import com.jaxson.coloros.synologynas.SynologyConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public final class SharedPreferencesBackupHashStore implements BackupHashStore {
    // 指定相册进程内持久化备份哈希索引的 SharedPreferences 文件
    public static final String PREFERENCES_NAME = "coloros_synology_backup_hashes";
    // 标识当前索引格式并与基于配置摘要的作用域键拼接
    private static final String KEY_PREFIX = "backup_hashes_v1_";

    // 持有照片备份哈希索引的本地偏好存储
    private final SharedPreferences preferences;

    /**
     * 绑定照片备份哈希索引存储
     *
     * @param preferences 相册进程内的专用 SharedPreferences
     */
    public SharedPreferencesBackupHashStore(SharedPreferences preferences) {
        this.preferences = preferences;
    }

    /**
     * 查询当前群晖配置作用域内已记录的 ColorOS 原生哈希
     *
     * @param config 决定索引作用域的群晖配置
     * @param hashes 待查询的 ColorOS 原生哈希集合
     * @return 当前作用域内已存在的不可变哈希集合
     */
    @Override
    public synchronized Set<String> findExisting(
            SynologyConfig config,
            Collection<String> hashes
    ) {
        // 读取当前配置作用域已持久化的全部原生哈希
        Set<String> uploaded = preferences.getStringSet(scopeKey(config), Set.of());
        // 收集输入集合与已上传集合的有序交集
        Set<String> existing = new LinkedHashSet<>();
        // 逐一检查调用方请求查询的 ColorOS 原生哈希
        for (String hash : hashes) {
            // 保存去除首尾空白后的原生哈希，保持写入与查询规范一致
            String nativeHash = nativeHash(hash);
            if (uploaded.contains(nativeHash)) {
                existing.add(nativeHash);
            }
        }
        return Set.copyOf(existing);
    }

    /**
     * 在 DSM 成功后同步提交单个 ColorOS 原生哈希
     *
     * @param config 决定索引作用域的群晖配置
     * @param hash 已确认存在于 DSM 的 ColorOS 原生哈希
     * @throws IOException 索引提交失败或偏好存储不可写
     */
    @Override
    public synchronized void recordUploaded(SynologyConfig config, String hash) throws IOException {
        // 生成与查询路径完全一致的配置作用域键
        String key = scopeKey(config);
        // 复制已上传集合，避免修改 SharedPreferences 返回的集合实例
        Set<String> uploaded = new LinkedHashSet<>(
                preferences.getStringSet(key, Set.of())
        );
        uploaded.add(nativeHash(hash));
        try {
            if (!preferences.edit().putStringSet(key, uploaded).commit()) {
                throw new IOException("群晖备份哈希索引保存失败");
            }
        } catch (RuntimeException /* SharedPreferences 写入时抛出的运行时错误 */ error) {
            throw new IOException("群晖备份哈希索引不可写", error);
        }
    }

    /**
     * 用连接身份、根目录和备份目录生成隔离索引的稳定作用域键
     *
     * @param config 当前照片备份使用的完整群晖配置
     * @return 不泄露配置明文的 SHA-256 作用域键
     */
    static String scopeKey(SynologyConfig config) {
        // 用不可出现在规范字段中的空字符分隔作用域组成部分
        String scope = config.serverUrl() + '\u0000'
                + config.username() + '\u0000'
                + config.remoteRoot() + '\u0000'
                + config.backupFolder();
        return KEY_PREFIX + sha256(scope);
    }

    /**
     * 保持 ColorOS 原生哈希内容，仅去除意外的首尾空白
     *
     * @param hash 待写入或查询的原生哈希
     * @return 去除首尾空白后的哈希文本
     */
    private static String nativeHash(String hash) {
        return hash == null ? "" : hash.trim();
    }

    /**
     * 计算配置作用域的稳定 SHA-256 十六进制摘要
     *
     * @param value 包含配置作用域字段的分隔文本
     * @return 64 位小写十六进制摘要
     */
    private static String sha256(String value) {
        try {
            // 保存配置作用域文本的 SHA-256 原始字节摘要
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            // 按固定区域设置把摘要编码为稳定的小写十六进制文本
            StringBuilder result = new StringBuilder(digest.length * 2);
            // 逐字节编码摘要，避免受默认区域设置影响
            for (byte item : digest) {
                result.append(String.format(Locale.ROOT, "%02x", item & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException /* 运行环境缺少标准 SHA-256 算法 */ error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }
}
