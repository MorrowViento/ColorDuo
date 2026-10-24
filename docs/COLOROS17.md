# ColorOS 17 适配与验证

## 参考固件

- 设备：一加 13 / PJZ110（用户提供）。
- 指纹：`OnePlus/PJZ110/OP5D0DL1:17/CP2A.260605.016/V.487427a-2227f32-224bc4c:user/release-keys`。
- SystemUI：17.99.02 / 179902，API 37，9 个 DEX。
- 参考 APK SHA-256：`a06bf8e0046a5e6a19347e09064e6ce8f79e0e963c78c8ce65f0fbf56d770e62`。
- 框架：模块要求 libxposed API 102。lsposed.zip 当前下载包 7912 的 framework.dex 已确认实现该 API；用户安装的 7933 需以实际加载日志验证。

## 接入方式

`HyperDuoModule` 按 `StatBatteryMeterView` 是否存在选择 ColorOS 适配器；原有 MIUI 适配仍保留。

| 功能 | 接入点 |
| --- | --- |
| 电池 View | `com.oplus.systemui.statusbar.pipeline.battery.ui.view.StatBatteryMeterView` |
| 绘制 | `battery_icon_view` 的 ImageView drawable |
| 原生视图更新 | `BatteryViewBinder.bind*`；外层调用前恢复原生状态，调用结束后保存最新原生状态并重装模块 drawable；按实例过滤的框架 setter 同时跟踪被内联的系统更新 |
| 电池状态 | `BatteryRepositoryImpl.getBatteryLevel/getChargeState/isSavingMode` 的 StateFlow |
| 每个 View 的颜色 | `LocationBasedBatteryViewModel.getBatteryColor` 与 `StatBatteryIcon.getTintColor` |
| Wi-Fi | `WifiRepositorySwitcher/WifiRepositoryImpl.getWifiNetwork` |
| 分卡信号 | `OplusCellularIconViewModelImpl.getIcon/getNetworkTypeText/getSubscriptionId` |
| 原生图标 | `StatusIconContainer.mIgnoredSlots`；仅撤销模块自己添加的 slot |

模块只接管 `PhoneStatusBarView` 下的电池实例。原生 Drawable、尺寸、ScaleType 和子视图可见性在接管前保存；系统更新完成后重新记录。异常时恢复原生显示，不再接管该实例。

绘图使用单独的 Canvas layer，避免上游渲染器清屏时影响相邻图标。SIM 和 Wi-Fi 状态采样限制为每秒一次，绘制回调不访问电话服务；熄屏时降低刷新频率。系统流缺失时可使用已有平台采样兜底；缺少电量时不隐藏原生图标。

## 第一轮真机验收

1. 启用后应出现默认圆环、电量、Wi-Fi 和分卡信号；日志应报告 adapter 安装和 host 激活。
2. 总开关关闭后，检查原生电池、百分比、充电图标和信号恢复；再次打开不应产生重复视图。
3. 修改大小、颜色和组件位置，确认即时更新；清空组件后原生信号应回来。
4. Wi-Fi 关闭、断连、重连；飞行模式；切换默认数据卡、停用 SIM；检查没有残留信号。
5. 插拔充电器、VOOC、低电量、省电模式、深浅色背景、横屏和屏幕熄灭后唤醒。
6. 切换系统电池样式，关闭模块后检查原生样式恢复。
7. 打开锁屏和快捷设置，确认本版仍使用原生布局。

记录手机完整系统版本、LSPosed 版本、操作步骤、前后截图及 ColorDuo 的加载/错误日志即可定位问题。

## 当前验证边界

已提供信号换算、slot 所有权释放、默认配置、配置传输和渲染测试，并构建可安装 APK。云端无法连接目标手机，因此这些检查不代表已经通过真机验收。

首版验收以主状态栏圆环为准。上游胶囊绘图和设置保留，但 ColorOS 的原生时钟接管、特殊通知/充电动画交接、环外读数的全部间距选项尚未完成真机验证。锁屏和快捷设置区域暂不接管。

测试签名沿用原仓库开发密钥；测试 APK 通过 Actions artifact 分发，工作流不自动发布 Release，不修改用户已有的 SystemUI 草稿附件。
