package com.jaxson.coloros.synologynas.dsm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.json.JSONObject;
import org.junit.Test;

/** 验证 DSM 系统信息型号解析与失败传播 */
public final class DsmSystemInfoResponseTest {
    /** 验证成功响应中的 NAS 型号能够原样返回 */
    @Test
    public void parsesDeviceModel() throws Exception {
        // response 是包含真实 NAS 型号的最小成功样本
        JSONObject response = new JSONObject(
                "{\"success\":true,\"data\":{\"model\":\"DS920+\"}}"
        );

        assertEquals("DS920+", DsmClient.parseDeviceModel(response));
    }

    /** 验证成功响应缺少型号时不会发布默认型号 */
    @Test
    public void rejectsMissingDeviceModel() throws Exception {
        // response 是缺少型号字段的成功样本
        JSONObject response = new JSONObject("{\"success\":true,\"data\":{}}");

        // error 是型号合同缺失触发的明确异常
        DsmException error = assertThrows(
                DsmException.class,
                () -> DsmClient.parseDeviceModel(response)
        );
        assertEquals("SYNO.Core.System 响应缺少 NAS 型号", error.getMessage());
    }

    /** 验证系统信息 DSM 错误码保持可观察 */
    @Test
    public void surfacesDsmApiFailure() throws Exception {
        // response 是系统信息 API 失败样本
        JSONObject response = new JSONObject(
                "{\"success\":false,\"error\":{\"code\":105}}"
        );

        // error 是系统信息失败映射出的 DSM 异常
        DsmException error = assertThrows(
                DsmException.class,
                () -> DsmClient.parseDeviceModel(response)
        );
        assertEquals("SYNO.Core.System 调用失败，DSM 错误码: 105", error.getMessage());
    }
}
