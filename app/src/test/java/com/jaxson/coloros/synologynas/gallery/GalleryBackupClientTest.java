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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

// 验证 ColorOS 备份请求字段到群晖领域请求的严格映射
public final class GalleryBackupClientTest {
    @Test
    // 验证 seq 固定字段可以重建领域请求并延迟打开照片输入流
    public void reconstructsColorOsRequestAndInvokesInputStreamProvider() throws Exception {
        // 记录 GalleryBackupClient 实际交付的领域请求和照片字节
        RecordingRepository repository = new RecordingRepository();
        // 创建只能在仓储使用输入源时调用的照片流生成器
        InputProvider provider = new InputProvider();
        repository.expectedProvider = provider;
        // 使用记录仓储执行本次 ColorOS 请求解析
        GalleryBackupClient client = new GalleryBackupClient(repository);
        // 覆盖当前 16.50.8 全字段布局的 ColorOS 备份请求
        seq request = new seq(
                GalleryContract.DEVICE_ID,
                "phone-id",
                "PLK110",
                "IMG_1.jpg",
                3L,
                provider,
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
        assertEquals(1, provider.invocations);
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

    @Test
    // 验证 seq.d/e/h 只接受当前合同中的 String、Long、String 类型
    public void rejectsBackupRequestFieldsWithDifferentRuntimeTypes() {
        assertInvalidRequestField(
                new InvalidFieldTypesRequest(7, 3L, "hash"),
                "d"
        );
        assertInvalidRequestField(
                new InvalidFieldTypesRequest("IMG_1.jpg", 3, "hash"),
                "e"
        );
        assertInvalidRequestField(
                new InvalidFieldTypesRequest("IMG_1.jpg", 3L, 9),
                "h"
        );
    }

    @Test
    // 验证领域仓储的运行时合同失败不被误当成 ColorOS DTO 读取失败
    public void preservesRepositoryIllegalArgumentException() {
        // 创建需要穿过适配边界的唯一仓储合同失败
        IllegalArgumentException expected = new IllegalArgumentException("仓储合同失败");
        // 创建会在收到完整领域请求后暴露失败的记录仓储
        RecordingRepository repository = new RecordingRepository();
        repository.uploadFailure = expected;
        // 使用固定 DTO 解析边界执行仓储失败场景
        GalleryBackupClient client = new GalleryBackupClient(repository);
        // 构造能完整通过 ColorOS 字段解析的请求
        seq request = request(new InputProvider());

        // 捕获不应被转换为 READ_DATA_FAILED 结果的原始异常
        IllegalArgumentException actual = assertThrows(
                IllegalArgumentException.class,
                () -> client.upload(request)
        );

        assertSame(expected, actual);
        assertTrue(repository.request != null);
    }

    // 验证错误字段类型返回读取失败且不进入实际仓储
    private static void assertInvalidRequestField(
            Object request, // 包含错误运行时字段类型的请求
            String fieldName // 应在失败消息中出现的字段名
    ) {
        // 记录请求是否错误进入上传路径的仓储
        RecordingRepository repository = new RecordingRepository();
        // 使用严格字段类型解析规则执行待测请求
        GalleryBackupClient client = new GalleryBackupClient(repository);

        // 请求解析阶段返回的明确读取失败结果
        BackupUploadResult result = client.upload(request);

        assertEquals(BackupUploadResult.Status.FAILED, result.status());
        assertEquals(BackupUploadResult.ErrorCode.READ_DATA_FAILED, result.errorCode());
        assertTrue(result.message().contains(fieldName));
        assertEquals(null, repository.request);
    }

    // 模拟 ColorOS Kotlin Function0 照片输入流生成器
    private static final class InputProvider {
        // 统计领域仓储实际打开照片输入流的次数
        private int invocations;

        @SuppressWarnings("unused")
        // 模拟 ColorOS Kotlin Function0 返回照片输入流
        public ByteArrayInputStream invoke() {
            invocations++;
            return new ByteArrayInputStream(new byte[]{1, 2, 3});
        }
    }

    // 模拟缺少 seq.d 原始文件名字段的错误请求
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
        private final String g = "unrelated.jpg";
    }

    // 模拟 d/e/h 字段可分别注入错误运行时类型的请求
    private static final class InvalidFieldTypesRequest {
        // 模拟 seq.d 的任意运行时字段值
        private final Object d;
        // 模拟 seq.e 的任意运行时字段值
        private final Object e;
        // 模拟有效的 seq.f 输入流生成器字段
        private final Object f = new InputProvider();
        // 模拟 seq.h 的任意运行时字段值
        private final Object h;

        // 创建分别控制 d/e/h 运行时类型的错误请求夹具
        private InvalidFieldTypesRequest(
                Object originalName, // 写入 seq.d 的测试值
                Object fileSize, // 写入 seq.e 的测试值
                Object fileHash // 写入 seq.h 的测试值
        ) {
            this.d = originalName;
            this.e = fileSize;
            this.h = fileHash;
        }
    }

    // 记录最终备份领域请求和输入字节的仓储夹具
    private static final class RecordingRepository implements BackupRepository {
        // 保存用于确认 DTO 解析阶段未提前读取照片的生成器
        private InputProvider expectedProvider;
        // 保存仓储收到领域请求后需要直接暴露的合同失败
        private RuntimeException uploadFailure;
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
            if (uploadFailure != null) {
                throw uploadFailure;
            }
            if (expectedProvider != null) {
                assertEquals(0, expectedProvider.invocations);
            }
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

    /**
     * 创建能通过当前固定字段合同的 ColorOS 备份请求
     *
     * @param provider 只由领域仓储按需调用的照片流生成器
     * @return 字段类型与值全部有效的 seq 请求
     */
    private static seq request(InputProvider provider) {
        return new seq(
                GalleryContract.DEVICE_ID,
                "phone-id",
                "PLK110",
                "IMG_1.jpg",
                3L,
                provider,
                "0123456789abcdef0123456789abcdef"
                        + "fedcba9876543210fedcba9876543210",
                "2026-08-16 10:00",
                List.of("Camera")
        );
    }
}
