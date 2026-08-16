package com.jaxson.coloros.synologynas.dsm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

/** 验证 DSM HTTPS 地址、反向代理路径与动态 API 路径边界 */
public final class DsmUrlBuilderTest {
    /** 验证 webapi 能在可选反向代理基础路径下正确拼接 */
    @Test
    public void buildsWebApiUrlBelowOptionalReverseProxyPath() {
        // parameters 按预期 URL 顺序保存列表查询参数
        LinkedHashMap<String, String> parameters = new LinkedHashMap<>();
        parameters.put("api", "SYNO.FileStation.List");
        parameters.put("folder_path", "/photo/家庭 相册");

        assertEquals(
                "https://nas.example.com:5001/dsm/webapi/entry.cgi"
                        + "?api=SYNO.FileStation.List&folder_path=%2Fphoto%2F%E5%AE%B6%E5%BA%AD%20%E7%9B%B8%E5%86%8C",
                DsmUrlBuilder.build("https://nas.example.com:5001/dsm/", "entry.cgi", parameters)
        );
    }

    /** 验证发现路径已有 webapi 前缀时不会重复追加目录段 */
    @Test
    public void acceptsWebApiPrefixedDiscoveryPathWithoutDuplicatingSegment() {
        assertEquals(
                "https://nas.example.com/webapi/auth.cgi?api=SYNO.API.Auth",
                DsmUrlBuilder.build(
                        "https://nas.example.com",
                        "/webapi/auth.cgi",
                        Map.of("api", "SYNO.API.Auth")
                )
        );
    }

    /** 验证明文地址与尝试逃逸 webapi 的发现路径都被拒绝 */
    @Test
    public void rejectsCleartextAndEscapingApiPaths() {
        assertThrows(
                IllegalArgumentException.class,
                () -> DsmUrlBuilder.normalizeBaseUrl("http://nas.example.com:5000")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> DsmUrlBuilder.build(
                        "https://nas.example.com",
                        "../auth.cgi",
                        Map.of()
                )
        );
    }
}
