<div align="center">
  <a href="https://play.google.com/store/apps/details?id=com.arslan.shizuwall">
    <img src="../app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png" alt="ShizuWall Icon" width="72" />
  </a>
  <h1>ShizuWall</h1>
  <strong>Android-Firewall ohne VPN.</strong><br/>
  Datenschutz zuerst, rein lokal, betrieben über Shizuku / lokalen ADB-Daemon / Root.
</div>

<p align="center">
  <a href="../README.md">English</a> ·
  <a href="README.tr.md">Türkçe</a> ·
  <b>Deutsch</b> ·
  <a href="README.it.md">Italiano</a> ·
  <a href="README.pt.md">Português</a> ·
  <a href="README.cs.md">Čeština</a> ·
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


## Warum ShizuWall

- **Kein VPN**: Vermeidet Paketmitschnitt und die Nebenwirkungen eines dauerhaften VPN-Tunnels.
- **Netzwerksteuerung pro App**: Schaltet den Netzwerkzugriff von Apps über Androids `connectivity`-Chain-3-Steuerung ein und aus.
- **Datenschutz by Design**: Offline-first, keine Analyse, keine Telemetrie, kein Tracking.
- **Automatisierbar**: Unterstützt `adb broadcast`-Befehle für Skripte und Aufgabenautomatisierung.
- **Steuerungsmöglichkeiten**: ShizuWall bietet drei bequeme Wege zur Steuerung der Firewall: Schnelleinstellungen-Kachel, App-Widget und schwebende Firewall-Schaltfläche.

## Screenshots

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

## Voraussetzungen

- Android 11 (API 30) oder höher
- Ein Steuerungs-Backend: Shizuku, lokaler ADB-Daemon oder Root-Zugriff

## Steuerungs-Backends

ShizuWall unterstützt drei Methoden zur Ausführung von Firewall-Befehlen:

| Methode | Beschreibung | Einrichtung |
|--------|---------|---------|
| **Shizuku** | Sichere API, die mit Systemdiensten kommuniziert. Erfordert die Shizuku-App. Forks werden unterstützt. | Shizuku-App installieren und einrichten, Berechtigungen erteilen |
| **Root** | Direkter Root-Zugriff. | Gerät mit den üblichen Methoden rooten |
| **LibADB (LADB)** | Nutzt die integrierte Funktion "Drahtloses Debugging" deines Telefons, sodass es sich wie ein per USB verbundener Computer verhält. So kann die App fortgeschrittene Systemänderungen vornehmen – ohne Computer, Root oder zusätzliche Apps wie Shizuku. | Drahtloses Debugging in den Entwickleroptionen aktivieren und koppeln (Anleitung ist in der App) |

## Funktionsweise

ShizuWall nutzt Androids **Chain 3** (Connectivity-Chain), um den Netzwerkzugriff pro App zu steuern. Dies sind die Plattformbefehle, die über Shizuku oder den lokalen Daemon ausgeführt werden:

### ADB-Chain-3-Befehle

```bash
# Firewall-Framework aktivieren
cmd connectivity set-chain3-enabled true

# Bestimmte App blockieren
cmd connectivity set-package-networking-enabled false <package.name>

# Blockierung einer bestimmten App aufheben
cmd connectivity set-package-networking-enabled true <package.name>

# Firewall-Framework deaktivieren
cmd connectivity set-chain3-enabled false
```

**Chain 3** ist ein Android-Plattformmechanismus, der den Netzwerkzugriff pro Paket auf Systemebene abfängt und steuert und so eine feingranulare Firewall-Kontrolle ermöglicht.

## Automatisierung (ADB-Broadcast)

Du kannst ShizuWall aus Skripten und Automatisierungswerkzeugen steuern.

**Action**: `shizuwall.CONTROL`  
**Komponente**: `com.arslan.shizuwall/.receivers.FirewallControlReceiver`  

**Extras**

- `state` (Boolean, erforderlich): `true` = aktivieren, `false` = deaktivieren
- `apps` (String, optional): Paketliste als CSV. Wird sie weggelassen, verwendet ShizuWall die gespeicherten ausgewählten Apps.

### Beispiele

```bash
# Firewall für gespeicherte ausgewählte Apps aktivieren
adb shell am broadcast -a shizuwall.CONTROL -n com.arslan.shizuwall/.receivers.FirewallControlReceiver --ez state true

# Firewall für gespeicherte ausgewählte Apps deaktivieren
adb shell am broadcast -a shizuwall.CONTROL -n com.arslan.shizuwall/.receivers.FirewallControlReceiver --ez state false

# Firewall für bestimmte Pakete aktivieren
adb shell am broadcast -a shizuwall.CONTROL -n com.arslan.shizuwall/.receivers.FirewallControlReceiver --ez state true --es apps "com.example.app1,com.example.app2"

# Firewall für bestimmte Pakete deaktivieren
adb shell am broadcast -a shizuwall.CONTROL -n com.arslan.shizuwall/.receivers.FirewallControlReceiver --ez state false --es apps "com.example.app1,com.example.app2"
```

> Eines der Steuerungs-Backends (Shizuku, lokaler ADB-Daemon oder Root) muss aktiv sein, damit Broadcasts funktionieren.

## Hinweise & Einschränkungen

- Firewall-Regeln werden durch das Verhalten der Android-Plattform beim Neustart gelöscht.
- Ein Neustart des Geräts setzt alle von ShizuWall gesetzten aktiven Netzwerksperren zurück.
- Die App fordert `android.permission.INTERNET` ausschließlich für das Koppeln beim drahtlosen Debugging an (Verbindung zum lokalen LibADB-Daemon).

## Build (Entwickler)

### App

```bash
./gradlew assembleRelease
```

### Daemon

Der Daemon auf dem Gerät (`SystemDaemon.java`) wird im Rahmen des normalen Gradle-Builds durch die Aufgabe `compileDaemonDex` automatisch zu einer DEX-Datei (`daemon.bin`) kompiliert. Er wird mit `javac` und `d8` aus den Android-SDK-Build-Tools aus dem Quellcode erzeugt und als Asset gepackt – im Repository liegt keine vorkompilierte Binärdatei, damit Server für reproduzierbare Builds (z. B. F-Droid) sie selbst erzeugen.

Es ist kein manueller Schritt nötig; `./gradlew assembleRelease` baut ihn mit. Die Aufgabe kann auch einzeln ausgeführt werden:

```bash
./gradlew :app:compileDaemonDex
```

## Haftungsausschluss zu Sicherheit und Verbreitung

ShizuWall wird **"wie besehen"** ohne jegliche Gewährleistung bereitgestellt.

Mit der Nutzung dieser App erkennst du an, dass sie auf weitreichenden Systemberechtigungen (Shizuku/ADB/Root) beruht, und akzeptierst alle damit verbundenen Risiken. Der Entwickler haftet nicht für Schäden wie Systeminstabilität, Datenverlust, Dienstunterbrechungen oder Nebenwirkungen blockierter Netzwerkverbindungen.

**Offizielle Verbreitung und Risiken durch Dritte**
Die Entwickler und Mitwirkenden von ShizuWall übernehmen keinerlei Haftung für Schäden, Sicherheitsverletzungen, Malware-Infektionen oder Datenverluste, die sich aus dem Herunterladen, Installieren oder Verwenden von APK-Dateien aus Drittquellen ergeben. ShizuWall wird offiziell ausschließlich über den Google Play Store, das offizielle GitHub-Repository und F-Droid entwickelt und verbreitet. ShizuWall besitzt, betreibt oder unterstützt keine offizielle Website. Jede Website oder Drittplattform, die behauptet, offizielle ShizuWall-Downloads anzubieten, steht in keinerlei Verbindung zum Projekt und ist nicht autorisiert. Wer die Anwendung außerhalb der offiziellen Kanäle bezieht, tut dies ausschließlich auf eigenes Risiko.

**ShizuWall ist eine Open-Source-Anwendung. Der Entwickler übernimmt keine finanzielle, moralische oder rechtliche Verantwortung für die Nutzung dieser Anwendung. Die gesamte Verantwortung liegt beim Nutzer.**

Prüfe immer, welche Apps du blockierst.

## Lizenz

Lizenziert unter der **GNU General Public License v3.0 (GPLv3)**. Siehe [LICENSE.md](../LICENSE.md).

Die mitgelieferten Tracker-Signaturdaten sind kein Code und stehen unter einer eigenen Lizenz (ODbL v1.0). Siehe [TRACKER_DATA_LICENSE.md](../TRACKER_DATA_LICENSE.md).

## Unterstützung

- ⭐ Projekt mit einem Stern versehen: [GitHub Stars](https://github.com/AhmetCanArslan/ShizuWall/stargazers)
- ☕ Spenden: [Buy Me a Coffee](https://buymeacoffee.com/ahmetcanarslan)
- ⬇️ Herunterladen: [Google Play Store](https://play.google.com/store/apps/details?id=com.arslan.shizuwall)

## Danksagungen

- [Shizuku](https://github.com/RikkaApps/Shizuku) — API, die den Ablauf für privilegierte Befehlsausführung ermöglicht.
- [LibADB](https://github.com/MuntashirAkon/libadb-android) — Unterstützung für drahtloses Debugging und Daemon-Verbindung.
- [Exodus Privacy](https://exodus-privacy.eu.org/) — Tracker-Signaturdatenbank zur Erkennung von Tracker-SDKs in Apps, lizenziert unter [ODbL v1.0](https://opendatacommons.org/licenses/odbl/1-0/). ShizuWall enthält eine gekürzte Kopie und kontaktiert deren API niemals. Siehe [TRACKER_DATA_LICENSE.md](../TRACKER_DATA_LICENSE.md).
