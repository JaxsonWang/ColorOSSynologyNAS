package com.oplus.aiunit.vision;

import java.io.ByteArrayOutputStream;

// 模拟 ColorOS 原图回调的 Kotlin Function2 JVM 桥接合同
public final class uhq implements kotlin.jvm.functions.Function2<Object, Object, Object> {
    // 按调用顺序汇集收到的原图字节
    private final ByteArrayOutputStream output = new ByteArrayOutputStream();
    // 记录最后一次回调携带的完成标记
    private boolean completed;

    // 模拟当前 Kotlin Function2 在 JVM 上公开的双 Object 桥接方法
    @Override
    public Object invoke(
            Object bytesValue, // 回调收到的原图字节对象
            Object completedValue // 回调收到的完成标记对象
    ) {
        // 已按当前回调合同确认的原图字节分块
        byte[] bytes = (byte[]) bytesValue;
        output.writeBytes(bytes);
        completed = (Boolean) completedValue;
        return kotlin.Unit.INSTANCE;
    }

    // 返回按回调顺序汇集的全部原图字节
    public byte[] bytes() {
        return output.toByteArray();
    }

    // 返回最后一次原图回调是否标记完成
    public boolean completed() {
        return completed;
    }
}
