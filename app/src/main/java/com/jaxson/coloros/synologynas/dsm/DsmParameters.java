package com.jaxson.coloros.synologynas.dsm;

import java.util.LinkedHashMap;

/** 集中构造保持插入顺序的 DSM 请求参数，消除两个客户端的重复实现 */
final class DsmParameters {
    /** 工具类不允许实例化 */
    private DsmParameters() {
    }

    /**
     * 将相邻键值对转换为保持顺序的请求参数表
     *
     * @param pairs 按“键、值”顺序排列的字符串
     * @return 保持调用点参数顺序的映射
     */
    static LinkedHashMap<String, String> of(String... pairs) {
        // parameters 保留调用点声明顺序，确保现有 URL 与请求体编码稳定
        LinkedHashMap<String, String> parameters = new LinkedHashMap<>();
        // index 每次跨过一个键和值
        for (int index = 0; index < pairs.length; index += 2) {
            parameters.put(pairs[index], pairs[index + 1]);
        }
        return parameters;
    }
}
