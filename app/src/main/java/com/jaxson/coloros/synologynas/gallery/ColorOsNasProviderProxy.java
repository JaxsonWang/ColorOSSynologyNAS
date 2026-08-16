package com.jaxson.coloros.synologynas.gallery;

import android.util.Log;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.function.IntSupplier;

// 在 FEINIU 槽位按设备标识分流群晖和原 Provider 请求
public final class ColorOsNasProviderProxy implements InvocationHandler {
    // 标识日志所属模块，便于从相册进程日志中筛选群晖调用
    private static final String TAG = "ColorOSSynologyNAS";
    // 定位 ColorOS 相册用于复用原生 NAS 页面体系的 Provider 枚举
    private static final String NAS_PROVIDER =
            "com.oplus.gallery.business_lib.nas.NasProvider";
    // 定位 ColorOS 相册用于表达 NAS Photos 可用性的枚举
    private static final String NAS_AVAILABILITY =
            "com.oplus.gallery.business_lib.nas.NasPhotosAvailabilityStatus";
    // 定位 ColorOS 相册用于表达 NAS Photos 运行状态的枚举
    private static final String NAS_APP_STATUS =
            "com.oplus.gallery.business_lib.nas.NasPhotosAppStatus";

    // 保留原飞牛 Provider，非群晖请求必须原样转发到该实例
    private final Object original;
    // 执行群晖相册浏览、预览与删除请求
    private final GalleryRemoteClient client;
    // 执行群晖备份可用性、查重与上传请求
    private final GalleryBackupService backupService;
    // 保留 FEINIU 枚举槽位，以复用 ColorOS 原生 NAS 页面
    private final Object feiniuProvider;
    // 提供相册进程已经缓存的照片数量，避免统计接口触发远端扫描
    private final IntSupplier photoCount;
    // 将内部相册模型映射为当前 ColorOS 私有 DTO
    private final ColorOsNasDtoMapper dtoMapper;
    // 将备份领域结果映射为当前 ColorOS 私有 DTO
    private final ColorOsNasBackupResultMapper backupResultMapper;
    // 构造 ColorOS 原图下载完成句柄并维护取消抑制集合
    private final ColorOsNasDownloadAdapter downloadAdapter;

    // 绑定原 Provider、群晖客户端和当前 ColorOS 私有反射合约
    private ColorOsNasProviderProxy(
            GalleryRemoteClient client, // 执行群晖远端图库操作的客户端
            GalleryBackupService backupService, // 执行群晖备份操作的服务
            Object original, // 接收非群晖请求的原飞牛 Provider
            ClassLoader classLoader, // 解析 ColorOS 私有类型的相册类加载器
            IntSupplier photoCount // 读取相册侧缓存照片数的供应器
    ) throws ReflectiveOperationException {
        this.original = original;
        this.client = client;
        this.backupService = backupService;
        this.photoCount = photoCount;
        feiniuProvider = ColorOsNasReflection.enumValue(
                classLoader,
                NAS_PROVIDER,
                "FEINIU"
        );
        dtoMapper = new ColorOsNasDtoMapper(classLoader, feiniuProvider);
        backupResultMapper = new ColorOsNasBackupResultMapper(classLoader, backupService);
        downloadAdapter = new ColorOsNasDownloadAdapter(classLoader);
    }

    // 创建实现 ColorOS dpk 接口的群晖动态代理，同时保留原 Provider 转发链路
    public static Object create(
            GalleryRemoteClient client, // 执行群晖远端图库操作的客户端
            GalleryBackupService backupService, // 执行群晖备份操作的服务
            Object original, // 接收非群晖请求的原飞牛 Provider
            ClassLoader classLoader, // 解析 ColorOS 私有类型的相册类加载器
            IntSupplier photoCount // 读取相册侧缓存照片数的供应器
    ) throws ReflectiveOperationException {
        // 约束代理只实现当前相册版本的 dpk Provider 接口
        Class<?> providerInterface = Class.forName(
                "com.oplus.aiunit.vision.dpk",
                false,
                classLoader
        );
        // 承载群晖分流与原 Provider 转发语义的调用处理器
        ColorOsNasProviderProxy handler = new ColorOsNasProviderProxy(
                client,
                backupService,
                original,
                classLoader,
                photoCount
        );
        return Proxy.newProxyInstance(
                providerInterface.getClassLoader(),
                new Class<?>[]{providerInterface},
                handler
        );
    }

    // 判断 Provider 是否已经由本模块替换，避免重复包装原 Provider
    public static boolean isSynologyProvider(Object value /* 待识别的 Provider 实例 */) {
        if (value == null || !Proxy.isProxyClass(value.getClass())) {
            return false;
        }
        return Proxy.getInvocationHandler(value) instanceof ColorOsNasProviderProxy;
    }

    // 消费一次合成下载句柄标记，使 ColorOS 的后续 cancel 调用直接结束
    public static boolean shouldSuppressCancel(Object value /* 待取消的原图下载句柄 */) {
        return ColorOsNasDownloadAdapter.shouldSuppressCancel(value);
    }

    // 返回当前群晖配置状态，供 Provider 注册表和设备入口判断
    public boolean isConfigured() {
        return client.isConfigured();
    }

    @Override
    @SuppressWarnings("unchecked")
    // 按 dpk 方法名和目标设备精确分流群晖请求，其余调用转发原 Provider
    public Object invoke(
            Object proxy, // JVM 创建并回传给调用处理器的动态代理
            Method method, // ColorOS 当前调用的 dpk 或 Object 方法
            Object[] args // ColorOS 按当前私有合约传入的原始参数
    ) throws Throwable {
        // 使用混淆后的方法名匹配当前 16.50.8 Provider 合约
        String name = method.getName();
        if (method.getDeclaringClass() == Object.class) {
            return invokeObjectMethod(proxy, name, args);
        }
        if ("b".equals(name)) {
            return feiniuProvider;
        }
        if ("c".equals(name)) {
            return true;
        }
        if (!targetsSynology(name, args)) {
            return invokeOriginal(method, args);
        }

        return switch (name) {
            case "a" -> getAlbum((String) args[1]);
            case "d" -> backupService.isEnabled() ? 1 : 0;
            case "e" -> dtoMapper.enumValue(
                    NAS_AVAILABILITY,
                    client.isConfigured() ? "AVAILABLE" : "UNKNOWN"
            );
            case "g" -> throw new UnsupportedOperationException(
                    "Synology image provider does not expose video size"
            );
            case "h" -> throw new UnsupportedOperationException(
                    "Synology provider does not expose a Feiniu app installation URL"
            );
            case "i" -> backupResultMapper.hashResult((List<String>) args[1]);
            // 群晖 HTTP 会话按请求延迟创建，不参与飞牛设备连接状态机
            case "j", "q", "u" -> null;
            case "k", "r" -> backupResultMapper.upload(args[0]);
            case "l" -> listAlbums((Integer) args[0], (Integer) args[1]);
            case "m" -> backupService.isEnabled() ? 1 : 0;
            case "n" -> dtoMapper.enumValue(
                    NAS_APP_STATUS,
                    client.isConfigured() ? "RUNNING" : "STOPPED"
            );
            case "o" -> dtoMapper.galleryStats(photoCount.getAsInt());
            case "p" -> deletePhotos((List<String>) args[1]);
            case "s" -> backupResultMapper.hashResult((List<String>) args[1]);
            case "t" -> listPhotos(
                    (String) args[3],
                    (Integer) args[0],
                    (Integer) args[1]
            );
            case "v" -> throw new UnsupportedOperationException(
                    "Synology image provider does not expose video byte ranges"
            );
            case "w" -> download((String) args[1], args[2]);
            case "x" -> thumbnail((String) args[1], args[2]);
            default -> throw new IllegalStateException(
                    "Unsupported ColorOS dpk method: " + name
            );
        };
    }

    // 保持动态代理的 Object 基础语义与原实现一致
    private Object invokeObjectMethod(
            Object proxy, // JVM 创建的动态代理实例
            String name, // 当前 Object 方法名
            Object[] args // Object 方法的原始参数
    ) {
        return switch (name) {
            case "toString" -> "SynologyDsm7NasProviderProxy";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            default -> throw new IllegalStateException("Unsupported Object method: " + name);
        };
    }

    // 根据各 dpk 方法的参数位置判断本次请求是否明确指向群晖设备
    private boolean targetsSynology(
            String methodName, // 当前混淆后的 dpk 方法名
            Object[] args // 用于读取设备标识或备份请求 DTO 的参数
    ) {
        if (args == null) {
            return false;
        }
        if (("k".equals(methodName) || "r".equals(methodName)) && args.length > 0) {
            try {
                return GalleryContract.DEVICE_ID.equals(
                        GalleryBackupClient.targetDeviceId(args[0])
                );
            } catch (ReflectiveOperationException error /* 备份目标设备解析异常 */) {
                throw new IllegalStateException("ColorOS 备份请求目标设备解析失败", error);
            }
        }
        // 指向设备标识在当前方法参数列表中的固定位置
        int deviceIndex = switch (methodName) {
            case "l", "t" -> 2;
            case "a", "d", "e", "g", "h", "i", "j", "m", "n", "o", "p", "q",
                    "s", "u", "v", "w", "x" -> 0;
            default -> throw new IllegalStateException(
                    "Unsupported ColorOS dpk routing method: " + methodName
            );
        };
        return args.length > deviceIndex
                && GalleryContract.DEVICE_ID.equals(args[deviceIndex]);
    }

    // 将不属于群晖设备的调用原样交还飞牛 Provider，并透传其真实异常
    private Object invokeOriginal(
            Method interfaceMethod, // dpk 接口上由 ColorOS 调用的方法
            Object[] args // 不经改写转发给原 Provider 的参数
    ) throws Throwable {
        try {
            return interfaceMethod.invoke(original, args);
        } catch (InvocationTargetException error /* 原 Provider 包装的真实调用异常 */) {
            throw error.getCause();
        }
    }

    // 读取单个群晖相册并映射为 ColorOS 相册 DTO
    private Object getAlbum(String albumId /* 远端相册稳定标识 */)
            throws IOException, ReflectiveOperationException {
        Log.i(TAG, "DSM get album");
        return dtoMapper.album(client.getAlbum(albumId));
    }

    // 分页读取群晖相册并映射为 ColorOS 相册 DTO
    private List<Object> listAlbums(int offset /* 起始偏移 */, int limit /* 最大数量 */)
            throws IOException, ReflectiveOperationException {
        Log.i(TAG, "DSM list albums");
        return dtoMapper.albums(client.listAlbums(offset, limit));
    }

    // 分页读取指定群晖相册的照片并映射为 ColorOS 照片 DTO
    private List<Object> listPhotos(
            String albumId, // 远端相册的稳定标识
            int offset, // ColorOS 请求的照片起始偏移
            int limit // ColorOS 请求的照片最大数量
    ) throws IOException, ReflectiveOperationException {
        Log.i(TAG, "DSM list photos");
        return dtoMapper.photos(client.listPhotos(albumId, offset, limit));
    }

    // 按 ColorOS 尺寸枚举读取群晖缩略图字节
    private byte[] thumbnail(
            String photoId, // 远端照片的稳定标识
            Object colorOsSize // ColorOS 私有缩略图尺寸枚举
    ) throws IOException {
        Log.i(TAG, "DSM load thumbnail");
        return client.getThumbnail(
                photoId,
                ColorOsNasDownloadAdapter.thumbnailSize(colorOsSize)
        );
    }

    // 将群晖原图同步流入 ColorOS 回调并返回已完成的合成下载句柄
    private Object download(
            String photoId, // 远端照片的稳定标识
            Object callback // ColorOS 接收原图分块与完成标记的回调
    ) throws IOException, ReflectiveOperationException {
        Log.i(TAG, "DSM stream original");
        // 记录已通过回调写入 ColorOS 的原图总字节数
        long bytes = client.streamOriginal(photoId, callback);
        return downloadAdapter.completedHandle(photoId, bytes);
    }

    // 删除群晖远端照片，失败时按当前 dpk 布尔合约返回 false
    private boolean deletePhotos(List<String> photoIds /* 待删除的远端照片标识 */) {
        Log.i(TAG, "DSM delete photos");
        try {
            // 表示 DSM 是否已确认全部目标照片删除成功
            boolean deleted = client.deletePhotos(photoIds);
            if (deleted) {
                Log.i(TAG, "DSM delete photos completed");
            }
            return deleted;
        } catch (IOException error /* DSM 删除或清单失效异常 */) {
            Log.e(
                    TAG,
                    "DSM delete photos failed: "
                            + error.getClass().getSimpleName()
                            + ": "
                            + error.getMessage(),
                    error
            );
            return false;
        }
    }
}
