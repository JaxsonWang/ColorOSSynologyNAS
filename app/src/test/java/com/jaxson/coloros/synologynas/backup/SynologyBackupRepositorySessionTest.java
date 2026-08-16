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
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;

/** 验证备份仓储在认证配置切换时严格管理 DSM SID 生命周期 */
public final class SynologyBackupRepositorySessionTest {
    // 提供首次配置上传使用的固定 ColorOS 原生哈希
    private static final String FIRST_HASH = "0123456789abcdef0123456789abcdef"
            + "fedcba9876543210fedcba9876543210";
    // 提供切换配置后上传使用的不同 ColorOS 原生哈希
    private static final String SECOND_HASH = "abcdef0123456789abcdef0123456789"
            + "0123456789abcdef0123456789abcdef";
    // 提供配置回切后上传使用的第三个 ColorOS 原生哈希
    private static final String THIRD_HASH = "11111111111111112222222222222222"
            + "33333333333333334444444444444444";

    /** 验证认证配置变化时先注销旧 SID 再发现并登录新会话 */
    @Test
    public void logsOutPreviousSidBeforeCreatingSessionForChangedConfig() {
        // 记录旧会话注销与新会话创建之间的精确调用顺序
        List<String> events = new ArrayList<>();
        // 创建持有旧认证 SID 的第一个 DSM 网关
        RecordingGateway firstGateway = new RecordingGateway("first", "sid-first", events);
        // 创建只允许在旧 SID 注销后使用的第二个 DSM 网关
        RecordingGateway secondGateway = new RecordingGateway("second", "sid-second", events);
        // 创建可在两次上传之间切换认证配置的唯一配置源
        MutableConfigSource configSource = new MutableConfigSource(config("first-password"));
        // 创建用于确认两次远端成功都提交原生哈希的内存索引
        InMemoryHashStore hashStore = new InMemoryHashStore();
        // 创建按当前密码选择对应 DSM 网关的备份仓储
        SynologyBackupRepository repository = repository(
                configSource,
                hashStore,
                firstGateway,
                secondGateway
        );

        // 建立并使用第一份认证配置对应的旧会话
        BackupUploadResult firstResult = repository.upload(request(FIRST_HASH));
        // 切换会改变会话指纹但不改变远端备份路径的认证密码
        configSource.set(config("second-password"));
        // 使用不同照片触发旧 SID 注销和新会话创建
        BackupUploadResult secondResult = repository.upload(request(SECOND_HASH));

        assertEquals(BackupUploadResult.Status.SUCCESS, firstResult.status());
        assertEquals(BackupUploadResult.Status.SUCCESS, secondResult.status());
        assertSame(firstGateway.discoveredCatalog, firstGateway.logoutCatalog);
        assertEquals("sid-first", firstGateway.logoutSid);
        assertEquals(
                List.of(
                        "first:discover",
                        "first:login",
                        "first:md5",
                        "first:upload",
                        "first:logout",
                        "second:discover",
                        "second:login",
                        "second:md5",
                        "second:upload"
                ),
                events
        );
        assertEquals(Set.of(FIRST_HASH, SECOND_HASH), hashStore.hashes);
    }

    /** 验证旧 SID 注销失败后清空本地会话且后续请求正常认证 */
    @Test
    public void clearsFailedLogoutSessionBeforeNextExplicitRequest() {
        // 记录注销失败前后是否错误进入新 DSM 会话创建路径
        List<String> events = new ArrayList<>();
        // 创建会在配置切换注销阶段返回明确失败的旧网关
        RecordingGateway firstGateway = new RecordingGateway("first", "sid-first", events);
        firstGateway.logoutFailure = new IOException("DSM 旧 SID 注销失败");
        // 创建本次注销失败请求不得调用的新配置网关
        RecordingGateway secondGateway = new RecordingGateway("second", "sid-second", events);
        // 创建初始指向第一份认证配置的可变配置源
        MutableConfigSource configSource = new MutableConfigSource(config("first-password"));
        // 创建可观察注销失败后索引提交状态的内存存储
        InMemoryHashStore hashStore = new InMemoryHashStore();
        // 创建共享旧会话缓存的备份仓储
        SynologyBackupRepository repository = repository(
                configSource,
                hashStore,
                firstGateway,
                secondGateway
        );

        // 建立旧配置对应的可复用 DSM 会话
        BackupUploadResult firstResult = repository.upload(request(FIRST_HASH));
        // 切换认证配置以触发旧 SID 注销
        configSource.set(config("second-password"));
        // 执行必须直接暴露注销失败的第二次上传
        BackupUploadResult secondResult = repository.upload(request(SECOND_HASH));

        assertEquals(BackupUploadResult.Status.SUCCESS, firstResult.status());
        assertEquals(BackupUploadResult.Status.FAILED, secondResult.status());
        assertEquals(BackupUploadResult.ErrorCode.UPLOAD_FAILED, secondResult.errorCode());
        assertEquals("DSM 旧 SID 注销失败", secondResult.message());
        assertSame(firstGateway.discoveredCatalog, firstGateway.logoutCatalog);
        assertEquals("sid-first", firstGateway.logoutSid);
        assertEquals(
                List.of(
                        "first:discover",
                        "first:login",
                        "first:md5",
                        "first:upload",
                        "first:logout"
                ),
                events
        );
        assertFalse(hashStore.hashes.contains(SECOND_HASH));

        // nextNewResult 是保持新配置的后续显式请求结果
        BackupUploadResult nextNewResult = repository.upload(request(SECOND_HASH));
        assertEquals(BackupUploadResult.Status.SUCCESS, nextNewResult.status());
        assertEquals(
                List.of(
                        "first:discover",
                        "first:login",
                        "first:md5",
                        "first:upload",
                        "first:logout",
                        "second:discover",
                        "second:login",
                        "second:md5",
                        "second:upload"
                ),
                events
        );

        // 回切第一份配置必须注销新 SID 并创建全新旧配置会话
        configSource.set(config("first-password"));
        // revertedResult 是配置回切后使用全新会话的上传结果
        BackupUploadResult revertedResult = repository.upload(request(THIRD_HASH));
        assertEquals(BackupUploadResult.Status.SUCCESS, revertedResult.status());
        assertEquals(
                List.of(
                        "first:discover",
                        "first:login",
                        "first:md5",
                        "first:upload",
                        "first:logout",
                        "second:discover",
                        "second:login",
                        "second:md5",
                        "second:upload",
                        "second:logout",
                        "first:discover",
                        "first:login",
                        "first:md5",
                        "first:upload"
                ),
                events
        );
        assertEquals(Set.of(FIRST_HASH, SECOND_HASH, THIRD_HASH), hashStore.hashes);
    }

    /**
     * 创建按认证密码选择确定性 DSM 网关的备份仓储
     *
     * @param configSource 两次上传共享的可变配置源
     * @param hashStore 记录远端成功照片的内存索引
     * @param firstGateway 第一份认证配置使用的 DSM 网关
     * @param secondGateway 第二份认证配置使用的 DSM 网关
     * @return 可观察配置切换会话顺序的备份仓储
     */
    private static SynologyBackupRepository repository(
            MutableConfigSource configSource, // 两次上传共享的可变配置源
            InMemoryHashStore hashStore, // 记录远端成功照片的内存索引
            RecordingGateway firstGateway, // 第一份认证配置使用的 DSM 网关
            RecordingGateway secondGateway // 第二份认证配置使用的 DSM 网关
    ) {
        return new SynologyBackupRepository(
                configSource,
                hashStore,
                config /* 当前上传读取到的配置快照 */ ->
                        "first-password".equals(config.password())
                                ? firstGateway
                                : secondGateway
        );
    }

    /**
     * 创建仅认证密码可变的启用备份配置
     *
     * @param password 当前 DSM 会话使用的认证密码
     * @return 远端路径和索引作用域保持相同的配置
     */
    private static SynologyConfig config(String password) {
        return new SynologyConfig(
                "https://nas.example.test",
                "user",
                password,
                "",
                "/home/Photos",
                true,
                "ColorOS Backup"
        );
    }

    /**
     * 创建内容固定且原生哈希可变的照片请求
     *
     * @param hash 当前照片的 ColorOS SHA-256 原生哈希
     * @return 可重复打开固定内容流的备份请求
     */
    private static BackupUploadRequest request(String hash) {
        return new BackupUploadRequest(
                "IMG_" + hash.charAt(0) + ".jpg",
                3L,
                () -> new ByteArrayInputStream(new byte[]{1, 2, 3}),
                hash
        );
    }

    /** 提供可在连续上传之间原子替换的测试配置快照 */
    private static final class MutableConfigSource implements SynologyConfigSource {
        // 保存下一次仓储调用必须读取的唯一配置
        private SynologyConfig config;

        /**
         * 使用初始认证配置创建可变测试源
         *
         * @param config 首次上传读取的配置
         */
        private MutableConfigSource(SynologyConfig config) {
            this.config = config;
        }

        /** @return 测试配置源始终持有一份完整配置 */
        @Override
        public boolean hasConfig() {
            return true;
        }

        /** @return 当前上传必须使用的配置快照 */
        @Override
        public SynologyConfig load() {
            return config;
        }

        /**
         * 切换下一次上传使用的认证配置
         *
         * @param config 替换当前值的新配置
         */
        private void set(SynologyConfig config) {
            this.config = config;
        }
    }

    /** 记录远端成功后提交的 ColorOS 原生哈希 */
    private static final class InMemoryHashStore implements BackupHashStore {
        // 保存测试中已经由远端确认成功的全部原生哈希
        private final Set<String> hashes = new LinkedHashSet<>();

        /**
         * 查询候选哈希与当前内存索引的交集
         *
         * @param config 本测试不改变索引判断的配置快照
         * @param candidates ColorOS 请求查询的候选哈希
         * @return 已存在于当前内存索引的哈希
         */
        @Override
        public Set<String> findExisting(
                SynologyConfig config, // 本测试使用的群晖配置快照
                Collection<String> candidates // ColorOS 请求查询的候选哈希
        ) {
            // 从候选集合创建不会修改调用方参数的查询结果
            Set<String> existing = new LinkedHashSet<>(candidates);
            existing.retainAll(hashes);
            return existing;
        }

        /**
         * 记录已经由 DSM 确认存在的照片哈希
         *
         * @param config 本次远端确认使用的配置快照
         * @param hash 已经完成远端确认的 ColorOS 原生哈希
         */
        @Override
        public void recordUploaded(
                SynologyConfig config, /* 本次远端确认使用的配置快照 */
                String hash /* 已经完成远端确认的 ColorOS 原生哈希 */
        ) {
            hashes.add(hash);
        }
    }

    /** 记录单个认证配置对应的 DSM 会话调用顺序 */
    private static final class RecordingGateway implements DsmBackupGateway {
        // 标识事件来源属于旧配置还是新配置网关
        private final String name;
        // 保存登录阶段返回并应在注销阶段原样接收的 SID
        private final String sid;
        // 汇总跨两个网关的严格调用顺序
        private final List<String> events;
        // 保存当前网关最后一次发现的 DSM API 目录
        private DsmApiCatalog discoveredCatalog;
        // 保存注销阶段实际收到的旧 DSM API 目录
        private DsmApiCatalog logoutCatalog;
        // 保存注销阶段实际收到的旧 SID
        private String logoutSid;
        // 指定注销旧 SID 时需要暴露的测试失败
        private IOException logoutFailure;

        /**
         * 创建绑定固定 SID 和共享事件列表的测试网关
         *
         * @param name 区分旧配置和新配置的事件前缀
         * @param sid 当前网关登录后返回的固定 SID
         * @param events 两个网关共同写入的调用顺序
         */
        private RecordingGateway(String name, String sid, List<String> events) {
            this.name = name;
            this.sid = sid;
            this.events = events;
        }

        /** @return 包含认证、MD5 和上传能力的固定 API 目录 */
        @Override
        public DsmApiCatalog discoverApis() throws IOException {
            events.add(name + ":discover");
            discoveredCatalog = DsmApiInfoParser.parse("""
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
            return discoveredCatalog;
        }

        /**
         * 返回当前认证配置对应的固定 SID
         *
         * @param catalog 当前网关发现的 DSM API 目录
         * @return 当前配置的测试 SID
         */
        @Override
        public String login(
                DsmApiCatalog catalog /* 当前网关发现的 DSM API 目录 */
        ) {
            events.add(name + ":login");
            return sid;
        }

        /**
         * 记录旧目录和 SID，并按测试配置明确成功或失败
         *
         * @param catalog 旧会话创建时使用的 DSM API 目录
         * @param sid 旧会话登录后持有的 SID
         * @throws IOException 测试指定的注销失败
         */
        @Override
        public void logout(
                DsmApiCatalog catalog, /* 旧会话创建时使用的 DSM API 目录 */
                String sid /* 旧会话登录后持有的 SID */
        ) throws IOException {
            events.add(name + ":logout");
            logoutCatalog = catalog;
            logoutSid = sid;
            if (logoutFailure != null) {
                throw logoutFailure;
            }
        }

        /**
         * 返回远端目标不存在以进入正常上传路径
         *
         * @param catalog 当前网关发现的 DSM API 目录
         * @param sid 当前配置登录后持有的 SID
         * @param remotePath 本次查询的完整远端路径
         * @return 固定为空的远端 MD5
         */
        @Override
        public Optional<String> md5(
                DsmApiCatalog catalog, // 当前网关发现的 DSM API 目录
                String sid, // 当前配置登录后持有的 SID
                String remotePath // 本次查询的完整远端路径
        ) {
            events.add(name + ":md5");
            return Optional.empty();
        }

        /**
         * 读取完整测试照片并返回 DSM 确认的上传字节数
         *
         * @param catalog 当前网关发现的 DSM API 目录
         * @param sid 当前配置登录后持有的 SID
         * @param path 仓储已经选定的远端备份路径
         * @param fileSize ColorOS 报告的照片字节数
         * @param input 当前上传独占的照片输入流
         * @return 实际读取并确认的照片字节数
         * @throws IOException 测试照片读取失败
         */
        @Override
        public long upload(
                DsmApiCatalog catalog, // 当前网关发现的 DSM API 目录
                String sid, // 当前配置登录后持有的 SID
                BackupPath path, // 仓储已经选定的远端备份路径
                long fileSize, // ColorOS 报告的照片字节数
                InputStream input // 当前上传独占的照片输入流
        ) throws IOException {
            events.add(name + ":upload");
            // 读取全部固定照片内容以模拟 DSM 已完成流式上传
            long uploadedBytes = input.readAllBytes().length;
            assertEquals(fileSize, uploadedBytes);
            return uploadedBytes;
        }
    }
}
