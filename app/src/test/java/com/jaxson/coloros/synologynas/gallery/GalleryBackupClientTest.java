package com.jaxson.coloros.synologynas.gallery;

import com.jaxson.coloros.synologynas.backup.BackupPath;
import com.jaxson.coloros.synologynas.backup.BackupRepository;
import com.jaxson.coloros.synologynas.backup.BackupUploadRequest;
import com.jaxson.coloros.synologynas.backup.BackupUploadResult;
import com.oplus.aiunit.vision.seq;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public final class GalleryBackupClientTest {
    @Test
    // 验证 seq 固定字段可以重建领域请求并延迟打开照片输入流
    public void reconstructsColorOsRequestAndInvokesInputStreamProvider() throws Exception {
        // 记录 GalleryBackupClient 实际交付的领域请求和照片字节
        RecordingRepository repository = new RecordingRepository();
        // 使用记录仓储执行本次 ColorOS 请求解析
        GalleryBackupClient client = new GalleryBackupClient(repository);
        // 覆盖当前 16.50.8 全字段布局的 ColorOS 备份请求
        seq request = new seq(
                GalleryContract.DEVICE_ID,
                "phone-id",
                "PLK110",
                "IMG_1.jpg",
                3L,
                new InputProvider(),
                "0123456789abcdef0123456789abcdef"
                        + "fedcba9876543210fedcba9876543210",
                "2026-08-16 10:00",
                List.of("Camera")
        );

        // 客户端解析并交付仓储后的实际上传结果
        BackupUploadResult result = client.upload(request);

        assertEquals(BackupUploadResult.Status.SUCCESS, result.status());
        assertEquals("IMG_1.jpg", repository.request.originalName());
        assertEquals(3L, repository.request.fileSize());
        assertEquals(
                "0123456789abcdef0123456789abcdef"
                        + "fedcba9876543210fedcba9876543210",
                repository.request.fileHash()
        );
        assertArrayEquals(new byte[]{1, 2, 3}, repository.bytes);
    }

    @Test
    // 验证原始文件名缺失时不再读取未约定的 seq.g 字段兜底
    public void rejectsMissingOriginalNameWithoutReadingUnrelatedField() {
        // 记录失败请求是否错误进入实际备份仓储
        RecordingRepository repository = new RecordingRepository();
        // 使用固定字段解析规则执行缺失文件名场景
        GalleryBackupClient client = new GalleryBackupClient(repository);
        // 构造 d 为空但无关 g 字段有值的请求夹具
        MissingNameRequest request = new MissingNameRequest();

        // 请求解析阶段返回的明确读取失败结果
        BackupUploadResult result = client.upload(request);

        assertEquals(BackupUploadResult.Status.FAILED, result.status());
        assertEquals(BackupUploadResult.ErrorCode.READ_DATA_FAILED, result.errorCode());
        assertEquals(null, repository.request);
    }

    private static final class InputProvider {
        @SuppressWarnings("unused")
        // 模拟 ColorOS Kotlin Function0 返回照片输入流
        public ByteArrayInputStream invoke() {
            return new ByteArrayInputStream(new byte[]{1, 2, 3});
        }
    }

    private static final class MissingNameRequest {
        // 模拟缺失的 seq.d 原始文件名字段
        private final String d = "";
        // 模拟有效的 seq.e 照片字节数字段
        private final long e = 3L;
        // 模拟有效的 seq.f 输入流生成器字段
        private final Object f = new InputProvider();
        // 模拟有效的 seq.h 照片内容 hash 字段
        private final String h = "0123456789abcdef0123456789abcdef"
                + "fedcba9876543210fedcba9876543210";
        // 模拟不得作为文件名来源的无关 seq.g 字段
        @SuppressWarnings("unused")
        private final String g = "fallback.jpg";
    }

    private static final class RecordingRepository implements BackupRepository {
        // 保存客户端最终交付给领域仓储的备份请求
        private BackupUploadRequest request;
        // 保存从请求输入源实际读取的照片字节
        private byte[] bytes;

        @Override
        // 为测试保持备份能力开启
        public boolean isEnabled() {
            return true;
        }

        @Override
        // 本场景不预置任何已备份照片 hash
        public Set<String> findExistingHashes(
                Collection<String> hashes // 客户端查询的照片内容 hash
        ) {
            return Set.of();
        }

        @Override
        // 记录领域请求、打开输入源并返回真实成功结果
        public BackupUploadResult upload(
                BackupUploadRequest request // 客户端解析完成的领域请求
        ) {
            this.request = request;
            try (java.io.InputStream input /* 本次实际读取的照片流 */ =
                         request.inputSource().open()) {
                bytes = input.readAllBytes();
            } catch (IOException error /* 测试输入源读取异常 */) {
                throw new AssertionError(error);
            }
            return BackupUploadResult.success(
                    new BackupPath("/home/Photos/ColorOS Backup", "IMG_1.jpg"),
                    bytes.length
            );
        }
    }
}
