# ColorOS 相册 Hook 维护与升级适配说明

> 本文记录 `ColorOSSynologyNAS` 当前对 ColorOS 自带相册的完整 Hook 合约、DSM 7
> 数据链路、线程约束以及相册升级后的重新适配流程。本文面向后续维护者，重点是让新版本
> 适配可以从已验证的语义锚点和运行不变量出发，而不是重新猜测整个飞牛 NAS 链路。

## 1. 文档目的

本模块通过 LSPosed/libxposed 接入 ColorOS 相册已有的飞牛 NAS 页面体系，用群晖 DSM 7
实现替换飞牛 Provider，从而在自带相册内完成：

- `图集 → 私有云图集`入口展示；
- 群晖 NAS 设备卡片、型号和连接状态展示；
- 远端相册、照片、缩略图和原图的直接浏览；
- 远端照片删除；
- ColorOS 原生 NAS 备份请求上传到 DSM；
- 从相册右上角“设备管理”进入模块配置页。

本文同时明确模块**没有**实现的行为：

- 不把远端照片同步或下载到 MediaStore；
- 不复制照片到 `/DCIM`；
- 不在手机端维护 DSM 照片镜像；
- 不 Hook `system_server`、系统界面、设备空间或设备快连；
- 不修改或重签 ColorOS 相册 APK；
- 不把版本门放宽成“任意新版都尝试 Hook”。

这里的“备份”是 ColorOS 相册原生 NAS 备份上传链路；“浏览”是按需访问 DSM。两者都不等于
把 DSM 图库同步到手机本地相册。

---

## 2. 当前适配基线与硬边界

### 2.1 相册版本

| 项目 | 当前值 |
|---|---|
| Package | `com.coloros.gallery3d` |
| Process | `com.coloros.gallery3d` |
| `versionName` | `16.50.8` |
| `versionCode` | `16050008` |
| DSM 目标 | DSM 7 File Station API |

版本门位于：

```text
app/src/main/java/com/jaxson/coloros/synologynas/HookPolicy.java
```

```java
public static final String TARGET_PACKAGE = "com.coloros.gallery3d";
public static final long TARGET_VERSION_CODE = 16050008L;
```

只有 package、process 和 versionCode **全部精确匹配**时才安装目标 Hook。相册私有类、混淆名、
DTO 构造器和字段布局随版本变化的风险很高，因此新版适配完成前不得仅修改版本号试运行。

### 2.2 libxposed 元数据

```text
app/src/main/resources/META-INF/xposed/java_init.list
  com.jaxson.coloros.synologynas.ColorOsSynologyNasModule

app/src/main/resources/META-INF/xposed/scope.list
  com.coloros.gallery3d

app/src/main/resources/META-INF/xposed/module.prop
  minApiVersion=101
  targetApiVersion=102
  staticScope=true
```

作用域固定为相册主包；当前实现不处理其他相册进程。

### 2.3 群晖合成设备常量

位于：

```text
app/src/main/java/com/jaxson/coloros/synologynas/gallery/GalleryContract.java
```

| 常量 | 当前值 | 用途 |
|---|---|---|
| `GALLERY_PACKAGE` | `com.coloros.gallery3d` | ColorOS 资源查找 |
| `DEVICE_ID` | `synology-dsm7` | 所有 Hook 和 Provider 分流的唯一设备标识 |
| `DEVICE_NAME` | `群晖 NAS` | 首页和设备卡片标题 |
| `DEFAULT_DEVICE_MODEL` | `DSM 7` | 尚无真实型号缓存时的占位型号 |

`DEVICE_ID` 是跨首页模型、设备列表、Provider、状态 Flow、备份请求和设备管理入口的核心合约。
不要在某个 Hook 中单独复制字符串，也不要为同一设备引入第二个 ID。

---

## 3. 总体架构与运行链路

### 3.1 相册浏览链路

```text
ColorOS native NAS UI
  └─ com.oplus.aiunit.vision.dpk dynamic proxy
       └─ ColorOsNasProviderProxy
            └─ GalleryRemoteClient
                 └─ RemoteGalleryRepository
                      └─ DsmClient
                           └─ DSM 7 File Station API
```

ColorOS 相册继续持有 `NasProvider.FEINIU` 这个枚举位置，但该位置对应的 Provider 实例会被
`ColorOsNasProviderProxy` 替换。代理只在请求目标为 `synology-dsm7` 时走 DSM；其他设备和
不属于群晖的 Provider 调用继续转发原飞牛 Provider。

这样可以复用 ColorOS 已有的：

- 私有云图集首页分组；
- NAS 设备页；
- NAS 相册列表和照片网格；
- 单图预览和原图下载交互；
- 删除操作入口；
- NAS 备份请求和结果 DTO。

### 3.2 配置发布链路

```text
MainActivity
  └─ DsmClient.testConnection()
       ├─ 验证 DSM 登录与 File Station API
       └─ 获取真实 NAS 型号
            └─ CredentialStore 保存凭据、备份开关和固定备份文件夹
                 └─ SynologyApplication.publishConfig()
                      └─ libxposed RemotePreferences
                           └─ 相册进程 RemoteConfigStore
```

真实 NAS 型号在用户点击“保存并连接”时获取并缓存，而不是在相册首页或卡片绑定时同步访问
DSM。这一约束直接关系到相册首次进入和“图集”页签响应速度。

旧配置缺少备份字段时按以下默认值迁移：

```text
backupEnabled = true
backupFolder = "ColorOS Backup"
```

### 3.3 备份上传链路

```text
ColorOS NasBackupUploadRequest (seq)
  └─ dpk proxy: i/s/k/r
       └─ GalleryBackupClient
            └─ SynologyBackupRepository
                 └─ DsmBackupClient
```

已实现：

- 通过模块配置页开启/关闭备份；
- 将所有照片写入 `remoteRoot/backupFolder` 这个固定目录；
- 不再根据每次请求的手机型号或本地相册名创建子目录；
- 查询本地已上传 hash 索引；
- 通过 DSM 远端 MD5 判断内容是否已存在；
- 同内容不重复上传；
- 同名但不同内容时使用稳定 hash 后缀；
- 只有上传成功后才写入本地 hash 索引；
- 将结果映射为 ColorOS 的 `yjq`、`teq` 和 `ycq`；
- 错误返回失败结果，不伪造成功。

备份关闭时，`dpk.d(...)` 和 `dpk.m(...)` 返回 `0`，ColorOS 不启动群晖备份任务；相册浏览、
预览和下载仍保持可用。备份文件夹参与本地 hash 索引作用域，用户切换目标文件夹后不会错误
复用旧目录的“已备份”记录。

正常 hash 命中可观察到：

```text
DSM backup hash query completed: requested=1, existing=1
```

---

## 4. Hook 安装生命周期

入口类：

```text
com.jaxson.coloros.synologynas.ColorOsSynologyNasModule
```

安装流程：

```text
onModuleLoaded()
  └─ 记录当前 processName

onPackageReady()
  ├─ 只接受 first package
  ├─ package/process 必须为 com.coloros.gallery3d
  └─ Hook Application.attach(Context)

Application.attach() interceptor
  ├─ 从真实 Context 取得相册 ClassLoader
  ├─ 再次校验 package/process/versionCode
  └─ installTargetHooks(...)
       ├─ 初始化 RemoteConfigStore / 浏览 / 状态 / 备份客户端
       ├─ initializeNasState()
       ├─ resolveHookTargets()
       ├─ installFeatureHooks()
       ├─ installDeleteDialogHooks()
       ├─ installProviderHooks()
       ├─ installNasPreloadHook()
       ├─ installGalleryHomeHook()
       ├─ installEntryHook()
       ├─ installSyntheticDevicePresenceHook()
       ├─ installLabelHook()
       └─ installGalleryCardHooks()
```

`resolveHookTargets()` 会先解析一整批类、方法和构造器。任何一个目标解析失败都会让本批目标
Hook 安装失败并记录 `hook target resolution failed`。因此相册升级时必须完整核对全部反射
合约，不能看到第一个崩溃点后只修一个方法名。

当前共涉及：

- 1 个启动 Hook：`Application.attach(Context)`；
- 18 个相册私有成员 Hook，其中包括 1 个构造器；
- 4 个只用于读取单图设备身份或调用回调、并未被 Hook 的反射方法。

`HookTargets` 字段与实际反射目标的完整对应关系：

| `HookTargets` 字段 | 当前反射目标 |
|---|---|
| `readBoolean` | `hda.c(String, boolean, boolean)` |
| `readBooleanDefault` | `hda.d(int, String, boolean)` |
| `openNasDeviceSpace` | `goq.a(Context, String, DiagnosePage, int)` |
| `resolveFolderNote` | `bug.w(long)` |
| `resolveNasStateMessage` | `zcq.m(Context)` |
| `cloudSyncProxyConstructor` | `CloudSyncProxyDM.<init>()` |
| `listNasDevices` | `ahq.b()` |
| `readGalleryStats` | `mgq.f(String)` |
| `preloadNasMetadata` | `ynq.a(int, ynq, ArrayList, CountDownLatch)` |
| `observeNasStatus` | `alq.h(NasProvider, String)` |
| `cancelNasDownload` | `z8g.cancel()` |
| `populateMainTabAlbumGroups` | `enn.i()` |
| `bindNasAlbumsCard` | `h9q.e(BaseViewHolder, int, wfb0)` |
| `applyNasAvailability` | `h9q.l(Integer, NasDeviceAvailability)` |
| `evaluateNasExitForRemovedDevice` | `ehq.b(CoroutineScope, boolean, String, Function1)` |
| `showMultiDeleteDialog` | `hfq$b.invokeSuspend(Object)` |
| `showSingleDeleteDialog` | `ijw$b.invokeSuspend(Object)` |
| `setDialogMessage` | `COUIAlertDialogBuilder.setMessage(CharSequence): COUIAlertDialogBuilder` |

以下成员只用于读取删除请求身份或执行 callback，不安装 Hook：

| `HookTargets` 字段 | 当前反射目标 |
|---|---|
| `functionOneInvoke` | `Function1.invoke(Object)` |
| `parseMediaPath` | `dst.b(String)` |
| `resolveMediaObject` | `q7b.f(dst)` |
| `loadNasMediaMetadata` | `jlq.i0()` |

`multiDeleteParams`、`deleteParamsDeviceId`、`singleDeleteItemPath` 和
`nasMediaDeviceId` 分别对应 `$params`、`gfq$a.a`、`$itemPath` 和 `jlq.F0` 字段。JADX 会把
`gfq$a.a` 显示成 Java 别名 `f9751a`，但 Android 反射必须使用 DEX 原始字段名 `a`。

---

## 5. 精确 Hook 清单与行为

### 5.1 能力开关：启用飞牛 NAS 页面体系

Hook：

```text
com.oplus.aiunit.vision.hda.c(String, boolean, boolean)
com.oplus.aiunit.vision.hda.d(int, String, boolean)
```

仅当配置 ID 为：

```text
feature_is_support_feiniu_nas
```

时返回 `true`；其他配置项必须 `chain.proceed()`，不能把相册所有布尔配置强制开启。

### 5.2 首页“私有云图集”入口注入

Hook：

```text
com.oplus.aiunit.vision.enn.i()
```

原方法完成后调用：

```text
ColorOsGalleryBridge.ensureSynologyHomeEntry(...)
```

向 `enn.e` 对应的 Lazy `ArrayList` 注入：

```text
e5q (NAS home group)
  └─ ogq (NAS device info)
```

当前首页 DTO 构造合约：

```text
e5q(type=5, count=1, deviceInfo=ogq)

ogq(
  id=0,
  deviceStatus=connected ? 1 : 0,
  bindStatus=1,
  albumCount=1,
  lastUpdateTime=0L,
  deviceUserId="synology-dsm7",
  deviceName="群晖 NAS",
  userName/currentModel=<cached DSM model>
)
```

插入位置在首页分组 `a == 4` 的“更多图集”项之前。若已有群晖项，则原地更新型号和状态而不
重复添加；其他 NAS 项必须保留。

只有 DSM 已配置时才注入首页入口。

### 5.3 点击群晖入口与“设备管理”

Hook：

```text
com.oplus.aiunit.vision.goq.a(
    Context,
    String deviceUserId,
    NasConnectionDiagnoseTrackHelper.DiagnosePage,
    int actionFlags
)
```

只拦截：

```text
deviceUserId == "synology-dsm7"
```

分支规则：

| 条件 | 行为 |
|---|---|
| 已配置且 `actionFlags != 20` | 返回 `null`，让相册后续原生 NAS 页面流程继续工作 |
| `actionFlags == 20` | 打开模块 `MainActivity`，对应右上角“设备管理” |
| 群晖设备未配置 | 打开模块配置页 |
| 非群晖设备 | `chain.proceed()` |

模块配置页通过以下 extra 确认入口设备：

```java
MainActivity.EXTRA_DEVICE_USER_ID = "device_user_id";
```

`actionFlags == 20` 是当前版本的反编译结论，新版必须重新确认。

### 5.4 替换飞牛 Provider

Hook：

```text
com.oplus.gallery.framework.abilities.cloudsync.CloudSyncProxyDM.<init>()
```

原构造器完成后执行：

```text
CloudSyncProxyDM
  └─ 按字段类型找到 alq
       └─ 按字段类型找到 xhb
            └─ 找到 Map<NasProvider, dpk>
                 └─ 取 NasProvider.FEINIU
                      └─ 替换为 ColorOsNasProviderProxy
```

原飞牛 Provider 被保存在动态代理中。只有目标设备为 `synology-dsm7` 的调用才走 DSM；其余
设备、Java Object 方法以及非群晖路径继续转发原 Provider。

### 5.5 设备列表注入

Hook：

```text
com.oplus.aiunit.vision.ahq.b()
```

原方法返回设备列表后：

1. 确认 `NasProvider.FEINIU` 已替换为已配置的群晖代理；
2. 从返回列表中移除旧的 `synology-dsm7` 合成项；
3. 保留所有其他 NAS 设备；
4. 添加新的 `ngq` 群晖设备 DTO。

当前 `ngq` 首个构造器参数合约：

```text
provider      = NasProvider.FEINIU
deviceUserId  = synology-dsm7
deviceName    = 群晖 NAS
userName      = 当前 NAS 型号
url           = synology://dsm7
status        = connected ? 1 : 0
expiresAt     = Long.MAX_VALUE
isAdmin       = true
albumCount    = 0
```

其余当前空字符串字段仍需在新版构造器核对时逐项映射，不能只依赖“第一个构造器”。

### 5.6 读取本地相册统计

Hook：

```text
com.oplus.aiunit.vision.mgq.f(String deviceUserId)
```

群晖设备仍先执行相册原方法，再从返回的 `jjq` 读取已保存照片数量：

- 更新内存中的 `currentPhotoCount`；
- 标记 `hasStoredSynologyMetadata = true`；
- 为启动元数据预加载跳过逻辑提供依据。

当前照片数量字段依次尝试：

```text
photoCount
a
f13529a
首个非 static int 字段
```

最后一项只是当前兼容手段，不是稳定合约；新版必须通过构造器、字段使用点和数据库映射重新
确认真实字段。

### 5.7 连接状态 StateFlow

Hook：

```text
com.oplus.aiunit.vision.alq.h(NasProvider, String deviceUserId)
```

群晖设备不使用原飞牛连接状态，而是立即返回模块创建的：

```text
MutableStateFlow<com.oplus.aiunit.vision.srb>
```

状态 DTO：

```text
srb(
  deviceId="synology-dsm7",
  availability=NasDeviceAvailability.CONNECTED/OFFLINE
)
```

连接探测在单线程后台执行器运行：

```text
ColorOSSynologyNAS-status
```

`AtomicBoolean nasStatusRefreshInFlight` 防止重复探测；Hook 本身先返回现有 Flow，后台结果再
更新 Flow、型号、状态和统计数据。

**升级验收不变量：进入相册后必须能够立即点击“图集”，相册主线程不能等待 DSM 网络请求。**

### 5.8 启动元数据预加载跳过

Hook：

```text
com.oplus.aiunit.vision.ynq.a(
    int currentIndex,
    ynq helper,
    ArrayList devices,
    CountDownLatch latch
)
```

目的：避免相册启动时对合成群晖设备执行原飞牛远程预加载，从而阻塞相册首次进入或“图集”
页签点击。

行为：

- 先判断当前位置是否为群晖合成设备；
- 只有本地已有群晖统计元数据时才跳过连续群晖项；
- 若跳过后到达列表末尾，主动执行 `CountDownLatch.countDown()`；
- 本地尚无群晖统计时保留第一次正常执行，避免永远没有初始元数据；
- 其他 NAS 设备继续执行原逻辑。

本地元数据来源：

```text
com.oplus.aiunit.vision.mgq.f("synology-dsm7")
```

### 5.9 页面恢复时保持合成设备存在

Hook：

```text
com.oplus.aiunit.vision.ehq.b(
    CoroutineScope,
    boolean,
    String deviceUserId,
    Function1<Boolean, Unit> onResult
)
```

群晖设备直接调用：

```text
onResult.invoke(false)
```

避免相册通过原飞牛设备存在性检查把合成群晖设备判定为已移除，然后退出 NAS 页面。其他设备
继续执行原方法。

这里的 `Function1.invoke(Object)` 由 `resolveHookTargets()` 反射解析并调用，但它本身不是
一个 Hook 点。

### 5.10 NAS 卡片绑定和群晖品牌样式

Hook：

```text
com.oplus.aiunit.vision.h9q.e(
    BaseViewHolder,
    int,
    com.oplus.aiunit.vision.wfb0
)
```

原绑定完成后，仅当：

```text
mjq.b.b == "synology-dsm7"
```

时应用群晖样式。

反射和资源依赖：

```text
h9q.j                      → 标题 TextView，并用于取得标题行 ViewGroup
ColorOS id/iv_nas_title_icon → 相册原 NAS Logo ImageView
h9q.k                      → 机型 TextView
h9q.l                      → 状态 TextView
```

设置：

```text
Image               = R.drawable.synology_logo
Background          = R.drawable.synology_logo_background
horizontal padding  = 3dp
vertical padding    = 1dp
model start margin  = 3dp
model               = DS220+ 等已缓存真实型号
status              = 已连接 / 未连接
```

Logo 背景 drawable 的圆角为 `3dp`。主题色：

| 主题 | Background | Foreground |
|---|---|---|
| 日间 | `#FFE2E3E5` | `#8A000000` |
| 夜间 | `#FF4A4A4C` | `#FFFFFFFF` |

日间底色用于贴近 ColorOS 原飞牛 Logo；夜间保持当前已验收样式。

### 5.11 卡片连接状态刷新

Hook：

```text
com.oplus.aiunit.vision.h9q.l(
    Integer state,
    NasDeviceAvailability availability
)
```

原方法完成后，若 `h9q.T == "synology-dsm7"`，重新写入群晖型号和状态：

```text
state == 1 → 已连接
其他/null → 未连接
```

这一步防止相册原飞牛状态绑定覆盖群晖机型和连接文案。

### 5.12 合成原图下载句柄的 cancel

Hook：

```text
com.oplus.aiunit.vision.z8g.cancel()
```

群晖原图通过相册 callback 同步流完后，Provider 会构造一个完成状态的合成 `z8g` 句柄。
这些句柄保存在弱引用集合中；相册之后对合成句柄调用 `cancel()` 时直接返回，避免合成句柄内部
空 `CoroutineScope` 导致崩溃。

其他真实下载句柄必须继续 `chain.proceed()`。

### 5.13 飞牛文案替换

Hook：

```text
com.oplus.aiunit.vision.bug.w(long)
com.oplus.aiunit.vision.zcq.m(Context)
```

替换规则集中在 `HookPolicy`：

```text
飞牛 NAS / FeiNiu NAS → 群晖 NAS
```

包含飞牛文案的 NAS 状态说明替换为：

```text
群晖 NAS 图片已接入私有云图集，可直接浏览和下载
```

不包含飞牛字样的其他文案保持相册原值。

### 5.14 群晖删除确认框去除飞牛回收站说明

ColorOS 16.50.8 的两条删除确认框路径分别是：

```text
多选：hfq$b.invokeSuspend(Object)
  └─ $params.a                       // deviceId，JADX 别名 f9751a
  └─ R.string.nas_delete_items_dialog_content

单图：ijw$b.invokeSuspend(Object)
  └─ $itemPath → dst.b → q7b.f → jlq.F0
  └─ R.string.nas_delete_single_item_dialog_content
```

两个资源都包含“删除后将在‘飞牛相册’回收站保留 30 天”。DSM 删除实际走
`SYNO.FileStation.Delete v2`，模块没有实现这条固定 30 天回收站合同，因此群晖弹窗不能展示
该说明。

`installDeleteDialogHooks()` 先从当前删除请求恢复 `deviceId`。仅当它等于
`synology-dsm7` 时，在该弹窗协程的同步调用范围内设置嵌套安全的 `ThreadLocal` 标志；
随后 `COUIAlertDialogBuilder.setMessage(CharSequence)` Hook 直接返回原 Builder，不调用原
`setMessage`。这样不会生成空正文 View，同时保留：

- 多选标题“N 个项目将从私有云删除”；
- 单图删除标题；
- “取消”和“删除”按钮；
- 原删除 callback 和 DSM 删除逻辑。

其他设备 ID、真实飞牛和相册其他弹窗始终 `chain.proceed()`。禁止全局改写上述字符串资源，
也不要用空字符串代替正文，否则会误伤其他 NAS 或留下空白布局。

---

## 6. ColorOS NAS Provider 合约

Provider 接口：

```text
com.oplus.aiunit.vision.dpk
```

当前完整签名：

```java
b3q a(String, String) throws IOException;
NasProvider b();
boolean c();
Object d(String, ContinuationImpl);
NasPhotosAvailabilityStatus e(String);
String f();
Object g(String, String, ContinuationImpl);
String h(String);
yjq i(String, ArrayList);
void j(String);
Object k(seq, ContinuationImpl);
List l(int, int, String) throws IOException;
int m(String);
NasPhotosAppStatus n(String);
jjq o(String) throws IOException;
boolean p(String, List<String>);
void q(String);
teq r(seq);
Object s(String, List, ContinuationImpl);
List t(int, int, String, String) throws IOException;
void u(String);
Object v(String, String, long, long, ContinuationImpl);
z8g w(String, String, uhq);
byte[] x(String, String, ThumbnailSize);
```

### 6.1 当前方法映射

| 方法 | 群晖路径行为 | 当前返回/实现 |
|---|---|---|
| `a` | 获取单个相册 | `GalleryRemoteClient.getAlbum()` |
| `b` | Provider 类型 | 返回 `NasProvider.FEINIU`，继续复用原飞牛页面 |
| `c` | Provider 能力 | 固定返回 `true`；是否已配置由其他状态接口表达 |
| `d` | 协程查询备份可用性 | 备份开启返回 `1`，关闭返回 `0` |
| `e` | Photos availability | 已配置为 `AVAILABLE`，否则为 `UNKNOWN` |
| `f` | 无群晖特殊分支 | 转发原飞牛 Provider |
| `g` | 当前群晖分支 | 返回 `0L` |
| `h` | 当前未使用 | 返回 `null` |
| `i` | 同步查询备份 hash | `GalleryBackupClient.findExistingHashes()` |
| `j` | 当前未使用 | 返回 `null` |
| `k` | 协程上传备份 | `GalleryBackupClient.upload()` |
| `l` | 分页列出远端相册 | `GalleryRemoteClient.listAlbums()` |
| `m` | 阻塞查询备份可用性 | 备份开启返回 `1`，关闭返回 `0` |
| `n` | Photos App 状态 | 已配置为 `RUNNING`，否则为 `STOPPED` |
| `o` | 图库统计 | 返回内存缓存照片数，不触发 DSM 清单扫描 |
| `p` | 删除远端照片 | `GalleryRemoteClient.deletePhotos()` |
| `q` | 当前未使用 | 返回 `null` |
| `r` | 同步上传备份 | `GalleryBackupClient.upload()` |
| `s` | 协程查询备份 hash | `GalleryBackupClient.findExistingHashes()` |
| `t` | 分页列出相册照片 | `GalleryRemoteClient.listPhotos()` |
| `u` | 当前未使用 | 返回 `null` |
| `v` | 当前未实现的字节区间请求 | 返回空字节数组 |
| `w` | 原图流式读取 | `GalleryRemoteClient.downloadOriginal()` + callback |
| `x` | 缩略图读取 | `GalleryRemoteClient.downloadThumbnail()` |

表中的固定值或空返回只描述当前 `16.50.8` 调用合约。新版如果开始实际使用这些方法，必须先从
调用点恢复语义，再决定实现，不要沿用旧返回值掩盖新行为。

### 6.2 群晖目标设备参数位置

不是所有 `dpk` 方法都把 `deviceUserId` 放在同一个参数位置：

```text
l / t   → args[2]
k / r   → 从 seq.a 或 seq.f24401a 解析 targetDeviceUserId
其他方法 → 默认 args[0]
```

如果当前调用不是群晖目标，动态代理转发原飞牛 Provider。新版修改接口签名后，必须同时更新：

- `targetsSynology()` 的参数定位；
- 每个 `case` 的参数索引；
- 测试 fixture `app/src/test/java/com/oplus/aiunit/vision/dpk.java`；
- 代理单元测试。

---

## 7. 反射 DTO、字段和资源合约

这是相册新版本最容易失效的部分。混淆类名不是稳定 API；下表是当前版本快照，不代表新版可
直接复用。

### 7.1 关键类型

| 当前类 | 当前语义 |
|---|---|
| `dpk` | NAS Provider interface |
| `ngq` | `NasDeviceDto` |
| `jjq` | `NasGalleryStatDto` |
| `e5q` | 首页 NAS group |
| `ogq` | `NasDeviceInfo` |
| `mjq` | NAS albums view data |
| `srb` | `DeviceStatusInfo` |
| `b3q` | `NasAlbumDto` |
| `NasPhotoInfo` | 远端照片 DTO |
| `z8g` | 原图下载句柄 |
| `wac` | 下载进度 |
| `seq` | `NasBackupUploadRequest` |
| `yjq` | 备份 hash 查询结果 |
| `teq` | 备份上传结果 |
| `ycq` | 备份目标路径 |
| `alq` | NAS 实现/管理核心对象 |
| `xhb` | Provider 注册表 |

### 7.2 关键字段

| 字段 | 当前语义 |
|---|---|
| `enn.e` | `Lazy<ArrayList<首页分组>>` |
| `e5q.a` | 首页分组类型；`4` 当前为“更多图集” |
| `e5q.d` | `ogq` |
| `ogq.b` | `deviceUserId` |
| `ogq.c` | `deviceName` |
| `ogq.d` | 设备状态 |
| `ogq.f` | `userName`；当前用于显示 NAS 型号 |
| `mjq.b` | `ogq` |
| `h9q.T` | 当前绑定的 `deviceUserId` |
| `h9q.j` | 标题 `TextView` |
| `h9q.k` | 机型 `TextView` |
| `h9q.l` | 状态 `TextView` |
| `jjq.photoCount/a/f13529a` | 照片数量候选字段 |
| `seq.a/f24401a` | `targetDeviceUserId` 候选字段 |
| `seq.b/c/d/e/f/h/k` | 备份请求的路径、文件、hash、输入流等字段 |

### 7.3 关键构造器和方法形状

新版至少重新核对：

- `ngq` 构造器参数数量、顺序和类型；
- `jjq(int, int)`；
- `ogq(int, int, int, int, long, String, String, String)`；
- `e5q(int, int, ogq)`；
- `srb(String, NasDeviceAvailability)`；
- `b3q` 和 `NasPhotoInfo` 构造器；
- `z8g` 构造器、完成状态和 `cancel()` 依赖；
- 原图 callback 是否仍为 `invoke(byte[], boolean)`；
- 备份输入流 provider 是否仍为 `invoke(): InputStream`；
- `MutableStateFlow` 创建和 `setValue()` 调用方式。

### 7.4 关键相册资源

当前卡片品牌依赖：

```text
id/iv_nas_title_icon
NasAlbumsViewDataBinding 对应的私有云图集卡片布局
```

升级时要同时检查：

- ID 是否改名或被内联；
- Logo 是否仍和标题处于同一 `ViewGroup`；
- `h9q.j/k/l` 是否仍对应标题、机型和状态；
- layout 或 ViewBinding 是否重构；
- 日间/夜间资源是否仍按相册主题生效。

---

## 8. DSM 浏览、预览和删除链路

### 8.1 DSM API

当前客户端使用：

```text
SYNO.API.Info
SYNO.API.Auth
SYNO.Core.System
SYNO.FileStation.List
SYNO.FileStation.Thumb
SYNO.FileStation.Download
SYNO.FileStation.Delete v2
```

配置只接受 HTTPS，并使用 Android 默认 TLS 严格证书校验。SID 只保留在进程内存中；模块 App
内保存的密码和 OTP 由 Android Keystore AES-256-GCM 加密。

### 8.2 相册和照片模型

当前目录策略：

- `/home/Photos`：Synology Photos 个人空间默认目录；
- `/photo`：可配置的共享空间目录；
- `ALL_PROJECT`：聚合全部项目；
- 其他目录按相对于配置根目录的路径生成相册；
- 照片按修改时间倒序；
- DSM 路径经 SHA-256 生成稳定正数 ID；
- 远端清单 snapshot TTL 为 60 秒。

### 8.3 缩略图和原图

- 缩略图按需返回字节数组；
- JPG、PNG 等普通格式优先使用 `SYNO.FileStation.Thumb`；
- WebP、HEIC、HEIF、AVIF 下载原文件到临时文件，再本地解码成 JPEG 缩略图；
- 原图通过 callback 分块直接流入 ColorOS 相册；
- 不写入 MediaStore，不复制到 `/DCIM`，不维护远端图片本地镜像。

以下三个文件的缩略图语义已经过真机验收，新版相册 Hook 迁移时不要顺带修改：

```text
app/src/main/java/com/jaxson/coloros/synologynas/dsm/DsmClient.java
app/src/main/java/com/jaxson/coloros/synologynas/dsm/LocalThumbnailGenerator.java
app/src/test/java/com/jaxson/coloros/synologynas/dsm/DsmThumbnailPolicyTest.java
```

当前锁定 SHA-256：

```text
a38c00f40efd0c74f37a109ab90728a733f9b9f709c26fa139147631844661db  DsmClient.java
b27cec0264f6a7669e27f79cb0d366871fe84dcf8dd5974c19e29ed9cbc302cc  LocalThumbnailGenerator.java
d1b339238712af3bc5c207a29148ef9e819d28d04abb564cd94c3ca29e55729a  DsmThumbnailPolicyTest.java
```

若缩略图需求本身发生变化，应作为独立任务修改并重新真机验收，而不是在混淆名迁移中改动。

### 8.4 删除

ColorOS 原生删除操作映射为：

```text
dpk.p(...)
  → GalleryRemoteClient.deletePhotos(...)
  → RemoteGalleryRepository.deletePhotos(...)
  → SYNO.FileStation.Delete v2
```

删除成功后清空 snapshot；下一次列表读取访问 DSM 最新清单。验收不能只看接口返回成功，必须
刷新相册并确认远端图片实际消失。

删除弹窗的正文资源仍属于飞牛语义。群晖请求会按 5.14 的 `deviceId` 边界跳过正文设置；标题、
按钮及本节删除链路不变。DSM 共享文件夹即使单独启用了回收站，也不等于“飞牛相册回收站保留
30 天”，不能恢复这段固定说明。

---

## 9. 性能与线程不变量

相册首页、首次进入和切换“图集”的响应性曾受 NAS 信息获取路径影响。后续版本必须维持以下
不变量：

1. `Application.attach()` 和各 UI Hook 不同步访问 DSM；
2. 卡片型号只读取“保存并连接”阶段已经获取并发布的缓存；
3. `alq.h(...)` 立即返回 `MutableStateFlow`，网络探测放到
   `ColorOSSynologyNAS-status`；
4. 同一时刻最多一个状态探测，由 `nasStatusRefreshInFlight` 保证；
5. `dpk.o(...)` 只返回内存/相册本地统计，不为了计数扫描 DSM 清单；
6. 有本地统计时，`ynq.a(...)` 跳过合成群晖设备的原飞牛启动预加载；
7. 相册列表、缩略图、原图和删除可以在相册调用它们的工作线程执行，但不能被 UI Hook
   提前变成主线程网络调用；
8. 不为“看起来更快”增加伪造列表、静默失败、第二份远端镜像或假连接状态。

真机性能验收必须从**强制停止相册后的第一次打开**开始，不能只测第二次进入后的热缓存路径。

---

## 10. ColorOS 相册新版本适配流程

### 10.1 获取版本和 APK

先确认设备和相册版本：

```bash
adb devices -l

adb -s SERIAL shell dumpsys package com.coloros.gallery3d | \
  rg "versionName|versionCode"

adb -s SERIAL shell pm path com.coloros.gallery3d
```

`pm path` 可能返回 base APK 和多个 split APK。保存原始列表，然后分别拉取；不要默认只有一个
APK：

```bash
mkdir -p /tmp/coloros-gallery-new-apks

adb -s SERIAL shell pm path com.coloros.gallery3d | \
  sed 's/^package://' | \
  while IFS= read -r apk; do
    adb -s SERIAL pull "$apk" /tmp/coloros-gallery-new-apks/
  done
```

### 10.2 使用 Jadx 解包

当前 Jadx 工具路径：

```text
/Volumes/MacHD/Other/Jadx
```

示例：

```bash
rm -rf /tmp/coloros-gallery-new-src

/Volumes/MacHD/Other/Jadx/bin/jadx \
  --output-dir /tmp/coloros-gallery-new-src \
  /tmp/coloros-gallery-new-apks/*.apk
```

`/tmp/coloros-gallery-new-src` 是临时目录，不能作为项目长期文档或源码依赖。

### 10.3 先按稳定语义锚点搜索

不要从 `hda`、`dpk`、`h9q` 等旧混淆名开始机械替换。先搜索以下较稳定的字符串、枚举、
Kotlin metadata 和资源：

```text
feature_is_support_feiniu_nas
NasProvider.FEINIU
NasBackupUploadRequest
NasAlbumDto
NasPhotoInfo
DeviceStatusInfo
NasAlbumsViewDataBinding
album_group_nas_albums_item
iv_nas_title_icon
NasConnectionDiagnoseTrackHelper
NasDeviceAvailability
ThumbnailSize
FeiNiu / 飞牛 / NAS 页面资源名
```

建议命令：

```bash
rg -n --glob '*.java' \
  'feature_is_support_feiniu_nas|NasProvider|NasBackupUploadRequest|NasAlbumDto|NasPhotoInfo|DeviceStatusInfo|NasAlbumsViewDataBinding|NasConnectionDiagnoseTrackHelper|NasDeviceAvailability|ThumbnailSize' \
  /tmp/coloros-gallery-new-src/sources

rg -n \
  'album_group_nas_albums_item|iv_nas_title_icon|飞牛|FeiNiu' \
  /tmp/coloros-gallery-new-src/resources
```

然后沿真实调用链恢复“谁构造 DTO、谁读取字段、谁调用 Provider、谁消费返回值”，不要只根据
字段类型或一个反编译注释定结论。

### 10.4 建立旧版到新版映射表

在修改代码前先完成以下映射：

| 语义 | 旧版本 | 新版本 | 证据 |
|---|---|---|---|
| 配置开关读取 | `hda.c/d` |  | 字符串调用点 |
| 打开 NAS 入口 | `goq.a` |  | DiagnosePage + actionFlags 调用点 |
| Provider 接口 | `dpk` |  | `Map<NasProvider, ?>` 和实现类 |
| Provider 容器构造 | `CloudSyncProxyDM.<init>` |  | 注册表初始化 |
| 设备列表 | `ahq.b` |  | 返回 `List<NasDeviceDto>` |
| 本地图库统计 | `mgq.f` |  | device ID 查询 |
| 启动元数据预加载 | `ynq.a` |  | device index + latch |
| 状态 Flow | `alq.h` |  | `DeviceStatusInfo` Flow |
| 下载 cancel | `z8g.cancel` |  | 下载句柄调用点 |
| 首页分组填充 | `enn.i` |  | 首页 group list |
| 卡片绑定 | `h9q.e` |  | NAS ViewBinding |
| 卡片状态刷新 | `h9q.l` |  | availability 消费点 |
| 设备移除判定 | `ehq.b` |  | 回调 Boolean |
| 标题文案 | `bug.w` |  | NAS 标题字符串 |
| 状态文案 | `zcq.m` |  | NAS 状态说明 |

“证据”列应写调用点、构造器、字段写入/读取或资源引用，避免只记录猜测出的新混淆名。

### 10.5 完整核对反射和调用合约

必须逐项确认：

1. `Application.attach(Context)` 后相册真实 ClassLoader 的获取仍成立；
2. 上表 18 个相册 Hook 目标的类、方法名、修饰符、参数顺序和返回值；
3. `Function1.invoke(Object)`、`dst.b(String)`、`q7b.f(dst)` 和 `jlq.i0()` 的调用仍成立；
4. `dpk` 全部方法签名、同步/协程形态和参数顺序；
5. `l/t` 的 device ID 参数位置；
6. `seq` 中 target device、文件路径、hash 和输入流 provider 字段；
7. `ngq/e5q/ogq/jjq/srb/b3q/NasPhotoInfo/yjq/teq/ycq` 构造器；
8. `enn/e5q/ogq/mjq/h9q/seq/jjq` 关键字段；
9. `actionFlags == 20` 是否仍是“设备管理”；
10. `NasDeviceAvailability.CONNECTED/OFFLINE` 是否仍存在且语义不变；
11. `iv_nas_title_icon`、私有云图集 layout 和 ViewBinding 字段；
12. 原图 callback 是否仍为 `invoke(byte[], boolean)`；
13. 备份输入流 provider 是否仍为 `invoke(): InputStream`；
14. `z8g` 构造器、完成状态、`cancel()` 和内部 scope 行为；
15. Provider 注册表是否仍由 `NasProvider.FEINIU` 键映射到接口实例；
16. 首页插入位置的分组类型 `a == 4` 是否仍代表“更多图集”；
17. 多选删除仍由 `hfq$b` 持有 `$params.a`（JADX 别名 `f9751a`），单图删除仍可由 `$itemPath` 恢复
    `jlq.F0`；
18. 两条删除弹窗仍调用返回 `COUIAlertDialogBuilder` 的
    `setMessage(CharSequence)`，而不是 bridge 方法或新的自定义正文 API。

### 10.6 修改顺序

所有合约确认后再按以下顺序修改：

1. 更新 `HookPolicy.TARGET_VERSION_CODE`；
2. 更新 `ColorOsSynologyNasModule.resolveHookTargets()`；
3. 更新 `ColorOsGalleryBridge` 的 DTO、字段和资源映射；
4. 更新 `ColorOsNasProviderProxy` 的接口和参数分流；
5. 如备份 DTO 变化，更新 `GalleryBackupClient`；
6. 同步更新 `app/src/test/java/com/oplus/...` 下的相册 fixture；
7. 更新/增加回归测试；
8. 完成 clean 构建后再安装模块和重启相册验收。

不要在新接口语义尚未恢复时增加兼容分支、吞异常或转发到错误 Provider。版本门存在的目的就是
让未适配版本明确不安装 Hook，而不是让相册在未知私有 API 上崩溃。

---

## 11. 构建与静态验收

### 11.1 clean 回归

```bash
cd /Volumes/MacHD/Developer/AndroidStudioProjects/ColorOSSynologyNAS

export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export ANDROID_HOME=/Volumes/MacHD/Library/Android/sdk

./gradlew clean testDebugUnitTest lintDebug assembleDebug \
  --no-build-cache \
  --no-daemon \
  --warning-mode all
```

当前已知基线：

```text
Unit tests: 72
Failures: 0
Errors: 0
Lint errors: 0
Existing lint warnings: 4
```

不得把 `UP-TO-DATE` 当作新版本适配的 fresh artifact，因此迁移验收使用 `clean` 和
`--no-build-cache`。

APK：

```text
app/build/outputs/apk/debug/app-debug.apk
```

### 11.2 代码级覆盖检查

至少执行：

```bash
# Hook 安装和反射目标
rg -n \
  'resolveHookTargets|installFeatureHooks|installProviderHooks|installNasPreloadHook|installGalleryHomeHook|installEntryHook|installSyntheticDevicePresenceHook|installLabelHook|installGalleryCardHooks' \
  app/src/main/java/com/jaxson/coloros/synologynas/ColorOsSynologyNasModule.java

# Provider 特殊方法分支
rg -n 'case "[a-z]"' \
  app/src/main/java/com/jaxson/coloros/synologynas/gallery/ColorOsNasProviderProxy.java

# DTO/字段桥接
rg -n \
  'NAS_DEVICE|GALLERY_STATS_DTO|NAS_HOME_GROUP|NAS_DEVICE_INFO|DEVICE_STATUS|NAS_VIEW_DATA|readField' \
  app/src/main/java/com/jaxson/coloros/synologynas/gallery/ColorOsGalleryBridge.java
```

检查重点不是命令有输出，而是文档、fixture、实际接口和调用点四者一致。

---

## 12. 真机验收清单

先清理日志，并从相册冷启动开始：

```bash
adb -s SERIAL logcat -c
adb -s SERIAL shell am force-stop com.coloros.gallery3d
```

逐项验证：

- [ ] 相册首次打开后能立即点击“图集”，无几秒卡死或需要连续点击；
- [ ] “私有云图集”入口存在，且重复进入不会重复注入；
- [ ] 群晖 Logo 尺寸、3dp 内边距和 3dp 机型间距正确；
- [ ] 日间 Logo 使用浅灰底，夜间保持深色样式；
- [ ] 显示真实 NAS 型号；
- [ ] “已连接/未连接”状态正确，并能随状态刷新；
- [ ] 点击群晖入口正常进入相册原生 NAS 图集；
- [ ] 右上角“设备管理”打开模块 DSM 配置页；
- [ ] 远端相册列表正常，分页无重复和遗漏；
- [ ] 照片网格和缩略图正常，无黑色缩略图；
- [ ] 单图预览正常；
- [ ] 下载原图正常；
- [ ] 删除远端照片成功，刷新后照片消失；
- [ ] 多选和单图删除确认框均不显示飞牛回收站说明，点“取消”不会删除照片；
- [ ] 备份 hash 查询、上传和重复文件处理正常；
- [ ] 其他 NAS 和非群晖请求仍走原 Provider；
- [ ] 相册进程无 Hook 解析、反射、空 scope 或主线程网络异常。

日志过滤：

```bash
adb -s SERIAL logcat | rg \
  'ColorOSSynologyNAS|hooks installed|replaced FEINIU|injected Synology|DSM '
```

当前关键成功日志：

```text
hooks installed for gallery version 16050008
replaced FEINIU provider with Synology DSM 7 provider
injected Synology private cloud album into ColorOS gallery home
injected Synology DSM 7 device into ColorOS gallery
```

出现下列日志时优先按反射合约处理，不要用宽泛 try/catch 隐藏：

```text
Application.attach resolution failed
Application.attach hook failed
hook target resolution failed
hook installation failed
```

---

## 13. 常见故障定位

### 13.1 完全没有群晖入口

依次检查：

1. 相册 package/process/version 是否精确命中；
2. LSPosed 作用域是否启用 `com.coloros.gallery3d`；
3. 是否出现 `hooks installed`；
4. `feature_is_support_feiniu_nas` 是否仍是能力开关；
5. `enn.i()` 是否仍负责首页分组；
6. DSM 配置是否已经通过 RemotePreferences 发布；
7. `ensureSynologyHomeEntry()` 的 Lazy list 和 DTO 构造器是否仍匹配。

### 13.2 入口存在但点击后进不去

检查：

- `goq.a(...)` 是否仍是入口启动器；
- `deviceUserId` 是否仍在第二个参数；
- 普通入口的 `actionFlags` 是否被误判为设备管理；
- 已配置普通入口是否正确返回 `null` 给相册后续原生流程；
- `ehq.b(...)` 是否把合成设备误判为已移除。

### 13.3 首次打开相册或切换“图集”卡住

优先检查主线程：

- 卡片绑定是否直接调用 `DsmClient`；
- `initializeNasState()` 是否扩大成同步网络请求；
- `alq.h(...)` 是否仍立即返回 StateFlow；
- `ynq.a(...)` 是否恢复了原飞牛群晖合成设备预加载；
- `dpk.o(...)` 是否为了照片计数触发全量 DSM 扫描；
- 状态探测是否绕过 `nasStatusRefreshInFlight` 并发启动。

### 13.4 型号或状态被飞牛文案覆盖

检查 `h9q.e(...)` 和 `h9q.l(...)` 两个阶段是否都重新执行
`applySynologyConnectionLabel()`，以及 `h9q.T` 是否仍是当前 device ID 字段。

### 13.5 缩略图黑屏但下载正常

先区分缩略图和原图链路：

- `dpk.x(...)`：缩略图字节数组；
- `dpk.w(...)`：原图 callback 流式读取。

检查 DSM 返回格式、`ThumbnailSize` 映射和本地解码策略，不要把“原图下载成功”当成缩略图链路
也已正确。不要在相册 Hook 迁移时顺带更改已锁定的缩略图语义。

### 13.6 删除返回成功但照片仍在

检查：

- `dpk.p(...)` 传入的是 DSM 原始路径还是相册 ID；
- `SYNO.FileStation.Delete v2` 是否真正完成；
- 成功后 snapshot 是否清空；
- 刷新是否重新读取 DSM，而不是继续消费旧页面数据；
- DSM 端文件是否实际消失。

### 13.7 设备管理点击无效

重新跟踪菜单点击到 `goq.a(...)` 的调用，确认新版 action flag；不要默认 `20` 永远不变。

---

## 14. 关键源码索引

| 文件 | 职责 |
|---|---|
| `ColorOsSynologyNasModule.java` | libxposed 入口、版本门后的 Hook 安装、删除弹窗分流、状态异步刷新 |
| `HookPolicy.java` | 目标版本、能力开关、设备管理动作、删除弹窗和文案策略 |
| `gallery/GalleryContract.java` | 群晖合成设备公共常量 |
| `gallery/ColorOsGalleryBridge.java` | ColorOS 私有 DTO/字段/资源反射桥接 |
| `gallery/ColorOsNasProviderProxy.java` | `dpk` 动态代理、群晖分流、浏览/删除/备份映射 |
| `gallery/GalleryRemoteClient.java` | Provider 到远端仓库的同步边界和 DTO 输入 |
| `gallery/RemoteGalleryRepository.java` | DSM 清单快照、相册/照片转换、分页和删除 |
| `gallery/GalleryBackupClient.java` | ColorOS 备份 DTO 解析与结果映射 |
| `backup/SynologyBackupRepository.java` | hash 去重和备份上传事务规则 |
| `backup/BackupPathPolicy.java` | 固定备份目录和文件名冲突规则 |
| `backup/SharedPreferencesBackupHashStore.java` | 按 DSM、图片根目录和备份文件夹隔离 hash 索引 |
| `dsm/DsmClient.java` | DSM 浏览、缩略图、下载和删除 API |
| `dsm/LocalThumbnailGenerator.java` | 特殊图片格式本地缩略图生成 |
| `dsm/DsmBackupClient.java` | DSM 备份文件检查和上传 |
| `MainActivity.kt` | DSM 凭据、图片目录、备份开关、备份文件夹和连接验证 |
| `SynologyApplication.kt` | 配置发布到 RemotePreferences |
| `security/CredentialStore.java` | Android Keystore 加密凭据保存 |
| `RemoteConfigStore.java` | 相册进程读取模块发布配置 |

相对项目根目录的完整位置：

```text
app/src/main/java/com/jaxson/coloros/synologynas/
```

相册测试 fixture 和回归测试位于：

```text
app/src/test/java/com/oplus/
app/src/test/java/com/jaxson/coloros/synologynas/
```

---

## 15. 当前基线保护原则

新版适配提交前，最后确认：

- 新版所有反射目标和 DTO 都有调用点证据；
- 版本号只在完整适配后更新；
- 非群晖请求仍转发原 Provider；
- 没有在 UI Hook 中引入 DSM 同步访问；
- 没有把浏览改成下载到本机；
- 没有改动已锁定缩略图链路，除非任务明确要求；
- 没有用异常吞噬、假成功、空列表或第二份状态源掩盖真实问题；
- clean 测试、Lint、构建和完整真机清单均已通过。

只要这些边界和语义锚点保持清晰，即使下一版混淆类名全部变化，也可以沿现有飞牛 NAS 真实
调用链完成局部重新映射，而不需要重头设计群晖接入。
