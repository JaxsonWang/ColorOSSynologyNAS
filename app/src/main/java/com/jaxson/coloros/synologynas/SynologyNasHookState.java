package com.jaxson.coloros.synologynas;

import android.util.Log;

import com.jaxson.coloros.synologynas.gallery.ColorOsGalleryBridge;
import com.jaxson.coloros.synologynas.gallery.GalleryContract;
import com.jaxson.coloros.synologynas.gallery.GalleryRemoteClient;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;

/**
 * 保存全部 Hook 共享的群晖型号、连接状态与本地照片统计
 */
final class SynologyNasHookState {
    // 提供日志接口并与模块实例保持相同生命周期
    private final XposedInterface xposed;
    // 在单一后台线程中执行 DSM 连接探测，禁止阻塞相册主线程
    private final ExecutorService nasStatusExecutor = Executors.newSingleThreadExecutor(
            /* 负责创建状态探测线程的工厂参数 */ runnable -> {
                // 承载 DSM 状态探测且不阻止相册进程退出的后台线程
                Thread thread = new Thread(runnable, "ColorOSSynologyNAS-status");
                thread.setDaemon(true);
                return thread;
            }
    );
    // 保证同一时刻最多存在一次 DSM 连接状态探测
    private final AtomicBoolean nasStatusRefreshInFlight = new AtomicBoolean();

    // 缓存配置或探测得到的真实 NAS 型号，供首页、列表和卡片共享
    private volatile String currentDeviceModel = GalleryContract.DEFAULT_DEVICE_MODEL;
    // 缓存当前群晖连接状态，供合成设备和 StateFlow 初值共享
    private volatile boolean currentNasConnected = false;
    // 缓存相册私有 DAO 中已保存的群晖照片数量
    private volatile int currentPhotoCount;
    // 标记相册私有 DAO 是否已有群晖统计，决定能否跳过启动预加载
    private volatile boolean hasStoredSynologyMetadata;

    /**
     * 创建与模块实例同生命周期的群晖 Hook 状态容器
     *
     * @param xposed 当前 libxposed 模块接口，用于记录状态探测日志
     */
    SynologyNasHookState(XposedInterface xposed) {
        this.xposed = xposed;
    }

    /**
     * 从已发布配置初始化 NAS 型号，不把配置存在推导为连接成功
     *
     * @param remoteClient 读取远程配置的相册客户端
     */
    void initialize(GalleryRemoteClient remoteClient) {
        try {
            currentDeviceModel = remoteClient.configuredDeviceModel();
        } catch (IOException /* 已保存型号读取异常 */ error) {
            xposed.log(
                    Log.ERROR,
                    ColorOsSynologyNasModule.TAG,
                    "stored Synology device model lookup failed",
                    error
            );
        }
    }

    /**
     * 返回当前共享的 NAS 型号
     *
     * @return 配置缓存或最近探测得到的 NAS 型号
     */
    String deviceModel() {
        return currentDeviceModel;
    }

    /**
     * 返回当前共享的群晖连接状态
     *
     * @return 最近已知连接状态
     */
    boolean connected() {
        return currentNasConnected;
    }

    /**
     * 仅以群晖卡片的 availability 结果更新共享连接状态
     *
     * @param deviceUserId 当前卡片绑定实例保存的 NAS 设备标识
     * @param state 相册 availability 回调传入的连接状态值
     * @return 群晖卡片状态已应用时返回 true，其他 NAS 返回 false
     */
    boolean updateConnectedFromAvailability(String deviceUserId, Integer state) {
        if (!HookPolicy.shouldApplySynologyAvailability(deviceUserId)) {
            return false;
        }
        currentNasConnected = state != null && state == 1;
        return true;
    }

    /**
     * 返回相册 DAO 最近读取的群晖照片数量
     *
     * @return 供 Provider 统计回调使用的照片数量
     */
    int photoCount() {
        return currentPhotoCount;
    }

    /**
     * 记录相册 DAO 已成功读取的群晖照片统计
     *
     * @param storedPhotoCount 相册私有统计 DTO 中的照片数量
     */
    void recordStoredPhotoCount(int storedPhotoCount) {
        currentPhotoCount = storedPhotoCount;
        hasStoredSynologyMetadata = true;
    }

    /**
     * 判断是否已从相册私有 DAO 取得群晖统计元数据
     *
     * @return 已取得可复用统计时返回 true
     */
    boolean hasStoredMetadata() {
        return hasStoredSynologyMetadata;
    }

    /**
     * 直接调用已解析的私有 DAO 方法读取群晖统计，并更新共享状态
     *
     * @param targets 包含固定 DAO 方法的完整相册私有目标集合
     * @return 私有 DAO 返回有效照片数量时返回 true
     * @throws ReflectiveOperationException 私有 DAO 调用或 DTO 读取失败时抛出
     */
    boolean readStoredMetadata(HookTargets targets) throws ReflectiveOperationException {
        // 保存私有 DAO 针对唯一群晖设备标识返回的统计 DTO
        Object galleryStats = targets.readGalleryStats().invoke(null, GalleryContract.DEVICE_ID);
        // 从固定统计 DTO 合约中读取照片数量
        Integer storedPhotoCount = ColorOsGalleryBridge.photoCount(galleryStats);
        if (storedPhotoCount == null) {
            return false;
        }
        recordStoredPhotoCount(storedPhotoCount);
        return true;
    }

    /**
     * 返回当前型号与连接状态的一致快照，供首页模型一次性读取
     *
     * @return 当前群晖设备状态快照
     */
    NasState snapshot() {
        return new NasState(currentDeviceModel, currentNasConnected);
    }

    /**
     * 在单线程执行器中刷新 DSM 连接状态，并将结果写入相册 StateFlow
     *
     * @param classLoader 相册私有状态 DTO 使用的类加载器
     * @param remoteClient 专用于后台状态探测的 DSM 客户端
     * @param nasStatusFlow 需要更新的相册 MutableStateFlow 实例
     */
    void refreshAsync(
            ClassLoader classLoader,
            GalleryRemoteClient remoteClient,
            Object nasStatusFlow
    ) {
        if (!nasStatusRefreshInFlight.compareAndSet(false, true)) {
            return;
        }
        nasStatusExecutor.execute(() -> {
            try {
                // 保存本次 DSM 探测更新后的型号与连接状态
                NasState nasState = refresh(remoteClient);
                ColorOsGalleryBridge.updateStatusFlow(
                        nasStatusFlow,
                        classLoader,
                        nasState.connected()
                );
            } catch (ReflectiveOperationException | RuntimeException
                     /* StateFlow 或相册状态 DTO 更新异常 */ error) {
                xposed.log(
                        Log.ERROR,
                        ColorOsSynologyNasModule.TAG,
                        "Synology NAS async status update failed",
                        error
                );
            } finally {
                nasStatusRefreshInFlight.set(false);
            }
        });
    }

    /**
     * 同步探测一次 DSM 型号，并更新共享型号与连接状态
     *
     * @param remoteClient 专用于状态探测的 DSM 客户端
     * @return 探测完成后的群晖状态快照
     */
    private NasState refresh(GalleryRemoteClient remoteClient) {
        // 在探测失败时保留最近已知型号，避免连接失败改写设备身份展示
        String model = currentDeviceModel;
        try {
            model = remoteClient.probeDeviceModel();
            currentDeviceModel = model;
            currentNasConnected = true;
            logInfo("Synology NAS connected: model=" + model);
        } catch (IOException /* DSM 连接或型号探测异常 */ error) {
            currentDeviceModel = model;
            currentNasConnected = false;
            xposed.log(
                    Log.WARN,
                    ColorOsSynologyNasModule.TAG,
                    "Synology NAS connection probe failed",
                    error
            );
        }
        return new NasState(currentDeviceModel, currentNasConnected);
    }

    /**
     * 以统一模块标签记录状态组件信息日志
     *
     * @param message 需要写入 libxposed 日志的信息
     */
    private void logInfo(String message) {
        xposed.log(Log.INFO, ColorOsSynologyNasModule.TAG, message);
    }

    /**
     * 表达同一时点的群晖型号与连接状态
     *
     * @param deviceModel 最近已知的群晖 NAS 型号
     * @param connected 最近探测或配置映射出的连接状态
     */
    record NasState(
            /* 最近已知的群晖 NAS 型号 */ String deviceModel,
            /* 最近探测或配置映射出的连接状态 */ boolean connected
    ) {
    }
}
