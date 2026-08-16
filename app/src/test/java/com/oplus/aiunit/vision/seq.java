package com.oplus.aiunit.vision;

import java.util.List;

public final class seq {
    // 模拟 seq.a 目标 NAS 设备标识字段
    public final String a;
    // 模拟 seq.b 手机设备标识字段
    public final String b;
    // 模拟 seq.c 手机设备名称字段
    public final String c;
    // 模拟 seq.d 照片原始文件名字段
    public final String d;
    // 模拟 seq.e 照片字节数字段
    public final long e;
    // 模拟 seq.f 输入流生成器字段
    public final Object f;
    // 模拟 seq.g 当前未参与群晖合同的字符串字段
    public final String g;
    // 模拟 seq.h ColorOS 原生照片 hash 字段
    public final String h;
    // 模拟 seq.i 文件创建时间字段
    public final String i;
    // 模拟 seq.j 当前未参与群晖合同的字符串字段
    public final String j;
    // 模拟 seq.k 手机相册名称列表字段
    public final List<String> k;
    // 模拟 seq.l 输入流缓冲区大小字段
    public final int l;

    // 按 ColorOS 当前九参数合同创建备份上传请求夹具
    public seq(
            String targetDeviceUserId, // 目标 NAS 设备唯一标识
            String deviceId, // 发起备份的手机设备标识
            String deviceName, // 发起备份的手机设备名称
            String originalName, // 照片原始文件名
            long fileSize, // 照片字节数
            Object inputStreamProvider, // 照片输入流生成器
            String fileHash, // ColorOS 原生照片 hash
            String fileCreateTime, // 照片创建时间文本
            List<String> deviceAlbumNames // 手机端相册名称列表
    ) {
        this.a = targetDeviceUserId;
        this.b = deviceId;
        this.c = deviceName;
        this.d = originalName;
        this.e = fileSize;
        this.f = inputStreamProvider;
        this.g = "";
        this.h = fileHash;
        this.i = fileCreateTime;
        this.j = "";
        this.k = deviceAlbumNames;
        this.l = 65_536;
    }
}
