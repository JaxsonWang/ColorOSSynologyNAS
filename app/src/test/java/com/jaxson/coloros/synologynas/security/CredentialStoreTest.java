package com.jaxson.coloros.synologynas.security;

import android.content.SharedPreferences;

import org.junit.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.security.GeneralSecurityException;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

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

    /**
     * 创建只实现配置键存在性查询的 SharedPreferences 测试代理
     *
     * @param keys 当前测试声明为已保存的配置键集合
     * @return 按指定集合回答 contains 的偏好存储代理
     */
    private static SharedPreferences preferences(Set<String> keys) {
        return (SharedPreferences) Proxy.newProxyInstance(
                CredentialStoreTest.class.getClassLoader(),
                new Class<?>[]{SharedPreferences.class},
                (
                        Object /* SharedPreferences 代理实例 */ proxy,
                        Method /* 当前被调用的接口方法 */ method,
                        Object[] /* 当前接口调用参数 */ arguments
                ) -> {
                    if ("contains".equals(method.getName())) {
                        return keys.contains(arguments[0]);
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );
    }
}
