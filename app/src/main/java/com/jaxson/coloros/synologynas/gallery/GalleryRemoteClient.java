package com.jaxson.coloros.synologynas.gallery;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

public final class GalleryRemoteClient {
    private final RemoteGalleryDataSource repository;
    private volatile String currentDeviceModel = GalleryContract.DEFAULT_DEVICE_MODEL;

    public GalleryRemoteClient(RemoteGalleryDataSource repository) {
        this.repository = repository;
    }

    public boolean isConfigured() {
        return repository.isConfigured();
    }

    public String configuredDeviceModel() throws IOException {
        String configured = repository.configuredDeviceModel();
        if (!configured.isBlank()) {
            currentDeviceModel = configured;
        }
        return currentDeviceModel;
    }

    public String probeDeviceModel() throws IOException {
        String model = repository.probeDeviceModel();
        if (model.isBlank()) {
            throw new IOException("DSM 返回了空 NAS 型号");
        }
        currentDeviceModel = model;
        return model;
    }

    public String currentDeviceModel() {
        return currentDeviceModel;
    }

    public List<RemoteAlbum> listAlbums(int offset, int limit) throws IOException {
        return repository.listAlbums(offset, limit);
    }

    public RemoteAlbum getAlbum(String albumId) throws IOException {
        return repository.getAlbum(albumId);
    }

    public List<RemotePhoto> listPhotos(String albumId, int offset, int limit)
            throws IOException {
        return repository.listPhotos(albumId, offset, limit);
    }

    public int photoCount() throws IOException {
        return repository.photoCount();
    }

    public byte[] getThumbnail(String photoId, String size) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            repository.downloadThumbnail(photoId, size, output);
            return output.toByteArray();
        }
    }

    public long streamOriginal(String photoId, Object callback) throws IOException {
        Method callbackMethod = findCallbackMethod(callback);
        CallbackOutputStream output = new CallbackOutputStream(callback, callbackMethod);
        repository.downloadOriginal(photoId, output);
        output.complete();
        return output.bytesWritten();
    }

    public boolean deletePhotos(List<String> photoIds) throws IOException {
        return repository.deletePhotos(photoIds);
    }

    private static Method findCallbackMethod(Object callback) throws IOException {
        for (Method method : callback.getClass().getMethods()) {
            if ("invoke".equals(method.getName()) && method.getParameterCount() == 2) {
                method.setAccessible(true);
                return method;
            }
        }
        throw new IOException("ColorOS 相册原图回调契约不匹配");
    }

    private static void invokeCallback(
            Object callback,
            Method method,
            byte[] bytes,
            boolean completed
    ) throws IOException {
        try {
            method.invoke(callback, bytes, completed);
        } catch (IllegalAccessException | InvocationTargetException error) {
            Throwable cause = error instanceof InvocationTargetException
                    && ((InvocationTargetException) error).getCause() != null
                    ? ((InvocationTargetException) error).getCause()
                    : error;
            throw new IOException("ColorOS 相册写入群晖原图失败", cause);
        }
    }

    private static final class CallbackOutputStream extends OutputStream {
        private final Object callback;
        private final Method callbackMethod;

        private long bytesWritten;

        private CallbackOutputStream(Object callback, Method callbackMethod) {
            this.callback = callback;
            this.callbackMethod = callbackMethod;
        }

        @Override
        public void write(int value) throws IOException {
            write(new byte[]{(byte) value}, 0, 1);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            if (length == 0) {
                return;
            }
            byte[] chunk = Arrays.copyOfRange(bytes, offset, offset + length);
            invokeCallback(callback, callbackMethod, chunk, false);
            bytesWritten += length;
        }

        private void complete() throws IOException {
            invokeCallback(callback, callbackMethod, new byte[0], true);
        }

        private long bytesWritten() {
            return bytesWritten;
        }
    }
}
