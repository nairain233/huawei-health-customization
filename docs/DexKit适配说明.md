# DexKit 适配与扫描报告

## 使用及统计

仅作用于 `com.huawei.health` 首包主进程。attach 完成后读取一次配置快照，服务独立安装；后台线程校验 APK 内容身份、读取缓存或完整扫描，不在主线程做 Dex/磁盘工作。规则版本 8 包含 96 个布局开关，成功条件为该项必要定位和内容识别均可用，与开关状态无关。扫描晚于页面创建时，Activity 弱引用追踪器对已创建视图重应用规则。

设置首页显示已匹配 m/n、已检查 p/n、版本、更新时间及服务状态。尚未扫描、运行中、完成、失败、过期和状态待确认分开展示。两分钟前的运行中报告只作为待确认状态；它不能证明目标进程仍在运行。匹配统计不宣称 Hook 安装或设备回归成功。

预约通过 RemotePreferences 的 `scan.request` UUID 保存。只有同步提交成功才显示已安排，失败尝试恢复旧值并提示。下次目标主进程启动时消费请求，切换前后台不触发。扫描未完整完成不确认请求，下次启动重试；扫描期间不读取新的布局开关快照。

## 定位依据

规则 8 统一使用实际 APK 的结构证据，不再区分基准版与未知版本。17.0.7.310 的反编译代码、17.0.7.320 和 17.0.8.300 的原始 APK 用于归纳规则；当前两份原包用于生产查询回归，310 尚无原包，不能宣称通过同等扫描验证。版本名与版本号仅影响缓存身份和诊断，不授权任何后备。

| 功能组 | 当前主要依据 | 未通过时 |
| --- | --- | --- |
| 健康顶部 | HomeFragment 生命周期、当前标题栏资源、完整可见性入口 | 保留对应控件 |
| 健康卡片 | Adapter 构造/刷新、模型签名与继承；圆环额外要求首页创建关系 | 每种内容身份独立跳过，合法多模型共同启用 |
| 编辑卡片 | 编辑标题 R 字段或当前常量、可见性调用、无参更新入口；itemView 的最近继承字段及 modify_cards_layout | 保留编辑入口，不依赖 n/l/m 字段 |
| 运动子页和区块 | 稳定页面、当前容器 ID、选中子页、4040 推荐入口与类型化 SectionBean getter | 标题仅在已确认页面/容器/绑定范围内识别，未知内容保持显示 |
| 快捷绑定 | 同日志候选的 SingleEntryContent 调用、动态 holder 类型、实际调用的 RelativeLayout accessor | 缺根字段或歧义时关闭单项视图路径，保留其他完整路径和整区功能 |
| 旧设备页 | 两个 Fragment 各自的生命周期及资源，刷新回调由营销 API 与局部更新调用链定位 | 两页独立，回调失败不授权缺失入口 |
| Arkui | DeviceDelegateEnum 初始化的工厂、create 的构造关系、委托继承及业务日志/功能布局 | 普通角色唯一，功能角色允许经验证的多个实现；不得借用旧设备页能力 |
| 我的列表/网格 | 初始化日志、Adapter 调用的完整模型 getter、当前 R.string ID/模型类型身份 | 每项独立保留，无历史数字 ID 后备 |
| 我的营销 | Adapter 内部 Map 成功回调、OnSuccessListener、filterMarketingRules；唯一请求链同时包含 4168/9013 和回调构造/订阅 | 缺调用关系或歧义时保留；过滤范围仍仅限这两个位置 |
| 底栏 | 清空/添加/布局完整描述符、添加时的当前标题 ID、原始 itemIndex | 无标题证据时保持显示，不使用固定五项索引 |

`LayoutResolution` 保存语义角色到完整成员描述符的 bindings、当前 resourceIds、模型/资源/委托 identities、页面 capabilities、requirements 和固定失败原因 issues。HookContext 持有自己的解析器和视图选择器；不再使用进程级可变别名表。方法、模型 getter 和字段均无同名或同参数数量回退。

页面安装统一通过 installSource 创建能力配置上下文，再在同一上下文中安装事务和 Hook，避免复制上下文后使用另一份事务状态。扫描复用方法查询和继承链结果；缺少运动子页身份时停用依赖选中子页的路径，单个旧设备页缺失不影响另一页及 Arkui。

Resources 查询与限定 R 类的所有合法候选合并校验，字段支持 public static int；候选必须属于 com.huawei.health、类型正确且无冲突。页面只消费这份快照，同时通过反向 ID 身份支持压缩资源名。快照不持有 Activity、Fragment 或 View。

规则 8 缓存复核完整描述符、资源当前值和绑定/资源/身份依赖，缺失绑定不能由剩余描述符冒充成功。静态初始化器仅作 Dex 关系证据，不进入 Java 反射复核集。旧规则缓存与报告过期，预约重扫流程不变。每个开关存在至少一条完整可安装路径才计入 m/n；页面能力和局部失败另存于定位快照与脱敏日志。扫描命中仍不代表 Hook 安装和设备流程通过。

## 规则 7：17.0.8.300 的历史定位变化

以下内容记录旧规则的证据和限制，当前执行逻辑以规则 8 为准。

- 编辑卡片改为 `FunctionSetCardViewHolder.k()V`，`n:LinearLayout` 绑定 `modify_cards_layout`；旧 `l()` 已是延时任务入口，不得复用。
- `SCUI_TwoModelCardData` 同时来自 `ModelSetCardData` 和实际 Dex 类 `ugc`，两者均继承 `AbstractBaseCardData`。已知新版只接受这两个完整签名及完整候选集合；新增第三个候选、缺少模型或继承校验失败时整个健康卡片组跳过。未知版本仍执行唯一查询，不能复用新版例外。
- `HomeCardAdapter` 刷新由扫描别名定位到 `b(ArrayList)`。运动快捷入口变为 `x(ColumnLayoutAdapter$d,int)`，根字段为 `cm:RelativeLayout`，绑定前恢复上一条目的模块快照，再执行宿主绑定并按新内容隐藏，防止覆盖宿主刚计算的尺寸。
- 我的列表定位到 `xxs.t()/m()`，行模型为 `xwc`，标题和类型访问器为 `e()/g()`；网格四模型为 `xwp/xwn/xwd/xxc`。营销业务回调为 `PersonalCenterRecyclerViewAdapter$a$5.b(Map)`，`onSuccess(Object)` 仍是桥接；调用方只请求 4168/9013，过滤范围不变。
- 旧设备营销刷新入口分别为 `DeviceFragment.a(List)` 和 `CardDeviceFragment.c(MarketingApi,Map)`。`DeviceDelegateEnum` 的工厂确认主卡/列表/提示/我的手表/表盘委托为 `ssf/ssy/sth/sse/sto`，功能委托为 `stc/stp/ssb`，特色委托为 `ssx`。扫描及运行时不使用 JADX 展示包 `defpackage`。
- 新版资源表为 56,258 项，新增 3,411、删除 2,370、同名 ID 改动 24,447 项；资源字符串名有压缩，如“关于”现为 `0x7f021181`。当前 APK 的 R 字段为 `public static int`，资源后备不再要求 final；“我的”静态 ID 表已按新版逐项重新核对。底栏标题由当前 R 字段的扫描别名识别，完整五项顺序及按模式省略项的代码已复核，静态索引限制保持不变。
- 声明服务由 98 增至 99，新增 `com.huawei.health.ui.notification.utils.MenstrualWidgetRemoteWorkerService`，未加入屏蔽预设。服务功能、schema v3、96 个布局开关与模块版本 1.4/14 均不变。
- 规则版本升至 7，旧缓存失效，旧报告显示过期；只对精确匹配 17.0.8.300 的版本开放专用符号、数字 ID 与标题后备。

## 17.0.7.320 的历史定位变化

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

### 2026-10-04：17.0.8.300，规则版本 7

- 使用上述 SHA-256 的真实新版 APK 执行生产查询，检查并匹配 96/96 项，24 个功能组无失败；编辑卡片、两个圆环模型、快捷入口绑定、两处设备刷新、九个 Arkui 委托及营销回调的精确描述符均通过断言。同一 APK 仅按资源表和通用规则扫描为 43/96，复现适配前的受限扫描数量；这不代表运行时所有 R 字段解析后的通用统计。
- 注入额外同时返回圆环/我的群组标识的 DEX 候选，确认两个对应组判为歧义，编辑卡片及家庭健康仍可用。缺编辑字段、快捷根字段、主卡委托、营销回调与健康 Adapter 分别验证局部跳过；R 字段测试覆盖 final/非 final、错误字段类型、非静态/私有字段、错误资源类型/所属包、无效 ID 及候选冲突。
- 完整执行 `testDebugUnitTest lintDebug assembleDebug assembleRelease compileDebugAndroidTestKotlin`，146 项 JVM 测试通过，0 失败、0 错误、0 跳过，Lint 无问题。构建未升级依赖，模块仍为 1.4/14。
- 两份 APK 均核验 API 102 元数据、`staticScope=true`、入口 `love.nairain.huawei.hook.HookEntry`、唯一 `com.huawei.health` 作用域及四种 ABI 的 DexKit 库，DEX 类定义中没有 libxposed API 类。Release 产物为未签名 APK；Debug 可用于设备安装验证。
- 扫描结果位于忽略的 `build/scan-17.0.8.300.json` 与 `.generic.json`，APK/测试检查汇总为 `build/verification-17.0.8.300.json`；负例 DEX 由 `app/src/test/fixtures/ScanCollision.java` 通过 `javac --release 8` 与 D8 生成，使用 `scan.fixture` 传入。我的页 21 个资源名称的数字后备均与新版 R.string 逐项复核。
- `adb devices` 没有连接设备。首次扫描/缓存/预约重扫、Provider/RemotePreferences 通信、页面回放、编辑卡片、快捷入口列表复用与恢复、设备页、底栏/RTL，以及登录、记录、连接和同步均未完成设备回归，不能把静态扫描与 JVM 检查称为实机验证。

### 历史验证记录

2026-10-02 的运行时编排优化将设备各组配置键限定为该组的扫描能力，观察器与排队任务随实际安装结果激活和撤销；扫描完成回放按实际 Fragment 实例限定页面，异步失败仅停用相应安装组。查询规则与缓存协议未改变，规则仍为 6。第一批实现、127 项 JVM 及 Debug/Release APK 验证记录见 [模块优化审查](模块优化审查.md) 的“实施记录”；当前设备页面回归仍待确认。

常规检查：`testDebugUnitTest lintDebug assembleDebug compileDebugAndroidTestKotlin`。

`ApkScanTest` 可通过 Gradle 属性 `scan.native`、`scan.apk`、`scan.resources`（JADX public.xml）、`scan.output` 使用桌面 DexKit 对真实 APK 执行生产查询。未提供属性时明确跳过该测试。桌面 native 库仅用于开发验证，不打包进 APK。

2026-10-01 的规则版本 6 验证使用新版真实 APK（SHA-256 `fb134f2facba915507a3cd3f2faa6b477f451ff0d7faeda638f0921d021cf63c`）：生产扫描检查并匹配 96/96 项，24 个功能组无失败；同一 APK 按未知版本策略扫描匹配 49/96 项，新版混淆委托和营销回调后备未开放。这不是 17.0.7.310 的兼容性实测。

本次完整执行 `testDebugUnitTest lintDebug assembleDebug compileDebugAndroidTestKotlin`，110 项 JVM 测试通过，无失败、错误或跳过。真实 APK 测试复核新版字段、内部类、列表访问器、营销回调和设备刷新入口；缺类、缺字段、缺回调及重复内容候选分别验证失败隔离。快捷入口和底栏的恢复验证属于单元测试，尚未替代实际页面滚动、重入验证。

Windows 桌面验证库由官方 DexKit 2.2.0 源码临时编译，位于忽略的 `build/dexkit-host`，不改变 Android 依赖。歧义测试源文件为 `app/src/test/fixtures/ScanCollision.java`，以 `javac --release 8` 编译，再用 Android SDK 的 D8 转为 DEX，通过 `scan.fixture` 传入。扫描报告为忽略的 `build/scan-17.0.7.320.json` 和 `build/scan-17.0.7.320.json.generic.json`。

本次 Debug APK 已检查 `minApiVersion=102`、`targetApiVersion=102`、`staticScope=true`，入口为 `love.nairain.huawei.hook.HookEntry`，唯一作用域为 `com.huawei.health`，含四种 ABI 的 DexKit 库；未打包 libxposed API 类。模块版本仍为 1.4/14。当前无连接设备，LSPosed 中的 Hook 安装、Provider/RemotePreferences 通信和下面的设备回归均待确认。

2026-09-08 的 44 项 JVM、`lintDebug`、`assembleDebug` 和 Compose 编译记录属于早期规则版本。规则版本 5 移除五个运动子页入口扫描项，共 96 项；版本 4 的 101/101 项 MuMu 17.0.7.310 APK 扫描记录仍属于历史验证，不代表版本 6 或新版 APK 的运行结果。JADX 的 `defpackage` 是展示包名；扫描和运行时使用 Dex 中的实际类名，如 `rxl`。桌面扫描可用 `scan.native`、`scan.apk`、`scan.resources` 配置；可选 `scan.fixture` 可指定包含重复内容候选的 DEX，验证重复内容候选不会误选。

APK 包含 API 102 入口、唯一运动健康作用域及四种 ABI 的 `libdexkit.so`。旧版 MuMu 扫描属于历史记录；17.0.7.320 的 Provider/RemotePreferences 跨进程通信、进程中断重试及各开关的页面恢复仍须运行时实测。

设备回归仍需启用 API 102 模块，验证首次扫描/缓存/预约重扫、页面重入、列表复用、RTL、关闭后原行为，以及登录、记录、设备连接和同步。没有实际设备证据的版本与业务流程不得标为已验证。

## 规则 8 的重构验证（2026-10-04）

- 两份 APK 使用同一个 LayoutScanner API，扫描不接收版本或 known 标记。分别输出 build/scan-rules8-7320.json 和 build/scan-rules8-8300.json，检查 96 个开关、快捷绑定、设备刷新、10 项 Arkui 页面能力及 9 个实际委托。
- 17.0.7.320 的 Maca 功能委托继承 BaseFeatureDelegate，新版继承 CommonFeatureDelegate；通用规则使用真实共同父类、工厂关系和功能布局共同核验。
- 桌面测试以各自 public.xml 校验各自 R.java 的资源值，覆盖压缩资源名与非 final 字段；生产环境使用当前宿主 Resources 和限定 R 字段，不读取反编译文件。
- app/src/test/fixtures/build_scan_fixtures.py 生成四份忽略目录中的 DEX，覆盖 holder/方法/字段改名、同日志干扰、额外绑定歧义与缺根字段。通过 scan.structure 属性传给 StructuralScanTest；夹具不会打包进模块。
- JVM 检查还覆盖同参数数量重载、模型 getter/字段无证据不调用、资源快照隔离、反向身份冲突、缓存缺绑定/资源/身份、规则 7 过期和内容候选失败隔离。
- JVM 共 152 项，0 失败/错误/跳过；Debug/Release 构建、Lint、设备测试编译通过。两份 APK 的 API 102 元数据、唯一 com.huawei.health 作用域、四个 DexKit ABI 和未打包 libxposed API 类定义已核验，记录于 build/verification-rules8.json。
- 后续检测到 API 37 设备，已安装宿主 17.0.8.300。connectedDebugAndroidTest 在安装测试 APK 时被系统以 INSTALL_FAILED_USER_RESTRICTED 拒绝，执行 0 项测试，不能记为设备通过。UTP 失败清理卸载了原模块，已备份原 APK 到 build/device-before-rules8.apk；恢复安装同样遭到设备安装限制，需要手机端允许安装后恢复。宿主未卸载，模块配置保留情况尚不能核对。用户随后要求不测试设备侧状态，本轮停止设备操作并保留备份。后续不要使用会自动清理已有模块安装的 connectedDebugAndroidTest；需明确保留已有安装和用户配置再做设备测试。
- 首次扫描/缓存/预约重扫、页面重入、列表复用、关闭开关、RTL 与登录/记录/设备连接/同步仍待运行时验证。310 仅有源码证据，未执行本轮真实 APK 扫描。
