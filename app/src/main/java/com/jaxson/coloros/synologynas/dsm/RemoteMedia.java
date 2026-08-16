package com.jaxson.coloros.synologynas.dsm;

public final class RemoteMedia {
    private final String remotePath;
    private final String name;
    private final long size;
    private final long modifiedSeconds;
    private final String mimeType;

    public RemoteMedia(
            String remotePath,
            String name,
            long size,
            long modifiedSeconds,
            String mimeType
    ) {
        this.remotePath = remotePath;
        this.name = name;
        this.size = size;
        this.modifiedSeconds = modifiedSeconds;
        this.mimeType = mimeType;
    }

    public String remotePath() {
        return remotePath;
    }

    public String name() {
        return name;
    }

    public long size() {
        return size;
    }

    public long modifiedSeconds() {
        return modifiedSeconds;
    }

    public String mimeType() {
        return mimeType;
    }
}

