package com.jaxson.coloros.synologynas.dsm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

import java.io.IOException;
import java.util.Map;

/** 验证浏览与备份网关公开同一严格注销合同 */
public final class DsmLogoutContractTest {
    /** 验证两个网关都要求调用者提供 API 目录与当前 SID */
    @Test
    public void exposesLogoutOnBothGateways() throws Exception {
        assertEquals(
                void.class,
                DsmGateway.class
                        .getMethod("logout", DsmApiCatalog.class, String.class)
                        .getReturnType()
        );
        assertEquals(
                void.class,
                DsmBackupGateway.class
                        .getMethod("logout", DsmApiCatalog.class, String.class)
                        .getReturnType()
        );
        assertEquals(
                IOException.class,
                DsmGateway.class
                        .getMethod("logout", DsmApiCatalog.class, String.class)
                        .getExceptionTypes()[0]
        );
        assertEquals(
                IOException.class,
                DsmBackupGateway.class
                        .getMethod("logout", DsmApiCatalog.class, String.class)
                        .getExceptionTypes()[0]
        );
    }

    /** 验证共享注销表单使用发现版本、固定会话名与唯一 SID */
    @Test
    public void buildsExactAuthLogoutParametersWithoutCredentials() {
        // auth 是 DSM 动态发现的认证 API 描述
        DsmApiInfo auth = new DsmApiInfo("SYNO.API.Auth", "auth.cgi", 1, 7);
        // parameters 是两个客户端共享的注销表单字段
        Map<String, String> parameters = DsmClient.logoutParameters(auth, "SID_VALUE");

        assertEquals("SYNO.API.Auth", parameters.get("api"));
        assertEquals("7", parameters.get("version"));
        assertEquals("logout", parameters.get("method"));
        assertEquals(DsmClient.SESSION_NAME, parameters.get("session"));
        assertEquals("SID_VALUE", parameters.get("_sid"));
        assertEquals(5, parameters.size());
        assertFalse(parameters.containsKey("account"));
        assertFalse(parameters.containsKey("passwd"));
        assertFalse(parameters.containsKey("otp_code"));
    }
}
