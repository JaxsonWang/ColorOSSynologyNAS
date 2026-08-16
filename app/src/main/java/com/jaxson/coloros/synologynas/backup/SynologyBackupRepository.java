package com.jaxson.coloros.synologynas.backup;

import com.jaxson.coloros.synologynas.SynologyConfig;
import com.jaxson.coloros.synologynas.SynologyConfigSource;
import com.jaxson.coloros.synologynas.dsm.DsmApiCatalog;
import com.jaxson.coloros.synologynas.dsm.DsmBackupClient;
import com.jaxson.coloros.synologynas.dsm.DsmBackupGateway;
import com.jaxson.coloros.synologynas.dsm.DsmBackupReadException;
import com.jaxson.coloros.synologynas.dsm.DsmException;
import com.jaxson.coloros.synologynas.dsm.DsmRemoteFileAlreadyExistsException;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

public final class SynologyBackupRepository implements BackupRepository {
    // 提供当前已保存且可解密的群晖配置
    private final SynologyConfigSource configSource;
    // 持久化只在 DSM 成功后确认的 ColorOS 原生哈希
    private final BackupHashStore hashStore;
    // 按当前配置创建 DSM 备份网关，测试可注入确定性实现
    private final Function<SynologyConfig, DsmBackupGateway> clientFactory;

    // 缓存与完整认证配置指纹绑定的进程内 DSM 会话
    private Session session;

    /**
     * 使用生产 DSM 客户端创建照片备份仓储
     *
     * @param configSource 群晖配置读取边界
     * @param hashStore 照片备份哈希索引
     */
    public SynologyBackupRepository(
            SynologyConfigSource configSource,
            BackupHashStore hashStore
    ) {
        this(configSource, hashStore, DsmBackupClient::new);
    }

    /**
     * 使用显式网关工厂创建可验证的照片备份仓储
     *
     * @param configSource 群晖配置读取边界
     * @param hashStore 照片备份哈希索引
     * @param clientFactory 按配置创建 DSM 备份网关的工厂
     */
    SynologyBackupRepository(
            SynologyConfigSource configSource,
            BackupHashStore hashStore,
            Function<SynologyConfig, DsmBackupGateway> clientFactory
    ) {
        this.configSource = configSource;
        this.hashStore = hashStore;
        this.clientFactory = clientFactory;
    }

    /**
     * 读取当前配置并判断是否允许照片备份
     *
     * @return 已配置且照片备份开关开启时为 true
     */
    @Override
    public boolean isEnabled() {
        if (!configSource.hasConfig()) {
            return false;
        }
        try {
            // 读取实际配置，避免仅凭配置键存在就误报备份已开启
            SynologyConfig config = configSource.load();
            return config != null && config.backupEnabled();
        } catch (GeneralSecurityException /* 凭据读取或解密错误 */ error) {
            throw new IllegalStateException("群晖备份配置读取失败", error);
        }
    }

    /**
     * 查询当前启用配置作用域内已备份的 ColorOS 原生哈希
     *
     * @param hashes ColorOS 请求查询的原生哈希集合
     * @return 已存在于当前索引作用域的哈希集合
     * @throws IOException 配置读取或索引查询失败
     */
    @Override
    public Set<String> findExistingHashes(Collection<String> hashes) throws IOException {
        // 加载当前启用配置，使查询与上传使用完全相同的作用域规则
        SynologyConfig config = loadEnabledConfig();
        return hashStore.findExisting(config, hashes);
    }

    /**
     * 串行执行“索引查询、远端校验、上传、成功后落索引”的单张照片事务
     *
     * @param request ColorOS 相册提供的照片备份请求
     * @return 可映射回 ColorOS 私有合约的明确结果
     */
    @Override
    public synchronized BackupUploadResult upload(BackupUploadRequest request) {
        try {
            // 读取当前启用配置并作为本次上传全链路唯一配置快照
            SynologyConfig config = loadEnabledConfig();
            if (!hashStore.findExisting(config, Set.of(request.fileHash())).isEmpty()) {
                return BackupUploadResult.alreadyExists("照片已存在于群晖备份索引");
            }

            // 获取与本次配置精确绑定的 DSM API 目录和认证会话
            Session activeSession = requireSession(config);
            // 根据远端同名文件的 MD5 内容确定首选、冲突或无需上传目标
            BackupPath target = resolveTarget(activeSession, config, request);
            // 保存 DSM 上传接口确认的写入字节数；远端已存在时保持为零
            long bytesWritten = 0L;
            if (target != null) {
                try (InputStream /* 本次 DSM 上传独占的照片输入流 */ input =
                             request.inputSource().open()) {
                    if (input == null) {
                        return BackupUploadResult.failed(
                                BackupUploadResult.ErrorCode.READ_DATA_FAILED,
                                "ColorOS 相册未提供照片输入流"
                        );
                    }
                    bytesWritten = activeSession.client.upload(
                            activeSession.catalog,
                            activeSession.sid,
                            target,
                            request.fileSize(),
                            input
                    );
                } catch (DsmBackupReadException /* DSM 上传读取照片失败 */ error) {
                    return BackupUploadResult.failed(
                            BackupUploadResult.ErrorCode.READ_DATA_FAILED,
                            message(error)
                    );
                } catch (DsmRemoteFileAlreadyExistsException /* 上传时远端目标已存在 */ error) {
                    return BackupUploadResult.alreadyExists(message(error));
                } catch (IOException /* DSM 上传请求或响应处理失败 */ error) {
                    return BackupUploadResult.failed(
                            BackupUploadResult.ErrorCode.UPLOAD_FAILED,
                            message(error)
                    );
                }
            }

            try {
                hashStore.recordUploaded(config, request.fileHash());
            } catch (IOException /* 远端成功后的本地哈希索引提交失败 */ error) {
                return BackupUploadResult.failed(
                        BackupUploadResult.ErrorCode.UPLOAD_NOTICE_FAILED,
                        message(error)
                );
            }
            if (target == null) {
                return BackupUploadResult.alreadyExists("群晖已存在相同内容的照片");
            }
            return BackupUploadResult.success(target, bytesWritten);
        } catch (DsmBackupReadException /* 远端内容校验前读取照片失败 */ error) {
            return BackupUploadResult.failed(
                    BackupUploadResult.ErrorCode.READ_DATA_FAILED,
                    message(error)
            );
        } catch (IllegalArgumentException /* ColorOS 请求字段不满足备份合约 */ error) {
            return BackupUploadResult.failed(
                    BackupUploadResult.ErrorCode.READ_DATA_FAILED,
                    message(error)
            );
        } catch (IOException /* 配置、发现、认证或远端校验失败 */ error) {
            return BackupUploadResult.failed(
                    BackupUploadResult.ErrorCode.UPLOAD_FAILED,
                    message(error)
            );
        }
    }

    /**
     * 根据远端同名文件的 MD5 内容选择实际上传路径或确认内容已存在
     *
     * @param activeSession 已完成发现和登录的 DSM 会话
     * @param config 本次上传使用的群晖配置快照
     * @param request ColorOS 相册提供的照片备份请求
     * @return 应上传的目标路径；远端已有相同内容时返回 null
     * @throws IOException DSM MD5 查询或本机照片读取失败
     */
    private BackupPath resolveTarget(
            Session activeSession,
            SynologyConfig config,
            BackupUploadRequest request
    ) throws IOException {
        // 生成保留安全原始文件名的首选 DSM 路径
        BackupPath primary = BackupPathPolicy.primary(config, request);
        // 查询首选路径是否已存在以及存在时的内容 MD5
        Optional<String> primaryHash = activeSession.client.md5(
                activeSession.catalog,
                activeSession.sid,
                primary.remotePath()
        );
        if (primaryHash.isEmpty()) {
            return primary;
        }
        // 仅在远端同名文件存在时读取本机照片并计算一次 MD5
        String localMd5 = localMd5(request.inputSource());
        if (localMd5.equals(primaryHash.get())) {
            return null;
        }

        // 生成带完整 ColorOS SHA-256 后缀的稳定冲突路径
        BackupPath collision = BackupPathPolicy.collision(config, request);
        // 查询稳定冲突路径是否已经由相同或不同内容占用
        Optional<String> collisionHash = activeSession.client.md5(
                activeSession.catalog,
                activeSession.sid,
                collision.remotePath()
        );
        if (collisionHash.isEmpty()) {
            return collision;
        }
        if (localMd5.equals(collisionHash.get())) {
            return null;
        }
        throw new DsmException("群晖备份目标发生 MD5 文件名冲突: " + collision.remotePath());
    }

    /**
     * 从可重复打开的照片输入源计算 DSM 内容比对需要的 MD5
     *
     * @param source 可从照片起始位置打开输入流的数据源
     * @return 32 位小写十六进制 MD5
     * @throws DsmBackupReadException ColorOS 照片数据读取失败
     */
    private static String localMd5(BackupInputSource source) throws DsmBackupReadException {
        try (InputStream /* 本次 MD5 计算独占的照片输入流 */ input = source.open()) {
            if (input == null) {
                throw new DsmBackupReadException("ColorOS 相册未提供照片输入流");
            }
            // 保存 DSM File Station MD5 接口使用的标准摘要计算器
            MessageDigest digest = MessageDigest.getInstance("MD5");
            // 使用固定大小缓冲区流式读取照片，避免把原图整体载入内存
            byte[] buffer = new byte[64 * 1024];
            // 保存当前一轮实际读取的字节数
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (count > 0) {
                    digest.update(buffer, 0, count);
                }
            }
            // 按固定区域设置构造 32 位小写十六进制摘要
            StringBuilder result = new StringBuilder(32);
            // 逐字节编码最终 MD5 摘要
            for (byte item : digest.digest()) {
                result.append(String.format(Locale.ROOT, "%02x", item & 0xff));
            }
            return result.toString();
        } catch (DsmBackupReadException /* 输入源未提供照片流 */ error) {
            throw error;
        } catch (IOException /* 照片流打开、读取或关闭失败 */ error) {
            throw new DsmBackupReadException("读取本机照片用于 DSM MD5 校验失败", error);
        } catch (NoSuchAlgorithmException /* 运行环境缺少标准 MD5 算法 */ error) {
            throw new IllegalStateException("MD5 unavailable", error);
        }
    }

    /**
     * 复用与完整认证配置指纹绑定的 DSM 会话，配置变化时重新发现并登录
     *
     * @param config 本次备份使用的完整群晖配置快照
     * @return 与该配置精确绑定的进程内 DSM 会话
     * @throws IOException DSM API 发现或登录失败
     */
    private synchronized Session requireSession(SynologyConfig config) throws IOException {
        // 计算包含认证字段和根目录的会话绑定指纹
        String fingerprint = fingerprint(config);
        if (session != null && session.configFingerprint.equals(fingerprint)) {
            return session;
        }
        // 为新配置快照创建 DSM 备份网关
        DsmBackupGateway client = clientFactory.apply(config);
        // 发现当前 DSM 实际支持的 API 路径和版本
        DsmApiCatalog catalog = client.discoverApis();
        // 使用发现后的认证合约获取仅驻留内存的 DSM SID
        String sid = client.login(catalog);
        session = new Session(fingerprint, client, catalog, sid);
        return session;
    }

    /**
     * 从配置源读取非空群晖配置并统一映射读取失败
     *
     * @return 当前已保存的群晖配置
     * @throws IOException 配置缺失、读取或凭据解密失败
     */
    private SynologyConfig loadConfig() throws IOException {
        try {
            // 从唯一配置源读取当前群晖配置快照
            SynologyConfig config = configSource.load();
            if (config == null) {
                throw new DsmException("请先在群晖 NAS 模块中保存 DSM 配置");
            }
            return config;
        } catch (DsmException /* 已具有明确业务语义的配置错误 */ error) {
            throw error;
        } catch (GeneralSecurityException /* 凭据读取或解密错误 */ error) {
            throw new DsmException("群晖凭据读取失败", error);
        }
    }

    /**
     * 读取配置并强制执行照片备份开关
     *
     * @return 当前已启用照片备份的群晖配置
     * @throws IOException 配置不可用或照片备份已关闭
     */
    private SynologyConfig loadEnabledConfig() throws IOException {
        // 读取已保存配置后在仓储边界集中检查备份开关
        SynologyConfig config = loadConfig();
        if (!config.backupEnabled()) {
            throw new DsmException("群晖照片备份已关闭");
        }
        return config;
    }

    /**
     * 生成绑定 DSM 认证会话的完整配置指纹
     *
     * @param config 当前群晖配置快照
     * @return 用空字符无歧义分隔的进程内配置指纹
     */
    private static String fingerprint(SynologyConfig config) {
        return config.serverUrl() + '\u0000'
                + config.username() + '\u0000'
                + config.password() + '\u0000'
                + config.otp() + '\u0000'
                + config.remoteRoot();
    }

    /**
     * 提取异常中的明确消息，并在消息缺失时保留异常类型
     *
     * @param error 需要映射到备份结果的直接失败原因
     * @return 非空的可观察错误信息
     */
    private static String message(Throwable error) {
        // 读取直接异常消息，避免为正常失败路径额外包装状态
        String message = error.getMessage();
        return message == null || message.isBlank()
                ? error.getClass().getSimpleName()
                : message;
    }

    private static final class Session {
        // 保存用于确认会话仍匹配当前认证配置的指纹
        private final String configFingerprint;
        // 保存已经完成 API 发现和登录的 DSM 备份网关
        private final DsmBackupGateway client;
        // 保存该 DSM 实例实际发现的 API 路径与版本目录
        private final DsmApiCatalog catalog;
        // 保存仅驻留当前进程内存的 DSM 会话标识
        private final String sid;

        /**
         * 创建与单个配置指纹绑定的不可变 DSM 认证会话
         *
         * @param configFingerprint 完整认证配置指纹
         * @param client 已完成发现和登录的 DSM 备份网关
         * @param catalog DSM 实际支持的 API 目录
         * @param sid 仅驻留内存的 DSM 会话标识
         */
        private Session(
                String configFingerprint,
                DsmBackupGateway client,
                DsmApiCatalog catalog,
                String sid
        ) {
            this.configFingerprint = configFingerprint;
            this.client = client;
            this.catalog = catalog;
            this.sid = sid;
        }
    }
}
