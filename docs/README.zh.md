<div align="center">
  <a href="https://play.google.com/store/apps/details?id=com.arslan.shizuwall">
    <img src="../app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png" alt="ShizuWall Icon" width="72" />
  </a>
  <h1>ShizuWall</h1>
  <strong>无需 VPN 的 Android 防火墙。</strong><br/>
  隐私优先、纯本地运行，基于 Shizuku / 本地 ADB 守护进程 / Root。
</div>

<p align="center">
  <a href="../README.md">English</a> ·
  <a href="README.tr.md">Türkçe</a> ·
  <a href="README.de.md">Deutsch</a> ·
  <a href="README.it.md">Italiano</a> ·
  <a href="README.pt.md">Português</a> ·
  <a href="README.cs.md">Čeština</a> ·
  <a href="README.ru.md">Русский</a> ·
  <a href="README.ar.md">العربية</a> ·
  <a href="README.hi.md">हिन्दी</a> ·
  <b>中文</b> ·
  <a href="README.ja.md">日本語</a>
</p>
<div style="height: 20px;">&nbsp;</div>
<p align="center">
  <a href="https://tooomm.github.io/github-release-stats/?username=AhmetCanArslan&repository=ShizuWall">
    <img alt="Downloads (GitHub)" src="https://img.shields.io/github/downloads/AhmetCanArslan/ShizuWall/total?color=ff9500&label=Total%20Downloads%20(GitHub)&logo=github&logoColor=white&style=flat-square" />
  </a>
  <img alt="Downloads (F-Droid)" src="https://img.shields.io/badge/dynamic/json?url=https%3A%2F%2Fgithub.com%2Fkitswas%2Ffdroid-metrics-dashboard%2Fraw%2Frefs%2Fheads%2Fmain%2Fprocessed%2Ftotal%2Fcom.arslan.shizuwall.json&query=%24.total_downloads&logo=fdroid&logoColor=white&label=Total%20Downloads%20(F-Droid)&style=flat-square" />
  <img alt="Repo size" src="https://img.shields.io/github/repo-size/AhmetCanArslan/ShizuWall?style=flat-square" />
  <img alt="Minimum SDK" src="https://img.shields.io/badge/Minimum%20SDK-30%20(Android%2011)-839192?style=flat-square&logo=android&logoColor=white" />
</p>

<p align="center">
  <a href="https://play.google.com/store/apps/details?id=com.arslan.shizuwall">
    <img alt="Get it on Google Play" src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" width="250" />
  </a>
  <a href="https://f-droid.org/packages/com.arslan.shizuwall/">
    <img alt="Get it on F-Droid" src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" width="250" />
  </a>
</p>

<p align="center">
  <a href="https://www.buymeacoffee.com/ahmetcanarslan">
    <img src="https://cdn.buymeacoffee.com/buttons/v2/default-yellow.png" alt="Buy Me A Coffee" width="220" />
  </a>
</p>


## 支持环境

Android 11+ 以及以下任一后端：

- **Shizuku**（支持分支版本）
- **LibADB**：通过无线调试配对，无需电脑
- **Root**

## 为什么选择 ShizuWall

- **无需 VPN**：不拦截数据包，没有常驻隧道。
- **按应用控制**：逐个应用封锁网络，包括工作资料和分身中的副本。
- **隐私**：离线优先，无分析，无遥测。
- **快捷入口**：快速设置磁贴、小部件和悬浮按钮。
- **自动化**：可通过 adb、Tasker、MacroDroid 等发送的广播进行控制。

## 截图

<p align="center">
  <img src="../assets/screenshots/v4.6/1.png" alt="ShizuWall" width="30%" />
</p>

## 工作原理

ShizuWall 使用 Android connectivity 的 **chain 3**，这是一种按软件包控制网络访问的平台机制：

```bash
cmd connectivity set-chain3-enabled true                                # 开启防火墙
cmd connectivity set-package-networking-enabled false <package.name>   # 封锁
cmd connectivity set-package-networking-enabled true <package.name>    # 解除封锁
cmd connectivity set-chain3-enabled false                               # 关闭防火墙
```

Android 会在重启时清除这些规则。

## 自动化

每条广播都需要有一个处于活动状态的后端。`apps` 是以逗号分隔的应用键列表：纯包名指向用户 0，带前缀的键（如 `150:com.example.app`）指向该工作资料或分身空间中的副本。

### 防火墙: `shizuwall.CONTROL`

| 参数 | 类型 | |
|---|---|---|
| `state` | boolean，必填 | `true` = 封锁，`false` = 解除封锁 |
| `apps` | string，可选 | 要封锁或解除封锁的应用。省略时广播会开关整个防火墙。 |

```bash
# 开启防火墙（state false 为关闭）
adb shell am broadcast -a shizuwall.CONTROL -n com.arslan.shizuwall/.receivers.FirewallControlReceiver --ez state true

# 封锁某个应用及其工作资料中的副本
adb shell am broadcast -a shizuwall.CONTROL -n com.arslan.shizuwall/.receivers.FirewallControlReceiver --ez state true --es apps "com.example.app,150:com.example.app"
```

- 带 `apps` 时，应用内的选择会同步：`state true` 选中这些应用，并在防火墙关闭时将其开启；`state false` 取消选中，防火墙保持开启。在白名单模式下 `apps` 指向允许列表，因此两种状态相反。
- 关闭 **显示其他配置文件中的应用** 时，纯包名也会作用于它的分身。开启时，每个资料只能通过各自的前缀指定。

### 新应用: `shizuwall.APP_INSTALLED`

上报新安装的应用，使新应用策略和通知像 ShizuWall 自己检测到安装一样生效。ShizuWall 只能看到自身所在资料中的安装，其他资料需通过这种方式覆盖。

```bash
adb shell am broadcast -a shizuwall.APP_INSTALLED -n com.arslan.shizuwall/.receivers.AppInstalledReceiver --es apps "com.example.newapp"
```

防火墙关闭时不会应用任何规则。没有 `INTERNET` 权限的应用会被忽略。

### 配置文件: `shizuwall.PROFILE`

启用已保存的配置：写入其应用选择、防火墙模式、各应用模式以及 *显示系统应用* 设置。

| 参数 | 类型 | |
|---|---|---|
| `profile` | string | 配置名称，与配置列表中显示的一致。 |
| `profile_id` | string | 内部 id；优先检查，不受重命名影响。该配置的 **自动化** 对话框会显示现成的命令。 |
| `force_enable` | boolean，可选 | 切换后开启防火墙，即使之前是关闭的。 |

`profile` 和 `profile_id` 必须提供其一。若都不匹配，则不做任何更改，并通过 toast 提示。

```bash
adb shell am broadcast -a shizuwall.PROFILE -n com.arslan.shizuwall/.receivers.ProfileControlReceiver --es profile "Work"
```

## 免责声明

ShizuWall 按**“原样”**提供，不附带任何保证。它依赖高级系统权限，开发者不对系统不稳定、数据丢失或网络屏蔽的副作用负责。请务必确认你屏蔽了哪些应用。

**官方来源仅为 Google Play、F-Droid 和本仓库。** 其他来源的 APK 与本项目无关，使用风险自负。

`INTERNET` 权限仅用于无线调试配对（LibADB）。

## 许可证

[GPLv3](../LICENSE.md)。随附的追踪器特征数据单独以 ODbL v1.0 授权，参见 [TRACKER_DATA_LICENSE.md](../TRACKER_DATA_LICENSE.md)。

## 致谢

- [Shizuku](https://github.com/RikkaApps/Shizuku): 执行特权命令。
- [LibADB](https://github.com/MuntashirAkon/libadb-android): 无线调试与守护进程连接。
- [Exodus Privacy](https://exodus-privacy.eu.org/): 追踪器特征数据库。ShizuWall 内置其精简副本，绝不访问他们的 API。
