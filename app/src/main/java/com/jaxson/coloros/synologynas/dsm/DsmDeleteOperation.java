package com.jaxson.coloros.synologynas.dsm;

import com.jaxson.coloros.synologynas.SynologyConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/** 负责 SYNO.FileStation.Delete v2 的任务启动、轮询与响应解析 */
final class DsmDeleteOperation {
    /** 删除合同固定使用 File Station Delete v2 */
    private static final int DELETE_API_VERSION = 2;
    /** 删除任务状态轮询间隔，保持原有 250 毫秒 */
    private static final long DELETE_POLL_INTERVAL_MS = 250L;
    /** 删除任务最长等待时间，保持原有 60 秒 */
    private static final long DELETE_TASK_TIMEOUT_MS = 60_000L;

    /** 当前 DSM 服务地址配置 */
    private final SynologyConfig config;

    /**
     * 创建绑定当前 DSM 配置的删除操作
     *
     * @param config 当前已发布的 DSM 配置
     */
    DsmDeleteOperation(SynologyConfig config) {
        this.config = config;
    }

    /**
     * 启动并等待远端图片删除任务完成
     *
     * @param catalog DSM 动态发现的 API 目录
     * @param sid 仅由当前业务调用持有的内存会话标识
     * @param media 待删除的远端图片
     * @throws IOException DSM 请求、业务错误或任务等待失败
     */
    void delete(DsmApiCatalog catalog, String sid, List<RemoteMedia> media) throws IOException {
        if (media.isEmpty()) {
            throw new IllegalArgumentException("待删除的群晖照片为空");
        }

        // deleteApi 是已验证支持 v2 的动态 API 描述
        DsmApiInfo deleteApi = requireDeleteApi(catalog);
        // paths 使用 DSM Delete v2 要求的 JSON 数组表达远端路径
        JSONArray paths = new JSONArray();
        for (/* 当前待删除图片 */ RemoteMedia item : media) {
            paths.put(item.remotePath());
        }
        // startParameters 完整表达删除任务启动合同
        Map<String, String> startParameters = DsmParameters.of(
                "api", deleteApi.name(),
                "version", Integer.toString(DELETE_API_VERSION),
                "method", "start",
                "path", paths.toString(),
                "accurate_progress", "true",
                "recursive", "true",
                "_sid", sid
        );
        // endpoint 是不携带查询参数的删除 API 地址
        String endpoint = DsmUrlBuilder.build(
                config.serverUrl(),
                deleteApi.path(),
                Map.of()
        );
        // startResponse 是删除任务启动响应
        JSONObject startResponse = DsmHttpTransport.executeJson(
                "POST",
                endpoint,
                DsmUrlBuilder.encodeParameters(startParameters)
        );
        // taskId 是 DSM 返回的删除任务标识
        String taskId = parseTaskId(startResponse);

        // deadlineNanos 使用单调时钟限定删除任务等待时间
        long deadlineNanos = System.nanoTime() + DELETE_TASK_TIMEOUT_MS * 1_000_000L;
        while (true) {
            // statusParameters 完整表达删除任务状态查询合同
            Map<String, String> statusParameters = DsmParameters.of(
                    "api", deleteApi.name(),
                    "version", Integer.toString(DELETE_API_VERSION),
                    "method", "status",
                    "taskid", taskId,
                    "_sid", sid
            );
            // statusUrl 是当前任务状态查询地址
            String statusUrl = DsmUrlBuilder.build(
                    config.serverUrl(),
                    deleteApi.path(),
                    statusParameters
            );
            if (parseFinished(DsmHttpTransport.executeJson("GET", statusUrl, null))) {
                return;
            }
            if (System.nanoTime() >= deadlineNanos) {
                throw new DsmException("群晖删除任务等待超时");
            }
            sleepForPoll();
        }
    }

    /**
     * 从成功的删除启动响应中提取任务标识
     *
     * @param response DSM 删除启动响应
     * @return 非空任务标识
     * @throws DsmException DSM 返回失败或缺少任务标识
     */
    static String parseTaskId(JSONObject response) throws DsmException {
        DsmHttpTransport.requireSuccess("SYNO.FileStation.Delete", response);
        // data 是删除启动响应的数据对象
        JSONObject data = response.optJSONObject("data");
        // taskId 是规范化后的删除任务标识
        String taskId = data == null ? "" : data.optString("taskid", "").trim();
        if (taskId.isEmpty()) {
            throw new DsmException("SYNO.FileStation.Delete 启动响应缺少 taskid");
        }
        return taskId;
    }

    /**
     * 从删除状态响应中读取完成标志
     *
     * @param response DSM 删除状态响应
     * @return 删除任务是否已经完成
     * @throws DsmException DSM 返回失败或缺少完成标志
     */
    static boolean parseFinished(JSONObject response) throws DsmException {
        DsmHttpTransport.requireSuccess("SYNO.FileStation.Delete", response);
        // data 是删除状态响应的数据对象
        JSONObject data = response.optJSONObject("data");
        if (data == null || !data.has("finished")) {
            throw new DsmException("SYNO.FileStation.Delete 状态响应缺少 finished");
        }
        return data.optBoolean("finished", false);
    }

    /**
     * 要求动态发现目录明确覆盖 Delete v2
     *
     * @param catalog DSM 动态发现的 API 目录
     * @return 可用于删除的 API 描述
     * @throws DsmException DSM 未提供 Delete v2
     */
    static DsmApiInfo requireApi(DsmApiCatalog catalog) throws DsmException {
        return requireDeleteApi(catalog);
    }

    /**
     * 校验删除 API 的发现版本范围
     *
     * @param catalog DSM 动态发现的 API 目录
     * @return 明确支持 v2 的删除 API
     * @throws DsmException DSM 未提供 Delete v2
     */
    private static DsmApiInfo requireDeleteApi(DsmApiCatalog catalog) throws DsmException {
        // deleteApi 来源于 SYNO.API.Info，路径与版本不在调用点硬编码
        DsmApiInfo deleteApi = catalog.require("SYNO.FileStation.Delete");
        if (deleteApi.minVersion() > DELETE_API_VERSION
                || deleteApi.maxVersion() < DELETE_API_VERSION) {
            throw new DsmException("DSM 未提供 SYNO.FileStation.Delete v2");
        }
        return deleteApi;
    }

    /**
     * 等待下一次删除任务状态查询，并把中断转换为明确失败
     *
     * @throws DsmException 当前线程在等待期间被中断
     */
    private static void sleepForPoll() throws DsmException {
        try {
            Thread.sleep(DELETE_POLL_INTERVAL_MS);
        } catch (/* 删除任务等待期间的线程中断 */ InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new DsmException("群晖删除任务被中断", error);
        }
    }
}
