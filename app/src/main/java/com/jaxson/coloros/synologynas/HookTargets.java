package com.jaxson.coloros.synologynas;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 固化 ColorOS 相册 16.50.8 的完整 Hook 与辅助反射成员集合
 *
 * @param readBoolean 按字符串读取能力开关的方法
 * @param readBooleanDefault 按资源标识读取能力开关的方法
 * @param openNasDeviceSpace 打开 NAS 页面或设备管理入口的方法
 * @param resolveFolderNote 生成 NAS 文件夹文案的方法
 * @param resolveNasStateMessage 生成 NAS 状态说明的方法
 * @param cloudSyncProxyConstructor CloudSyncProxyDM 的无参构造器
 * @param listNasDevices 读取相册 NAS 设备列表的方法
 * @param readGalleryStats 读取指定 NAS 设备本地统计的方法
 * @param preloadNasMetadata 启动阶段预加载 NAS 元数据的方法
 * @param observeNasStatus 返回 NAS 连接状态 Flow 的方法
 * @param cancelNasDownload 取消 NAS 原图下载句柄的方法
 * @param populateMainTabAlbumGroups 填充相册首页分组的方法
 * @param bindNasAlbumsCard 绑定私有云图集卡片内容的方法
 * @param applyNasAvailability 刷新私有云图集连接状态的方法
 * @param nasBindingDeviceId 卡片绑定实例保存当前设备标识的字段
 * @param evaluateNasExitForRemovedDevice 判断设备移除后是否退出页面的方法
 * @param functionOneInvoke 执行 Kotlin Function1 回调的辅助方法
 * @param showMultiDeleteDialog 显示多选删除确认框的协程方法
 * @param multiDeleteParams 多选删除协程保存请求参数的字段
 * @param deleteParamsDeviceId 多选删除参数保存设备标识的字段
 * @param showSingleDeleteDialog 显示单图删除确认框的协程方法
 * @param singleDeleteItemPath 单图删除协程保存媒体路径的字段
 * @param parseMediaPath 将字符串解析为媒体路径对象的方法
 * @param resolveMediaObject 根据媒体路径查找媒体对象的方法
 * @param nasMediaDeviceId NAS 媒体对象保存设备标识的字段
 * @param loadNasMediaMetadata 延迟装载 NAS 媒体元数据的方法
 * @param setDialogMessage 设置 ColorOS 对话框正文的方法
 */
record HookTargets(
        /* 按字符串读取能力开关的目标 */ Method readBoolean,
        /* 按资源标识读取能力开关的目标 */ Method readBooleanDefault,
        /* 打开 NAS 页面或设备管理入口的目标 */ Method openNasDeviceSpace,
        /* 生成 NAS 文件夹文案的目标 */ Method resolveFolderNote,
        /* 生成 NAS 状态说明的目标 */ Method resolveNasStateMessage,
        /* CloudSyncProxyDM 的无参构造目标 */ Constructor<?> cloudSyncProxyConstructor,
        /* 读取相册 NAS 设备列表的目标 */ Method listNasDevices,
        /* 读取指定 NAS 设备本地统计的目标 */ Method readGalleryStats,
        /* 启动阶段预加载 NAS 元数据的目标 */ Method preloadNasMetadata,
        /* 返回 NAS 连接状态 Flow 的目标 */ Method observeNasStatus,
        /* 取消 NAS 原图下载句柄的目标 */ Method cancelNasDownload,
        /* 填充相册首页分组的目标 */ Method populateMainTabAlbumGroups,
        /* 绑定私有云图集卡片内容的目标 */ Method bindNasAlbumsCard,
        /* 刷新私有云图集连接状态的目标 */ Method applyNasAvailability,
        /* 卡片绑定实例保存当前设备标识的字段 */ Field nasBindingDeviceId,
        /* 判断设备移除后是否退出页面的目标 */ Method evaluateNasExitForRemovedDevice,
        /* 执行 Kotlin Function1 回调的辅助成员 */ Method functionOneInvoke,
        /* 显示多选删除确认框的目标 */ Method showMultiDeleteDialog,
        /* 多选删除协程保存请求参数的字段 */ Field multiDeleteParams,
        /* 多选删除参数保存设备标识的字段 */ Field deleteParamsDeviceId,
        /* 显示单图删除确认框的目标 */ Method showSingleDeleteDialog,
        /* 单图删除协程保存媒体路径的字段 */ Field singleDeleteItemPath,
        /* 将字符串解析为媒体路径对象的辅助成员 */ Method parseMediaPath,
        /* 根据媒体路径查找媒体对象的辅助成员 */ Method resolveMediaObject,
        /* NAS 媒体对象保存设备标识的字段 */ Field nasMediaDeviceId,
        /* 延迟装载 NAS 媒体元数据的辅助成员 */ Method loadNasMediaMetadata,
        /* 设置 ColorOS 对话框正文的目标 */ Method setDialogMessage
) {
}
