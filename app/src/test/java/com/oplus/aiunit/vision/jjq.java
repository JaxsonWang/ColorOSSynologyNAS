package com.oplus.aiunit.vision;

// 模拟 ColorOS NAS 图库统计的数据合同
public final class jjq {
    // 模拟 jjq.a 图库统计中的远端照片数量
    public final int a;
    // 模拟 jjq.b 图库统计中的远端视频数量
    public final int b;

    // 按 ColorOS 当前双计数合同创建图库统计夹具
    public jjq(
            int photoCount, // 远端照片数量
            int videoCount // 远端视频数量
    ) {
        this.a = photoCount;
        this.b = videoCount;
    }
}
