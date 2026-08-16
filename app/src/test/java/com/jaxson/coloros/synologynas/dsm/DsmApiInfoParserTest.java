package com.jaxson.coloros.synologynas.dsm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public final class DsmApiInfoParserTest {
    @Test
    public void parsesDsm7ApiPathsAndMaximumVersions() throws Exception {
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

        DsmApiCatalog catalog = DsmApiInfoParser.parse(json);

        assertEquals("auth.cgi", catalog.require("SYNO.API.Auth").path());
        assertEquals(7, catalog.require("SYNO.API.Auth").maxVersion());
        assertEquals(2, catalog.require("SYNO.FileStation.List").maxVersion());
    }

    @Test
    public void reportsDsmApiErrorCode() {
        DsmException error = assertThrows(
                DsmException.class,
                () -> DsmApiInfoParser.parse("{\"success\":false,\"error\":{\"code\":102}}")
        );
        assertEquals("SYNO.API.Info 调用失败，DSM 错误码: 102", error.getMessage());
    }
}

