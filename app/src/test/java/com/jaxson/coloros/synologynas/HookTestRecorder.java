package com.jaxson.coloros.synologynas;

import java.lang.reflect.Executable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import io.github.libxposed.api.XposedInterface;

/**
 * 记录安装器提交的 Hook 目标、顺序和拦截器
 */
final class HookTestRecorder implements InvocationHandler {
    // 保存按实际注册顺序收到的 Hook 目标
    private final ArrayList<Executable> executables = new ArrayList<>();
    // 按目标成员身份保存安装完成的拦截器
    private final Map<Executable, XposedInterface.Hooker> hookers = new IdentityHashMap<>();
    // 暴露给被测安装器的 libxposed 接口代理
    private final XposedInterface xposed;

    /**
     * 创建只记录 Hook 注册且不执行外部副作用的接口代理
     */
    HookTestRecorder() {
        xposed = (XposedInterface) Proxy.newProxyInstance(
                XposedInterface.class.getClassLoader(),
                new Class<?>[]{XposedInterface.class},
                this
        );
    }

    /**
     * 处理安装器对 libxposed 接口的 Hook 注册与日志调用
     *
     * @param proxy 当前接口代理实例
     * @param method 本次调用的接口方法
     * @param arguments 本次调用的参数
     * @return Hook Builder、空日志结果或对象基础方法结果
     */
    @Override
    public Object invoke(
            Object proxy, // 当前接口代理实例
            Method method, // 本次调用的接口方法
            Object[] arguments // 本次接口调用参数
    ) {
        if (method.getDeclaringClass() == Object.class) {
            return invokeObjectMethod(proxy, method, arguments);
        }
        if ("hook".equals(method.getName())) {
            // 保存本次安装器准备注册的目标成员
            Executable executable = (Executable) arguments[0];
            executables.add(executable);
            return hookBuilder(executable);
        }
        if ("log".equals(method.getName())) {
            return null;
        }
        throw new AssertionError("unexpected XposedInterface call: " + method.getName());
    }

    /**
     * 返回供被测安装器使用的 libxposed 接口代理
     *
     * @return 当前记录器持有的唯一接口代理
     */
    XposedInterface xposed() {
        return xposed;
    }

    /**
     * 返回 Hook 安装器提交目标的不可变顺序视图
     *
     * @return 按实际注册顺序排列的目标成员
     */
    List<Executable> executables() {
        return List.copyOf(executables);
    }

    /**
     * 取得指定目标安装完成的拦截器
     *
     * @param executable 需要查找拦截器的精确目标成员
     * @return 绑定该目标的拦截器
     */
    XposedInterface.Hooker hooker(Executable executable) {
        // 保存指定目标已经安装的拦截器
        XposedInterface.Hooker hooker = hookers.get(executable);
        if (hooker == null) {
            throw new AssertionError("hooker not installed for " + executable);
        }
        return hooker;
    }

    /**
     * 创建接收拦截器并完成本次 Hook 注册的 Builder 代理
     *
     * @param executable 当前 Builder 对应的目标成员
     * @return 支持直接 intercept 调用的 Builder 代理
     */
    private XposedInterface.HookBuilder hookBuilder(Executable executable) {
        // 负责记录 Builder 方法调用的代理处理器
        InvocationHandler handler = (
                Object proxy /* 当前 Builder 代理实例 */,
                Method method /* 本次调用的 Builder 方法 */,
                Object[] arguments /* 本次 Builder 调用参数 */
        ) -> {
            if ("intercept".equals(method.getName())) {
                hookers.put(executable, (XposedInterface.Hooker) arguments[0]);
                return hookHandle(executable);
            }
            if (method.getReturnType() == XposedInterface.HookBuilder.class) {
                return proxy;
            }
            throw new AssertionError("unexpected HookBuilder call: " + method.getName());
        };
        return (XposedInterface.HookBuilder) Proxy.newProxyInstance(
                XposedInterface.HookBuilder.class.getClassLoader(),
                new Class<?>[]{XposedInterface.HookBuilder.class},
                handler
        );
    }

    /**
     * 创建仅返回已安装目标成员的 Hook 句柄代理
     *
     * @param executable 当前句柄代表的目标成员
     * @return 不执行卸载或替换的句柄代理
     */
    private static XposedInterface.HookHandle hookHandle(Executable executable) {
        // 负责响应句柄成员读取的代理处理器
        InvocationHandler handler = (
                Object proxy /* 当前 Hook 句柄代理实例 */,
                Method method /* 本次调用的句柄方法 */,
                Object[] arguments /* 本次句柄调用参数 */
        ) -> {
            if ("getExecutable".equals(method.getName())) {
                return executable;
            }
            if ("getId".equals(method.getName())) {
                return null;
            }
            if ("unhook".equals(method.getName())) {
                return null;
            }
            throw new AssertionError("unexpected HookHandle call: " + method.getName());
        };
        return (XposedInterface.HookHandle) Proxy.newProxyInstance(
                XposedInterface.HookHandle.class.getClassLoader(),
                new Class<?>[]{XposedInterface.HookHandle.class},
                handler
        );
    }

    /**
     * 为接口代理提供稳定的对象身份方法
     *
     * @param proxy 当前接口代理实例
     * @param method 当前对象基础方法
     * @param arguments 当前对象基础方法参数
     * @return 对象基础方法结果
     */
    private static Object invokeObjectMethod(
            Object proxy, // 当前接口代理实例
            Method method, // 当前对象基础方法
            Object[] arguments // 当前对象基础方法参数
    ) {
        return switch (method.getName()) {
            case "toString" -> "HookTestRecorder";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == arguments[0];
            default -> throw new AssertionError("unexpected Object call: " + method.getName());
        };
    }

    /**
     * 直接执行指定 Hook 拦截器并把 proceed 调用交给测试动作
     *
     * @param hooker 需要执行的已安装拦截器
     * @param thisObject 当前 Hook 调用的接收者
     * @param arguments 当前 Hook 调用参数
     * @param proceedAction 原调用继续执行时触发的测试动作
     * @return 拦截器最终返回值
     * @throws Throwable 拦截器或测试动作抛出的原始异常
     */
    static Object invokeHook(
            XposedInterface.Hooker hooker, // 需要执行的已安装拦截器
            Object thisObject, // 当前 Hook 调用的接收者
            List<Object> arguments, // 当前 Hook 调用参数
            ProceedAction proceedAction // 原调用继续执行时触发的测试动作
    ) throws Throwable {
        return hooker.intercept(new XposedInterface.Chain() {
            /**
             * 返回测试调用未绑定的占位目标成员
             *
             * @return 固定为空的目标成员
             */
            @Override
            public Executable getExecutable() {
                return null;
            }

            /**
             * 返回本次测试 Hook 调用的接收者
             *
             * @return 调用 invokeHook 时传入的接收者
             */
            @Override
            public Object getThisObject() {
                return thisObject;
            }

            /**
             * 返回本次测试 Hook 调用的不可变参数列表
             *
             * @return 调用 invokeHook 时传入的参数
             */
            @Override
            public List<Object> getArgs() {
                return arguments;
            }

            /**
             * 返回指定位置的 Hook 调用参数
             *
             * @param index 需要读取的参数位置
             * @return 指定位置的调用参数
             */
            @Override
            public Object getArg(int index /* 需要读取的参数位置 */) {
                return arguments.get(index);
            }

            /**
             * 以原参数继续执行测试调用
             *
             * @return 测试动作返回值
             * @throws Throwable 测试动作抛出的原始异常
             */
            @Override
            public Object proceed() throws Throwable {
                return proceedAction.proceed(arguments.toArray());
            }

            /**
             * 以替换参数继续执行测试调用
             *
             * @param replacementArguments 替换后的完整参数序列
             * @return 测试动作返回值
             * @throws Throwable 测试动作抛出的原始异常
             */
            @Override
            public Object proceed(
                    Object[] replacementArguments /* 替换后的完整参数序列 */
            ) throws Throwable {
                return proceedAction.proceed(replacementArguments);
            }

            /**
             * 拒绝当前直接 Hook 测试未使用的接收者替换调用
             *
             * @param replacementThisObject 替换后的接收者
             * @return 本测试不会返回结果
             */
            @Override
            public Object proceedWith(
                    Object replacementThisObject /* 替换后的接收者 */
            ) {
                throw new AssertionError("unexpected proceedWith call");
            }

            /**
             * 拒绝当前直接 Hook 测试未使用的接收者和参数替换调用
             *
             * @param replacementThisObject 替换后的接收者
             * @param replacementArguments 替换后的完整参数序列
             * @return 本测试不会返回结果
             */
            @Override
            public Object proceedWith(
                    Object replacementThisObject, // 替换后的接收者
                    Object[] replacementArguments // 替换后的完整参数序列
            ) {
                throw new AssertionError("unexpected proceedWith call");
            }
        });
    }

    /**
     * 表达 Hook 调用继续执行时由测试控制的原方法动作
     */
    @FunctionalInterface
    interface ProceedAction {
        /**
         * 执行一次原方法测试动作
         *
         * @param arguments 本次继续执行使用的完整参数序列
         * @return 测试动作产生的原方法结果
         * @throws Throwable 测试动作需要传播的原始异常
         */
        Object proceed(Object[] arguments) throws Throwable;
    }
}
