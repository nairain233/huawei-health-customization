# 华为运动健康精简 UI Hook 指南

## 安装顺序

1. `HookEntry` 校验目标包、主进程和首包。
2. `onPackageReady` 仅安装 `Application.attach(Context)` Hook。
3. attach 原方法结束后读取真实 versionName/versionCode；只放行 `17.0.7.310/1700007310`。
4. 读取一次 `RemotePreferences` schema v2 快照；读取失败使用全显示默认值。
5. 注册表按健康、运动、设备、我的、底栏逐项安装并记录 `Installed`、`Disabled`、`Unsupported` 或 `Failed`。

## 定位和数据处理

- 只维护目标版本的静态符号表，不使用 DexKit、不扫描未知版本、不保存解析缓存。
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

升级支持前必须重新取得 APK、核验类/方法/资源与页面层级，增加新的完整符号快照和针对性测试。不得让未知版本复用旧混淆符号。

每次修改至少运行 `testDebugUnitTest`、`lintDebug`、`assembleDebug`，检查 APK 的 API 102 元数据、唯一作用域及无 DexKit native 库，再在启用 LSPosed 的目标设备上回归。
