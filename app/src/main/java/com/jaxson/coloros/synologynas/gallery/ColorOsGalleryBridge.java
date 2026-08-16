package com.jaxson.coloros.synologynas.gallery;

import android.content.Context;
import android.content.pm.ApplicationInfo;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Map;
import java.util.function.IntSupplier;

// 连接 ColorOS 私有 NAS DTO、状态、首页入口和 Provider 注册表
public final class ColorOsGalleryBridge {
    // 定位 ColorOS NAS 实现和管理核心对象
    private static final String NAS_IMPL = "com.oplus.aiunit.vision.alq";
    // 定位 ColorOS NAS Provider 注册表
    private static final String NAS_REGISTRY = "com.oplus.aiunit.vision.xhb";
    // 定位 ColorOS NAS 设备 DTO
    private static final String NAS_DEVICE = "com.oplus.aiunit.vision.ngq";
    // 定位 ColorOS NAS 图库统计 DTO
    private static final String GALLERY_STATS_DTO = "com.oplus.aiunit.vision.jjq";
    // 定位 ColorOS 首页 NAS 分组 DTO
    private static final String NAS_HOME_GROUP = "com.oplus.aiunit.vision.e5q";
    // 定位 ColorOS 首页 NAS 设备信息 DTO
    private static final String NAS_DEVICE_INFO = "com.oplus.aiunit.vision.ogq";
    // 定位 ColorOS NAS Provider 枚举
    private static final String NAS_PROVIDER =
            "com.oplus.gallery.business_lib.nas.NasProvider";
    // 定位 ColorOS NAS 设备状态 DTO
    private static final String DEVICE_STATUS = "com.oplus.aiunit.vision.srb";
    // 定位 ColorOS NAS 设备连接状态枚举
    private static final String DEVICE_AVAILABILITY =
            "com.oplus.gallery.business_lib.nas.NasDeviceAvailability";

    // 禁止实例化只负责 ColorOS 私有 NAS 合约适配的工具类
    private ColorOsGalleryBridge() {
    }

    // 将 FEINIU 注册表槽位替换为保留原 Provider 转发能力的群晖代理
    public static boolean replaceProvider(
            Object cloudSyncProxy, // ColorOS CloudSyncProxyDM 实例
            ClassLoader classLoader, // 解析相册私有类型的类加载器
            GalleryRemoteClient client, // 执行群晖浏览、预览和删除的客户端
            GalleryBackupService backupService, // 执行群晖备份的服务
            IntSupplier photoCount // 读取相册侧缓存照片数的供应器
    ) throws ReflectiveOperationException {
        // CloudSyncProxyDM 中固定类型为 alq 的 NAS 管理对象
        Object nas = ColorOsGalleryReflection.readTypedField(
                cloudSyncProxy,
                "d",
                NAS_IMPL
        );
        // alq 中固定类型为 xhb 的 Provider 注册表
        Object registry = ColorOsGalleryReflection.readTypedField(
                nas,
                "a",
                NAS_REGISTRY
        );
        // xhb 中以 NasProvider 为键保存 dpk 实例的唯一映射
        Map<Object, Object> providers = ColorOsGalleryReflection.registryMap(registry);
        // ColorOS 原生 NAS 页面当前使用的 FEINIU 枚举槽位
        Object feiniu = ColorOsNasReflection.enumValue(
                classLoader,
                NAS_PROVIDER,
                "FEINIU"
        );
        // 需要保留给非群晖请求继续调用的原飞牛 Provider
        Object original = providers.get(feiniu);
        if (ColorOsNasProviderProxy.isSynologyProvider(original)) {
            return false;
        }
        if (original == null) {
            throw new IllegalStateException("ColorOS NAS registry is missing FEINIU provider");
        }
        providers.put(
                feiniu,
                ColorOsNasProviderProxy.create(
                        client,
                        backupService,
                        original,
                        classLoader,
                        photoCount
                )
        );
        return true;
    }

    // 保留其他 NAS 设备并以当前型号和状态重建唯一群晖设备 DTO
    public static ArrayList<Object> withSynologyDevice(
            ArrayList<?> original, // ColorOS 原方法返回的全部 NAS 设备
            ClassLoader classLoader, // 解析相册私有类型的类加载器
            String deviceModel, // 共享 Hook 状态中的真实 NAS 型号
            boolean connected // 共享 Hook 状态中的当前连接状态
    ) throws ReflectiveOperationException {
        // 比原列表多预留一个位置的合成设备结果
        ArrayList<Object> result = new ArrayList<>(original.size() + 1);
        for (Object device : original) { // ColorOS 原列表中的当前 NAS 设备
            if (!isSyntheticDevice(device)) {
                result.add(device);
            }
        }
        result.add(newDevice(classLoader, deviceModel, connected));
        return result;
    }

    // 创建可由后台连接探测更新的群晖设备 MutableStateFlow
    public static Object mutableStatusFlow(
            ClassLoader classLoader, // 解析相册私有 DTO 与 Kotlin Flow 的类加载器
            boolean connected // StateFlow 初始连接状态
    ) throws ReflectiveOperationException {
        // 当前连接状态对应的 ColorOS srb DTO
        Object status = newStatusInfo(classLoader, connected);
        // Kotlin MutableStateFlow 工厂所在的运行时类型
        Class<?> stateFlowKt = Class.forName(
                "kotlinx.coroutines.flow.StateFlowKt",
                false,
                classLoader
        );
        // 当前 Kotlin 运行时暴露的 MutableStateFlow 工厂方法
        Method mutableStateFlow = stateFlowKt.getMethod("MutableStateFlow", Object.class);
        return mutableStateFlow.invoke(null, status);
    }

    // 将群晖连接探测结果写入现有 ColorOS MutableStateFlow
    public static void updateStatusFlow(
            Object statusFlow, // 已返回给 ColorOS 的群晖 MutableStateFlow
            ClassLoader classLoader, // 解析相册私有 DTO 与 Kotlin Flow 的类加载器
            boolean connected // 本次 DSM 探测确认的连接状态
    ) throws ReflectiveOperationException {
        // Kotlin MutableStateFlow 接口的运行时类型
        Class<?> mutableStateFlow = Class.forName(
                "kotlinx.coroutines.flow.MutableStateFlow",
                false,
                classLoader
        );
        // 当前 Kotlin MutableStateFlow 更新值的固定接口方法
        Method setValue = mutableStateFlow.getMethod("setValue", Object.class);
        setValue.invoke(statusFlow, newStatusInfo(classLoader, connected));
    }

    // 按当前构造器合约创建绑定唯一群晖设备 ID 的状态 DTO
    private static Object newStatusInfo(
            ClassLoader classLoader, // 解析相册状态 DTO 与枚举的类加载器
            boolean connected // 需要映射为 CONNECTED 或 OFFLINE 的状态
    ) throws ReflectiveOperationException {
        // 与当前连接布尔值严格对应的 NasDeviceAvailability 枚举
        Object availability = ColorOsNasReflection.enumValue(
                classLoader,
                DEVICE_AVAILABILITY,
                connected ? "CONNECTED" : "OFFLINE"
        );
        // ColorOS srb 设备状态 DTO 的运行时类型
        Class<?> statusType = Class.forName(DEVICE_STATUS, false, classLoader);
        // 当前版本接收设备标识和 availability 的固定构造器
        Constructor<?> statusConstructor = statusType.getDeclaredConstructor(
                String.class,
                availability.getClass()
        );
        statusConstructor.setAccessible(true);
        return statusConstructor.newInstance(GalleryContract.DEVICE_ID, availability);
    }

    // 用相册进程已有缓存构造 ColorOS 图库统计 DTO，不扫描 DSM 清单
    public static Object galleryStats(
            ClassLoader classLoader, // 解析 jjq 私有 DTO 的相册类加载器
            int photoCount // 相册私有 DAO 已保存的群晖照片数量
    ) throws ReflectiveOperationException {
        // ColorOS jjq 图库统计 DTO 的运行时类型
        Class<?> type = Class.forName(GALLERY_STATS_DTO, false, classLoader);
        // 当前版本固定接收照片数和视频数的构造器
        Constructor<?> constructor = type.getDeclaredConstructor(int.class, int.class);
        constructor.setAccessible(true);
        return constructor.newInstance(photoCount, 0);
    }

    // 从当前版本精确 jjq.a 字段读取照片数量
    public static Integer photoCount(Object galleryStats /* ColorOS jjq 统计 DTO */)
            throws ReflectiveOperationException {
        if (galleryStats == null) {
            return null;
        }
        // jjq.a 对应当前运行时 DEX 的照片数量字段
        Field field = galleryStats.getClass().getDeclaredField("a");
        if (field.getType() != int.class || Modifier.isStatic(field.getModifiers())) {
            throw new NoSuchFieldException("ColorOS NAS gallery photo count");
        }
        field.setAccessible(true);
        return field.getInt(galleryStats);
    }

    // 已有本地统计时跳过连续群晖合成设备的原飞牛启动预加载
    public static int nextPreloadIndex(
            ArrayList<?> devices, // ColorOS 当前准备预加载的 NAS 设备列表
            int currentIndex, // 原方法本次准备处理的设备位置
            boolean hasStoredSynologyMetadata // 相册私有 DAO 是否已有群晖统计
    ) throws ReflectiveOperationException {
        if (!hasStoredSynologyMetadata) {
            return currentIndex;
        }
        // 跳过群晖合成项后应交还原方法继续处理的位置
        int nextIndex = currentIndex;
        // 逐项越过当前位置开始的连续群晖合成设备
        while (nextIndex < devices.size() && isSyntheticDevice(devices.get(nextIndex))) {
            nextIndex++;
        }
        return nextIndex;
    }

    // 在首页 NAS 分组中插入、修复或更新唯一群晖入口
    public static boolean ensureSynologyHomeEntry(
            Object mainTabAlbumSetModel, // ColorOS 首页相册分组模型
            ClassLoader classLoader, // 解析相册私有 DTO 的类加载器
            String deviceModel, // 共享 Hook 状态中的真实 NAS 型号
            boolean connected // 共享 Hook 状态中的当前连接状态
    ) throws ReflectiveOperationException {
        // 首页 Lazy 字段实际承载的可变分组列表
        ArrayList<Object> items = mainTabItems(mainTabAlbumSetModel);
        // 按当前型号和状态构造的完整群晖首页分组
        Object entry = newHomeEntry(classLoader, deviceModel, connected);

        for (int index = 0; index < items.size(); index++) { // 当前首页分组位置
            // 当前首页分组对象
            Object item = items.get(index);
            if (!isSynologyHomeEntry(item)) {
                continue;
            }
            if (isCompleteSynologyHomeEntry(item, deviceModel, connected)) {
                return false;
            }
            items.set(index, entry);
            return true;
        }

        // 默认追加；存在类型 4 的“更多图集”时改为插入其前方
        int insertionIndex = items.size();
        for (int index = 0; index < items.size(); index++) { // 当前首页分组位置
            if (ColorOsGalleryReflection.readIntField(items.get(index), "a") == 4) {
                insertionIndex = index;
                break;
            }
        }
        items.add(insertionIndex, entry);
        return true;
    }

    // 判断给定 dpk 实例是否为已配置的本模块群晖代理
    public static boolean isConfiguredProvider(Object provider /* 待识别的 dpk 实例 */) {
        if (!ColorOsNasProviderProxy.isSynologyProvider(provider)) {
            return false;
        }
        return ((ColorOsNasProviderProxy) java.lang.reflect.Proxy.getInvocationHandler(provider))
                .isConfigured();
    }

    // 从 alq 管理对象的 FEINIU 槽位判断群晖代理配置状态
    public static boolean isConfiguredManager(Object manager /* ColorOS alq 管理对象 */)
            throws ReflectiveOperationException {
        // alq 中固定类型为 xhb 的 Provider 注册表
        Object registry = ColorOsGalleryReflection.readTypedField(
                manager,
                "a",
                NAS_REGISTRY
        );
        // xhb 中以 NasProvider 为键保存 dpk 实例的唯一映射
        Map<Object, Object> providers = ColorOsGalleryReflection.registryMap(registry);
        // ColorOS 原生 NAS 页面当前使用的 FEINIU 枚举槽位
        Object feiniu = ColorOsNasReflection.enumValue(
                manager.getClass().getClassLoader(),
                NAS_PROVIDER,
                "FEINIU"
        );
        return isConfiguredProvider(providers.get(feiniu));
    }

    // 为群晖卡片替换品牌 Logo，并同步型号和连接状态标签
    public static void applySynologyCardBranding(
            Context galleryContext, // ColorOS 相册进程的主题 Context
            ApplicationInfo moduleApplicationInfo, // 模块资源所在应用信息
            Object binding, // 当前版本 NasAlbumsViewDataBinding 实例
            Object viewData, // 当前卡片绑定的 mjq 数据
            String deviceModel, // 共享 Hook 状态中的真实 NAS 型号
            boolean connected // 共享 Hook 状态中的当前连接状态
    ) throws ReflectiveOperationException {
        ColorOsGalleryCardBridge.applyBranding(
                galleryContext,
                moduleApplicationInfo,
                binding,
                viewData,
                deviceModel,
                connected
        );
    }

    // 仅对当前群晖卡片写入共享型号和连接状态标签
    public static void applySynologyConnectionLabel(
            Object binding, // 当前版本 NasAlbumsViewDataBinding 实例
            String deviceModel, // 共享 Hook 状态中的真实 NAS 型号
            boolean connected // 共享 Hook 状态中的当前连接状态
    ) throws ReflectiveOperationException {
        ColorOsGalleryCardBridge.applyConnectionLabel(binding, deviceModel, connected);
    }

    // 按当前 ngq 精确构造器合约创建群晖设备 DTO
    private static Object newDevice(
            ClassLoader classLoader, // 解析 ngq 和 NasProvider 的相册类加载器
            String deviceModel, // 共享 Hook 状态中的真实 NAS 型号
            boolean connected // 共享 Hook 状态中的当前连接状态
    ) throws ReflectiveOperationException {
        // ColorOS 原生 NAS 页面当前使用的 FEINIU 枚举槽位
        Object provider = ColorOsNasReflection.enumValue(
                classLoader,
                NAS_PROVIDER,
                "FEINIU"
        );
        // ColorOS ngq NAS 设备 DTO 的运行时类型
        Class<?> type = Class.forName(NAS_DEVICE, false, classLoader);
        // 当前版本 ngq 的精确十一参数构造器
        Constructor<?> constructor = type.getDeclaredConstructor(
                provider.getClass(),
                String.class,
                String.class,
                String.class,
                String.class,
                String.class,
                int.class,
                String.class,
                long.class,
                boolean.class,
                int.class
        );
        constructor.setAccessible(true);
        return constructor.newInstance(
                provider,
                GalleryContract.DEVICE_ID,
                GalleryContract.DEVICE_NAME,
                deviceModel,
                "synology://dsm7",
                "",
                connected ? 1 : 0,
                "",
                Long.MAX_VALUE,
                true,
                0
        );
    }

    @SuppressWarnings("unchecked")
    // 读取首页模型 e 字段的 Lazy 值并要求其为可变 ArrayList
    private static ArrayList<Object> mainTabItems(Object model /* 首页相册分组模型 */)
            throws ReflectiveOperationException {
        // enn.e 对应的 Lazy<ArrayList<首页分组>> 字段
        Field itemsLazyField = model.getClass().getDeclaredField("e");
        itemsLazyField.setAccessible(true);
        // 首页分组字段当前持有的 Kotlin Lazy 实例
        Object itemsLazy = itemsLazyField.get(model);
        // Kotlin Lazy 用于取得实际列表的无参数方法
        Method getValue = itemsLazy.getClass().getDeclaredMethod("getValue");
        getValue.setAccessible(true);
        // Kotlin Lazy 当前解析出的首页分组值
        Object value = getValue.invoke(itemsLazy);
        if (!(value instanceof ArrayList<?>)) {
            throw new IllegalStateException("ColorOS main album groups are not an ArrayList");
        }
        return (ArrayList<Object>) value;
    }

    // 按当前 ogq/e5q 构造器合约创建群晖首页分组
    private static Object newHomeEntry(
            ClassLoader classLoader, // 解析 ogq 和 e5q 的相册类加载器
            String deviceModel, // 共享 Hook 状态中的真实 NAS 型号
            boolean connected // 共享 Hook 状态中的当前连接状态
    ) throws ReflectiveOperationException {
        // ColorOS ogq NAS 设备信息 DTO 的运行时类型
        Class<?> deviceInfoType = Class.forName(NAS_DEVICE_INFO, false, classLoader);
        // 当前版本 ogq 的精确八参数构造器
        Constructor<?> deviceInfoConstructor = deviceInfoType.getDeclaredConstructor(
                int.class,
                int.class,
                int.class,
                int.class,
                long.class,
                String.class,
                String.class,
                String.class
        );
        deviceInfoConstructor.setAccessible(true);
        // 绑定唯一设备 ID、品牌、型号和连接状态的 ogq 对象
        Object deviceInfo = deviceInfoConstructor.newInstance(
                0,
                connected ? 1 : 0,
                1,
                1,
                0L,
                GalleryContract.DEVICE_ID,
                GalleryContract.DEVICE_NAME,
                deviceModel
        );

        // ColorOS e5q 首页 NAS 分组 DTO 的运行时类型
        Class<?> homeGroupType = Class.forName(NAS_HOME_GROUP, false, classLoader);
        // 当前版本 e5q 接收类型、数量和设备信息的固定构造器
        Constructor<?> homeGroupConstructor = homeGroupType.getDeclaredConstructor(
                int.class,
                int.class,
                deviceInfoType
        );
        homeGroupConstructor.setAccessible(true);
        return homeGroupConstructor.newInstance(5, 1, deviceInfo);
    }

    // 判断首页分组是否绑定唯一群晖设备 ID
    private static boolean isSynologyHomeEntry(Object item /* 首页分组候选对象 */)
            throws ReflectiveOperationException {
        if (item == null || !NAS_HOME_GROUP.equals(item.getClass().getName())) {
            return false;
        }
        // e5q.d 对应的 NAS 设备信息 DTO
        Object deviceInfo = ColorOsGalleryReflection.readField(item, "d");
        return GalleryContract.DEVICE_ID.equals(
                ColorOsGalleryReflection.readField(deviceInfo, "b")
        );
    }

    // 判断已有群晖首页分组的品牌、状态和型号是否与共享状态完全一致
    private static boolean isCompleteSynologyHomeEntry(
            Object item, // 已确认绑定群晖设备 ID 的 e5q 分组
            String deviceModel, // 共享 Hook 状态中的真实 NAS 型号
            boolean connected // 共享 Hook 状态中的当前连接状态
    ) throws ReflectiveOperationException {
        // e5q.d 对应的 NAS 设备信息 DTO
        Object deviceInfo = ColorOsGalleryReflection.readField(item, "d");
        return GalleryContract.DEVICE_NAME.equals(
                ColorOsGalleryReflection.readField(deviceInfo, "c")
        ) && ColorOsGalleryReflection.readIntField(deviceInfo, "d")
                == (connected ? 1 : 0)
                && deviceModel.equals(ColorOsGalleryReflection.readField(deviceInfo, "f"));
    }

    // 按当前 ngq.b 精确字段合约判断设备是否为群晖合成项
    private static boolean isSyntheticDevice(Object owner /* NAS 设备候选对象 */)
            throws ReflectiveOperationException {
        return owner != null
                && NAS_DEVICE.equals(owner.getClass().getName())
                && GalleryContract.DEVICE_ID.equals(
                        ColorOsGalleryReflection.readField(owner, "b")
                );
    }
}
