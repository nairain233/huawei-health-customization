# 华为运动健康精简 UI Hook 指南

## 安装顺序

1. `HookEntry` 校验目标包、主进程和首包。
2. `onPackageReady` 仅安装 `Application.attach(Context)` Hook。
3. attach 原方法结束后读取真实包信息；版本用于扫描身份及限定后备，不作为全局白名单。
4. 读取一次 `RemotePreferences` schema v2 快照；读取失败使用全显示默认值。
5. 服务入口独立校验安装；工作线程读取缓存或运行 DexKit，复核精确描述符后按匹配结果过滤布局配置，再安装独立功能组。
6. 每组注册期间保持放行，成功后激活；失败先保持整组无效再回滚。首次已经发生的构造和绑定事件不回放。

## 定位和数据处理

- 使用 DexKit 2.2.0 扫描 base/split APK，唯一匹配、精确签名和运行时复核全部通过才启用。缓存及报告见 [DexKit 适配说明](DexKit适配说明.md)。
- 优先生命周期、Adapter、资源 ID/名称和模型类型；当前版本文案仅作限定后备。
- 列表一律基于输入创建浅拷贝，不原地删除目标应用集合。
- 未识别内容保持显示。一个数据源失败时只跳过该源，并输出不含业务数据的诊断。
- RecyclerView 分组过滤后清掉无内容标题、连续间距和首尾冗余项。

## View 操作

- 通用工具只以弱键保存原始可见性和布局尺寸/边距。
- 非主线程调用通过 `View.post` 切回主线程；重复折叠和恢复保持幂等。
- 禁止静态强引用 Activity、Fragment 或 View。
- 底栏按每个 `HealthBottomView` 保存独立弱状态，保留原 Tab 索引，并在 LTR/RTL 中重新布局可见项。

## 新增或升级版本

新增规则必须取得实际 APK、核验方法/字段/调用关系及资源语义，增加定位证据和针对性测试。未知版本只使用通过校验的能力，不复用旧混淆符号或历史资源数字 ID。规则变更递增 `ScanProtocol.RULES`。

每次修改至少运行 `testDebugUnitTest`、`lintDebug`、`assembleDebug`，检查 APK 的 API 102 元数据、唯一作用域、DexKit Android native 库及未打包框架 API，再在启用 LSPosed 的目标设备上回归。
