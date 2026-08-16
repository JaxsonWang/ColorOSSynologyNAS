package com.jaxson.coloros.synologynas;

import org.json.JSONException;
import org.json.JSONObject;

final class RemoteConfigCodec {
    // 标识 RemotePreferences JSON 中的 DSM 服务地址字段
    private static final String KEY_SERVER = "server";
    // 标识 RemotePreferences JSON 中的 DSM 用户名字段
    private static final String KEY_USERNAME = "username";
    // 标识 RemotePreferences JSON 中的 DSM 密码字段
    private static final String KEY_PASSWORD = "password";
    // 标识 RemotePreferences JSON 中的一次性验证码字段
    private static final String KEY_OTP = "otp";
    // 标识 RemotePreferences JSON 中的远端图片根目录字段
    private static final String KEY_REMOTE_ROOT = "remote_root";
    // 标识 RemotePreferences JSON 中的 NAS 设备型号字段
    private static final String KEY_DEVICE_MODEL = "device_model";
    // 标识 RemotePreferences JSON 中的照片备份开关字段
    private static final String KEY_BACKUP_ENABLED = "backup_enabled";
    // 标识 RemotePreferences JSON 中的照片备份目录字段
    private static final String KEY_BACKUP_FOLDER = "backup_folder";

    /** 禁止实例化仅承担配置序列化职责的工具类 */
    private RemoteConfigCodec() {
    }

    /**
     * 把完整配置编码成跨进程 RemotePreferences 使用的 JSON 文本
     *
     * @param config 已完成业务校验的群晖配置
     * @return 保留全部配置字段的 JSON 文本
     */
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
        } catch (JSONException /* JSON 编码失败的直接原因 */ error) {
            throw new IllegalStateException("群晖远程配置编码失败", error);
        }
    }

    /**
     * 从 RemotePreferences JSON 严格恢复当前完整配置 schema
     *
     * @param encoded RemotePreferences 中保存的配置 JSON
     * @return 经过 SynologyConfig 校验的配置对象
     */
    static SynologyConfig decode(String encoded) {
        try {
            // 承载待解码字段并保持 JSON 必填字段的严格读取语义
            JSONObject object = new JSONObject(encoded);
            return new SynologyConfig(
                    object.getString(KEY_SERVER),
                    object.getString(KEY_USERNAME),
                    object.getString(KEY_PASSWORD),
                    object.getString(KEY_OTP),
                    object.getString(KEY_REMOTE_ROOT),
                    object.getString(KEY_DEVICE_MODEL),
                    object.getBoolean(KEY_BACKUP_ENABLED),
                    object.getString(KEY_BACKUP_FOLDER)
            );
        } catch (JSONException /* JSON 格式或必填字段异常 */ error) {
            throw new IllegalStateException("群晖远程配置格式错误", error);
        }
    }
}
