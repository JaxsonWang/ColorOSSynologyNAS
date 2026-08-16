package com.jaxson.coloros.synologynas;

import org.json.JSONException;
import org.json.JSONObject;

final class RemoteConfigCodec {
    private static final String KEY_SERVER = "server";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_PASSWORD = "password";
    private static final String KEY_OTP = "otp";
    private static final String KEY_REMOTE_ROOT = "remote_root";
    private static final String KEY_DEVICE_MODEL = "device_model";
    private static final String KEY_BACKUP_ENABLED = "backup_enabled";
    private static final String KEY_BACKUP_FOLDER = "backup_folder";

    private RemoteConfigCodec() {
    }

    static String encode(SynologyConfig config) {
        try {
            return new JSONObject()
                    .put(KEY_SERVER, config.serverUrl())
                    .put(KEY_USERNAME, config.username())
                    .put(KEY_PASSWORD, config.password())
                    .put(KEY_OTP, config.otp())
                    .put(KEY_REMOTE_ROOT, config.remoteRoot())
                    .put(KEY_DEVICE_MODEL, config.deviceModel())
                    .put(KEY_BACKUP_ENABLED, config.backupEnabled())
                    .put(KEY_BACKUP_FOLDER, config.backupFolder())
                    .toString();
        } catch (JSONException error) {
            throw new IllegalStateException("群晖远程配置编码失败", error);
        }
    }

    static SynologyConfig decode(String encoded) {
        try {
            JSONObject object = new JSONObject(encoded);
            return new SynologyConfig(
                    object.getString(KEY_SERVER),
                    object.getString(KEY_USERNAME),
                    object.getString(KEY_PASSWORD),
                    object.optString(KEY_OTP, ""),
                    object.getString(KEY_REMOTE_ROOT),
                    object.optString(KEY_DEVICE_MODEL, ""),
                    object.optBoolean(
                            KEY_BACKUP_ENABLED,
                            SynologyConfig.DEFAULT_BACKUP_ENABLED
                    ),
                    object.optString(
                            KEY_BACKUP_FOLDER,
                            SynologyConfig.DEFAULT_BACKUP_FOLDER
                    )
            );
        } catch (JSONException error) {
            throw new IllegalStateException("群晖远程配置格式错误", error);
        }
    }
}
