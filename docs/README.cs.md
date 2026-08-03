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


## Proč ShizuWall

- **Žádná VPN**: Vyhýbá se zachytávání paketů a vedlejším účinkům trvalého VPN tunelu.
- **Řízení sítě pro jednotlivé aplikace**: Zapíná a vypíná síť aplikací pomocí ovládání chain-3 v `connectivity` Androidu.
- **Soukromí už v návrhu**: Primárně offline, žádná analytika, žádná telemetrie, žádné sledování.
- **Připraveno na automatizaci**: Podporuje příkazy `adb broadcast` pro skripty a automatizaci úloh.
- **Způsoby ovládání**: ShizuWall nabízí tři pohodlné způsoby ovládání firewallu: dlaždici v Rychlém nastavení, widget a plovoucí tlačítko firewallu.

## Snímky obrazovky

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

## Požadavky

- Android 11 (API 30) nebo novější
- Jedno ovládací rozhraní: Shizuku, lokální ADB démon nebo root přístup

## Ovládací rozhraní

ShizuWall podporuje tři způsoby spouštění příkazů firewallu:

| Metoda | Popis | Nastavení |
|--------|---------|---------|
| **Shizuku** | Bezpečné API komunikující se systémovými službami. Vyžaduje aplikaci Shizuku. Forky jsou podporovány. | Nainstalujte a nastavte aplikaci Shizuku, udělte oprávnění |
| **Root** | Přímý root přístup. | Rootněte zařízení standardními postupy |
| **LibADB (LADB)** | Využívá vestavěnou funkci „Bezdrátové ladění“ vašeho telefonu, takže se chová jako počítač připojený přes USB. Aplikace tak může provádět pokročilé systémové změny bez počítače, rootu i bez dalších aplikací jako Shizuku. | Zapněte bezdrátové ladění a spárujte je v možnostech pro vývojáře (návod je v aplikaci) |

## Jak to funguje

ShizuWall používá **Chain 3** Androidu (řetězec konektivity) k řízení sítě jednotlivých aplikací. Toto jsou systémové příkazy spouštěné přes Shizuku nebo lokálního démona:

### Příkazy ADB Chain 3

```bash
# Zapnout rámec firewallu
cmd connectivity set-chain3-enabled true

# Zablokovat konkrétní aplikaci
cmd connectivity set-package-networking-enabled false <package.name>

# Odblokovat konkrétní aplikaci
cmd connectivity set-package-networking-enabled true <package.name>

# Vypnout rámec firewallu
cmd connectivity set-chain3-enabled false
```

**Chain 3** je mechanismus platformy Android, který na systémové úrovni zachytává a řídí síťový přístup jednotlivých balíčků, což umožňuje jemné řízení firewallu.

## Automatizace (ADB broadcast)

ShizuWall můžete ovládat ze skriptů a automatizačních nástrojů.

**Akce**: `shizuwall.CONTROL`  
**Komponenta**: `com.arslan.shizuwall/.receivers.FirewallControlReceiver`  

**Extras**

- `state` (boolean, povinné): `true` = zapnout, `false` = vypnout
- `apps` (řetězec, volitelné): seznam balíčků oddělených čárkou. Pokud chybí, ShizuWall použije uložené vybrané aplikace.

### Příklady

```bash
# Zapnout firewall pro uložené vybrané aplikace
adb shell am broadcast -a shizuwall.CONTROL -n com.arslan.shizuwall/.receivers.FirewallControlReceiver --ez state true

# Vypnout firewall pro uložené vybrané aplikace
adb shell am broadcast -a shizuwall.CONTROL -n com.arslan.shizuwall/.receivers.FirewallControlReceiver --ez state false

# Zapnout firewall pro konkrétní balíčky
adb shell am broadcast -a shizuwall.CONTROL -n com.arslan.shizuwall/.receivers.FirewallControlReceiver --ez state true --es apps "com.example.app1,com.example.app2"

# Vypnout firewall pro konkrétní balíčky
adb shell am broadcast -a shizuwall.CONTROL -n com.arslan.shizuwall/.receivers.FirewallControlReceiver --ez state false --es apps "com.example.app1,com.example.app2"
```

> Aby broadcasty fungovaly, musí být aktivní jedno z ovládacích rozhraní (Shizuku, lokální ADB démon nebo root).

## Poznámky a omezení

- Pravidla firewallu se při restartu mažou kvůli chování platformy Android.
- Restart zařízení zruší veškeré aktivní blokace sítě nastavené ShizuWallem.
- Aplikace žádá o `android.permission.INTERNET` pouze kvůli párování bezdrátového ladění (připojení k lokálnímu démonu LibADB).

## Sestavení (pro vývojáře)

### Aplikace

```bash
./gradlew assembleRelease
```

### Démon

Démon běžící v zařízení (`SystemDaemon.java`) se automaticky kompiluje do DEX souboru (`daemon.bin`) jako součást běžného sestavení Gradle úlohou `compileDaemonDex`. Je kompilován ze zdrojového kódu pomocí `javac` a `d8` z build-tools Android SDK a zabalen jako asset — v repozitáři není žádný předkompilovaný binární soubor, takže servery pro reprodukovatelná sestavení (např. F-Droid) si jej vytvoří samy.

Není nutný žádný ruční krok; `./gradlew assembleRelease` jej sestaví. Úlohu lze spustit i samostatně:

```bash
./gradlew :app:compileDaemonDex
```

## Prohlášení o bezpečnosti a distribuci

ShizuWall je poskytován **„tak, jak je“**, bez jakékoli záruky.

Používáním této aplikace berete na vědomí, že se opírá o pokročilá systémová oprávnění (Shizuku/ADB/root), a přijímáte veškerá související rizika. Vývojář neodpovídá za škody, jako je nestabilita systému, ztráta dat, přerušení služeb nebo vedlejší účinky zablokované sítě.

**Oficiální distribuce a rizika třetích stran**
Vývojáři a přispěvatelé ShizuWallu nenesou žádnou odpovědnost za škody, bezpečnostní incidenty, nákazu malwarem nebo ztrátu dat vzniklé stažením, instalací či používáním souborů APK získaných ze zdrojů třetích stran. ShizuWall je oficiálně vyvíjen a distribuován výhradně prostřednictvím Google Play, svého oficiálního repozitáře na GitHubu a F-Droidu. ShizuWall nevlastní, neprovozuje ani nepodporuje žádné oficiální webové stránky. Jakákoli webová stránka nebo platforma třetí strany tvrdící, že nabízí oficiální stažení ShizuWallu, je zcela nesouvisející a neautorizovaná. Uživatelé, kteří aplikaci získají mimo oficiální kanály, tak činí výhradně na vlastní riziko.

**ShizuWall je aplikace s otevřeným zdrojovým kódem. Vývojář nepřijímá žádnou finanční, morální ani právní odpovědnost za používání této aplikace. Veškerá odpovědnost je na uživateli.**

Vždy si ověřte, které aplikace blokujete.

## Licence

Licencováno pod **GNU General Public License v3.0 (GPLv3)**. Viz [LICENSE.md](../LICENSE.md).

Přiložená data podpisů trackerů nejsou kód a mají vlastní licenci (ODbL v1.0). Viz [TRACKER_DATA_LICENSE.md](../TRACKER_DATA_LICENSE.md).

## Podpora

- ⭐ Dejte projektu hvězdičku: [GitHub Stars](https://github.com/AhmetCanArslan/ShizuWall/stargazers)
- ☕ Přispějte: [Buy Me a Coffee](https://buymeacoffee.com/ahmetcanarslan)
- ⬇️ Stáhněte si: [Google Play Store](https://play.google.com/store/apps/details?id=com.arslan.shizuwall)

## Poděkování

- [Shizuku](https://github.com/RikkaApps/Shizuku) — API umožňující spouštění privilegovaných příkazů.
- [LibADB](https://github.com/MuntashirAkon/libadb-android) — Podpora bezdrátového ladění a připojení k démonovi.
- [Exodus Privacy](https://exodus-privacy.eu.org/) — Databáze podpisů trackerů použitá k detekci sledovacích SDK uvnitř aplikací, licencovaná pod [ODbL v1.0](https://opendatacommons.org/licenses/odbl/1-0/). ShizuWall obsahuje zkrácenou kopii a nikdy nekontaktuje jejich API. Viz [TRACKER_DATA_LICENSE.md](../TRACKER_DATA_LICENSE.md).
