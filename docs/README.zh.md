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
  <img alt="Last commit" src="https://img.shields.io/github/last-commit/AhmetCanArslan/ShizuWall?style=flat-square" />
  <img alt="Repo size" src="https://img.shields.io/github/repo-size/AhmetCanArslan/ShizuWall?style=flat-square" />
  <img alt="License" src="https://img.shields.io/github/license/AhmetCanArslan/ShizuWall?style=flat-square" />
  <img alt="Android" src="https://img.shields.io/badge/Android-11%2B-3DDC84?style=flat-square&logo=android&logoColor=white" />
  <img alt="Downloads" src="https://img.shields.io/github/downloads/AhmetCanArslan/ShizuWall/total?color=ff9500&style=flat-square" />
  <a href="https://github.com/timschneeb/awesome-shizuku?tab=readme-ov-file#network">
    <img alt="Awesome" src="https://awesome.re/mentioned-badge-flat.svg" style="display:inline-block;" />
  </a>
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


## 为什么选择 ShizuWall

- **无需 VPN**: 避免数据包拦截以及常驻 VPN 隧道带来的副作用。
- **逐应用网络控制**: 通过 Android 的 `connectivity` chain-3 控制开关各应用的联网权限。
- **隐私优先的设计**: 离线优先，无分析、无遥测、无追踪。
- **易于自动化**: 支持 `adb broadcast` 命令，便于脚本与任务自动化。
- **控制方式**: ShizuWall 提供三种便捷的防火墙控制方式：快捷设置磁贴、桌面小部件和悬浮防火墙按钮。

## 截图

<p align="center">
  <img src="../assets/screenshots/v4.6/1.png" width="30%" />
  <img src="../assets/screenshots/v4.6/2.png" width="30%" />
  <img src="../assets/screenshots/v4.6/3.png" width="30%" />
  <img src="../assets/screenshots/v4.6/4.png" width="30%" />
  <img src="../assets/screenshots/v4.6/5.png" width="30%" />
  <img src="../assets/screenshots/v4.6/6.png" width="30%" />
  <img src="../assets/screenshots/v4.6/7.png" width="30%" />
  <img src="../assets/screenshots/v4.6/8.png" width="30%" />
  <img src="../assets/screenshots/v4.6/9.png" width="30%" />
  <img src="../assets/screenshots/v4.6/10.png" width="30%" />
  <img src="../assets/screenshots/v4.6/11.png" width="30%" />
  <img src="../assets/screenshots/v4.6/12.png" width="30%" />
  <img src="../assets/screenshots/v4.6/13.png" width="30%" />
  <img src="../assets/screenshots/v4.6/14.png" width="30%" />
  <img src="../assets/screenshots/v4.6/15.png" width="30%" />
  <img src="../assets/screenshots/v4.6/16.png" width="30%" />

</p>

## 系统要求

- Android 11（API 30）或更高版本
- 一种控制后端：Shizuku、本地 ADB 守护进程或 Root 权限

## 控制后端

ShizuWall 支持三种执行防火墙命令的方式：

| 方式 | 说明 | 设置 |
|--------|---------|---------|
| **Shizuku** | 与系统服务通信的安全 API。需要 Shizuku 应用，同时支持其各类分支版本。 | 安装并设置 Shizuku 应用，授予权限 |
| **Root** | 直接使用 Root 权限。 | 使用常规方法为设备获取 Root |
| **LibADB (LADB)** | 利用手机内置的“无线调试”功能，使其表现得像通过 USB 连接的电脑。这样应用无需电脑、Root 或 Shizuku 等额外应用即可进行高级系统更改。 | 在开发者选项中启用无线调试并完成配对（应用内附有指南） |

## 工作原理

ShizuWall 使用 Android 的 **Chain 3**（连接链）来控制各应用的联网。以下是通过 Shizuku 或本地守护进程执行的平台命令：

### ADB Chain 3 命令

```bash
# 启用防火墙框架
cmd connectivity set-chain3-enabled true

# 阻止指定应用
cmd connectivity set-package-networking-enabled false <package.name>

# 解除指定应用的阻止
cmd connectivity set-package-networking-enabled true <package.name>

# 停用防火墙框架
cmd connectivity set-chain3-enabled false
```

**Chain 3** 是 Android 平台的一种机制，可在系统层面拦截并控制各软件包的网络访问，从而实现精细的防火墙控制。

## 自动化（ADB 广播）

你可以通过脚本和自动化工具控制 ShizuWall。

**Action**: `shizuwall.CONTROL`  
**组件**: `com.arslan.shizuwall/.receivers.FirewallControlReceiver`  

**附加参数**

- `state` （布尔值，必填）：`true` = 启用，`false` = 停用
- `apps` （字符串，可选）：以逗号分隔的包名列表。若省略，ShizuWall 将使用已保存的所选应用。

### 示例

```bash
# 为已保存的所选应用启用防火墙
adb shell am broadcast -a shizuwall.CONTROL -n com.arslan.shizuwall/.receivers.FirewallControlReceiver --ez state true

# 为已保存的所选应用停用防火墙
adb shell am broadcast -a shizuwall.CONTROL -n com.arslan.shizuwall/.receivers.FirewallControlReceiver --ez state false

# 为指定包名启用防火墙
adb shell am broadcast -a shizuwall.CONTROL -n com.arslan.shizuwall/.receivers.FirewallControlReceiver --ez state true --es apps "com.example.app1,com.example.app2"

# 为指定包名停用防火墙
adb shell am broadcast -a shizuwall.CONTROL -n com.arslan.shizuwall/.receivers.FirewallControlReceiver --ez state false --es apps "com.example.app1,com.example.app2"
```

> 广播要生效，必须有一种控制后端（Shizuku、本地 ADB 守护进程或 Root）处于活动状态。

## 说明与限制

- 由于 Android 平台的行为，防火墙规则会在重启后被清除。
- 重启设备会重置 ShizuWall 已应用的所有网络阻止。
- 应用仅为无线调试配对（LibADB 本地守护进程连接）请求 `android.permission.INTERNET` 权限。

## 构建（开发者）

### 应用

```bash
./gradlew assembleRelease
```

### 守护进程

设备端守护进程（`SystemDaemon.java`）会在常规 Gradle 构建过程中由 `compileDaemonDex` 任务自动编译为 DEX 文件（`daemon.bin`）。它使用 Android SDK build-tools 中的 `javac` 与 `d8` 从源码编译并作为资源打包——仓库中没有预编译的二进制文件，因此可复现构建服务器（如 F-Droid）会自行生成它。

无需手动步骤；`./gradlew assembleRelease` 会一并构建。该任务也可单独运行：

```bash
./gradlew :app:compileDaemonDex
```

## 安全与分发免责声明

ShizuWall 按**“原样”**提供，不作任何形式的担保。

使用本应用即表示你了解它依赖于高级系统权限（Shizuku/ADB/Root），并接受所有相关风险。对于系统不稳定、数据丢失、服务中断或网络被阻止所导致的副作用等损害，开发者概不负责。

**官方分发与第三方风险**
对于从第三方来源获取的 APK 文件在下载、安装或使用过程中造成的任何损害、安全漏洞、恶意软件感染或数据丢失，ShizuWall 的开发者与贡献者不承担任何责任。ShizuWall 仅通过 Google Play 商店、其官方 GitHub 仓库和 F-Droid 进行官方开发与分发。ShizuWall 不拥有、不运营也不认可任何官方网站。任何声称提供 ShizuWall 官方下载的网站或第三方平台均与本项目完全无关且未获授权。通过非官方渠道获取本应用的用户须自行承担全部风险。

**ShizuWall 是一款开源应用。开发者对本应用的使用不承担任何经济、道义或法律责任。所有责任由用户自行承担。**

请务必确认你阻止的是哪些应用。

## 许可证

基于 **GNU General Public License v3.0 (GPLv3)** 授权。参见 [LICENSE.md](../LICENSE.md)。

随附的追踪器特征数据不属于代码，采用单独的许可证（ODbL v1.0）。参见 [TRACKER_DATA_LICENSE.md](../TRACKER_DATA_LICENSE.md)。

## 支持

- ⭐ 为项目点星：[GitHub Stars](https://github.com/AhmetCanArslan/ShizuWall/stargazers)
- ☕ 捐赠：[Buy Me a Coffee](https://buymeacoffee.com/ahmetcanarslan)
- ⬇️ 下载：[Google Play 商店](https://play.google.com/store/apps/details?id=com.arslan.shizuwall)

## 致谢

- [Shizuku](https://github.com/RikkaApps/Shizuku) — 使特权命令执行流程成为可能的 API。
- [LibADB](https://github.com/MuntashirAkon/libadb-android) — 无线调试与守护进程连接支持。
- [Exodus Privacy](https://exodus-privacy.eu.org/) — 用于检测应用内追踪 SDK 的追踪器特征数据库，采用 [ODbL v1.0](https://opendatacommons.org/licenses/odbl/1-0/) 许可。ShizuWall 内置了精简副本，且从不访问其 API。参见 [TRACKER_DATA_LICENSE.md](../TRACKER_DATA_LICENSE.md)。
