package com.jaxson.coloros.synologynas.dsm;

import org.json.JSONObject;

import java.io.IOException;

/** 表达 DSM 网络协议、业务错误码与响应格式失败 */
public final class DsmException extends IOException {
    /**
     * 创建仅含明确错误消息的 DSM 异常
     *
     * @param message 面向调用方的错误说明
     */
    public DsmException(String message) {
        super(message);
    }

    /**
     * 创建保留底层失败原因的 DSM 异常
     *
     * @param message 面向调用方的错误说明
     * @param cause 底层网络、解析或线程失败
     */
    public DsmException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 将标准 DSM API 失败响应转换为含原始错误码的异常
     *
     * @param apiName 当前 DSM API 名称
     * @param response DSM JSON 响应
     * @return 可直接抛出的 DSM 异常
     */
    static DsmException fromApiResponse(String apiName, JSONObject response) {
        // code 是 DSM 返回或缺失时的规范错误码
        int code = apiErrorCode(response);
        return new DsmException(apiName + " 调用失败，DSM 错误码: " + code);
    }

    /**
     * 将 File Station 列表错误映射为目录或权限的直接说明
     *
     * @param folder 当前读取的远端目录
     * @param response DSM JSON 响应
     * @return 可直接抛出的列表异常
     */
    static DsmException fromFileStationListResponse(String folder, JSONObject response) {
        // code 是 DSM 列表业务错误码
        int code = apiErrorCode(response);
        if (code == 408) {
            return new DsmException(
                    "远端目录不存在: " + folder
                            + "。Synology Photos 个人空间通常为 /home/Photos，"
                            + "共享空间为 /photo"
            );
        }
        if (code == 403 || code == 407) {
            return new DsmException(
                    "当前 DSM 账号无权读取远端目录: " + folder + "，DSM 错误码: " + code
            );
        }
        return fromApiResponse("SYNO.FileStation.List", response);
    }

    /**
     * 读取 DSM 标准 error.code 字段
     *
     * @param response DSM JSON 响应
     * @return 错误码；字段缺失时为 -1
     */
    private static int apiErrorCode(JSONObject response) {
        // error 是 DSM 可选错误对象
        JSONObject error = response.optJSONObject("error");
        return error == null ? -1 : error.optInt("code", -1);
    }
}
