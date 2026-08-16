package com.jaxson.coloros.synologynas.dsm;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public final class DsmApiInfoParser {
    private DsmApiInfoParser() {
    }

    public static DsmApiCatalog parse(String json) throws DsmException {
        try {
            JSONObject root = new JSONObject(json);
            if (!root.optBoolean("success", false)) {
                throw DsmException.fromApiResponse("SYNO.API.Info", root);
            }
            JSONObject data = root.getJSONObject("data");
            Map<String, DsmApiInfo> apis = new LinkedHashMap<>();
            Iterator<String> names = data.keys();
            while (names.hasNext()) {
                String name = names.next();
                JSONObject value = data.getJSONObject(name);
                apis.put(name, new DsmApiInfo(
                        name,
                        value.getString("path"),
                        value.getInt("minVersion"),
                        value.getInt("maxVersion")
                ));
            }
            return new DsmApiCatalog(apis);
        } catch (JSONException | IllegalArgumentException error) {
            throw new DsmException("DSM API 信息响应格式错误", error);
        }
    }
}

