# ColorDuo

**面向 ColorOS 17 的状态栏三合一图标模块。**

由 [chorusfruit-233](https://github.com/chorusfruit-233) 独立维护，fork 自 [HyperDuo](https://github.com/yixing233/HyperDuo)。本项目复用原始绘图与设置界面，为 ColorOS 17 维护独立的系统适配、版本和问题跟踪。

## 环境与范围

- 参考设备：一加 13 / PJZ110，ColorOS 17、Android 17、SystemUI 17.99.02。
- 框架：支持 libxposed API 102 的 LSPosed，参考官方 2.2.1（7933）。
- 需要 root，作用域为 `com.android.systemui`。
- 当前接管主状态栏，锁屏和快捷设置区域保持原生。
- 其他 ColorOS 17 固件需单独验证；HyperOS、MIUI 和其他系统版本不在当前适配范围内。

## 功能

电池、Wi-Fi、分卡信号和网络类型可组合进图标，按区域配置组件优先顺序。卡一、卡二可独立放置，环外可显示堆叠信号、网络类型、充电功率和电池温度；配置修改实时应用。

当前为 `0.1.0-alpha4` 测试版本。原生状态模型与参考固件的电流单位已接入，离线测试和构建通过，目标手机复测进展见 [适配记录](docs/COLOROS17.md)。

## 安装与设置

1. 从 [ColorDuo Actions](https://github.com/chorusfruit-233/ColorDuo/actions/workflows/build.yml) 下载测试包，解压后安装 `ColorDuo-*.apk`。
2. 在 LSPosed 启用 **ColorDuo**，作用域勾选系统界面。
3. 重启 SystemUI 或手机。
4. 在绘图页添加所需组件。圆环初始组件区为空；同一区域按列表顺序选择一个组件显示。

应用 ID：`io.github.chorusfruit233.colorduo`。覆盖安装同签名测试包保留配置，更新 APK 后需要重启 SystemUI。

预览使用模拟信号；真机显示实际读数。网络类型接替 Wi-Fi 的配置方法、双卡放置和环外读数说明见本仓库的 [配置指南](docs/CONFIGURATION.md)。

## 项目入口

- [源码与当前说明](https://github.com/chorusfruit-233/ColorDuo)
- [构建与下载](https://github.com/chorusfruit-233/ColorDuo/actions/workflows/build.yml)
- [问题反馈](https://github.com/chorusfruit-233/ColorDuo/issues)
- [ColorDuo 更新记录](docs/CHANGELOG.md)

## 致谢与许可证

感谢 yixing233 与 HyperDuo 贡献者提供原始绘图、布局及设置界面。ColorDuo 专注 ColorOS 17 的适配与维护。

遵循 [GPL-3.0-only](LICENSE)，分发 APK 时提供对应源码并保留原作者署名。
