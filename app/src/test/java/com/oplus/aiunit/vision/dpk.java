package com.oplus.aiunit.vision;

import com.oplus.gallery.business_lib.nas.NasPhotosAppStatus;
import com.oplus.gallery.business_lib.nas.NasPhotosAvailabilityStatus;
import com.oplus.gallery.business_lib.nas.NasProvider;
import com.oplus.gallery.business_lib.nas.ThumbnailSize;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import kotlin.coroutines.jvm.internal.ContinuationImpl;

// 模拟 ColorOS NAS Provider 的完整接口合同
public interface dpk {
    // 表达 ColorOS 读取单个远端相册的接口合同
    default b3q a(
            String deviceUserId, // 目标 NAS 设备标识
            String albumId // 目标相册稳定标识
    ) throws IOException {
        return null;
    }

    // 表达 ColorOS 查询当前 Provider 枚举槽位的接口合同
    default NasProvider b() {
        return NasProvider.FEINIU;
    }

    // 表达 ColorOS 查询 Provider 基础能力的接口合同
    default boolean c() {
        return false;
    }

    // 表达 ColorOS 协程查询目标设备备份能力的接口合同
    default Object d(
            String deviceUserId, // 目标 NAS 设备标识
            ContinuationImpl continuation // Kotlin 协程续体测试占位
    ) {
        return -1;
    }

    // 表达 ColorOS 查询 NAS Photos 可用性的接口合同
    default NasPhotosAvailabilityStatus e(String deviceUserId /* 目标 NAS 设备标识 */) {
        return NasPhotosAvailabilityStatus.UNKNOWN;
    }

    // 表达 ColorOS 读取 Provider 固定文本的接口合同
    default String f() {
        return "original";
    }

    // 表达 ColorOS 协程读取目标资源的接口合同
    default Object g(
            String deviceUserId, // 目标 NAS 设备标识
            String resourceId, // 目标资源稳定标识
            ContinuationImpl continuation // Kotlin 协程续体测试占位
    ) {
        return null;
    }

    // 表达 ColorOS 读取目标设备文本的接口合同
    default String h(String deviceUserId /* 目标 NAS 设备标识 */) {
        return null;
    }

    // 表达 ColorOS 同步查询已备份 hash 的接口合同
    default yjq i(
            String deviceUserId, // 目标 NAS 设备标识
            ArrayList<String> hashes // 待确认的照片内容 hash
    ) {
        return new yjq.a(-1, "unsupported");
    }

    // 表达 ColorOS 执行目标设备无返回操作的接口合同
    default void j(String deviceUserId /* 目标 NAS 设备标识 */) {
    }

    // 表达 ColorOS 协程上传单张备份照片的接口合同
    default Object k(
            seq request, // ColorOS 备份上传请求 DTO
            ContinuationImpl continuation // Kotlin 协程续体测试占位
    ) {
        return r(request);
    }

    // 表达 ColorOS 分页读取远端相册列表的接口合同
    default List<?> l(
            int offset, // 相册起始偏移
            int limit, // 相册最大数量
            String deviceUserId // 目标 NAS 设备标识
    ) throws IOException {
        return List.of();
    }

    // 表达 ColorOS 同步查询目标设备备份能力的接口合同
    default int m(String deviceUserId /* 目标 NAS 设备标识 */) {
        return 0;
    }

    // 表达 ColorOS 查询 NAS Photos 运行状态的接口合同
    default NasPhotosAppStatus n(String deviceUserId /* 目标 NAS 设备标识 */) {
        return NasPhotosAppStatus.UNKNOWN;
    }

    // 表达 ColorOS 读取目标设备图库统计的接口合同
    jjq o(String deviceUserId /* 目标 NAS 设备标识 */) throws IOException;

    // 表达 ColorOS 删除远端照片列表的接口合同
    default boolean p(
            String deviceUserId, // 目标 NAS 设备标识
            List<String> photoIds // 待删除的照片稳定标识
    ) {
        return false;
    }

    // 表达 ColorOS 执行目标设备第二个无返回操作的接口合同
    default void q(String deviceUserId /* 目标 NAS 设备标识 */) {
    }

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
            ContinuationImpl continuation // Kotlin 协程续体测试占位
    ) {
        return new yjq.a(-1, "unsupported");
    }

    // 表达 ColorOS 分页读取指定相册照片的接口合同
    default List<?> t(
            int offset, // 照片起始偏移
            int limit, // 照片最大数量
            String deviceUserId, // 目标 NAS 设备标识
            String albumId // 目标相册稳定标识
    ) throws IOException {
        return List.of();
    }

    // 表达 ColorOS 执行目标设备第三个无返回操作的接口合同
    default void u(String deviceUserId /* 目标 NAS 设备标识 */) {
    }

    // 表达 ColorOS 协程读取视频字节区间的接口合同
    default Object v(
            String deviceUserId, // 目标 NAS 设备标识
            String videoId, // 目标视频稳定标识
            long startByte, // 字节区间起点
            long endByte, // 字节区间终点
            ContinuationImpl continuation // Kotlin 协程续体测试占位
    ) {
        return null;
    }

    // 表达 ColorOS 流式下载原图并返回下载句柄的接口合同
    default z8g w(
            String deviceUserId, // 目标 NAS 设备标识
            String photoId, // 目标照片稳定标识
            uhq callback // 接收原图分块和完成标记的回调
    ) {
        return null;
    }

    // 表达 ColorOS 同步读取照片缩略图的接口合同
    default byte[] x(
            String deviceUserId, // 目标 NAS 设备标识
            String photoId, // 目标照片稳定标识
            ThumbnailSize size // ColorOS 请求的缩略图尺寸
    ) {
        return new byte[0];
    }
}
