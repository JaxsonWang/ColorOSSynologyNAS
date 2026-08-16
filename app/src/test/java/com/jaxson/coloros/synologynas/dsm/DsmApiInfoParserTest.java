package com.jaxson.coloros.synologynas.dsm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

/** 验证 SYNO.API.Info 动态路径、最大版本与错误码合同 */
public final class DsmApiInfoParserTest {
    /** 验证 DSM 7 API 路径和版本能够完整进入目录 */
    @Test
    public void parsesDsm7ApiPathsAndMaximumVersions() throws Exception {
        // json 是包含认证、列表和下载 API 的最小成功样本
        String json = """
                {
                  "success": true,
                  "data": {
                    "SYNO.API.Auth": {
                      "path": "auth.cgi",
                      "minVersion": 1,
                      "maxVersion": 7
                    },
                    "SYNO.FileStation.List": {
                      "path": "entry.cgi",
                      "minVersion": 1,
                      "maxVersion": 2
                    },
                    "SYNO.FileStation.Download": {
                      "path": "entry.cgi",
                      "minVersion": 1,
                      "maxVersion": 2
                    }
                  }
                }
                """;

        // catalog 是解析后的动态 API 目录
        DsmApiCatalog catalog = DsmApiInfoParser.parse(json);

        assertEquals("auth.cgi", catalog.require("SYNO.API.Auth").path());
        assertEquals(7, catalog.require("SYNO.API.Auth").maxVersion());
        assertEquals(2, catalog.require("SYNO.FileStation.List").maxVersion());
    }

    /** 验证 API 发现失败时原始 DSM 错误码保持可观察 */
    @Test
    public void reportsDsmApiErrorCode() {
        // error 是解析失败响应得到的明确 DSM 异常
        DsmException error = assertThrows(
                DsmException.class,
                () -> DsmApiInfoParser.parse("{\"success\":false,\"error\":{\"code\":102}}")
        );
        assertEquals("SYNO.API.Info 调用失败，DSM 错误码: 102", error.getMessage());
    }
}
