package com.oplus.aiunit.vision;

// 模拟 ColorOS NAS 备份目标路径的数据合同
public final class ycq {
    // 模拟 ycq.a 的临时或备份目录字段
    public final String a;
    // 模拟 ycq.b 的展示目录字段
    public final String b;
    // 模拟 ycq.c 的当前配额字段
    public final long c;
    // 模拟 ycq.d 的最大配额字段
    public final long d;
    // 模拟 ycq.e 的目录访问权限字段
    public final boolean e;

    // 按 ColorOS 当前五参数合同创建备份路径夹具
    public ycq(
            String tmpPath, // 临时或备份目录
            String displayPath, // 对相册展示的目录
            long quotaCurrent, // 当前已使用配额
            long quotaMax, // 最大可用配额
            boolean hasAccess // 是否允许访问该目录
    ) {
        this.a = tmpPath;
        this.b = displayPath;
        this.c = quotaCurrent;
        this.d = quotaMax;
        this.e = hasAccess;
    }
}
