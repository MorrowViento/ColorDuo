# ColorDuo

将状态栏的电池、Wi-Fi 和移动信号合并为一个三合一图标，基于 [HyperDuo](https://github.com/yixing233/HyperDuo) 移植。

当前版本：`0.1.0-alpha3`。适配对象是一加 13（PJZ110）、ColorOS 17、Android 17，参考 SystemUI `17.99.02`。此版本已完成静态适配与构建验证，真机验证结果会记录在 [ColorOS 适配说明](docs/COLOROS17.md)。

## 当前实现

- 独立的 ColorOS 电池视图适配，复用上游绘图和设置界面。
- 从系统状态模型读取电量、颜色、普通充电和 VOOC 充电状态。
- 读取 Wi-Fi、分卡信号与网络类型；将系统信号等级换算成四点显示。
- 默认值、组件位置与优先级、双卡选择、预览示例完全沿用上游 `e9dffbc`，不添加移植版专属布局。
- 环外堆叠信号、功率/温度格式、字体、字号和排序复用上游函数；ColorOS 适配器负责挂载原生视图。
- 配置修改实时应用；关闭模块、组件交还系统或电池视图卸载时恢复对应原生显示。

本版适配主状态栏。锁屏和快捷设置区域使用系统原生显示。时钟显示由上游规则决定；ColorOS 特殊动画等尚需真机验证的范围见适配说明。

## 安装测试

需要 root 和支持 **libxposed API 102** 的 LSPosed。应用 ID 是 `io.github.chorusfruit233.colorduo`，可与上游应用分别安装。

1. 从该分支的 GitHub Actions 构建产物下载测试 APK 并安装。
2. 在 LSPosed 启用 ColorDuo，作用域勾选 **系统界面（com.android.systemui）**。
3. 打开 ColorDuo 设置，然后通过 LSPosed 重启作用域或重启系统界面。
4. 确认图标出现，再测试总开关、Wi-Fi 开关、充电和深浅色背景。

上游圆环初始组件区为空，卡一/卡二独立信号默认关闭。需要在绘图页放置组件；双卡同时显示时，将两张卡的信号放在不同区域。同一区域的列表表示优先顺序，每次只显示一个组件。预览沿用上游固定示例值（卡一 2 级、卡二 4 级），实际状态栏读取手机信号。

覆盖安装保留已有配置。alpha1/alpha2 曾保存的自定义布局不会自动清空；可在绘图页重新安排，或清空各区域组件并关闭分卡开关，恢复上游初始布局。

设置中的框架连接状态不能单独证明 SystemUI 的 Hook 已命中。日志中应包含 `ColorOS adapter installed` 和 `ColorOS host active`，消息标识为 `ColorDuo`。

## 构建与验证

使用完整的 JDK 21、Android SDK Platform 37.0、Build Tools 37.0.0；Gradle Wrapper 已固定为 9.8.0：

```sh
python3 tests/upstream-parity.py
sh tests/coloros-policy.sh
sh tests/coloros-settings.sh
./gradlew :app:assembleDebug
```

首次构建需要联网。`.tools/maven` 包含 libxposed 依赖；其他依赖从 Maven 下载。APK 生成于 `app/build/outputs/apk/debug/app-debug.apk`。

测试包使用仓库内已有的开发签名。正式发行时应配置自己的发布签名，并保留同一签名用于后续升级。

## 致谢与许可证

原始绘图、布局规则和设置界面来自 yixing233 与 HyperDuo 贡献者；原有开发资料保存在 `docs/`。ColorOS 适配代码在 `ColorOsHooks.java` 和 `ColorOsPolicy.java`。

遵循 [GPL-3.0-only](LICENSE)。分发修改后的 APK 时需要提供其对应源码，并保留上游署名与许可证。
