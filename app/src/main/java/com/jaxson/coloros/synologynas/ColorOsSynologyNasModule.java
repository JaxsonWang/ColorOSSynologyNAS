package com.jaxson.coloros.synologynas;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import com.jaxson.coloros.synologynas.backup.SharedPreferencesBackupHashStore;
import com.jaxson.coloros.synologynas.backup.SynologyBackupRepository;
import com.jaxson.coloros.synologynas.gallery.GalleryBackupClient;
import com.jaxson.coloros.synologynas.gallery.GalleryRemoteClient;
import com.jaxson.coloros.synologynas.gallery.RemoteGalleryRepository;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.error.HookFailedError;

/**
 * 承担 libxposed 生命周期接入与整组相册 Hook 的依赖装配
 */
public final class ColorOsSynologyNasModule extends XposedModule {
    // 统一标识模块在相册进程中产生的日志
    static final String TAG = "ColorOSSynologyNAS";

    // 保存 libxposed 回调提供的当前进程名，用于精确进程门校验
    private String processName;
    // 标记 Application.attach Hook 是否已经安装，避免重复注册启动 Hook
    private boolean attachHookInstalled;
    // 标记目标相册 Hook 是否已完成整组安装，避免重复替换私有成员
    private boolean targetHooksInstalled;
    // 保存群晖设备运行状态，并跨各 Hook 回调共享连接结果与统计数据
    private final SynologyNasHookState hookState = new SynologyNasHookState(this);

    /**
     * 记录当前模块实例所在进程，供后续包加载阶段执行精确目标判断
     *
     * @param param libxposed 提供的模块加载参数，包含当前进程名
     */
    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        processName = param.getProcessName();
    }

    /**
     * 仅在 ColorOS 相册主进程的首个包加载阶段安装 Application.attach Hook
     *
     * @param param libxposed 提供的包就绪参数，包含包名、类加载器和首包标记
     */
    @Override
    public synchronized void onPackageReady(PackageReadyParam param) {
        if (attachHookInstalled
                || !param.isFirstPackage()
                || !HookPolicy.TARGET_PACKAGE.equals(param.getPackageName())
                || !HookPolicy.TARGET_PACKAGE.equals(processName)) {
            return;
        }

        try {
            hookApplicationAttach(param.getClassLoader());
            attachHookInstalled = true;
        } catch (ReflectiveOperationException /* Application.attach 解析异常 */ error) {
            log(Log.ERROR, TAG, "Application.attach resolution failed", error);
        } catch (HookFailedError | RuntimeException /* 启动 Hook 安装异常 */ error) {
            log(Log.ERROR, TAG, "Application.attach hook failed", error);
        }
    }

    /**
     * Hook Application.attach，以真实相册 Context 和 ClassLoader 安装私有目标
     *
     * @param classLoader 相册包加载阶段提供的类加载器
     * @throws ReflectiveOperationException Application.attach 无法按固定签名解析时抛出
     */
    @SuppressLint("DiscouragedPrivateApi")
    private void hookApplicationAttach(ClassLoader classLoader)
            throws ReflectiveOperationException {
        // 对应 Android Application.attach(Context) 的唯一启动目标
        Method attach = Application.class.getDeclaredMethod("attach", Context.class);
        attach.setAccessible(true);
        hook(attach).intercept(/* Application.attach 的 Hook 调用链 */ chain -> {
            // 获取相册 Application 实际绑定的 Context，避免使用模块包上下文
            Context context = (Context) chain.getArg(0);
            installTargetHooks(context, context.getClassLoader());
            return chain.proceed();
        });
    }

    /**
     * 按既定顺序装配客户端、解析完整私有合约并安装全部目标 Hook
     *
     * @param context 相册 Application 的真实 Context，用于读取包信息和进程内存储
     * @param classLoader 相册私有类的真实类加载器
     */
    private synchronized void installTargetHooks(Context context, ClassLoader classLoader) {
        if (targetHooksInstalled || !isSupportedPackageVersion(context)) {
            return;
        }

        try {
            // 读取模块发布给相册进程的唯一远程配置组
            SharedPreferences remotePreferences = getRemotePreferences(RemoteConfigStore.GROUP);
            // 将 RemotePreferences 解码为 DSM 客户端依赖的配置来源
            RemoteConfigStore remoteConfigStore = new RemoteConfigStore(remotePreferences);
            // 为浏览与 Provider 请求保留原有独立 DSM 会话客户端
            GalleryRemoteClient remoteClient = new GalleryRemoteClient(
                    new RemoteGalleryRepository(remoteConfigStore)
            );
            // 为后台连接状态探测保留原有独立 DSM 会话客户端
            GalleryRemoteClient statusClient = new GalleryRemoteClient(
                    new RemoteGalleryRepository(remoteConfigStore)
            );
            // 将相册原生备份入口连接到同一远程配置和本地上传 hash 索引
            GalleryBackupClient backupClient = new GalleryBackupClient(
                    new SynologyBackupRepository(
                            remoteConfigStore,
                            new SharedPreferencesBackupHashStore(
                                    context.getSharedPreferences(
                                            SharedPreferencesBackupHashStore.PREFERENCES_NAME,
                                            Context.MODE_PRIVATE
                                    )
                            )
                    )
            );
            hookState.initialize(remoteClient);
            // 一次性解析当前相册版本的完整 Hook 与辅助反射成员集合
            HookTargets targets = HookTargetResolver.resolve(classLoader);
            // 集中安装除删除弹窗外的相册 Hook，并共享同一批已解析目标
            GalleryHookInstaller hookInstaller = new GalleryHookInstaller(
                    this,
                    context,
                    getModuleApplicationInfo(),
                    classLoader,
                    targets,
                    remoteClient,
                    statusClient,
                    backupClient,
                    hookState
            );
            hookInstaller.installFeatureHooks();
            // 删除弹窗使用独立 ThreadLocal 作用域，避免与其他 Hook 职责耦合
            DeleteDialogHookInstaller deleteDialogInstaller =
                    new DeleteDialogHookInstaller(this, targets);
            deleteDialogInstaller.install();
            hookInstaller.installProviderHooks();
            hookInstaller.installNasPreloadHook();
            hookInstaller.installGalleryHomeHook();
            hookInstaller.installEntryHook();
            hookInstaller.installSyntheticDevicePresenceHook();
            hookInstaller.installLabelHook();
            hookInstaller.installGalleryCardHooks();
            targetHooksInstalled = true;
            logInfo("remote DSM configuration available: " + remoteClient.isConfigured());
            logInfo("hooks installed for gallery version " + HookPolicy.TARGET_VERSION_CODE);
        } catch (ReflectiveOperationException /* 私有目标整组解析异常 */ error) {
            log(Log.ERROR, TAG, "hook target resolution failed", error);
        } catch (HookFailedError | RuntimeException /* 目标 Hook 安装异常 */ error) {
            log(Log.ERROR, TAG, "hook installation failed", error);
        }
    }

    /**
     * 校验真实相册包名、进程名和 versionCode 是否全部匹配固定适配基线
     *
     * @param context 相册 Application 的真实 Context
     * @return 三项目标信息完全匹配时返回 true
     */
    private boolean isSupportedPackageVersion(Context context) {
        try {
            // 读取目标包当前安装版本，禁止依据模块自身版本或包加载参数推断
            PackageInfo packageInfo = context.getPackageManager()
                    .getPackageInfo(HookPolicy.TARGET_PACKAGE, 0);
            // 使用 long versionCode 与固定 ColorOS 相册构建号精确比较
            long versionCode = packageInfo.getLongVersionCode();
            // 汇总包、进程和版本三项不可放宽的安装条件
            boolean supported = HookPolicy.isSupportedTarget(
                    context.getPackageName(),
                    processName,
                    versionCode
            );
            if (!supported) {
                logInfo("unsupported gallery target; hooks not installed: package="
                        + context.getPackageName() + ", process=" + processName
                        + ", versionCode=" + versionCode);
            }
            return supported;
        } catch (PackageManager.NameNotFoundException /* 相册包信息读取异常 */ error) {
            log(Log.ERROR, TAG, "gallery package lookup failed", error);
            return false;
        }
    }

    /**
     * 以统一标签记录模块信息日志
     *
     * @param message 需要写入 libxposed 日志的信息
     */
    private void logInfo(String message) {
        log(Log.INFO, TAG, message);
    }
}
