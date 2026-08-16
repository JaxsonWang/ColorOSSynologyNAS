package com.jaxson.coloros.synologynas.dsm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

public final class DsmDeleteResponseTest {
    @Test
    public void parsesDeleteTaskId() throws Exception {
        JSONObject response = new JSONObject("""
                {"success":true,"data":{"taskid":"FileStation_Delete_fixture"}}
                """);

        assertEquals(
                "FileStation_Delete_fixture",
                DsmClient.parseDeleteTaskId(response)
        );
    }

    @Test
    public void parsesDeleteTaskProgress() throws Exception {
        assertFalse(DsmClient.parseDeleteFinished(new JSONObject("""
                {"success":true,"data":{"finished":false,"progress":50}}
                """)));
        assertTrue(DsmClient.parseDeleteFinished(new JSONObject("""
                {"success":true,"data":{"finished":true,"progress":100}}
                """)));
    }

    @Test
    public void rejectsDeleteErrorAndMalformedResponses() throws Exception {
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
}
