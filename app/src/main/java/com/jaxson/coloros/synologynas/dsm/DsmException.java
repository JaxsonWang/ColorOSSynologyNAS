package com.jaxson.coloros.synologynas.dsm;

import org.json.JSONException;
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
        try {
            // code 是 DSM 明确返回的整数错误码
            int code = requireApiErrorCode(apiName, response);
            return new DsmException(apiName + " 调用失败，DSM 错误码: " + code);
        } catch (/* 失败响应缺少严格错误码 */ DsmException error) {
            return error;
        }
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
        int code;
        try {
            code = requireApiErrorCode("SYNO.FileStation.List", response);
        } catch (/* 列表失败响应缺少严格错误码 */ DsmException error) {
            return error;
        }
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
     * @param apiName 当前 DSM API 名称
     * @param response DSM JSON 响应
     * @return DSM 明确返回的整数错误码
     * @throws DsmException error.code 缺失、类型错误或超出整数范围
     */
    static int requireApiErrorCode(String apiName, JSONObject response) throws DsmException {
        try {
            // value 是尚未缩窄为 int 的协议错误码
            Object value = response.getJSONObject("error").get("code");
            if (!(value instanceof Integer) && !(value instanceof Long)) {
                throw new JSONException("error.code 不是整数");
            }
            // code 是保留范围检查的长整数错误码
            long code = ((Number) value).longValue();
            if (code < Integer.MIN_VALUE || code > Integer.MAX_VALUE) {
                throw new JSONException("error.code 超出整数范围");
            }
            return (int) code;
        } catch (/* error 或 code 字段缺失和类型错误 */ JSONException error) {
            throw new DsmException(apiName + " 失败响应格式错误", error);
        }
    }
}
