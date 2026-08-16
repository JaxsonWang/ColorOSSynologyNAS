package com.jaxson.coloros.synologynas.backup;

import com.jaxson.coloros.synologynas.SynologyConfig;

import org.junit.Test;

import static org.junit.Assert.assertNotEquals;

public final class SharedPreferencesBackupHashStoreTest {
    @Test
    public void isolatesHashIndexByConfiguredBackupFolder() {
        SynologyConfig first = config("ColorOS Backup");
        SynologyConfig second = config("Family Backup");

        assertNotEquals(
                SharedPreferencesBackupHashStore.scopeKey(first),
                SharedPreferencesBackupHashStore.scopeKey(second)
        );
    }

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
