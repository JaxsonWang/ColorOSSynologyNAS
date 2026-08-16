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
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class SynologyBackupRepositoryTest {
    private static final String HASH = "0123456789abcdef0123456789abcdef"
            + "fedcba9876543210fedcba9876543210";
    private static final String CONTENT_MD5 = "5289df737df57326fcdd22597afb1fac";

    @Test
    public void uploadsToDsmAndRecordsHashOnlyAfterSuccess() {
        InMemoryHashStore hashStore = new InMemoryHashStore();
        FakeGateway gateway = new FakeGateway();
        SynologyBackupRepository repository = repository(hashStore, gateway);

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

    @Test
    public void skipsNetworkWhenHashIndexAlreadyContainsPhoto() {
        InMemoryHashStore hashStore = new InMemoryHashStore();
        hashStore.hashes.add(HASH);
        FakeGateway gateway = new FakeGateway();
        SynologyBackupRepository repository = repository(hashStore, gateway);

        BackupUploadResult result = repository.upload(request());

        assertEquals(BackupUploadResult.Status.ALREADY_EXISTS, result.status());
        assertEquals(0, gateway.discoverInvocations);
        assertNull(gateway.uploadedPath);
    }

    @Test
    public void usesHashSuffixWhenOriginalNameContainsDifferentRemotePhoto() {
        InMemoryHashStore hashStore = new InMemoryHashStore();
        FakeGateway gateway = new FakeGateway();
        gateway.primaryHash = Optional.of("ffffffffffffffffffffffffffffffff");
        SynologyBackupRepository repository = repository(hashStore, gateway);

        BackupUploadResult result = repository.upload(request());

        assertEquals(BackupUploadResult.Status.SUCCESS, result.status());
        assertEquals(
                "/home/Photos/ColorOS Backup/"
                        + "IMG_1_0123456789abcdef0123456789abcdef"
                        + "fedcba9876543210fedcba9876543210.jpg",
                gateway.uploadedPath
        );
    }

    @Test
    public void recordsNativeHashForVerifiedRemoteDuplicateWithoutUploading() {
        InMemoryHashStore hashStore = new InMemoryHashStore();
        FakeGateway gateway = new FakeGateway();
        gateway.primaryHash = Optional.of(CONTENT_MD5);
        SynologyBackupRepository repository = repository(hashStore, gateway);

        BackupUploadResult result = repository.upload(request());

        assertEquals(BackupUploadResult.Status.ALREADY_EXISTS, result.status());
        assertNull(gateway.uploadedPath);
        assertTrue(hashStore.hashes.contains(HASH));
    }

    @Test
    public void doesNotRecordHashWhenDsmUploadFails() {
        InMemoryHashStore hashStore = new InMemoryHashStore();
        FakeGateway gateway = new FakeGateway();
        gateway.uploadFailure = new IOException("DSM 416");
        SynologyBackupRepository repository = repository(hashStore, gateway);

        BackupUploadResult result = repository.upload(request());

        assertEquals(BackupUploadResult.Status.FAILED, result.status());
        assertEquals(BackupUploadResult.ErrorCode.UPLOAD_FAILED, result.errorCode());
        assertEquals("DSM 416", result.message());
        assertFalse(hashStore.hashes.contains(HASH));
    }

    @Test
    public void disabledBackupDoesNotConnectOrUpload() {
        InMemoryHashStore hashStore = new InMemoryHashStore();
        FakeGateway gateway = new FakeGateway();
        SynologyBackupRepository repository = repository(
                hashStore,
                gateway,
                config(false, "ColorOS Backup")
        );

        BackupUploadResult result = repository.upload(request());

        assertFalse(repository.isEnabled());
        assertEquals(BackupUploadResult.Status.FAILED, result.status());
        assertEquals("群晖照片备份已关闭", result.message());
        assertEquals(0, gateway.discoverInvocations);
        assertNull(gateway.uploadedPath);
    }

    private static SynologyBackupRepository repository(
            InMemoryHashStore hashStore,
            FakeGateway gateway
    ) {
        return repository(hashStore, gateway, config(true, "ColorOS Backup"));
    }

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

    private static SynologyBackupRepository repository(
            InMemoryHashStore hashStore,
            FakeGateway gateway,
            SynologyConfig config
    ) {
        SynologyConfigSource configSource = new SynologyConfigSource() {
            @Override
            public boolean hasConfig() {
                return true;
            }

            @Override
            public SynologyConfig load() {
                return config;
            }
        };
        return new SynologyBackupRepository(configSource, hashStore, ignored -> gateway);
    }

    private static BackupUploadRequest request() {
        return new BackupUploadRequest(
                "synology-dsm7",
                "phone-id",
                "PLK110",
                "IMG_1.jpg",
                3L,
                () -> new ByteArrayInputStream(new byte[]{1, 2, 3}),
                HASH,
                List.of("Camera")
        );
    }

    private static final class InMemoryHashStore implements BackupHashStore {
        private final Set<String> hashes = new LinkedHashSet<>();

        @Override
        public Set<String> findExisting(
                SynologyConfig config,
                Collection<String> candidates
        ) {
            Set<String> result = new LinkedHashSet<>(candidates);
            result.retainAll(hashes);
            return result;
        }

        @Override
        public void recordUploaded(SynologyConfig config, String hash) {
            hashes.add(hash);
        }
    }

    private static final class FakeGateway implements DsmBackupGateway {
        private Optional<String> primaryHash = Optional.empty();
        private Optional<String> collisionHash = Optional.empty();
        private IOException uploadFailure;
        private int discoverInvocations;
        private String uploadedPath;

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

        @Override
        public String login(DsmApiCatalog catalog) {
            return "sid";
        }

        @Override
        public Optional<String> md5(
                DsmApiCatalog catalog,
                String sid,
                String remotePath
        ) {
            return remotePath.contains(HASH) ? collisionHash : primaryHash;
        }

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
