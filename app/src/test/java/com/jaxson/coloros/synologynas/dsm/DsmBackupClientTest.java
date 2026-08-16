package com.jaxson.coloros.synologynas.dsm;

import com.jaxson.coloros.synologynas.backup.BackupPath;

import org.json.JSONObject;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/** 验证 File Station MD5 与 Upload v2 的编码和成功边界 */
public final class DsmBackupClientTest {
    /** 验证上传字段顺序、v2 版本和 file 最后出现的官方合同 */
    @Test
    public void buildsOfficialUploadV2MultipartContractWithFileAsLastPart() {
        // body 是文件内容之前的 UTF-8 multipart 文本
        String body = new String(
                DsmBackupClient.multipartPrefix(
                        "boundary",
                        "/home/Photos/ColorOS Backup/PLK110/Camera",
                        "IMG_1.jpg"
                ),
                StandardCharsets.UTF_8
        );

        assertTrue(body.contains("name=\"api\"\r\n\r\nSYNO.FileStation.Upload"));
        assertTrue(body.contains("name=\"version\"\r\n\r\n2"));
        assertTrue(body.contains("name=\"method\"\r\n\r\nupload"));
        assertTrue(body.contains("name=\"create_parents\"\r\n\r\ntrue"));
        assertTrue(body.contains("name=\"overwrite\"\r\n\r\nfalse"));
        assertTrue(body.endsWith(
                "name=\"file\"; filename=\"IMG_1.jpg\"\r\n"
                        + "Content-Type: application/octet-stream\r\n\r\n"
        ));
    }

    /** 验证 MD5 文件路径按官方 JSON 字符串参数编码 */
    @Test
    public void encodesMd5FilePathAsOfficialJsonStringParameter() {
        // url 是包含 JSON 引号和远端路径百分号编码的请求地址
        String url = DsmUrlBuilder.build(
                "https://nas.example.test",
                "entry.cgi",
                Map.of(
                        "api", "SYNO.FileStation.MD5",
                        "file_path", DsmBackupClient.fileStationStringParameter(
                                "/home/Photos/ColorOS Backup/PLK110/Camera/IMG_1.jpg"
                        )
                )
        );

        assertTrue(url.contains(
                "file_path=%22%2Fhome%2FPhotos%2FColorOS%20Backup%2FPLK110"
                        + "%2FCamera%2FIMG_1.jpg%22"
        ));
    }

    /** 验证文件不存在和 create_parents 尚未创建父目录都表示 MD5 目标缺失 */
    @Test
    public void treatsMissingFileAndMissingParentPathAsAbsentMd5Target() {
        assertTrue(DsmBackupClient.isMissingMd5TargetError(408));
        assertTrue(DsmBackupClient.isMissingMd5TargetError(418));
        assertFalse(DsmBackupClient.isMissingMd5TargetError(414));
    }

    /** 验证 MD5 任务标识同样按 JSON 字符串参数编码 */
    @Test
    public void encodesMd5TaskIdAsOfficialJsonStringParameter() {
        // encoded 是含 JSON 双引号的任务状态查询参数
        String encoded = DsmUrlBuilder.encodeParameters(Map.of(
                "taskid", DsmBackupClient.fileStationStringParameter("51CBD95028B22AED")
        ));

        assertTrue(encoded.contains("taskid=%2251CBD95028B22AED%22"));
    }

    /** 验证 MD5 任务进行中与完成状态只接受布尔值 */
    @Test
    public void parsesStrictMd5FinishedBoolean() throws Exception {
        assertFalse(DsmBackupClient.parseMd5Finished(
                "SYNO.FileStation.MD5",
                new JSONObject("{\"finished\":false}")
        ));
        assertTrue(DsmBackupClient.parseMd5Finished(
                "SYNO.FileStation.MD5",
                new JSONObject("{\"finished\":true}")
        ));
    }

    /** 验证 MD5 完成标志的字符串、数字、空值和缺失都立即失败 */
    @Test
    public void rejectsMalformedMd5FinishedValues() {
        // malformedData 保存 finished 类型错误和字段缺失的状态对象
        String[] malformedData = {
                "{\"finished\":\"true\"}",
                "{\"finished\":1}",
                "{\"finished\":null}",
                "{}"
        };

        for (/* 当前待拒绝的 MD5 状态对象 */ String json : malformedData) {
            assertThrows(
                    DsmException.class,
                    () -> DsmBackupClient.parseMd5Finished(
                            "SYNO.FileStation.MD5",
                            new JSONObject(json)
                    )
            );
        }
    }

    /** 验证 MD5 任务标识和摘要不会把非字符串 JSON 值隐式转成文本 */
    @Test
    public void rejectsNonStringMd5TaskIdAndHash() throws Exception {
        // taskIdError 是数字任务标识触发的严格类型错误
        DsmException taskIdError = assertThrows(
                DsmException.class,
                () -> DsmBackupClient.parseTaskId(
                        "SYNO.FileStation.MD5",
                        new JSONObject("{\"data\":{\"taskid\":51}}")
                )
        );
        // hashError 是数字摘要触发的严格类型错误
        DsmException hashError = assertThrows(
                DsmException.class,
                () -> DsmBackupClient.parseMd5Hash(
                        "SYNO.FileStation.MD5",
                        new JSONObject("{\"md5\":1234}")
                )
        );

        assertEquals("SYNO.FileStation.MD5 启动响应 taskid 类型错误", taskIdError.getMessage());
        assertEquals("SYNO.FileStation.MD5 状态响应 MD5 类型错误", hashError.getMessage());
    }

    /** 验证只有 DSM 明确返回 success JSON 才确认上传字节数 */
    @Test
    public void acceptsExplicitDsmUploadSuccess() throws Exception {
        // path 是上传响应解析所需的远端路径语义
        BackupPath path = new BackupPath("/home/Photos/ColorOS Backup", "IMG_1.jpg");

        assertEquals(
                1_024L,
                DsmBackupClient.parseUploadResponse(
                        "SYNO.FileStation.Upload",
                        "{\"success\":true}",
                        path,
                        1_024L
                )
        );
    }

    /** 验证 HTTP 2xx 空正文不再被伪造成上传成功 */
    @Test
    public void rejectsEmptyDsmUploadResponse() {
        // path 是失败消息仍可关联的远端路径语义
        BackupPath path = new BackupPath("/home/Photos/ColorOS Backup", "IMG_1.jpg");

        // error 是空响应触发的明确协议异常
        DsmException error = assertThrows(
                DsmException.class,
                () -> DsmBackupClient.parseUploadResponse(
                        "SYNO.FileStation.Upload",
                        "",
                        path,
                        1_024L
                )
        );
        assertEquals("SYNO.FileStation.Upload 未返回有效 JSON", error.getMessage());
    }

    /** 验证 DSM 已存在错误仍映射为备份仓储识别的冲突类型 */
    @Test
    public void preservesRemoteFileConflictMapping() {
        // path 是冲突异常必须携带的完整远端路径
        BackupPath path = new BackupPath("/home/Photos/ColorOS Backup", "IMG_1.jpg");

        // error 是 overwrite=false 冲突的专用异常
        DsmRemoteFileAlreadyExistsException error = assertThrows(
                DsmRemoteFileAlreadyExistsException.class,
                () -> DsmBackupClient.parseUploadResponse(
                        "SYNO.FileStation.Upload",
                        "{\"success\":false,\"error\":{\"code\":414}}",
                        path,
                        1_024L
                )
        );
        assertEquals(
                "群晖备份文件已存在: /home/Photos/ColorOS Backup/IMG_1.jpg",
                error.getMessage()
        );
    }
}
