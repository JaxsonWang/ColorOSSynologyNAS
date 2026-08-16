package com.jaxson.coloros.synologynas.gallery;

import java.lang.reflect.Field;
import java.util.Map;

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

    // 在当前类固定字段集合中读取指定私有类型的非空实例
    static Object readFieldByType(
            Object owner, // 持有目标 ColorOS 私有组件的对象
            String className // 当前版本确认的字段类型完整类名
    ) throws ReflectiveOperationException {
        for (Field field : owner.getClass().getDeclaredFields()) { // 当前候选实例字段
            if (field.getType().getName().equals(className)) {
                field.setAccessible(true);
                // 候选字段在当前对象上的实际值
                Object value = field.get(owner);
                if (value != null) {
                    return value;
                }
            }
        }
        throw new NoSuchFieldException(
                owner.getClass().getName() + " field of type " + className
        );
    }

    @SuppressWarnings("unchecked")
    // 从当前 xhb 注册表唯一 Map 字段取得 Provider 映射
    static Map<Object, Object> registryMap(Object registry /* ColorOS xhb 注册表对象 */)
            throws ReflectiveOperationException {
        for (Field field : registry.getClass().getDeclaredFields()) { // 注册表候选字段
            if (Map.class.isAssignableFrom(field.getType())) {
                field.setAccessible(true);
                return (Map<Object, Object>) field.get(registry);
            }
        }
        throw new NoSuchFieldException("ColorOS NAS registry map");
    }
}
