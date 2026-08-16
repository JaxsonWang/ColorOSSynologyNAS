package com.jaxson.coloros.synologynas;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.util.Log;

import com.jaxson.coloros.synologynas.gallery.ColorOsGalleryBridge;
import com.jaxson.coloros.synologynas.gallery.ColorOsNasProviderProxy;
import com.jaxson.coloros.synologynas.gallery.GalleryBackupClient;
import com.jaxson.coloros.synologynas.gallery.GalleryContract;
import com.jaxson.coloros.synologynas.gallery.GalleryRemoteClient;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;

import io.github.libxposed.api.XposedInterface;

/**
 * 安装相册能力、Provider、页面入口、状态和卡片等业务 Hook
 */
final class GalleryHookInstaller {
    // 标识模块配置页所在的 Android 包
    private static final String MODULE_PACKAGE = "com.jaxson.coloros.synologynas";
    // 标识相册“设备管理”需要打开的模块配置 Activity
    private static final String MODULE_ACTIVITY = MODULE_PACKAGE + ".MainActivity";

    // 提供 Hook 注册和模块日志能力
    private final XposedInterface xposed;
    // 保存相册真实 Context，供卡片资源与设备管理入口使用
    private final Context context;
    // 保存模块 ApplicationInfo，供相册进程加载群晖 Logo 资源
    private final ApplicationInfo moduleApplicationInfo;
    // 保存相册私有类加载器，供 DTO、StateFlow 与 Provider 代理创建使用
    private final ClassLoader classLoader;
    // 保存当前相册版本完整且已验证的反射目标集合
    private final HookTargets targets;
    // 保存浏览、Provider 和配置状态读取使用的 DSM 客户端
    private final GalleryRemoteClient remoteClient;
    // 保存后台连接状态探测专用的独立 DSM 客户端
    private final GalleryRemoteClient statusClient;
    // 保存相册原生 NAS 备份请求使用的群晖备份客户端
    private final GalleryBackupClient backupClient;
    // 保存全部 Hook 共享且唯一的群晖型号、连接和照片统计状态
    private final SynologyNasHookState hookState;
    // 保存首个 Hook 注册前创建成功的群晖设备状态 Flow
    private final Object nasStatusFlow;

    /**
     * 创建绑定到同一依赖组和完整私有目标集合的业务 Hook 安装器
     *
     * @param xposed 当前 libxposed 模块接口
     * @param context 相册 Application 的真实 Context
     * @param moduleApplicationInfo 模块自身资源对应的 ApplicationInfo
     * @param classLoader 相册私有类的真实类加载器
     * @param targets 当前相册版本的完整私有目标集合
     * @param remoteClient 浏览、Provider 与配置判断使用的 DSM 客户端
     * @param statusClient 后台连接状态探测使用的独立 DSM 客户端
     * @param backupClient 相册原生备份入口使用的群晖客户端
     * @param hookState 全部 Hook 共享的唯一群晖运行状态
     * @throws ReflectiveOperationException 相册状态 Flow 初值无法创建时抛出
     */
    GalleryHookInstaller(
            XposedInterface xposed, // 提供 Hook 注册和模块日志的接口
            Context context, // 相册 Application 实际绑定的 Context
            ApplicationInfo moduleApplicationInfo, // 模块自身资源对应的应用信息
            ClassLoader classLoader, // 解析相册私有类型的真实类加载器
            HookTargets targets, // 已完整解析的相册私有成员集合
            GalleryRemoteClient remoteClient, // 浏览和 Provider 请求使用的客户端
            GalleryRemoteClient statusClient, // 后台状态探测专用客户端
            GalleryBackupClient backupClient, // 相册原生备份请求使用的客户端
            SynologyNasHookState hookState // 全部业务 Hook 共享的状态容器
    ) throws ReflectiveOperationException {
        this.xposed = xposed;
        this.context = context;
        this.moduleApplicationInfo = moduleApplicationInfo;
        this.classLoader = classLoader;
        this.targets = targets;
        this.remoteClient = remoteClient;
        this.statusClient = statusClient;
        this.backupClient = backupClient;
        this.hookState = hookState;
        this.nasStatusFlow = ColorOsGalleryBridge.mutableStatusFlow(
                classLoader,
                hookState.connected()
        );
    }

    /**
     * 仅为相册原有飞牛 NAS 页面体系强制返回已启用
     */
    void installFeatureHooks() {
        xposed.hook(targets.readBoolean()).intercept(
                /* 字符串能力配置读取方法的 Hook 调用链 */ chain -> {
                    // 保存相册本次读取的能力配置标识
                    String configId = (String) chain.getArg(0);
                    if (HookPolicy.shouldForceFeature(configId)) {
                        return true;
                    }
                    return chain.proceed();
                }
        );
        xposed.hook(targets.readBooleanDefault()).intercept(
                /* 资源能力配置读取方法的 Hook 调用链 */ chain -> {
                    // 保存相册本次读取的能力配置标识
                    String configId = (String) chain.getArg(1);
                    if (HookPolicy.shouldForceFeature(configId)) {
                        return true;
                    }
                    return chain.proceed();
                }
        );
    }

    /**
     * 安装 Provider 替换、设备注入、统计读取、状态 Flow 与下载句柄 Hook
     */
    void installProviderHooks() {
        xposed.hook(targets.cloudSyncProxyConstructor()).intercept(
                /* CloudSyncProxyDM 构造完成后的 Hook 调用链 */ chain -> {
                    // 保留原构造器执行结果，并在实例初始化后替换 FEINIU Provider
                    Object result = chain.proceed();
                    // 标记本次构造是否首次完成 FEINIU Provider 替换
                    boolean replaced = ColorOsGalleryBridge.replaceProvider(
                            chain.getThisObject(),
                            classLoader,
                            remoteClient,
                            backupClient,
                            hookState::photoCount
                    );
                    if (replaced) {
                        logInfo("replaced FEINIU provider with Synology DSM 7 provider");
                    }
                    return result;
                }
        );

        xposed.hook(targets.listNasDevices()).intercept(
                /* NAS 设备列表读取方法的 Hook 调用链 */ chain -> {
                    // 保存相册原方法返回的完整 NAS 设备列表
                    Object result = chain.proceed();
                    // 将安装前已确认类型的原列表传给群晖设备注入桥接层
                    ArrayList<?> devices = (ArrayList<?>) result;
                    if (!ColorOsGalleryBridge.isConfiguredManager(chain.getThisObject())) {
                        return result;
                    }
                    // 一次读取当前群晖型号与连接状态，避免同一设备混用跨时点数据
                    SynologyNasHookState.NasState nasState = hookState.snapshot();
                    // 保存保留其他设备且重新注入唯一群晖项的新列表
                    ArrayList<Object> devicesWithSynology = ColorOsGalleryBridge.withSynologyDevice(
                            devices,
                            classLoader,
                            nasState.deviceModel(),
                            nasState.connected()
                    );
                    logInfo("injected Synology DSM 7 device into ColorOS gallery");
                    return devicesWithSynology;
                }
        );

        xposed.hook(targets.readGalleryStats()).intercept(
                /* NAS 本地统计读取方法的 Hook 调用链 */ chain -> {
                    // 保存本次统计查询对应的设备标识
                    String deviceUserId = (String) chain.getArg(0);
                    if (!GalleryContract.DEVICE_ID.equals(deviceUserId)) {
                        return chain.proceed();
                    }
                    // 保存相册原 DAO 返回的群晖统计 DTO
                    Object result = chain.proceed();
                    // 从统计 DTO 中读取可供 Provider 和预加载逻辑复用的照片数量
                    Integer storedPhotoCount = ColorOsGalleryBridge.photoCount(result);
                    if (storedPhotoCount != null) {
                        hookState.recordStoredPhotoCount(storedPhotoCount);
                    }
                    return result;
                }
        );

        xposed.hook(targets.observeNasStatus()).intercept(
                /* NAS 连接状态观察方法的 Hook 调用链 */ chain -> {
                    // 保存本次状态观察对应的设备标识
                    String deviceUserId = (String) chain.getArg(1);
                    if (!GalleryContract.DEVICE_ID.equals(deviceUserId)) {
                        return chain.proceed();
                    }
                    hookState.refreshAsync(classLoader, statusClient, nasStatusFlow);
                    return nasStatusFlow;
                }
        );

        xposed.hook(targets.cancelNasDownload()).intercept(
                /* NAS 下载句柄 cancel 方法的 Hook 调用链 */ chain -> {
                    if (ColorOsNasProviderProxy.shouldSuppressCancel(chain.getThisObject())) {
                        return null;
                    }
                    return chain.proceed();
                }
        );
    }

    /**
     * 跳过已有本地统计的连续群晖启动预加载项，保持相册主线程可用
     */
    void installNasPreloadHook() {
        xposed.hook(targets.preloadNasMetadata()).intercept(
                /* NAS 启动元数据预加载方法的 Hook 调用链 */ chain -> {
                    // 保存相册本次准备预加载的设备列表位置
                    int currentIndex = (int) chain.getArg(0);
                    // 保存相册启动预加载使用的可变 NAS 设备列表
                    @SuppressWarnings("unchecked")
                    ArrayList<Object> devices = (ArrayList<Object>) chain.getArg(2);
                    if (currentIndex >= devices.size()) {
                        return chain.proceed();
                    }

                    // 保存跳过连续群晖项后应继续处理的位置
                    int nextIndex;
                    // 先确认当前位置确实属于群晖项，避免无意义读取私有 DAO
                    int nextIndexIfStored = ColorOsGalleryBridge.nextPreloadIndex(
                            devices,
                            currentIndex,
                            true
                    );
                    if (nextIndexIfStored == currentIndex) {
                        return chain.proceed();
                    }
                    // 合并 Hook 已读统计与当前私有 DAO 查询结果
                    boolean hasStoredMetadata = hookState.hasStoredMetadata()
                            || hookState.readStoredMetadata(targets);
                    if (!hasStoredMetadata) {
                        return chain.proceed();
                    }
                    nextIndex = nextIndexIfStored;

                    logInfo("skipped Synology startup metadata preload; local photo count="
                            + hookState.photoCount());
                    if (nextIndex >= devices.size()) {
                        // 完成列表末尾跳过时主动释放相册原预加载等待者
                        CountDownLatch completion = (CountDownLatch) chain.getArg(3);
                        completion.countDown();
                        return null;
                    }

                    // 复制原调用参数，只将递归处理位置推进到下一个非群晖设备
                    Object[] args = chain.getArgs().toArray();
                    args[0] = nextIndex;
                    return chain.proceed(args);
                }
        );
    }

    /**
     * 在 DSM 已配置时向相册首页原生分组注入或更新唯一群晖入口
     */
    void installGalleryHomeHook() {
        xposed.hook(targets.populateMainTabAlbumGroups()).intercept(
                /* 相册首页分组填充方法的 Hook 调用链 */ chain -> {
                    // 保存相册首页原方法的返回值，注入完成后保持原值返回
                    Object result = chain.proceed();
                    if (!remoteClient.isConfigured()) {
                        return result;
                    }
                    // 一次读取当前群晖型号与连接状态，避免同一入口混用不同时点数据
                    SynologyNasHookState.NasState nasState = hookState.snapshot();
                    if (ColorOsGalleryBridge.ensureSynologyHomeEntry(
                            chain.getThisObject(),
                            classLoader,
                            nasState.deviceModel(),
                            nasState.connected()
                    )) {
                        logInfo("injected Synology private cloud album into ColorOS gallery home");
                    }
                    return result;
                }
        );
    }

    /**
     * 将群晖设备管理动作或未配置入口精确导向模块配置页
     */
    void installEntryHook() {
        xposed.hook(targets.openNasDeviceSpace()).intercept(
                /* NAS 页面与设备管理入口方法的 Hook 调用链 */ chain -> {
                    // 保存相册入口方法传入的真实 Context
                    Context entryContext = (Context) chain.getArg(0);
                    // 保存相册入口方法传入的 NAS 设备标识
                    String deviceUserId = (String) chain.getArg(1);
                    if (!GalleryContract.DEVICE_ID.equals(deviceUserId)) {
                        return chain.proceed();
                    }
                    // 保存当前版本确认的入口动作标记
                    int actionFlags = (int) chain.getArg(3);
                    if (remoteClient.isConfigured()
                            && !HookPolicy.shouldOpenSynologyDeviceManager(actionFlags)) {
                        return null;
                    }
                    // 构造只指向模块配置页的显式 Intent，不携带无人消费的额外状态
                    Intent intent = new Intent()
                            .setComponent(new ComponentName(MODULE_PACKAGE, MODULE_ACTIVITY))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    entryContext.startActivity(intent);
                    logInfo(HookPolicy.shouldOpenSynologyDeviceManager(actionFlags)
                            ? "opened Synology device management from ColorOS gallery"
                            : "opened Synology configuration for offline synthetic device");
                    return null;
                }
        );
    }

    /**
     * 让页面恢复检查将唯一群晖合成设备视为未移除
     */
    void installSyntheticDevicePresenceHook() {
        xposed.hook(targets.evaluateNasExitForRemovedDevice()).intercept(
                /* 页面恢复设备移除检查方法的 Hook 调用链 */ chain -> {
                    // 保存相册当前检查的 NAS 设备标识
                    String deviceUserId = (String) chain.getArg(2);
                    if (!GalleryContract.DEVICE_ID.equals(deviceUserId)) {
                        return chain.proceed();
                    }
                    // 保存相册等待设备移除判断结果的 Kotlin 回调对象
                    Object onResult = chain.getArg(3);
                    targets.functionOneInvoke().invoke(onResult, Boolean.FALSE);
                    logInfo("kept Synology DSM device active during NAS page resume");
                    return null;
                }
        );
    }

    /**
     * 安装群晖卡片品牌样式和连接状态刷新 Hook
     */
    void installGalleryCardHooks() {
        xposed.hook(targets.bindNasAlbumsCard()).intercept(
                /* 私有云图集卡片绑定方法的 Hook 调用链 */ chain -> {
                    // 保存相册原卡片绑定方法的返回值
                    Object result = chain.proceed();
                    // 一次读取当前群晖型号与连接状态，避免同一卡片混用跨时点数据
                    SynologyNasHookState.NasState nasState = hookState.snapshot();
                    ColorOsGalleryBridge.applySynologyCardBranding(
                            context,
                            moduleApplicationInfo,
                            chain.getThisObject(),
                            chain.getArg(2),
                            nasState.deviceModel(),
                            nasState.connected()
                    );
                    return result;
                }
        );
        xposed.hook(targets.applyNasAvailability()).intercept(
                /* 私有云图集 availability 刷新方法的 Hook 调用链 */ chain -> {
                    // 保存相册原连接状态刷新方法的返回值
                    Object result = chain.proceed();
                    // 从当前 binding 的固定字段读取实际设备，禁止其他 NAS 污染群晖状态
                    String deviceUserId = (String) targets.nasBindingDeviceId()
                            .get(chain.getThisObject());
                    // 保存当前卡片 availability 回调携带的相册连接状态值
                    Integer state = (Integer) chain.getArg(0);
                    if (!hookState.updateConnectedFromAvailability(deviceUserId, state)) {
                        return result;
                    }
                    // 一次读取刚由群晖卡片更新的型号与连接状态原子快照
                    SynologyNasHookState.NasState nasState = hookState.snapshot();
                    ColorOsGalleryBridge.applySynologyConnectionLabel(
                            chain.getThisObject(),
                            nasState.deviceModel(),
                            nasState.connected()
                    );
                    return result;
                }
        );
    }

    /**
     * 以统一模块标签记录业务 Hook 信息日志
     *
     * @param message 需要写入 libxposed 日志的信息
     */
    private void logInfo(String message) {
        xposed.log(Log.INFO, ColorOsSynologyNasModule.TAG, message);
    }

}
