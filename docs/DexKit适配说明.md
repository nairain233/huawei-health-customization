# DexKit 适配与扫描报告

## 使用及统计

仅作用于 `com.huawei.health` 首包主进程。attach 完成后读取一次配置快照，服务独立安装；后台线程校验 APK 内容身份、读取缓存或完整扫描，不在主线程做 Dex/磁盘工作。全部 78 个布局开关参与扫描，成功条件为该项必要定位和内容识别均可用，与开关状态无关。

设置首页显示已匹配 m/n、已检查 p/n、版本、更新时间及服务状态。尚未扫描、运行中、完成、失败、过期和状态待确认分开展示。两分钟前的运行中报告只作为待确认状态；它不能证明目标进程仍在运行。匹配统计不宣称 Hook 安装或设备回归成功。

预约通过 RemotePreferences 的 `scan.request` UUID 保存。只有同步提交成功才显示已安排，失败尝试恢复旧值并提示。下次目标主进程启动时消费请求，切换前后台不触发。扫描未完整完成不确认请求，下次启动重试；扫描期间不读取新的布局开关快照。

## 定位依据

基准是仓库“调试”中的 17.0.7.310 APK 与 JADX 产物。查询使用唯一候选、完整参数和返回类型；继承入口按最近声明层级定位。扫描器与页面逻辑分离，回调不运行 DexKit。

| 功能组 | 主要依据 | 未通过时 |
| --- | --- | --- |
| 健康顶部 | HomeFragment 生命周期、health_tab_titlebar、CustomTitleBar 两个可见性入口 | 保留对应控件 |
| 顶部卡片 | HomeCardAdapter 构造/列表刷新、getCardName 字符串与签名 | 保留卡片 |
| 健康卡片 | initCard 日志、被访问的 List 字段、唯一 List 刷新入口、CardConstructor/FunctionSetSubCardData 标识访问器 | 保留该组 |
| 编辑卡片 | 基准版本已核验的 l() 与 LinearLayout 字段 m | 未知版本暂不启用 |
| 健康快捷入口 | initViewHolder/updateView 日志、共同的 GridTemplate 字段及内容访问器 | 保留入口；未知版本不使用中文标题后备 |
| 运动顶部 | SportEntranceFragment 生命周期和对应资源名称 | 缺少资源的项目保留 |
| 运动区块 | SportTabPageResTrigger 的继承入口、4040 常量证据与运行时范围检查、SectionBean 类型化访问器 | 无法确认的项目保留；仅依赖标题的项目限基准版本 |
| 运动快捷绑定 | setQuickEntryLayout 日志、绑定签名及 holder 根容器字段 | 基准版本限定后备，缺失则不安装该入口 |
| 设备 | 两种设备 Fragment 生命周期与独立资源名 | 缺少资源的项目保留 |
| 我的列表 | initRecyclerList/initOverseaRecyclerList、Adapter 调用的行类型及标题访问器 | 缺少必要访问器或内容身份时保留 |
| 我的网格 | 四种 getCardName 业务字符串、返回类型与 Adapter 刷新入口 | 独立保留未命中模型 |
| 底栏 | 清空入口的 removeAllViews 调用关系、添加/布局完整签名、当前标题资源身份 | 不根据未知版本的位置猜测 Tab |

资源名压缩时可读取当前 APK 的已知 R 类常量，并核验资源类型及所属包。历史数字 ID 和固定底栏索引仅在基准版本使用。显示文本后备不扩散到未知版本。静态查询命中不保证服务端动态内容均可识别。

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

2026-09-08 验证：44 项 JVM 测试通过，`lintDebug`、`assembleDebug` 和 Compose 测试编译通过。提供的 17.0.7.310 APK 按基准规则匹配 78/78；同一 APK 禁用历史版本后备时匹配 53/78，此结果不能代表其他版本已验证。真实查询还覆盖共享依赖缺失与候选歧义隔离。`scan.fixture` 可指定由 `tools/scan-fixtures/ScanCollision.java` 经 javac/D8 生成的 DEX，验证重复内容候选不会误选。运动快捷入口的两个方法共用日志和签名，使用 SingleEntryContent 调用关系进一步消歧。

APK 已检查 API 102 入口、唯一运动健康作用域及四种 ABI 的 `libdexkit.so`。Compose 状态测试仅编译，Provider/RemotePreferences 跨进程通信、进程中断重试及真实页面恢复仍需设备验证；本次无可用设备，未将这些项目标记为通过。

设备回归仍需启用 API 102 模块，验证首次扫描/缓存/预约重扫、页面重入、列表复用、RTL、关闭后原行为，以及登录、记录、设备连接和同步。没有实际设备证据的版本与业务流程不得标为已验证。
