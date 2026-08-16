package com.jaxson.coloros.synologynas.dsm;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class DsmApiCatalog {
    private final Map<String, DsmApiInfo> apis;

    DsmApiCatalog(Map<String, DsmApiInfo> apis) {
        this.apis = Collections.unmodifiableMap(new LinkedHashMap<>(apis));
    }

    public DsmApiInfo require(String apiName) throws DsmException {
        DsmApiInfo info = apis.get(apiName);
        if (info == null) {
            throw new DsmException("DSM 未提供必需 API: " + apiName);
        }
        return info;
    }

    public Map<String, DsmApiInfo> all() {
        return apis;
    }
}

