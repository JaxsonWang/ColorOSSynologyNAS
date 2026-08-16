package com.jaxson.coloros.synologynas.dsm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

public final class DsmUrlBuilderTest {
    @Test
    public void buildsWebApiUrlBelowOptionalReverseProxyPath() {
        LinkedHashMap<String, String> parameters = new LinkedHashMap<>();
        parameters.put("api", "SYNO.FileStation.List");
        parameters.put("folder_path", "/photo/家庭 相册");

        assertEquals(
                "https://nas.example.com:5001/dsm/webapi/entry.cgi"
                        + "?api=SYNO.FileStation.List&folder_path=%2Fphoto%2F%E5%AE%B6%E5%BA%AD%20%E7%9B%B8%E5%86%8C",
                DsmUrlBuilder.build("https://nas.example.com:5001/dsm/", "entry.cgi", parameters)
        );
    }

    @Test
    public void acceptsWebApiPrefixedDiscoveryPathWithoutDuplicatingSegment() {
        assertEquals(
                "https://nas.example.com/webapi/auth.cgi?api=SYNO.API.Auth",
                DsmUrlBuilder.build(
                        "https://nas.example.com",
                        "/webapi/auth.cgi",
                        Map.of("api", "SYNO.API.Auth")
                )
        );
    }

    @Test
    public void rejectsCleartextAndEscapingApiPaths() {
        assertThrows(
                IllegalArgumentException.class,
                () -> DsmUrlBuilder.normalizeBaseUrl("http://nas.example.com:5000")
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> DsmUrlBuilder.build(
                        "https://nas.example.com",
                        "../auth.cgi",
                        Map.of()
                )
        );
    }
}

