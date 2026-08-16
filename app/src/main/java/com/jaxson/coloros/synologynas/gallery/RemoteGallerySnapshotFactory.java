package com.jaxson.coloros.synologynas.gallery;

import com.jaxson.coloros.synologynas.SynologyConfig;
import com.jaxson.coloros.synologynas.dsm.RemoteMedia;

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

// 从 DSM 图片清单构造确定性相册和照片快照
final class RemoteGallerySnapshotFactory {
    // ColorOS 使用固定相册标识 0 表达聚合全部远端照片的项目
    private static final String ALL_ALBUM_ID = "0";

    // 禁止实例化只负责确定性清单映射的工厂类
    private RemoteGallerySnapshotFactory() {
    }

    // 将 DSM 图片清单映射为相册、照片及其稳定索引的不可变快照
    static RemoteGalleryRepository.Snapshot build(
            SynologyConfig config, // 决定远端根目录的当前群晖配置
            String configFingerprint, // 绑定快照与配置的内存指纹
            long loadedAtMillis, // DSM 清单成功返回后的快照完成时间
            List<RemoteMedia> media // DSM 本次返回的全部远端图片
    ) {
        // 按修改时间倒序和路径稳定次序排列的 DSM 图片副本
        List<RemoteMedia> sortedMedia = new ArrayList<>(media);
        sortedMedia.sort(Comparator
                .comparingLong(RemoteMedia::modifiedSeconds)
                .reversed()
                .thenComparing(RemoteMedia::remotePath));

        // 防止稳定 long 照片标识发生极低概率碰撞
        Set<String> usedPhotoIds = new LinkedHashSet<>();
        // 防止 ColorOS int 照片标识发生碰撞
        Set<Integer> usedGalleryPhotoIds = new LinkedHashSet<>();
        // 按修改时间倒序保存全部内部照片模型
        List<RemotePhoto> allPhotos = new ArrayList<>(sortedMedia.size());
        // 按稳定照片标识索引内部照片模型
        Map<String, RemotePhoto> photosById = new LinkedHashMap<>();
        // 按相对目录聚合内部照片模型
        Map<String, List<RemotePhoto>> photosByDirectory = new LinkedHashMap<>();

        for (RemoteMedia item : sortedMedia) { // 当前需要映射的 DSM 图片
            // 从完整远端路径生成且解决碰撞的稳定照片标识
            String photoId = uniqueLongId("photo:" + item.remotePath(), usedPhotoIds);
            // 从完整远端路径生成且解决碰撞的 ColorOS int 标识
            int galleryId = uniqueIntId("photo:" + item.remotePath(), usedGalleryPhotoIds);
            // 绑定两类稳定标识与 DSM 媒体元数据的内部照片模型
            RemotePhoto photo = new RemotePhoto(photoId, galleryId, item);
            allPhotos.add(photo);
            photosById.put(photoId, photo);

            // 图片相对于配置根目录的父目录
            String directory = relativeDirectory(config.remoteRoot(), item.remotePath());
            if (!directory.isEmpty()) {
                photosByDirectory.computeIfAbsent(
                        directory,
                        ignored /* 首次遇到该目录时创建照片容器 */ -> new ArrayList<>()
                ).add(photo);
            }
        }

        // 先保存 ALL_PROJECT，再按目录名排序保存普通相册
        List<RemoteAlbum> albums = new ArrayList<>();
        // 按稳定相册标识索引内部相册模型
        Map<String, RemoteAlbum> albumsById = new LinkedHashMap<>();
        // 按稳定相册标识索引不可变照片列表
        Map<String, List<RemotePhoto>> photosByAlbumId = new LinkedHashMap<>();
        // 防止稳定 long 相册标识发生极低概率碰撞
        Set<String> usedAlbumIds = new LinkedHashSet<>();
        // 防止 ColorOS int 相册标识发生碰撞
        Set<Integer> usedGalleryAlbumIds = new LinkedHashSet<>();

        // 聚合全部远端照片且标识固定为 0 的 ALL_PROJECT 相册
        RemoteAlbum allAlbum = createAlbum(
                ALL_ALBUM_ID,
                uniqueIntId("album:all", usedGalleryAlbumIds),
                "ALL_PROJECT",
                allPhotos
        );
        albums.add(allAlbum);
        albumsById.put(allAlbum.id(), allAlbum);
        photosByAlbumId.put(allAlbum.id(), List.copyOf(allPhotos));

        // 需要按大小写不敏感顺序创建相册的相对目录名
        List<String> directories = new ArrayList<>(photosByDirectory.keySet());
        directories.sort(String.CASE_INSENSITIVE_ORDER);
        for (String directory : directories) { // 当前需要创建相册的相对目录
            // 从相对目录生成且解决碰撞的稳定相册标识
            String albumId = uniqueLongId("album:" + directory, usedAlbumIds);
            // 从相对目录生成且解决碰撞的 ColorOS int 标识
            int galleryId = uniqueIntId("album:" + directory, usedGalleryAlbumIds);
            // 已按全局修改时间顺序聚合到该目录的照片
            List<RemotePhoto> albumPhotos = photosByDirectory.get(directory);
            // 绑定目录、封面、数量和更新时间的内部相册模型
            RemoteAlbum album = createAlbum(albumId, galleryId, directory, albumPhotos);
            albums.add(album);
            albumsById.put(album.id(), album);
            photosByAlbumId.put(album.id(), List.copyOf(albumPhotos));
        }

        return new RemoteGalleryRepository.Snapshot(
                configFingerprint,
                loadedAtMillis,
                List.copyOf(albums),
                Map.copyOf(albumsById),
                Map.copyOf(photosById),
                Map.copyOf(photosByAlbumId)
        );
    }

    // 从相册照片计算封面、数量和最近更新时间
    private static RemoteAlbum createAlbum(
            String id, // 远端相册的稳定 long 字符串标识
            int galleryId, // ColorOS DTO 使用的稳定正 int 标识
            String name, // ColorOS 展示的相对目录或 ALL_PROJECT 名称
            List<RemotePhoto> photos // 已按修改时间倒序排列的相册照片
    ) {
        // 修改时间最新且用于相册封面的首张照片
        RemotePhoto cover = photos.isEmpty() ? null : photos.get(0);
        // 相册全部照片中的最近修改时间，单位转换为毫秒
        long updateTime = photos.stream()
                .mapToLong(photo /* 当前参与更新时间计算的照片 */ ->
                        photo.media().modifiedSeconds() * 1_000L)
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

    // 返回图片相对于配置根目录的父目录，根目录图片不创建额外相册
    private static String relativeDirectory(
            String root, // 当前配置的远端图库根目录
            String path // DSM 图片完整远端路径
    ) {
        // 确保根目录匹配只接受完整路径段边界
        String prefix = root.endsWith("/") ? root : root + "/";
        if (!path.startsWith(prefix)) {
            return "";
        }
        // 去掉根目录前缀后的相对图片路径
        String relative = path.substring(prefix.length());
        // 相对图片路径最后一个目录分隔符位置
        int slash = relative.lastIndexOf('/');
        return slash <= 0 ? "" : relative.substring(0, slash);
    }

    // 生成稳定正 long 字符串标识，并在本快照内确定性解决碰撞
    private static String uniqueLongId(
            String value, // 参与稳定散列的业务键
            Set<String> used // 本快照已分配的 long 字符串标识
    ) {
        // 当前尝试分配的正 long 候选值
        long candidate = positiveLongDigest(value);
        // 当前候选值的十进制字符串形式
        String id = Long.toString(candidate);
        // 逐一递增直至占用一个尚未分配的正 long 标识
        while (!used.add(id)) {
            candidate = candidate == Long.MAX_VALUE ? 1L : candidate + 1L;
            id = Long.toString(candidate);
        }
        return id;
    }

    // 生成稳定正 int 标识，并在本快照内确定性解决碰撞
    private static int uniqueIntId(
            String value, // 参与稳定散列的业务键
            Set<Integer> used // 本快照已分配的正 int 标识
    ) {
        // 当前尝试分配的正 int 候选值
        int candidate = positiveIntDigest(value);
        // 逐一递增直至占用一个尚未分配的正 int 标识
        while (!used.add(candidate)) {
            candidate = candidate == Integer.MAX_VALUE ? 1 : candidate + 1;
        }
        return candidate;
    }

    // 从 SHA-256 前八字节生成非零正 long 标识
    private static long positiveLongDigest(String value /* 稳定业务键 */) {
        // 业务键对应的完整 SHA-256 摘要
        byte[] digest = sha256(value);
        // 清除符号位后的 long 摘要值
        long result = ByteBuffer.wrap(digest).getLong() & Long.MAX_VALUE;
        return result == 0L ? 1L : result;
    }

    // 从 SHA-256 前四字节生成非零正 int 标识
    private static int positiveIntDigest(String value /* 稳定业务键 */) {
        // 业务键对应的完整 SHA-256 摘要
        byte[] digest = sha256(value);
        // 清除符号位后的 int 摘要值
        int result = ByteBuffer.wrap(digest).getInt() & Integer.MAX_VALUE;
        return result == 0 ? 1 : result;
    }

    // 计算稳定业务键的 SHA-256 摘要
    private static byte[] sha256(String value /* 需要稳定散列的业务键 */) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException error /* Java 运行时缺少必需摘要算法 */) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }
}
