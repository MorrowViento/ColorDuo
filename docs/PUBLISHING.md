# 构建与模块仓发布

## GitHub Actions

| 工作流 | 用途 |
| --- | --- |
| [Build ColorDuo](../.github/workflows/build.yml) | main 的代码推送、PR 和手动触发；检查通过后构建测试包 |
| [Checks](../.github/workflows/checks.yml) | 两种构建共用的五组行为回归及发布元数据检查，不安装 Android SDK |
| [Prepare release](../.github/workflows/release.yml) | 手动构建私有签名的 release APK，上传发布素材并创建本仓库 Release 草稿 |

文档修改跳过测试包构建；同一分支的新构建取消旧构建。Android 环境统一使用 JDK 21、SDK 37 和仓库内的 Gradle Wrapper，Gradle 缓存由公共安装步骤管理。

测试包名称为 `ColorDuo-<version>-debug-<commit>.apk`，版本从 Gradle 产物读取。下载包还含 `SHA256SUMS` 和 `build-info.json`；后者记录实际包名、版本、签名指纹、Xposed 元数据和模块仓用户名匹配结果。

## 发布签名与草稿

在源码仓库 Actions secrets 中配置：

| Secret | 内容 |
| --- | --- |
| `COLORDUO_KEYSTORE_BASE64` | 私有 keystore 的单行 Base64 |
| `COLORDUO_KEYSTORE_PASSWORD` | keystore 密码 |
| `COLORDUO_KEY_ALIAS` | 签名别名 |
| `COLORDUO_KEY_PASSWORD` | 私钥密码 |

在 `docs/CHANGELOG.md` 添加对应版本的 `## <version>` 条目，再手动运行 Prepare release，填写版本名和递增的 versionCode。流程校验 APK 版本、签名、release 的非调试标记、现代 Xposed 入口及作用域；公开开发签名会被拒绝。签名文件仅在 runner 临时目录使用，任务结束时清除。

开发测试包继续使用原签名以支持覆盖升级。没有私有签名环境时，本地 release 构建产出未签名 APK。换成新签名后，旧开发签名测试包不能直接覆盖安装。

流程只创建源码仓库的 Release 草稿，并附 APK 和校验文件。带后缀的版本自动标记为预发布；应用内检查更新只查询稳定 Release，预发布包通过 Actions 或 Release 页面下载。模块仓素材位于 Actions 发布 bundle 的 `README.md`、`SUMMARY`、`CHANGELOG.md`；当前没有向外部模块仓自动提交申请或上传发布的步骤。

## 模块仓条件核对

核对日期：2026-10-24。规则来自 [submission README](https://github.com/Xposed-Modules-Repo/submission/blob/1034cef40f882cfe5321437b65c47631fc363ef5/README.md)、[用户名校验实现](https://github.com/Xposed-Modules-Repo/submission/blob/1034cef40f882cfe5321437b65c47631fc363ef5/src/bot.ts) 和 [现代 Xposed APK 校验实现](https://github.com/Xposed-Modules-Repo/modules/blob/ab45ed4ea7516ef3414f3a47cafeac72680f86de/src/index.ts)。

| 项目 | 当前情况 |
| --- | --- |
| 申请者与包名 | **不匹配**：`chorusfruit-233` 经机器人规范化后是 `chorusfruit_233`，要求前缀 `io.github.chorusfruit_233.`；当前为 `io.github.chorusfruit233.colorduo` |
| Xposed APK 结构 | 具有 `META-INF/xposed/module.prop`、`java_init.list`、`scope.list`，API 102；模块仓支持这种现代格式 |
| 模块介绍与摘要 | 已有 `module-README.md` 和 `module-SUMMARY`，复制到模块仓后对应 `README.md`、`SUMMARY`；介绍中的链接使用源码仓库绝对地址 |
| 可公开下载的版本 | 当前只有 Actions 测试包，没有公开发布的 ColorDuo Release APK；现有 SystemUI 草稿是适配参考文件 |
| 发布签名 | 测试包使用公开开发签名；正式发布需配置并验证上述四项 secrets。私有签名是本项目发布流程的要求，submission 机器人并未规定这一条件 |
| 模块仓申请与设置 | 本次查询未找到 ColorDuo 的申请和模块仓；仍需申请、接受邀请，填写仓库 Description（模块名）和 Homepage（支持链接） |

**结论：当前包名不满足自动审核条件，尚不具备直接上架并展示的完整条件。** 本次保留应用 ID；改包名会成为独立安装的新应用，已有配置不会自动迁移。

## 申请与上架顺序

1. 确定应用 ID。若由 `chorusfruit-233` 账户走自动审核，使用 `io.github.chorusfruit_233.colorduo`；保留现名则需另行人工申诉，不能保证获批。
2. 在 [submission](https://github.com/Xposed-Modules-Repo/submission) 新建 Issue，标题为 `[submission] <最终 applicationId>`，然后接受机器人发出的模块仓维护邀请。
3. 在浏览器为模块仓设置 Description 为 `ColorDuo`，Homepage 指向源码仓库或 Issues。官方提示维护权限不足以通过 API 修改这些设置。
4. 只上传模块说明与摘要，不把源码推入模块仓。将 `module-README.md` 作为 `README.md`、`module-SUMMARY` 作为 `SUMMARY`。
5. 将同一签名 APK 发布到模块仓：Release 标题为版本名、正文为该版更新记录、tag 为 `<versionCode>-<versionName>`，例如 `10005-0.1.0-alpha5`。首次发布时同时附上有效 APK。

官方说明完整资料及 Release 更新后通常需要 5～60 分钟显示。最终是否收录以模块仓机器人及维护者处理结果为准。
