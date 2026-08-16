package com.jaxson.coloros.synologynas;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;

/**
 * 验证 Hook 目标解析严格使用固定参数与返回类型合同
 */
public final class HookTargetResolverTest {
    /**
     * 验证重载方法按完整参数序列精确解析并核对返回类型
     *
     * @throws Exception 反射目标未按固定签名解析时抛出
     */
    @Test
    public void resolvesMethodByExactParametersAndReturnType() throws Exception {
        // 按固定参数与返回类型解析出的目标方法
        Method method = HookTargetResolver.findDeclaredMethodReturning(
                OverloadedTarget.class,
                "setMessage",
                String.class,
                CharSequence.class
        );

        assertEquals(CharSequence.class, method.getParameterTypes()[0]);
        assertEquals(String.class, method.getReturnType());
    }

    /**
     * 验证参数匹配但返回类型不符时立即拒绝目标
     *
     * @throws Exception 目标按预期拒绝时由 JUnit 核对异常类型
     */
    @Test(expected = NoSuchMethodException.class)
    public void rejectsMethodWithDifferentReturnType() throws Exception {
        HookTargetResolver.findDeclaredMethodReturning(
                OverloadedTarget.class,
                "setMessage",
                Object.class,
                CharSequence.class
        );
    }

    /**
     * 验证目标集合已移除两个缺少设备身份边界的全局文案方法
     */
    @Test
    public void excludesGlobalNasLabelTargets() {
        // 当前完整私有目标与辅助成员的固定数量
        RecordComponent[] components = HookTargets.class.getRecordComponents();
        assertEquals(25, components.length);
        for (RecordComponent /* 当前检查的目标组件 */ component : components) {
            assertFalse("resolveFolderNote".equals(component.getName()));
            assertFalse("resolveNasStateMessage".equals(component.getName()));
        }
    }

    /**
     * 验证运行时按实例读取的字段必须同时匹配名称、类型和非静态性质
     *
     * @throws Exception 严格字段合同未按预期执行时抛出
     */
    @Test
    public void resolvesOnlyExactInstanceFieldContract() throws Exception {
        // 按名称、类型和实例性质解析出的目标字段
        Field field = HookTargetResolver.findDeclaredInstanceField(
                ExactMemberTarget.class,
                "instanceValue",
                String.class
        );

        assertEquals(String.class, field.getType());
        assertThrows(
                NoSuchFieldException.class,
                () -> HookTargetResolver.findDeclaredInstanceField(
                        ExactMemberTarget.class,
                        "instanceValue",
                        Object.class
                )
        );
        assertThrows(
                NoSuchFieldException.class,
                () -> HookTargetResolver.findDeclaredInstanceField(
                        ExactMemberTarget.class,
                        "staticValue",
                        String.class
                )
        );
    }

    /**
     * 验证以 null 或实例接收者调用的方法在安装前核对静态性质
     *
     * @throws Exception 严格方法性质未按预期执行时抛出
     */
    @Test
    public void validatesStaticAndInstanceInvocationContracts() throws Exception {
        // 测试类声明的静态目标方法
        Method staticMethod = ExactMemberTarget.class.getDeclaredMethod("staticMethod");
        // 测试类声明的实例目标方法
        Method instanceMethod = ExactMemberTarget.class.getDeclaredMethod("instanceMethod");

        assertSame(staticMethod, HookTargetResolver.requireStatic(staticMethod));
        assertSame(instanceMethod, HookTargetResolver.requireInstance(instanceMethod));
        assertThrows(
                NoSuchMethodException.class,
                () -> HookTargetResolver.requireStatic(instanceMethod)
        );
        assertThrows(
                NoSuchMethodException.class,
                () -> HookTargetResolver.requireInstance(staticMethod)
        );
    }

    /**
     * 提供名称相同但参数类型不同的严格解析测试目标
     */
    private static final class OverloadedTarget {
        /**
         * 返回 CharSequence 参数重载的固定字符串结果
         *
         * @param value 测试传入的字符序列
         * @return 原字符序列的字符串形式
         */
        private String setMessage(CharSequence value) {
            return value.toString();
        }

        /**
         * 返回 String 参数重载的原字符串
         *
         * @param value 测试传入的字符串
         * @return 保持不变的原字符串
         */
        private String setMessage(String value) {
            return value;
        }
    }

    /**
     * 提供字段类型与方法静态性质的严格解析测试目标
     */
    private static final class ExactMemberTarget {
        // 保存严格实例字段解析使用的字符串值
        private String instanceValue;
        // 保存必须被实例字段解析拒绝的静态字符串值
        private static String staticValue;

        /** 提供严格静态调用合同使用的测试方法 */
        private static void staticMethod() {
        }

        /** 提供严格实例调用合同使用的测试方法 */
        private void instanceMethod() {
        }
    }
}
