package com.jaxson.coloros.synologynas.gallery;

import com.jaxson.coloros.synologynas.backup.BackupPath;
import com.jaxson.coloros.synologynas.backup.BackupUploadResult;
import com.oplus.aiunit.vision.dpk;
import com.oplus.aiunit.vision.jjq;
import com.oplus.aiunit.vision.seq;
import com.oplus.aiunit.vision.teq;
import com.oplus.aiunit.vision.yjq;
import com.oplus.gallery.framework.abilities.cloudsync.nas.api.model.NasBackupUploadErrorCode;

import org.junit.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class ColorOsNasProviderProxyTest {
    @Test
    public void returnsCachedGalleryStatsWithoutLoadingRemoteInventory() throws Exception {
        CountingDataSource dataSource = new CountingDataSource();
        GalleryRemoteClient client = new GalleryRemoteClient(dataSource);
        dpk original = deviceUserId -> null;
        dpk proxy = (dpk) ColorOsNasProviderProxy.create(
                client,
                new RecordingBackupService(),
                original,
                getClass().getClassLoader(),
                () -> 1594
        );

        jjq stats = proxy.o(GalleryContract.DEVICE_ID);

        assertEquals(1594, stats.photoCount);
        assertEquals(0, dataSource.photoCountInvocations);
    }

    @Test
    public void exposesSynologyBackupCapabilityAndExistingHashes() throws Exception {
        RecordingBackupService backupService = new RecordingBackupService();
        backupService.existingHashes = Set.of(
                "0123456789abcdef0123456789abcdef"
                        + "fedcba9876543210fedcba9876543210"
        );
        dpk proxy = proxy(backupService, deviceUserId -> null);

        assertEquals(1, proxy.m(GalleryContract.DEVICE_ID));
        assertEquals(1, proxy.d(GalleryContract.DEVICE_ID, null));
        yjq result = proxy.i(
                GalleryContract.DEVICE_ID,
                new ArrayList<>(backupService.existingHashes)
        );

        assertTrue(result instanceof yjq.b);
        assertEquals(backupService.existingHashes, ((yjq.b) result).a);
    }

    @Test
    public void hidesSynologyBackupCapabilityWhenDisabled() throws Exception {
        RecordingBackupService backupService = new RecordingBackupService();
        backupService.enabled = false;
        dpk proxy = proxy(backupService, deviceUserId -> null);

        assertEquals(0, proxy.m(GalleryContract.DEVICE_ID));
        assertEquals(0, proxy.d(GalleryContract.DEVICE_ID, null));
    }

    @Test
    public void mapsSuccessfulUploadToColorOsNoticeSuccess() throws Exception {
        RecordingBackupService backupService = new RecordingBackupService();
        backupService.uploadResult = BackupUploadResult.success(
                new BackupPath(
                        "/home/Photos/ColorOS Backup",
                        "IMG_1.jpg"
                ),
                3L
        );
        dpk proxy = proxy(backupService, deviceUserId -> null);

        teq result = proxy.r(request(GalleryContract.DEVICE_ID));

        assertTrue(result instanceof teq.b);
        teq.b success = (teq.b) result;
        assertEquals(1, success.d);
        assertEquals(0, success.e);
        assertEquals(3L, success.c);
        assertEquals("/home/Photos/ColorOS Backup/IMG_1.jpg", success.b);
        assertTrue(success.a.e);
    }

    @Test
    public void mapsDuplicateAndUploadFailureWithoutFakeSuccess() throws Exception {
        RecordingBackupService backupService = new RecordingBackupService();
        dpk proxy = proxy(backupService, deviceUserId -> null);

        backupService.uploadResult = BackupUploadResult.alreadyExists("already uploaded");
        teq duplicate = proxy.r(request(GalleryContract.DEVICE_ID));
        assertEquals(NasBackupUploadErrorCode.FILE_ALREADY_EXISTS, ((teq.a) duplicate).a);

        backupService.uploadResult = BackupUploadResult.failed(
                BackupUploadResult.ErrorCode.UPLOAD_FAILED,
                "DSM 416"
        );
        teq failed = proxy.r(request(GalleryContract.DEVICE_ID));
        assertEquals(NasBackupUploadErrorCode.UPLOAD_FAILED, ((teq.a) failed).a);
        assertEquals("DSM 416", ((teq.a) failed).b);
    }

    @Test
    public void routesRequestObjectByTargetDeviceAndForwardsOtherNas() throws Exception {
        RecordingBackupService backupService = new RecordingBackupService();
        final int[] originalUploads = {0};
        dpk original = new dpk() {
            @Override
            public jjq o(String deviceUserId) {
                return null;
            }

            @Override
            public teq r(seq request) {
                originalUploads[0]++;
                return new teq.a(NasBackupUploadErrorCode.UNKNOWN, "original", null);
            }
        };
        dpk proxy = proxy(backupService, original);

        proxy.r(request(GalleryContract.DEVICE_ID));
        proxy.r(request("feiniu-device"));

        assertEquals(1, backupService.uploadInvocations);
        assertEquals(1, originalUploads[0]);
    }

    private dpk proxy(RecordingBackupService backupService, dpk original) throws Exception {
        return (dpk) ColorOsNasProviderProxy.create(
                new GalleryRemoteClient(new CountingDataSource()),
                backupService,
                original,
                getClass().getClassLoader(),
                () -> 1594
        );
    }

    private static seq request(String targetDeviceId) {
        return new seq(
                targetDeviceId,
                "phone-id",
                "PLK110",
                "IMG_1.jpg",
                3L,
                new Object() {
                    @SuppressWarnings("unused")
                    public java.io.InputStream invoke() {
                        return new java.io.ByteArrayInputStream(new byte[]{1, 2, 3});
                    }
                },
                "0123456789abcdef0123456789abcdef"
                        + "fedcba9876543210fedcba9876543210",
                "2026-08-16 10:00",
                List.of("Camera")
        );
    }

    private static final class RecordingBackupService implements GalleryBackupService {
        private Set<String> existingHashes = Set.of();
        private BackupUploadResult uploadResult = BackupUploadResult.alreadyExists("duplicate");
        private int uploadInvocations;
        private boolean enabled = true;

        @Override
        public boolean isConfigured() {
            return true;
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        @Override
        public Set<String> findExistingHashes(Collection<String> hashes) {
            return existingHashes;
        }

        @Override
        public BackupUploadResult upload(Object colorOsRequest) {
            uploadInvocations++;
            return uploadResult;
        }
    }

    private static final class CountingDataSource implements RemoteGalleryDataSource {
        private int photoCountInvocations;

        @Override
        public boolean isConfigured() {
            return true;
        }

        @Override
        public String configuredDeviceModel() {
            return "DS220+";
        }

        @Override
        public String probeDeviceModel() {
            return "DS220+";
        }

        @Override
        public List<RemoteAlbum> listAlbums(int offset, int limit) {
            return List.of();
        }

        @Override
        public RemoteAlbum getAlbum(String albumId) throws IOException {
            throw new IOException("Unexpected getAlbum");
        }

        @Override
        public List<RemotePhoto> listPhotos(String albumId, int offset, int limit) {
            return List.of();
        }

        @Override
        public int photoCount() {
            photoCountInvocations++;
            return 1594;
        }

        @Override
        public void downloadThumbnail(String photoId, String size, OutputStream output) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void downloadOriginal(String photoId, OutputStream output) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean deletePhotos(List<String> photoIds) {
            throw new UnsupportedOperationException();
        }
    }
}
