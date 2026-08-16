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
    public void reconstructsColorOsRequestAndInvokesInputStreamProvider() throws Exception {
        RecordingRepository repository = new RecordingRepository();
        GalleryBackupClient client = new GalleryBackupClient(repository);
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

        BackupUploadResult result = client.upload(request);

        assertEquals(BackupUploadResult.Status.SUCCESS, result.status());
        assertEquals(GalleryContract.DEVICE_ID, repository.request.targetDeviceUserId());
        assertEquals("phone-id", repository.request.phoneDeviceId());
        assertEquals("PLK110", repository.request.phoneDeviceName());
        assertEquals("IMG_1.jpg", repository.request.originalName());
        assertEquals(List.of("Camera"), repository.request.deviceAlbumNames());
        assertArrayEquals(new byte[]{1, 2, 3}, repository.bytes);
    }

    @Test
    public void readsJadxAliasForTargetDeviceId() throws Exception {
        class AliasRequest {
            @SuppressWarnings("unused")
            public final String f24401a = GalleryContract.DEVICE_ID;
        }

        assertEquals(
                GalleryContract.DEVICE_ID,
                GalleryBackupClient.targetDeviceId(new AliasRequest())
        );
    }

    private static final class InputProvider {
        @SuppressWarnings("unused")
        public ByteArrayInputStream invoke() {
            return new ByteArrayInputStream(new byte[]{1, 2, 3});
        }
    }

    private static final class RecordingRepository implements BackupRepository {
        private BackupUploadRequest request;
        private byte[] bytes;

        @Override
        public boolean isConfigured() {
            return true;
        }

        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public Set<String> findExistingHashes(Collection<String> hashes) {
            return Set.of();
        }

        @Override
        public BackupUploadResult upload(BackupUploadRequest request) {
            this.request = request;
            try (java.io.InputStream input = request.inputSource().open()) {
                bytes = input.readAllBytes();
            } catch (IOException error) {
                throw new AssertionError(error);
            }
            return BackupUploadResult.success(
                    new BackupPath("/home/Photos/ColorOS Backup", "IMG_1.jpg"),
                    bytes.length
            );
        }
    }
}
