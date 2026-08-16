package com.oplus.aiunit.vision;

import java.util.Set;

public abstract class yjq {
    public static final class a extends yjq {
        public final int a;
        public final String b;

        public a(int errorCode, String message) {
            this.a = errorCode;
            this.b = message;
        }
    }

    public static final class b extends yjq {
        public final Set<String> a;

        public b(Set<String> existingHashes) {
            this.a = existingHashes;
        }
    }
}
