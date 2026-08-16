package com.jaxson.coloros.synologynas.dsm;

import static org.junit.Assert.assertEquals;

import org.json.JSONObject;
import org.junit.Test;

public final class DsmFileStationErrorTest {
    @Test
    public void explainsMissingSynologyPhotosRoot() throws Exception {
        JSONObject response = new JSONObject("""
                {"success":false,"error":{"code":408}}
                """);

        DsmException error = DsmException.fromFileStationListResponse("/photo", response);

        assertEquals(
                "远端目录不存在: /photo。Synology Photos 个人空间通常为 /home/Photos，"
                        + "共享空间为 /photo",
                error.getMessage()
        );
    }

    @Test
    public void explainsFileStationPermissionFailure() throws Exception {
        JSONObject response = new JSONObject("""
                {"success":false,"error":{"code":407}}
                """);

        DsmException error = DsmException.fromFileStationListResponse(
                "/home/Photos",
                response
        );

        assertEquals(
                "当前 DSM 账号无权读取远端目录: /home/Photos，DSM 错误码: 407",
                error.getMessage()
        );
    }
}
