package com.oplus.aiunit.vision;

import com.oplus.gallery.business_lib.nas.NasProvider;

public final class ngq {
    public final NasProvider provider;
    public final String b;
    public final String c;
    public final String d;
    public final String e;
    public final String f;
    public final int g;
    public final String h;
    public final long i;
    public final boolean j;
    public final int k;

    public ngq(
            NasProvider provider,
            String deviceUserId,
            String deviceName,
            String userName,
            String url,
            String accessToken,
            int status,
            String refreshToken,
            long expiresAt,
            boolean admin,
            int albumCount
    ) {
        this.provider = provider;
        this.b = deviceUserId;
        this.c = deviceName;
        this.d = userName;
        this.e = url;
        this.f = accessToken;
        this.g = status;
        this.h = refreshToken;
        this.i = expiresAt;
        this.j = admin;
        this.k = albumCount;
    }
}
