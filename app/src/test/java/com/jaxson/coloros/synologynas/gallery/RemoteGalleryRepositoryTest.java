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
    // 提供所有仓储场景共享的完整 HTTPS 群晖配置
    private static final SynologyConfig CONFIG = new SynologyConfig(
            "https://nas.example.com:5001",
            "user",
            "password",
            "",
            "/home/Photos",
            "DS920+",
            SynologyConfig.DEFAULT_BACKUP_ENABLED,
            SynologyConfig.DEFAULT_BACKUP_FOLDER
    );

    @Test
    // 验证保存型号不联网且实时型号来自 DSM 网关
    public void returnsStoredAndLiveDeviceModel() throws IOException {
        // 返回空清单和固定实时型号的 DSM 网关
        FakeDsmGateway gateway = new FakeDsmGateway(List.of());
        // 使用固定配置与记录网关创建远端图库仓储
        RemoteGalleryRepository repository = repository(gateway);

        assertEquals("DS920+", repository.configuredDeviceModel());
        assertEquals("DS220+", repository.probeDeviceModel());
    }

    @Test
    // 验证快照同时创建 ALL_PROJECT 与按相对目录划分的相册
    public void buildSnapshotCreatesAllAlbumAndDirectoryAlbums() {
        // 从根目录、Family 和 Trips/Paris 图片构造的完整快照
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
                        .map(photo /* 当前巴黎相册照片 */ -> photo.media().name())
                        .toList());
    }

    @Test
    // 验证照片 ID 为正数且不受 DSM 清单输入顺序影响
    public void buildSnapshotUsesStablePositiveNumericIds() {
        // 包含两个不同路径图片的原始 DSM 清单
        List<RemoteMedia> input = List.of(
                media("/home/Photos/A/one.jpg", 100L),
                media("/home/Photos/B/two.jpg", 200L)
        );
        // 使用原始顺序和第一组快照元数据构造的结果
        RemoteGalleryRepository.Snapshot first = RemoteGalleryRepository.buildSnapshot(
                CONFIG,
                "first",
                1L,
                input
        );
        // 使用反转顺序和第二组快照元数据构造的结果
        RemoteGalleryRepository.Snapshot second = RemoteGalleryRepository.buildSnapshot(
                CONFIG,
                "second",
                2L,
                List.of(input.get(1), input.get(0))
        );

        for (RemotePhoto photo : first.photosByAlbumId.get("0")) { // 首个快照的当前照片
            assertTrue(photo.id().matches("[1-9][0-9]*"));
            assertTrue(photo.galleryId() > 0);
            // 第二个快照中远端路径相同的照片
            RemotePhoto matching = second.photosByAlbumId.get("0").stream()
                    .filter(candidate /* 第二快照中的候选照片 */ ->
                            candidate.media().remotePath()
                            .equals(photo.media().remotePath()))
                    .findFirst()
                    .orElseThrow();
            assertEquals(photo.id(), matching.id());
            assertEquals(photo.galleryId(), matching.galleryId());
        }
        assertNotEquals(
                first.photosByAlbumId.get("0").get(0).id(),
                first.photosByAlbumId.get("0").get(1).id()
        );
    }

    @Test
    // 验证分页返回请求窗口并在偏移到达末尾时返回空列表
    public void pageReturnsRequestedWindowAndEmptyTail() {
        // 按既定顺序提供分页输入的整数列表
        List<Integer> values = List.of(10, 20, 30, 40);

        assertEquals(List.of(20, 30), RemoteGalleryRepository.page(values, 1, 2));
        assertEquals(List.of(40), RemoteGalleryRepository.page(values, 3, 2));
        assertEquals(List.of(), RemoteGalleryRepository.page(values, 4, 2));
    }

    @Test
    // 验证负偏移和非正 limit 都明确拒绝
    public void pageRejectsInvalidArguments() {
        assertInvalidPage(-1, 1);
        assertInvalidPage(0, 0);
    }

    @Test
    // 验证删除映射到 DSM 媒体，并在成功后重新加载失效快照
    public void deletesMappedRemoteMediaAndReloadsInvalidatedSnapshot() throws IOException {
        // 持有两张照片且记录删除与清单调用的 DSM 网关
        FakeDsmGateway gateway = new FakeDsmGateway(List.of(
                media("/home/Photos/one.jpg", 200L),
                media("/home/Photos/two.jpg", 100L)
        ));
        // 使用记录网关创建远端图库仓储
        RemoteGalleryRepository repository = repository(gateway);
        // 删除前从 ALL_PROJECT 读取的两张远端照片
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
    // 验证 DSM 删除失败会向上抛出且保留原清单快照
    public void preservesSnapshotAndSurfacesDsmDeleteFailure() throws IOException {
        // 持有一张照片并可注入删除异常的 DSM 网关
        FakeDsmGateway gateway = new FakeDsmGateway(List.of(
                media("/home/Photos/one.jpg", 100L)
        ));
        gateway.deleteFailure = new IOException("delete failed");
        // 使用删除失败网关创建远端图库仓储
        RemoteGalleryRepository repository = repository(gateway);
        // 删除前从 ALL_PROJECT 读取的唯一远端照片
        RemotePhoto photo = repository.listPhotos("0", 0, 10).get(0);

        assertThrows(
                IOException.class,
                () /* 触发预期的 DSM 删除失败 */ ->
                        repository.deletePhotos(List.of(photo.id()))
        );
        assertEquals(1, repository.listPhotos("0", 0, 10).size());
        assertEquals(1, gateway.listInvocations);
    }

    // 断言给定无效分页参数会返回包含参数语义的明确异常
    private static void assertInvalidPage(
            int offset, // 待验证的分页起始偏移
            int limit // 待验证的分页最大数量
    ) {
        try {
            RemoteGalleryRepository.page(List.of(1), offset, limit);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected /* 预期的分页参数异常 */) {
            assertTrue(expected.getMessage().contains("分页参数无效"));
        }
    }

    // 创建名称取自路径且 MIME 固定为 JPEG 的 DSM 图片夹具
    private static RemoteMedia media(
            String path, // DSM 图片完整远端路径
            long modifiedSeconds // DSM 图片修改时间秒值
    ) {
        return new RemoteMedia(
                path,
                path.substring(path.lastIndexOf('/') + 1),
                1024L,
                modifiedSeconds,
                "image/jpeg"
        );
    }

    // 使用固定配置和指定记录网关创建待测仓储
    private static RemoteGalleryRepository repository(
            FakeDsmGateway gateway // 本场景使用的 DSM 网关
    ) {
        // 始终返回固定测试配置的相册进程配置来源
        SynologyConfigSource configSource = new SynologyConfigSource() {
            @Override
            // 固定声明测试配置已经存在
            public boolean hasConfig() {
                return true;
            }

            @Override
            // 返回所有仓储场景共享的完整群晖配置
            public SynologyConfig load() {
                return CONFIG;
            }
        };
        return new RemoteGalleryRepository(
                configSource,
                ignored /* 忽略固定配置并返回指定网关 */ -> gateway
        );
    }

    private static final class FakeDsmGateway implements DsmGateway {
        // 保存当前 DSM 清单并在成功删除后同步移除媒体
        private final List<RemoteMedia> inventory;
        // 记录最近一次 DSM Delete API 收到的媒体列表
        private List<RemoteMedia> deletedMedia = List.of();
        // 控制 DSM Delete API 是否抛出指定异常
        private IOException deleteFailure;
        // 记录 DSM 全量图片清单读取次数
        private int listInvocations;

        // 使用给定图片清单创建可变 DSM 网关夹具
        private FakeDsmGateway(List<RemoteMedia> inventory /* 初始 DSM 图片清单 */) {
            this.inventory = new ArrayList<>(inventory);
        }

        @Override
        // 本测试不使用具体 API 发现结果
        public DsmApiCatalog discoverApis() {
            return null;
        }

        @Override
        // 返回只存在于测试进程内的固定 SID
        public String login(DsmApiCatalog catalog /* 动态 API 目录测试占位 */) {
            return "sid";
        }

        @Override
        // 返回 DSM 实时探测得到的固定 NAS 型号
        public String getDeviceModel(
                DsmApiCatalog catalog, // 动态 API 目录测试占位
                String sid // 当前测试会话 SID
        ) {
            return "DS220+";
        }

        @Override
        // 记录全量清单调用并返回当前不可变图片快照
        public List<RemoteMedia> listImages(
                DsmApiCatalog catalog, // 动态 API 目录测试占位
                String sid // 当前测试会话 SID
        ) {
            listInvocations++;
            return List.copyOf(inventory);
        }

        @Override
        // 本测试禁止进入原图下载路径
        public void download(
                DsmApiCatalog catalog, // 动态 API 目录测试占位
                String sid, // 当前测试会话 SID
                RemoteMedia media, // 待下载的 DSM 媒体
                OutputStream output // 接收原图字节的输出流
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        // 本测试禁止进入缩略图下载路径
        public void downloadThumbnail(
                DsmApiCatalog catalog, // 动态 API 目录测试占位
                String sid, // 当前测试会话 SID
                RemoteMedia media, // 待读取缩略图的 DSM 媒体
                String size, // 请求的缩略图尺寸
                OutputStream output // 接收缩略图字节的输出流
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        // 按测试场景抛出删除异常或记录并移除目标媒体
        public void delete(
                DsmApiCatalog catalog, // 动态 API 目录测试占位
                String sid, // 当前测试会话 SID
                List<RemoteMedia> media // 本次待删除的 DSM 媒体
        ) throws IOException {
            if (deleteFailure != null) {
                throw deleteFailure;
            }
            deletedMedia = List.copyOf(media);
            inventory.removeAll(media);
        }
    }
}
