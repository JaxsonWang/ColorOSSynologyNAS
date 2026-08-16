package com.jaxson.coloros.synologynas.gallery;

import com.jaxson.coloros.synologynas.dsm.RemoteMedia;
import com.oplus.aiunit.vision.b3q;
import com.oplus.gallery.business_lib.nas.NasPhotoInfo;
import com.oplus.gallery.business_lib.nas.NasProvider;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

// 验证群晖相册和照片模型到 ColorOS 私有 DTO 的精确映射
public final class ColorOsNasDtoMapperTest {
    @Test
    // 验证内部相册模型按当前十参数合同完整映射为 b3q
    public void mapsRemoteAlbumToCurrentColorOsContract() throws Exception {
        // 使用测试类加载器和 FEINIU 页面槽位创建 DTO 映射器
        ColorOsNasDtoMapper mapper = new ColorOsNasDtoMapper(
                getClass().getClassLoader(),
                NasProvider.FEINIU
        );
        // 包含稳定 ID、封面、数量和更新时间的内部相册模型
        RemoteAlbum album = new RemoteAlbum(
                "11",
                22,
                "Trips/Paris",
                3,
                "33",
                44,
                55L
        );

        // 按 ColorOS 当前构造器映射出的 NAS 相册 DTO
        b3q result = (b3q) mapper.album(album);

        assertEquals(NasProvider.FEINIU, result.provider);
        assertEquals(22, result.galleryId);
        assertEquals("11", result.id);
        assertEquals("Trips/Paris", result.name);
        assertEquals(GalleryContract.DEVICE_ID, result.deviceUserId);
        assertEquals(3, result.imageCount);
        assertEquals(0, result.videoCount);
        assertEquals("33", result.coverPhotoId);
        assertEquals(44, result.coverGalleryId);
        assertEquals(55L, result.updateTimeMillis);
    }

    @Test
    // 验证内部照片模型按当前十三参数合同完整映射为 NasPhotoInfo
    public void mapsRemotePhotoToCurrentColorOsContract() throws Exception {
        // 使用测试类加载器和 FEINIU 页面槽位创建 DTO 映射器
        ColorOsNasDtoMapper mapper = new ColorOsNasDtoMapper(
                getClass().getClassLoader(),
                NasProvider.FEINIU
        );
        // 提供文件名、大小、MIME 与修改时间的 DSM 远端媒体
        RemoteMedia media = new RemoteMedia(
                "/home/Photos/Trips/IMG_1.jpg",
                "IMG_1.jpg",
                1024L,
                123L,
                "image/jpeg"
        );
        // 绑定稳定字符串与 ColorOS int 标识的内部照片模型
        RemotePhoto photo = new RemotePhoto("66", 77, media);

        // 列表映射返回的唯一 ColorOS NAS 照片 DTO
        NasPhotoInfo result = (NasPhotoInfo) mapper.photos(List.of(photo)).get(0);

        assertEquals(77, result.galleryId);
        assertEquals("66", result.id);
        assertEquals(GalleryContract.DEVICE_ID, result.deviceUserId);
        assertEquals("IMG_1.jpg", result.name);
        assertEquals(1024L, result.size);
        assertEquals("image/jpeg", result.mimeType);
        assertEquals(Long.valueOf(123_000L), result.modifiedAtMillis);
        assertEquals(NasPhotoInfo.MediaType.IMAGE, result.mediaType);
        assertEquals(NasPhotoInfo.LivePhotoType.NONE, result.livePhotoType);
    }
}
