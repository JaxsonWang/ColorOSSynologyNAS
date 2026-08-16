package com.oplus.aiunit.vision;

import java.util.List;

public final class seq {
    public final String a;
    public final String b;
    public final String c;
    public final String d;
    public final long e;
    public final Object f;
    public final String g;
    public final String h;
    public final String i;
    public final String j;
    public final List<String> k;
    public final int l;

    public seq(
            String targetDeviceUserId,
            String deviceId,
            String deviceName,
            String originalName,
            long fileSize,
            Object inputStreamProvider,
            String fileHash,
            String fileCreateTime,
            List<String> deviceAlbumNames
    ) {
        this.a = targetDeviceUserId;
        this.b = deviceId;
        this.c = deviceName;
        this.d = originalName;
        this.e = fileSize;
        this.f = inputStreamProvider;
        this.g = "";
        this.h = fileHash;
        this.i = fileCreateTime;
        this.j = "";
        this.k = deviceAlbumNames;
        this.l = 65_536;
    }
}
