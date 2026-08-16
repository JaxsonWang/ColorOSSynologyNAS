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
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class RemoteGalleryRepository implements RemoteGalleryDataSource {
    // 限制远端清单快照在相册进程内最多复用一分钟
    private static final long INVENTORY_TTL_MILLIS = 60_000L;

    // 提供相册进程当前可见的群晖配置
    private final SynologyConfigSource configSource;
    // 按配置创建 DSM 网关，生产使用 DsmClient，测试可注入记录实现
    private final Function<SynologyConfig, DsmGateway> clientFactory;

    // 缓存最近成功读取并完成映射的远端图库清单
    private Snapshot snapshot;
    // 缓存与当前配置指纹严格绑定的 DSM API、SID 和客户端
    private Session session;

    // 使用生产 DSM 客户端创建远端图库仓储
    public RemoteGalleryRepository(SynologyConfigSource configSource /* 群晖配置来源 */) {
        this(configSource, DsmClient::new);
    }

    // 使用可注入 DSM 网关创建远端图库仓储，供同包测试验证数据路径
    RemoteGalleryRepository(
            SynologyConfigSource configSource, // 群晖配置来源
            Function<SynologyConfig, DsmGateway> clientFactory // DSM 网关工厂
    ) {
        this.configSource = configSource;
        this.clientFactory = clientFactory;
    }

    @Override
    // 返回相册进程当前是否已经取得完整群晖配置
    public boolean isConfigured() {
        return configSource.hasConfig();
    }

    @Override
    // 从已发布配置读取上次连接确认的 NAS 型号，不执行网络请求
    public synchronized String configuredDeviceModel() throws IOException {
        return loadConfig().deviceModel();
    }

    @Override
    // 复用当前 DSM 会话实时读取 NAS 型号
    public synchronized String probeDeviceModel() throws IOException {
        // 当前相册进程可见的完整群晖配置
        SynologyConfig config = loadConfig();
        // 与当前配置指纹绑定的已认证 DSM 会话
        Session activeSession = requireSession(config, fingerprint(config));
        return activeSession.client.getDeviceModel(activeSession.catalog, activeSession.sid);
    }

    @Override
    // 从有效清单快照分页返回远端相册
    public synchronized List<RemoteAlbum> listAlbums(
            int offset, // ColorOS 请求的相册起始偏移
            int limit // ColorOS 请求的相册最大数量
    ) throws IOException {
        return page(requireSnapshot().albums, offset, limit);
    }

    @Override
    // 按稳定相册标识读取一个远端相册
    public synchronized RemoteAlbum getAlbum(String albumId /* 远端相册稳定标识 */)
            throws IOException {
        // 当前快照中与稳定标识对应的相册
        RemoteAlbum album = requireSnapshot().albumsById.get(albumId);
        if (album == null) {
            throw new DsmException("群晖相册不存在: " + albumId);
        }
        return album;
    }

    @Override
    // 从有效清单快照分页返回指定相册的远端照片
    public synchronized List<RemotePhoto> listPhotos(
            String albumId, // 远端相册稳定标识
            int offset, // ColorOS 请求的照片起始偏移
            int limit // ColorOS 请求的照片最大数量
    ) throws IOException {
        // 当前快照中属于目标相册的有序照片列表
        List<RemotePhoto> photos = requireSnapshot().photosByAlbumId.get(albumId);
        if (photos == null) {
            throw new DsmException("群晖相册不存在: " + albumId);
        }
        return page(photos, offset, limit);
    }

    @Override
    // 将指定远端照片的缩略图直接写入调用方输出流
    public void downloadThumbnail(
            String photoId, // 远端照片稳定标识
            String size, // 群晖客户端支持的缩略图尺寸标识
            OutputStream output // 接收缩略图字节的 ColorOS 调用链输出流
    ) throws IOException {
        // 当前快照中与稳定标识对应的远端照片
        RemotePhoto photo;
        // 与当前配置指纹绑定的已认证 DSM 会话
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
    // 将指定远端照片原文件直接写入 ColorOS 回调输出流
    public void downloadOriginal(
            String photoId, // 远端照片稳定标识
            OutputStream output // 接收原图字节的 ColorOS 回调输出流
    ) throws IOException {
        // 当前快照中与稳定标识对应的远端照片
        RemotePhoto photo;
        // 与当前配置指纹绑定的已认证 DSM 会话
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
    // 删除已映射的远端照片，并仅在 DSM 成功后失效清单缓存
    public boolean deletePhotos(List<String> photoIds /* 待删除的远端照片标识 */)
            throws IOException {
        if (photoIds.isEmpty()) {
            throw new IllegalArgumentException("待删除的群晖照片为空");
        }

        // 按调用顺序保存需要交给 DSM Delete API 的远端媒体
        List<RemoteMedia> media = new ArrayList<>(photoIds.size());
        // 与当前配置指纹绑定的已认证 DSM 会话
        Session activeSession;
        synchronized (this) {
            // 删除前用于校验全部照片标识的当前清单快照
            Snapshot activeSnapshot = requireSnapshot();
            for (String photoId : photoIds) { // 当前需要映射到 DSM 路径的照片标识
                // 当前快照中与删除标识对应的远端照片
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

    // 从有效清单快照解析指定照片，不存在时暴露明确 DSM 错误
    private RemotePhoto requirePhoto(String photoId /* 远端照片稳定标识 */)
            throws IOException {
        // 当前快照中与稳定标识对应的远端照片
        RemotePhoto photo = requireSnapshot().photosById.get(photoId);
        if (photo == null) {
            throw new DsmException("群晖照片不存在: " + photoId);
        }
        return photo;
    }

    // 复用未过期快照，否则从 DSM 全量清单构建唯一的新快照
    private Snapshot requireSnapshot() throws IOException {
        // 当前相册进程可见的完整群晖配置
        SynologyConfig config = loadConfig();
        // 绑定配置内容且不会写入日志的内存指纹
        String fingerprint = fingerprint(config);
        // 仅用于判断已有快照 TTL 的请求开始时间
        long now = System.currentTimeMillis();
        if (snapshot != null
                && snapshot.configFingerprint.equals(fingerprint)
                && now - snapshot.loadedAtMillis < INVENTORY_TTL_MILLIS) {
            return snapshot;
        }

        // 与当前配置指纹绑定的已认证 DSM 会话
        Session activeSession = requireSession(config, fingerprint);
        // DSM 本次全量递归扫描返回的图片清单
        List<RemoteMedia> media = activeSession.client.listImages(
                activeSession.catalog,
                activeSession.sid
        );
        snapshot = buildSnapshot(
                config,
                fingerprint,
                System.currentTimeMillis(),
                media
        );
        return snapshot;
    }

    // 按当前配置取得或创建已认证 DSM 会话
    private Session requireSession() throws IOException {
        // 当前相册进程可见的完整群晖配置
        SynologyConfig config = loadConfig();
        return requireSession(config, fingerprint(config));
    }

    // 仅复用配置指纹完全一致的会话，否则重新发现 API 并登录
    private Session requireSession(
            SynologyConfig config, // 当前相册进程可见的完整群晖配置
            String fingerprint // 与该配置严格对应的内存指纹
    ) throws IOException {
        if (session != null && session.configFingerprint.equals(fingerprint)) {
            return session;
        }
        // 按当前配置创建的 DSM 网关
        DsmGateway client = clientFactory.apply(config);
        // 由 SYNO.API.Info 动态发现的 API 路径与版本目录
        DsmApiCatalog catalog = client.discoverApis();
        // 登录当前固定 session 后取得且仅保存在内存中的 SID
        String sid = client.login(catalog);
        session = new Session(fingerprint, client, catalog, sid);
        return session;
    }

    // 读取完整群晖配置，并将凭据来源异常映射为明确 DSM 错误
    private SynologyConfig loadConfig() throws IOException {
        try {
            // 相册进程从 RemotePreferences 读取的当前配置
            SynologyConfig config = configSource.load();
            if (config == null) {
                throw new DsmException("请先在群晖 NAS 模块中保存 DSM 配置");
            }
            return config;
        } catch (DsmException error /* 已具备明确业务语义的 DSM 异常 */) {
            throw error;
        } catch (GeneralSecurityException error /* 远端凭据解密异常 */) {
            throw new DsmException("群晖凭据读取失败", error);
        }
    }

    // 将 DSM 图片清单按唯一目录、稳定 ID 和时间顺序构造成只读快照
    static Snapshot buildSnapshot(
            SynologyConfig config, // 决定远端根目录的当前群晖配置
            String configFingerprint, // 绑定快照与配置的内存指纹
            long loadedAtMillis, // DSM 清单成功返回后的快照完成时间
            List<RemoteMedia> media // DSM 本次返回的全部远端图片
    ) {
        return RemoteGallerySnapshotFactory.build(
                config,
                configFingerprint,
                loadedAtMillis,
                media
        );
    }

    // 按 ColorOS offset/limit 合约返回不可变分页窗口
    static <T> List<T> page(
            List<T> items, // 已按业务顺序排列的完整列表
            int offset, // 从零开始的请求偏移
            int limit // 必须大于零的请求数量
    ) {
        if (offset < 0 || limit <= 0) {
            throw new IllegalArgumentException(
                    "分页参数无效: offset=" + offset + ", limit=" + limit
            );
        }
        if (offset >= items.size()) {
            return List.of();
        }
        return List.copyOf(items.subList(offset, Math.min(offset + limit, items.size())));
    }

    static final class Snapshot {
        // 绑定该快照与生成配置的内存指纹
        final String configFingerprint;
        // DSM 清单成功返回后记录的快照完成时间
        final long loadedAtMillis;
        // 按 ColorOS 展示顺序排列的不可变相册列表
        final List<RemoteAlbum> albums;
        // 按稳定相册标识索引的不可变相册映射
        final Map<String, RemoteAlbum> albumsById;
        // 按稳定照片标识索引的不可变照片映射
        final Map<String, RemotePhoto> photosById;
        // 按稳定相册标识索引的不可变照片列表
        final Map<String, List<RemotePhoto>> photosByAlbumId;

        // 保存一次完整且自洽的远端图库清单映射结果
        Snapshot(
                String configFingerprint, // 生成快照的配置指纹
                long loadedAtMillis, // 清单成功返回后的完成时间
                List<RemoteAlbum> albums, // 有序相册列表
                Map<String, RemoteAlbum> albumsById, // 相册标识索引
                Map<String, RemotePhoto> photosById, // 照片标识索引
                Map<String, List<RemotePhoto>> photosByAlbumId // 相册照片索引
        ) {
            this.configFingerprint = configFingerprint;
            this.loadedAtMillis = loadedAtMillis;
            this.albums = albums;
            this.albumsById = albumsById;
            this.photosById = photosById;
            this.photosByAlbumId = photosByAlbumId;
        }
    }

    private static final class Session {
        // 绑定会话与创建配置的内存指纹
        private final String configFingerprint;
        // 使用该配置创建的 DSM 网关
        private final DsmGateway client;
        // 由 DSM 动态发现的 API 路径与版本目录
        private final DsmApiCatalog catalog;
        // 仅保留在相册进程内存中的 DSM 登录 SID
        private final String sid;

        // 保存一次与配置严格绑定的已认证 DSM 会话
        private Session(
                String configFingerprint, // 创建会话的配置指纹
                DsmGateway client, // 当前 DSM 网关
                DsmApiCatalog catalog, // 动态发现的 API 目录
                String sid // 当前内存 SID
        ) {
            this.configFingerprint = configFingerprint;
            this.client = client;
            this.catalog = catalog;
            this.sid = sid;
        }
    }

    // 为配置生成仅在进程内比较的会话指纹，不将内容持久化或输出日志
    private static String fingerprint(SynologyConfig config /* 当前完整群晖配置 */) {
        return config.serverUrl() + '\u0000'
                + config.username() + '\u0000'
                + config.password() + '\u0000'
                + config.otp() + '\u0000'
                + config.remoteRoot();
    }
}
