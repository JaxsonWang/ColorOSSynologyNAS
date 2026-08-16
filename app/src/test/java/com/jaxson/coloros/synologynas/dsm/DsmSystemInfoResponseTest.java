package com.jaxson.coloros.synologynas.dsm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.json.JSONObject;
import org.junit.Test;

public final class DsmSystemInfoResponseTest {
    @Test
    public void parsesDeviceModel() throws Exception {
        JSONObject response = new JSONObject(
                "{\"success\":true,\"data\":{\"model\":\"DS920+\"}}"
        );

        assertEquals("DS920+", DsmClient.parseDeviceModel(response));
    }

    @Test
    public void rejectsMissingDeviceModel() throws Exception {
        JSONObject response = new JSONObject("{\"success\":true,\"data\":{}}");

        DsmException error = assertThrows(
                DsmException.class,
                () -> DsmClient.parseDeviceModel(response)
        );
        assertEquals("SYNO.Core.System 响应缺少 NAS 型号", error.getMessage());
    }

    @Test
    public void surfacesDsmApiFailure() throws Exception {
        JSONObject response = new JSONObject(
                "{\"success\":false,\"error\":{\"code\":105}}"
        );

        DsmException error = assertThrows(
                DsmException.class,
                () -> DsmClient.parseDeviceModel(response)
        );
        assertEquals("SYNO.Core.System 调用失败，DSM 错误码: 105", error.getMessage());
    }
}
