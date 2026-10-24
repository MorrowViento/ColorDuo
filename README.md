# ColorDuo

**专注 ColorOS 17 的状态栏三合一图标模块。**

ColorDuo 是由 [chorusfruit-233](https://github.com/chorusfruit-233) 独立维护的 [HyperDuo](https://github.com/yixing233/HyperDuo) fork，将电池、Wi-Fi 和移动信号整合为一个可配置的状态栏图标。

本项目复用 HyperDuo 的绘图、配置与设置界面，为 ColorOS 17 维护自己的 SystemUI 适配、版本、测试和问题跟踪。ColorDuo 的支持范围、下载和使用说明以本仓库为准。

当前测试版本：**`0.1.0-alpha5`**。

[下载测试包与构建列表](https://github.com/chorusfruit-233/ColorDuo/actions/workflows/build.yml) · [配置说明](docs/CONFIGURATION.md) · [更新记录](docs/CHANGELOG.md) · [问题反馈](https://github.com/chorusfruit-233/ColorDuo/issues)

## 适配范围

| 项目 | 当前参考环境 |
| --- | --- |
| 系统 | **ColorOS 17** |
| 手机 | 一加 13 / PJZ110 |
| Android | 17 / API 37 |
| SystemUI | 17.99.02 |
| 框架 | 官方 LSPosed 2.2.1（7933），支持 libxposed API 102 |
| 权限 | 已 root，在 LSPosed 中启用模块 |

其他 ColorOS 17 机型和固件需要单独验证。HyperOS、MIUI 和其他系统版本不属于本项目当前的适配范围。

alpha5 已将同类电池视图的接管扩展到**主状态栏、下拉通知栏、控制面板头部和锁屏**。每个实例管理自己所在行的图标与恢复状态，目标是保持与上游一致的跨页面显示。离线检查覆盖接管、容器隔离和迁移恢复；实际展开/收起动画、跨页面配色、双卡、胶囊及特殊动画仍需目标手机验证，详见 [适配与验证记录](docs/COLOROS17.md)。

## 功能

- 电池圆环、Wi-Fi 弧形和每张 SIM 卡的信号点可配置位置与尺寸，支持整体配色和文字字体调整。
- 每个区域支持有序组件列表：前一项当前无法绘制时，由下一项接替。
- 卡一、卡二可独立放置；环外堆叠信号支持仅显示数据卡。
- 网络类型可放在环内或环外，读取 ColorOS 原生映射的 5G、5GA、4G+ 等文字。
- 电池旁可显示充电功率和温度；功率使用电池侧电流与电压，参考固件已有电流单位适配。
- 配置修改实时应用，关闭接管时恢复对应原生图标。

共享绘图与布局规则当前基于 HyperDuo `e9dffbc`。ColorOS 的视图、状态模型和固件差异由本仓库适配。

## 安装

1. 从本仓库的 [GitHub Actions](https://github.com/chorusfruit-233/ColorDuo/actions/workflows/build.yml) 下载 `ColorDuo-*.apk` 构建产物，解压 ZIP 后安装 APK。
2. 在 LSPosed 中启用 **ColorDuo**，作用域勾选 **系统界面（com.android.systemui）**。
3. 打开 ColorDuo，然后重启 SystemUI 或手机。
4. 在绘图页添加需要的组件，按 [配置说明](docs/CONFIGURATION.md) 调整顺序和位置。

ColorDuo 的应用 ID 为 `io.github.chorusfruit233.colorduo`，拥有独立的应用配置。覆盖安装同签名的 ColorDuo 测试包会保留已有配置，更新 APK 后需重启 SystemUI 才能加载新代码。日常修改显示设置会实时生效。

## 配置要点

圆环初始组件区为空，分卡信号默认关闭，需要自行添加组件。同一区域的列表是优先顺序，每帧只显示其中一个组件。

例如，要实现连接 Wi-Fi 时显示弧形、断开后显示网络类型，可以这样安排：

| 区域 | 从先到后的组件顺序 |
| --- | --- |
| 上部 | 电量数字 |
| 中部 | Wi-Fi 弧 → 网络类型 |
| 下部 | 当前使用卡的信号点 |

网络类型设为“环内”。双卡同时显示时，把两张卡的信号点放在不同区域。预览使用固定示例数据：卡一 2 级、卡二 4 级；实际状态栏读取手机信号。

完整说明、环外读数及“无 Wi-Fi”预览的含义见 [配置说明](docs/CONFIGURATION.md)。仓库内继承的 HyperDuo 旧截图作为历史素材保留，ColorDuo 当前设置以本仓库文档为准。

## 问题反馈

请到 [ColorDuo Issues](https://github.com/chorusfruit-233/ColorDuo/issues) 提交问题，并说明机型、完整固件版本、Android/LSPosed/ColorDuo 版本、组件配置、操作步骤和实际表现。开启调试日志后，可附上与 ColorDuo 有关的 SystemUI 加载及错误日志。

日志标识为 `ColorDuo`；`ColorOS adapter installed` 和 `ColorOS host active` 表示适配器安装及主状态栏接管已执行。设置页的框架连接状态只表示配置通道已经连接。

## 开发与文档

使用 JDK 21、Android SDK Platform 37.0、Build Tools 37.0.0 和 Gradle Wrapper 9.8.0：

```sh
python3 tests/upstream-parity.py
sh tests/coloros-policy.sh
sh tests/coloros-settings.sh
python3 tests/coloros-power.py
python3 tests/coloros-hosts.py
./gradlew :app:assembleDebug
```

APK 输出：`app/build/outputs/apk/debug/app-debug.apk`。测试包使用仓库内继承的开发签名；正式分发需要维护自己的发布签名。

- [ColorDuo 开发指南](docs/COLOROS17-DEVELOPMENT.md)：源码职责、构建、检查与分发。
- [ColorOS 17 适配与验证记录](docs/COLOROS17.md)：固件、接入点、已知边界与验收。
- [文档索引](docs/README.md)：当前说明与上游历史资料。

## 来源与许可证

原始项目：[yixing233/HyperDuo](https://github.com/yixing233/HyperDuo)。原始绘图、布局规则和设置界面由 yixing233 与 HyperDuo 贡献者开发；ColorOS 17 适配与 ColorDuo 版本由本仓库维护。

遵循 [GPL-3.0-only](LICENSE)。分发修改后的 APK 时须提供对应源码，并保留原作者署名和许可证。
