package com.oplus.aiunit.vision;

import com.oplus.gallery.framework.abilities.cloudsync.nas.api.model.NasBackupUploadErrorCode;

import java.util.ArrayList;

// 模拟 ColorOS 备份上传结果的封闭类型层级
public abstract class teq {
    // 模拟 ColorOS 备份上传失败结果的数据合同
    public static final class a extends teq {
        // 模拟 teq.a 的 ColorOS 上传错误码字段
        public final NasBackupUploadErrorCode a;
        // 模拟 teq.a 的上传失败消息字段
        public final String b;
        // 模拟 teq.a 的可空 gRPC 错误码字段
        public final Integer c;

        // 按 ColorOS 当前三参数合同创建上传失败结果夹具
        public a(
                NasBackupUploadErrorCode code, // ColorOS 上传错误码
                String message, // 上传失败消息
                Integer grpcCode // 可空 gRPC 错误码
        ) {
            this.a = code;
            this.b = message;
            this.c = grpcCode;
        }
    }

    // 模拟 ColorOS 备份上传成功结果的数据合同
    public static final class b extends teq {
        // 模拟 teq.b 的备份目标路径字段
        public final ycq a;
        // 模拟 teq.b 的已保存文件路径字段
        public final String b;
        // 模拟 teq.b 的实际写入字节数字段
        public final long c;
        // 模拟 teq.b 的成功通知数量字段
        public final int d;
        // 模拟 teq.b 的重复通知数量字段
        public final int e;
        // 模拟 teq.b 的通知明细列表字段
        public final ArrayList<?> f;

        // 按 ColorOS 当前六参数合同创建上传成功结果夹具
        public b(
                ycq backupPath, // 备份目标路径 DTO
                String savedPath, // 已保存的完整远端路径
                long bytesWritten, // 实际写入字节数
                int noticeSuccessCount, // 成功通知数量
                int noticeRepeatCount, // 重复通知数量
                ArrayList<?> noticeItems // 通知明细列表
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
