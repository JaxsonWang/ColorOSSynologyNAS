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
import static org.junit.Assert.assertTrue;

public final class ColorOsGalleryBridgeTest {
    @Test
    public void createsGalleryStatsForRemotePhotoCount() throws Exception {
        Object result = ColorOsGalleryBridge.galleryStats(
                getClass().getClassLoader(),
                37
        );

        assertTrue(result instanceof jjq);
        jjq stats = (jjq) result;
        assertEquals(37, stats.photoCount);
        assertEquals(0, stats.videoCount);
    }

    @Test
    public void readsPhotoCountFromStoredGalleryStats() throws Exception {
        Object stats = ColorOsGalleryBridge.galleryStats(
                getClass().getClassLoader(),
                37
        );

        assertEquals(Integer.valueOf(37), ColorOsGalleryBridge.photoCount(stats));
        assertEquals(null, ColorOsGalleryBridge.photoCount(null));
    }

    @Test
    public void updatesMutableStatusFlowWithoutBlockingTheCaller() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        Object flow = ColorOsGalleryBridge.mutableStatusFlow(classLoader, false);
        Class<?> stateFlow = Class.forName(
                "kotlinx.coroutines.flow.StateFlow",
                false,
                classLoader
        );

        srb initial = (srb) stateFlow.getMethod("getValue").invoke(flow);
        assertEquals(NasDeviceAvailability.OFFLINE, initial.b);

        ColorOsGalleryBridge.updateStatusFlow(flow, classLoader, true);

        srb connected = (srb) stateFlow.getMethod("getValue").invoke(flow);
        assertEquals(NasDeviceAvailability.CONNECTED, connected.b);
    }

    @Test
    public void insertsSynologyHomeEntryBeforeMoreAlbums() throws Exception {
        oe2<Integer> localAlbums = new oe2<>(1, 12);
        oe2<Integer> moreAlbums = new oe2<>(4, 8);
        MainTabModelFixture model = new MainTabModelFixture(localAlbums, moreAlbums);

        assertTrue(ColorOsGalleryBridge.ensureSynologyHomeEntry(
                model,
                getClass().getClassLoader(),
                "DS920+",
                true
        ));

        assertEquals(3, model.items.size());
        assertSame(localAlbums, model.items.get(0));
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
    public void keepsExistingOtherNasEntriesAndDoesNotDuplicateSynology() throws Exception {
        e5q otherNas = new e5q(
                5,
                3,
                new ogq(7, 1, 1, 3, 0L, "other-nas", "其他 NAS", "other")
        );
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
    public void repairsIncompleteSyntheticEntryInPlace() throws Exception {
        e5q incomplete = new e5q(
                5,
                0,
                new ogq(0, 0, 0, 0, 0L, GalleryContract.DEVICE_ID, "", "")
        );
        oe2<Integer> moreAlbums = new oe2<>(4, 8);
        MainTabModelFixture model = new MainTabModelFixture(incomplete, moreAlbums);

        assertTrue(ColorOsGalleryBridge.ensureSynologyHomeEntry(
                model,
                getClass().getClassLoader(),
                "DS920+",
                true
        ));

        assertEquals(2, model.items.size());
        e5q repaired = (e5q) model.items.get(0);
        assertEquals(GalleryContract.DEVICE_ID, repaired.d.b);
        assertEquals(GalleryContract.DEVICE_NAME, repaired.d.c);
        assertEquals(1, repaired.d.d);
        assertEquals("DS920+", repaired.d.f);
        assertSame(moreAlbums, model.items.get(1));
    }

    @Test
    public void updatesDeviceModelAndConnectionStateInPlace() throws Exception {
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
        MainTabModelFixture model = new MainTabModelFixture(existing);

        assertTrue(ColorOsGalleryBridge.ensureSynologyHomeEntry(
                model,
                getClass().getClassLoader(),
                "DS220+",
                false
        ));

        assertEquals(1, model.items.size());
        e5q updated = (e5q) model.items.get(0);
        assertEquals("DS220+", updated.d.f);
        assertEquals(0, updated.d.d);
    }

    @Test
    public void createsSyntheticDeviceWithCurrentModelAndConnectionState() throws Exception {
        ArrayList<Object> devices = ColorOsGalleryBridge.withSynologyDevice(
                new ArrayList<>(),
                getClass().getClassLoader(),
                "DS220+",
                true
        );

        assertEquals(1, devices.size());
        ngq device = (ngq) devices.get(0);
        assertEquals(GalleryContract.DEVICE_ID, device.b);
        assertEquals("DS220+", device.d);
        assertEquals(1, device.g);
    }

    @Test
    public void skipsOnlySynologyStartupPreloadWhenLocalMetadataExists() throws Exception {
        ngq synology = device(GalleryContract.DEVICE_ID);
        ngq otherNas = device("other-nas");
        ArrayList<Object> devices = new ArrayList<>();
        devices.add(synology);
        devices.add(otherNas);

        assertEquals(1, ColorOsGalleryBridge.nextPreloadIndex(devices, 0, true));
        assertEquals(1, ColorOsGalleryBridge.nextPreloadIndex(devices, 1, true));
        assertSame(otherNas, devices.get(1));
    }

    @Test
    public void keepsFirstSynologySyncWithoutLocalMetadata() throws Exception {
        ArrayList<Object> devices = new ArrayList<>();
        devices.add(device(GalleryContract.DEVICE_ID));

        assertEquals(0, ColorOsGalleryBridge.nextPreloadIndex(devices, 0, false));
    }

    @Test
    public void advancesPastLastSynologyDeviceSoCallerCanReleaseLatch() throws Exception {
        ArrayList<Object> devices = new ArrayList<>();
        devices.add(device("other-nas"));
        devices.add(device(GalleryContract.DEVICE_ID));

        assertEquals(devices.size(), ColorOsGalleryBridge.nextPreloadIndex(devices, 1, true));
    }

    private static ngq device(String deviceUserId) {
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

    private static final class MainTabModelFixture {
        private final ArrayList<Object> items = new ArrayList<>();
        private final ValueHolder e = new ValueHolder(items);

        private MainTabModelFixture(Object... initialItems) {
            for (Object item : initialItems) {
                items.add(item);
            }
        }
    }

    private static final class ValueHolder {
        private final Object value;

        private ValueHolder(Object value) {
            this.value = value;
        }

        public Object getValue() {
            return value;
        }
    }
}
