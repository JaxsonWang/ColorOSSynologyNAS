package com.oplus.aiunit.vision;

import kotlinx.coroutines.flow.Flow;

// 模拟 ColorOS 原图下载句柄公开接口
public interface yok {
    // 表达 ColorOS 取消原图下载的句柄合同
    void cancel();

    // 表达 ColorOS 订阅原图下载进度的句柄合同
    Flow<wac> getProgress();
}
