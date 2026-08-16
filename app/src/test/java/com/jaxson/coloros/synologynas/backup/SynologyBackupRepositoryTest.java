package com.jaxson.coloros.synologynas.backup;

import com.jaxson.coloros.synologynas.SynologyConfig;
import com.jaxson.coloros.synologynas.SynologyConfigSource;
import com.jaxson.coloros.synologynas.dsm.DsmApiCatalog;
import com.jaxson.coloros.synologynas.dsm.DsmApiInfoParser;
import com.jaxson.coloros.synologynas.dsm.DsmBackupGateway;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class SynologyBackupRepositoryTest {
    // 提供符合 ColorOS 当前 SHA-256 合约的固定原生哈希
    private static final String HASH = "0123456789abcdef0123456789abcdef"
            + "fedcba9876543210fedcba9876543210";
    // 提供固定照片内容对应的 DSM MD5 校验值
    private static final String CONTENT_MD5 = "5289df737df57326fcdd22597afb1fac";

    /** 验证 DSM 上传成功后才写入 ColorOS 原生哈希索引 */
    @Test
    public void uploadsToDsmAndRecordsHashOnlyAfterSuccess() {
        // 创建可观察哈希写入结果的内存索引
        InMemoryHashStore hashStore = new InMemoryHashStore();
        // 创建默认上传成功的 DSM 网关
        FakeGateway gateway = new FakeGateway();
        // 创建使用上述测试边界的备份仓储
        SynologyBackupRepository repository = repository(hashStore, gateway);

        // 执行单张照片的完整备份链路
        BackupUploadResult result = repository.upload(request());

        assertEquals(BackupUploadResult.Status.SUCCESS, result.status());
        assertEquals(3L, result.bytesWritten());
        assertEquals(
                "/home/Photos/ColorOS Backup/IMG_1.jpg",
                result.savedPath()
        );
        assertEquals(result.savedPath(), gateway.uploadedPath);
        assertTrue(hashStore.hashes.contains(HASH));
    }

    /** 验证本地哈希索引命中时不会建立 DSM 会话或上传 */
    @Test
    public void skipsNetworkWhenHashIndexAlreadyContainsPhoto() {
        // 创建并预置当前照片哈希的内存索引
        InMemoryHashStore hashStore = new InMemoryHashStore();
        hashStore.hashes.add(HASH);
        // 创建用于确认没有网络调用的 DSM 网关
        FakeGateway gateway = new FakeGateway();
        // 创建使用预置索引和可观察网关的备份仓储
        SynologyBackupRepository repository = repository(hashStore, gateway);

        // 执行已经存在于索引中的照片备份请求
        BackupUploadResult result = repository.upload(request());

        assertEquals(BackupUploadResult.Status.ALREADY_EXISTS, result.status());
        assertEquals(0, gateway.discoverInvocations);
        assertNull(gateway.uploadedPath);
    }

    /** 验证首选路径被不同内容占用时使用稳定哈希后缀路径 */
    @Test
    public void usesHashSuffixWhenOriginalNameContainsDifferentRemotePhoto() {
        // 创建初始为空的内存哈希索引
        InMemoryHashStore hashStore = new InMemoryHashStore();
        // 创建首选路径返回不同内容 MD5 的 DSM 网关
        FakeGateway gateway = new FakeGateway();
        gateway.primaryHash = Optional.of("ffffffffffffffffffffffffffffffff");
        // 创建使用冲突响应网关的备份仓储
        SynologyBackupRepository repository = repository(hashStore, gateway);

        // 执行会发生首选路径内容冲突的备份请求
        BackupUploadResult result = repository.upload(request());

        assertEquals(BackupUploadResult.Status.SUCCESS, result.status());
        assertEquals(
                "/home/Photos/ColorOS Backup/"
                        + "IMG_1_0123456789abcdef0123456789abcdef"
                        + "fedcba9876543210fedcba9876543210.jpg",
                gateway.uploadedPath
        );
    }

    /** 验证远端同名同内容照片无需上传但仍记录 ColorOS 原生哈希 */
    @Test
    public void recordsNativeHashForVerifiedRemoteDuplicateWithoutUploading() {
        // 创建用于确认远端验证成功后落索引的内存存储
        InMemoryHashStore hashStore = new InMemoryHashStore();
        // 创建首选路径返回本机内容 MD5 的 DSM 网关
        FakeGateway gateway = new FakeGateway();
        gateway.primaryHash = Optional.of(CONTENT_MD5);
        // 创建使用远端重复内容响应的备份仓储
        SynologyBackupRepository repository = repository(hashStore, gateway);

        // 执行会由远端 MD5 确认内容已存在的备份请求
        BackupUploadResult result = repository.upload(request());

        assertEquals(BackupUploadResult.Status.ALREADY_EXISTS, result.status());
        assertNull(gateway.uploadedPath);
        assertTrue(hashStore.hashes.contains(HASH));
    }

    /** 验证 DSM 上传失败时不会污染本地哈希索引 */
    @Test
    public void doesNotRecordHashWhenDsmUploadFails() {
        // 创建用于确认失败后没有写入的内存哈希索引
        InMemoryHashStore hashStore = new InMemoryHashStore();
        // 创建会在上传阶段返回明确 IOException 的 DSM 网关
        FakeGateway gateway = new FakeGateway();
        gateway.uploadFailure = new IOException("DSM 416");
        // 创建使用失败网关的备份仓储
        SynologyBackupRepository repository = repository(hashStore, gateway);

        // 执行预期上传失败的照片备份请求
        BackupUploadResult result = repository.upload(request());

        assertEquals(BackupUploadResult.Status.FAILED, result.status());
        assertEquals(BackupUploadResult.ErrorCode.UPLOAD_FAILED, result.errorCode());
        assertEquals("DSM 416", result.message());
        assertFalse(hashStore.hashes.contains(HASH));
    }

    /** 验证关闭照片备份时直接失败且不会连接 DSM */
    @Test
    public void disabledBackupDoesNotConnectOrUpload() {
        // 创建用于确认关闭备份后没有写入的内存索引
        InMemoryHashStore hashStore = new InMemoryHashStore();
        // 创建用于确认关闭备份后没有网络调用的 DSM 网关
        FakeGateway gateway = new FakeGateway();
        // 创建显式关闭照片备份的仓储
        SynologyBackupRepository repository = repository(
                hashStore,
                gateway,
                config(false, "ColorOS Backup")
        );

        // 执行备份开关关闭时的照片请求
        BackupUploadResult result = repository.upload(request());

        assertFalse(repository.isEnabled());
        assertEquals(BackupUploadResult.Status.FAILED, result.status());
        assertEquals("群晖照片备份已关闭", result.message());
        assertEquals(0, gateway.discoverInvocations);
        assertNull(gateway.uploadedPath);
    }

    /**
     * 使用默认启用配置创建测试仓储
     *
     * @param hashStore 可观察的内存哈希索引
     * @param gateway 可配置响应的 DSM 测试网关
     * @return 使用固定启用配置的备份仓储
     */
    private static SynologyBackupRepository repository(
            InMemoryHashStore hashStore,
            FakeGateway gateway
    ) {
        return repository(hashStore, gateway, config(true, "ColorOS Backup"));
    }

    /**
     * 创建备份开关和目录可变的固定群晖配置
     *
     * @param backupEnabled 照片备份开关
     * @param backupFolder 单层照片备份目录
     * @return 固定连接身份和图片根目录的群晖配置
     */
    private static SynologyConfig config(boolean backupEnabled, String backupFolder) {
        return new SynologyConfig(
                "https://nas.example.test",
                "user",
                "pass",
                "",
                "/home/Photos",
                backupEnabled,
                backupFolder
        );
    }

    /**
     * 使用显式配置和测试边界创建备份仓储
     *
     * @param hashStore 可观察的内存哈希索引
     * @param gateway 可配置响应的 DSM 测试网关
     * @param config 本次测试使用的群晖配置
     * @return 使用固定配置源和网关工厂的备份仓储
     */
    private static SynologyBackupRepository repository(
            InMemoryHashStore hashStore,
            FakeGateway gateway,
            SynologyConfig config
    ) {
        // 创建始终返回本次固定配置的配置源
        SynologyConfigSource configSource = new SynologyConfigSource() {
            /** @return 固定配置源始终已经配置 */
            @Override
            public boolean hasConfig() {
                return true;
            }

            /** @return 本次测试指定的群晖配置 */
            @Override
            public SynologyConfig load() {
                return config;
            }
        };
        return new SynologyBackupRepository(
                configSource,
                hashStore,
                /* 测试只验证固定网关，不按配置创建客户端 */ ignored -> gateway
        );
    }

    /** @return 使用固定照片内容、名称和原生哈希的备份请求 */
    private static BackupUploadRequest request() {
        return new BackupUploadRequest(
                "IMG_1.jpg",
                3L,
                () -> new ByteArrayInputStream(new byte[]{1, 2, 3}),
                HASH
        );
    }

    private static final class InMemoryHashStore implements BackupHashStore {
        // 保存测试过程中确认已经落库的 ColorOS 原生哈希
        private final Set<String> hashes = new LinkedHashSet<>();

        /**
         * 返回候选哈希与内存索引的交集
         *
         * @param config 本测试不参与隔离计算的固定配置
         * @param candidates 待查询的 ColorOS 原生哈希集合
         * @return 保持候选顺序的已存在哈希集合
         */
        @Override
        public Set<String> findExisting(
                SynologyConfig config,
                Collection<String> candidates
        ) {
            // 从候选集合开始构造不会修改调用方数据的交集
            Set<String> result = new LinkedHashSet<>(candidates);
            result.retainAll(hashes);
            return result;
        }

        /**
         * 把远端已确认成功的哈希写入内存索引
         *
         * @param config 本测试不参与隔离计算的固定配置
         * @param hash 已由仓储确认成功的 ColorOS 原生哈希
         */
        @Override
        public void recordUploaded(SynologyConfig config, String hash) {
            hashes.add(hash);
        }
    }

    private static final class FakeGateway implements DsmBackupGateway {
        // 保存首选 DSM 路径的可配置 MD5 查询结果
        private Optional<String> primaryHash = Optional.empty();
        // 保存稳定冲突 DSM 路径的可配置 MD5 查询结果
        private Optional<String> collisionHash = Optional.empty();
        // 保存上传阶段需要抛出的可配置失败
        private IOException uploadFailure;
        // 统计 DSM API 发现调用次数以证明是否建立网络会话
        private int discoverInvocations;
        // 保存测试网关最后实际接收的 DSM 上传路径
        private String uploadedPath;

        /**
         * 返回包含认证、上传和 MD5 能力的固定 DSM API 目录
         *
         * @return 可供仓储完成测试链路的 DSM API 目录
         */
        @Override
        public DsmApiCatalog discoverApis() throws IOException {
            discoverInvocations++;
            return DsmApiInfoParser.parse("""
                    {
                      "success": true,
                      "data": {
                        "SYNO.API.Auth": {
                          "path": "auth.cgi", "minVersion": 1, "maxVersion": 7
                        },
                        "SYNO.FileStation.Upload": {
                          "path": "entry.cgi", "minVersion": 2, "maxVersion": 3
                        },
                        "SYNO.FileStation.MD5": {
                          "path": "entry.cgi", "minVersion": 2, "maxVersion": 2
                        }
                      }
                    }
                    """);
        }

        /**
         * 返回仅供当前测试进程使用的固定 DSM 会话标识
         *
         * @param catalog 已发现的固定 DSM API 目录
         * @return 固定测试会话标识
         */
        @Override
        public String login(DsmApiCatalog catalog) {
            return "sid";
        }

        /**
         * 按首选或冲突路径返回测试预设的远端内容 MD5
         *
         * @param catalog 已发现的固定 DSM API 目录
         * @param sid 固定测试会话标识
         * @param remotePath 仓储请求查询的 DSM 完整路径
         * @return 对应路径预设的可选 MD5
         */
        @Override
        public Optional<String> md5(
                DsmApiCatalog catalog,
                String sid,
                String remotePath
        ) {
            return remotePath.contains(HASH) ? collisionHash : primaryHash;
        }

        /**
         * 记录目标路径并验证上传字节数，或抛出测试预设失败
         *
         * @param catalog 已发现的固定 DSM API 目录
         * @param sid 固定测试会话标识
         * @param path 仓储选择的 DSM 上传路径
         * @param fileSize ColorOS 报告的照片字节数
         * @param input 仓储打开的照片输入流
         * @return 模拟 DSM 确认写入的字节数
         * @throws IOException 测试预设上传失败或输入流读取失败
         */
        @Override
        public long upload(
                DsmApiCatalog catalog,
                String sid,
                BackupPath path,
                long fileSize,
                InputStream input
        ) throws IOException {
            if (uploadFailure != null) {
                throw uploadFailure;
            }
            uploadedPath = path.remotePath();
            assertEquals(fileSize, input.readAllBytes().length);
            return fileSize;
        }
    }
}
