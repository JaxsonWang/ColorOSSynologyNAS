package com.jaxson.coloros.synologynas.gallery;

// 集中解析 ColorOS 私有枚举
final class ColorOsNasReflection {
    // 禁止实例化只承载 ColorOS 私有反射公共操作的工具类
    private ColorOsNasReflection() {
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    // 从相册类加载器解析当前私有枚举值，集中保持反射行为一致
    static Object enumValue(
            ClassLoader classLoader, // 解析 ColorOS 私有类型的相册类加载器
            String className, // 当前版本私有枚举的完整类名
            String value // 当前合约要求返回的枚举常量名
    ) throws ReflectiveOperationException {
        // 当前 ColorOS 私有枚举的运行时类型
        Class<?> type = Class.forName(className, false, classLoader);
        return Enum.valueOf((Class<? extends Enum>) type, value);
    }
}
