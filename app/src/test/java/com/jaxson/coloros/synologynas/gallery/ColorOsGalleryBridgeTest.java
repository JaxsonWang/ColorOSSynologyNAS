package com.jaxson.coloros.synologynas.gallery;

import com.oplus.aiunit.vision.jjq;
import com.oplus.aiunit.vision.e5q;
import com.oplus.aiunit.vision.ngq;
import com.oplus.aiunit.vision.oe2;
import com.oplus.aiunit.vision.ogq;
import com.oplus.aiunit.vision.srb;
import com.oplus.gallery.business_lib.nas.NasDeviceAvailability;

import org.junit.Test;

import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public final class ColorOsGalleryBridgeTest {
    @Test
    // 验证 Hook 缓存照片数按 jjq 构造合同映射且视频数固定为零
    public void createsGalleryStatsForRemotePhotoCount() throws Exception {
        // 由 Bridge 按当前私有构造器创建的图库统计 DTO
        Object result = ColorOsGalleryBridge.galleryStats(
                getClass().getClassLoader(),
                37
        );

        assertTrue(result instanceof jjq);
        // 已确认类型的图库统计夹具
        jjq stats = (jjq) result;
        assertEquals(37, stats.a);
        assertEquals(0, stats.b);
    }

    @Test
    // 验证图库统计只从当前运行时 jjq.a 字段读取照片数
    public void readsPhotoCountFromStoredGalleryStats() throws Exception {
        // 按当前构造器创建且 a 字段为 37 的图库统计 DTO
        Object stats = ColorOsGalleryBridge.galleryStats(
                getClass().getClassLoader(),
                37
        );

        assertEquals(Integer.valueOf(37), ColorOsGalleryBridge.photoCount(stats));
        assertEquals(null, ColorOsGalleryBridge.photoCount(null));
    }

    @Test
    // 验证缺少精确 jjq.a 字段时立即暴露合约漂移而不扫描其他 int 字段
    public void rejectsUnknownGalleryStatsFieldLayout() {
        // 仅包含无关 int 字段的错误统计布局夹具
        UnknownGalleryStats stats = new UnknownGalleryStats();

        assertThrows(
                NoSuchFieldException.class,
                () /* 触发错误字段布局读取 */ -> ColorOsGalleryBridge.photoCount(stats)
        );
    }

    @Test
    // 验证 MutableStateFlow 可先返回离线初值再同步更新为已连接
    public void updatesMutableStatusFlowWithoutBlockingTheCaller() throws Exception {
        // 解析测试 fixture 和 Kotlin Flow 运行时的类加载器
        ClassLoader classLoader = getClass().getClassLoader();
        // Bridge 创建并返回给 ColorOS 的群晖状态流
        Object flow = ColorOsGalleryBridge.mutableStatusFlow(classLoader, false);
        // 用于反射读取 Flow 当前值的 Kotlin StateFlow 接口
        Class<?> stateFlow = Class.forName(
                "kotlinx.coroutines.flow.StateFlow",
                false,
                classLoader
        );

        // 状态流创建后立即可读的离线初值
        srb initial = (srb) stateFlow.getMethod("getValue").invoke(flow);
        assertEquals(NasDeviceAvailability.OFFLINE, initial.b);

        ColorOsGalleryBridge.updateStatusFlow(flow, classLoader, true);

        // 状态流更新后立即可读的已连接值
        srb connected = (srb) stateFlow.getMethod("getValue").invoke(flow);
        assertEquals(NasDeviceAvailability.CONNECTED, connected.b);
    }

    @Test
    // 验证群晖首页入口插入在“更多图集”分组之前
    public void insertsSynologyHomeEntryBeforeMoreAlbums() throws Exception {
        // 模拟类型 1 的本地相册首页分组
        oe2<Integer> localAlbums = new oe2<>(1, 12);
        // 模拟类型 4 的“更多图集”首页分组
        oe2<Integer> moreAlbums = new oe2<>(4, 8);
        // 按本地相册、“更多图集”顺序创建首页模型
        MainTabModelFixture model = new MainTabModelFixture(localAlbums, moreAlbums);

        assertTrue(ColorOsGalleryBridge.ensureSynologyHomeEntry(
                model,
                getClass().getClassLoader(),
                "DS920+",
                true
        ));

        assertEquals(3, model.items.size());
        assertSame(localAlbums, model.items.get(0));
        // 插入到“更多图集”前方的群晖 e5q 分组
        e5q entry = (e5q) model.items.get(1);
        assertEquals(5, entry.a);
        assertEquals(1, entry.c);
        assertEquals(GalleryContract.DEVICE_ID, entry.d.b);
        assertEquals(GalleryContract.DEVICE_NAME, entry.d.c);
        assertEquals(1, entry.d.d);
        assertEquals("DS920+", entry.d.f);
        assertSame(moreAlbums, model.items.get(2));
    }

    @Test
    // 验证其他 NAS 分组保持原位置且完整群晖入口不会重复添加
    public void keepsExistingOtherNasEntriesAndDoesNotDuplicateSynology() throws Exception {
        // 模拟必须保留的其他 NAS 首页分组
        e5q otherNas = new e5q(
                5,
                3,
                new ogq(7, 1, 1, 3, 0L, "other-nas", "其他 NAS", "other")
        );
        // 模拟型号和连接状态均完整的现有群晖分组
        e5q synology = new e5q(
                5,
                1,
                new ogq(
                        0,
                        1,
                        1,
                        1,
                        0L,
                        GalleryContract.DEVICE_ID,
                        GalleryContract.DEVICE_NAME,
                        "DS920+"
                )
        );
        // 按其他 NAS、群晖顺序创建首页模型
        MainTabModelFixture model = new MainTabModelFixture(otherNas, synology);

        assertFalse(ColorOsGalleryBridge.ensureSynologyHomeEntry(
                model,
                getClass().getClassLoader(),
                "DS920+",
                true
        ));

        assertEquals(2, model.items.size());
        assertSame(otherNas, model.items.get(0));
        assertSame(synology, model.items.get(1));
    }

    @Test
    // 验证绑定群晖 ID 但字段不完整的入口会在原位置修复
    public void repairsIncompleteSyntheticEntryInPlace() throws Exception {
        // 模拟仅保留群晖设备 ID 的不完整首页分组
        e5q incomplete = new e5q(
                5,
                0,
                new ogq(0, 0, 0, 0, 0L, GalleryContract.DEVICE_ID, "", "")
        );
        // 模拟应继续保持末尾位置的“更多图集”分组
        oe2<Integer> moreAlbums = new oe2<>(4, 8);
        // 按不完整群晖、“更多图集”顺序创建首页模型
        MainTabModelFixture model = new MainTabModelFixture(incomplete, moreAlbums);

        assertTrue(ColorOsGalleryBridge.ensureSynologyHomeEntry(
                model,
                getClass().getClassLoader(),
                "DS920+",
                true
        ));

        assertEquals(2, model.items.size());
        // 在原位置重建后的完整群晖首页分组
        e5q repaired = (e5q) model.items.get(0);
        assertEquals(GalleryContract.DEVICE_ID, repaired.d.b);
        assertEquals(GalleryContract.DEVICE_NAME, repaired.d.c);
        assertEquals(1, repaired.d.d);
        assertEquals("DS920+", repaired.d.f);
        assertSame(moreAlbums, model.items.get(1));
    }

    @Test
    // 验证已有群晖入口的型号与连接状态变化会原位更新
    public void updatesDeviceModelAndConnectionStateInPlace() throws Exception {
        // 模拟型号为 DS920+ 且已连接的原群晖入口
        e5q existing = new e5q(
                5,
                1,
                new ogq(
                        0,
                        1,
                        1,
                        1,
                        0L,
                        GalleryContract.DEVICE_ID,
                        GalleryContract.DEVICE_NAME,
                        "DS920+"
                )
        );
        // 仅包含一个现有群晖入口的首页模型
        MainTabModelFixture model = new MainTabModelFixture(existing);

        assertTrue(ColorOsGalleryBridge.ensureSynologyHomeEntry(
                model,
                getClass().getClassLoader(),
                "DS220+",
                false
        ));

        assertEquals(1, model.items.size());
        // 更新为 DS220+ 且离线后的群晖入口
        e5q updated = (e5q) model.items.get(0);
        assertEquals("DS220+", updated.d.f);
        assertEquals(0, updated.d.d);
    }

    @Test
    // 验证设备列表合成项写入唯一 ID、当前型号和连接状态
    public void createsSyntheticDeviceWithCurrentModelAndConnectionState() throws Exception {
        // Bridge 在空原列表基础上创建的群晖设备列表
        ArrayList<Object> devices = ColorOsGalleryBridge.withSynologyDevice(
                new ArrayList<>(),
                getClass().getClassLoader(),
                "DS220+",
                true
        );

        assertEquals(1, devices.size());
        // 列表中唯一的群晖 ngq 设备 DTO
        ngq device = (ngq) devices.get(0);
        assertEquals(GalleryContract.DEVICE_ID, device.b);
        assertEquals("DS220+", device.d);
        assertEquals(1, device.g);
    }

    @Test
    // 验证已有本地统计时只跳过连续群晖合成设备
    public void skipsOnlySynologyStartupPreloadWhenLocalMetadataExists() throws Exception {
        // 位于预加载起点的群晖设备夹具
        ngq synology = device(GalleryContract.DEVICE_ID);
        // 必须交还原飞牛预加载路径处理的其他 NAS 设备
        ngq otherNas = device("other-nas");
        // 按群晖、其他 NAS 顺序保存预加载设备
        ArrayList<Object> devices = new ArrayList<>();
        devices.add(synology);
        devices.add(otherNas);

        assertEquals(1, ColorOsGalleryBridge.nextPreloadIndex(devices, 0, true));
        assertEquals(1, ColorOsGalleryBridge.nextPreloadIndex(devices, 1, true));
        assertSame(otherNas, devices.get(1));
    }

    @Test
    // 验证没有本地统计时首个群晖设备仍执行原启动预加载
    public void keepsFirstSynologySyncWithoutLocalMetadata() throws Exception {
        // 仅包含群晖设备的启动预加载列表
        ArrayList<Object> devices = new ArrayList<>();
        devices.add(device(GalleryContract.DEVICE_ID));

        assertEquals(0, ColorOsGalleryBridge.nextPreloadIndex(devices, 0, false));
    }

    @Test
    // 验证末尾群晖设备被跳过后返回列表长度供调用方释放 latch
    public void advancesPastLastSynologyDeviceSoCallerCanReleaseLatch() throws Exception {
        // 按其他 NAS、群晖顺序保存预加载设备
        ArrayList<Object> devices = new ArrayList<>();
        devices.add(device("other-nas"));
        devices.add(device(GalleryContract.DEVICE_ID));

        assertEquals(devices.size(), ColorOsGalleryBridge.nextPreloadIndex(devices, 1, true));
    }

    // 创建仅设备 ID 不同的完整 ngq 测试夹具
    private static ngq device(String deviceUserId /* NAS 设备唯一标识 */) {
        return new ngq(
                com.oplus.gallery.business_lib.nas.NasProvider.FEINIU,
                deviceUserId,
                "NAS",
                "user",
                "https://nas.example.test",
                "",
                1,
                "",
                Long.MAX_VALUE,
                true,
                1
        );
    }

    private static final class UnknownGalleryStats {
        // 模拟不得被当作照片数量读取的无关 int 字段
        private final int unrelated = 37;
    }

    private static final class MainTabModelFixture {
        // 保存首页 Lazy 实际返回的可变分组列表
        private final ArrayList<Object> items = new ArrayList<>();
        // 模拟 enn.e 字段持有的 Kotlin Lazy 包装对象
        private final ValueHolder e = new ValueHolder(items);

        // 按给定顺序初始化 ColorOS 首页分组列表
        private MainTabModelFixture(Object... initialItems /* 初始首页分组 */) {
            for (Object item : initialItems) { // 当前加入首页模型的分组
                items.add(item);
            }
        }
    }

    private static final class ValueHolder {
        // 保存模拟 Kotlin Lazy 返回的实际首页分组列表
        private final Object value;

        // 创建始终返回固定对象的 Lazy 值夹具
        private ValueHolder(Object value /* Lazy 应返回的固定对象 */) {
            this.value = value;
        }

        // 模拟 Kotlin Lazy.getValue 返回首页分组列表
        public Object getValue() {
            return value;
        }
    }
}
