package com.jaxson.coloros.synologynas.dsm;

import static org.junit.Assert.assertEquals;

import org.json.JSONObject;
import org.junit.Test;

/** 验证 File Station 列表错误对配置目录问题的直接说明 */
public final class DsmFileStationErrorTest {
    /** 验证远端根目录不存在时提示 Synology Photos 常见目录 */
    @Test
    public void explainsMissingSynologyPhotosRoot() throws Exception {
        // response 是 DSM 目录不存在错误样本
        JSONObject response = new JSONObject("""
                {"success":false,"error":{"code":408}}
                """);

        // error 是面向配置调用方的目录错误说明
        DsmException error = DsmException.fromFileStationListResponse("/photo", response);

        assertEquals(
                "远端目录不存在: /photo。Synology Photos 个人空间通常为 /home/Photos，"
                        + "共享空间为 /photo",
                error.getMessage()
        );
    }

    /** 验证账号无目录读取权限时保留路径与 DSM 错误码 */
    @Test
    public void explainsFileStationPermissionFailure() throws Exception {
        // response 是 DSM 目录权限错误样本
        JSONObject response = new JSONObject("""
                {"success":false,"error":{"code":407}}
                """);

        // error 是面向配置调用方的权限错误说明
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
