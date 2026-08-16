package com.oplus.gallery.business_lib.nas;

// 模拟 ColorOS NAS Photos 运行状态枚举全集
public enum NasPhotosAppStatus {
    RUNNING, // 模拟 NAS Photos 正在运行
    STOPPED, // 模拟 NAS Photos 已停止
    INSTALLING, // 模拟 NAS Photos 正在安装
    UPDATING, // 模拟 NAS Photos 正在更新
    ERROR, // 模拟 NAS Photos 运行错误
    NO_INSTALLED, // 模拟 NAS Photos 尚未安装
    UNKNOWN // 模拟 NAS Photos 运行状态未知
}
