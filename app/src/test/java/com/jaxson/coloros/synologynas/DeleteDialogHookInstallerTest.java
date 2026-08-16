package com.jaxson.coloros.synologynas;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

import com.jaxson.coloros.synologynas.gallery.GalleryContract;

import org.junit.Test;

import java.lang.reflect.Executable;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.libxposed.api.XposedInterface;

/**
 * 验证删除正文 Hook 只在两条群晖删除协程的同步调用范围内生效
 */
public final class DeleteDialogHookInstallerTest {
    /**
     * 验证多选、单图和正文三个 Hook 按固定协作顺序安装
     *
     * @throws Exception 测试反射目标无法构造时抛出
     */
    @Test
    public void installsThreeDeleteHooksInProductionOrder() throws Exception {
        // 保存当前版本完整的 Hook 目标测试集合
        HookTargets targets = HookTestTargets.targets();
        // 记录删除安装器提交的目标成员和拦截器
        HookTestRecorder recorder = install(targets);
        // 保存生产安装流程要求的三个删除 Hook 精确顺序
        List<Executable> expectedOrder = List.of(
                targets.showMultiDeleteDialog(),
                targets.showSingleDeleteDialog(),
                targets.setDialogMessage()
        );

        assertEquals(3, expectedOrder.size());
        assertEquals(expectedOrder, recorder.executables());
    }

    /**
     * 验证多选群晖删除只抑制嵌套调用中的正文设置
     *
     * @throws Throwable 被测拦截器执行失败时抛出
     */
    @Test
    public void suppressesMessageOnlyInsideSynologyMultiDeleteCall() throws Throwable {
        // 保存当前版本完整的 Hook 目标测试集合
        HookTargets targets = HookTestTargets.targets();
        // 记录删除安装器提交的目标成员和拦截器
        HookTestRecorder recorder = install(targets);
        // 保存多选群晖删除协程测试实例
        HookTestTargets.Members coroutine = new HookTestTargets.Members();
        coroutine.multiDeleteParams = new HookTestTargets.DeleteParams(GalleryContract.DEVICE_ID);
        // 统计 Builder 原正文方法的实际执行次数
        AtomicInteger messageProceedCalls = new AtomicInteger();

        // 保存多选删除协程嵌套正文设置后的最终返回值
        Object result = invokeDeleteWithMessage(
                recorder,
                targets.showMultiDeleteDialog(),
                coroutine,
                targets.setDialogMessage(),
                messageProceedCalls
        );

        assertSame(coroutine, result);
        assertEquals(0, messageProceedCalls.get());
        invokeStandaloneMessage(recorder, targets.setDialogMessage(), messageProceedCalls);
        assertEquals(1, messageProceedCalls.get());
    }

    /**
     * 验证单图群晖删除沿固定媒体路径恢复身份并抑制正文
     *
     * @throws Throwable 被测反射链或拦截器执行失败时抛出
     */
    @Test
    public void suppressesMessageInsideSynologySingleDeleteCall() throws Throwable {
        // 保存当前版本完整的 Hook 目标测试集合
        HookTargets targets = HookTestTargets.targets();
        // 记录删除安装器提交的目标成员和拦截器
        HookTestRecorder recorder = install(targets);
        // 保存单图群晖删除协程测试实例
        HookTestTargets.Members coroutine = new HookTestTargets.Members();
        coroutine.singleDeleteItemPath = GalleryContract.DEVICE_ID;
        // 统计 Builder 原正文方法的实际执行次数
        AtomicInteger messageProceedCalls = new AtomicInteger();

        invokeDeleteWithMessage(
                recorder,
                targets.showSingleDeleteDialog(),
                coroutine,
                targets.setDialogMessage(),
                messageProceedCalls
        );

        assertEquals(0, messageProceedCalls.get());
    }

    /**
     * 验证非群晖删除始终保留相册原正文设置行为
     *
     * @throws Throwable 被测拦截器执行失败时抛出
     */
    @Test
    public void preservesMessageForOtherNasDeleteCall() throws Throwable {
        // 保存当前版本完整的 Hook 目标测试集合
        HookTargets targets = HookTestTargets.targets();
        // 记录删除安装器提交的目标成员和拦截器
        HookTestRecorder recorder = install(targets);
        // 保存其他 NAS 多选删除协程测试实例
        HookTestTargets.Members coroutine = new HookTestTargets.Members();
        coroutine.multiDeleteParams = new HookTestTargets.DeleteParams("feiniu-nas");
        // 统计 Builder 原正文方法的实际执行次数
        AtomicInteger messageProceedCalls = new AtomicInteger();

        invokeDeleteWithMessage(
                recorder,
                targets.showMultiDeleteDialog(),
                coroutine,
                targets.setDialogMessage(),
                messageProceedCalls
        );

        assertEquals(1, messageProceedCalls.get());
    }

    /**
     * 验证其他 NAS 单图删除沿固定身份链解析后仍完整执行原正文
     *
     * @throws Throwable 被测反射链或拦截器执行失败时抛出
     */
    @Test
    public void preservesMessageForOtherNasSingleDeleteCall() throws Throwable {
        // 保存当前版本完整的 Hook 目标测试集合
        HookTargets targets = HookTestTargets.targets();
        // 记录删除安装器提交的目标成员和拦截器
        HookTestRecorder recorder = install(targets);
        // 保存其他 NAS 单图删除协程测试实例
        HookTestTargets.Members coroutine = new HookTestTargets.Members();
        coroutine.singleDeleteItemPath = "feiniu-nas";
        // 统计 Builder 原正文方法的实际执行次数
        AtomicInteger messageProceedCalls = new AtomicInteger();

        invokeDeleteWithMessage(
                recorder,
                targets.showSingleDeleteDialog(),
                coroutine,
                targets.setDialogMessage(),
                messageProceedCalls
        );

        assertEquals(1, messageProceedCalls.get());
    }

    /**
     * 验证群晖删除原调用抛错后仍清除当前线程的正文抑制作用域
     *
     * @throws Throwable 测试反射目标或被测拦截器执行失败时抛出
     */
    @Test
    public void clearsSuppressionScopeWhenDeleteCallThrows() throws Throwable {
        // 保存当前版本完整的 Hook 目标测试集合
        HookTargets targets = HookTestTargets.targets();
        // 记录删除安装器提交的目标成员和拦截器
        HookTestRecorder recorder = install(targets);
        // 保存多选群晖删除协程测试实例
        HookTestTargets.Members coroutine = new HookTestTargets.Members();
        coroutine.multiDeleteParams = new HookTestTargets.DeleteParams(GalleryContract.DEVICE_ID);
        // 保存已安装的多选删除拦截器
        XposedInterface.Hooker multiDeleteHooker = recorder.hooker(targets.showMultiDeleteDialog());
        // 统计异常结束后 Builder 原正文方法的实际执行次数
        AtomicInteger messageProceedCalls = new AtomicInteger();

        assertThrows(
                ExpectedDeleteFailure.class,
                () -> HookTestRecorder.invokeHook(
                        multiDeleteHooker,
                        coroutine,
                        List.of(new Object()),
                        ignoredArguments /* 当前未参与断言的删除调用参数 */ -> {
                            throw new ExpectedDeleteFailure();
                        }
                )
        );
        invokeStandaloneMessage(recorder, targets.setDialogMessage(), messageProceedCalls);

        assertEquals(1, messageProceedCalls.get());
    }

    /**
     * 验证单图身份解析失败会直接传播且不执行原删除协程
     *
     * @throws Exception 测试反射目标无法构造时抛出
     */
    @Test
    public void propagatesSingleDeleteIdentityResolutionFailure() throws Exception {
        // 保存当前版本完整的 Hook 目标测试集合
        HookTargets targets = HookTestTargets.targets();
        // 记录删除安装器提交的目标成员和拦截器
        HookTestRecorder recorder = install(targets);
        // 保存会触发固定媒体路径解析异常的单图删除协程实例
        HookTestTargets.Members coroutine = new HookTestTargets.Members();
        coroutine.singleDeleteItemPath = "throw";
        // 统计身份解析失败后原删除协程的执行次数
        AtomicInteger deleteProceedCalls = new AtomicInteger();

        assertThrows(
                InvocationTargetException.class,
                () -> HookTestRecorder.invokeHook(
                        recorder.hooker(targets.showSingleDeleteDialog()),
                        coroutine,
                        List.of(new Object()),
                        ignoredArguments /* 当前不应执行的原删除参数 */ -> {
                            deleteProceedCalls.incrementAndGet();
                            return coroutine;
                        }
                )
        );
        assertEquals(0, deleteProceedCalls.get());
    }

    /**
     * 验证 NAS 媒体重载后仍缺少设备身份时直接终止且不执行原删除协程
     *
     * @throws Exception 测试反射目标无法构造时抛出
     */
    @Test
    public void rejectsMissingSingleDeleteDeviceIdAfterMetadataLoad() throws Exception {
        // 保存当前版本完整的 Hook 目标测试集合
        HookTargets targets = HookTestTargets.targets();
        // 记录删除安装器提交的目标成员和拦截器
        HookTestRecorder recorder = install(targets);
        // 保存重载元数据后仍缺少设备标识的单图 NAS 删除协程实例
        HookTestTargets.Members coroutine = new HookTestTargets.Members();
        coroutine.singleDeleteItemPath = "";
        // 统计身份缺失后原删除协程的执行次数
        AtomicInteger deleteProceedCalls = new AtomicInteger();

        assertThrows(
                IllegalStateException.class,
                () -> HookTestRecorder.invokeHook(
                        recorder.hooker(targets.showSingleDeleteDialog()),
                        coroutine,
                        List.of(new Object()),
                        ignoredArguments /* 当前不应执行的原删除参数 */ -> {
                            deleteProceedCalls.incrementAndGet();
                            return coroutine;
                        }
                )
        );
        assertEquals(0, deleteProceedCalls.get());
    }

    /**
     * 创建并执行删除 Hook 安装器
     *
     * @param targets 当前版本完整的 Hook 目标测试集合
     * @return 已记录三个删除 Hook 的接口记录器
     */
    private static HookTestRecorder install(HookTargets targets) {
        // 记录删除安装器提交的目标成员和拦截器
        HookTestRecorder recorder = new HookTestRecorder();
        // 创建绑定测试接口和目标集合的删除 Hook 安装器
        DeleteDialogHookInstaller installer = new DeleteDialogHookInstaller(
                recorder.xposed(),
                targets
        );
        installer.install();
        return recorder;
    }

    /**
     * 在指定删除协程原调用中嵌套执行一次正文设置 Hook
     *
     * @param recorder 保存已安装删除拦截器的记录器
     * @param deleteExecutable 当前验证的多选或单图删除目标
     * @param coroutine 当前删除协程测试实例
     * @param messageExecutable 删除正文设置目标
     * @param messageProceedCalls Builder 原正文方法执行次数
     * @return 删除协程原调用返回值
     * @throws Throwable 被测拦截器执行失败时抛出
     */
    private static Object invokeDeleteWithMessage(
            HookTestRecorder recorder, // 保存已安装删除拦截器的记录器
            Executable deleteExecutable, // 当前验证的删除协程目标
            HookTestTargets.Members coroutine, // 当前删除协程测试实例
            Executable messageExecutable, // 删除正文设置目标
            AtomicInteger messageProceedCalls // Builder 原正文方法执行次数
    ) throws Throwable {
        // 保存已安装的删除协程拦截器
        XposedInterface.Hooker deleteHooker = recorder.hooker(deleteExecutable);
        // 保存已安装的正文设置拦截器
        XposedInterface.Hooker messageHooker = recorder.hooker(messageExecutable);
        return HookTestRecorder.invokeHook(
                deleteHooker,
                coroutine,
                List.of(new Object()),
                ignoredDeleteArguments /* 当前未参与断言的删除调用参数 */ -> HookTestRecorder.invokeHook(
                        messageHooker,
                        coroutine,
                        List.of("FEINIU recycle-bin copy"),
                        ignoredMessageArguments /* 当前未参与断言的正文调用参数 */ -> {
                            messageProceedCalls.incrementAndGet();
                            return coroutine;
                        }
                )
        );
    }

    /**
     * 在任何删除协程作用域外执行一次正文设置 Hook
     *
     * @param recorder 保存已安装正文拦截器的记录器
     * @param messageExecutable 删除正文设置目标
     * @param messageProceedCalls Builder 原正文方法执行次数
     * @throws Throwable 被测拦截器执行失败时抛出
     */
    private static void invokeStandaloneMessage(
            HookTestRecorder recorder, // 保存已安装正文拦截器的记录器
            Executable messageExecutable, // 删除正文设置目标
            AtomicInteger messageProceedCalls // Builder 原正文方法执行次数
    ) throws Throwable {
        // 保存正文设置调用使用的 Builder 测试实例
        HookTestTargets.Members builder = new HookTestTargets.Members();
        HookTestRecorder.invokeHook(
                recorder.hooker(messageExecutable),
                builder,
                List.of("original copy"),
                ignoredArguments /* 当前未参与断言的正文调用参数 */ -> {
                    messageProceedCalls.incrementAndGet();
                    return builder;
                }
        );
    }

    /**
     * 标识删除原调用按测试预期抛出的固定异常
     */
    private static final class ExpectedDeleteFailure extends RuntimeException {
    }
}
