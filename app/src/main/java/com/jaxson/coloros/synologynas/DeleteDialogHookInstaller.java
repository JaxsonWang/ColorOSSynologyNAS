package com.jaxson.coloros.synologynas;

import android.util.Log;

import io.github.libxposed.api.XposedInterface;

/**
 * 安装群晖删除确认框作用域 Hook，仅移除不适用于 DSM 的飞牛回收站正文
 */
final class DeleteDialogHookInstaller {
    // 提供 Hook 注册和模块日志能力
    private final XposedInterface xposed;
    // 保存删除请求身份解析和对话框正文设置所需的精确私有成员
    private final HookTargets targets;
    // 记录当前线程嵌套进入群晖删除协程的深度，限制正文抑制作用域
    private final ThreadLocal<Integer> deleteMessageSuppressionDepth = new ThreadLocal<>();

    /**
     * 创建绑定到同一模块接口和完整目标集合的删除弹窗安装器
     *
     * @param xposed 当前 libxposed 模块接口
     * @param targets 已按固定相册版本解析的完整私有目标集合
     */
    DeleteDialogHookInstaller(XposedInterface xposed, HookTargets targets) {
        this.xposed = xposed;
        this.targets = targets;
    }

    /**
     * 依次安装多选删除、单图删除和 Builder 正文三个协同 Hook
     */
    void install() {
        xposed.hook(targets.showMultiDeleteDialog()).intercept(
                /* 多选删除确认框协程的 Hook 调用链 */ chain -> {
                    // 保存当前多选删除请求对应的设备标识
                    String deviceUserId;
                    try {
                        // 从协程实例读取固定的多选删除请求参数
                        Object params = targets.multiDeleteParams().get(chain.getThisObject());
                        deviceUserId = (String) targets.deleteParamsDeviceId().get(params);
                    } catch (ReflectiveOperationException | RuntimeException
                             /* 多选删除设备身份解析异常 */ error) {
                        logError("multi-select delete dialog device lookup failed", error);
                        return chain.proceed();
                    }
                    return interceptDeleteDialog(chain, deviceUserId);
                }
        );

        xposed.hook(targets.showSingleDeleteDialog()).intercept(
                /* 单图删除确认框协程的 Hook 调用链 */ chain -> {
                    // 保存当前单图删除请求对应的设备标识
                    String deviceUserId;
                    try {
                        deviceUserId = resolveSingleDeleteDeviceId(chain.getThisObject());
                    } catch (ReflectiveOperationException | RuntimeException
                             /* 单图删除设备身份解析异常 */ error) {
                        logError("single-photo delete dialog device lookup failed", error);
                        return chain.proceed();
                    }
                    return interceptDeleteDialog(chain, deviceUserId);
                }
        );

        xposed.hook(targets.setDialogMessage()).intercept(
                /* ColorOS 对话框正文设置方法的 Hook 调用链 */ chain -> {
                    // 读取当前线程进入群晖删除协程的嵌套深度
                    Integer suppressionDepth = deleteMessageSuppressionDepth.get();
                    if (suppressionDepth == null || suppressionDepth == 0) {
                        return chain.proceed();
                    }
                    logInfo("removed FEINIU recycle-bin copy from Synology delete dialog");
                    return chain.getThisObject();
                }
        );
    }

    /**
     * 沿相册固定媒体路径解析链恢复单图删除请求的 NAS 设备标识
     *
     * @param dialogCoroutine 当前单图删除确认框协程实例
     * @return NAS 媒体对象中的设备标识；非目标媒体对象返回 null
     * @throws ReflectiveOperationException 任一固定字段或辅助方法调用失败时抛出
     */
    private String resolveSingleDeleteDeviceId(Object dialogCoroutine)
            throws ReflectiveOperationException {
        // 从删除协程固定字段读取当前单图的字符串路径
        String itemPath = (String) targets.singleDeleteItemPath().get(dialogCoroutine);
        // 将字符串路径解析为相册私有媒体路径对象
        Object mediaPath = targets.parseMediaPath().invoke(null, itemPath);
        // 根据媒体路径解析相册持有的媒体对象
        Object mediaItem = targets.resolveMediaObject().invoke(null, mediaPath);
        if (!targets.nasMediaDeviceId().getDeclaringClass().isInstance(mediaItem)) {
            return null;
        }
        // 优先读取媒体对象当前已装载的 NAS 设备标识
        String deviceUserId = (String) targets.nasMediaDeviceId().get(mediaItem);
        if (deviceUserId == null || deviceUserId.isEmpty()) {
            targets.loadNasMediaMetadata().invoke(mediaItem);
            deviceUserId = (String) targets.nasMediaDeviceId().get(mediaItem);
        }
        return deviceUserId;
    }

    /**
     * 仅在群晖删除协程同步调用范围内启用正文抑制标记并保持嵌套语义
     *
     * @param chain 当前删除确认框协程的 Hook 调用链
     * @param deviceUserId 从当前删除请求恢复的 NAS 设备标识
     * @return 原删除协程的返回值
     * @throws Throwable 原删除协程抛出的异常保持原样传播
     */
    private Object interceptDeleteDialog(
            XposedInterface.Chain chain,
            String deviceUserId
    ) throws Throwable {
        if (!HookPolicy.shouldSuppressSynologyDeleteMessage(deviceUserId)) {
            return chain.proceed();
        }
        // 读取当前线程已有的嵌套深度，首次进入时按零处理
        Integer currentDepth = deleteMessageSuppressionDepth.get();
        // 保存进入本层前的深度，以便 finally 精确恢复
        int previousDepth = currentDepth == null ? 0 : currentDepth;
        deleteMessageSuppressionDepth.set(previousDepth + 1);
        try {
            return chain.proceed();
        } finally {
            if (previousDepth == 0) {
                deleteMessageSuppressionDepth.remove();
            } else {
                deleteMessageSuppressionDepth.set(previousDepth);
            }
        }
    }

    /**
     * 以统一模块标签记录删除弹窗信息日志
     *
     * @param message 需要写入 libxposed 日志的信息
     */
    private void logInfo(String message) {
        xposed.log(Log.INFO, ColorOsSynologyNasModule.TAG, message);
    }

    /**
     * 以统一模块标签记录删除弹窗错误日志
     *
     * @param message 描述失败阶段的信息
     * @param error 需要保留堆栈的实际异常
     */
    private void logError(String message, Throwable error) {
        xposed.log(Log.ERROR, ColorOsSynologyNasModule.TAG, message, error);
    }
}
