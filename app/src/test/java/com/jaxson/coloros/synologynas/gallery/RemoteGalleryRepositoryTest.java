package com.jaxson.coloros.synologynas.gallery;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.jaxson.coloros.synologynas.SynologyConfig;
import com.jaxson.coloros.synologynas.SynologyConfigSource;
import com.jaxson.coloros.synologynas.dsm.DsmApiCatalog;
import com.jaxson.coloros.synologynas.dsm.DsmGateway;
import com.jaxson.coloros.synologynas.dsm.RemoteMedia;

import org.junit.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public final class RemoteGalleryRepositoryTest {
    private static final SynologyConfig CONFIG = new SynologyConfig(
            "https://nas.example.com:5001",
            "user",
            "password",
            "",
            "/home/Photos",
            "DS920+"
    );

    @Test
    public void returnsStoredAndLiveDeviceModel() throws IOException {
        FakeDsmGateway gateway = new FakeDsmGateway(List.of());
        RemoteGalleryRepository repository = repository(gateway);

        assertEquals("DS920+", repository.configuredDeviceModel());
        assertEquals("DS220+", repository.probeDeviceModel());
    }

    @Test
    public void buildSnapshotCreatesAllAlbumAndDirectoryAlbums() {
        RemoteGalleryRepository.Snapshot snapshot = RemoteGalleryRepository.buildSnapshot(
                CONFIG,
                "fingerprint",
                123L,
                List.of(
                        media("/home/Photos/root.jpg", 300L),
                        media("/home/Photos/Trips/Paris/second.jpg", 200L),
                        media("/home/Photos/Trips/Paris/first.jpg", 400L),
                        media("/home/Photos/Family/family.jpg", 100L)
                )
        );

        assertEquals(List.of("ALL_PROJECT", "Family", "Trips/Paris"),
                snapshot.albums.stream().map(RemoteAlbum::name).toList());
        assertEquals(4, snapshot.albums.get(0).imageCount());
        assertEquals(1, snapshot.albums.get(1).imageCount());
        assertEquals(2, snapshot.albums.get(2).imageCount());
        assertEquals(List.of("first.jpg", "second.jpg"),
                snapshot.photosByAlbumId.get(snapshot.albums.get(2).id()).stream()
                        .map(photo -> photo.media().name())
                        .toList());
    }

    @Test
    public void buildSnapshotUsesStablePositiveNumericIds() {
        List<RemoteMedia> input = List.of(
                media("/home/Photos/A/one.jpg", 100L),
                media("/home/Photos/B/two.jpg", 200L)
        );
        RemoteGalleryRepository.Snapshot first = RemoteGalleryRepository.buildSnapshot(
                CONFIG,
                "first",
                1L,
                input
        );
        RemoteGalleryRepository.Snapshot second = RemoteGalleryRepository.buildSnapshot(
                CONFIG,
                "second",
                2L,
                List.of(input.get(1), input.get(0))
        );

        for (RemotePhoto photo : first.allPhotos) {
            assertTrue(photo.id().matches("[1-9][0-9]*"));
            assertTrue(photo.galleryId() > 0);
            RemotePhoto matching = second.allPhotos.stream()
                    .filter(candidate -> candidate.media().remotePath()
                            .equals(photo.media().remotePath()))
                    .findFirst()
                    .orElseThrow();
            assertEquals(photo.id(), matching.id());
            assertEquals(photo.galleryId(), matching.galleryId());
        }
        assertNotEquals(first.allPhotos.get(0).id(), first.allPhotos.get(1).id());
    }

    @Test
    public void pageReturnsRequestedWindowAndEmptyTail() {
        List<Integer> values = List.of(10, 20, 30, 40);

        assertEquals(List.of(20, 30), RemoteGalleryRepository.page(values, 1, 2));
        assertEquals(List.of(40), RemoteGalleryRepository.page(values, 3, 2));
        assertEquals(List.of(), RemoteGalleryRepository.page(values, 4, 2));
    }

    @Test
    public void pageRejectsInvalidArguments() {
        assertInvalidPage(-1, 1);
        assertInvalidPage(0, 0);
    }

    @Test
    public void deletesMappedRemoteMediaAndReloadsInvalidatedSnapshot() throws IOException {
        FakeDsmGateway gateway = new FakeDsmGateway(List.of(
                media("/home/Photos/one.jpg", 200L),
                media("/home/Photos/two.jpg", 100L)
        ));
        RemoteGalleryRepository repository = repository(gateway);
        List<RemotePhoto> initial = repository.listPhotos("0", 0, 10);

        assertTrue(repository.deletePhotos(initial.stream().map(RemotePhoto::id).toList()));
        assertEquals(
                List.of("/home/Photos/one.jpg", "/home/Photos/two.jpg"),
                gateway.deletedMedia.stream().map(RemoteMedia::remotePath).toList()
        );
        assertEquals(List.of(), repository.listPhotos("0", 0, 10));
        assertEquals(2, gateway.listInvocations);
    }

    @Test
    public void preservesSnapshotAndSurfacesDsmDeleteFailure() throws IOException {
        FakeDsmGateway gateway = new FakeDsmGateway(List.of(
                media("/home/Photos/one.jpg", 100L)
        ));
        gateway.deleteFailure = new IOException("delete failed");
        RemoteGalleryRepository repository = repository(gateway);
        RemotePhoto photo = repository.listPhotos("0", 0, 10).get(0);

        assertThrows(
                IOException.class,
                () -> repository.deletePhotos(List.of(photo.id()))
        );
        assertEquals(1, repository.listPhotos("0", 0, 10).size());
        assertEquals(1, gateway.listInvocations);
    }

    private static void assertInvalidPage(int offset, int limit) {
        try {
            RemoteGalleryRepository.page(List.of(1), offset, limit);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("分页参数无效"));
        }
    }

    private static RemoteMedia media(String path, long modifiedSeconds) {
        return new RemoteMedia(
                path,
                path.substring(path.lastIndexOf('/') + 1),
                1024L,
                modifiedSeconds,
                "image/jpeg"
        );
    }

    private static RemoteGalleryRepository repository(FakeDsmGateway gateway) {
        SynologyConfigSource configSource = new SynologyConfigSource() {
            @Override
            public boolean hasConfig() {
                return true;
            }

            @Override
            public SynologyConfig load() {
                return CONFIG;
            }
        };
        return new RemoteGalleryRepository(configSource, ignored -> gateway);
    }

    private static final class FakeDsmGateway implements DsmGateway {
        private final List<RemoteMedia> inventory;
        private List<RemoteMedia> deletedMedia = List.of();
        private IOException deleteFailure;
        private int listInvocations;

        private FakeDsmGateway(List<RemoteMedia> inventory) {
            this.inventory = new ArrayList<>(inventory);
        }

        @Override
        public DsmApiCatalog discoverApis() {
            return null;
        }

        @Override
        public String login(DsmApiCatalog catalog) {
            return "sid";
        }

        @Override
        public String getDeviceModel(DsmApiCatalog catalog, String sid) {
            return "DS220+";
        }

        @Override
        public List<RemoteMedia> listImages(DsmApiCatalog catalog, String sid) {
            listInvocations++;
            return List.copyOf(inventory);
        }

        @Override
        public void download(
                DsmApiCatalog catalog,
                String sid,
                RemoteMedia media,
                OutputStream output
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void downloadThumbnail(
                DsmApiCatalog catalog,
                String sid,
                RemoteMedia media,
                String size,
                OutputStream output
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete(
                DsmApiCatalog catalog,
                String sid,
                List<RemoteMedia> media
        ) throws IOException {
            if (deleteFailure != null) {
                throw deleteFailure;
            }
            deletedMedia = List.copyOf(media);
            inventory.removeAll(media);
        }
    }
}
