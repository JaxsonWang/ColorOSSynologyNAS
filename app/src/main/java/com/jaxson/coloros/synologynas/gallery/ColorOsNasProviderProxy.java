package com.jaxson.coloros.synologynas.gallery;

import android.util.Log;

import com.jaxson.coloros.synologynas.backup.BackupUploadResult;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ColorOsNasProviderProxy implements InvocationHandler {
    private static final String NAS_PROVIDER =
            "com.oplus.gallery.business_lib.nas.NasProvider";
    private static final String NAS_PHOTO_INFO =
            "com.oplus.gallery.business_lib.nas.NasPhotoInfo";
    private static final String NAS_PHOTO_MEDIA_TYPE = NAS_PHOTO_INFO + "$MediaType";
    private static final String NAS_PHOTO_LIVE_TYPE = NAS_PHOTO_INFO + "$LivePhotoType";
    private static final String NAS_AVAILABILITY =
            "com.oplus.gallery.business_lib.nas.NasPhotosAvailabilityStatus";
    private static final String NAS_APP_STATUS =
            "com.oplus.gallery.business_lib.nas.NasPhotosAppStatus";
    private static final String ALBUM_DTO = "com.oplus.aiunit.vision.b3q";
    private static final String DOWNLOAD_PROGRESS = "com.oplus.aiunit.vision.wac";
    private static final String DOWNLOAD_HANDLE = "com.oplus.aiunit.vision.z8g";
    private static final String BACKUP_HASH_RESULT = "com.oplus.aiunit.vision.yjq";
    private static final String BACKUP_UPLOAD_RESULT = "com.oplus.aiunit.vision.teq";
    private static final String BACKUP_PATH = "com.oplus.aiunit.vision.ycq";
    private static final String BACKUP_UPLOAD_ERROR =
            "com.oplus.gallery.framework.abilities.cloudsync.nas.api.model."
                    + "NasBackupUploadErrorCode";
    private static final String TAG = "ColorOSSynologyNAS";
    private static final Logger LOGGER = Logger.getLogger(TAG);

    private static final Set<Object> SYNTHETIC_DOWNLOADS = Collections.synchronizedSet(
            Collections.newSetFromMap(new WeakHashMap<>())
    );

    private final Object original;
    private final ClassLoader galleryClassLoader;
    private final GalleryRemoteClient client;
    private final GalleryBackupService backupService;
    private final Object feiniuProvider;
    private final IntSupplier photoCount;

    private ColorOsNasProviderProxy(
            GalleryRemoteClient client,
            GalleryBackupService backupService,
            Object original,
            ClassLoader classLoader,
            IntSupplier photoCount
    )
            throws ReflectiveOperationException {
        this.original = original;
        galleryClassLoader = classLoader;
        this.client = client;
        this.backupService = backupService;
        this.photoCount = photoCount;
        feiniuProvider = enumValue(NAS_PROVIDER, "FEINIU");
    }

    public static Object create(
            GalleryRemoteClient client,
            GalleryBackupService backupService,
            Object original,
            ClassLoader classLoader,
            IntSupplier photoCount
    )
            throws ReflectiveOperationException {
        Class<?> providerInterface = Class.forName(
                "com.oplus.aiunit.vision.dpk",
                false,
                classLoader
        );
        ColorOsNasProviderProxy handler = new ColorOsNasProviderProxy(
                client,
                backupService,
                original,
                classLoader,
                photoCount
        );
        return Proxy.newProxyInstance(
                providerInterface.getClassLoader(),
                new Class<?>[]{providerInterface},
                handler
        );
    }

    public static boolean isSynologyProvider(Object value) {
        if (value == null || !Proxy.isProxyClass(value.getClass())) {
            return false;
        }
        return Proxy.getInvocationHandler(value) instanceof ColorOsNasProviderProxy;
    }

    public static boolean shouldSuppressCancel(Object value) {
        return SYNTHETIC_DOWNLOADS.remove(value);
    }

    public boolean isConfigured() {
        return client.isConfigured();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String name = method.getName();
        if (method.getDeclaringClass() == Object.class) {
            return invokeObjectMethod(proxy, name, args);
        }
        if ("b".equals(name)) {
            return feiniuProvider;
        }
        if ("c".equals(name)) {
            return true;
        }
        if (!targetsSynology(name, args)) {
            return invokeOriginal(method, args);
        }

        return switch (name) {
            case "a" -> getAlbum((String) args[1]);
            case "d" -> backupService.isEnabled() ? 1 : 0;
            case "e" -> enumValue(
                    NAS_AVAILABILITY,
                    client.isConfigured() ? "AVAILABLE" : "UNKNOWN"
            );
            case "g" -> 0L;
            case "h" -> null;
            case "i" -> hashResult((List<String>) args[1]);
            case "j", "q", "u" -> null;
            case "k", "r" -> upload(args[0]);
            case "l" -> listAlbums((Integer) args[0], (Integer) args[1]);
            case "m" -> backupService.isEnabled() ? 1 : 0;
            case "n" -> enumValue(
                    NAS_APP_STATUS,
                    client.isConfigured() ? "RUNNING" : "STOPPED"
            );
            case "o" -> galleryStats();
            case "p" -> deletePhotos((List<String>) args[1]);
            case "s" -> hashResult((List<String>) args[1]);
            case "t" -> listPhotos(
                    (String) args[3],
                    (Integer) args[0],
                    (Integer) args[1]
            );
            case "v" -> new byte[0];
            case "w" -> download((String) args[1], args[2]);
            case "x" -> thumbnail((String) args[1], args[2]);
            default -> invokeOriginal(method, args);
        };
    }

    private Object invokeObjectMethod(Object proxy, String name, Object[] args) {
        return switch (name) {
            case "toString" -> "SynologyDsm7NasProviderProxy";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            default -> throw new IllegalStateException("Unsupported Object method: " + name);
        };
    }

    private boolean targetsSynology(String methodName, Object[] args) {
        if (args == null) {
            return false;
        }
        if (("k".equals(methodName) || "r".equals(methodName)) && args.length > 0) {
            try {
                return GalleryContract.DEVICE_ID.equals(
                        GalleryBackupClient.targetDeviceId(args[0])
                );
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("ColorOS 备份请求目标设备解析失败", error);
            }
        }
        int deviceIndex = switch (methodName) {
            case "l", "t" -> 2;
            default -> 0;
        };
        return args.length > deviceIndex
                && GalleryContract.DEVICE_ID.equals(args[deviceIndex]);
    }

    private Object invokeOriginal(Method interfaceMethod, Object[] args) throws Throwable {
        try {
            return interfaceMethod.invoke(original, args);
        } catch (InvocationTargetException error) {
            throw error.getCause() == null ? error : error.getCause();
        }
    }

    private Object getAlbum(String albumId) throws Exception {
        Log.i(TAG, "DSM get album");
        return album(client.getAlbum(albumId));
    }

    private List<Object> listAlbums(int offset, int limit) throws Exception {
        Log.i(TAG, "DSM list albums");
        return albums(client.listAlbums(offset, limit));
    }

    private List<Object> listPhotos(String albumId, int offset, int limit) throws Exception {
        Log.i(TAG, "DSM list photos");
        return photos(client.listPhotos(albumId, offset, limit));
    }

    private Object galleryStats() throws ReflectiveOperationException {
        int cachedPhotoCount = photoCount.getAsInt();
        return ColorOsGalleryBridge.galleryStats(galleryClassLoader, cachedPhotoCount);
    }

    private Object hashResult(List<String> hashes) throws ReflectiveOperationException {
        try {
            Set<String> existing = backupService.findExistingHashes(hashes);
            LOGGER.info("DSM backup hash query completed: requested="
                    + hashes.size() + ", existing=" + existing.size());
            Class<?> type = Class.forName(
                    BACKUP_HASH_RESULT + "$b",
                    false,
                    galleryClassLoader
            );
            Constructor<?> constructor = type.getDeclaredConstructor(Set.class);
            constructor.setAccessible(true);
            return constructor.newInstance(existing);
        } catch (ReflectiveOperationException error) {
            throw error;
        } catch (Exception error) {
            LOGGER.log(Level.WARNING, "DSM backup hash query failed", error);
            Class<?> type = Class.forName(
                    BACKUP_HASH_RESULT + "$a",
                    false,
                    galleryClassLoader
            );
            Constructor<?> constructor = type.getDeclaredConstructor(int.class, String.class);
            constructor.setAccessible(true);
            return constructor.newInstance(-1, errorMessage(error));
        }
    }

    private Object upload(Object colorOsRequest) throws ReflectiveOperationException {
        BackupUploadResult result = backupService.upload(colorOsRequest);
        if (result.status() == BackupUploadResult.Status.SUCCESS) {
            LOGGER.info("DSM backup upload completed: bytes=" + result.bytesWritten());
            return uploadSuccess(result);
        }
        String errorCode = result.status() == BackupUploadResult.Status.ALREADY_EXISTS
                ? "FILE_ALREADY_EXISTS"
                : result.errorCode().name();
        LOGGER.warning("DSM backup upload result: code=" + errorCode
                + ", message=" + result.message());
        return uploadFailure(errorCode, result.message());
    }

    private Object uploadSuccess(BackupUploadResult result)
            throws ReflectiveOperationException {
        Class<?> pathType = Class.forName(BACKUP_PATH, false, galleryClassLoader);
        Constructor<?> pathConstructor = pathType.getDeclaredConstructor(
                String.class,
                String.class,
                long.class,
                long.class,
                boolean.class
        );
        pathConstructor.setAccessible(true);
        Object backupPath = pathConstructor.newInstance(
                result.backupFolder(),
                result.backupFolder(),
                0L,
                0L,
                true
        );

        Class<?> resultType = Class.forName(
                BACKUP_UPLOAD_RESULT + "$b",
                false,
                galleryClassLoader
        );
        Constructor<?> resultConstructor = resultType.getDeclaredConstructor(
                pathType,
                String.class,
                long.class,
                int.class,
                int.class,
                ArrayList.class
        );
        resultConstructor.setAccessible(true);
        return resultConstructor.newInstance(
                backupPath,
                result.savedPath(),
                result.bytesWritten(),
                1,
                0,
                new ArrayList<>()
        );
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object uploadFailure(String errorCode, String message)
            throws ReflectiveOperationException {
        Class<?> errorType = Class.forName(BACKUP_UPLOAD_ERROR, false, galleryClassLoader);
        Object code = Enum.valueOf((Class<? extends Enum>) errorType, errorCode);
        Class<?> resultType = Class.forName(
                BACKUP_UPLOAD_RESULT + "$a",
                false,
                galleryClassLoader
        );
        Constructor<?> constructor = resultType.getDeclaredConstructor(
                errorType,
                String.class,
                Integer.class
        );
        constructor.setAccessible(true);
        return constructor.newInstance(code, message, null);
    }

    private static String errorMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank()
                ? error.getClass().getSimpleName()
                : message;
    }

    private byte[] thumbnail(String photoId, Object colorOsSize) throws Exception {
        Log.i(TAG, "DSM load thumbnail");
        return client.getThumbnail(photoId, thumbnailSize(colorOsSize));
    }

    private boolean deletePhotos(List<String> photoIds) {
        Log.i(TAG, "DSM delete photos");
        try {
            boolean deleted = client.deletePhotos(photoIds);
            if (deleted) {
                Log.i(TAG, "DSM delete photos completed");
            }
            return deleted;
        } catch (Exception error) {
            Log.e(
                    TAG,
                    "DSM delete photos failed: "
                            + error.getClass().getSimpleName()
                            + ": "
                            + errorMessage(error),
                    error
            );
            return false;
        }
    }

    private List<Object> albums(List<RemoteAlbum> albums) throws ReflectiveOperationException {
        List<Object> result = new ArrayList<>(albums.size());
        for (RemoteAlbum album : albums) {
            result.add(album(album));
        }
        return result;
    }

    private Object album(RemoteAlbum album) throws ReflectiveOperationException {
        Class<?> type = Class.forName(ALBUM_DTO, false, galleryClassLoader);
        Constructor<?> constructor = type.getDeclaredConstructors()[0];
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

    private List<Object> photos(List<RemotePhoto> photos) throws ReflectiveOperationException {
        List<Object> result = new ArrayList<>(photos.size());
        Class<?> type = Class.forName(NAS_PHOTO_INFO, false, galleryClassLoader);
        Constructor<?> constructor = type.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        Object image = enumValue(NAS_PHOTO_MEDIA_TYPE, "IMAGE");
        Object noLivePhoto = enumValue(NAS_PHOTO_LIVE_TYPE, "NONE");
        for (RemotePhoto photo : photos) {
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

    private Object download(String photoId, Object callback) throws Exception {
        Log.i(TAG, "DSM stream original");
        long bytes = client.streamOriginal(photoId, callback);
        Object progress = newDownloadProgress(photoId, bytes);
        Object channel = newCompletedChannel(progress);

        Class<?> type = Class.forName(DOWNLOAD_HANDLE, false, galleryClassLoader);
        Constructor<?> constructor = type.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        Object handle = constructor.newInstance(
                channel,
                new AtomicReference<>(),
                null,
                null,
                GalleryContract.DEVICE_ID,
                photoId
        );
        SYNTHETIC_DOWNLOADS.add(handle);
        return handle;
    }

    private Object newDownloadProgress(String photoId, long bytes)
            throws ReflectiveOperationException {
        Class<?> type = Class.forName(DOWNLOAD_PROGRESS, false, galleryClassLoader);
        Constructor<?> constructor = type.getDeclaredConstructor(
                String.class,
                long.class,
                long.class,
                int.class,
                boolean.class
        );
        constructor.setAccessible(true);
        return constructor.newInstance(photoId, bytes, bytes, 100, true);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object newCompletedChannel(Object progress) throws ReflectiveOperationException {
        Class<?> channelKt = Class.forName(
                "kotlinx.coroutines.channels.ChannelKt",
                false,
                galleryClassLoader
        );
        Class<?> overflowType = Class.forName(
                "kotlinx.coroutines.channels.BufferOverflow",
                false,
                galleryClassLoader
        );
        Class<?> function1 = Class.forName(
                "kotlin.jvm.functions.Function1",
                false,
                galleryClassLoader
        );
        Object suspend = Enum.valueOf((Class<? extends Enum>) overflowType, "SUSPEND");
        Method channelFactory = channelKt.getMethod(
                "Channel",
                int.class,
                overflowType,
                function1
        );
        Object channel = channelFactory.invoke(null, Integer.MAX_VALUE, suspend, null);

        Method trySend = null;
        for (Method candidate : channel.getClass().getMethods()) {
            if (candidate.getName().startsWith("trySend")
                    && candidate.getParameterCount() == 1) {
                trySend = candidate;
                break;
            }
        }
        if (trySend == null) {
            throw new NoSuchMethodException("Channel.trySend");
        }
        trySend.setAccessible(true);
        trySend.invoke(channel, progress);
        Method close = channel.getClass().getMethod("close", Throwable.class);
        close.invoke(channel, new Object[]{null});
        return channel;
    }

    private String thumbnailSize(Object colorOsSize) {
        return "THUMBNAIL_SIZE_L".equals(String.valueOf(colorOsSize))
                ? GalleryContract.THUMBNAIL_LARGE
                : GalleryContract.THUMBNAIL_SMALL;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object enumValue(String className, String value)
            throws ReflectiveOperationException {
        Class<?> type = Class.forName(className, false, galleryClassLoader);
        return Enum.valueOf((Class<? extends Enum>) type, value);
    }
}
