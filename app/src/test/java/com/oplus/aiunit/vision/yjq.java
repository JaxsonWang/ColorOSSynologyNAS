package com.oplus.aiunit.vision;

import java.util.Set;

// 模拟 ColorOS 已备份照片 hash 查询结果的封闭类型层级
public abstract class yjq {
    // 模拟 ColorOS 已备份照片 hash 查询失败结果的数据合同
    public static final class a extends yjq {
        // 模拟 yjq.a 的 hash 查询错误码字段
        public final int a;
        // 模拟 yjq.a 的 hash 查询失败消息字段
        public final String b;

        // 按 ColorOS 当前双参数合同创建 hash 查询失败夹具
        public a(int errorCode /* 查询错误码 */, String message /* 失败消息 */) {
            this.a = errorCode;
            this.b = message;
        }
    }

    // 模拟 ColorOS 已备份照片 hash 查询成功结果的数据合同
    public static final class b extends yjq {
        // 模拟 yjq.b 的已存在照片 hash 集合字段
        public final Set<String> a;

        // 按 ColorOS 当前单参数合同创建 hash 查询成功夹具
        public b(Set<String> existingHashes /* 已存在的照片内容 hash */) {
            this.a = existingHashes;
        }
    }
}
