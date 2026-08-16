package com.jaxson.coloros.synologynas.gallery;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

// 将群晖相册领域模型映射为 ColorOS 当前私有 DTO
final class ColorOsNasDtoMapper {
    // 定位 ColorOS 当前版本的 NAS 相册 DTO
    private static final String ALBUM_DTO = "com.oplus.aiunit.vision.b3q";
    // 定位 ColorOS 当前版本的 NAS 照片 DTO
    private static final String NAS_PHOTO_INFO =
            "com.oplus.gallery.business_lib.nas.NasPhotoInfo";
    // 定位 NAS 照片 DTO 使用的媒体类型枚举
    private static final String NAS_PHOTO_MEDIA_TYPE = NAS_PHOTO_INFO + "$MediaType";
    // 定位 NAS 照片 DTO 使用的实况照片类型枚举
    private static final String NAS_PHOTO_LIVE_TYPE = NAS_PHOTO_INFO + "$LivePhotoType";

    // 解析 ColorOS 私有 DTO 的相册类加载器
    private final ClassLoader galleryClassLoader;
    // 写入 DTO 的 FEINIU 枚举槽位，用于继续复用 ColorOS NAS 页面
    private final Object feiniuProvider;

    // 固定当前相册版本的类加载器和 Provider 枚举值
    ColorOsNasDtoMapper(
            ClassLoader galleryClassLoader, // 解析 ColorOS 私有 DTO 的类加载器
            Object feiniuProvider // 当前相册版本的 FEINIU Provider 枚举值
    ) {
        this.galleryClassLoader = galleryClassLoader;
        this.feiniuProvider = feiniuProvider;
    }

    // 将内部相册列表逐项转换为 ColorOS NAS 相册 DTO
    List<Object> albums(List<RemoteAlbum> albums /* 已分页的群晖相册 */)
            throws ReflectiveOperationException {
        // 按输入顺序保存映射后的 ColorOS 相册 DTO
        List<Object> result = new ArrayList<>(albums.size());
        for (RemoteAlbum album : albums) { // 当前需要映射的群晖相册
            result.add(album(album));
        }
        return result;
    }

    // 按 ColorOS 16.50.8 构造器顺序构造单个 NAS 相册 DTO
    Object album(RemoteAlbum album /* 待暴露给 ColorOS 的群晖相册 */)
            throws ReflectiveOperationException {
        // 当前 ColorOS 相册 DTO 的运行时类型
        Class<?> type = Class.forName(ALBUM_DTO, false, galleryClassLoader);
        // 当前版本 NAS 相册 DTO 的精确十参数构造器
        Constructor<?> constructor = type.getDeclaredConstructor(
                feiniuProvider.getClass(),
                int.class,
                String.class,
                String.class,
                String.class,
                int.class,
                int.class,
                String.class,
                int.class,
                long.class
        );
        constructor.setAccessible(true);
        return constructor.newInstance(
                feiniuProvider,
                album.galleryId(),
                album.id(),
                album.name(),
                GalleryContract.DEVICE_ID,
                album.imageCount(),
                0,
                album.coverPhotoId(),
                album.coverGalleryId(),
                album.updateTimeMillis()
        );
    }

    // 将内部照片列表逐项转换为 ColorOS NAS 照片 DTO
    List<Object> photos(List<RemotePhoto> photos /* 已分页的群晖照片 */)
            throws ReflectiveOperationException {
        // 按输入顺序保存映射后的 ColorOS 照片 DTO
        List<Object> result = new ArrayList<>(photos.size());
        // 当前 ColorOS 照片 DTO 的运行时类型
        Class<?> type = Class.forName(NAS_PHOTO_INFO, false, galleryClassLoader);
        // 图片媒体类型枚举值，群晖清单已限制为图片
        Object image = enumValue(NAS_PHOTO_MEDIA_TYPE, "IMAGE");
        // 非实况照片枚举值，DSM File Station 当前只提供单文件图片
        Object noLivePhoto = enumValue(NAS_PHOTO_LIVE_TYPE, "NONE");
        // 当前版本 NAS 照片 DTO 的精确十三参数构造器
        Constructor<?> constructor = type.getDeclaredConstructor(
                int.class,
                String.class,
                String.class,
                String.class,
                long.class,
                Integer.class,
                Integer.class,
                String.class,
                Long.class,
                image.getClass(),
                noLivePhoto.getClass(),
                boolean.class,
                int.class
        );
        constructor.setAccessible(true);
        for (RemotePhoto photo : photos) { // 当前需要映射的群晖照片
            result.add(constructor.newInstance(
                    photo.galleryId(),
                    photo.id(),
                    GalleryContract.DEVICE_ID,
                    photo.media().name(),
                    photo.media().size(),
                    null,
                    null,
                    photo.media().mimeType(),
                    photo.media().modifiedSeconds() * 1_000L,
                    image,
                    noLivePhoto,
                    false,
                    0
            ));
        }
        return result;
    }

    // 用相册进程已有缓存构造 ColorOS 图库统计 DTO
    Object galleryStats(int photoCount /* 相册侧已缓存的群晖照片数量 */)
            throws ReflectiveOperationException {
        return ColorOsGalleryBridge.galleryStats(galleryClassLoader, photoCount);
    }

    // 解析当前 ColorOS 私有枚举值供 Provider 返回
    Object enumValue(
            String className, // 当前版本私有枚举的完整类名
            String value // 当前 Provider 合约要求返回的枚举常量名
    ) throws ReflectiveOperationException {
        return ColorOsNasReflection.enumValue(galleryClassLoader, className, value);
    }
}
