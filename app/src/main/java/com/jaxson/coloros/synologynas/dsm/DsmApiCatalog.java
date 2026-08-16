package com.jaxson.coloros.synologynas.dsm;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 保存 SYNO.API.Info 动态发现且不可变的 API 描述 */
public final class DsmApiCatalog {
    /** 按 DSM 响应顺序保存的只读 API 映射 */
    private final Map<String, DsmApiInfo> apis;

    /**
     * 创建与输入映射隔离的只读 API 目录
     *
     * @param apis 解析得到的 API 名称与描述映射
     */
    DsmApiCatalog(Map<String, DsmApiInfo> apis) {
        this.apis = Collections.unmodifiableMap(new LinkedHashMap<>(apis));
    }

    /**
     * 获取正常业务路径必需的 DSM API
     *
     * @param apiName 必需的 DSM API 名称
     * @return 动态发现的 API 描述
     * @throws DsmException DSM 没有提供该 API
     */
    public DsmApiInfo require(String apiName) throws DsmException {
        // info 是按名称命中的动态 API 描述
        DsmApiInfo info = apis.get(apiName);
        if (info == null) {
            throw new DsmException("DSM 未提供必需 API: " + apiName);
        }
        return info;
    }
}
