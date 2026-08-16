package com.jaxson.coloros.synologynas.gallery;

import com.jaxson.coloros.synologynas.dsm.RemoteMedia;
import com.oplus.aiunit.vision.b3q;
import com.oplus.aiunit.vision.dpk;
import com.oplus.aiunit.vision.jjq;
import com.oplus.aiunit.vision.uhq;
import com.oplus.aiunit.vision.wac;
import com.oplus.aiunit.vision.z8g;
import com.oplus.gallery.business_lib.nas.NasPhotoInfo;
import com.oplus.gallery.business_lib.nas.ThumbnailSize;

import org.junit.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

// 验证当前 dpk 完整 JVM 合同、参数位置与双 Provider 分流
public final class ColorOsNasProviderContractTest {
    @Test
    // 验证 a/l/t/p/x/w 按当前参数位置进入群晖路径并返回对应私有类型
    public void routesCurrentGalleryContractToSynologyWithExactArguments() throws Exception {
        // 记录六类群晖浏览操作参数的远端数据源
        RecordingDataSource dataSource = new RecordingDataSource();
        // 按完整 dpk 合同创建待测群晖 Provider
        dpk proxy = proxy(dataSource, new RecordingOriginalProvider());

        // dpk.a 返回的单相册私有 DTO
        b3q album = proxy.a(GalleryContract.DEVICE_ID, "album-1");
        // dpk.l 返回的分页相册私有 DTO 列表
        List<?> albums = proxy.l(4, 5, GalleryContract.DEVICE_ID);
        // dpk.t 返回的分页照片私有 DTO 列表
        List<?> photos = proxy.t(6, 7, GalleryContract.DEVICE_ID, "album-1");
        // 记录原图回调实际收到字节和完成标记的夹具
        uhq callback = new uhq();
        // dpk.w 返回的当前 ColorOS 原图下载句柄
        z8g handle = proxy.w(GalleryContract.DEVICE_ID, "photo-1", callback);

        assertEquals("album-1", dataSource.requestedAlbumId);
        assertEquals(31, album.galleryId);
        assertEquals(4, dataSource.albumOffset);
        assertEquals(5, dataSource.albumLimit);
        assertTrue(albums.get(0) instanceof b3q);
        assertEquals("album-1", dataSource.photoAlbumId);
        assertEquals(6, dataSource.photoOffset);
        assertEquals(7, dataSource.photoLimit);
        assertTrue(photos.get(0) instanceof NasPhotoInfo);
        assertTrue(proxy.p(GalleryContract.DEVICE_ID, List.of("photo-1")));
        assertEquals(List.of("photo-1"), dataSource.deletedPhotoIds);
        assertArrayEquals(
                new byte[]{4, 5, 6},
                proxy.x(
                        GalleryContract.DEVICE_ID,
                        "photo-1",
                        ThumbnailSize.THUMBNAIL_SIZE_L
                )
        );
        assertEquals(GalleryContract.THUMBNAIL_LARGE, dataSource.thumbnailSize);
        assertEquals("photo-1", dataSource.thumbnailPhotoId);
        assertArrayEquals(new byte[]{1, 2, 3}, callback.bytes());
        assertTrue(callback.completed());
        assertEquals(GalleryContract.DEVICE_ID, handle.deviceUserId);
        assertEquals("photo-1", handle.photoId);
        // 从合成句柄消费由精确 trySend ABI 写入的唯一完成进度
        wac progress = (wac) handle.channel.receive(null);
        assertEquals("photo-1", progress.photoId);
        assertEquals(3L, progress.downloadedSize);
        assertEquals(3L, progress.totalSize);
        assertEquals(100, progress.progress);
        assertTrue(progress.completed);
        assertTrue(handle.channel.isClosedForReceive());
    }

    @Test
    // 验证 a/l/t/p/x/w 的非群晖请求按原参数和返回对象转发原 Provider
    public void forwardsCurrentGalleryContractToOriginalProviderUnchanged() throws Exception {
        // 为每类方法返回唯一对象并记录全部参数的原 Provider
        RecordingOriginalProvider original = new RecordingOriginalProvider();
        // 不应收到非群晖请求的远端数据源
        RecordingDataSource dataSource = new RecordingDataSource();
        // 同时持有群晖与原 Provider 的待测代理
        dpk proxy = proxy(dataSource, original);
        // 传给原图下载方法且必须保持对象身份的回调
        uhq callback = new uhq();

        assertSame(original.album, proxy.a("other-nas", "other-album"));
        assertSame(original.albums, proxy.l(8, 9, "other-nas"));
        assertSame(original.photos, proxy.t(10, 11, "other-nas", "other-album"));
        assertFalse(proxy.p("other-nas", List.of("other-photo")));
        assertSame(
                original.thumbnail,
                proxy.x("other-nas", "other-photo", ThumbnailSize.THUMBNAIL_SIZE_S)
        );
        assertSame(original.handle, proxy.w("other-nas", "other-photo", callback));

        assertEquals("other-album", original.albumId);
        assertEquals(8, original.albumOffset);
        assertEquals(9, original.albumLimit);
        assertEquals(10, original.photoOffset);
        assertEquals(11, original.photoLimit);
        assertEquals("other-album", original.photoAlbumId);
        assertEquals(List.of("other-photo"), original.deletedPhotoIds);
        assertEquals(ThumbnailSize.THUMBNAIL_SIZE_S, original.thumbnailSize);
        assertSame(callback, original.callback);
        assertEquals(0, dataSource.synologyInvocations);
    }

    @Test
    // 验证未知缩略图枚举不再静默映射为小图
    public void rejectsUnknownThumbnailSizeInsteadOfUsingSmallDefault() {
        assertThrows(
                IllegalArgumentException.class,
                () /* 触发未知缩略图枚举映射 */ ->
                        ColorOsNasDownloadAdapter.thumbnailSize(UnknownThumbnailSize.UNKNOWN)
        );
    }

    @Test
    // 验证仅图片的群晖 Provider 不用空值伪造飞牛安装地址或视频能力
    public void rejectsUnsupportedFeiniuAndVideoOperations() throws Exception {
        // 不应因不支持操作收到任何远端图片调用的数据源
        RecordingDataSource dataSource = new RecordingDataSource();
        // 按完整 dpk 合同创建待测群晖 Provider
        dpk proxy = proxy(dataSource, new RecordingOriginalProvider());

        assertThrows(
                UnsupportedOperationException.class,
                () /* 请求群晖未暴露的视频总字节数 */ ->
                        proxy.g(GalleryContract.DEVICE_ID, "video-1", null)
        );
        assertThrows(
                UnsupportedOperationException.class,
                () /* 请求仅属于飞牛应用的安装地址 */ ->
                        proxy.h(GalleryContract.DEVICE_ID)
        );
        assertThrows(
                UnsupportedOperationException.class,
                () /* 请求群晖未暴露的视频字节区间 */ ->
                        proxy.v(GalleryContract.DEVICE_ID, "video-1", 0L, 99L, null)
        );
        assertEquals(0, dataSource.synologyInvocations);
    }

    @Test
    // 验证群晖不进入飞牛连接状态机且非群晖请求仍原样转发
    public void isolatesFeiniuLifecycleAndUnsupportedOperationsByDevice() throws Exception {
        // 记录群晖图片路径是否被连接状态方法错误触发的数据源
        RecordingDataSource dataSource = new RecordingDataSource();
        // 记录非群晖方法参数和返回值的原 Provider
        RecordingOriginalProvider original = new RecordingOriginalProvider();
        // 同时持有群晖和原飞牛行为的待测代理
        dpk proxy = proxy(dataSource, original);

        proxy.j(GalleryContract.DEVICE_ID);
        proxy.q(GalleryContract.DEVICE_ID);
        proxy.u(GalleryContract.DEVICE_ID);
        assertEquals(0, dataSource.synologyInvocations);

        assertEquals(73L, proxy.g("other-nas", "video-1", null));
        assertEquals("https://feiniu.example/install", proxy.h("other-nas"));
        assertArrayEquals(
                new byte[]{7, 3},
                (byte[]) proxy.v("other-nas", "video-1", 4L, 8L, null)
        );
        proxy.j("other-nas");
        proxy.q("other-nas");
        proxy.u("other-nas");
        assertEquals(List.of("j", "q", "u"), original.lifecycleCalls);
    }

    // 绑定记录数据源与原 Provider 创建完整 dpk 动态代理
    private dpk proxy(
            RecordingDataSource dataSource, // 接收群晖路径调用的数据源
            dpk original // 接收非群晖路径调用的原 Provider
    ) throws ReflectiveOperationException {
        return (dpk) ColorOsNasProviderProxy.create(
                new GalleryRemoteClient(dataSource),
                null,
                original,
                getClass().getClassLoader(),
                () /* 返回固定缓存照片数量 */ -> 0
        );
    }

    // 模拟当前 ThumbnailSize 合同之外的枚举值
    private enum UnknownThumbnailSize {
        UNKNOWN // 模拟当前合同之外的缩略图枚举值
    }

    // 记录群晖 Provider 浏览、下载与删除参数的数据源
    private static final class RecordingDataSource implements RemoteGalleryDataSource {
        // 记录进入群晖数据源的操作次数
        private int synologyInvocations;
        // 记录单相册请求标识
        private String requestedAlbumId;
        // 记录相册分页起始偏移
        private int albumOffset;
        // 记录相册分页最大数量
        private int albumLimit;
        // 记录照片分页目标相册
        private String photoAlbumId;
        // 记录照片分页起始偏移
        private int photoOffset;
        // 记录照片分页最大数量
        private int photoLimit;
        // 记录最近一次删除的照片标识
        private List<String> deletedPhotoIds = List.of();
        // 记录最近一次缩略图请求的照片标识
        private String thumbnailPhotoId;
        // 记录最近一次缩略图请求的尺寸
        private String thumbnailSize;

        @Override
        // 为 Provider 状态合同返回已配置
        public boolean isConfigured() {
            return true;
        }

        @Override
        // 返回测试配置中的 NAS 型号
        public String configuredDeviceModel() {
            return "DS920+";
        }

        @Override
        // 返回测试探测得到的 NAS 型号
        public String probeDeviceModel() {
            return "DS920+";
        }

        @Override
        // 记录相册分页参数并返回唯一相册
        public List<RemoteAlbum> listAlbums(
                int offset, // 请求的相册起始偏移
                int limit // 请求的相册最大数量
        ) {
            synologyInvocations++;
            albumOffset = offset;
            albumLimit = limit;
            return List.of(album());
        }

        @Override
        // 记录单相册标识并返回唯一相册
        public RemoteAlbum getAlbum(String albumId /* 请求的相册稳定标识 */) {
            synologyInvocations++;
            requestedAlbumId = albumId;
            return album();
        }

        @Override
        // 记录照片分页参数并返回唯一照片
        public List<RemotePhoto> listPhotos(
                String albumId, // 请求的相册稳定标识
                int offset, // 请求的照片起始偏移
                int limit // 请求的照片最大数量
        ) {
            synologyInvocations++;
            photoAlbumId = albumId;
            photoOffset = offset;
            photoLimit = limit;
            // 绑定固定 DSM 元数据的唯一远端照片
            RemotePhoto photo = new RemotePhoto(
                    "photo-1",
                    41,
                    new RemoteMedia(
                            "/home/Photos/album-1/IMG_1.jpg",
                            "IMG_1.jpg",
                            3L,
                            5L,
                            "image/jpeg"
                    )
            );
            return List.of(photo);
        }

        @Override
        // 记录缩略图参数并写入固定响应字节
        public void downloadThumbnail(
                String photoId, // 请求的照片稳定标识
                String size, // 请求的缩略图尺寸
                OutputStream output // 接收缩略图字节的输出流
        ) throws IOException {
            synologyInvocations++;
            thumbnailPhotoId = photoId;
            thumbnailSize = size;
            output.write(new byte[]{4, 5, 6});
        }

        @Override
        // 向原图回调路径写入固定响应字节
        public void downloadOriginal(
                String photoId, // 请求的照片稳定标识
                OutputStream output // 接收原图字节的输出流
        ) throws IOException {
            synologyInvocations++;
            output.write(new byte[]{1, 2, 3});
        }

        @Override
        // 记录删除标识并返回成功
        public boolean deletePhotos(List<String> photoIds /* 请求删除的照片标识 */) {
            synologyInvocations++;
            deletedPhotoIds = List.copyOf(photoIds);
            return true;
        }

        // 创建供单相册和相册列表共同返回的固定模型
        private static RemoteAlbum album() {
            return new RemoteAlbum("album-1", 31, "Trips", 1, "photo-1", 41, 5_000L);
        }
    }

    // 记录非群晖请求原样转发参数与返回对象的 Provider
    private static final class RecordingOriginalProvider implements dpk {
        // 保存单相册转发应返回的唯一对象
        private final b3q album = new b3q(
                com.oplus.gallery.business_lib.nas.NasProvider.FEINIU,
                1,
                "original-album",
                "Original",
                "other-nas",
                0,
                0,
                "",
                0,
                0L
        );
        // 保存相册列表转发应返回的唯一对象
        private final List<Object> albums = List.of(new Object());
        // 保存照片列表转发应返回的唯一对象
        private final List<Object> photos = List.of(new Object());
        // 保存缩略图转发应返回的唯一数组
        private final byte[] thumbnail = {9, 8, 7};
        // 保存原图下载转发应返回的唯一句柄
        private final z8g handle = new z8g(
                null,
                new AtomicReference<>(),
                null,
                null,
                "other-nas",
                "other-photo"
        );
        // 记录单相册请求标识
        private String albumId;
        // 记录相册分页起始偏移
        private int albumOffset;
        // 记录相册分页最大数量
        private int albumLimit;
        // 记录照片分页起始偏移
        private int photoOffset;
        // 记录照片分页最大数量
        private int photoLimit;
        // 记录照片分页目标相册
        private String photoAlbumId;
        // 记录删除照片标识
        private List<String> deletedPhotoIds;
        // 记录缩略图尺寸
        private ThumbnailSize thumbnailSize;
        // 记录原图下载回调对象
        private uhq callback;
        // 按调用顺序记录飞牛连接状态方法
        private final List<String> lifecycleCalls = new java.util.ArrayList<>();

        @Override
        // 记录并返回原 Provider 单相册结果
        public b3q a(
                String deviceUserId, // 原 Provider 收到的设备标识
                String albumId // 原 Provider 收到的相册标识
        ) {
            this.albumId = albumId;
            return album;
        }

        @Override
        // 记录并返回原 Provider 相册分页结果
        public List<?> l(
                int offset, // 原 Provider 收到的起始偏移
                int limit, // 原 Provider 收到的最大数量
                String deviceUserId // 原 Provider 收到的设备标识
        ) {
            albumOffset = offset;
            albumLimit = limit;
            return albums;
        }

        @Override
        // 记录并返回原 Provider 照片分页结果
        public List<?> t(
                int offset, // 原 Provider 收到的起始偏移
                int limit, // 原 Provider 收到的最大数量
                String deviceUserId, // 原 Provider 收到的设备标识
                String albumId // 原 Provider 收到的相册标识
        ) {
            photoOffset = offset;
            photoLimit = limit;
            photoAlbumId = albumId;
            return photos;
        }

        @Override
        // 记录原 Provider 删除参数并返回固定失败标记
        public boolean p(
                String deviceUserId, // 原 Provider 收到的设备标识
                List<String> photoIds // 原 Provider 收到的照片标识
        ) {
            deletedPhotoIds = photoIds;
            return false;
        }

        @Override
        // 记录并返回原 Provider 缩略图结果
        public byte[] x(
                String deviceUserId, // 原 Provider 收到的设备标识
                String photoId, // 原 Provider 收到的照片标识
                ThumbnailSize size // 原 Provider 收到的缩略图尺寸
        ) {
            thumbnailSize = size;
            return thumbnail;
        }

        @Override
        // 记录并返回原 Provider 原图下载句柄
        public z8g w(
                String deviceUserId, // 原 Provider 收到的设备标识
                String photoId, // 原 Provider 收到的照片标识
                uhq callback // 原 Provider 收到的原图回调
        ) {
            this.callback = callback;
            return handle;
        }

        @Override
        // 返回固定视频总字节数并保留原参数转发路径
        public Object g(
                String deviceUserId, // 原 Provider 收到的设备标识
                String resourceId, // 原 Provider 收到的视频标识
                kotlin.coroutines.jvm.internal.ContinuationImpl continuation // 协程续体
        ) {
            return 73L;
        }

        @Override
        // 返回固定飞牛应用安装地址
        public String h(String deviceUserId /* 原 Provider 收到的设备标识 */) {
            return "https://feiniu.example/install";
        }

        @Override
        // 记录原 Provider 首个连接状态调用
        public void j(String deviceUserId /* 原 Provider 收到的设备标识 */) {
            lifecycleCalls.add("j");
        }

        @Override
        // 记录原 Provider第二个连接状态调用
        public void q(String deviceUserId /* 原 Provider 收到的设备标识 */) {
            lifecycleCalls.add("q");
        }

        @Override
        // 记录原 Provider 断开连接调用
        public void u(String deviceUserId /* 原 Provider 收到的设备标识 */) {
            lifecycleCalls.add("u");
        }

        @Override
        // 返回固定视频字节区间并保留原参数转发路径
        public Object v(
                String deviceUserId, // 原 Provider 收到的设备标识
                String videoId, // 原 Provider 收到的视频标识
                long startByte, // 原 Provider 收到的字节区间起点
                long endByte, // 原 Provider 收到的字节区间终点
                kotlin.coroutines.jvm.internal.ContinuationImpl continuation // 协程续体
        ) {
            return new byte[]{7, 3};
        }

        @Override
        // 本测试不使用原 Provider 图库统计
        public jjq o(String deviceUserId /* 原 Provider 收到的设备标识 */) {
            return null;
        }
    }
}
