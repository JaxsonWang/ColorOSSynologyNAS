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
    public static final String PREFERENCES_NAME = "coloros_synology_backup_hashes";
    private static final String KEY_PREFIX = "backup_hashes_v1_";

    private final SharedPreferences preferences;

    public SharedPreferencesBackupHashStore(SharedPreferences preferences) {
        this.preferences = preferences;
    }

    @Override
    public synchronized Set<String> findExisting(
            SynologyConfig config,
            Collection<String> hashes
    ) {
        Set<String> uploaded = preferences.getStringSet(scopeKey(config), Set.of());
        Set<String> existing = new LinkedHashSet<>();
        for (String hash : hashes) {
            String nativeHash = nativeHash(hash);
            if (uploaded.contains(nativeHash)) {
                existing.add(nativeHash);
            }
        }
        return Set.copyOf(existing);
    }

    @Override
    public synchronized void recordUploaded(SynologyConfig config, String hash) throws IOException {
        String key = scopeKey(config);
        Set<String> uploaded = new LinkedHashSet<>(
                preferences.getStringSet(key, Set.of())
        );
        uploaded.add(nativeHash(hash));
        try {
            if (!preferences.edit().putStringSet(key, uploaded).commit()) {
                throw new IOException("群晖备份哈希索引保存失败");
            }
        } catch (RuntimeException error) {
            throw new IOException("群晖备份哈希索引不可写", error);
        }
    }

    static String scopeKey(SynologyConfig config) {
        String scope = config.serverUrl() + '\u0000'
                + config.username() + '\u0000'
                + config.remoteRoot() + '\u0000'
                + config.backupFolder();
        return KEY_PREFIX + sha256(scope);
    }

    private static String nativeHash(String hash) {
        return hash == null ? "" : hash.trim();
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                result.append(String.format(Locale.ROOT, "%02x", item & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }
}
