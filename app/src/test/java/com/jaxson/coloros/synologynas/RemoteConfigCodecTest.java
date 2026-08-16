package com.jaxson.coloros.synologynas;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

public final class RemoteConfigCodecTest {
    /** 验证完整配置经过 RemotePreferences JSON 编解码后字段保持一致 */
    @Test
    public void roundTripsCompleteConfiguration() {
        // 构造覆盖凭据、设备型号和自定义备份设置的完整配置
        SynologyConfig original = new SynologyConfig(
                "https://nas.example.test:5001",
                "user",
                "password",
                "123456",
                "/home/Photos",
                "DS920+",
                false,
                "手机备份"
        );

        // 执行生产链路使用的 JSON 编码和解码
        SynologyConfig decoded = RemoteConfigCodec.decode(RemoteConfigCodec.encode(original));

        assertEquals(original.serverUrl(), decoded.serverUrl());
        assertEquals(original.username(), decoded.username());
        assertEquals(original.password(), decoded.password());
        assertEquals(original.otp(), decoded.otp());
        assertEquals(original.remoteRoot(), decoded.remoteRoot());
        assertEquals(original.deviceModel(), decoded.deviceModel());
        assertEquals(original.backupEnabled(), decoded.backupEnabled());
        assertEquals(original.backupFolder(), decoded.backupFolder());
    }

    /** 验证缺少当前 schema 字段的旧 JSON 被明确拒绝 */
    @Test
    public void rejectsLegacyConfigurationMissingCurrentSchemaFields() {
        try {
            RemoteConfigCodec.decode(
                    "{\"server\":\"https://nas.example.test:5001\","
                            + "\"username\":\"user\","
                            + "\"password\":\"password\","
                            + "\"remote_root\":\"/home/Photos\"}"
            );
            fail("Expected current schema validation failure");
        } catch (IllegalStateException /* 预期的当前配置 schema 缺字段错误 */ expected) {
            assertEquals("群晖远程配置格式错误", expected.getMessage());
        }
    }

    /** 验证连接识别设备型号时不会覆盖用户的照片备份设置 */
    @Test
    public void preservesBackupSettingsWhenDeviceModelIsUpdated() {
        // 创建关闭备份并使用自定义目录的配置后仅更新设备型号
        SynologyConfig config = new SynologyConfig(
                "https://nas.example.test:5001",
                "user",
                "password",
                "",
                "/home/Photos",
                false,
                "家庭照片"
        ).withDeviceModel("DS920+");

        assertEquals(false, config.backupEnabled());
        assertEquals("家庭照片", config.backupFolder());
        assertEquals("DS920+", config.deviceModel());
    }

    /** 验证备份目录严格限制为单层名称 */
    @Test
    public void rejectsNestedBackupFolder() {
        try {
            new SynologyConfig(
                    "https://nas.example.test:5001",
                    "user",
                    "password",
                    "",
                    "/home/Photos",
                    true,
                    "ColorOS/Camera"
            );
            fail("Expected invalid backup folder");
        } catch (IllegalArgumentException /* 预期的嵌套目录校验错误 */ expected) {
            assertEquals("备份文件夹只能填写单个文件夹名称", expected.getMessage());
        }
    }

    /** 验证缺少 RemotePreferences 必填字段的 JSON 明确失败 */
    @Test
    public void rejectsIncompleteConfiguration() {
        try {
            RemoteConfigCodec.decode("{\"server\":\"https://HOST\"}");
            fail("Expected invalid remote configuration");
        } catch (IllegalStateException /* 预期的远程配置格式错误 */ expected) {
            assertEquals("群晖远程配置格式错误", expected.getMessage());
        }
    }
}
