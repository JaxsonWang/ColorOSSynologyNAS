package com.jaxson.coloros.synologynas.security;

import android.content.SharedPreferences;

import org.junit.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.security.GeneralSecurityException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/** 验证模块私有凭据存储的完整 schema 判定合同 */
public final class CredentialStoreTest {
    // 保存当前凭据存储 schema 要求同时存在的全部八个键
    private static final Set<String> ALL_KEYS = Set.of(
            "server",
            "username",
            "password",
            "otp",
            "remote_root",
            "device_model",
            "backup_enabled",
            "backup_folder"
    );

    /** 验证完全没有配置键时仍表示用户尚未配置 */
    @Test
    public void returnsNullOnlyWhenNoConfigKeyExists() throws GeneralSecurityException {
        // 创建完全没有群晖配置键的凭据存储
        CredentialStore store = new CredentialStore(preferences(Set.of()));

        assertFalse(store.hasConfig());
        assertNull(store.load());
    }

    /** 验证只有当前 schema 的全部八个键同时存在时配置才可用 */
    @Test
    public void requiresEveryCurrentSchemaKeyForHasConfig() {
        // 创建完整包含当前八键 schema 的凭据存储
        CredentialStore store = new CredentialStore(preferences(ALL_KEYS));

        assertTrue(store.hasConfig());
    }

    /** 验证部分旧配置不会通过默认值补全并继续读取 */
    @Test
    public void rejectsPartialConfigurationInsteadOfApplyingDefaults() {
        // 创建仅残留服务地址字段的不完整旧配置
        CredentialStore store = new CredentialStore(preferences(Set.of("server")));

        // 捕获配置存在性检查产生的明确 schema 损坏错误
        IllegalStateException hasConfigError = assertThrows(
                IllegalStateException.class,
                store::hasConfig
        );
        // 捕获配置读取产生的同一明确 schema 损坏错误
        IllegalStateException loadError = assertThrows(IllegalStateException.class, store::load);

        assertEquals("群晖配置字段不完整", hasConfigError.getMessage());
        assertEquals("群晖配置字段不完整", loadError.getMessage());
    }

    /** 验证单次配置存在性判断只读取一个不可变偏好快照 */
    @Test
    public void readsOnePreferenceSnapshotForEachSchemaDecision() {
        // 记录凭据存储读取完整偏好快照的实际次数
        AtomicInteger snapshotReads = new AtomicInteger();
        // 创建完整配置并禁止生产代码回到逐键读取路径的偏好代理
        CredentialStore store = new CredentialStore(preferences(ALL_KEYS, snapshotReads));

        assertTrue(store.hasConfig());
        assertEquals(1, snapshotReads.get());
    }

    /** 验证单次配置恢复只读取一个不可变偏好快照 */
    @Test
    public void readsOnePreferenceSnapshotForEachConfigurationLoad() {
        // 记录配置恢复读取完整偏好快照的实际次数
        AtomicInteger snapshotReads = new AtomicInteger();
        // 创建完整 schema，并用错误密文格式在 Keystore 访问前结束读取
        CredentialStore store = new CredentialStore(preferences(ALL_KEYS, snapshotReads));

        assertThrows(GeneralSecurityException.class, store::load);
        assertEquals(1, snapshotReads.get());
    }

    /**
     * 创建只允许读取单份完整偏好快照的 SharedPreferences 测试代理
     *
     * @param keys 当前测试声明为已保存的配置键集合
     * @return 按指定键集合返回固定 getAll 快照的偏好存储代理
     */
    private static SharedPreferences preferences(
            Set<String> keys // 当前测试声明为已保存的配置键集合
    ) {
        return preferences(keys, new AtomicInteger());
    }

    /**
     * 创建记录完整偏好快照读取次数的 SharedPreferences 测试代理
     *
     * @param keys 当前测试声明为已保存的配置键集合
     * @param snapshotReads 记录 getAll 调用次数的计数器
     * @return 仅允许通过 getAll 读取固定配置快照的偏好存储代理
     */
    private static SharedPreferences preferences(
            Set<String> keys, // 当前测试声明为已保存的配置键集合
            AtomicInteger snapshotReads // 记录 getAll 调用次数的计数器
    ) {
        // 为当前声明存在的配置键构造固定类型测试值
        Map<String, Object> values = new LinkedHashMap<>();
        for (String /* 当前写入测试快照的配置键 */ key : keys) {
            values.put(key, "backup_enabled".equals(key) ? Boolean.TRUE : "value");
        }
        return (SharedPreferences) Proxy.newProxyInstance(
                CredentialStoreTest.class.getClassLoader(),
                new Class<?>[]{SharedPreferences.class},
                (
                        Object /* SharedPreferences 代理实例 */ proxy,
                        Method /* 当前被调用的接口方法 */ method,
                        Object[] /* 当前接口调用参数 */ arguments
                ) -> {
                    if ("getAll".equals(method.getName())) {
                        snapshotReads.incrementAndGet();
                        return Map.copyOf(values);
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );
    }
}
