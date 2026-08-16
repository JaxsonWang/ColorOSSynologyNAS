package com.jaxson.coloros.synologynas.backup;

import com.jaxson.coloros.synologynas.SynologyConfig;
import com.jaxson.coloros.synologynas.SynologyConfigSource;
import com.jaxson.coloros.synologynas.dsm.DsmApiCatalog;
import com.jaxson.coloros.synologynas.dsm.DsmApiInfoParser;
import com.jaxson.coloros.synologynas.dsm.DsmBackupGateway;
import com.jaxson.coloros.synologynas.dsm.DsmRemoteFileAlreadyExistsException;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class SynologyBackupRepositoryConcurrencyTest {
    // 提供第一张同名照片的固定 ColorOS SHA-256 原生哈希
    private static final String FIRST_HASH = "0123456789abcdef0123456789abcdef"
            + "fedcba9876543210fedcba9876543210";
    // 提供第二张同名不同内容照片的固定 ColorOS SHA-256 原生哈希
    private static final String SECOND_HASH = "abcdef0123456789abcdef0123456789"
            + "0123456789abcdef0123456789abcdef";
    // 固定两个任务完成断言允许等待的最长时间
    private static final long TASK_TIMEOUT_SECONDS = 5L;
    // 固定未串行化实现可进入同一远端决策点的竞争窗口
    private static final long DECISION_WINDOW_MILLIS = 1_000L;

    /** 验证同一仓储并发处理同名不同内容时第二张照片进入稳定冲突路径 */
    @Test
    public void serializesConcurrentSameNameDecisionUploadAndHashCommit()
            throws InterruptedException, ExecutionException, TimeoutException {
        // 保存两个成功结果最终都应包含的 ColorOS 原生哈希
        Set<String> expectedHashes = Set.of(FIRST_HASH, SECOND_HASH);
        // 创建线程安全的可观察哈希索引
        ConcurrentHashStore hashStore = new ConcurrentHashStore();
        // 创建能放大未串行化路径竞争的 DSM 网关
        ConcurrentGateway gateway = new ConcurrentGateway();
        // 创建共享同一会话和路径决策边界的备份仓储
        SynologyBackupRepository repository = repository(hashStore, gateway);
        // 确认第二个工作线程已经开始调用同一仓储
        CountDownLatch secondTaskStarted = new CountDownLatch(1);
        // 使用两个工作线程同时发起同名照片备份
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            // 先提交第一张照片并让它停在首选路径 MD5 决策点
            Future<BackupUploadResult> first = executor.submit(
                    () -> repository.upload(request(FIRST_HASH, new byte[]{1, 2, 3}))
            );
            assertTrue(gateway.firstPrimaryQuery.await(TASK_TIMEOUT_SECONDS, TimeUnit.SECONDS));

            // 在第一张照片尚未完成路径决策时提交同名不同内容照片
            Future<BackupUploadResult> second = executor.submit(
                    () -> {
                        secondTaskStarted.countDown();
                        return repository.upload(request(SECOND_HASH, new byte[]{4, 5, 6}));
                    }
            );
            assertTrue(secondTaskStarted.await(TASK_TIMEOUT_SECONDS, TimeUnit.SECONDS));

            // 等待第一张照片完成首选路径上传和哈希提交
            BackupUploadResult firstResult = first.get(TASK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            // 等待第二张照片完成稳定冲突路径上传和哈希提交
            BackupUploadResult secondResult = second.get(TASK_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            assertEquals(BackupUploadResult.Status.SUCCESS, firstResult.status());
            assertEquals(BackupUploadResult.Status.SUCCESS, secondResult.status());
            assertEquals(
                    Set.of(
                            "/home/Photos/ColorOS Backup/IMG_1.jpg",
                            "/home/Photos/ColorOS Backup/IMG_1_" + SECOND_HASH + ".jpg"
                    ),
                    gateway.uploadedPaths
            );
            assertEquals(expectedHashes, hashStore.hashes);
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * 创建使用固定配置与并发测试边界的备份仓储
     *
     * @param hashStore 线程安全的可观察哈希索引
     * @param gateway 放大路径竞争并记录上传结果的 DSM 网关
     * @return 共享单个会话和事务锁的备份仓储
     */
    private static SynologyBackupRepository repository(
            ConcurrentHashStore hashStore,
            ConcurrentGateway gateway
    ) {
        // 创建始终返回同一启用配置的测试配置源
        SynologyConfigSource configSource = new SynologyConfigSource() {
            /** @return 固定测试配置源始终已经配置 */
            @Override
            public boolean hasConfig() {
                return true;
            }

            /** @return 当前并发测试使用的固定群晖配置 */
            @Override
            public SynologyConfig load() {
                return config();
            }
        };
        return new SynologyBackupRepository(
                configSource,
                hashStore,
                /* 并发测试始终复用同一个可观察 DSM 网关 */ ignored -> gateway
        );
    }

    /** @return 使用默认备份目录的固定群晖测试配置 */
    private static SynologyConfig config() {
        return new SynologyConfig(
                "https://nas.example.test",
                "user",
                "pass",
                "",
                "/home/Photos",
                true,
                "ColorOS Backup"
        );
    }

    /**
     * 创建名称固定但内容与 ColorOS 原生哈希可变的备份请求
     *
     * @param hash 当前照片的 ColorOS SHA-256 原生哈希
     * @param content 当前照片的固定测试内容
     * @return 可重复打开内容流的照片备份请求
     */
    private static BackupUploadRequest request(String hash, byte[] content) {
        return new BackupUploadRequest(
                "IMG_1.jpg",
                content.length,
                () -> new ByteArrayInputStream(content),
                hash
        );
    }

    private static final class ConcurrentHashStore implements BackupHashStore {
        // 保存并发测试中已经完成远端确认的 ColorOS 原生哈希
        private final Set<String> hashes = ConcurrentHashMap.newKeySet();

        /**
         * 返回候选哈希与线程安全内存索引的交集
         *
         * @param config 本测试使用的固定群晖配置
         * @param candidates 待查询的 ColorOS 原生哈希集合
         * @return 已存在于内存索引的哈希集合
         */
        @Override
        public Set<String> findExisting(
                SynologyConfig config,
                Collection<String> candidates
        ) {
            // 从候选哈希复制出不会修改调用方数据的结果集合
            Set<String> result = new LinkedHashSet<>(candidates);
            result.retainAll(hashes);
            return result;
        }

        /**
         * 记录已经完成 DSM 上传或内容验证的原生哈希
         *
         * @param config 本测试使用的固定群晖配置
         * @param hash 已由仓储确认成功的 ColorOS 原生哈希
         */
        @Override
        public void recordUploaded(SynologyConfig config, String hash) {
            hashes.add(hash);
        }
    }

    private static final class ConcurrentGateway implements DsmBackupGateway {
        // 保存 DSM 完整路径与实际内容 MD5 的线程安全映射
        private final ConcurrentHashMap<String, String> remoteHashes = new ConcurrentHashMap<>();
        // 保存 DSM 网关实际成功写入的全部路径
        private final Set<String> uploadedPaths = ConcurrentHashMap.newKeySet();
        // 通知测试线程第一张照片已经进入首选路径查询
        private final CountDownLatch firstPrimaryQuery = new CountDownLatch(1);
        // 让未串行化实现的两个首选路径查询在上传前观察同一远端状态
        private final CountDownLatch simultaneousPrimaryQueries = new CountDownLatch(2);

        /**
         * 返回包含认证、上传和 MD5 能力的固定 DSM API 目录
         *
         * @return 可供仓储完成并发测试链路的 DSM API 目录
         */
        @Override
        public DsmApiCatalog discoverApis() throws IOException {
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
         * 查询远端路径 MD5，并在首选路径放大未串行化决策的竞争窗口
         *
         * @param catalog 已发现的固定 DSM API 目录
         * @param sid 固定测试会话标识
         * @param remotePath 仓储请求查询的 DSM 完整路径
         * @return 当前远端路径已经保存的可选内容 MD5
         * @throws IOException 等待并发决策窗口时线程被中断
         */
        @Override
        public Optional<String> md5(
                DsmApiCatalog catalog,
                String sid,
                String remotePath
        ) throws IOException {
            if (remotePath.endsWith("/IMG_1.jpg")) {
                firstPrimaryQuery.countDown();
                simultaneousPrimaryQueries.countDown();
                try {
                    simultaneousPrimaryQueries.await(
                            DECISION_WINDOW_MILLIS,
                            TimeUnit.MILLISECONDS
                    );
                } catch (InterruptedException /* 并发决策窗口等待被中断 */ error) {
                    Thread.currentThread().interrupt();
                    throw new IOException("并发备份测试被中断", error);
                }
            }
            return Optional.ofNullable(remoteHashes.get(remotePath));
        }

        /**
         * 按 overwrite=false 语义原子写入远端路径并记录实际上传结果
         *
         * @param catalog 已发现的固定 DSM API 目录
         * @param sid 固定测试会话标识
         * @param path 仓储选择的 DSM 上传路径
         * @param fileSize ColorOS 报告的照片字节数
         * @param input 仓储打开的照片输入流
         * @return 模拟 DSM 确认写入的字节数
         * @throws IOException 输入流读取失败或目标路径已经存在
         */
        @Override
        public long upload(
                DsmApiCatalog catalog,
                String sid,
                BackupPath path,
                long fileSize,
                InputStream input
        ) throws IOException {
            // 读取当前照片全部测试内容以生成远端 MD5
            byte[] content = input.readAllBytes();
            // 计算 DSM 后续内容校验会返回的 MD5
            String contentMd5 = md5(content);
            // 原子保存路径并获取可能已经存在的内容 MD5
            String existing = remoteHashes.putIfAbsent(path.remotePath(), contentMd5);
            if (existing != null) {
                throw new DsmRemoteFileAlreadyExistsException(path.remotePath());
            }
            uploadedPaths.add(path.remotePath());
            assertEquals(fileSize, content.length);
            return content.length;
        }

        /**
         * 计算测试照片内容对应的 DSM MD5
         *
         * @param content 固定测试照片内容
         * @return 32 位小写十六进制 MD5
         */
        private static String md5(byte[] content) {
            try {
                return HexFormat.of().formatHex(
                        MessageDigest.getInstance("MD5").digest(content)
                );
            } catch (NoSuchAlgorithmException /* 运行环境缺少标准 MD5 算法 */ error) {
                throw new AssertionError(error);
            }
        }
    }
}
