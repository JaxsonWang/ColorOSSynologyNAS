package com.jaxson.coloros.synologynas.gallery;

import com.oplus.aiunit.vision.xhb;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

// 验证 ColorOS 固定字段反射严格遵循当前 DEX 布局
public final class ColorOsGalleryReflectionTest {
    @Test
    // 验证精确字段名和声明类型同时匹配时读取目标实例
    public void readsFieldWithExactNameAndDeclaredType() throws Exception {
        // 固定应由反射返回的目标注册表实例
        xhb expected = new xhb();
        // 同时包含静态同类型字段和精确实例字段的夹具
        ExactTypedFieldFixture fixture = new ExactTypedFieldFixture(expected);

        assertSame(
                expected,
                ColorOsGalleryReflection.readTypedField(
                        fixture,
                        "a",
                        xhb.class.getName()
                )
        );
    }

    @Test
    // 验证类型相同但字段名漂移时立即拒绝读取
    public void rejectsFieldWithDifferentName() {
        // 只包含错误字段名的漂移夹具
        RenamedTypedFieldFixture fixture = new RenamedTypedFieldFixture();

        assertThrows(
                NoSuchFieldException.class,
                () /* 触发精确字段合同解析 */ ->
                        ColorOsGalleryReflection.readTypedField(
                                fixture,
                                "a",
                                xhb.class.getName()
                        )
        );
    }

    @Test
    // 验证 Provider 注册表只读取当前 xhb.a ConcurrentHashMap 字段
    public void readsExactRegistryMap() throws Exception {
        // 使用真实字段名和声明类型的注册表夹具
        xhb registry = new xhb();

        assertSame(registry.a, ColorOsGalleryReflection.registryMap(registry));
    }

    @Test
    // 验证注册表字段类型不是当前 ConcurrentHashMap 合同时立即拒绝
    public void rejectsRegistryWithDifferentMapType() {
        // 使用普通 Map 声明 xhb.a 的错误注册表夹具
        WrongMapTypeRegistry registry = new WrongMapTypeRegistry();

        assertThrows(
                NoSuchFieldException.class,
                () /* 触发错误注册表字段类型解析 */ ->
                        ColorOsGalleryReflection.registryMap(registry)
        );
    }

    // 模拟字段名和类型均与当前 DEX 一致的对象
    private static final class ExactTypedFieldFixture {
        // 模拟不得被当成实例组件读取的静态同类型字段
        private static final xhb STATIC_REGISTRY = new xhb();
        // 模拟 CloudSyncProxyDM.d 或 alq.a 的精确实例字段
        private final xhb a;

        // 创建绑定指定目标实例的类型读取夹具
        private ExactTypedFieldFixture(xhb registry /* 应由反射返回的注册表 */) {
            this.a = registry;
        }
    }

    // 模拟声明类型正确但字段名已经漂移的对象
    private static final class RenamedTypedFieldFixture {
        // 模拟声明类型正确但字段名错误的实例字段
        private final xhb renamed = new xhb();
    }

    // 模拟字段名正确但声明类型已经漂移的注册表
    private static final class WrongMapTypeRegistry {
        // 模拟字段名正确但声明类型不是当前 DEX 类型的注册表
        private final Map<Object, Object> a = new LinkedHashMap<>();
    }
}
