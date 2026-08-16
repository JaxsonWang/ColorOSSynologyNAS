package com.jaxson.coloros.synologynas.dsm;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class DsmBackupClientTest {
    @Test
    public void buildsOfficialUploadV2MultipartContractWithFileAsLastPart() {
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

    @Test
    public void encodesMd5FilePathAsOfficialJsonStringParameter() {
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

    @Test
    public void treatsMissingFileAndMissingParentPathAsAbsentMd5Target() {
        assertTrue(DsmBackupClient.isMissingMd5TargetError(408));
        assertTrue(DsmBackupClient.isMissingMd5TargetError(418));
        assertFalse(DsmBackupClient.isMissingMd5TargetError(414));
    }

    @Test
    public void encodesMd5TaskIdAsOfficialJsonStringParameter() {
        String encoded = DsmUrlBuilder.encodeParameters(Map.of(
                "taskid", DsmBackupClient.fileStationStringParameter("51CBD95028B22AED")
        ));

        assertTrue(encoded.contains("taskid=%2251CBD95028B22AED%22"));
    }
}
