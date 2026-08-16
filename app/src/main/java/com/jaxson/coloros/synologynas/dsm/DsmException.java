package com.jaxson.coloros.synologynas.dsm;

import org.json.JSONObject;

import java.io.IOException;

public final class DsmException extends IOException {
    public DsmException(String message) {
        super(message);
    }

    public DsmException(String message, Throwable cause) {
        super(message, cause);
    }

    static DsmException fromApiResponse(String apiName, JSONObject response) {
        int code = apiErrorCode(response);
        return new DsmException(apiName + " 调用失败，DSM 错误码: " + code);
    }

    static DsmException fromFileStationListResponse(String folder, JSONObject response) {
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

    private static int apiErrorCode(JSONObject response) {
        JSONObject error = response.optJSONObject("error");
        return error == null ? -1 : error.optInt("code", -1);
    }
}
