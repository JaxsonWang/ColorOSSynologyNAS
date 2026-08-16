package com.oplus.aiunit.vision;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public interface dpk {
    // 表达 ColorOS 协程查询目标设备备份能力的接口合同
    default Object d(
            String deviceUserId, // 目标 NAS 设备标识
            Object continuation // Kotlin 协程续体测试占位
    ) {
        return -1;
    }

    // 表达 ColorOS 同步查询已备份 hash 的接口合同
    default yjq i(
            String deviceUserId, // 目标 NAS 设备标识
            ArrayList<String> hashes // 待确认的照片内容 hash
    ) {
        return new yjq.a(-1, "unsupported");
    }

    // 表达 ColorOS 协程上传单张备份照片的接口合同
    default Object k(
            seq request, // ColorOS 备份上传请求 DTO
            Object continuation // Kotlin 协程续体测试占位
    ) {
        return r(request);
    }

    // 表达 ColorOS 同步查询目标设备备份能力的接口合同
    default int m(String deviceUserId /* 目标 NAS 设备标识 */) {
        return 0;
    }

    // 表达 ColorOS 读取目标设备图库统计的接口合同
    jjq o(String deviceUserId /* 目标 NAS 设备标识 */) throws IOException;

    // 表达 ColorOS 同步上传单张备份照片的接口合同
    default teq r(seq request /* ColorOS 备份上传请求 DTO */) {
        return new teq.a(
                com.oplus.gallery.framework.abilities.cloudsync.nas.api.model
                        .NasBackupUploadErrorCode.UNKNOWN,
                "unsupported",
                null
        );
    }

    // 表达 ColorOS 协程查询已备份 hash 的接口合同
    default Object s(
            String deviceUserId, // 目标 NAS 设备标识
            List<String> hashes, // 待确认的照片内容 hash
            Object continuation // Kotlin 协程续体测试占位
    ) {
        return new yjq.a(-1, "unsupported");
    }
}
