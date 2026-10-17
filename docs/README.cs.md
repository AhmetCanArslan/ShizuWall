<div align="center">
  <a href="https://play.google.com/store/apps/details?id=com.arslan.shizuwall">
    <img src="../app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png" alt="ShizuWall Icon" width="72" />
  </a>
  <h1>ShizuWall</h1>
  <strong>Firewall pro Android bez VPN.</strong><br/>
  Soukromí na prvním místě, pouze lokálně, poháněno Shizuku / lokálním ADB démonem / rootem.
</div>

<p align="center">
  <a href="../README.md">English</a> ·
  <a href="README.tr.md">Türkçe</a> ·
  <a href="README.de.md">Deutsch</a> ·
  <a href="README.it.md">Italiano</a> ·
  <a href="README.pt.md">Português</a> ·
  <b>Čeština</b> ·
  <a href="README.ru.md">Русский</a> ·
  <a href="README.ar.md">العربية</a> ·
  <a href="README.hi.md">हिन्दी</a> ·
  <a href="README.zh.md">中文</a> ·
  <a href="README.ja.md">日本語</a>
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


## Funguje s

Android 11+ a jeden z těchto backendů:

- **Shizuku** (forky jsou podporovány)
- **LibADB**: páruje se přes bezdrátové ladění, bez počítače
- **Root**

## Proč ShizuWall

- **Bez VPN**: žádné zachytávání paketů, žádný trvalý tunel.
- **Řízení po aplikacích**: blokuje síť jednotlivým aplikacím včetně kopií v pracovním profilu a klonů.
- **Soukromé**: offline-first, žádná analytika, žádná telemetrie.
- **Rychlý přístup**: dlaždice Rychlého nastavení, widget a plovoucí tlačítko.
- **Automatizace**: ovladatelné broadcasty z adb, Taskeru, MacroDroidu a podobných nástrojů.

## Snímek obrazovky

<p align="center">
  <img src="../assets/screenshots/v4.6/1.png" alt="ShizuWall" width="30%" />
</p>

## Jak to funguje

ShizuWall ovládá androidí connectivity **chain 3**, mechanismus platformy, který řídí přístup k síti po balíčcích:

```bash
cmd connectivity set-chain3-enabled true                                # firewall zapnut
cmd connectivity set-package-networking-enabled false <package.name>   # blokovat
cmd connectivity set-package-networking-enabled true <package.name>    # odblokovat
cmd connectivity set-chain3-enabled false                               # firewall vypnut
```

Android tato pravidla při restartu smaže.

## Automatizace

Každý broadcast vyžaduje aktivní backend. `apps` je seznam klíčů aplikací oddělených čárkou: samotný název balíčku míří na uživatele 0, klíč s prefixem jako `150:com.example.app` na kopii v daném pracovním profilu nebo klonovaném prostoru.

### Firewall: `shizuwall.CONTROL`

| Extra | Typ | |
|---|---|---|
| `state` | boolean, povinné | `true` = blokovat, `false` = odblokovat |
| `apps` | string, volitelné | Aplikace k zablokování nebo odblokování. Bez něj broadcast přepíná celý firewall. |

```bash
# Zapnout firewall (state false ho vypne)
adb shell am broadcast -a shizuwall.CONTROL -n com.arslan.shizuwall/.receivers.FirewallControlReceiver --ez state true

# Zablokovat aplikaci i její kopii v pracovním profilu
adb shell am broadcast -a shizuwall.CONTROL -n com.arslan.shizuwall/.receivers.FirewallControlReceiver --ez state true --es apps "com.example.app,150:com.example.app"
```

- S `apps` se přizpůsobí i výběr v aplikaci: `state true` aplikace vybere a zapne firewall, pokud byl vypnutý; `state false` je z výběru odebere a firewall nechá zapnutý. V režimu whitelistu `apps` míří na seznam povolených, takže jsou oba stavy obrácené.
- Je-li **Zobrazit aplikace z jiných profilů** vypnuto, platí samotný název balíčku i pro jeho klony. Je-li zapnuto, každý profil se adresuje jen svým prefixem.

### Nová aplikace: `shizuwall.APP_INSTALLED`

Oznámí nově nainstalovanou aplikaci, takže se na ni použijí pravidla i oznámení pro nové aplikace, jako by instalaci zaznamenal sám ShizuWall. ShizuWall vidí jen instalace ve vlastním profilu; takto lze pokrýt i ostatní.

```bash
adb shell am broadcast -a shizuwall.APP_INSTALLED -n com.arslan.shizuwall/.receivers.AppInstalledReceiver --es apps "com.example.newapp"
```

Když je firewall vypnutý, nepoužije se žádné pravidlo. Aplikace bez oprávnění `INTERNET` se ignorují.

### Profil: `shizuwall.PROFILE`

Aktivuje uložený profil: zapíše jeho výběr aplikací, režim firewallu, režimy jednotlivých aplikací a nastavení *Zobrazit systémové aplikace*.

| Extra | Typ | |
|---|---|---|
| `profile` | string | Název profilu, jak je uveden v seznamu profilů. |
| `profile_id` | string | Interní ID; kontroluje se jako první a přejmenování ho neovlivní. Dialog **Automatizace** u profilu ukazuje hotový příkaz. |
| `force_enable` | boolean, volitelné | Po přepnutí zapne firewall, i když byl vypnutý. |

Je vyžadováno `profile` nebo `profile_id`. Pokud žádné neodpovídá, nic se nezmění a oznámí to toast.

```bash
adb shell am broadcast -a shizuwall.PROFILE -n com.arslan.shizuwall/.receivers.ProfileControlReceiver --es profile "Work"
```

## Prohlášení o odpovědnosti

ShizuWall je poskytován **„tak, jak je“**, bez záruky. Využívá pokročilá systémová oprávnění a vývojář neodpovídá za nestabilitu systému, ztrátu dat ani vedlejší účinky blokování sítě. Vždy si ověřte, které aplikace blokujete.

**Oficiální zdroje jsou pouze Google Play, F-Droid a tento repozitář.** APK odjinud nejsou s projektem spojeny a používáte je na vlastní riziko.

Oprávnění `INTERNET` se vyžaduje jen pro párování bezdrátového ladění (LibADB).

## Licence

[GPLv3](../LICENSE.md). Přibalená data signatur trackerů jsou licencována samostatně pod ODbL v1.0, viz [TRACKER_DATA_LICENSE.md](../TRACKER_DATA_LICENSE.md).

## Poděkování

- [Shizuku](https://github.com/RikkaApps/Shizuku): spouštění privilegovaných příkazů.
- [LibADB](https://github.com/MuntashirAkon/libadb-android): bezdrátové ladění a připojení k démonu.
- [Exodus Privacy](https://exodus-privacy.eu.org/): databáze signatur trackerů. ShizuWall obsahuje její zkrácenou kopii a nikdy nekontaktuje jejich API.
