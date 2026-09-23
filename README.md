<div align="center">
  <a href="https://play.google.com/store/apps/details?id=com.arslan.shizuwall">
    <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png" alt="ShizuWall Icon" width="72" />
  </a>
  <h1>ShizuWall</h1>
  <strong>Android firewall without VPN.</strong><br/>
  Privacy-first, local-only, powered by Shizuku / local ADB daemon / Root.
</div>

<p align="center">
  <b>English</b> ·
  <a href="docs/README.tr.md">Türkçe</a> ·
  <a href="docs/README.de.md">Deutsch</a> ·
  <a href="docs/README.it.md">Italiano</a> ·
  <a href="docs/README.pt.md">Português</a> ·
  <a href="docs/README.cs.md">Čeština</a> ·
  <a href="docs/README.ru.md">Русский</a> ·
  <a href="docs/README.ar.md">العربية</a> ·
  <a href="docs/README.hi.md">हिन्दी</a> ·
  <a href="docs/README.zh.md">中文</a> ·
  <a href="docs/README.ja.md">日本語</a>
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


## Why ShizuWall

- **No VPN**: avoids packet interception and persistent VPN tunnel side effects.
- **Per-app network control**: toggles app networking through Android's `connectivity` chain-3 controls.
- **Privacy-first by design**: offline-first, no analytics, no telemetry, no tracking.
- **Automation ready**: supports `adb broadcast` commands for scripts and task automation.
- **Control Methods**: ShizuWall provides three convenient ways (Quick Settings Toggle, App Widget, Floating Firewall Button) to control the firewall.

## Screenshots

<p align="center">
  <img src="assets/screenshots/v4.6/1.png" width="30%" />
  <img src="assets/screenshots/v4.6/2.png" width="30%" />
  <img src="assets/screenshots/v4.6/3.png" width="30%" />
  <img src="assets/screenshots/v4.6/4.png" width="30%" />
  <img src="assets/screenshots/v4.6/5.png" width="30%" />
  <img src="assets/screenshots/v4.6/6.png" width="30%" />
  <img src="assets/screenshots/v4.6/7.png" width="30%" />
  <img src="assets/screenshots/v4.6/8.png" width="30%" />
  <img src="assets/screenshots/v4.6/9.png" width="30%" />
  <img src="assets/screenshots/v4.6/10.png" width="30%" />
  <img src="assets/screenshots/v4.6/11.png" width="30%" />
  <img src="assets/screenshots/v4.6/12.png" width="30%" />
  <img src="assets/screenshots/v4.6/13.png" width="30%" />
  <img src="assets/screenshots/v4.6/14.png" width="30%" />
  <img src="assets/screenshots/v4.6/15.png" width="30%" />
  <img src="assets/screenshots/v4.6/16.png" width="30%" />

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
- `apps` (string, optional): CSV app key list. If omitted, ShizuWall uses saved selected apps. An entry may carry a profile prefix — `150:com.example.app` targets the work-profile/clone copy, a bare package name targets user 0.

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

# Enable firewall for an app in user 0 and its work-profile clone
adb shell am broadcast -a shizuwall.CONTROL -n com.arslan.shizuwall/.receivers.FirewallControlReceiver --ez state true --es apps "com.example.app,150:com.example.app"
```

> Clones are handled automatically unless **Show other profiles** is on: with that setting off, a bare package name mirrors its rule to every clone, and with it on each profile is addressed separately through its prefix.

> A broadcast carrying `apps` keeps ShizuWall in sync: `state true` selects those apps and turns the firewall on if it was off, `state false` unselects them and leaves the firewall on. In whitelist mode `apps` addresses the allow list, so the two states are reversed. Without `apps` the broadcast is a global toggle.

### New App Installed

Automation apps (MacroDroid, Tasker, etc.) can notify ShizuWall about a freshly installed package, so the new-app policy and the new-app notification are applied to it exactly as if ShizuWall's own app monitor had seen the install.

**Action**: `shizuwall.APP_INSTALLED`  
**Component**: `com.arslan.shizuwall/.receivers.AppInstalledReceiver`

**Extras**

- `apps` (string, required): CSV app key list of the newly installed apps. As with `shizuwall.CONTROL`, an entry may carry a profile prefix — `150:com.example.app` reports an install inside a work profile, clone space or private space.

```bash
adb shell am broadcast -a shizuwall.APP_INSTALLED -n com.arslan.shizuwall/.receivers.AppInstalledReceiver --es apps "com.example.newapp"

# An app installed in a work profile / clone space
adb shell am broadcast --user 0 -a shizuwall.APP_INSTALLED -n com.arslan.shizuwall/.receivers.AppInstalledReceiver --es apps "150:com.example.newapp"
```

This is the supported way to cover the other profiles: ShizuWall itself only sees `ACTION_PACKAGE_ADDED` for the profile it runs in, so an automation app that can watch the other profiles (Tasker, MacroDroid, a shell script) reports the install and ShizuWall applies the same policy and notification to it. Send it with `--user 0` when the sender runs outside the main profile.

The broadcast is handled by a manifest receiver, so it works even when the app monitor service is stopped — that service is only needed for ShizuWall's own detection of installs in its profile. The package is handled according to the active firewall mode: in `WHITELIST` it is blocked immediately, in the other modes it is blocked and added to the selected apps only when *Auto firewall new apps* is on. No rule is applied while the firewall is off.

A notification with a one-tap action (allow, firewall or add to the selected list, depending on the current state) is posted when *New app notifications* is on, or whenever the app was auto-firewalled. Apps that the main profile can resolve and that hold no `INTERNET` permission are ignored; an app that only exists in another profile is reported by its key, without a label or icon.

### Profile Switch

A saved profile can be activated from a macro app (Tasker, MacroDroid, Automate) or from adb. Activating a profile writes its app selection, firewall mode, per-app modes and *Show system apps* setting, and then re-applies the firewall.

**Action**: `shizuwall.PROFILE`  
**Component**: `com.arslan.shizuwall/.receivers.ProfileControlReceiver`

**Extras**

- `profile` (string): profile name, as shown in the profiles sheet.
- `profile_id` (string): the profile's internal id. Checked before `profile`, and unaffected by renames — the per-profile **Automation** dialog in the app shows the ready-made command for the profile you are looking at.
- `force_enable` (boolean, optional): turn the firewall on after switching even if it was off and *Turn on firewall when activating a profile* is off.

One of `profile` or `profile_id` is required; if neither resolves to a saved profile, nothing is changed and a toast reports the name that was not found.

```bash
# Switch to a profile by name
adb shell am broadcast -a shizuwall.PROFILE -n com.arslan.shizuwall/.receivers.ProfileControlReceiver --es profile "Work"

# Switch to a profile by id
adb shell am broadcast -a shizuwall.PROFILE -n com.arslan.shizuwall/.receivers.ProfileControlReceiver --es profile_id "f2c1a0e4-..."

# Switch and turn the firewall on even if it was off
adb shell am broadcast -a shizuwall.PROFILE -n com.arslan.shizuwall/.receivers.ProfileControlReceiver --es profile "Work" --ez force_enable true
```

The firewall is re-applied with the new selection when it was already on, when *Turn on firewall when activating a profile* is on, or when `force_enable` is set; otherwise only the selection is written and the widgets and tiles are refreshed. A profile carrying a firewall mode other than `DEFAULT` also turns off the enable-confirmation dialog, which only exists for `DEFAULT`.

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

The on-device daemon (`SystemDaemon.java`) is compiled to a DEX (`daemon.bin`)
automatically as part of the normal Gradle build by the `compileDaemonDex`
task. It is compiled from source using `javac` and `d8` from the Android SDK
build-tools and packaged as an asset — there is no prebuilt binary in the
repository, so reproducible build servers (e.g. F-Droid) produce it themselves.

No manual step is required; `./gradlew assembleRelease` builds it. The task can
also be run on its own:

```bash
./gradlew :app:compileDaemonDex
```

## Security and Distribution Disclaimer

ShizuWall is provided **"as is"** without warranty of any kind.

By using this app, you acknowledge that it relies on advanced system permissions (Shizuku/ADB/Root), and you accept all related risks. The developer is not responsible for damages such as system instability, data loss, service interruption or side effects from blocked networking.

**Official Distribution and Third-Party Risks**
The developers and contributors of ShizuWall assume no liability or responsibility for any damages, security breaches, malware infections or data losses arising from the download, installation, or use of APK files obtained from third-party sources. ShizuWall is officially developed and distributed exclusively through the Google Play Store, its official GitHub repository and F-Droid. ShizuWall does not own, operate or endorse any official website. Any website or third-party platform claiming to offer official ShizuWall downloads is entirely unaffiliated and unauthorized. Users obtaining the application outside of the officially designated channels do so strictly at their own risk.

**ShizuWall is an open-source application. The developer accepts no financial, moral, or legal responsibility for the use of this application. All responsibility belongs to the user.**

Always verify which apps you block.

## License

Licensed under **GNU General Public License v3.0 (GPLv3)**. See [LICENSE.md](LICENSE.md).

The bundled tracker signature data is not code and carries its own license
(ODbL v1.0). See [TRACKER_DATA_LICENSE.md](TRACKER_DATA_LICENSE.md).

## Support

- ⭐ Star the project: [GitHub Stars](https://github.com/AhmetCanArslan/ShizuWall/stargazers)
- ☕ Donate: [Buy Me a Coffee](https://buymeacoffee.com/ahmetcanarslan)
- ⬇️ Download: [Google Play Store](https://play.google.com/store/apps/details?id=com.arslan.shizuwall)

## Credits

- [Shizuku](https://github.com/RikkaApps/Shizuku) — API that enables privileged command execution flow.
- [LibADB](https://github.com/MuntashirAkon/libadb-android) — Wireless debugging and daemon connection support.
- [Exodus Privacy](https://exodus-privacy.eu.org/) — Tracker signature database used to detect tracker SDKs inside apps, licensed under [ODbL v1.0](https://opendatacommons.org/licenses/odbl/1-0/). ShizuWall bundles a trimmed copy and never contacts their API. See [TRACKER_DATA_LICENSE.md](TRACKER_DATA_LICENSE.md).
