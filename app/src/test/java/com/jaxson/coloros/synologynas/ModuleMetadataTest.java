package com.jaxson.coloros.synologynas;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * 验证 libxposed API、入口类和静态目标作用域元数据保持固定合同
 */
public final class ModuleMetadataTest {
    /**
     * 验证模块只声明 API 102 入口并将静态作用域限制为 ColorOS 相册
     *
     * @throws IOException 测试资源无法读取时抛出
     */
    @Test
    public void declaresModernApi102EntryAndGalleryOnlyScope() throws IOException {
        // 读取模块声明的 libxposed API 版本与静态作用域属性
        Properties moduleProperties = loadProperties("META-INF/xposed/module.prop");

        assertEquals("101", moduleProperties.getProperty("minApiVersion"));
        assertEquals("102", moduleProperties.getProperty("targetApiVersion"));
        assertEquals("true", moduleProperties.getProperty("staticScope"));
        assertEquals(
                "com.jaxson.coloros.synologynas.ColorOsSynologyNasModule",
                loadText("META-INF/xposed/java_init.list")
        );
        assertEquals("com.coloros.gallery3d", loadText("META-INF/xposed/scope.list"));
    }

    /**
     * 从测试类路径加载指定 Java Properties 资源
     *
     * @param resourceName 相对于测试类路径根目录的资源名称
     * @return 已解析的属性集合
     * @throws IOException 资源内容无法读取时抛出
     */
    private static Properties loadProperties(String resourceName) throws IOException {
        // 保存并返回当前元数据资源解析出的键值集合
        Properties properties = new Properties();
        try (InputStream /* 当前属性资源输入流 */ input = resource(resourceName)) {
            properties.load(input);
        }
        return properties;
    }

    /**
     * 从测试类路径加载并清理指定纯文本资源
     *
     * @param resourceName 相对于测试类路径根目录的资源名称
     * @return 使用 UTF-8 解码且去除首尾空白的资源文本
     * @throws IOException 资源内容无法读取时抛出
     */
    private static String loadText(String resourceName) throws IOException {
        try (InputStream /* 当前纯文本资源输入流 */ input = resource(resourceName)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8).trim();
        }
    }

    /**
     * 从当前测试类加载器取得必需的模块元数据资源
     *
     * @param resourceName 相对于测试类路径根目录的资源名称
     * @return 非空资源输入流
     */
    private static InputStream resource(String resourceName) {
        // 保存类加载器针对指定名称返回的资源输入流
        InputStream input = ModuleMetadataTest.class.getClassLoader()
                .getResourceAsStream(resourceName);
        assertNotNull("Missing resource: " + resourceName, input);
        return input;
    }
}
