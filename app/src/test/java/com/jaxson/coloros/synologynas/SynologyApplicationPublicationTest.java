package com.jaxson.coloros.synologynas;

import org.junit.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import io.github.libxposed.service.XposedService;

import static org.junit.Assert.assertTrue;

/** 验证服务生命周期与用户保存发布共享同一应用级顺序边界 */
public final class SynologyApplicationPublicationTest {
    /** 验证服务生命周期与凭据保存发布入口共用 SynologyApplication 实例监视器 */
    @Test
    public void serializesServiceLifecycleAndCredentialCommitPublication()
            throws NoSuchMethodException {
        // 定位服务绑定后读取持久配置并发布的回调入口
        Method serviceBind = SynologyApplication.class.getDeclaredMethod(
                "onServiceBind",
                XposedService.class
        );
        // 定位只允许清除当前失效服务的死亡回调入口
        Method serviceDied = SynologyApplication.class.getDeclaredMethod(
                "onServiceDied",
                XposedService.class
        );
        // 定位用户完成连接验证后的凭据保存和远程发布入口
        Method userPublication = SynologyApplication.class.getDeclaredMethod(
                "saveAndPublishConfig",
                SynologyConfig.class
        );
        // 定位只允许两个同步入口内部调用的 RemotePreferences 提交方法
        Method remotePublication = SynologyApplication.class.getDeclaredMethod(
                "publishRemoteConfig",
                SynologyConfig.class
        );

        assertTrue(Modifier.isSynchronized(serviceBind.getModifiers()));
        assertTrue(Modifier.isSynchronized(serviceDied.getModifiers()));
        assertTrue(Modifier.isSynchronized(userPublication.getModifiers()));
        assertTrue(Modifier.isPrivate(remotePublication.getModifiers()));
    }
}
