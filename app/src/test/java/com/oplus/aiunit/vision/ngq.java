package com.oplus.aiunit.vision;

import com.oplus.gallery.business_lib.nas.NasProvider;

public final class ngq {
    // 模拟 ColorOS NAS 设备归属的 Provider 枚举
    public final NasProvider provider;
    // 模拟 ngq.b 目标设备唯一标识字段
    public final String b;
    // 模拟 ngq.c 设备品牌名称字段
    public final String c;
    // 模拟 ngq.d 用户名或 NAS 型号展示字段
    public final String d;
    // 模拟 ngq.e NAS 服务地址字段
    public final String e;
    // 模拟 ngq.f 访问令牌字段
    public final String f;
    // 模拟 ngq.g 设备连接状态字段
    public final int g;
    // 模拟 ngq.h 刷新令牌字段
    public final String h;
    // 模拟 ngq.i 令牌过期时间字段
    public final long i;
    // 模拟 ngq.j 管理员标记字段
    public final boolean j;
    // 模拟 ngq.k 相册数量字段
    public final int k;

    // 按 ColorOS 当前十一参数合同创建 NAS 设备夹具
    public ngq(
            NasProvider provider, // 设备归属的 NAS Provider
            String deviceUserId, // NAS 设备唯一标识
            String deviceName, // NAS 设备品牌名称
            String userName, // 用户名或 NAS 型号展示值
            String url, // NAS 服务地址
            String accessToken, // 访问令牌测试值
            int status, // 设备连接状态
            String refreshToken, // 刷新令牌测试值
            long expiresAt, // 令牌过期时间
            boolean admin, // 管理员标记
            int albumCount // 远端相册数量
    ) {
        this.provider = provider;
        this.b = deviceUserId;
        this.c = deviceName;
        this.d = userName;
        this.e = url;
        this.f = accessToken;
        this.g = status;
        this.h = refreshToken;
        this.i = expiresAt;
        this.j = admin;
        this.k = albumCount;
    }
}
