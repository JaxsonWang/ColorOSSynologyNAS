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
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** 使用 Android Keystore 加密保存模块 App 内的群晖敏感凭据 */
public final class CredentialStore implements SynologyConfigSource {
    // 指定仅由模块 App 保存敏感群晖凭据的 SharedPreferences 文件
    private static final String PREFERENCES = "synology_credentials";
    // 标识 Android Keystore 中唯一的 AES-256-GCM 凭据密钥
    private static final String KEY_ALIAS = "ColorOSSynologyNAS.credentials.v1";
    // 标识明文保存的 DSM HTTPS 服务地址字段
    private static final String KEY_SERVER = "server";
    // 标识明文保存的 DSM 用户名字段
    private static final String KEY_USERNAME = "username";
    // 标识必须使用 Keystore 加密保存的 DSM 密码字段
    private static final String KEY_PASSWORD = "password";
    // 标识必须使用 Keystore 加密保存的一次性验证码字段
    private static final String KEY_OTP = "otp";
    // 标识明文保存的 DSM 图片根目录字段
    private static final String KEY_REMOTE_ROOT = "remote_root";
    // 标识连接验证后识别的 NAS 设备型号字段
    private static final String KEY_DEVICE_MODEL = "device_model";
    // 标识照片备份开关字段
    private static final String KEY_BACKUP_ENABLED = "backup_enabled";
    // 标识单层照片备份目录字段
    private static final String KEY_BACKUP_FOLDER = "backup_folder";
    // 固定凭据加密为 Android Keystore 支持的 AES-GCM 无填充模式
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    // 集中定义当前凭据存储 schema 必须同时存在的八个配置键
    private static final String[] REQUIRED_CONFIG_KEYS = {
            KEY_SERVER,
            KEY_USERNAME,
            KEY_PASSWORD,
            KEY_OTP,
            KEY_REMOTE_ROOT,
            KEY_DEVICE_MODEL,
            KEY_BACKUP_ENABLED,
            KEY_BACKUP_FOLDER
    };

    // 持有模块 App 私有的群晖凭据和配置存储
    private final SharedPreferences preferences;

    /**
     * 绑定模块 App 私有的群晖凭据存储
     *
     * @param context 用于打开应用私有 SharedPreferences 的上下文
     */
    public CredentialStore(
            Context context // 用于打开应用私有 SharedPreferences 的上下文
    ) {
        this(context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE));
    }

    /**
     * 绑定显式凭据存储，供同包测试验证完整 schema 规则
     *
     * @param preferences 模块 App 私有的群晖凭据偏好存储
     */
    CredentialStore(
            SharedPreferences preferences // 模块 App 私有的群晖凭据偏好存储
    ) {
        this.preferences = preferences;
    }

    /**
     * 使用 Android Keystore 加密密码和 OTP 后同步提交完整群晖配置
     *
     * @param config 已完成业务校验的群晖配置
     * @throws GeneralSecurityException Keystore 或 AES-GCM 加密失败
     */
    public void save(
            SynologyConfig config // 已完成业务校验且需要加密保存的群晖配置
    ) throws GeneralSecurityException {
        // 保存与密码字段名绑定附加数据后的 AES-GCM 密文
        String encryptedPassword = encrypt(KEY_PASSWORD, config.password());
        // 保存与 OTP 字段名绑定附加数据后的 AES-GCM 密文
        String encryptedOtp = encrypt(KEY_OTP, config.otp());
        // 记录完整配置的同步提交结果，避免部分发布状态被误报成功
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

    /** @return 模块私有存储是否同时包含当前 schema 的全部八个配置键 */
    @Override
    public boolean hasConfig() {
        // 保存本次完整性判断使用的唯一偏好快照
        Map<String, ?> values = preferences.getAll();
        if (!hasAnyConfigKey(values)) {
            return false;
        }
        requireCompleteConfig(values);
        return true;
    }

    /**
     * 解密敏感字段并恢复完整群晖配置
     *
     * @return 已恢复配置；尚未保存配置时返回 null
     * @throws GeneralSecurityException Keystore 或 AES-GCM 解密失败
     */
    @Override
    public SynologyConfig load() throws GeneralSecurityException {
        // 保存本次配置恢复全程使用的唯一偏好快照
        Map<String, ?> values = preferences.getAll();
        if (!hasAnyConfigKey(values)) {
            return null;
        }
        requireCompleteConfig(values);
        return new SynologyConfig(
                requiredString(values, KEY_SERVER),
                requiredString(values, KEY_USERNAME),
                decrypt(KEY_PASSWORD, requiredString(values, KEY_PASSWORD)),
                decrypt(KEY_OTP, requiredString(values, KEY_OTP)),
                requiredString(values, KEY_REMOTE_ROOT),
                requiredString(values, KEY_DEVICE_MODEL),
                requiredBoolean(values, KEY_BACKUP_ENABLED),
                requiredString(values, KEY_BACKUP_FOLDER)
        );
    }

    /**
     * 判断单次偏好快照是否包含至少一个配置 schema 键
     *
     * @param values 本次操作固定使用的完整偏好快照
     * @return 任一当前配置键存在时为 true
     */
    private static boolean hasAnyConfigKey(
            Map<String, ?> values // 本次操作固定使用的完整偏好快照
    ) {
        // 逐一检查当前 schema 键以区分从未配置与配置损坏
        for (String /* 当前检查是否存在的配置 schema 键 */ key : REQUIRED_CONFIG_KEYS) {
            if (values.containsKey(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断单次偏好快照是否完整包含全部配置 schema 键
     *
     * @param values 本次操作固定使用的完整偏好快照
     * @return 全部当前配置键均存在时为 true
     */
    private static boolean hasAllConfigKeys(
            Map<String, ?> values // 本次操作固定使用的完整偏好快照
    ) {
        // 逐一检查当前 schema 键，任一缺失都不视为可用配置
        for (String /* 当前检查是否完整存在的配置 schema 键 */ key : REQUIRED_CONFIG_KEYS) {
            if (!values.containsKey(key)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 要求单次偏好快照完整包含全部配置 schema 键
     *
     * @param values 本次操作固定使用的完整偏好快照
     */
    private static void requireCompleteConfig(
            Map<String, ?> values // 本次操作固定使用的完整偏好快照
    ) {
        if (!hasAllConfigKeys(values)) {
            throw new IllegalStateException("群晖配置字段不完整");
        }
    }

    /**
     * 严格读取当前 schema 要求存在的字符串字段
     *
     * @param key 待读取的 SharedPreferences 字段名
     * @return 已保存的字符串值，允许业务字段保存空字符串
     */
    private static String requiredString(
            Map<String, ?> values, // 本次操作固定使用的完整偏好快照
            String key // 待读取且必须保存为字符串的 SharedPreferences 字段名
    ) {
        // 从固定快照读取实际值，避免跨提交组合不同版本字段
        Object value = values.get(key);
        if (!(value instanceof String)) {
            throw new IllegalStateException("群晖配置字段无效: " + key);
        }
        return (String) value;
    }

    /**
     * 严格读取当前 schema 要求存在的布尔字段
     *
     * @param key 待读取的 SharedPreferences 字段名
     * @return 已保存的布尔值
     */
    private static boolean requiredBoolean(
            Map<String, ?> values, // 本次操作固定使用的完整偏好快照
            String key // 待读取且必须保存为布尔值的 SharedPreferences 字段名
    ) {
        // 从固定快照读取实际类型，避免布尔默认值掩盖类型错误
        Object value = values.get(key);
        if (!(value instanceof Boolean)) {
            throw new IllegalStateException("群晖配置字段无效: " + key);
        }
        return (Boolean) value;
    }

    /**
     * 使用随机 GCM IV 和字段名附加数据加密单个敏感字段
     *
     * @param field 绑定密文用途的 SharedPreferences 字段名
     * @param plaintext 待加密的敏感字段明文
     * @return 包含版本、IV 和密文的可持久化文本
     * @throws GeneralSecurityException Keystore 或 AES-GCM 加密失败
     */
    private String encrypt(
            String field, // 作为 GCM 附加数据绑定密文用途的配置字段名
            String plaintext // 待加密且只在当前调用栈使用的敏感字段明文
    ) throws GeneralSecurityException {
        // 创建固定 AES-GCM 转换的单次加密器实例
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
        cipher.updateAAD(field.getBytes(StandardCharsets.UTF_8));
        // 保存包含 GCM 认证标签的密文字节
        byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        return "v1:"
                + Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP)
                + ":"
                + Base64.encodeToString(ciphertext, Base64.NO_WRAP);
    }

    /**
     * 校验版本格式并使用字段名附加数据解密单个敏感字段
     *
     * @param field 绑定密文用途的 SharedPreferences 字段名
     * @param encoded 包含版本、IV 和密文的持久化文本
     * @return UTF-8 编码的敏感字段明文
     * @throws GeneralSecurityException 密文格式、Keystore 或 GCM 认证失败
     */
    private String decrypt(
            String field, // 作为 GCM 附加数据校验密文用途的配置字段名
            String encoded // 包含版本、IV 和密文的持久化文本
    ) throws GeneralSecurityException {
        // 拆分固定的版本、IV 和密文三段格式
        String[] parts = encoded.split(":", -1);
        if (parts.length != 3 || !"v1".equals(parts[0])) {
            throw new GeneralSecurityException("不支持的凭据密文格式");
        }
        // 解码创建该密文时由 GCM 随机生成的 IV
        byte[] iv;
        try {
            iv = Base64.decode(parts[1], Base64.NO_WRAP);
        } catch (IllegalArgumentException /* IV 文本不是合法 Base64 */ error) {
            throw new GeneralSecurityException("凭据 IV 编码无效", error);
        }
        // 解码包含 GCM 认证标签的密文字节
        byte[] ciphertext;
        try {
            ciphertext = Base64.decode(parts[2], Base64.NO_WRAP);
        } catch (IllegalArgumentException /* 密文不是合法 Base64 */ error) {
            throw new GeneralSecurityException("凭据密文编码无效", error);
        }
        // 创建固定 AES-GCM 转换的单次解密器实例
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(128, iv));
        cipher.updateAAD(field.getBytes(StandardCharsets.UTF_8));
        return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
    }

    /**
     * 读取已有 Android Keystore 密钥，首次保存时创建 AES-256-GCM 密钥
     *
     * @return 仅存于 Android Keystore 的凭据加密密钥
     * @throws GeneralSecurityException Keystore 访问或密钥生成失败
     */
    private SecretKey getOrCreateKey() throws GeneralSecurityException {
        // 打开 Android 系统 Keystore，密钥材料不会进入应用存储
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        try {
            keyStore.load(null);
        } catch (java.io.IOException /* Android Keystore 数据加载失败 */ error) {
            throw new GeneralSecurityException("Android Keystore 加载失败", error);
        }
        // 查询当前固定别名下已存在的凭据加密密钥
        SecretKey existing = (SecretKey) keyStore.getKey(KEY_ALIAS, null);
        if (existing != null) {
            return existing;
        }

        // 创建仅允许 AES-GCM 加解密的新 Android Keystore 密钥生成器
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
