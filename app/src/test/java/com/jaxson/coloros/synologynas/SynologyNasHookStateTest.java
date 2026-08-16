package com.jaxson.coloros.synologynas;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.jaxson.coloros.synologynas.gallery.GalleryContract;
import com.jaxson.coloros.synologynas.gallery.GalleryRemoteClient;
import com.jaxson.coloros.synologynas.gallery.RemoteAlbum;
import com.jaxson.coloros.synologynas.gallery.RemoteGalleryDataSource;
import com.jaxson.coloros.synologynas.gallery.RemotePhoto;

import org.junit.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Proxy;
import java.util.List;

import io.github.libxposed.api.XposedInterface;

/**
 * 验证 Hook 共享的 NAS 型号与连接状态始终作为单个原子快照发布
 */
public final class SynologyNasHookStateTest {
    /**
     * 验证未配置状态保持默认快照且不读取不存在的配置型号
     */
    @Test
    public void skipsStoredModelLookupWhenRemoteConfigIsMissing() throws Exception {
        // 记录配置读取次数的未配置远端数据源
        TrackingDataSource dataSource = new TrackingDataSource(false, "DS923+", "DS923+");
        // 使用未配置数据源创建的相册远端客户端
        GalleryRemoteClient remoteClient = new GalleryRemoteClient(dataSource);
        // 本次验证使用的 Hook 原子状态容器
        SynologyNasHookState hookState = new SynologyNasHookState(xposed());

        hookState.initialize(remoteClient);

        assertEquals(0, dataSource.configuredModelReads());
        assertEquals(
                GalleryContract.DEFAULT_DEVICE_MODEL,
                hookState.snapshot().deviceModel()
        );
        assertFalse(hookState.connected());
    }

    /**
     * 验证群晖卡片 availability 只更新连接状态并保留同一快照中的型号
     */
    @Test
    public void availabilityUpdatePreservesConfiguredModel() throws Exception {
        // 提供已配置型号的远端数据源
        TrackingDataSource dataSource = new TrackingDataSource(true, "DS923+", "DS1522+");
        // 使用已配置数据源创建的相册远端客户端
        GalleryRemoteClient remoteClient = new GalleryRemoteClient(dataSource);
        // 本次验证使用的 Hook 原子状态容器
        SynologyNasHookState hookState = new SynologyNasHookState(xposed());
        hookState.initialize(remoteClient);

        assertTrue(hookState.updateConnectedFromAvailability(GalleryContract.DEVICE_ID, 1));

        // availability 更新后一次读取的完整状态快照
        SynologyNasHookState.NasState nasState = hookState.snapshot();
        assertEquals("DS923+", nasState.deviceModel());
        assertTrue(nasState.connected());
        assertFalse(hookState.updateConnectedFromAvailability("feiniu-nas", 0));
        assertSame(nasState, hookState.snapshot());
    }

    /**
     * 验证实时探测返回并发布的是同一个不可变状态实例
     */
    @Test
    public void refreshReturnsThePublishedSnapshot() throws Exception {
        // 提供实时探测型号的远端数据源
        TrackingDataSource dataSource = new TrackingDataSource(true, "DS923+", "DS1522+");
        // 使用探测数据源创建的相册远端客户端
        GalleryRemoteClient remoteClient = new GalleryRemoteClient(dataSource);
        // 本次验证使用的 Hook 原子状态容器
        SynologyNasHookState hookState = new SynologyNasHookState(xposed());
        hookState.initialize(remoteClient);

        // 实时探测构造并原子发布的状态快照
        SynologyNasHookState.NasState refreshedState = hookState.refresh(remoteClient);

        assertSame(refreshedState, hookState.snapshot());
        assertEquals("DS1522+", refreshedState.deviceModel());
        assertTrue(refreshedState.connected());
    }

    /**
     * 验证实时探测失败会保留已发布型号并原子切换为断开状态
     */
    @Test
    public void refreshFailurePreservesPublishedModelAndDisconnects() throws Exception {
        // 提供已配置型号但实时探测失败的远端数据源
        TrackingDataSource dataSource = new FailingProbeTrackingDataSource();
        // 使用失败探测数据源创建相册远端客户端
        GalleryRemoteClient remoteClient = new GalleryRemoteClient(dataSource);
        // 本次验证使用的 Hook 原子状态容器
        SynologyNasHookState hookState = new SynologyNasHookState(xposed());
        hookState.initialize(remoteClient);
        hookState.updateConnectedFromAvailability(GalleryContract.DEVICE_ID, 1);

        // 失败探测构造并原子发布的状态快照
        SynologyNasHookState.NasState refreshedState = hookState.refresh(remoteClient);

        assertSame(refreshedState, hookState.snapshot());
        assertEquals("DS923+", refreshedState.deviceModel());
        assertFalse(refreshedState.connected());
    }

    /**
     * 验证已读取的群晖照片统计由共享状态同时发布数量与元数据标记
     */
    @Test
    public void recordsStoredPhotoCountAndMetadataAvailability() {
        // 本次验证使用的 Hook 照片统计状态容器
        SynologyNasHookState hookState = new SynologyNasHookState(xposed());

        assertEquals(0, hookState.photoCount());
        assertFalse(hookState.hasStoredMetadata());

        hookState.recordStoredPhotoCount(1594);

        assertEquals(1594, hookState.photoCount());
        assertTrue(hookState.hasStoredMetadata());
    }

    /**
     * 验证已发布型号读取失败会在首个业务 Hook 注册前直接终止初始化
     */
    @Test
    public void propagatesConfiguredModelLookupFailure() {
        // 提供固定抛出配置读取异常的远端数据源
        TrackingDataSource dataSource = new FailingTrackingDataSource();
        // 使用失败数据源创建的相册远端客户端
        GalleryRemoteClient remoteClient = new GalleryRemoteClient(dataSource);
        // 本次验证使用的 Hook 原子状态容器
        SynologyNasHookState hookState = new SynologyNasHookState(xposed());

        assertThrows(IOException.class, () -> hookState.initialize(remoteClient));
        assertEquals(GalleryContract.DEFAULT_DEVICE_MODEL, hookState.snapshot().deviceModel());
        assertFalse(hookState.snapshot().connected());
    }

    /**
     * 创建只接收日志调用的 libxposed 测试接口
     *
     * @return 不执行外部副作用的接口代理
     */
    private static XposedInterface xposed() {
        return (XposedInterface) Proxy.newProxyInstance(
                XposedInterface.class.getClassLoader(),
                new Class<?>[]{XposedInterface.class},
                (
                        /* 接收接口代理实例 */ proxy,
                        /* 接收本次调用的方法 */ method,
                        /* 接收本次调用的参数 */ arguments
                ) -> null
        );
    }

    /**
     * 提供可统计配置读取并返回固定型号的远端图库数据源
     */
    private static class TrackingDataSource implements RemoteGalleryDataSource {
        // 标记测试数据源是否包含完整远端配置
        private final boolean configured;
        // 保存配置中最后确认的 NAS 型号
        private final String configuredModel;
        // 保存实时探测应返回的 NAS 型号
        private final String probedModel;

        // 统计配置型号的实际读取次数
        private int configuredModelReads;

        /**
         * 创建返回固定配置状态与型号的测试数据源
         *
         * @param configured 是否存在完整远端配置
         * @param configuredModel 配置中最后确认的 NAS 型号
         * @param probedModel 实时探测返回的 NAS 型号
         */
        private TrackingDataSource(
                boolean configured, // 是否存在完整远端配置
                String configuredModel, // 配置中最后确认的 NAS 型号
                String probedModel // 实时探测需要返回的 NAS 型号
        ) {
            this.configured = configured;
            this.configuredModel = configuredModel;
            this.probedModel = probedModel;
        }

        /**
         * 返回测试数据源的固定配置状态
         *
         * @return 构造测试数据源时指定的配置状态
         */
        @Override
        public boolean isConfigured() {
            return configured;
        }

        /**
         * 返回固定配置型号并累计读取次数
         *
         * @return 构造测试数据源时指定的配置型号
         */
        @Override
        public String configuredDeviceModel() throws IOException {
            configuredModelReads++;
            return configuredModel;
        }

        /**
         * 返回固定实时探测型号
         *
         * @return 构造测试数据源时指定的探测型号
         */
        @Override
        public String probeDeviceModel() throws IOException {
            return probedModel;
        }

        /**
         * 返回配置型号的实际读取次数
         *
         * @return 当前累计读取次数
         */
        private int configuredModelReads() {
            return configuredModelReads;
        }

        /**
         * 拒绝本状态测试不会触发的相册清单读取
         *
         * @param offset 相册清单起始偏移
         * @param limit 相册清单最大数量
         * @return 本测试不会返回相册清单
         */
        @Override
        public List<RemoteAlbum> listAlbums(
                int offset, // 相册清单起始偏移
                int limit // 相册清单最大数量
        ) {
            throw new AssertionError("unexpected listAlbums call");
        }

        /**
         * 拒绝本状态测试不会触发的单相册读取
         *
         * @param albumId 远端相册稳定标识
         * @return 本测试不会返回相册
         */
        @Override
        public RemoteAlbum getAlbum(String albumId /* 远端相册稳定标识 */) {
            throw new AssertionError("unexpected getAlbum call");
        }

        /**
         * 拒绝本状态测试不会触发的照片清单读取
         *
         * @param albumId 远端相册稳定标识
         * @param offset 照片清单起始偏移
         * @param limit 照片清单最大数量
         * @return 本测试不会返回照片清单
         */
        @Override
        public List<RemotePhoto> listPhotos(
                String albumId, // 远端相册稳定标识
                int offset, // 照片清单起始偏移
                int limit // 照片清单最大数量
        ) {
            throw new AssertionError("unexpected listPhotos call");
        }

        /**
         * 拒绝本状态测试不会触发的缩略图下载
         *
         * @param photoId 远端照片稳定标识
         * @param size 缩略图尺寸标识
         * @param output 调用方输出流
         */
        @Override
        public void downloadThumbnail(
                String photoId, // 远端照片稳定标识
                String size, // 缩略图尺寸标识
                OutputStream output // 接收缩略图的输出流
        ) {
            throw new AssertionError("unexpected downloadThumbnail call");
        }

        /**
         * 拒绝本状态测试不会触发的原图下载
         *
         * @param photoId 远端照片稳定标识
         * @param output 调用方输出流
         */
        @Override
        public void downloadOriginal(
                String photoId, // 远端照片稳定标识
                OutputStream output // 接收原图的输出流
        ) {
            throw new AssertionError("unexpected downloadOriginal call");
        }

        /**
         * 拒绝本状态测试不会触发的照片删除
         *
         * @param photoIds 待删除的远端照片标识
         * @return 本测试不会返回删除结果
         */
        @Override
        public boolean deletePhotos(List<String> photoIds /* 待删除的远端照片标识 */) {
            throw new AssertionError("unexpected deletePhotos call");
        }
    }

    /**
     * 提供固定失败的已发布型号读取边界
     */
    private static final class FailingTrackingDataSource extends TrackingDataSource {
        /** 创建已配置但型号读取失败的测试数据源 */
        private FailingTrackingDataSource() {
            super(true, "unused", "unused");
        }

        /**
         * 抛出固定配置读取异常，验证安装初始化不会伪造成功状态
         *
         * @return 本失败路径不会返回型号
         * @throws IOException 固定表达已发布配置读取失败
         */
        @Override
        public String configuredDeviceModel() throws IOException {
            throw new IOException("expected configured model failure");
        }
    }

    /**
     * 提供固定失败的实时型号探测边界
     */
    private static final class FailingProbeTrackingDataSource extends TrackingDataSource {
        /** 创建已配置且保留固定已发布型号的数据源 */
        private FailingProbeTrackingDataSource() {
            super(true, "DS923+", "unused");
        }

        /**
         * 抛出固定探测异常，验证失败状态不会丢失已发布型号
         *
         * @return 本失败路径不会返回型号
         * @throws IOException 固定表达实时 DSM 型号探测失败
         */
        @Override
        public String probeDeviceModel() throws IOException {
            throw new IOException("expected device model probe failure");
        }
    }
}
