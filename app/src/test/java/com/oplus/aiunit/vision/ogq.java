package com.oplus.aiunit.vision;

// 模拟 ColorOS 首页展示的 NAS 设备信息合同
public final class ogq {
    // 模拟 ogq 的内部数字标识字段
    public final int f19603a;
    // 模拟 ogq.b 设备唯一标识字段
    public final String b;
    // 模拟 ogq.c 设备品牌名称字段
    public final String c;
    // 模拟 ogq.d 设备连接状态字段
    public final int d;
    // 模拟 ogq.e 设备绑定状态字段
    public final int e;
    // 模拟 ogq.f 用户名或 NAS 型号字段
    public final String f;
    // 模拟 ogq.g 相册数量字段
    public final int g;
    // 模拟 ogq.h 最近更新时间字段
    public final long h;

    // 按 ColorOS 当前八参数合同创建首页 NAS 设备信息夹具
    public ogq(
            int id, // 内部数字标识
            int deviceStatus, // 设备连接状态
            int bindStatus, // 设备绑定状态
            int albumCount, // 远端相册数量
            long lastUpdateTime, // 最近更新时间
            String deviceUserId, // NAS 设备唯一标识
            String deviceName, // NAS 设备品牌名称
            String userName // 用户名或 NAS 型号展示值
    ) {
        this.f19603a = id;
        this.b = deviceUserId;
        this.c = deviceName;
        this.d = deviceStatus;
        this.e = bindStatus;
        this.f = userName;
        this.g = albumCount;
        this.h = lastUpdateTime;
    }
}
