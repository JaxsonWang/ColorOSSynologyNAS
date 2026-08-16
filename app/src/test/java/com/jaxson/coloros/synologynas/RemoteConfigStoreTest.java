package com.jaxson.coloros.synologynas;

import android.content.SharedPreferences;

import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/** 验证远程配置键存在性与严格解码的存储合同 */
public final class RemoteConfigStoreTest {
    /** 验证配置键不存在时表示相册进程尚未收到配置 */
    @Test
    public void returnsUnconfiguredOnlyWhenConfigKeyIsAbsent() {
        // 创建不包含远程配置键的偏好存储
        RemoteConfigStore store = new RemoteConfigStore(preferences(false, null));

        assertFalse(store.hasConfig());
        assertNull(store.load());
    }

    /** 验证已存在的空白配置交由严格 JSON 解码边界明确拒绝 */
    @Test
    public void rejectsBlankValueWhenConfigKeyExists() {
        // 创建键存在但内容损坏的远程配置存储
        RemoteConfigStore store = new RemoteConfigStore(preferences(true, "   "));

        assertTrue(store.hasConfig());
        assertThrows(IllegalStateException.class, store::load);
    }

    /** 验证 RemotePreferences 同步提交失败时不会报告配置已发布 */
    @Test
    public void rejectsFailedRemotePreferenceCommit() {
        // 创建提交固定失败的远程配置存储
        RemoteConfigStore store = new RemoteConfigStore(failedCommitPreferences());
        // 创建覆盖当前全部远程配置字段的有效配置
        SynologyConfig config = new SynologyConfig(
                "https://nas.example.test:5001",
                "user",
                "password",
                "",
                "/home/Photos",
                "DS920+",
                true,
                "ColorOS Backup"
        );

        // 捕获同步提交失败产生的明确发布错误
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> store.save(config)
        );

        assertEquals("群晖远程配置发布失败", error.getMessage());
    }

    /**
     * 创建只实现配置键存在性和文本读取的 SharedPreferences 测试代理
     *
     * @param containsConfig 当前代理是否包含远程配置键
     * @param encodedConfig 当前代理返回的远程配置文本
     * @return 按指定状态回答 contains 和 getString 的偏好存储代理
     */
    private static SharedPreferences preferences(
            boolean containsConfig, // 当前代理是否包含远程配置键
            String encodedConfig // 当前代理返回的远程配置文本
    ) {
        return (SharedPreferences) java.lang.reflect.Proxy.newProxyInstance(
                RemoteConfigStoreTest.class.getClassLoader(),
                new Class<?>[]{SharedPreferences.class},
                (
                        Object /* SharedPreferences 代理实例 */ proxy,
                        Method /* 当前被调用的接口方法 */ method,
                        Object[] /* 当前接口调用参数 */ arguments
                ) -> switch (method.getName()) {
                    case "contains" -> containsConfig;
                    case "getString" -> encodedConfig;
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    /** @return 固定返回同步提交失败的 SharedPreferences 测试代理 */
    private static SharedPreferences failedCommitPreferences() {
        // 创建只接受字符串写入并固定提交失败的编辑器代理
        SharedPreferences.Editor editor = (SharedPreferences.Editor) java.lang.reflect.Proxy
                .newProxyInstance(
                        RemoteConfigStoreTest.class.getClassLoader(),
                        new Class<?>[]{SharedPreferences.Editor.class},
                        (
                                Object /* Editor 代理实例 */ proxy,
                                Method /* 当前被调用的编辑器方法 */ method,
                                Object[] /* 当前编辑器调用参数 */ arguments
                        ) -> switch (method.getName()) {
                            case "putString" -> proxy;
                            case "commit" -> false;
                            default -> throw new UnsupportedOperationException(method.getName());
                        }
                );
        return (SharedPreferences) java.lang.reflect.Proxy.newProxyInstance(
                RemoteConfigStoreTest.class.getClassLoader(),
                new Class<?>[]{SharedPreferences.class},
                (
                        Object /* SharedPreferences 代理实例 */ proxy,
                        Method /* 当前被调用的接口方法 */ method,
                        Object[] /* 当前接口调用参数 */ arguments
                ) -> {
                    if ("edit".equals(method.getName())) {
                        return editor;
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );
    }
}
