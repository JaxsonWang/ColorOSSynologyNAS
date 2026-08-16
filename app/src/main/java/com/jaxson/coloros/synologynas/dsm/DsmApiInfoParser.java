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
            DsmHttpTransport.requireSuccess("SYNO.API.Info", root);
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
                        DsmHttpTransport.requiredString(value, "path"),
                        requireInteger(value, "minVersion"),
                        requireInteger(value, "maxVersion")
                ));
            }
            return new DsmApiCatalog(apis);
        } catch (/* JSON 字段或 API 描述合同错误 */ JSONException | IllegalArgumentException error) {
            throw new DsmException("DSM API 信息响应格式错误", error);
        }
    }

    /**
     * 严格读取 API 发现响应中的整数版本字段
     *
     * @param object 当前 API 描述对象
     * @param field 必需的版本字段名
     * @return DSM 明确返回的整数版本
     * @throws JSONException 字段缺失、类型错误或超出整数范围
     */
    private static int requireInteger(JSONObject object, String field) throws JSONException {
        // value 是尚未缩窄为 int 的版本字段
        Object value = object.get(field);
        if (!(value instanceof Integer) && !(value instanceof Long)) {
            throw new JSONException(field + " 不是整数");
        }
        // number 是用于检查 int 范围的长整数版本
        long number = ((Number) value).longValue();
        if (number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) {
            throw new JSONException(field + " 超出整数范围");
        }
        return (int) number;
    }
}
