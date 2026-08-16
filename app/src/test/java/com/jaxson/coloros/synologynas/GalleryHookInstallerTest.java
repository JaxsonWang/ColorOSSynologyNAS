package com.jaxson.coloros.synologynas;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.jaxson.coloros.synologynas.gallery.GalleryRemoteClient;

import org.junit.Test;

import java.lang.reflect.Executable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 验证业务 Hook 安装器按固定数量和顺序注册全部目标
 */
public final class GalleryHookInstallerTest {
    /**
     * 验证 13 个非删除 Hook 在所有构造依赖成功后按生产顺序安装
     *
     * @throws Exception 测试反射目标或状态 Flow 无法构造时抛出
     */
    @Test
    public void installsThirteenGalleryHooksInProductionOrder() throws Exception {
        // 保存当前版本完整的 Hook 目标测试集合
        HookTargets targets = HookTestTargets.targets();
        // 记录业务安装器提交的目标成员和拦截器
        HookTestRecorder recorder = new HookTestRecorder();
        // 提供不执行远端请求的浏览客户端
        GalleryRemoteClient remoteClient = new GalleryRemoteClient(null);
        // 提供不执行远端请求的独立状态客户端
        GalleryRemoteClient statusClient = new GalleryRemoteClient(null);
        // 保存业务 Hook 共享的原子状态容器
        SynologyNasHookState hookState = new SynologyNasHookState(recorder.xposed());
        // 创建已在首个 Hook 注册前完成状态 Flow 构造的安装器
        GalleryHookInstaller installer = new GalleryHookInstaller(
                recorder.xposed(),
                null,
                null,
                getClass().getClassLoader(),
                targets,
                remoteClient,
                statusClient,
                null,
                hookState
        );

        installer.installFeatureHooks();
        installer.installProviderHooks();
        installer.installNasPreloadHook();
        installer.installGalleryHomeHook();
        installer.installEntryHook();
        installer.installSyntheticDevicePresenceHook();
        installer.installGalleryCardHooks();

        // 保存生产安装流程要求的 13 个业务 Hook 精确顺序
        List<Executable> expectedOrder = List.of(
                targets.readBoolean(),
                targets.readBooleanDefault(),
                targets.cloudSyncProxyConstructor(),
                targets.listNasDevices(),
                targets.readGalleryStats(),
                targets.observeNasStatus(),
                targets.cancelNasDownload(),
                targets.preloadNasMetadata(),
                targets.populateMainTabAlbumGroups(),
                targets.openNasDeviceSpace(),
                targets.evaluateNasExitForRemovedDevice(),
                targets.bindNasAlbumsCard(),
                targets.applyNasAvailability()
        );
        assertEquals(13, expectedOrder.size());
        assertEquals(expectedOrder, recorder.executables());
    }

    /**
     * 验证非群晖和非目标能力分支都原样执行相册原方法
     *
     * @throws Throwable 测试反射目标或拦截器执行失败时抛出
     */
    @Test
    public void preservesOriginalGalleryBehaviorOutsideSynologyBranches() throws Throwable {
        // 保存当前版本完整的 Hook 目标测试集合
        HookTargets targets = HookTestTargets.targets();
        // 记录业务安装器提交的目标成员和拦截器
        HookTestRecorder recorder = new HookTestRecorder();
        // 提供不执行远端请求的浏览客户端
        GalleryRemoteClient remoteClient = new GalleryRemoteClient(null);
        // 保存业务 Hook 共享的原子状态容器
        SynologyNasHookState hookState = new SynologyNasHookState(recorder.xposed());
        // 创建已在首个 Hook 注册前完成状态 Flow 构造的安装器
        GalleryHookInstaller installer = new GalleryHookInstaller(
                recorder.xposed(),
                null,
                null,
                getClass().getClassLoader(),
                targets,
                remoteClient,
                remoteClient,
                null,
                hookState
        );
        installer.installFeatureHooks();
        installer.installProviderHooks();
        installer.installNasPreloadHook();
        installer.installEntryHook();
        installer.installSyntheticDevicePresenceHook();
        installer.installGalleryCardHooks();
        // 统计所有非群晖分支执行相册原方法的次数
        AtomicInteger proceedCalls = new AtomicInteger();
        // 保存非 void 原方法应原样返回的固定对象
        Object originalResult = new Object();
        // 保存其他 NAS 卡片绑定和 availability 使用的实例
        HookTestTargets.Members otherNasBinding = new HookTestTargets.Members();
        otherNasBinding.nasBindingDeviceId = "feiniu-nas";
        // 保存其他 NAS availability 前的群晖原子状态对象
        SynologyNasHookState.NasState originalNasState = hookState.snapshot();

        assertEquals(Boolean.FALSE, HookTestRecorder.invokeHook(
                recorder.hooker(targets.readBoolean()),
                null,
                List.of("other_feature", false, false),
                ignoredArguments /* 当前不参与断言的能力参数 */ -> {
                    proceedCalls.incrementAndGet();
                    return Boolean.FALSE;
                }
        ));
        assertEquals(Boolean.FALSE, HookTestRecorder.invokeHook(
                recorder.hooker(targets.readBooleanDefault()),
                null,
                List.of(1, "other_feature", false),
                ignoredArguments /* 当前不参与断言的资源能力参数 */ -> {
                    proceedCalls.incrementAndGet();
                    return Boolean.FALSE;
                }
        ));
        assertSame(originalResult, HookTestRecorder.invokeHook(
                recorder.hooker(targets.readGalleryStats()),
                null,
                List.of("feiniu-nas"),
                ignoredArguments /* 当前不参与断言的其他 NAS 统计参数 */ -> {
                    proceedCalls.incrementAndGet();
                    return originalResult;
                }
        ));
        assertSame(originalResult, HookTestRecorder.invokeHook(
                recorder.hooker(targets.observeNasStatus()),
                new HookTestTargets.Members(),
                List.of(new Object(), "feiniu-nas"),
                ignoredArguments /* 当前不参与断言的其他 NAS 状态参数 */ -> {
                    proceedCalls.incrementAndGet();
                    return originalResult;
                }
        ));
        assertSame(originalResult, HookTestRecorder.invokeHook(
                recorder.hooker(targets.preloadNasMetadata()),
                new HookTestTargets.Members(),
                List.of(
                        0,
                        new HookTestTargets.Members(),
                        new ArrayList<>(),
                        new CountDownLatch(1)
                ),
                ignoredArguments /* 当前不参与断言的空预加载参数 */ -> {
                    proceedCalls.incrementAndGet();
                    return originalResult;
                }
        ));
        assertSame(originalResult, HookTestRecorder.invokeHook(
                recorder.hooker(targets.openNasDeviceSpace()),
                null,
                Arrays.asList(null, "feiniu-nas", new Object(), 0),
                ignoredArguments /* 当前不参与断言的其他 NAS 入口参数 */ -> {
                    proceedCalls.incrementAndGet();
                    return originalResult;
                }
        ));
        assertSame(originalResult, HookTestRecorder.invokeHook(
                recorder.hooker(targets.evaluateNasExitForRemovedDevice()),
                null,
                List.of(new Object(), false, "feiniu-nas", new Object()),
                ignoredArguments /* 当前不参与断言的其他 NAS 恢复参数 */ -> {
                    proceedCalls.incrementAndGet();
                    return originalResult;
                }
        ));
        assertSame(originalResult, HookTestRecorder.invokeHook(
                recorder.hooker(targets.cancelNasDownload()),
                new Object(),
                List.of(),
                ignoredArguments /* 当前不参与断言的普通下载取消参数 */ -> {
                    proceedCalls.incrementAndGet();
                    return originalResult;
                }
        ));
        assertSame(originalResult, HookTestRecorder.invokeHook(
                recorder.hooker(targets.bindNasAlbumsCard()),
                otherNasBinding,
                List.of(new Object(), 0, new Object()),
                ignoredArguments /* 当前不参与断言的其他 NAS 卡片绑定参数 */ -> {
                    proceedCalls.incrementAndGet();
                    return originalResult;
                }
        ));
        assertSame(originalResult, HookTestRecorder.invokeHook(
                recorder.hooker(targets.applyNasAvailability()),
                otherNasBinding,
                List.of(0, new Object()),
                ignoredArguments /* 当前不参与断言的其他 NAS availability 参数 */ -> {
                    proceedCalls.incrementAndGet();
                    return originalResult;
                }
        ));

        assertEquals(10, proceedCalls.get());
        assertSame(originalNasState, hookState.snapshot());
        assertTrue((Boolean) HookTestRecorder.invokeHook(
                recorder.hooker(targets.readBoolean()),
                null,
                List.of(HookPolicy.FEINIU_FEATURE_FLAG, false, false),
                ignoredArguments /* 目标能力不应执行原方法的参数 */ -> {
                    throw new AssertionError("target feature proceeded");
                }
        ));
    }
}
