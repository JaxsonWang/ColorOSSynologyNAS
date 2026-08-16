package com.jaxson.coloros.synologynas.gallery;

import com.jaxson.coloros.synologynas.SynologyConfig;
import com.jaxson.coloros.synologynas.SynologyConfigSource;
import com.jaxson.coloros.synologynas.dsm.DsmApiCatalog;
import com.jaxson.coloros.synologynas.dsm.DsmClient;
import com.jaxson.coloros.synologynas.dsm.DsmException;
import com.jaxson.coloros.synologynas.dsm.DsmGateway;
import com.jaxson.coloros.synologynas.dsm.RemoteMedia;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public final class RemoteGalleryRepository implements RemoteGalleryDataSource {
    private static final long INVENTORY_TTL_MILLIS = 60_000L;
    private static final String ALL_ALBUM_ID = "0";

    private final SynologyConfigSource configSource;
    private final Function<SynologyConfig, DsmGateway> clientFactory;

    private Snapshot snapshot;
    private Session session;

    public RemoteGalleryRepository(SynologyConfigSource configSource) {
        this(configSource, DsmClient::new);
    }

    RemoteGalleryRepository(
            SynologyConfigSource configSource,
            Function<SynologyConfig, DsmGateway> clientFactory
    ) {
        this.configSource = configSource;
        this.clientFactory = clientFactory;
    }

    @Override
    public boolean isConfigured() {
        return configSource.hasConfig();
    }

    @Override
    public synchronized String configuredDeviceModel() throws IOException {
        return loadConfig().deviceModel();
    }

    @Override
    public synchronized String probeDeviceModel() throws IOException {
        SynologyConfig config = loadConfig();
        Session activeSession = requireSession(config, fingerprint(config));
        return activeSession.client.getDeviceModel(activeSession.catalog, activeSession.sid);
    }

    @Override
    public synchronized List<RemoteAlbum> listAlbums(int offset, int limit) throws IOException {
        return page(requireSnapshot().albums, offset, limit);
    }

    @Override
    public synchronized RemoteAlbum getAlbum(String albumId) throws IOException {
        RemoteAlbum album = requireSnapshot().albumsById.get(albumId);
        if (album == null) {
            throw new DsmException("群晖相册不存在: " + albumId);
        }
        return album;
    }

    @Override
    public synchronized List<RemotePhoto> listPhotos(
            String albumId,
            int offset,
            int limit
    ) throws IOException {
        List<RemotePhoto> photos = requireSnapshot().photosByAlbumId.get(albumId);
        if (photos == null) {
            throw new DsmException("群晖相册不存在: " + albumId);
        }
        return page(photos, offset, limit);
    }

    @Override
    public synchronized int photoCount() throws IOException {
        return requireSnapshot().allPhotos.size();
    }

    @Override
    public void downloadThumbnail(String photoId, String size, OutputStream output)
            throws IOException {
        RemotePhoto photo;
        Session activeSession;
        synchronized (this) {
            photo = requirePhoto(photoId);
            activeSession = requireSession();
        }
        activeSession.client.downloadThumbnail(
                activeSession.catalog,
                activeSession.sid,
                photo.media(),
                size,
                output
        );
    }

    @Override
    public void downloadOriginal(String photoId, OutputStream output) throws IOException {
        RemotePhoto photo;
        Session activeSession;
        synchronized (this) {
            photo = requirePhoto(photoId);
            activeSession = requireSession();
        }
        activeSession.client.download(
                activeSession.catalog,
                activeSession.sid,
                photo.media(),
                output
        );
    }

    @Override
    public boolean deletePhotos(List<String> photoIds) throws IOException {
        if (photoIds.isEmpty()) {
            throw new IllegalArgumentException("待删除的群晖照片为空");
        }

        List<RemoteMedia> media = new ArrayList<>(photoIds.size());
        Session activeSession;
        synchronized (this) {
            Snapshot activeSnapshot = requireSnapshot();
            for (String photoId : photoIds) {
                RemotePhoto photo = activeSnapshot.photosById.get(photoId);
                if (photo == null) {
                    throw new DsmException("群晖照片不存在");
                }
                media.add(photo.media());
            }
            activeSession = requireSession();
        }

        activeSession.client.delete(activeSession.catalog, activeSession.sid, media);
        synchronized (this) {
            snapshot = null;
        }
        return true;
    }

    private RemotePhoto requirePhoto(String photoId) throws IOException {
        RemotePhoto photo = requireSnapshot().photosById.get(photoId);
        if (photo == null) {
            throw new DsmException("群晖照片不存在: " + photoId);
        }
        return photo;
    }

    private Snapshot requireSnapshot() throws IOException {
        SynologyConfig config = loadConfig();
        String fingerprint = fingerprint(config);
        long now = System.currentTimeMillis();
        if (snapshot != null
                && snapshot.configFingerprint.equals(fingerprint)
                && now - snapshot.loadedAtMillis < INVENTORY_TTL_MILLIS) {
            return snapshot;
        }

        Session activeSession = requireSession(config, fingerprint);
        List<RemoteMedia> media = activeSession.client.listImages(
                activeSession.catalog,
                activeSession.sid
        );
        snapshot = buildSnapshot(config, fingerprint, now, media);
        return snapshot;
    }

    private Session requireSession() throws IOException {
        SynologyConfig config = loadConfig();
        return requireSession(config, fingerprint(config));
    }

    private Session requireSession(SynologyConfig config, String fingerprint) throws IOException {
        if (session != null && session.configFingerprint.equals(fingerprint)) {
            return session;
        }
        DsmGateway client = clientFactory.apply(config);
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

    static Snapshot buildSnapshot(
            SynologyConfig config,
            String configFingerprint,
            long loadedAtMillis,
            List<RemoteMedia> media
    ) {
        List<RemoteMedia> sortedMedia = new ArrayList<>(media);
        sortedMedia.sort(Comparator
                .comparingLong(RemoteMedia::modifiedSeconds)
                .reversed()
                .thenComparing(RemoteMedia::remotePath));

        Set<String> usedPhotoIds = new LinkedHashSet<>();
        Set<Integer> usedGalleryPhotoIds = new LinkedHashSet<>();
        List<RemotePhoto> allPhotos = new ArrayList<>(sortedMedia.size());
        Map<String, RemotePhoto> photosById = new LinkedHashMap<>();
        Map<String, List<RemotePhoto>> photosByDirectory = new LinkedHashMap<>();

        for (RemoteMedia item : sortedMedia) {
            String photoId = uniqueLongId("photo:" + item.remotePath(), usedPhotoIds);
            int galleryId = uniqueIntId("photo:" + item.remotePath(), usedGalleryPhotoIds);
            RemotePhoto photo = new RemotePhoto(photoId, galleryId, item);
            allPhotos.add(photo);
            photosById.put(photoId, photo);

            String directory = relativeDirectory(config.remoteRoot(), item.remotePath());
            if (!directory.isEmpty()) {
                photosByDirectory.computeIfAbsent(directory, ignored -> new ArrayList<>())
                        .add(photo);
            }
        }

        List<RemoteAlbum> albums = new ArrayList<>();
        Map<String, RemoteAlbum> albumsById = new LinkedHashMap<>();
        Map<String, List<RemotePhoto>> photosByAlbumId = new LinkedHashMap<>();
        Set<String> usedAlbumIds = new LinkedHashSet<>();
        Set<Integer> usedGalleryAlbumIds = new LinkedHashSet<>();

        RemoteAlbum allAlbum = createAlbum(
                ALL_ALBUM_ID,
                uniqueIntId("album:all", usedGalleryAlbumIds),
                "ALL_PROJECT",
                allPhotos
        );
        albums.add(allAlbum);
        albumsById.put(allAlbum.id(), allAlbum);
        photosByAlbumId.put(allAlbum.id(), List.copyOf(allPhotos));

        List<String> directories = new ArrayList<>(photosByDirectory.keySet());
        directories.sort(String.CASE_INSENSITIVE_ORDER);
        for (String directory : directories) {
            String albumId = uniqueLongId("album:" + directory, usedAlbumIds);
            int galleryId = uniqueIntId("album:" + directory, usedGalleryAlbumIds);
            List<RemotePhoto> albumPhotos = photosByDirectory.get(directory);
            RemoteAlbum album = createAlbum(albumId, galleryId, directory, albumPhotos);
            albums.add(album);
            albumsById.put(album.id(), album);
            photosByAlbumId.put(album.id(), List.copyOf(albumPhotos));
        }

        return new Snapshot(
                configFingerprint,
                loadedAtMillis,
                List.copyOf(albums),
                Map.copyOf(albumsById),
                List.copyOf(allPhotos),
                Map.copyOf(photosById),
                Map.copyOf(photosByAlbumId)
        );
    }

    private static RemoteAlbum createAlbum(
            String id,
            int galleryId,
            String name,
            List<RemotePhoto> photos
    ) {
        RemotePhoto cover = photos.isEmpty() ? null : photos.get(0);
        long updateTime = photos.stream()
                .mapToLong(photo -> photo.media().modifiedSeconds() * 1_000L)
                .max()
                .orElse(0L);
        return new RemoteAlbum(
                id,
                galleryId,
                name,
                photos.size(),
                cover == null ? "0" : cover.id(),
                cover == null ? 0 : cover.galleryId(),
                updateTime
        );
    }

    private static String relativeDirectory(String root, String path) {
        String prefix = root.endsWith("/") ? root : root + "/";
        if (!path.startsWith(prefix)) {
            return "";
        }
        String relative = path.substring(prefix.length());
        int slash = relative.lastIndexOf('/');
        return slash <= 0 ? "" : relative.substring(0, slash);
    }

    private static String uniqueLongId(String value, Set<String> used) {
        long candidate = positiveLongDigest(value);
        String id = Long.toString(candidate);
        while (!used.add(id)) {
            candidate = candidate == Long.MAX_VALUE ? 1L : candidate + 1L;
            id = Long.toString(candidate);
        }
        return id;
    }

    private static int uniqueIntId(String value, Set<Integer> used) {
        int candidate = positiveIntDigest(value);
        while (!used.add(candidate)) {
            candidate = candidate == Integer.MAX_VALUE ? 1 : candidate + 1;
        }
        return candidate;
    }

    private static long positiveLongDigest(String value) {
        byte[] digest = sha256(value);
        long result = ByteBuffer.wrap(digest).getLong() & Long.MAX_VALUE;
        return result == 0L ? 1L : result;
    }

    private static int positiveIntDigest(String value) {
        byte[] digest = sha256(value);
        int result = ByteBuffer.wrap(digest).getInt() & Integer.MAX_VALUE;
        return result == 0 ? 1 : result;
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }

    private static String fingerprint(SynologyConfig config) {
        return config.serverUrl() + '\u0000'
                + config.username() + '\u0000'
                + config.password() + '\u0000'
                + config.otp() + '\u0000'
                + config.remoteRoot();
    }

    static <T> List<T> page(List<T> items, int offset, int limit) {
        if (offset < 0 || limit <= 0) {
            throw new IllegalArgumentException("分页参数无效: offset=" + offset + ", limit=" + limit);
        }
        if (offset >= items.size()) {
            return List.of();
        }
        return List.copyOf(items.subList(offset, Math.min(offset + limit, items.size())));
    }

    static final class Snapshot {
        final String configFingerprint;
        final long loadedAtMillis;
        final List<RemoteAlbum> albums;
        final Map<String, RemoteAlbum> albumsById;
        final List<RemotePhoto> allPhotos;
        final Map<String, RemotePhoto> photosById;
        final Map<String, List<RemotePhoto>> photosByAlbumId;

        Snapshot(
                String configFingerprint,
                long loadedAtMillis,
                List<RemoteAlbum> albums,
                Map<String, RemoteAlbum> albumsById,
                List<RemotePhoto> allPhotos,
                Map<String, RemotePhoto> photosById,
                Map<String, List<RemotePhoto>> photosByAlbumId
        ) {
            this.configFingerprint = configFingerprint;
            this.loadedAtMillis = loadedAtMillis;
            this.albums = albums;
            this.albumsById = albumsById;
            this.allPhotos = allPhotos;
            this.photosById = photosById;
            this.photosByAlbumId = photosByAlbumId;
        }
    }

    private static final class Session {
        private final String configFingerprint;
        private final DsmGateway client;
        private final DsmApiCatalog catalog;
        private final String sid;

        private Session(
                String configFingerprint,
                DsmGateway client,
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
