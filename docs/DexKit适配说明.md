# DexKit 适配与扫描报告

## 使用及统计

仅作用于 `com.huawei.health` 首包主进程。attach 完成后读取一次配置快照，服务独立安装；后台线程校验 APK 内容身份、读取缓存或完整扫描，不在主线程做 Dex/磁盘工作。规则版本 6 包含 96 个布局开关，成功条件为该项必要定位和内容识别均可用，与开关状态无关。扫描晚于页面创建时，Activity 弱引用追踪器对已创建视图重应用规则。

设置首页显示已匹配 m/n、已检查 p/n、版本、更新时间及服务状态。尚未扫描、运行中、完成、失败、过期和状态待确认分开展示。两分钟前的运行中报告只作为待确认状态；它不能证明目标进程仍在运行。匹配统计不宣称 Hook 安装或设备回归成功。

预约通过 RemotePreferences 的 `scan.request` UUID 保存。只有同步提交成功才显示已安排，失败尝试恢复旧值并提示。下次目标主进程启动时消费请求，切换前后台不触发。扫描未完整完成不确认请求，下次启动重试；扫描期间不读取新的布局开关快照。

## 定位依据

唯一静态适配基准是仓库“调试”中的 17.0.7.320 APK（versionCode `1700007320`）。新版 JADX 产物位于忽略的 `build/host-17.0.7.320`；`调试/jadx-out` 仍属于 17.0.7.310，不能当作新版证据。17.0.7.310 与其他未知版本只使用通用扫描能力，不维护版本专用规则。查询使用唯一候选、完整参数和返回类型；继承入口按最近声明层级定位。扫描器与页面逻辑分离，回调不运行 DexKit。

| 功能组 | 主要依据 | 未通过时 |
| --- | --- | --- |
| 健康顶部 | HomeFragment 生命周期、health_tab_titlebar、CustomTitleBar 两个可见性入口 | 保留对应控件 |
| 顶部卡片 | HomeCardAdapter 构造/列表刷新、getCardName 字符串与签名，包括 FunctionMenuCardData、HealthQuickEntryCardData | 保留或移除对应整张卡片 |
| 编辑卡片 | 基准版本已核验的 l() 与 LinearLayout 字段 l，字段绑定 modify_cards_layout | 未知版本暂不启用 |
| 运动五子页 | SportEntranceFragment 生命周期、分类栏/搜索/菜单/Banner 资源、已核验子页和区块标题；子页入口不再作为屏蔽项 | 缺少身份的项目保留，标题后备仅用于 17.0.7.320 |
| 运动区块 | SportTabPageResTrigger 的继承入口、4040 常量证据与运行时范围检查、SectionBean 类型化访问器 | 无法确认的项目保留；仅依赖标题的项目限基准版本 |
| 运动快捷绑定 | setQuickEntryLayout 日志、x(ColumnLayoutAdapter$b,int)、SingleEntryContent 调用及 holder 的 cq 根容器字段 | 基准版本限定后备，缺失则不安装该入口 |
| 旧设备页 | DeviceFragment、CardDeviceFragment 生命周期与独立资源名 | 缺少资源的项目保留 |
| 新设备父页 | NewDeviceFragment 生命周期与设备/商城切换资源 | 隐藏冲突时保留设备入口，先切至仍可见子页 |
| Arkui 设备页 | ArkuiDeviceFragment、BaseViewDelegate.obtainView、实际 Dex 委托类名及区块资源 | 单个委托或资源缺失时只跳过对应项 |
| 商城页 | VMallFragment 生命周期与商城卡片资源 | 缺少定位时保留商城 |
| 我的列表 | initRecyclerList/initOverseaRecyclerList、Adapter 调用的行类型及标题访问器 | 缺少必要访问器或内容身份时保留 |
| 我的网格 | 四种 getCardName 业务字符串、返回类型与 Adapter 刷新入口 | 独立保留未命中模型 |
| 我的营销卡片 | `PersonalCenterRecyclerViewAdapter$b$4.d(Map)` 的 Map 签名和 `filterMarketingRules(Map)` 调用关系；调用方只请求 4168/9013 | 未知版本或回调缺失、歧义时保持两张卡片 |
| 底栏 | 清空入口的 removeAllViews 调用关系、添加/布局完整签名、当前标题资源身份 | 不根据未知版本的位置猜测 Tab |

资源名压缩时可读取当前 APK 的已知 R 类常量，并核验资源类型及所属包。历史数字 ID 和固定底栏索引仅在基准版本使用。显示文本后备不扩散到未知版本。静态查询命中不保证服务端动态内容均可识别。

## 17.0.7.320 的定位变化

- 直接读取新版 APK 的 DEX 表复核原始类、方法与字段名，不使用 JADX 展示的 `defpackage` 或桥接别名代替 Dex 描述符。编辑卡片仍使用 `l()V`，容器由旧字段 `m` 改为 `l:LinearLayout`。
- 快捷入口 holder 从 `$e` 改为 `$b`，根字段从 `cn` 改为 `cq:RelativeLayout`；该字段仍绑定 `item_quick_entry_root_layout`。绑定查询通过返回 `SingleEntryContent` 的调用排除共享日志的另一种网格。
- 我的营销 holder 从 `$c` 改为 `$b`，实际业务方法仍为 `$b$4.d(Map)V`，`onSuccess(Object)` 只作桥接。我的列表日志锚点定位到 `wsl.r()/n()`，行模型为 `wqy`，标题与类型访问器分别为 `e()/h()`；网格模型按四个 getCardName 字符串解析为 `wro/wri/wrb/wry`。
- 旧设备页刷新为 `DeviceFragment.a(List)` 与 `CardDeviceFragment.e(MarketingApi,Map)`。新设备页枚举 `DeviceDelegateEnum` 确认主卡/列表/提示/我的手表/表盘对应 `rxl/rya/ryi/ryg/ryr`，功能区对应 `rxz/ryq/rxg`，特色区对应 `ryb`。旧版 `rxz` 是特色区，新版是功能区，禁止合并两版映射。
- 两版 public.xml 的 55,217 个资源名称及 ID 一致；已有数字 ID 以新版资源表重新核对后保留，仅向精确匹配 17.0.7.320 的版本开放。服务名称规范化后两版均有 98 个声明，无新增或删除；缺少声明的预设组件仍不生效，不补入猜测组件。
- 规则版本由 5 升至 6，布局开关与配置 schema 不变；APK 身份包含规则版本，旧缓存不复用，旧报告在首页标记过期。
- Arkui 委托能力只来源于 `device.new.arkui` 成功项，旧设备页共享配置键的命中不会开放混淆委托。新版 `MainActivity` 按模式省略会员、运动或设备项，固定底栏索引只对基准版完整五项布局使用，其他布局依据添加时记录的标题身份。

## 缓存和回传

- 目标应用私有 `files/huawei_trim_scan` 下保存定位缓存和最后报告；不直接读写模块私有配置。
- SHA-256 身份覆盖包、版本、安装更新时间、规则版本、base/split 路径和内容。缓存原子写入，读取后反射复核完整描述符。更新、降级、split 变化、损坏、重扫请求均会重新解析。
- 正常未命中与歧义可以缓存；扫描基础设施错误不会被保存为永久未命中。单组类加载错误不妨碍其他已解析组安装。
- 模块 Provider 只接受自身 UID 或包管理器确认的运动健康 UID，通过 `call("report")` 接收有大小限制的结构化报告。没有配置修改、任意文件或查询接口。
- 报告包含协议/规则版本、APK 身份、请求/运行标识、顺序、检查/匹配集合与脱敏状态。功能 ID、数量和顺序均校验，旧运行或重复更新不得覆盖新结果。
- 模块持久化最后报告；首页仅在前台定期刷新。回传失败不阻塞 Hook，目标本地报告供下次启动重传。

## 验证

常规检查：`testDebugUnitTest lintDebug assembleDebug compileDebugAndroidTestKotlin`。

`ApkScanTest` 可通过 Gradle 属性 `scan.native`、`scan.apk`、`scan.resources`（JADX public.xml）、`scan.output` 使用桌面 DexKit 对真实 APK 执行生产查询。未提供属性时明确跳过该测试。桌面 native 库仅用于开发验证，不打包进 APK。

2026-10-01 的规则版本 6 验证使用新版真实 APK（SHA-256 `fb134f2facba915507a3cd3f2faa6b477f451ff0d7faeda638f0921d021cf63c`）：生产扫描检查并匹配 96/96 项，24 个功能组无失败；同一 APK 按未知版本策略扫描匹配 49/96 项，新版混淆委托和营销回调后备未开放。这不是 17.0.7.310 的兼容性实测。

本次完整执行 `testDebugUnitTest lintDebug assembleDebug compileDebugAndroidTestKotlin`，110 项 JVM 测试通过，无失败、错误或跳过。真实 APK 测试复核新版字段、内部类、列表访问器、营销回调和设备刷新入口；缺类、缺字段、缺回调及重复内容候选分别验证失败隔离。快捷入口和底栏的恢复验证属于单元测试，尚未替代实际页面滚动、重入验证。

Windows 桌面验证库由官方 DexKit 2.2.0 源码临时编译，位于忽略的 `build/dexkit-host`，不改变 Android 依赖。歧义测试源文件为 `app/src/test/fixtures/ScanCollision.java`，以 `javac --release 8` 编译，再用 Android SDK 的 D8 转为 DEX，通过 `scan.fixture` 传入。扫描报告为忽略的 `build/scan-17.0.7.320.json` 和 `build/scan-17.0.7.320.json.generic.json`。

本次 Debug APK 已检查 `minApiVersion=102`、`targetApiVersion=102`、`staticScope=true`，入口为 `love.nairain.huawei.hook.HookEntry`，唯一作用域为 `com.huawei.health`，含四种 ABI 的 DexKit 库；未打包 libxposed API 类。模块版本仍为 1.4/14。当前无连接设备，LSPosed 中的 Hook 安装、Provider/RemotePreferences 通信和下面的设备回归均待确认。

2026-09-08 的 44 项 JVM、`lintDebug`、`assembleDebug` 和 Compose 编译记录属于早期规则版本。规则版本 5 移除五个运动子页入口扫描项，共 96 项；版本 4 的 101/101 项 MuMu 17.0.7.310 APK 扫描记录仍属于历史验证，不代表版本 6 或新版 APK 的运行结果。JADX 的 `defpackage` 是展示包名；扫描和运行时使用 Dex 中的实际类名，如 `rxl`。桌面扫描可用 `scan.native`、`scan.apk`、`scan.resources` 配置；可选 `scan.fixture` 可指定包含重复内容候选的 DEX，验证重复内容候选不会误选。

APK 包含 API 102 入口、唯一运动健康作用域及四种 ABI 的 `libdexkit.so`。旧版 MuMu 扫描属于历史记录；17.0.7.320 的 Provider/RemotePreferences 跨进程通信、进程中断重试及各开关的页面恢复仍须运行时实测。

设备回归仍需启用 API 102 模块，验证首次扫描/缓存/预约重扫、页面重入、列表复用、RTL、关闭后原行为，以及登录、记录、设备连接和同步。没有实际设备证据的版本与业务流程不得标为已验证。
