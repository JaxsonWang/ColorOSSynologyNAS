package com.jaxson.coloros.synologynas.backup;

import com.jaxson.coloros.synologynas.SynologyConfig;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.junit.Assert.assertEquals;

public final class BackupPathPolicyTest {
    @Test
    public void mapsEveryColorOsRequestToConfiguredBackupFolder() {
        BackupUploadRequest first = request("OPPO/PLK110", "相机/主相册", "IMG:1.jpg");
        BackupUploadRequest second = request("Different phone", "Screenshots", "IMG:2.jpg");

        BackupPath firstPath = BackupPathPolicy.primary(config(), first);
        BackupPath secondPath = BackupPathPolicy.primary(config(), second);

        assertEquals(
                "/home/Photos/手机备份/IMG_1.jpg",
                firstPath.remotePath()
        );
        assertEquals(firstPath.folder(), secondPath.folder());
        assertEquals("/home/Photos/手机备份/IMG_2.jpg", secondPath.remotePath());
    }

    @Test
    public void addsStableNativeHashSuffixForDifferentContentWithSameName() {
        BackupUploadRequest request = request("PLK110", "Camera", "IMG_1.jpg");

        BackupPath path = BackupPathPolicy.collision(config(), request);

        assertEquals(
                "/home/Photos/手机备份/"
                        + "IMG_1_0123456789abcdef0123456789abcdef"
                        + "fedcba9876543210fedcba9876543210.jpg",
                path.remotePath()
        );
    }

    private static SynologyConfig config() {
        return new SynologyConfig(
                "https://nas.example.test",
                "user",
                "pass",
                "",
                "/home/Photos",
                true,
                "手机备份"
        );
    }

    private static BackupUploadRequest request(
            String deviceName,
            String albumName,
            String fileName
    ) {
        return new BackupUploadRequest(
                "synology-dsm7",
                "phone-id",
                deviceName,
                fileName,
                3L,
                () -> new ByteArrayInputStream(new byte[]{1, 2, 3}),
                "0123456789abcdef0123456789abcdef"
                        + "fedcba9876543210fedcba9876543210",
                List.of(albumName)
        );
    }
}
