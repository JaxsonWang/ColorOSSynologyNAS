package com.oplus.aiunit.vision;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public interface dpk {
    default Object d(String deviceUserId, Object continuation) {
        return -1;
    }

    default yjq i(String deviceUserId, ArrayList<String> hashes) {
        return new yjq.a(-1, "unsupported");
    }

    default Object k(seq request, Object continuation) {
        return r(request);
    }

    default int m(String deviceUserId) {
        return 0;
    }

    jjq o(String deviceUserId) throws IOException;

    default teq r(seq request) {
        return new teq.a(
                com.oplus.gallery.framework.abilities.cloudsync.nas.api.model
                        .NasBackupUploadErrorCode.UNKNOWN,
                "unsupported",
                null
        );
    }

    default Object s(String deviceUserId, List<String> hashes, Object continuation) {
        return new yjq.a(-1, "unsupported");
    }
}
