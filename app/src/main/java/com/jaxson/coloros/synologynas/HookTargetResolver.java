package com.jaxson.coloros.synologynas;

import android.content.Context;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;

/**
 * 按 ColorOS 相册 16.50.8 的精确类名、成员名和签名解析整组私有合约
 */
final class HookTargetResolver {
    // 标识读取相册能力配置的混淆包装类
    private static final String CONFIG_WRAPPER = "com.oplus.aiunit.vision.hda";
    // 标识启动 NAS 页面与设备管理入口的混淆类
    private static final String NAS_ENTRY_STARTER = "com.oplus.aiunit.vision.goq";
    // 标识入口方法使用的诊断页面枚举类型
    private static final String NAS_DIAGNOSE_PAGE =
            "com.oplus.gallery.basebiz.track.NasConnectionDiagnoseTrackHelper$DiagnosePage";
    // 标识生成 NAS 文件夹说明的混淆配置类
    private static final String FOLDER_NOTE_CONFIG = "com.oplus.aiunit.vision.bug";
    // 标识生成 NAS 状态说明的混淆类
    private static final String NAS_SYNC_STATE_INFO = "com.oplus.aiunit.vision.zcq";
    // 标识保存各 NAS Provider 实例的相册代理类
    private static final String CLOUD_SYNC_PROXY =
            "com.oplus.gallery.framework.abilities.cloudsync.CloudSyncProxyDM";
    // 标识提供 NAS 设备列表的混淆管理类
    private static final String NAS_DEVICE_MANAGER = "com.oplus.aiunit.vision.ahq";
    // 标识持久化 NAS 相册统计的混淆 DAO 类
    private static final String NAS_DEVICE_DAO = "com.oplus.aiunit.vision.mgq";
    // 标识相册启动阶段 NAS 元数据预加载辅助类
    private static final String NAS_PRELOAD_HELPER = "com.oplus.aiunit.vision.ynq";
    // 标识相册 NAS 业务实现类
    private static final String NAS_IMPL = "com.oplus.aiunit.vision.alq";
    // 标识页面恢复时判断设备是否移除的混淆类
    private static final String NAS_DEVICE_OFFLINE_BINDING = "com.oplus.aiunit.vision.ehq";
    // 标识目标方法签名中的 Kotlin 协程作用域类型
    private static final String COROUTINE_SCOPE = "kotlinx.coroutines.CoroutineScope";
    // 标识目标方法签名和回调调用使用的 Kotlin Function1 类型
    private static final String FUNCTION_ONE = "kotlin.jvm.functions.Function1";
    // 标识相册内部区分 NAS 厂商的 Provider 枚举类型
    private static final String NAS_PROVIDER =
            "com.oplus.gallery.business_lib.nas.NasProvider";
    // 标识相册原图下载操作返回的句柄类
    private static final String NAS_DOWNLOAD_HANDLE = "com.oplus.aiunit.vision.z8g";
    // 标识填充首页图集分组的混淆模型类
    private static final String MAIN_TAB_ALBUM_SET_MODEL = "com.oplus.aiunit.vision.enn";
    // 标识绑定私有云图集卡片的混淆类
    private static final String NAS_ALBUMS_VIEW_BINDING = "com.oplus.aiunit.vision.h9q";
    // 标识卡片绑定方法参数中的 NAS 视图数据类型
    private static final String VIEW_DATA_BASE = "com.oplus.aiunit.vision.wfb0";
    // 标识卡片绑定方法参数中的 ColorOS 基础 ViewHolder 类型
    private static final String BASE_VIEW_HOLDER =
            "com.oplus.gallery.standard_lib.baselist.view.BaseViewHolder";
    // 标识多选删除确认框对应的协程实现类
    private static final String MULTI_DELETE_DIALOG = "com.oplus.aiunit.vision.hfq$b";
    // 标识单图删除确认框对应的协程实现类
    private static final String SINGLE_DELETE_DIALOG = "com.oplus.aiunit.vision.ijw$b";
    // 标识多选删除请求参数的混淆数据类
    private static final String NAS_DELETE_PARAMS = "com.oplus.aiunit.vision.gfq$a";
    // 标识相册将字符串转换为媒体路径的混淆类型
    private static final String MEDIA_PATH = "com.oplus.aiunit.vision.dst";
    // 标识相册根据路径解析媒体对象的混淆类
    private static final String MEDIA_OBJECT_RESOLVER = "com.oplus.aiunit.vision.q7b";
    // 标识保存 NAS 媒体设备身份的混淆媒体类
    private static final String NAS_MEDIA_ITEM = "com.oplus.aiunit.vision.jlq";
    // 标识 ColorOS 删除确认框使用的 Builder 类型
    private static final String DIALOG_BUILDER =
            "com.coui.appcompat.dialog.COUIAlertDialogBuilder";

    /**
     * 阻止创建无状态解析器实例，所有解析均通过静态入口执行
     */
    private HookTargetResolver() {
    }

    /**
     * 一次性解析当前相册版本的全部 Hook 目标和辅助反射成员
     *
     * @param classLoader 能访问 ColorOS 相册私有类的真实类加载器
     * @return 完整且已设为可访问的私有目标集合
     * @throws ReflectiveOperationException 任一固定类或成员不匹配时抛出
     */
    static HookTargets resolve(ClassLoader classLoader) throws ReflectiveOperationException {
        // 对应打开 NAS 页面方法的诊断页参数类型
        Class<?> diagnosePage = Class.forName(NAS_DIAGNOSE_PAGE, false, classLoader);
        // 对应状态观察方法和 Provider 注册表使用的枚举类型
        Class<?> nasProvider = Class.forName(NAS_PROVIDER, false, classLoader);
        // 对应需要在构造完成后替换 FEINIU Provider 的代理类型
        Class<?> cloudSyncProxy = Class.forName(CLOUD_SYNC_PROXY, false, classLoader);
        // 对应设备移除检查方法的协程作用域参数类型
        Class<?> coroutineScope = Class.forName(COROUTINE_SCOPE, false, classLoader);
        // 对应设备移除检查结果回调的 Kotlin 函数类型
        Class<?> functionOne = Class.forName(FUNCTION_ONE, false, classLoader);
        // 对应卡片连接状态方法的可用性枚举参数类型
        Class<?> nasDeviceAvailability = Class.forName(
                "com.oplus.gallery.business_lib.nas.NasDeviceAvailability",
                false,
                classLoader
        );
        // 对应 NAS 卡片绑定方法的基础 ViewHolder 参数类型
        Class<?> baseViewHolder = Class.forName(BASE_VIEW_HOLDER, false, classLoader);
        // 对应 NAS 卡片绑定方法的视图数据基类参数类型
        Class<?> viewDataBase = Class.forName(VIEW_DATA_BASE, false, classLoader);
        // 对应启动元数据预加载方法的递归辅助参数类型
        Class<?> nasPreloadHelper = Class.forName(NAS_PRELOAD_HELPER, false, classLoader);
        // 对应多选删除协程中保存的请求参数类型
        Class<?> deleteParams = Class.forName(NAS_DELETE_PARAMS, false, classLoader);
        // 对应媒体对象解析方法的路径参数类型
        Class<?> mediaPath = Class.forName(MEDIA_PATH, false, classLoader);
        // 对应删除正文设置方法的 Builder 声明和返回类型
        Class<?> dialogBuilder = Class.forName(DIALOG_BUILDER, false, classLoader);
        // 对应 Provider 注册表所在代理类的无参构造目标
        Constructor<?> cloudSyncProxyConstructor = cloudSyncProxy.getDeclaredConstructor();
        cloudSyncProxyConstructor.setAccessible(true);
        return new HookTargets(
                findDeclaredMethod(
                        classLoader,
                        CONFIG_WRAPPER,
                        "c",
                        String.class,
                        boolean.class,
                        boolean.class
                ),
                findDeclaredMethod(
                        classLoader,
                        CONFIG_WRAPPER,
                        "d",
                        int.class,
                        String.class,
                        boolean.class
                ),
                findDeclaredMethod(
                        classLoader,
                        NAS_ENTRY_STARTER,
                        "a",
                        Context.class,
                        String.class,
                        diagnosePage,
                        int.class
                ),
                findDeclaredMethod(classLoader, FOLDER_NOTE_CONFIG, "w", long.class),
                findDeclaredMethod(classLoader, NAS_SYNC_STATE_INFO, "m", Context.class),
                cloudSyncProxyConstructor,
                findDeclaredMethod(classLoader, NAS_DEVICE_MANAGER, "b"),
                findDeclaredMethod(classLoader, NAS_DEVICE_DAO, "f", String.class),
                findDeclaredMethod(
                        classLoader,
                        NAS_PRELOAD_HELPER,
                        "a",
                        int.class,
                        nasPreloadHelper,
                        ArrayList.class,
                        CountDownLatch.class
                ),
                findDeclaredMethod(classLoader, NAS_IMPL, "h", nasProvider, String.class),
                findDeclaredMethod(classLoader, NAS_DOWNLOAD_HANDLE, "cancel"),
                findDeclaredMethod(classLoader, MAIN_TAB_ALBUM_SET_MODEL, "i"),
                findDeclaredMethod(
                        classLoader,
                        NAS_ALBUMS_VIEW_BINDING,
                        "e",
                        baseViewHolder,
                        int.class,
                        viewDataBase
                ),
                findDeclaredMethod(
                        classLoader,
                        NAS_ALBUMS_VIEW_BINDING,
                        "l",
                        Integer.class,
                        nasDeviceAvailability
                ),
                findDeclaredField(classLoader, NAS_ALBUMS_VIEW_BINDING, "T"),
                findDeclaredMethod(
                        classLoader,
                        NAS_DEVICE_OFFLINE_BINDING,
                        "b",
                        coroutineScope,
                        boolean.class,
                        String.class,
                        functionOne
                ),
                functionOne.getMethod("invoke", Object.class),
                findDeclaredMethod(classLoader, MULTI_DELETE_DIALOG, "invokeSuspend", Object.class),
                findDeclaredField(classLoader, MULTI_DELETE_DIALOG, "$params"),
                findDeclaredField(deleteParams, "a"),
                findDeclaredMethod(classLoader, SINGLE_DELETE_DIALOG, "invokeSuspend", Object.class),
                findDeclaredField(classLoader, SINGLE_DELETE_DIALOG, "$itemPath"),
                findDeclaredMethod(classLoader, MEDIA_PATH, "b", String.class),
                findDeclaredMethod(classLoader, MEDIA_OBJECT_RESOLVER, "f", mediaPath),
                findDeclaredField(classLoader, NAS_MEDIA_ITEM, "F0"),
                findDeclaredMethod(classLoader, NAS_MEDIA_ITEM, "i0"),
                findDeclaredMethodReturning(
                        dialogBuilder,
                        "setMessage",
                        dialogBuilder,
                        CharSequence.class
                )
        );
    }

    /**
     * 按声明类、名称和参数类型精确解析私有方法
     *
     * @param classLoader 相册私有类加载器
     * @param className 声明目标方法的完整类名
     * @param methodName DEX 中的原始方法名
     * @param parameterTypes 目标方法的精确参数类型序列
     * @return 已设为可访问的目标方法
     * @throws ReflectiveOperationException 类或方法签名不匹配时抛出
     */
    private static Method findDeclaredMethod(
            ClassLoader classLoader,
            String className,
            String methodName,
            Class<?>... parameterTypes
    ) throws ReflectiveOperationException {
        // 按固定类名加载目标声明类，不触发类初始化
        Class<?> targetClass = Class.forName(className, false, classLoader);
        // 按固定名称和完整参数序列获取唯一目标方法
        Method method = targetClass.getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method;
    }

    /**
     * 按完整类名和 DEX 原始字段名精确解析私有字段
     *
     * @param classLoader 相册私有类加载器
     * @param className 声明目标字段的完整类名
     * @param fieldName DEX 中的原始字段名
     * @return 已设为可访问的目标字段
     * @throws ReflectiveOperationException 类或字段不匹配时抛出
     */
    private static Field findDeclaredField(
            ClassLoader classLoader,
            String className,
            String fieldName
    ) throws ReflectiveOperationException {
        return findDeclaredField(Class.forName(className, false, classLoader), fieldName);
    }

    /**
     * 按声明类和 DEX 原始字段名精确解析私有字段
     *
     * @param targetClass 声明目标字段的相册私有类
     * @param fieldName DEX 中的原始字段名
     * @return 已设为可访问的目标字段
     * @throws ReflectiveOperationException 字段不匹配时抛出
     */
    private static Field findDeclaredField(Class<?> targetClass, String fieldName)
            throws ReflectiveOperationException {
        // 按目标类本身声明的固定名称读取字段，禁止遍历猜测
        Field field = targetClass.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field;
    }

    /**
     * 同时按名称、返回类型和参数类型精确定位存在重载的私有方法
     *
     * @param targetClass 声明目标方法的相册私有类
     * @param methodName DEX 中的原始方法名
     * @param returnType 目标方法的精确返回类型
     * @param parameterTypes 目标方法的精确参数类型序列
     * @return 已设为可访问的唯一目标方法
     * @throws NoSuchMethodException 无声明方法满足完整签名时抛出
     */
    private static Method findDeclaredMethodReturning(
            Class<?> targetClass,
            String methodName,
            Class<?> returnType,
            Class<?>... parameterTypes
    ) throws NoSuchMethodException {
        for (Method /* 当前检查的目标类声明方法 */ method
                : targetClass.getDeclaredMethods()) {
            if (!methodName.equals(method.getName())
                    || !returnType.equals(method.getReturnType())) {
                continue;
            }
            // 保存当前候选方法的真实参数类型序列
            Class<?>[] actualParameterTypes = method.getParameterTypes();
            if (actualParameterTypes.length != parameterTypes.length) {
                continue;
            }
            // 标记当前候选方法是否完整匹配请求的参数签名
            boolean parametersMatch = true;
            for (int /* 当前比较的参数位置 */ index = 0;
                    index < parameterTypes.length;
                    index++) {
                if (!parameterTypes[index].equals(actualParameterTypes[index])) {
                    parametersMatch = false;
                    break;
                }
            }
            if (parametersMatch) {
                method.setAccessible(true);
                return method;
            }
        }
        throw new NoSuchMethodException(
                targetClass.getName() + "." + methodName + " with requested return type"
        );
    }
}
