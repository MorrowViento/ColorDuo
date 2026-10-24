# ColorDuo 更新记录

本页记录 `chorusfruit-233/ColorDuo` 自身的测试版本，专注 ColorOS 17。HyperDuo 1.x 发布说明保留在 [release-notes/](release-notes/) 作为上游历史资料。

以下版本均于 2026-10-24 构建。离线检查与构建结果、参考固件数据和真机反馈分别记录，具体边界见 [适配与验证记录](COLOROS17.md)。

## 0.1.0-alpha4

- 修复网络类型采集：捕获实际 ViewModel、backing flow 和原生绑定模型，合并前后缀，支持 5GA、4G+ 等文字。
- 按参考固件的真实接口样本校正 mA → µA，解决功率被缩小 1000 倍并显示为 0.0W 的问题，保留共享功率公式和格式。
- 增加实际 Binder 电流样本、原生网络模型及空值的回归检查，继续执行共享源码一致性检查。
- [构建与检查通过](https://github.com/chorusfruit-233/ColorDuo/actions/runs/40080394596)，[下载测试包](https://github.com/chorusfruit-233/ColorDuo/actions/runs/40080394596/artifacts/12555017705)。两项修复待目标手机复测。

## 0.1.0-alpha3

- 恢复共享默认配置与迁移规则，撤回早期测试版本额外添加的初始布局和默认开启分卡。
- 环外信号、数据卡选择、功率温度格式、字体和排版复用共享函数；接入原生时钟与百分比交接条件。
- 增加 27 个共享源文件与固定上游基线的对比检查。
- 已通过构建和离线检查；用户随后反馈网络类型不显示、功率恒为零，由 alpha4 处理。

## 0.1.0-alpha2

- 修复缺少 InnerClass 元数据时无法识别已连接 Wi-Fi 模型的问题。
- 观察原生 Wi-Fi 图标转换路径，处理仓库初始值、断开与重连，分离 Wi-Fi 更新和电话服务采样限频。
- 补充 Wi-Fi 解析、连接恢复与图标所有权的回归检查，构建通过。

## 0.1.0-alpha1

- 引入 ColorOS 17 主状态栏适配器、独立应用 ID 和 ColorDuo 更新地址。
- 接入电池、颜色、VOOC、Wi-Fi 与分卡采样，保存并恢复原生视图和图标 slot。
- 提供 Gradle Wrapper、Android SDK 37 构建工作流与测试 APK。
- 目标设备日志确认适配器加载和主状态栏接管，Wi-Fi 问题在后续版本修复。
