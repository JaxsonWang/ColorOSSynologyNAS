package com.jaxson.coloros.synologynas.security;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import com.jaxson.coloros.synologynas.SynologyConfig;
import com.jaxson.coloros.synologynas.SynologyConfigSource;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class CredentialStore implements SynologyConfigSource {
    private static final String PREFERENCES = "synology_credentials";
    private static final String KEY_ALIAS = "ColorOSSynologyNAS.credentials.v1";
    private static final String KEY_SERVER = "server";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_PASSWORD = "password";
    private static final String KEY_OTP = "otp";
    private static final String KEY_REMOTE_ROOT = "remote_root";
    private static final String KEY_DEVICE_MODEL = "device_model";
    private static final String KEY_BACKUP_ENABLED = "backup_enabled";
    private static final String KEY_BACKUP_FOLDER = "backup_folder";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    private final SharedPreferences preferences;

    public CredentialStore(Context context) {
        preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    public void save(SynologyConfig config) throws GeneralSecurityException {
        String encryptedPassword = encrypt(KEY_PASSWORD, config.password());
        String encryptedOtp = encrypt(KEY_OTP, config.otp());
        boolean committed = preferences.edit()
                .putString(KEY_SERVER, config.serverUrl())
                .putString(KEY_USERNAME, config.username())
                .putString(KEY_PASSWORD, encryptedPassword)
                .putString(KEY_OTP, encryptedOtp)
                .putString(KEY_REMOTE_ROOT, config.remoteRoot())
                .putString(KEY_DEVICE_MODEL, config.deviceModel())
                .putBoolean(KEY_BACKUP_ENABLED, config.backupEnabled())
                .putString(KEY_BACKUP_FOLDER, config.backupFolder())
                .commit();
        if (!committed) {
            throw new IllegalStateException("群晖配置保存失败");
        }
    }

    @Override
    public boolean hasConfig() {
        return preferences.contains(KEY_SERVER);
    }

    @Override
    public SynologyConfig load() throws GeneralSecurityException {
        if (!preferences.contains(KEY_SERVER)) {
            return null;
        }
        return new SynologyConfig(
                preferences.getString(KEY_SERVER, ""),
                preferences.getString(KEY_USERNAME, ""),
                decrypt(KEY_PASSWORD, requiredEncryptedValue(KEY_PASSWORD)),
                decrypt(KEY_OTP, requiredEncryptedValue(KEY_OTP)),
                preferences.getString(KEY_REMOTE_ROOT, "/home/Photos"),
                preferences.getString(KEY_DEVICE_MODEL, ""),
                preferences.getBoolean(
                        KEY_BACKUP_ENABLED,
                        SynologyConfig.DEFAULT_BACKUP_ENABLED
                ),
                preferences.getString(
                        KEY_BACKUP_FOLDER,
                        SynologyConfig.DEFAULT_BACKUP_FOLDER
                )
        );
    }

    private String requiredEncryptedValue(String key) {
        String value = preferences.getString(key, null);
        if (value == null) {
            throw new IllegalStateException("群晖凭据不完整: " + key);
        }
        return value;
    }

    private String encrypt(String field, String plaintext) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
        cipher.updateAAD(field.getBytes(StandardCharsets.UTF_8));
        byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        return "v1:"
                + Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP)
                + ":"
                + Base64.encodeToString(ciphertext, Base64.NO_WRAP);
    }

    private String decrypt(String field, String encoded) throws GeneralSecurityException {
        String[] parts = encoded.split(":", -1);
        if (parts.length != 3 || !"v1".equals(parts[0])) {
            throw new GeneralSecurityException("不支持的凭据密文格式");
        }
        byte[] iv = Base64.decode(parts[1], Base64.NO_WRAP);
        byte[] ciphertext = Base64.decode(parts[2], Base64.NO_WRAP);
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(128, iv));
        cipher.updateAAD(field.getBytes(StandardCharsets.UTF_8));
        return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
    }

    private SecretKey getOrCreateKey() throws GeneralSecurityException {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        try {
            keyStore.load(null);
        } catch (java.io.IOException error) {
            throw new GeneralSecurityException("Android Keystore 加载失败", error);
        }
        SecretKey existing = (SecretKey) keyStore.getKey(KEY_ALIAS, null);
        if (existing != null) {
            return existing;
        }

        KeyGenerator generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore"
        );
        generator.init(new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
        )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return generator.generateKey();
    }
}
