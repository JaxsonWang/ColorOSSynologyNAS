package com.oplus.aiunit.vision;

import com.oplus.gallery.business_lib.nas.NasDeviceAvailability;

// 模拟 ColorOS NAS 设备可用状态的数据合同
public final class srb {
    // 模拟 srb 的目标设备唯一标识字段
    public final String f24797a;
    // 模拟 srb.b NAS 设备连接状态字段
    public final NasDeviceAvailability b;

    // 按 ColorOS 当前双参数合同创建设备状态夹具
    public srb(
            String deviceId, // NAS 设备唯一标识
            NasDeviceAvailability availability // NAS 设备连接状态
    ) {
        this.f24797a = deviceId;
        this.b = availability;
    }
}
