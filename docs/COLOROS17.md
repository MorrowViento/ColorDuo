# ColorDuo：ColorOS 17 适配与验证

ColorDuo 是由 chorusfruit-233 独立维护、专注 ColorOS 17 的 HyperDuo fork。本页记录本仓库的系统接入、参考固件证据与验证进展。各 alpha 章节保留对应版本的修复记录；当前安装和配置见 [项目首页](../README.md)、[配置说明](CONFIGURATION.md)，本项目版本历史见 [更新记录](CHANGELOG.md)。

## 参考固件

- 设备：一加 13 / PJZ110（用户提供）。
- 指纹：`OnePlus/PJZ110/OP5D0DL1:17/CP2A.260605.016/V.487427a-2227f32-224bc4c:user/release-keys`。
- SystemUI：17.99.02 / 179902，API 37，9 个 DEX。
- 参考 APK SHA-256：`a06bf8e0046a5e6a19347e09064e6ce8f79e0e963c78c8ce65f0fbf56d770e62`。
- 框架：模块要求 libxposed API 102。用户安装的官方 LSPosed 2.2.1（7933）已在提供的加载日志中确认 API 102 和 Hook 注入。

## 接入方式

`HyperDuoModule` 按 `StatBatteryMeterView` 是否存在选择 ColorOS 适配器；原有 MIUI 适配作为继承代码保留。ColorDuo 当前的适配与验收范围为 ColorOS 17。

| 功能 | 接入点 |
| --- | --- |
| 电池 View | `com.oplus.systemui.statusbar.pipeline.battery.ui.view.StatBatteryMeterView` |
| 绘制 | `battery_icon_view` 的 ImageView drawable |
| 原生视图更新 | `BatteryViewBinder.bind*`；外层调用前恢复原生状态，调用结束后保存最新原生状态并重装模块 drawable；按实例过滤的框架 setter 同时跟踪被内联的系统更新 |
| 电池状态 | `BatteryRepositoryImpl.getBatteryLevel/getChargeState/isSavingMode` 的 StateFlow |
| 每个 View 的颜色 | `LocationBasedBatteryViewModel.getBatteryColor` 与 `StatBatteryIcon.getTintColor` |
| Wi-Fi | `WifiIcon.Companion.fromModel` 的实时模型，仓库 StateFlow 和框架连接状态兜底 |
| 分卡信号 | `OplusCellularIconViewModelImpl` / `OplusMobileIconViewModel` 构造时捕获 backing StateFlow；getter 用于兼容没有内联调用的固件 |
| 网络类型 | `Os17Binding.bindNetworkTypeText` 的实际模型；Interactor 原生映射用于提供可见性过滤前的制式文字 |
| 充电功率 | `BatteryManager.CURRENT_NOW` 经参考固件单位归一化后，传给上游 `BatteryPresence` 的原有计算 |
| 原生图标 | `StatusIconContainer.mIgnoredSlots`；仅撤销模块自己添加的 slot |

模块只接管 `PhoneStatusBarView` 下的电池实例。原生 Drawable、尺寸、ScaleType 和子视图可见性在接管前保存；系统更新完成后重新记录。异常时恢复原生显示，不再接管该实例。

绘图使用单独的 Canvas layer，避免上游渲染器清屏时影响相邻图标。电话服务采样限制为每秒一次，Wi-Fi 模型与连接通知独立更新缓存，绘制回调不访问电话服务；熄屏时降低刷新频率。系统流缺失时可使用已有平台采样兜底；缺少电量时不隐藏原生图标。

## 共享代码来源与当前规则

当前共享代码基线为 HyperDuo `e9dffbcc8f65a5f3dc379c0b25ccdc0462a8a28d`。本版本保留该基线的绘图、组件排序与配置迁移；ColorOS 系统接入和参考固件的数据单位归一化由 ColorDuo 实现。版本号、下载、问题跟踪与平台验证由本仓库独立维护。

- `TrioSettings`、`TrioAppearance`、`TrioRenderer`、预览与绘图设置沿用上游。`Prefs` 仅改变独立应用的重载广播名。
- 圆环初始组件区为空，分卡组件默认关闭；不再预设 Wi-Fi、卡一/卡二的优先列表。
- 每个区域每帧只选优先列表中首个可绘制组件。双卡同显由用户将两张卡分别放置在不同区域。
- 预览保持上游固定信号示例，实际读数按真实卡槽传给共享渲染器，不把预览值当作系统值。
- 环外视图直接使用上游的分卡/数据卡选择、堆叠绘制、功率温度格式、字体、字号、后缀缩放、间距、排序与空间分配函数。ColorOS 的 `system_icons` 是 LinearLayout，因此在原生电池旁挂载读数组，布局负责为它预留空间。
- 原生时钟容器 `clock_for_fake` 的隐藏条件来自上游 `hidesStatusClock()`；模块关闭、布局交还时恢复原生可见性，系统更新同样纳入原生状态跟踪。

`python3 tests/upstream-parity.py` 对比固定上游提交的 27 个共享源文件。只允许应用标识、配置刷新分发、启动器别名定位、现有辅助函数访问级别，以及电流采样入口调用 ROM 单位适配的差异；功率公式、渲染、默认值、预览、选择规则与迁移逻辑存在差异即失败。CI 使用完整提交历史执行此检查。

## 第一轮真机验收

1. 启用后应出现上游默认圆环；在绘图页放置电量、Wi-Fi 和信号组件后逐项检查。日志应报告 adapter 安装和 host 激活。
2. 总开关关闭后，检查原生电池、百分比、充电图标和信号恢复；再次打开不应产生重复视图。
3. 修改大小、颜色和组件位置，确认即时更新；清空组件后原生信号应回来。
4. Wi-Fi 关闭、断连、重连；飞行模式；切换默认数据卡、停用 SIM；检查没有残留信号。
5. 插拔充电器、VOOC、低电量、省电模式、深浅色背景、横屏和屏幕熄灭后唤醒。
6. 切换系统电池样式，关闭模块后检查原生样式恢复。
7. 打开锁屏和快捷设置，确认本版仍使用原生布局。

记录手机完整系统版本、LSPosed 版本、操作步骤、前后截图及 ColorDuo 的加载/错误日志即可定位问题。

## 当前验证边界

已提供信号换算、slot 所有权释放、默认配置、配置传输和渲染测试，并构建可安装 APK。云端无法连接目标手机，因此这些检查不代表已经通过真机验收。

当前验收以主状态栏圆环为准。胶囊绘图、原生时钟交接和环外间距已按共享规则接线，尚需目标手机验证。ColorOS 特殊通知/充电动画仍需独立验收；锁屏和快捷设置区域暂不接管。各项功能需按 ColorOS 17 的目标固件记录结果。

测试签名沿用原仓库开发密钥；测试 APK 通过 Actions artifact 分发，工作流不自动发布 Release，不修改用户已有的 SystemUI 草稿附件。

## alpha2：Wi-Fi 状态识别修复

用户日志确认 alpha1 已正常注入并激活电池视图，但 Wi-Fi 未绘制且原生图标仍显示；系统 Wi-Fi 日志同时记录了有效的两格、三格信号。

参考 APK 的 `WifiNetworkModel$Active` DEX 类没有 class annotations。alpha1 使用 `getSimpleName().equals("Active")`，在缺少内部类元数据时会误判这个已连接对象。alpha2 改用通过目标 ClassLoader 找到的实际类型，并对字段/方法取值做范围检查。

同时观察原生图标转换时使用的 Wi-Fi 模型，保留连接状态兜底，避免未订阅仓库的初始快照覆盖有效连接。框架采样采用 NetworkCapabilities 与关联状态，兼容 legacy TYPE_WIFI NetworkInfo 缺失及 VPN 默认路由。Wi-Fi 的缓存刷新独立于电话服务采样限频。

新增回归测试复现没有 InnerClass 元数据的二进制类名，并覆盖冷仓库、断开/重连、未知连接、弱信号、无效等级和 carrier-merged 状态。调试日志只记录连接状态、等级和 slot 接管结果，不记录 SSID/BSSID。

复测：直接覆盖安装 alpha2 后重启 SystemUI，检查连接时环内 Wi-Fi 与原生图标交接、关闭/重连，以及关闭模块后恢复原生 Wi-Fi。

## alpha3：恢复上游默认与共享行为

撤回 alpha1/alpha2 额外添加的默认组件布局及默认开启分卡信号，恢复上游的默认值和配置迁移。已有用户配置保持原样；升级不会猜测并删除用户使用过的布局。可在绘图页清空各区域组件并关闭分卡开关，恢复上游初始布局。

删除 ColorOS 简化版环外绘制，复用上游原有函数；修正单卡在卡槽二、仅显示数据卡、信号尺寸和间距、功率/温度双行、网络类型字号与 A 后缀、自定义字体和环外排序的设置接线。原生百分比与时钟是否隐藏也交给上游条件判断。

验证包含共享源码对比、配置传输、显式空布局、旧配置迁移、双卡独立放置、单卡/双卡/数据卡读数选择、实际绘图以及 alpha2 的 Wi-Fi 回归。覆盖安装 alpha3 后重启 SystemUI，在相同配置和信号条件下对照上游预览；实际双卡、RTL、胶囊和特殊动画仍需真机复测。

## alpha4：网络类型与充电功率

用户在 alpha3 上确认网络类型不显示、充电功率持续为 0。

参考 DEX 中没有对 `OplusCellularIconViewModelImpl.getIcon/getNetworkTypeText` 的调用，原生调用已经内联。仅 Hook 这些 getter 无法获取模型。alpha4 同时观察 Cellular 和包装 ViewModel 的构造，直接记录卡号和 backing flow，并观察参考 DEX 中仍由初始化与 Flow collector 调用的 `Os17Binding.bindNetworkTypeText`。

ColorOS 的网络类型值是 `OplusNetworkTypeTextModel`，含有具体文字模型的 prefix/suffix；alpha3 只接受 CharSequence，拿到模型后仍会丢弃。alpha4 使用原生映射合并前后缀，保留 `5GA`、`4G+` 等文字。原生 UI 可能因 Wi-Fi/数据状态过滤掉标签，适配器同时记录 Interactor 的原始映射，由上游配置决定 ColorDuo 是否显示。无服务、飞行模式和卡号切换会清理当前类型；模块接管类型时，仅跟踪并隐藏原生 `mobile_type_text/mobile_type_image`，交还系统时恢复。

功率单位通过用户的两组只读数据确认：

- `dumpsys battery` 报告电流 -285，电池电压 4465 mV，100% 电量、FULL 且 AC attached；`current_now` 节点读数 -221，`voltage_now` 为 4463000 µV。读数取得时间不同，电流会变化。
- `IBatteryPropertiesRegistrar.getProperty(CURRENT_NOW)` 返回的 long 为 `fffffffffffffe10`，解码后是 -496；该固件在 Android 约定 µA 的接口里报告 mA。
- -496 mA、4465 mV 对应 2.21464 W。alpha3 按 µA 计算成 0.00221464 W，上游格式化后为 `0.0W`。alpha4 在采样入口先乘 1000，再使用原有功率公式和格式化，得到 `2.2W`。

单位配置严格限定本说明的已验证完整固件指纹。其他固件继续使用 Android 的 µA 约定，不根据电流大小猜测单位；未支持值和溢出保持 unknown，真实 0 仍为 0。功率仍表示电池侧瞬时读数；100% 时会随涓流和系统负载变化。

`python3 tests/coloros-power.py` 提取并执行生产代码中的 `wattsOf`、`powerText`、`perfText`，覆盖实际 Binder 样本、单位误差复现、格式化、真实零、unknown、溢出、正负电流、充满但仍插电、拔电和温度双行。网络模型测试覆盖 backing field、接口 getter、原生图标映射、前后缀及空值。共享源码一致性检查保留，功率公式未修改。

覆盖安装 alpha4 后重启 SystemUI，检查已有网络类型组件在 5G/4G 切换、Wi-Fi 切换和飞行模式下更新；插电时功率应随实际电流变化。日志首个 `ColorOS battery` 样本包含原始电流、单位、归一化电流和电压；开启调试日志可持续记录变化。未访问 root 命令或 sysfs 作为运行时数据源。
