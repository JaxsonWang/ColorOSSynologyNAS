package com.jaxson.coloros.synologynas.gallery;

import com.jaxson.coloros.synologynas.backup.BackupUploadResult;
import com.oplus.aiunit.vision.teq;
import com.oplus.aiunit.vision.yjq;
import com.oplus.gallery.framework.abilities.cloudsync.nas.api.model.NasBackupUploadErrorCode;

import org.junit.Test;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** 验证群晖备份领域结果到 ColorOS 私有 DTO 的精确映射 */
public final class ColorOsNasBackupResultMapperTest {
    /** 验证索引查询失败保留原始消息并构造 yjq 失败结果 */
    @Test
    public void mapsHashQueryIOExceptionWithoutSubstitutingItsMessage() throws Exception {
        // 创建会在索引查询边界暴露明确失败的备份服务
        RecordingBackupService service = new RecordingBackupService();
        service.queryFailure = new IOException("群晖配置字段不完整");
        // 创建使用当前测试类加载器的私有 DTO 映射器
        ColorOsNasBackupResultMapper mapper = mapper(service);

        // 映射索引查询失败后返回的 ColorOS 封闭类型
        Object result = mapper.hashResult(List.of("photo-hash"));

        assertTrue(result instanceof yjq.a);
        assertEquals(-1, ((yjq.a) result).a);
        assertEquals("群晖配置字段不完整", ((yjq.a) result).b);
    }

    /** 验证四种领域失败精确映射到 ColorOS 已有上传错误码 */
    @Test
    public void mapsEveryDomainFailureToMatchingColorOsErrorCode() throws Exception {
        // 创建可以逐次替换上传结果的备份服务
        RecordingBackupService service = new RecordingBackupService();
        // 创建直接映射每个领域错误的私有 DTO 映射器
        ColorOsNasBackupResultMapper mapper = mapper(service);

        // 逐一验证领域错误名与 ColorOS 已有枚举常量一致
        for (BackupUploadResult.ErrorCode /* 当前待映射的领域错误码 */ code
                : BackupUploadResult.ErrorCode.values()) {
            service.uploadResult = BackupUploadResult.failed(code, code.name());

            // 当前领域错误映射后的 ColorOS 上传失败 DTO
            Object result = mapper.upload(new Object());

            assertTrue(result instanceof teq.a);
            assertEquals(NasBackupUploadErrorCode.valueOf(code.name()), ((teq.a) result).a);
            assertEquals(code.name(), ((teq.a) result).b);
        }
    }

    /**
     * 创建使用当前测试类加载器的备份结果映射器
     *
     * @param service 提供可配置查询和上传结果的测试服务
     * @return 可解析当前 ColorOS 夹具类型的映射器
     */
    private static ColorOsNasBackupResultMapper mapper(RecordingBackupService service) {
        return new ColorOsNasBackupResultMapper(
                ColorOsNasBackupResultMapperTest.class.getClassLoader(),
                service
        );
    }

    /** 提供可切换索引查询和上传结果的确定性备份服务 */
    private static final class RecordingBackupService implements GalleryBackupService {
        // 保存索引查询边界需要暴露的明确失败
        private IOException queryFailure;
        // 保存下一次映射需要返回的领域上传结果
        private BackupUploadResult uploadResult;

        /** @return 测试服务始终启用备份能力 */
        @Override
        public boolean isEnabled() {
            return true;
        }

        /**
         * 暴露当前测试指定的索引查询失败
         *
         * @param hashes 本次 ColorOS 请求查询的照片内容标识
         * @return 本夹具不会返回正常索引结果
         * @throws IOException 测试预置的索引查询失败
         */
        @Override
        public Set<String> findExistingHashes(
                Collection<String> hashes // 本次 ColorOS 请求查询的照片内容标识
        ) throws IOException {
            throw queryFailure;
        }

        /**
         * 返回当前测试预置的领域上传结果
         *
         * @param colorOsRequest 本映射测试不解析的 ColorOS 请求对象
         * @return 需要映射为私有 DTO 的领域结果
         */
        @Override
        public BackupUploadResult upload(
                Object colorOsRequest // 本映射测试不解析的 ColorOS 请求对象
        ) {
            return uploadResult;
        }
    }
}
