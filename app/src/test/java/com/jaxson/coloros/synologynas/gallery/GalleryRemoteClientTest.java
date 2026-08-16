package com.jaxson.coloros.synologynas.gallery;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public final class GalleryRemoteClientTest {
    @Test
    public void streamsOriginalBytesAndCompletionIntoGalleryCallback() throws IOException {
        GalleryRemoteClient client = new GalleryRemoteClient(new FakeDataSource());
        RecordingCallback callback = new RecordingCallback();

        long bytes = client.streamOriginal("photo", callback);

        assertEquals(3L, bytes);
        assertArrayEquals(new byte[]{1, 2, 3}, callback.bytes());
        assertTrue(callback.completed);
        assertEquals(3, callback.invocations);
    }

    @Test
    public void returnsRemoteThumbnailWithoutWritingLocalMedia() throws IOException {
        GalleryRemoteClient client = new GalleryRemoteClient(new FakeDataSource());

        assertArrayEquals(
                new byte[]{4, 5, 6},
                client.getThumbnail("photo", GalleryContract.THUMBNAIL_LARGE)
        );
    }

    @Test
    public void delegatesRemotePhotoDeletion() throws IOException {
        FakeDataSource dataSource = new FakeDataSource();
        GalleryRemoteClient client = new GalleryRemoteClient(dataSource);

        assertTrue(client.deletePhotos(List.of("photo-1", "photo-2")));
        assertEquals(List.of("photo-1", "photo-2"), dataSource.deletedPhotoIds);
    }

    @Test
    public void exposesStoredAndProbedDeviceModel() throws IOException {
        GalleryRemoteClient client = new GalleryRemoteClient(new FakeDataSource());

        assertEquals("DS920+", client.configuredDeviceModel());
        assertEquals("DS220+", client.probeDeviceModel());
        assertEquals("DS220+", client.currentDeviceModel());
    }

    public static final class RecordingCallback {
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();

        private boolean completed;
        private int invocations;

        public void invoke(byte[] bytes, boolean completed) throws IOException {
            invocations++;
            output.write(bytes);
            this.completed = completed;
        }

        private byte[] bytes() {
            return output.toByteArray();
        }
    }

    private static final class FakeDataSource implements RemoteGalleryDataSource {
        private List<String> deletedPhotoIds = List.of();

        @Override
        public boolean isConfigured() {
            return true;
        }

        @Override
        public String configuredDeviceModel() {
            return "DS920+";
        }

        @Override
        public String probeDeviceModel() {
            return "DS220+";
        }

        @Override
        public List<RemoteAlbum> listAlbums(int offset, int limit) {
            return new ArrayList<>();
        }

        @Override
        public RemoteAlbum getAlbum(String albumId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<RemotePhoto> listPhotos(String albumId, int offset, int limit) {
            return new ArrayList<>();
        }

        @Override
        public int photoCount() {
            return 0;
        }

        @Override
        public void downloadThumbnail(String photoId, String size, OutputStream output)
                throws IOException {
            output.write(new byte[]{4, 5, 6});
        }

        @Override
        public void downloadOriginal(String photoId, OutputStream output) throws IOException {
            output.write(new byte[]{1, 2});
            output.write(new byte[]{3});
        }

        @Override
        public boolean deletePhotos(List<String> photoIds) {
            deletedPhotoIds = List.copyOf(photoIds);
            return true;
        }
    }
}
