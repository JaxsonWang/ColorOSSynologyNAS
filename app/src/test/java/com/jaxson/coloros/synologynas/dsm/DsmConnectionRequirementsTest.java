package com.jaxson.coloros.synologynas.dsm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

/** 验证保存并连接按备份开关发现并校验 File Station v2 能力 */
public final class DsmConnectionRequirementsTest {
    /** 验证关闭备份时只发现浏览、删除和系统信息 API */
    @Test
    public void excludesBackupApisWhenBackupIsDisabled() throws Exception {
        assertEquals(
                "SYNO.API.Auth,SYNO.FileStation.List,"
                        + "SYNO.FileStation.Download,SYNO.FileStation.Thumb,"
                        + "SYNO.FileStation.Delete,SYNO.Core.System",
                DsmClient.discoveryQuery(false)
        );
        DsmClient.requireTestConnectionApis(baseCatalog(), false);
    }

    /** 验证开启备份时同一次发现请求包含 Upload 与 MD5 */
    @Test
    public void includesBackupApisWhenBackupIsEnabled() {
        assertEquals(
                "SYNO.API.Auth,SYNO.FileStation.List,"
                        + "SYNO.FileStation.Download,SYNO.FileStation.Thumb,"
                        + "SYNO.FileStation.Delete,SYNO.Core.System,"
                        + "SYNO.FileStation.Upload,SYNO.FileStation.MD5",
                DsmClient.discoveryQuery(true)
        );
    }

    /** 验证开启备份时缺少 Upload 或 MD5 会使连接验证明确失败 */
    @Test
    public void rejectsMissingEnabledBackupApis() {
        // missingUpload 是完全缺少上传 API 的基础连接目录
        DsmException missingUpload = assertThrows(
                DsmException.class,
                () -> DsmClient.requireTestConnectionApis(baseCatalog(), true)
        );
        assertEquals("DSM 未提供必需 API: SYNO.FileStation.Upload", missingUpload.getMessage());

        // apisWithoutMd5 保存支持上传但缺少 MD5 的连接目录
        Map<String, DsmApiInfo> apisWithoutMd5 = baseApis();
        apisWithoutMd5.put(
                "SYNO.FileStation.Upload",
                new DsmApiInfo("SYNO.FileStation.Upload", "entry.cgi", 1, 2)
        );
        // missingMd5 是仅缺少 MD5 API 得到的连接失败
        DsmException missingMd5 = assertThrows(
                DsmException.class,
                () -> DsmClient.requireTestConnectionApis(
                        new DsmApiCatalog(apisWithoutMd5),
                        true
                )
        );
        assertEquals("DSM 未提供必需 API: SYNO.FileStation.MD5", missingMd5.getMessage());
    }

    /** 验证开启备份时 Upload 与 MD5 都必须明确覆盖 v2 */
    @Test
    public void requiresVersionTwoForEnabledBackupApis() throws Exception {
        // validApis 保存浏览合同和支持 v2 的上传 API
        Map<String, DsmApiInfo> validApis = baseApis();
        validApis.put(
                "SYNO.FileStation.Upload",
                new DsmApiInfo("SYNO.FileStation.Upload", "entry.cgi", 1, 2)
        );
        validApis.put(
                "SYNO.FileStation.MD5",
                new DsmApiInfo("SYNO.FileStation.MD5", "entry.cgi", 2, 2)
        );
        DsmClient.requireTestConnectionApis(new DsmApiCatalog(validApis), true);

        // invalidApis 保存不支持 v2 的 MD5 API
        Map<String, DsmApiInfo> invalidApis = new LinkedHashMap<>(validApis);
        invalidApis.put(
                "SYNO.FileStation.MD5",
                new DsmApiInfo("SYNO.FileStation.MD5", "entry.cgi", 3, 3)
        );
        // error 是备份 API 版本合同失败得到的明确异常
        DsmException error = assertThrows(
                DsmException.class,
                () -> DsmClient.requireTestConnectionApis(
                        new DsmApiCatalog(invalidApis),
                        true
                )
        );
        assertEquals("SYNO.FileStation.MD5 未提供 v2", error.getMessage());

        // invalidUploadApis 保存不支持 v2 的上传 API
        Map<String, DsmApiInfo> invalidUploadApis = new LinkedHashMap<>(validApis);
        invalidUploadApis.put(
                "SYNO.FileStation.Upload",
                new DsmApiInfo("SYNO.FileStation.Upload", "entry.cgi", 3, 3)
        );
        // uploadError 是上传版本合同失败得到的明确异常
        DsmException uploadError = assertThrows(
                DsmException.class,
                () -> DsmClient.requireTestConnectionApis(
                        new DsmApiCatalog(invalidUploadApis),
                        true
                )
        );
        assertEquals("SYNO.FileStation.Upload 未提供 v2", uploadError.getMessage());
    }

    /** 验证浏览链路要求 List API 明确覆盖 v2 */
    @Test
    public void requiresVersionTwoForMediaListing() {
        // invalidApis 保存只支持 v1 的列表 API
        Map<String, DsmApiInfo> invalidApis = baseApis();
        invalidApis.put(
                "SYNO.FileStation.List",
                new DsmApiInfo("SYNO.FileStation.List", "entry.cgi", 1, 1)
        );
        // error 是列表版本合同失败得到的明确异常
        DsmException error = assertThrows(
                DsmException.class,
                () -> DsmClient.requireTestConnectionApis(
                        new DsmApiCatalog(invalidApis),
                        false
                )
        );
        assertEquals("DSM 未提供 SYNO.FileStation.List v2", error.getMessage());
    }

    /**
     * 创建不含可选备份 API 的基础连接目录
     *
     * @return 浏览与删除链路所需的 API 目录
     */
    private static DsmApiCatalog baseCatalog() {
        return new DsmApiCatalog(baseApis());
    }

    /**
     * 创建不含可选备份 API 的基础连接映射
     *
     * @return 浏览与删除链路所需的 API 映射
     */
    private static Map<String, DsmApiInfo> baseApis() {
        // apis 保存连接验证直接要求的浏览和删除 API
        Map<String, DsmApiInfo> apis = new LinkedHashMap<>();
        apis.put(
                "SYNO.FileStation.List",
                new DsmApiInfo("SYNO.FileStation.List", "entry.cgi", 1, 2)
        );
        apis.put(
                "SYNO.FileStation.Download",
                new DsmApiInfo("SYNO.FileStation.Download", "entry.cgi", 1, 2)
        );
        apis.put(
                "SYNO.FileStation.Thumb",
                new DsmApiInfo("SYNO.FileStation.Thumb", "entry.cgi", 1, 2)
        );
        apis.put(
                "SYNO.FileStation.Delete",
                new DsmApiInfo("SYNO.FileStation.Delete", "entry.cgi", 1, 2)
        );
        return apis;
    }
}
