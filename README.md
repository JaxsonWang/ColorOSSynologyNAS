# ColorOSSynologyNAS

面向 ColorOS 16 自带相册 `16.50.8 (16050008)` 的群晖 DSM 7 远程相册模块。

相册 Hook、私有反射合约、DSM 数据链路和新版迁移验收流程见
[`COLOROS_GALLERY_HOOK_MAINTENANCE.md`](COLOROS_GALLERY_HOOK_MAINTENANCE.md)。

## 已实现链路

```text
ColorOS 自带相册 → 图集 → 私有云图集
        │ LSPosed 精确 Hook
        ▼
群晖 NAS 设备与远程相册列表
        │ libxposed RemotePreferences 配置通道
        │ DSM 7 File Station API
        │ SYNO.API.Info / Auth / List / Thumb / Download / Delete / Upload / MD5
        ▼
远程缩略图与原图按需流式读取
        ▼
ColorOS 自带相册内直接浏览和预览
```

手机端入口：`ColorOS 相册 → 图集 → 私有云图集`。入口直接复用 ColorOS 相册
原生飞牛 NAS 首页卡片、NAS 相册列表、照片网格和单图预览页面；模块 App 只负责
DSM 配置，不提供相册入口，也不在模块内另建图库。

首页私有云图集卡片使用 Synology Logo，展示 DSM 当前返回的 NAS 具体型号以及
“已连接/未连接”状态。进入完整私有云图集后，右上角“设备管理”直接打开模块的
DSM 配置页；相册浏览入口仍只存在于 ColorOS 自带相册。

模块仅作用于 `com.coloros.gallery3d`，不会 Hook `system_server`、系统界面、
设备快连或设备空间进程，也不会修改、重签 ColorOS 相册 APK。

图片不写入 MediaStore，不复制到 `/DCIM`，也不在设备上维护远端图片镜像。

## 精确 Hook

- `com.oplus.aiunit.vision.hda.c(String, boolean, boolean)`
- `com.oplus.aiunit.vision.hda.d(int, String, boolean)`
- `com.oplus.aiunit.vision.goq.a(Context, String, DiagnosePage, int)`
- `com.oplus.aiunit.vision.enn.i()`
- `com.oplus.aiunit.vision.h9q.e(BaseViewHolder, int, wfb0)`
- `com.oplus.aiunit.vision.h9q.l(Integer, NasDeviceAvailability)`
- ColorOS 相册 NAS Provider 构造、设备列表、连接状态和媒体读取接口

Hook 仅在相册版本号精确等于 `16050008` 时安装。模块复用相册已有的
`feature_is_support_feiniu_nas` 和远程 NAS 展示流程，在首页模型输出层注入群晖
私有云图集卡片，并将原 Provider 替换为 DSM 7 远程实现，不修改相册私有数据库
格式。DSM 客户端直接运行在相册 Hook 进程中，不依赖会被 ColorOS AppsFilter
阻断的跨包 ContentProvider。

## DSM 与凭据

- 仅接受 HTTPS DSM 地址，使用 Android 默认 TLS 严格证书校验；
- 先调用 `SYNO.API.Info`，再使用 DSM 返回的 API 路径和最大版本；
- 通过 `SYNO.Core.System` 读取 NAS 具体型号并写入私有云图集卡片；
- 登录 session 固定命名为 `ColorOSSynologyNAS`，SID 只保留在进程内存中；
- Synology Photos 个人空间默认目录为 `/home/Photos`，共享空间使用 `/photo`；
- “保存并连接”验证登录、File Station API 和已配置的远端目录，启用备份时同时要求
  `SYNO.FileStation.Upload` 与 `SYNO.FileStation.MD5` 支持 v2；
- 模块 App 内的密码和 OTP 通过 Android Keystore AES-256-GCM 加密保存；
- Hook 运行配置通过 libxposed 的模块专属 RemotePreferences 发布给相册进程；
- 相册、图片元数据和缩略图从 DSM 按需读取，原图直接流入 ColorOS 相册回调；
- 模块配置页可独立开启/关闭照片备份，并自定义图片目录下的固定备份文件夹；
- 所有 ColorOS 备份请求复用同一个配置目录，不再按手机型号或本地相册名重复创建子目录；
- ColorOS 原生删除操作映射到 `SYNO.FileStation.Delete v2`，成功后立即失效远端
  清单缓存，下一次加载直接读取 DSM 当前结果；
- 不申请存储、通知或前台同步权限，不执行本地同步。

## 构建

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export ANDROID_HOME=/Volumes/MacHD/Library/Android/sdk
./gradlew --no-daemon clean testDebugUnitTest lintDebug assembleDebug --warning-mode all
```

输出：`app/build/outputs/apk/debug/app-debug.apk`。

## 当前边界

- DSM 侧基于 DSM 7 File Station API，当前只向 ColorOS 相册提供图片；
- ColorOS 私有接口按 `16.50.8 (16050008)` 精确适配，相册版本变化后需要重新核对 Hook；
- LSPosed 作用域固定为 `com.coloros.gallery3d`。
