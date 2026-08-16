package com.oplus.aiunit.vision;

public final class ycq {
    public final String a;
    public final String b;
    public final long c;
    public final long d;
    public final boolean e;

    public ycq(
            String tmpPath,
            String displayPath,
            long quotaCurrent,
            long quotaMax,
            boolean hasAccess
    ) {
        this.a = tmpPath;
        this.b = displayPath;
        this.c = quotaCurrent;
        this.d = quotaMax;
        this.e = hasAccess;
    }
}
