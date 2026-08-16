package com.oplus.aiunit.vision;

public final class e5q extends oe2<ogq> {
    // 模拟首页 NAS 分组中的设备数量字段
    public final int c;
    // 模拟首页 NAS 分组中的设备信息字段
    public final ogq d;

    // 按 ColorOS 当前三参数合同创建首页 NAS 分组夹具
    public e5q(
            int type, // 首页分组类型
            int count, // 分组内设备数量
            ogq nasDeviceInfo // 首页展示的 NAS 设备信息
    ) {
        super(type, nasDeviceInfo);
        this.c = count;
        this.d = nasDeviceInfo;
    }
}
