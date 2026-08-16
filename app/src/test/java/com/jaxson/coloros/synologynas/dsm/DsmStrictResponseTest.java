package com.jaxson.coloros.synologynas.dsm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.jaxson.coloros.synologynas.backup.BackupPath;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

/** 验证 API 发现、认证和业务响应不接受 JSON 隐式类型转换 */
public final class DsmStrictResponseTest {
    /** 验证 success 仅接受 JSON 布尔值 */
    @Test
    public void readsOnlyBooleanSuccessValues() throws Exception {
        assertTrue(DsmHttpTransport.readSuccess(
                "SYNO.API.Auth",
                new JSONObject("{\"success\":true}")
        ));
        assertFalse(DsmHttpTransport.readSuccess(
                "SYNO.API.Auth",
                new JSONObject("{\"success\":false}")
        ));

        // malformedResponses 保存缺失或类型错误的成功标志
        String[] malformedResponses = {
                "{}",
                "{\"success\":\"true\"}",
                "{\"success\":1}",
                "{\"success\":null}"
        };
        for (/* 当前待拒绝的成功响应 */ String json : malformedResponses) {
            assertThrows(
                    DsmException.class,
                    () -> DsmHttpTransport.readSuccess(
                            "SYNO.API.Auth",
                            new JSONObject(json)
                    )
            );
        }
    }

    /** 验证 Auth SID 只接受非空字符串 */
    @Test
    public void parsesOnlyNonEmptyStringSid() throws Exception {
        assertEquals(
                "SID_VALUE",
                DsmHttpTransport.parseSid(new JSONObject(
                        "{\"data\":{\"sid\":\" SID_VALUE \"}}"
                ))
        );

        // malformedResponses 保存缺失、空值或类型错误的 SID 响应
        String[] malformedResponses = {
                "{}",
                "{\"data\":{}}",
                "{\"data\":{\"sid\":\"   \"}}",
                "{\"data\":{\"sid\":7}}",
                "{\"data\":{\"sid\":null}}"
        };
        for (/* 当前待拒绝的认证响应 */ String json : malformedResponses) {
            assertThrows(
                    DsmException.class,
                    () -> DsmHttpTransport.parseSid(new JSONObject(json))
            );
        }
    }

    /** 验证错误码必须是 JSON 整数且不能触发错误冲突映射 */
    @Test
    public void rejectsCoercedApiErrorCodes() {
        assertThrows(
                DsmException.class,
                () -> DsmException.requireApiErrorCode(
                        "SYNO.FileStation.Upload",
                        new JSONObject("{\"error\":{\"code\":\"414\"}}")
                )
        );
        assertThrows(
                DsmException.class,
                () -> DsmBackupClient.parseUploadResponse(
                        "SYNO.FileStation.Upload",
                        "{\"success\":false,\"error\":{\"code\":\"414\"}}",
                        new BackupPath("/home/Photos", "IMG_1.jpg"),
                        1L
                )
        );
    }

    /** 验证 API 发现版本字段不接受数字字符串 */
    @Test
    public void rejectsCoercedApiInfoVersions() {
        assertThrows(
                DsmException.class,
                () -> DsmApiInfoParser.parse("""
                        {
                          "success":true,
                          "data":{
                            "SYNO.API.Auth":{
                              "path":"auth.cgi",
                              "minVersion":"1",
                              "maxVersion":7
                            }
                          }
                        }
                        """)
        );
    }

    /** 验证 taskid、md5 和设备型号都拒绝非字符串值 */
    @Test
    public void rejectsCoercedBusinessStrings() {
        assertThrows(
                DsmException.class,
                () -> DsmBackupClient.parseTaskId(
                        "SYNO.FileStation.MD5",
                        new JSONObject("{\"data\":{\"taskid\":1}}")
                )
        );
        assertThrows(
                DsmException.class,
                () -> DsmBackupClient.parseMd5Hash(
                        "SYNO.FileStation.MD5",
                        new JSONObject("{\"md5\":1}")
                )
        );
        assertThrows(
                DsmException.class,
                () -> DsmClient.parseDeviceModel(new JSONObject(
                        "{\"success\":true,\"data\":{\"model\":920}}"
                ))
        );
        assertThrows(
                DsmException.class,
                () -> DsmClient.parseDeleteTaskId(new JSONObject(
                        "{\"success\":true,\"data\":{\"taskid\":1}}"
                ))
        );
    }

    /** 验证共享字符串边界直接拒绝 Android getString 会转换的原始值 */
    @Test
    public void rejectsAndroidStringCoercionAtSharedBoundary() throws JSONException {
        // invalidValues 覆盖 Android JSONObject.getString 会强制转换的原始类型
        Object[] invalidValues = {7, Boolean.FALSE};
        for (Object /* 当前交给严格字符串边界的错误原始值 */ invalidValue : invalidValues) {
            // object 保存一个保持错误原始类型的 DSM 测试字段
            JSONObject object = new JSONObject().put("field", invalidValue);

            // error 保存统一严格字符串边界返回的预期类型异常
            JSONException error = assertThrows(
                    JSONException.class,
                    () /* 读取当前错误类型的 DSM 字段 */ ->
                            DsmHttpTransport.requiredString(object, "field")
            );
            assertEquals("field 不是字符串", error.getMessage());
        }
    }
}
