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
    // 验证原图分块和唯一完成标记按顺序写入 ColorOS 回调
    public void streamsOriginalBytesAndCompletionIntoGalleryCallback() throws IOException {
        // 使用固定三字节原图的数据源客户端
        GalleryRemoteClient client = new GalleryRemoteClient(new FakeDataSource());
        // 记录 ColorOS 回调收到的字节、次数和完成状态
        RecordingCallback callback = new RecordingCallback();

        // 客户端实际报告的原图写入字节数
        long bytes = client.streamOriginal("photo", callback);

        assertEquals(3L, bytes);
        assertArrayEquals(new byte[]{1, 2, 3}, callback.bytes());
        assertTrue(callback.completed);
        assertEquals(3, callback.invocations);
    }

    @Test
    // 验证缩略图只返回内存字节且不产生本地媒体文件
    public void returnsRemoteThumbnailWithoutWritingLocalMedia() throws IOException {
        // 使用固定缩略图响应的数据源客户端
        GalleryRemoteClient client = new GalleryRemoteClient(new FakeDataSource());

        assertArrayEquals(
                new byte[]{4, 5, 6},
                client.getThumbnail("photo", GalleryContract.THUMBNAIL_LARGE)
        );
    }

    @Test
    // 验证删除请求完整委托给远端图库数据源
    public void delegatesRemotePhotoDeletion() throws IOException {
        // 记录客户端最终交付的照片删除标识
        FakeDataSource dataSource = new FakeDataSource();
        // 使用记录数据源执行远端删除
        GalleryRemoteClient client = new GalleryRemoteClient(dataSource);

        assertTrue(client.deletePhotos(List.of("photo-1", "photo-2")));
        assertEquals(List.of("photo-1", "photo-2"), dataSource.deletedPhotoIds);
    }

    @Test
    // 验证保存型号与实时探测型号直接来自唯一数据源
    public void exposesStoredAndProbedDeviceModel() throws IOException {
        // 使用分别返回 DS920+ 和 DS220+ 的型号数据源
        GalleryRemoteClient client = new GalleryRemoteClient(new FakeDataSource());

        assertEquals("DS920+", client.configuredDeviceModel());
        assertEquals("DS220+", client.probeDeviceModel());
    }

    public static final class RecordingCallback {
        // 按回调顺序汇集收到的原图字节
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();

        // 记录最后一次回调是否携带完成标记
        private boolean completed;
        // 记录数据分块和完成通知的回调总次数
        private int invocations;

        // 模拟 ColorOS 双参数原图回调并记录每次调用
        public void invoke(
                byte[] bytes, // 本次收到的原图字节分块
                boolean completed // 本次是否为完成通知
        ) throws IOException {
            invocations++;
            output.write(bytes);
            this.completed = completed;
        }

        // 返回按回调顺序汇集的全部原图字节
        private byte[] bytes() {
            return output.toByteArray();
        }
    }

    private static final class FakeDataSource implements RemoteGalleryDataSource {
        // 记录最近一次远端删除请求的照片标识
        private List<String> deletedPhotoIds = List.of();

        @Override
        // 为客户端配置状态测试返回已配置
        public boolean isConfigured() {
            return true;
        }

        @Override
        // 返回配置中保存的 NAS 型号
        public String configuredDeviceModel() {
            return "DS920+";
        }

        @Override
        // 返回实时 DSM 探测得到的 NAS 型号
        public String probeDeviceModel() {
            return "DS220+";
        }

        @Override
        // 本测试数据源不提供远端相册内容
        public List<RemoteAlbum> listAlbums(
                int offset, // 请求的相册起始偏移
                int limit // 请求的相册最大数量
        ) {
            return new ArrayList<>();
        }

        @Override
        // 本测试未覆盖单相册读取，调用即表示路径错误
        public RemoteAlbum getAlbum(String albumId /* 远端相册稳定标识 */) {
            throw new UnsupportedOperationException();
        }

        @Override
        // 本测试数据源不提供远端照片清单
        public List<RemotePhoto> listPhotos(
                String albumId, // 远端相册稳定标识
                int offset, // 请求的照片起始偏移
                int limit // 请求的照片最大数量
        ) {
            return new ArrayList<>();
        }

        @Override
        // 向客户端返回固定三字节缩略图
        public void downloadThumbnail(
                String photoId, // 远端照片稳定标识
                String size, // 请求的缩略图尺寸标识
                OutputStream output // 接收缩略图字节的输出流
        )
                throws IOException {
            output.write(new byte[]{4, 5, 6});
        }

        @Override
        // 分两次写入固定三字节原图以验证回调分块语义
        public void downloadOriginal(
                String photoId, // 远端照片稳定标识
                OutputStream output // 接收原图字节的输出流
        ) throws IOException {
            output.write(new byte[]{1, 2});
            output.write(new byte[]{3});
        }

        @Override
        // 记录删除标识并模拟 DSM 删除成功
        public boolean deletePhotos(List<String> photoIds /* 待删除的照片标识 */) {
            deletedPhotoIds = List.copyOf(photoIds);
            return true;
        }
    }
}
