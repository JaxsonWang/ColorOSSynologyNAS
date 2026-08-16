package com.jaxson.coloros.synologynas.dsm;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.util.Size;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

/** 使用 Android ImageDecoder 为 DSM 特殊图片格式生成有界 JPEG 缩略图 */
final class LocalThumbnailGenerator {
    /** JPEG 编码质量保持真机已验收的 88 */
    private static final int JPEG_QUALITY = 88;

    /** 工具类不允许实例化 */
    private LocalThumbnailGenerator() {
    }

    /**
     * 解码临时源文件、按最大边缩放并写出不含透明通道的 JPEG
     *
     * @param source 本次调用下载的临时原图
     * @param maxDimension 缩略图最大边长
     * @param output 调用方持有的 JPEG 输出流
     * @throws IOException 解码或编码失败
     */
    static void generate(File source, int maxDimension, OutputStream output) throws IOException {
        if (maxDimension <= 0) {
            throw new IllegalArgumentException("缩略图最大尺寸必须大于零");
        }

        // imageSource 是指向本次临时原图的 ImageDecoder 输入
        ImageDecoder.Source imageSource = ImageDecoder.createSource(source);
        // decoded 是经过软件解码和有界缩放的位图
        Bitmap decoded = ImageDecoder.decodeBitmap(
                imageSource,
                (/* 当前解码器 */ ImageDecoder decoder,
                 /* 图片头部信息 */ ImageDecoder.ImageInfo info,
                 /* 未使用的源参数 */ ImageDecoder.Source ignored) -> {
                    decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                    decoder.setMemorySizePolicy(ImageDecoder.MEMORY_POLICY_LOW_RAM);
                    // sourceSize 是原图宽高
                    Size sourceSize = info.getSize();
                    // largestDimension 是决定是否缩放的原图最大边
                    int largestDimension = Math.max(
                            sourceSize.getWidth(),
                            sourceSize.getHeight()
                    );
                    if (largestDimension > maxDimension) {
                        // scale 是保持宽高比的目标缩放系数
                        float scale = (float) maxDimension / largestDimension;
                        decoder.setTargetSize(
                                Math.max(1, Math.round(sourceSize.getWidth() * scale)),
                                Math.max(1, Math.round(sourceSize.getHeight() * scale))
                        );
                    }
                }
        );

        // encoded 默认复用解码位图；有透明通道时改用白底位图
        Bitmap encoded = decoded;
        try {
            if (decoded.hasAlpha()) {
                encoded = Bitmap.createBitmap(
                        decoded.getWidth(),
                        decoded.getHeight(),
                        Bitmap.Config.ARGB_8888
                );
                // canvas 用白色背景合成透明图片，避免 JPEG 黑底
                Canvas canvas = new Canvas(encoded);
                canvas.drawColor(Color.WHITE);
                canvas.drawBitmap(decoded, 0.0f, 0.0f, null);
            }
            if (!encoded.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
                throw new IOException("本地缩略图 JPEG 编码失败");
            }
        } finally {
            if (encoded != decoded) {
                encoded.recycle();
            }
            decoded.recycle();
        }
    }
}
