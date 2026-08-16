package com.jaxson.coloros.synologynas.backup;

import com.jaxson.coloros.synologynas.SynologyConfig;

import org.junit.Test;

import static org.junit.Assert.assertNotEquals;

/** 验证本地照片备份哈希索引的配置作用域隔离规则 */
public final class SharedPreferencesBackupHashStoreTest {
    /** 验证不同备份目录使用彼此隔离的本地哈希索引作用域 */
    @Test
    public void isolatesHashIndexByConfiguredBackupFolder() {
        // 创建使用默认备份目录的第一份配置
        SynologyConfig first = config("ColorOS Backup");
        // 创建仅备份目录不同的第二份配置
        SynologyConfig second = config("Family Backup");

        assertNotEquals(
                SharedPreferencesBackupHashStore.scopeKey(first),
                SharedPreferencesBackupHashStore.scopeKey(second)
        );
    }

    /**
     * 创建仅备份目录可变的固定群晖测试配置
     *
     * @param backupFolder 参与哈希索引作用域计算的备份目录
     * @return 固定连接身份和根目录的群晖配置
     */
    private static SynologyConfig config(String backupFolder) {
        return new SynologyConfig(
                "https://nas.example.test",
                "user",
                "pass",
                "",
                "/home/Photos",
                true,
                backupFolder
        );
    }
}
