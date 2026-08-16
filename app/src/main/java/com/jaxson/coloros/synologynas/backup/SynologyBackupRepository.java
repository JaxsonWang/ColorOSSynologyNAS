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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

public final class SynologyBackupRepository implements BackupRepository {
    private final SynologyConfigSource configSource;
    private final BackupHashStore hashStore;
    private final Function<SynologyConfig, DsmBackupGateway> clientFactory;

    private Session session;

    public SynologyBackupRepository(
            SynologyConfigSource configSource,
            BackupHashStore hashStore
    ) {
        this(configSource, hashStore, DsmBackupClient::new);
    }

    SynologyBackupRepository(
            SynologyConfigSource configSource,
            BackupHashStore hashStore,
            Function<SynologyConfig, DsmBackupGateway> clientFactory
    ) {
        this.configSource = configSource;
        this.hashStore = hashStore;
        this.clientFactory = clientFactory;
    }

    @Override
    public boolean isConfigured() {
        return configSource.hasConfig();
    }

    @Override
    public boolean isEnabled() {
        if (!configSource.hasConfig()) {
            return false;
        }
        try {
            SynologyConfig config = configSource.load();
            return config != null && config.backupEnabled();
        } catch (Exception error) {
            throw new IllegalStateException("群晖备份配置读取失败", error);
        }
    }

    @Override
    public Set<String> findExistingHashes(Collection<String> hashes) throws IOException {
        SynologyConfig config = loadEnabledConfig();
        return hashStore.findExisting(config, hashes);
    }

    @Override
    public BackupUploadResult upload(BackupUploadRequest request) {
        try {
            SynologyConfig config = loadEnabledConfig();
            if (!hashStore.findExisting(config, Set.of(request.fileHash())).isEmpty()) {
                return BackupUploadResult.alreadyExists("照片已存在于群晖备份索引");
            }

            Session activeSession = requireSession(config);
            BackupPath target = resolveTarget(activeSession, config, request);
            long bytesWritten = 0L;
            if (target != null) {
                try (InputStream input = request.inputSource().open()) {
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
                } catch (DsmBackupReadException error) {
                    return BackupUploadResult.failed(
                            BackupUploadResult.ErrorCode.READ_DATA_FAILED,
                            message(error)
                    );
                } catch (DsmRemoteFileAlreadyExistsException error) {
                    return BackupUploadResult.alreadyExists(message(error));
                } catch (IOException error) {
                    return BackupUploadResult.failed(
                            BackupUploadResult.ErrorCode.UPLOAD_FAILED,
                            message(error)
                    );
                }
            }

            try {
                hashStore.recordUploaded(config, request.fileHash());
            } catch (IOException error) {
                return BackupUploadResult.failed(
                        BackupUploadResult.ErrorCode.UPLOAD_NOTICE_FAILED,
                        message(error)
                );
            }
            if (target == null) {
                return BackupUploadResult.alreadyExists("群晖已存在相同内容的照片");
            }
            return BackupUploadResult.success(target, bytesWritten);
        } catch (DsmBackupReadException error) {
            return BackupUploadResult.failed(
                    BackupUploadResult.ErrorCode.READ_DATA_FAILED,
                    message(error)
            );
        } catch (IllegalArgumentException error) {
            return BackupUploadResult.failed(
                    BackupUploadResult.ErrorCode.READ_DATA_FAILED,
                    message(error)
            );
        } catch (IOException error) {
            return BackupUploadResult.failed(
                    BackupUploadResult.ErrorCode.UPLOAD_FAILED,
                    message(error)
            );
        }
    }

    private BackupPath resolveTarget(
            Session activeSession,
            SynologyConfig config,
            BackupUploadRequest request
    ) throws IOException {
        BackupPath primary = BackupPathPolicy.primary(config, request);
        Optional<String> primaryHash = activeSession.client.md5(
                activeSession.catalog,
                activeSession.sid,
                primary.remotePath()
        );
        if (primaryHash.isEmpty()) {
            return primary;
        }
        String localMd5 = localMd5(request.inputSource());
        if (localMd5.equals(primaryHash.get())) {
            return null;
        }

        BackupPath collision = BackupPathPolicy.collision(config, request);
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

    private static String localMd5(BackupInputSource source) throws DsmBackupReadException {
        try (InputStream input = source.open()) {
            if (input == null) {
                throw new DsmBackupReadException("ColorOS 相册未提供照片输入流");
            }
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[64 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (count > 0) {
                    digest.update(buffer, 0, count);
                }
            }
            StringBuilder result = new StringBuilder(32);
            for (byte item : digest.digest()) {
                result.append(String.format(Locale.ROOT, "%02x", item & 0xff));
            }
            return result.toString();
        } catch (DsmBackupReadException error) {
            throw error;
        } catch (IOException error) {
            throw new DsmBackupReadException("读取本机照片用于 DSM MD5 校验失败", error);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("MD5 unavailable", error);
        }
    }

    private synchronized Session requireSession(SynologyConfig config) throws IOException {
        String fingerprint = fingerprint(config);
        if (session != null && session.configFingerprint.equals(fingerprint)) {
            return session;
        }
        DsmBackupGateway client = clientFactory.apply(config);
        DsmApiCatalog catalog = client.discoverApis();
        String sid = client.login(catalog);
        session = new Session(fingerprint, client, catalog, sid);
        return session;
    }

    private SynologyConfig loadConfig() throws IOException {
        try {
            SynologyConfig config = configSource.load();
            if (config == null) {
                throw new DsmException("请先在群晖 NAS 模块中保存 DSM 配置");
            }
            return config;
        } catch (DsmException error) {
            throw error;
        } catch (Exception error) {
            throw new DsmException("群晖凭据读取失败", error);
        }
    }

    private SynologyConfig loadEnabledConfig() throws IOException {
        SynologyConfig config = loadConfig();
        if (!config.backupEnabled()) {
            throw new DsmException("群晖照片备份已关闭");
        }
        return config;
    }

    private static String fingerprint(SynologyConfig config) {
        return config.serverUrl() + '\u0000'
                + config.username() + '\u0000'
                + config.password() + '\u0000'
                + config.otp() + '\u0000'
                + config.remoteRoot();
    }

    private static String message(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank()
                ? error.getClass().getSimpleName()
                : message;
    }

    private static final class Session {
        private final String configFingerprint;
        private final DsmBackupGateway client;
        private final DsmApiCatalog catalog;
        private final String sid;

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
