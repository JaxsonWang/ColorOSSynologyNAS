package com.jaxson.coloros.synologynas.dsm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

/** 验证 File Station Delete v2 启动与状态响应边界 */
public final class DsmDeleteResponseTest {
    /** 验证删除启动响应能够提取非空任务标识 */
    @Test
    public void parsesDeleteTaskId() throws Exception {
        // response 是包含任务标识的最小成功样本
        JSONObject response = new JSONObject("""
                {"success":true,"data":{"taskid":"FileStation_Delete_fixture"}}
                """);

        assertEquals(
                "FileStation_Delete_fixture",
                DsmClient.parseDeleteTaskId(response)
        );
    }

    /** 验证删除任务进行中与完成状态均保持原布尔语义 */
    @Test
    public void parsesDeleteTaskProgress() throws Exception {
        assertFalse(DsmClient.parseDeleteFinished(new JSONObject("""
                {"success":true,"data":{"finished":false,"progress":50}}
                """)));
        assertTrue(DsmClient.parseDeleteFinished(new JSONObject("""
                {"success":true,"data":{"finished":true,"progress":100}}
                """)));
    }

    /** 验证删除错误码、缺少任务标识和缺少完成字段都明确失败 */
    @Test
    public void rejectsDeleteErrorAndMalformedResponses() throws Exception {
        // apiError 是 DSM 删除业务失败映射出的明确异常
        DsmException apiError = assertThrows(
                DsmException.class,
                () -> DsmClient.parseDeleteTaskId(new JSONObject("""
                        {"success":false,"error":{"code":408}}
                        """))
        );
        assertEquals(
                "SYNO.FileStation.Delete 调用失败，DSM 错误码: 408",
                apiError.getMessage()
        );
        assertThrows(
                DsmException.class,
                () -> DsmClient.parseDeleteTaskId(new JSONObject("""
                        {"success":true,"data":{}}
                        """))
        );
        assertThrows(
                DsmException.class,
                () -> DsmClient.parseDeleteFinished(new JSONObject("""
                        {"success":true,"data":{}}
                        """))
        );
    }

    /** 验证删除完成标志的字符串、数字和空值不会进入轮询超时 */
    @Test
    public void rejectsNonBooleanDeleteFinishedValues() {
        // invalidFinishedValues 保存协议不允许的 finished JSON 值
        String[] invalidFinishedValues = {"\"true\"", "1", "null"};

        for (/* 当前待拒绝的 finished JSON 值 */ String value : invalidFinishedValues) {
            assertThrows(
                    DsmException.class,
                    () -> DsmClient.parseDeleteFinished(new JSONObject(
                            "{\"success\":true,\"data\":{\"finished\":" + value + "}}"
                    ))
            );
        }
    }
}
