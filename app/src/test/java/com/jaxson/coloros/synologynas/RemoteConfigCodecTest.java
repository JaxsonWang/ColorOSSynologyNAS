package com.jaxson.coloros.synologynas;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

/** 验证群晖配置模型与跨进程 JSON 编解码合同 */
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

    /** 验证密码保持原始空格且图片根目录只在模型边界规范化 */
    @Test
    public void preservesPasswordWhitespaceAndNormalizesRemoteRoot() {
        // 创建密码带首尾空格且图片目录带末尾斜线的配置
        SynologyConfig nestedRoot = new SynologyConfig(
                "https://nas.example.test:5001",
                "user",
                "  password  ",
                "",
                "/home/Photos///",
                true,
                "ColorOS Backup"
        );
        // 创建直接使用 DSM 根目录的配置
        SynologyConfig root = new SynologyConfig(
                "https://nas.example.test:5001",
                "user",
                "password",
                "",
                "/",
                true,
                "ColorOS Backup"
        );
        // 执行跨进程配置实际使用的编解码链路
        SynologyConfig decoded = RemoteConfigCodec.decode(RemoteConfigCodec.encode(nestedRoot));

        assertEquals("  password  ", nestedRoot.password());
        assertEquals("  password  ", decoded.password());
        assertEquals("/home/Photos", nestedRoot.remoteRoot());
        assertEquals("/home/Photos", decoded.remoteRoot());
        assertEquals("/", root.remoteRoot());
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

    /** 验证字符串布尔值不会被 RemotePreferences 严格 schema 自动转换 */
    @Test
    public void rejectsStringEncodedBackupFlag() {
        try {
            RemoteConfigCodec.decode(
                    "{\"server\":\"https://nas.example.test:5001\","
                            + "\"username\":\"user\","
                            + "\"password\":\"password\","
                            + "\"otp\":\"\","
                            + "\"remote_root\":\"/home/Photos\","
                            + "\"device_model\":\"DS920+\","
                            + "\"backup_enabled\":\"true\","
                            + "\"backup_folder\":\"ColorOS Backup\"}"
            );
            fail("Expected strict backup flag type validation failure");
        } catch (IllegalStateException /* 预期的远程配置布尔字段类型错误 */ expected) {
            assertEquals("群晖远程配置格式错误", expected.getMessage());
        }
    }

    /** 验证全部字符串字段不会被 Android JSONObject 从数字或布尔值强制转换 */
    @Test
    public void rejectsNonStringConfigurationFields() throws JSONException {
        // invalidValues 覆盖 Android JSONObject.getString 会强制转换的两类原始值
        Object[] invalidValues = {7, Boolean.FALSE};
        for (Object /* 当前交给严格字符串边界的错误原始值 */ invalidValue : invalidValues) {
            // object 保存一个可直接验证原始类型的测试字段
            JSONObject object = new JSONObject().put("field", invalidValue);

            // error 保存严格字符串读取边界返回的预期类型异常
            JSONException error = assertThrows(
                    JSONException.class,
                    () /* 读取当前错误类型的原始字段 */ ->
                            RemoteConfigCodec.requiredString(object, "field")
            );
            assertEquals(
                    "Remote configuration field is not string: field",
                    error.getMessage()
            );
        }
    }
}
