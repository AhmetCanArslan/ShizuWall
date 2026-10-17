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
  <a href="https://tooomm.github.io/github-release-stats/?username=AhmetCanArslan&repository=ShizuWall">
    <img alt="Downloads (GitHub)" src="https://img.shields.io/github/downloads/AhmetCanArslan/ShizuWall/total?color=ff9500&label=Total%20Downloads%20(GitHub)&logo=github&logoColor=white&style=flat-square" />
  </a>
  <img alt="Downloads (F-Droid)" src="https://img.shields.io/badge/dynamic/json?url=https%3A%2F%2Fgithub.com%2Fkitswas%2Ffdroid-metrics-dashboard%2Fraw%2Frefs%2Fheads%2Fmain%2Fprocessed%2Ftotal%2Fcom.arslan.shizuwall.json&query=%24.total_downloads&logo=fdroid&logoColor=white&label=Total%20Downloads%20(F-Droid)&style=flat-square" />
  <img alt="Last commit" src="https://img.shields.io/github/last-commit/AhmetCanArslan/ShizuWall?style=flat-square" />
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


## Works with

Android 11+ and one of these backends:

- **Shizuku** (forks are supported)
- **LibADB**: pairs over Wireless Debugging, no computer needed
- **Root**

## Why ShizuWall

- **No VPN**: no packet interception, no persistent tunnel.
- **Per-app control**: blocks networking per app, including work-profile and clone copies.
- **Private**: offline-first, no analytics, no telemetry.
- **Quick access**: Quick Settings tile, widget and floating button.
- **Automation**: controllable through broadcasts from adb, Tasker, MacroDroid and similar.

## Screenshot

<p align="center">
  <img src="assets/screenshots/v4.6/1.png" alt="ShizuWall main screen" width="30%" />
</p>

## How It Works

ShizuWall drives Android's connectivity **chain 3**, a platform mechanism that controls network access per package:

```bash
cmd connectivity set-chain3-enabled true                                # firewall on
cmd connectivity set-package-networking-enabled false <package.name>   # block
cmd connectivity set-package-networking-enabled true <package.name>    # unblock
cmd connectivity set-chain3-enabled false                               # firewall off
```

Android clears these rules on reboot.

## Automation

Every broadcast needs an active backend. `apps` is a CSV list of app keys: a bare package name targets user 0, a prefixed one such as `150:com.example.app` targets the copy in that work profile or clone space.

### Firewall: `shizuwall.CONTROL`

| Extra | Type | |
|---|---|---|
| `state` | boolean, required | `true` = block, `false` = unblock |
| `apps` | string, optional | Apps to block or unblock. Without it the broadcast toggles the whole firewall. |

```bash
# Firewall on (state false turns it off)
adb shell am broadcast -a shizuwall.CONTROL -n com.arslan.shizuwall/.receivers.FirewallControlReceiver --ez state true

# Block an app and its work-profile copy (state false unblocks them)
adb shell am broadcast -a shizuwall.CONTROL -n com.arslan.shizuwall/.receivers.FirewallControlReceiver --ez state true --es apps "com.example.app,150:com.example.app"
```

With `apps`, the selection in the app follows: `state true` selects the apps and turns the firewall on if it was off, `state false` unselects them and leaves the firewall on. In whitelist mode `apps` addresses the allow list, so the two states are reversed.

### New app: `shizuwall.APP_INSTALLED`

Reports a newly installed app, so the new-app policy and notification apply to it as if ShizuWall had detected the install itself. ShizuWall only sees installs in its own profile, so this is how to cover the others.

```bash
adb shell am broadcast -a shizuwall.APP_INSTALLED -n com.arslan.shizuwall/.receivers.AppInstalledReceiver --es apps "com.example.newapp"
```

No rule is applied while the firewall is off. Apps without the `INTERNET` permission are ignored.

### Profile: `shizuwall.PROFILE`

Activates a saved profile: its app selection, firewall mode, per-app modes and *Show system apps* setting.

| Extra | Type | |
|---|---|---|
| `profile` | string | Profile name, as shown in the profiles sheet. |
| `profile_id` | string | Internal id; checked first and unaffected by renames. The profile's **Automation** dialog shows the ready-made command. |
| `force_enable` | boolean, optional | Turn the firewall on after switching even if it was off. |

One of `profile` or `profile_id` is required. If neither matches, nothing changes and a toast reports it.

```bash
adb shell am broadcast -a shizuwall.PROFILE -n com.arslan.shizuwall/.receivers.ProfileControlReceiver --es profile "Work"
```

## Disclaimer

ShizuWall is provided **"as is"**, without warranty. It relies on advanced system permissions, and the developer is not responsible for system instability, data loss or side effects of blocked networking. Always verify which apps you block.

**Official sources are Google Play, F-Droid and this repository only.** APKs from anywhere else are unaffiliated and used at your own risk.

`INTERNET` is requested only for wireless debugging pairing (LibADB).

## License

[GPLv3](LICENSE.md). The bundled tracker signature data is licensed separately under ODbL v1.0, see [TRACKER_DATA_LICENSE.md](TRACKER_DATA_LICENSE.md).

## Credits

- [Shizuku](https://github.com/RikkaApps/Shizuku): privileged command execution.
- [LibADB](https://github.com/MuntashirAkon/libadb-android): wireless debugging and daemon connection.
- [Exodus Privacy](https://exodus-privacy.eu.org/): tracker signature database. ShizuWall bundles a trimmed copy and never contacts their API.
