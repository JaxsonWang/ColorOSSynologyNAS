package com.jaxson.coloros.synologynas;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

public final class RemoteConfigCodecTest {
    @Test
    public void roundTripsCompleteConfiguration() {
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

    @Test
    public void decodesLegacyConfigurationWithoutDeviceModel() {
        SynologyConfig decoded = RemoteConfigCodec.decode(
                "{\"server\":\"https://nas.example.test:5001\","
                        + "\"username\":\"user\","
                        + "\"password\":\"password\","
                        + "\"remote_root\":\"/home/Photos\"}"
        );

        assertEquals("", decoded.deviceModel());
        assertEquals(SynologyConfig.DEFAULT_BACKUP_ENABLED, decoded.backupEnabled());
        assertEquals(SynologyConfig.DEFAULT_BACKUP_FOLDER, decoded.backupFolder());
    }

    @Test
    public void preservesBackupSettingsWhenDeviceModelIsUpdated() {
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
        } catch (IllegalArgumentException expected) {
            assertEquals("备份文件夹只能填写单个文件夹名称", expected.getMessage());
        }
    }

    @Test
    public void rejectsIncompleteConfiguration() {
        try {
            RemoteConfigCodec.decode("{\"server\":\"https://HOST\"}");
            fail("Expected invalid remote configuration");
        } catch (IllegalStateException expected) {
            assertEquals("群晖远程配置格式错误", expected.getMessage());
        }
    }
}
