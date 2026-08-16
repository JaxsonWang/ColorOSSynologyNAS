package com.jaxson.coloros.synologynas;

import android.content.Context;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
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
                findDeclaredMethodReturning(
                        classLoader,
                        CONFIG_WRAPPER,
                        "c",
                        boolean.class,
                        String.class,
                        boolean.class,
                        boolean.class
                ),
                findDeclaredMethodReturning(
                        classLoader,
                        CONFIG_WRAPPER,
                        "d",
                        boolean.class,
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
                cloudSyncProxyConstructor,
                findDeclaredMethodReturning(
                        classLoader,
                        NAS_DEVICE_MANAGER,
                        "b",
                        ArrayList.class
                ),
                requireStatic(findDeclaredMethod(
                        classLoader,
                        NAS_DEVICE_DAO,
                        "f",
                        String.class
                )),
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
                findDeclaredInstanceField(
                        classLoader,
                        NAS_ALBUMS_VIEW_BINDING,
                        "T",
                        String.class
                ),
                findDeclaredMethod(
                        classLoader,
                        NAS_DEVICE_OFFLINE_BINDING,
                        "b",
                        coroutineScope,
                        boolean.class,
                        String.class,
                        functionOne
                ),
                requireInstance(functionOne.getMethod("invoke", Object.class)),
                findDeclaredMethod(classLoader, MULTI_DELETE_DIALOG, "invokeSuspend", Object.class),
                findDeclaredInstanceField(
                        classLoader,
                        MULTI_DELETE_DIALOG,
                        "$params",
                        deleteParams
                ),
                findDeclaredInstanceField(deleteParams, "a", String.class),
                findDeclaredMethod(classLoader, SINGLE_DELETE_DIALOG, "invokeSuspend", Object.class),
                findDeclaredInstanceField(
                        classLoader,
                        SINGLE_DELETE_DIALOG,
                        "$itemPath",
                        String.class
                ),
                requireStatic(findDeclaredMethod(classLoader, MEDIA_PATH, "b", String.class)),
                requireStatic(findDeclaredMethod(
                        classLoader,
                        MEDIA_OBJECT_RESOLVER,
                        "f",
                        mediaPath
                )),
                findDeclaredInstanceField(
                        classLoader,
                        NAS_MEDIA_ITEM,
                        "F0",
                        String.class
                ),
                requireInstance(findDeclaredMethod(classLoader, NAS_MEDIA_ITEM, "i0")),
                requireInstance(findDeclaredMethodReturning(
                        dialogBuilder,
                        "setMessage",
                        dialogBuilder,
                        CharSequence.class
                ))
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
            ClassLoader classLoader, // 解析目标声明类的相册类加载器
            String className, // 声明目标方法的完整类名
            String methodName, // DEX 中确认的精确方法名
            Class<?>... parameterTypes // 当前方法的完整参数类型序列
    ) throws ReflectiveOperationException {
        // 按固定类名加载目标声明类，不触发类初始化
        Class<?> targetClass = Class.forName(className, false, classLoader);
        // 按固定名称和完整参数序列获取唯一目标方法
        Method method = targetClass.getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method;
    }

    /**
     * 按声明类、名称、返回类型和参数类型精确解析私有方法
     *
     * @param classLoader 相册私有类加载器
     * @param className 声明目标方法的完整类名
     * @param methodName DEX 中的原始方法名
     * @param returnType 目标方法的精确返回类型
     * @param parameterTypes 目标方法的精确参数类型序列
     * @return 已设为可访问的唯一目标方法
     * @throws ReflectiveOperationException 类或完整方法签名不匹配时抛出
     */
    private static Method findDeclaredMethodReturning(
            ClassLoader classLoader, // 解析目标声明类的相册类加载器
            String className, // 声明目标方法的完整类名
            String methodName, // DEX 中确认的精确方法名
            Class<?> returnType, // 当前方法必须匹配的返回类型
            Class<?>... parameterTypes // 当前方法的完整参数类型序列
    ) throws ReflectiveOperationException {
        return findDeclaredMethodReturning(
                Class.forName(className, false, classLoader),
                methodName,
                returnType,
                parameterTypes
        );
    }

    /**
     * 按完整类名和 DEX 原始字段名精确解析私有字段
     *
     * @param classLoader 相册私有类加载器
     * @param className 声明目标字段的完整类名
     * @param fieldName DEX 中的原始字段名
     * @param fieldType 当前版本确认的精确字段类型
     * @return 已设为可访问的目标字段
     * @throws ReflectiveOperationException 类或字段不匹配时抛出
     */
    private static Field findDeclaredInstanceField(
            ClassLoader classLoader, // 解析目标声明类的相册类加载器
            String className, // 声明目标字段的完整类名
            String fieldName, // DEX 中确认的精确字段名
            Class<?> fieldType // 当前字段必须匹配的实例类型
    ) throws ReflectiveOperationException {
        return findDeclaredInstanceField(
                Class.forName(className, false, classLoader),
                fieldName,
                fieldType
        );
    }

    /**
     * 按声明类和 DEX 原始字段名精确解析私有字段
     *
     * @param targetClass 声明目标字段的相册私有类
     * @param fieldName DEX 中的原始字段名
     * @param fieldType 当前版本确认的精确字段类型
     * @return 已设为可访问的目标字段
     * @throws ReflectiveOperationException 字段名称、类型或实例性质不匹配时抛出
     */
    static Field findDeclaredInstanceField(
            Class<?> targetClass, // 直接声明目标字段的运行时类型
            String fieldName, // DEX 中确认的精确字段名
            Class<?> fieldType // 当前字段必须匹配的实例类型
    )
            throws ReflectiveOperationException {
        // 按目标类本身声明的固定名称读取字段，禁止遍历猜测
        Field field = targetClass.getDeclaredField(fieldName);
        if (Modifier.isStatic(field.getModifiers()) || !fieldType.equals(field.getType())) {
            throw new NoSuchFieldException(
                    targetClass.getName() + "." + fieldName + ":" + fieldType.getName()
            );
        }
        field.setAccessible(true);
        return field;
    }

    /**
     * 要求以 null 接收者调用的私有方法保持静态性质
     *
     * @param method 已按名称和参数解析的方法
     * @return 已确认是静态方法的原目标
     * @throws NoSuchMethodException 目标方法不是静态方法时抛出
     */
    static Method requireStatic(Method method) throws NoSuchMethodException {
        if (!Modifier.isStatic(method.getModifiers())) {
            throw new NoSuchMethodException(method + " must be static");
        }
        return method;
    }

    /**
     * 要求以实际对象调用的私有方法保持实例性质
     *
     * @param method 已按名称和参数解析的方法
     * @return 已确认是实例方法的原目标
     * @throws NoSuchMethodException 目标方法是静态方法时抛出
     */
    static Method requireInstance(Method method) throws NoSuchMethodException {
        if (Modifier.isStatic(method.getModifiers())) {
            throw new NoSuchMethodException(method + " must be an instance method");
        }
        return method;
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
    static Method findDeclaredMethodReturning(
            Class<?> targetClass, // 直接声明目标方法的运行时类型
            String methodName, // DEX 中确认的精确方法名
            Class<?> returnType, // 当前方法必须匹配的返回类型
            Class<?>... parameterTypes // 当前方法的完整参数类型序列
    ) throws NoSuchMethodException {
        // 按固定名称和完整参数序列获取唯一目标方法
        Method method = targetClass.getDeclaredMethod(methodName, parameterTypes);
        if (!returnType.equals(method.getReturnType())) {
            throw new NoSuchMethodException(
                    targetClass.getName() + "." + methodName + " with requested return type"
            );
        }
        method.setAccessible(true);
        return method;
    }
}
