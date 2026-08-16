package com.jaxson.coloros.synologynas.gallery;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicReference;

// 适配 ColorOS 缩略图枚举、完成进度 Channel 和下载句柄
final class ColorOsNasDownloadAdapter {
    // 定位 ColorOS 原图下载进度 DTO
    private static final String DOWNLOAD_PROGRESS = "com.oplus.aiunit.vision.wac";
    // 定位 ColorOS 原图下载句柄 DTO
    private static final String DOWNLOAD_HANDLE = "com.oplus.aiunit.vision.z8g";
    // 当前 Kotlin SendChannel 非阻塞写入方法的精确 JVM 名称
    private static final String TRY_SEND_METHOD = "trySend-JP2dKIU";
    // 以弱引用记录已同步完成的合成句柄，避免 ColorOS 后续 cancel 崩溃
    private static final Set<Object> SYNTHETIC_DOWNLOADS = Collections.synchronizedSet(
            Collections.newSetFromMap(new WeakHashMap<>())
    );

    // 解析 ColorOS 下载 DTO 和 Kotlin Channel 的相册类加载器
    private final ClassLoader galleryClassLoader;

    // 固定当前相册进程的私有下载合约解析边界
    ColorOsNasDownloadAdapter(ClassLoader galleryClassLoader /* 相册类加载器 */) {
        this.galleryClassLoader = galleryClassLoader;
    }

    // 消费一次合成下载句柄标记，使对应 cancel 调用由 Hook 直接结束
    static boolean shouldSuppressCancel(Object value /* 待取消的原图下载句柄 */) {
        return SYNTHETIC_DOWNLOADS.remove(value);
    }

    // 将 ColorOS 缩略图枚举映射为群晖客户端当前支持的尺寸标识
    static String thumbnailSize(Object colorOsSize /* ColorOS 私有尺寸枚举 */) {
        if (!(colorOsSize instanceof Enum<?> size /* 已确认的缩略图枚举 */)) {
            throw new IllegalArgumentException("ColorOS thumbnail size is not an enum");
        }
        return switch (size.name()) {
            case "THUMBNAIL_SIZE_L" -> GalleryContract.THUMBNAIL_LARGE;
            case "THUMBNAIL_SIZE_S" -> GalleryContract.THUMBNAIL_SMALL;
            default -> throw new IllegalArgumentException(
                    "Unknown ColorOS thumbnail size: " + size.name()
            );
        };
    }

    // 为已同步写完的原图构造 ColorOS 完成状态下载句柄
    Object completedHandle(
            String photoId, // 已写入 ColorOS 回调的远端照片稳定标识
            long bytes // 已写入 ColorOS 回调的原图总字节数
    ) throws ReflectiveOperationException {
        // 表达下载已完成且进度为百分之百的 ColorOS DTO
        Object progress = newDownloadProgress(photoId, bytes);
        // 只包含完成进度并已关闭的 Kotlin Channel
        Object channel = newCompletedChannel(progress);
        // ColorOS 原图下载句柄的运行时类型
        Class<?> type = Class.forName(DOWNLOAD_HANDLE, false, galleryClassLoader);
        // 当前版本下载句柄构造器中的 Kotlin Channel 接口
        Class<?> channelType = Class.forName(
                "kotlinx.coroutines.channels.Channel",
                false,
                galleryClassLoader
        );
        // 当前版本下载句柄构造器中的 Kotlin CoroutineScope 接口
        Class<?> coroutineScopeType = Class.forName(
                "kotlinx.coroutines.CoroutineScope",
                false,
                galleryClassLoader
        );
        // 当前版本下载句柄构造器中的 Kotlin Job 接口
        Class<?> jobType = Class.forName(
                "kotlinx.coroutines.Job",
                false,
                galleryClassLoader
        );
        // 当前版本用于承载下载 Channel 的精确六参数构造器
        Constructor<?> constructor = type.getDeclaredConstructor(
                channelType,
                AtomicReference.class,
                coroutineScopeType,
                jobType,
                String.class,
                String.class
        );
        constructor.setAccessible(true);
        // 返回给 ColorOS 并等待后续 cancel 调用的合成句柄
        Object handle = constructor.newInstance(
                channel,
                new AtomicReference<>(),
                null,
                null,
                GalleryContract.DEVICE_ID,
                photoId
        );
        SYNTHETIC_DOWNLOADS.add(handle);
        return handle;
    }

    // 按当前构造器合约创建百分之百完成的下载进度 DTO
    private Object newDownloadProgress(
            String photoId, // 已写入完成的远端照片稳定标识
            long bytes // 同时作为当前字节数和总字节数的实际写入量
    ) throws ReflectiveOperationException {
        // ColorOS 下载进度 DTO 的运行时类型
        Class<?> type = Class.forName(DOWNLOAD_PROGRESS, false, galleryClassLoader);
        // 当前版本下载进度 DTO 的完整构造器
        Constructor<?> constructor = type.getDeclaredConstructor(
                String.class,
                long.class,
                long.class,
                int.class,
                boolean.class
        );
        constructor.setAccessible(true);
        return constructor.newInstance(photoId, bytes, bytes, 100, true);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    // 创建写入一条完成进度后立即关闭的 Kotlin Channel
    private Object newCompletedChannel(Object progress /* 完成进度 DTO */)
            throws ReflectiveOperationException {
        // Kotlin Channel 工厂方法所在的运行时类型
        Class<?> channelKt = Class.forName(
                "kotlinx.coroutines.channels.ChannelKt",
                false,
                galleryClassLoader
        );
        // Kotlin Channel 缓冲溢出策略的运行时枚举类型
        Class<?> overflowType = Class.forName(
                "kotlinx.coroutines.channels.BufferOverflow",
                false,
                galleryClassLoader
        );
        // Kotlin Channel 工厂第三个参数使用的 Function1 运行时类型
        Class<?> function1 = Class.forName(
                "kotlin.jvm.functions.Function1",
                false,
                galleryClassLoader
        );
        // 当前 Kotlin Channel 接口继承的精确发送合同类型
        Class<?> sendChannelType = Class.forName(
                "kotlinx.coroutines.channels.SendChannel",
                false,
                galleryClassLoader
        );
        // 保持当前句柄使用的 SUSPEND 溢出策略
        Object suspend = Enum.valueOf((Class<? extends Enum>) overflowType, "SUSPEND");
        // 当前 Kotlin 运行时暴露的 Channel 工厂方法
        Method channelFactory = channelKt.getMethod(
                "Channel",
                int.class,
                overflowType,
                function1
        );
        // 承载唯一完成进度的无界 Channel 实例
        Object channel = channelFactory.invoke(null, Integer.MAX_VALUE, suspend, null);

        // 当前 SendChannel 接口用于非阻塞写入完成进度的精确方法
        Method trySend = sendChannelType.getMethod(TRY_SEND_METHOD, Object.class);
        if (trySend.getReturnType() != Object.class
                || Modifier.isStatic(trySend.getModifiers())) {
            throw new NoSuchMethodException(
                    "SendChannel.trySend-JP2dKIU(Object): Object instance method not found"
            );
        }
        trySend.invoke(channel, progress);
        // 当前 Channel 接口用于正常关闭且不携带异常的方法
        Method close = channel.getClass().getMethod("close", Throwable.class);
        close.invoke(channel, new Object[]{null});
        return channel;
    }
}
