package android.util;

// 提供本地 JVM Hook 测试所需的最小 Android 日志类型与方法夹具
public final class Log {
    // 禁止实例化只用于本地 JVM 单测的 Android 日志夹具
    private Log() {
    }

    // 模拟 Android 信息日志并返回固定写入结果
    public static int i(
            String tag, // 日志标签
            String message // 日志文本
    ) {
        return 0;
    }

    // 模拟 Android 错误日志并返回固定写入结果
    public static int e(
            String tag, // 日志标签
            String message, // 日志文本
            Throwable error // 日志携带的异常
    ) {
        return 0;
    }
}
