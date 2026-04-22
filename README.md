<div align="center">
  <a href="https://play.google.com/store/apps/details?id=com.arslan.shizuwall">
    <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png" alt="ShizuWall Icon" width="72" />
  </a>
  <h1>ShizuWall</h1>
  <strong>Android firewall without VPN.</strong><br/>
  Privacy-first, local-only, powered by Shizuku / local ADB daemon / Root.
</div>
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
  
</p>

<p align="center">
  <a href="https://www.buymeacoffee.com/ahmetcanarslan">
    <img src="https://cdn.buymeacoffee.com/buttons/v2/default-yellow.png" alt="Buy Me A Coffee" width="220" />
  </a>
</p>

## Star History

<a href="https://www.star-history.com/?repos=AhmetCanArslan%2FShizuWall&type=date&legend=top-left">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/image?repos=AhmetCanArslan/ShizuWall&type=date&theme=dark&legend=top-left" />
    <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/image?repos=AhmetCanArslan/ShizuWall&type=date&legend=top-left" />
    <img alt="Star History Chart" src="https://api.star-history.com/image?repos=AhmetCanArslan/ShizuWall&type=date&legend=top-left" />
  </picture>
</a>

## Why ShizuWall

- **No VPN**: avoids packet interception and persistent VPN tunnel side effects.
- **Per-app network control**: toggles app networking through Android's `connectivity` chain-3 controls.
- **Privacy-first by design**: offline-first, no analytics, no telemetry, no tracking.
- **Automation ready**: supports `adb broadcast` commands for scripts and task automation.
- **Control Methods**: ShizuWall provides three convenient ways (Quick Settings Toggle, App Widget, Floating Firewall Button) to control the firewall.

## Screenshots

<p align="center">
  <img src="assets/screenShots/v4.4/1.png" width="30%" />
  <img src="assets/screenShots/v4.4/2.png" width="30%" />
  <img src="assets/screenShots/v4.4/3.png" width="30%" />
  <img src="assets/screenShots/v4.4/4.png" width="30%" />
  <img src="assets/screenShots/v4.4/5.png" width="30%" />
  <img src="assets/screenShots/v4.4/6.png" width="30%" />
  <img src="assets/screenShots/v4.4/7.png" width="30%" />
  <img src="assets/screenShots/v4.4/8.png" width="30%" />
</p>

## Requirements

- Android 11 (API 30) or higher
- One control backend: Shizuku, local ADB daemon or root access

## Control Backends

ShizuWall supports three methods to execute firewall commands:

| Method | Description | Setup |
|--------|---------|---------|
| **Shizuku** | Secure API that communicates with system services. Requires Shizuku app. Forks are supported. | Install and setup Shizuku app, grant permissions |
| **Root** | Direct root access. | Root your device using standard methods |
| **LibADB (LADB)** | Uses the built-in "Wireless Debugging" feature of your phone to act like a computer connected via USB. This allows the app to perform advanced system changes without needing a computer, root or extra apps like Shizuku. | Enable wireless debugging and pair in Developer Options (Guide is in app) |

## How It Works

ShizuWall uses Android's **Chain 3** (connectivity chain) to control per-app networking. These are the platform commands executed through Shizuku or the local daemon:

### ADB Chain 3 Commands

```bash
# Enable firewall framework
cmd connectivity set-chain3-enabled true

# Block specific app
cmd connectivity set-package-networking-enabled false <package.name>

# Unblock specific app
cmd connectivity set-package-networking-enabled true <package.name>

# Disable firewall framework
cmd connectivity set-chain3-enabled false
```

**Chain 3** is an Android platform mechanism that intercepts and controls per-package network access at the system level, allowing fine-grained firewall control.

## Automation (ADB Broadcast)

You can control ShizuWall from scripts and automation tools.

**Action**: `shizuwall.CONTROL`  
**Component**: `com.arslan.shizuwall/.receivers.FirewallControlReceiver`

**Extras**

- `state` (boolean, required): `true` = enable, `false` = disable
- `apps` (string, optional): CSV package list. If omitted, ShizuWall uses saved selected apps.

### Examples

```bash
# Enable firewall for saved selected apps
adb shell am broadcast -a shizuwall.CONTROL -n com.arslan.shizuwall/.receivers.FirewallControlReceiver --ez state true

# Disable firewall for saved selected apps
adb shell am broadcast -a shizuwall.CONTROL -n com.arslan.shizuwall/.receivers.FirewallControlReceiver --ez state false

# Enable firewall for specific packages
adb shell am broadcast -a shizuwall.CONTROL -n com.arslan.shizuwall/.receivers.FirewallControlReceiver --ez state true --es apps "com.example.app1,com.example.app2"

# Disable firewall for specific packages
adb shell am broadcast -a shizuwall.CONTROL -n com.arslan.shizuwall/.receivers.FirewallControlReceiver --ez state false --es apps "com.example.app1,com.example.app2"
```

> One of the control backends (Shizuku, local ADB daemon or Root) must be active for broadcasts to succeed.

## Notes & Limitations

- Firewall rules are cleared on reboot by Android platform behavior.
- Rebooting the device resets any active ShizuWall-applied network blocks.
- The app requests `android.permission.INTERNET` only for wireless debugging pairing (LibADB local daemon connection).

## Build (Developers)

### App

```bash
./gradlew assembleRelease
```

### Daemon

The on-device daemon is compiled to `app/src/main/assets/daemon.bin`.

**Prerequisites**

- Android SDK
- Java 11
- `d8` (Android Build Tools)

**Compile**

1. Open [scripts/compile_daemon.sh](scripts/compile_daemon.sh).
2. Update `SDK_PATH`, `BUILD_TOOLS_VER`, and `PLATFORM_VER` for your environment.
3. Run:

```bash
chmod +x scripts/compile_daemon.sh
./scripts/compile_daemon.sh
```

## Security Disclaimer

ShizuWall is provided **"as is"** without warranty of any kind.

By using this app, you acknowledge that it relies on advanced system permissions (Shizuku/ADB/Root), and you accept all related risks. The developer is not responsible for damages such as system instability, data loss, service interruption, or side effects from blocked networking.

Always verify which apps you block.

## License

Licensed under **GNU General Public License v3.0 (GPLv3)**. See [LICENSE.md](LICENSE.md).

## Support

- ⭐ Star the project: [GitHub Stars](https://github.com/AhmetCanArslan/ShizuWall/stargazers)
- ☕ Donate: [Buy Me a Coffee](https://buymeacoffee.com/ahmetcanarslan)
- ⬇️ Download: [Google Play Store](https://play.google.com/store/apps/details?id=com.arslan.shizuwall)

## Credits

- [Shizuku](https://github.com/RikkaApps/Shizuku) — API that enables privileged command execution flow.
- [LibADB](https://github.com/MuntashirAkon/libadb-android) — Wireless debugging and daemon connection support.
