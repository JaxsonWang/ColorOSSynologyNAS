package com.jaxson.coloros.synologynas.dsm;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** 将 SYNO.API.Info 响应转换为动态 API 目录 */
public final class DsmApiInfoParser {
    /** 工具类不允许实例化 */
    private DsmApiInfoParser() {
    }

    /**
     * 解析并校验 API 发现响应
     *
     * @param json SYNO.API.Info 原始 JSON 文本
     * @return 保持响应顺序的 DSM API 目录
     * @throws DsmException DSM 失败或响应格式非法
     */
    public static DsmApiCatalog parse(String json) throws DsmException {
        try {
            // root 是 API 发现响应根对象
            JSONObject root = new JSONObject(json);
            if (!root.optBoolean("success", false)) {
                throw DsmException.fromApiResponse("SYNO.API.Info", root);
            }
            // data 保存各 API 的动态路径与版本范围
            JSONObject data = root.getJSONObject("data");
            // apis 按 DSM 响应遍历顺序累积 API 描述
            Map<String, DsmApiInfo> apis = new LinkedHashMap<>();
            // names 迭代发现响应中的 API 名称
            Iterator<String> names = data.keys();
            while (names.hasNext()) {
                // name 是当前 API 名称
                String name = names.next();
                // value 是当前 API 的路径与版本对象
                JSONObject value = data.getJSONObject(name);
                apis.put(name, new DsmApiInfo(
                        name,
                        value.getString("path"),
                        value.getInt("minVersion"),
                        value.getInt("maxVersion")
                ));
            }
            return new DsmApiCatalog(apis);
        } catch (/* JSON 字段或 API 描述合同错误 */ JSONException | IllegalArgumentException error) {
            throw new DsmException("DSM API 信息响应格式错误", error);
        }
    }
}
