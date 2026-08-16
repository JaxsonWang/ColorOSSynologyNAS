package com.jaxson.coloros.synologynas;

import org.json.JSONException;
import org.json.JSONObject;

/** 在唯一边界严格编解码跨进程群晖配置 JSON */
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
    static String encode(
            SynologyConfig config // 已完成业务校验且需要跨进程发布的群晖配置
    ) {
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
    static SynologyConfig decode(
            String encoded // RemotePreferences 中保存的完整配置 JSON
    ) {
        try {
            // 承载待解码字段并保持 JSON 必填字段的严格读取语义
            JSONObject object = new JSONObject(encoded);
            return new SynologyConfig(
                    requiredString(object, KEY_SERVER),
                    requiredString(object, KEY_USERNAME),
                    requiredString(object, KEY_PASSWORD),
                    requiredString(object, KEY_OTP),
                    requiredString(object, KEY_REMOTE_ROOT),
                    requiredString(object, KEY_DEVICE_MODEL),
                    requiredBoolean(object, KEY_BACKUP_ENABLED),
                    requiredString(object, KEY_BACKUP_FOLDER)
            );
        } catch (JSONException /* JSON 格式或必填字段异常 */ error) {
            throw new IllegalStateException("群晖远程配置格式错误", error);
        }
    }

    /**
     * 严格读取 JSON 字符串字段，禁止 Android JSONObject 执行隐式类型转换
     *
     * @param object 当前 RemotePreferences 配置 JSON
     * @param key 必须保存为原生字符串的字段名
     * @return JSON 中直接保存的字符串值
     * @throws JSONException 字段缺失或实际类型不是字符串
     */
    static String requiredString(
            JSONObject object, // 当前 RemotePreferences 配置 JSON
            String key // 必须保存为原生字符串的字段名
    ) throws JSONException {
        // 读取原始字段值，避免 Android JSONObject.getString 强制转换数字或布尔值
        Object value = object.get(key);
        if (!(value instanceof String)) {
            throw new JSONException("Remote configuration field is not string: " + key);
        }
        return (String) value;
    }

    /**
     * 严格读取 JSON 布尔字段，禁止把字符串文本转换为 schema 值
     *
     * @param object 当前 RemotePreferences 配置 JSON
     * @param key 必须保存为原生布尔值的字段名
     * @return JSON 中直接保存的布尔值
     * @throws JSONException 字段缺失或实际类型不是布尔值
     */
    private static boolean requiredBoolean(
            JSONObject object, // 当前 RemotePreferences 配置 JSON
            String key // 必须保存为原生布尔值的字段名
    ) throws JSONException {
        // 读取未经 JSONObject 类型转换的原始字段值
        Object value = object.get(key);
        if (!(value instanceof Boolean)) {
            throw new JSONException("Remote configuration field is not boolean: " + key);
        }
        return (Boolean) value;
    }
}
