package com.jaxson.coloros.synologynas.dsm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 验证 File Station List 分页、目录项字段与图片 MIME 严格合同 */
public final class DsmMediaListingResponseTest {
    /** 验证列表分页参数固定使用 v2 且完整携带字段合同 */
    @Test
    public void buildsExactListVersionTwoParameters() {
        // parameters 是最大版本无关的固定 v2 列表请求参数
        Map<String, String> parameters = DsmMediaListing.requestParameters(
                "SYNO.FileStation.List",
                "SID_VALUE",
                "/home/Photos",
                2_000,
                1_000
        );

        assertEquals("SYNO.FileStation.List", parameters.get("api"));
        assertEquals("2", parameters.get("version"));
        assertEquals("list", parameters.get("method"));
        assertEquals("/home/Photos", parameters.get("folder_path"));
        assertEquals("2000", parameters.get("offset"));
        assertEquals("1000", parameters.get("limit"));
        assertEquals("name", parameters.get("sort_by"));
        assertEquals("asc", parameters.get("sort_direction"));
        assertEquals("[\"size\",\"time\"]", parameters.get("additional"));
        assertEquals("SID_VALUE", parameters.get("_sid"));
        assertEquals(10, parameters.size());
    }

    /** 验证目录、图片元数据和唯一 MIME 映射能够从严格响应生成 */
    @Test
    public void parsesStrictDirectoryAndImageEntries() throws Exception {
        // folders 接收分页中的远端子目录
        ArrayDeque<String> folders = new ArrayDeque<>();
        // media 接收分页中的受支持图片
        List<RemoteMedia> media = new ArrayList<>();
        // data 是包含目录和大写扩展名图片的严格列表分页
        JSONObject data = pageData(
                """
                {
                  "name":"Album",
                  "path":"/home/Photos/Album",
                  "isdir":true,
                  "additional":{"size":0,"time":{"mtime":1700000000}}
                },
                {
                  "name":"IMG_1.JPEG",
                  "path":"/home/Photos/IMG_1.JPEG",
                  "isdir":false,
                  "additional":{"size":2048,"time":{"mtime":1700000001}}
                }
                """,
                0,
                3
        );

        // finished 表示当前两项尚未到达 total 指定的目录末尾
        boolean finished = DsmMediaListing.parseFolderPage(
                "SYNO.FileStation.List",
                data,
                0,
                folders,
                media
        ).finished(0);

        assertFalse(finished);
        assertEquals(List.of("/home/Photos/Album"), new ArrayList<>(folders));
        assertEquals(1, media.size());
        assertEquals("/home/Photos/IMG_1.JPEG", media.get(0).remotePath());
        assertEquals(2_048L, media.get(0).size());
        assertEquals(1_700_000_001L, media.get(0).modifiedSeconds());
        assertEquals("image/jpeg", media.get(0).mimeType());
    }

    /** 验证分页完成判断使用传入 offset 和 DSM 明确 total */
    @Test
    public void completesPaginationAtReportedTotal() throws Exception {
        // folders 接收分页中的远端子目录
        ArrayDeque<String> folders = new ArrayDeque<>();
        // media 接收分页中的受支持图片
        List<RemoteMedia> media = new ArrayList<>();
        // data 是总计三项中的最后两项
        JSONObject data = pageData(
                """
                {
                  "name":"IMG_2.png",
                  "path":"/home/Photos/IMG_2.png",
                  "isdir":false,
                  "additional":{"size":10,"time":{"mtime":20}}
                },
                {
                  "name":"notes.txt",
                  "path":"/home/Photos/notes.txt",
                  "isdir":false,
                  "additional":{"size":30,"time":{"mtime":40}}
                }
                """,
                1,
                3
        );

        assertTrue(DsmMediaListing.parseFolderPage(
                "SYNO.FileStation.List",
                data,
                1,
                folders,
                media
        ).finished(1));
        assertEquals(1, media.size());
        assertEquals("image/png", media.get(0).mimeType());
    }

    /** 验证所有必需列表字段缺失时都明确报告协议格式错误 */
    @Test
    public void rejectsMissingRequiredListFields() {
        // malformedPages 逐一缺少 files、offset、total 或目录项必需字段
        String[] malformedPages = {
                "{\"offset\":0,\"total\":0}",
                "{\"files\":[],\"total\":0}",
                "{\"files\":[],\"offset\":0}",
                pageJson("{\"name\":\"a.jpg\",\"isdir\":false,\"additional\":"
                        + "{\"size\":1,\"time\":{\"mtime\":2}}}", 1),
                pageJson("{\"path\":\"/photo/a.jpg\",\"isdir\":false,\"additional\":"
                        + "{\"size\":1,\"time\":{\"mtime\":2}}}", 1),
                pageJson("{\"name\":\"a.jpg\",\"path\":\"/photo/a.jpg\",\"additional\":"
                        + "{\"size\":1,\"time\":{\"mtime\":2}}}", 1),
                pageJson("{\"name\":\"a.jpg\",\"path\":\"/photo/a.jpg\",\"isdir\":false}", 1),
                pageJson("{\"name\":\"a.jpg\",\"path\":\"/photo/a.jpg\",\"isdir\":false,\"additional\":"
                        + "{\"time\":{\"mtime\":2}}}", 1),
                pageJson("{\"name\":\"a.jpg\",\"path\":\"/photo/a.jpg\",\"isdir\":false,\"additional\":"
                        + "{\"size\":1}}", 1),
                pageJson("{\"name\":\"a.jpg\",\"path\":\"/photo/a.jpg\",\"isdir\":false,\"additional\":"
                        + "{\"size\":1,\"time\":{}}}", 1)
        };

        for (/* 当前待拒绝的字段缺失分页 */ String json : malformedPages) {
            assertMalformedPage(json);
        }
    }

    /** 验证所有必需列表字段的错误类型都不会被默认值掩盖 */
    @Test
    public void rejectsWrongRequiredListFieldTypes() {
        // malformedPages 逐一给出 files、offset、total 或目录项字段的错误类型
        String[] malformedPages = {
                "{\"files\":{},\"offset\":0,\"total\":0}",
                "{\"files\":[],\"offset\":\"0\",\"total\":0}",
                "{\"files\":[],\"offset\":0,\"total\":\"0\"}",
                pageJson("{\"name\":\"a.jpg\",\"path\":7,\"isdir\":false,\"additional\":"
                        + "{\"size\":1,\"time\":{\"mtime\":2}}}", 1),
                pageJson("{\"name\":7,\"path\":\"/photo/a.jpg\",\"isdir\":false,\"additional\":"
                        + "{\"size\":1,\"time\":{\"mtime\":2}}}", 1),
                pageJson("{\"name\":\"a.jpg\",\"path\":\"/photo/a.jpg\",\"isdir\":\"false\",\"additional\":"
                        + "{\"size\":1,\"time\":{\"mtime\":2}}}", 1),
                pageJson("{\"name\":\"a.jpg\",\"path\":\"/photo/a.jpg\",\"isdir\":false,\"additional\":[]}", 1),
                pageJson("{\"name\":\"a.jpg\",\"path\":\"/photo/a.jpg\",\"isdir\":false,\"additional\":"
                        + "{\"size\":\"1\",\"time\":{\"mtime\":2}}}", 1),
                pageJson("{\"name\":\"a.jpg\",\"path\":\"/photo/a.jpg\",\"isdir\":false,\"additional\":"
                        + "{\"size\":1,\"time\":[]}}", 1),
                pageJson("{\"name\":\"a.jpg\",\"path\":\"/photo/a.jpg\",\"isdir\":false,\"additional\":"
                        + "{\"size\":1,\"time\":{\"mtime\":\"2\"}}}", 1)
        };

        for (/* 当前待拒绝的字段类型错误分页 */ String json : malformedPages) {
            assertMalformedPage(json);
        }
    }

    /** 验证空分页与尚有剩余条目的 total 矛盾时不会静默截断 */
    @Test
    public void rejectsEmptyPageBeforeReportedTotal() {
        assertMalformedPage("{\"files\":[],\"offset\":0,\"total\":1}");
    }

    /** 验证 DSM 返回的分页起点必须与本次请求 offset 完全一致 */
    @Test
    public void rejectsMismatchedResponseOffset() throws Exception {
        // data 是请求第二页却声明仍为首页的矛盾响应
        JSONObject data = new JSONObject("{\"files\":[],\"offset\":0,\"total\":1}");

        // error 是响应分页起点不一致得到的明确协议异常
        DsmException error = assertThrows(
                DsmException.class,
                () -> DsmMediaListing.parseFolderPage(
                        "SYNO.FileStation.List",
                        data,
                        1,
                        new ArrayDeque<>(),
                        new ArrayList<>()
                )
        );
        assertEquals("SYNO.FileStation.List 响应格式错误", error.getMessage());
    }

    /**
     * 构建含指定目录项和总数的列表 data 对象
     *
     * @param entriesJson 逗号分隔的目录项 JSON
     * @param offset DSM 返回的当前分页起点
     * @param total DSM 报告的目录条目总数
     * @return File Station List data 对象
     */
    private static JSONObject pageData(String entriesJson, int offset, int total) throws Exception {
        return new JSONObject(pageJson(entriesJson, offset, total));
    }

    /**
     * 构建含指定目录项和总数的列表 data JSON
     *
     * @param entriesJson 逗号分隔的目录项 JSON
     * @param total DSM 报告的目录条目总数
     * @return File Station List data JSON
     */
    private static String pageJson(String entriesJson, int total) {
        return pageJson(entriesJson, 0, total);
    }

    /**
     * 构建含指定目录项、分页起点和总数的列表 data JSON
     *
     * @param entriesJson 逗号分隔的目录项 JSON
     * @param offset DSM 返回的当前分页起点
     * @param total DSM 报告的目录条目总数
     * @return File Station List data JSON
     */
    private static String pageJson(String entriesJson, int offset, int total) {
        return "{\"files\":[" + entriesJson + "],\"offset\":" + offset
                + ",\"total\":" + total + "}";
    }

    /**
     * 断言列表分页因协议格式错误而失败
     *
     * @param json 待解析的列表 data JSON
     */
    private static void assertMalformedPage(String json) {
        // error 是严格列表合同拒绝当前响应得到的异常
        DsmException error = assertThrows(
                DsmException.class,
                () -> DsmMediaListing.parseFolderPage(
                        "SYNO.FileStation.List",
                        new JSONObject(json),
                        0,
                        new ArrayDeque<>(),
                        new ArrayList<>()
                )
        );
        assertEquals("SYNO.FileStation.List 响应格式错误", error.getMessage());
    }
}
