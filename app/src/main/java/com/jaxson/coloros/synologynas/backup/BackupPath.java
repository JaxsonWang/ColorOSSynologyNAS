package com.jaxson.coloros.synologynas.backup;

public final class BackupPath {
    private final String folder;
    private final String fileName;

    public BackupPath(String folder, String fileName) {
        this.folder = folder;
        this.fileName = fileName;
    }

    public String folder() {
        return folder;
    }

    public String fileName() {
        return fileName;
    }

    public String remotePath() {
        return folder + "/" + fileName;
    }
}
