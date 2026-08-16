package com.jaxson.coloros.synologynas.gallery;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

public final class GalleryRemoteClient {
    // 提供群晖配置、图库清单、媒体读取和删除能力的数据源
    private final RemoteGalleryDataSource repository;

    // 将 ColorOS 适配层绑定到唯一远端图库数据源
    public GalleryRemoteClient(RemoteGalleryDataSource repository /* 远端图库数据源 */) {
        this.repository = repository;
    }

    // 返回相册进程当前是否已经取得完整群晖配置
    public boolean isConfigured() {
        return repository.isConfigured();
    }

    // 读取配置中上次连接确认的 NAS 型号，不在客户端保存第二份状态
    public String configuredDeviceModel() throws IOException {
        return repository.configuredDeviceModel();
    }

    // 实时探测 NAS 型号，并拒绝 DSM 返回的空型号
    public String probeDeviceModel() throws IOException {
        // DSM 本次实时返回的 NAS 型号
        String model = repository.probeDeviceModel();
        if (model.isBlank()) {
            throw new IOException("DSM 返回了空 NAS 型号");
        }
        return model;
    }

    // 分页读取群晖相册列表
    public List<RemoteAlbum> listAlbums(
            int offset, // ColorOS 请求的相册起始偏移
            int limit // ColorOS 请求的相册最大数量
    ) throws IOException {
        return repository.listAlbums(offset, limit);
    }

    // 按稳定相册标识读取一个群晖相册
    public RemoteAlbum getAlbum(String albumId /* 远端相册稳定标识 */)
            throws IOException {
        return repository.getAlbum(albumId);
    }

    // 分页读取指定群晖相册的照片
    public List<RemotePhoto> listPhotos(
            String albumId, // 远端相册稳定标识
            int offset, // ColorOS 请求的照片起始偏移
            int limit // ColorOS 请求的照片最大数量
    ) throws IOException {
        return repository.listPhotos(albumId, offset, limit);
    }

    // 将群晖缩略图读取到内存并作为 ColorOS dpk.x 返回值交付
    public byte[] getThumbnail(
            String photoId, // 远端照片稳定标识
            String size // 群晖客户端支持的缩略图尺寸标识
    ) throws IOException {
        try (ByteArrayOutputStream output /* 汇集本次缩略图字节 */ =
                     new ByteArrayOutputStream()) {
            repository.downloadThumbnail(photoId, size, output);
            return output.toByteArray();
        }
    }

    // 将群晖原图按读取分块写入 ColorOS 回调并返回实际字节数
    public long streamOriginal(
            String photoId, // 远端照片稳定标识
            Object callback // ColorOS 接收字节分块与完成标记的回调
    ) throws IOException {
        // 当前 ColorOS 回调中固定接收 byte[] 和 boolean 的 invoke 方法
        Method callbackMethod = findCallbackMethod(callback);
        // 将 OutputStream 写入适配为 ColorOS callback.invoke 调用
        CallbackOutputStream output = new CallbackOutputStream(callback, callbackMethod);
        repository.downloadOriginal(photoId, output);
        output.complete();
        return output.bytesWritten();
    }

    // 将 ColorOS 删除操作映射到远端图库数据源
    public boolean deletePhotos(List<String> photoIds /* 待删除的远端照片标识 */)
            throws IOException {
        return repository.deletePhotos(photoIds);
    }

    // 定位当前 ColorOS 原图回调固定的双参数 invoke 方法
    private static Method findCallbackMethod(Object callback /* ColorOS 原图回调对象 */)
            throws IOException {
        for (Method method : callback.getClass().getMethods()) { // 回调公开候选方法
            if ("invoke".equals(method.getName()) && method.getParameterCount() == 2) {
                method.setAccessible(true);
                return method;
            }
        }
        throw new IOException("ColorOS 相册原图回调契约不匹配");
    }

    // 调用 ColorOS 原图回调，并将反射失败映射为明确的图片写入错误
    private static void invokeCallback(
            Object callback, // ColorOS 原图回调对象
            Method method, // 已确认的双参数 invoke 方法
            byte[] bytes, // 本次交付给相册的原图字节分块
            boolean completed // 是否为最后一次完成通知
    ) throws IOException {
        try {
            method.invoke(callback, bytes, completed);
        } catch (IllegalAccessException | InvocationTargetException error
                 /* ColorOS 原图回调调用异常 */) {
            // 反射包装异常中需要向上游暴露的真实失败原因
            Throwable cause = error instanceof InvocationTargetException
                    && ((InvocationTargetException) error).getCause() != null
                    ? ((InvocationTargetException) error).getCause()
                    : error;
            throw new IOException("ColorOS 相册写入群晖原图失败", cause);
        }
    }

    private static final class CallbackOutputStream extends OutputStream {
        // 接收群晖原图分块的 ColorOS 回调对象
        private final Object callback;
        // 已确认的 ColorOS 双参数 invoke 方法
        private final Method callbackMethod;

        // 累计已经成功交付给 ColorOS 回调的原图字节数
        private long bytesWritten;

        // 将一个 ColorOS 回调及其 invoke 方法包装为顺序输出流
        private CallbackOutputStream(
                Object callback, // ColorOS 原图回调对象
                Method callbackMethod // 已确认的双参数 invoke 方法
        ) {
            this.callback = callback;
            this.callbackMethod = callbackMethod;
        }

        @Override
        // 将单字节写入转换为标准分块写入路径
        public void write(int value /* OutputStream 约定的低八位字节值 */)
                throws IOException {
            write(new byte[]{(byte) value}, 0, 1);
        }

        @Override
        // 将非空字节窗口复制为独立分块并立即交付 ColorOS 回调
        public void write(
                byte[] bytes, // 调用方提供的源字节缓冲区
                int offset, // 本次有效数据在缓冲区中的起始位置
                int length // 本次需要交付的有效字节数量
        ) throws IOException {
            if (length == 0) {
                return;
            }
            // 与源缓冲区生命周期解耦后交付 ColorOS 的独立字节分块
            byte[] chunk = Arrays.copyOfRange(bytes, offset, offset + length);
            invokeCallback(callback, callbackMethod, chunk, false);
            bytesWritten += length;
        }

        // 在全部原图字节写入成功后向 ColorOS 发送唯一完成标记
        private void complete() throws IOException {
            invokeCallback(callback, callbackMethod, new byte[0], true);
        }

        // 返回已经成功交付给 ColorOS 回调的原图字节总数
        private long bytesWritten() {
            return bytesWritten;
        }
    }
}
