package com.oplus.aiunit.vision;

// 模拟 ColorOS 首页分组的泛型基础数据合同
public class oe2<T> {
    // 模拟 ColorOS 首页分组类型字段
    public final int a;
    // 模拟 ColorOS 首页分组承载的数据字段
    public final T b;

    // 创建包含分组类型和数据的基础首页夹具
    public oe2(int type /* 首页分组类型 */, T data /* 分组承载的数据 */) {
        this.a = type;
        this.b = data;
    }
}
