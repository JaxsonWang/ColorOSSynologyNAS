package com.oplus.aiunit.vision;

import com.oplus.gallery.framework.abilities.cloudsync.nas.api.model.NasBackupUploadErrorCode;

import java.util.ArrayList;

public abstract class teq {
    public static final class a extends teq {
        public final NasBackupUploadErrorCode a;
        public final String b;
        public final Integer c;

        public a(NasBackupUploadErrorCode code, String message, Integer grpcCode) {
            this.a = code;
            this.b = message;
            this.c = grpcCode;
        }
    }

    public static final class b extends teq {
        public final ycq a;
        public final String b;
        public final long c;
        public final int d;
        public final int e;
        public final ArrayList<?> f;

        public b(
                ycq backupPath,
                String savedPath,
                long bytesWritten,
                int noticeSuccessCount,
                int noticeRepeatCount,
                ArrayList<?> noticeItems
        ) {
            this.a = backupPath;
            this.b = savedPath;
            this.c = bytesWritten;
            this.d = noticeSuccessCount;
            this.e = noticeRepeatCount;
            this.f = noticeItems;
        }
    }
}
