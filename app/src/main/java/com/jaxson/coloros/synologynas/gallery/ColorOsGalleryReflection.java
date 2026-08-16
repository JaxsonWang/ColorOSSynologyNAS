package com.jaxson.coloros.synologynas.gallery;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// 集中执行 ColorOS 相册当前 DEX 固定字段反射
final class ColorOsGalleryReflection {
    // 禁止实例化只承载 ColorOS 相册固定反射操作的工具类
    private ColorOsGalleryReflection() {
    }

    // 沿继承层次读取当前版本明确命名的私有字段
    static Object readField(
            Object owner, // 声明或继承目标字段的 ColorOS 对象
            String name // 当前版本确认的混淆字段名
    ) throws ReflectiveOperationException {
        // 从对象具体类型开始定位字段声明
        Class<?> type = owner.getClass();
        while (type != null) {
            try {
                // 当前类型中与合约字段名精确匹配的声明
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(owner);
            } catch (NoSuchFieldException ignored /* 当前类型没有目标字段 */) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(owner.getClass().getName() + "." + name);
    }

    // 读取当前版本明确命名且类型固定为 int 的私有字段
    static int readIntField(
            Object owner, // 声明或继承目标字段的 ColorOS 对象
            String name // 当前版本确认的混淆字段名
    ) throws ReflectiveOperationException {
        // 目标字段反射读取到的原始值
        Object value = readField(owner, name);
        if (!(value instanceof Integer integer /* 已确认的 int 字段值 */)) {
            throw new IllegalStateException(
                    owner.getClass().getName() + "." + name + " is not an int"
            );
        }
        return integer;
    }

    // 从当前类读取字段名和声明类型均与 DEX 合同一致的非空实例
    static Object readTypedField(
            Object owner, // 持有目标 ColorOS 私有组件的对象
            String name, // 当前版本确认的混淆字段名
            String className // 当前版本确认的字段类型完整类名
    ) throws ReflectiveOperationException {
        // 当前类型中与 DEX 字段名精确匹配的声明
        Field field = owner.getClass().getDeclaredField(name);
        if (Modifier.isStatic(field.getModifiers())
                || !field.getType().getName().equals(className)) {
            throw new NoSuchFieldException(
                    owner.getClass().getName() + "." + name + ":" + className
            );
        }
        field.setAccessible(true);
        // 精确目标字段在当前对象上的实际值
        Object value = field.get(owner);
        if (value == null) {
            throw new IllegalStateException(
                    owner.getClass().getName() + "." + name + " is null"
            );
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    // 从当前 xhb.a 精确 ConcurrentHashMap 字段取得 Provider 映射
    static Map<Object, Object> registryMap(Object registry /* ColorOS xhb 注册表对象 */)
            throws ReflectiveOperationException {
        // xhb.a 对应当前运行时 DEX 的 Provider 注册表字段
        Field field = registry.getClass().getDeclaredField("a");
        if (Modifier.isStatic(field.getModifiers())
                || field.getType() != ConcurrentHashMap.class) {
            throw new NoSuchFieldException("ColorOS NAS registry xhb.a");
        }
        field.setAccessible(true);
        // 精确注册表字段保存的 Provider 映射
        Object value = field.get(registry);
        if (!(value instanceof ConcurrentHashMap<?, ?>)) {
            throw new IllegalStateException("ColorOS NAS registry xhb.a is null");
        }
        return (Map<Object, Object>) value;
    }
}
