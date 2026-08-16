package com.jaxson.coloros.synologynas.dsm;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.util.Size;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

final class LocalThumbnailGenerator {
    private static final int JPEG_QUALITY = 88;

    private LocalThumbnailGenerator() {
    }

    static void generate(File source, int maxDimension, OutputStream output) throws IOException {
        if (maxDimension <= 0) {
            throw new IllegalArgumentException("缩略图最大尺寸必须大于零");
        }

        ImageDecoder.Source imageSource = ImageDecoder.createSource(source);
        Bitmap decoded = ImageDecoder.decodeBitmap(imageSource, (decoder, info, ignored) -> {
            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
            decoder.setMemorySizePolicy(ImageDecoder.MEMORY_POLICY_LOW_RAM);
            Size sourceSize = info.getSize();
            int largestDimension = Math.max(sourceSize.getWidth(), sourceSize.getHeight());
            if (largestDimension > maxDimension) {
                float scale = (float) maxDimension / largestDimension;
                decoder.setTargetSize(
                        Math.max(1, Math.round(sourceSize.getWidth() * scale)),
                        Math.max(1, Math.round(sourceSize.getHeight() * scale))
                );
            }
        });

        Bitmap encoded = decoded;
        try {
            if (decoded.hasAlpha()) {
                encoded = Bitmap.createBitmap(
                        decoded.getWidth(),
                        decoded.getHeight(),
                        Bitmap.Config.ARGB_8888
                );
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
