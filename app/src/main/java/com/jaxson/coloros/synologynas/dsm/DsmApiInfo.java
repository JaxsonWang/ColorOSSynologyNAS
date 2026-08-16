package com.jaxson.coloros.synologynas.dsm;

public final class DsmApiInfo {
    private final String name;
    private final String path;
    private final int minVersion;
    private final int maxVersion;

    public DsmApiInfo(String name, String path, int minVersion, int maxVersion) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("DSM API name is empty");
        }
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("DSM API path is empty: " + name);
        }
        if (minVersion <= 0 || maxVersion < minVersion) {
            throw new IllegalArgumentException("Invalid DSM API version range: " + name);
        }
        this.name = name;
        this.path = path;
        this.minVersion = minVersion;
        this.maxVersion = maxVersion;
    }

    public String name() {
        return name;
    }

    public String path() {
        return path;
    }

    public int minVersion() {
        return minVersion;
    }

    public int maxVersion() {
        return maxVersion;
    }
}

