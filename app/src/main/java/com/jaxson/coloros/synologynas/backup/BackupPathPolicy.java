package com.jaxson.coloros.synologynas.backup;

import com.jaxson.coloros.synologynas.SynologyConfig;

public final class BackupPathPolicy {
    private BackupPathPolicy() {
    }

    public static BackupPath primary(SynologyConfig config, BackupUploadRequest request) {
        return new BackupPath(folder(config), fileName(request.originalName()));
    }

    public static BackupPath collision(SynologyConfig config, BackupUploadRequest request) {
        String original = fileName(request.originalName());
        int dot = original.lastIndexOf('.');
        String base = dot <= 0 ? original : original.substring(0, dot);
        String extension = dot <= 0 ? "" : original.substring(dot);
        return new BackupPath(
                folder(config),
                base + "_" + request.stableHashSuffix() + extension
        );
    }

    private static String folder(SynologyConfig config) {
        String root = config.remoteRoot();
        while (root.endsWith("/")) {
            root = root.substring(0, root.length() - 1);
        }
        return root + "/" + config.backupFolder();
    }

    static String safeSegment(String value, String fallback) {
        String normalized = value == null ? "" : value.trim();
        normalized = normalized.replaceAll("[\\p{Cntrl}/\\\\:*?\"<>|]", "_");
        normalized = normalized.replaceAll("\\s+", " ");
        normalized = normalized.replaceAll("[. ]+$", "");
        if (normalized.isEmpty() || ".".equals(normalized) || "..".equals(normalized)) {
            return fallback;
        }
        return normalized;
    }

    private static String fileName(String originalName) {
        return safeSegment(originalName, "photo");
    }
}
