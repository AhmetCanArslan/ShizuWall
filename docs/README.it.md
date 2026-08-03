<div align="center">
  <a href="https://play.google.com/store/apps/details?id=com.arslan.shizuwall">
    <img src="../app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png" alt="ShizuWall Icon" width="72" />
  </a>
  <h1>ShizuWall</h1>
  <strong>Firewall per Android senza VPN.</strong><br/>
  Privacy al primo posto, solo in locale, basato su Shizuku / daemon ADB locale / Root.
</div>

<p align="center">
  <a href="../README.md">English</a> ·
  <a href="README.tr.md">Türkçe</a> ·
  <a href="README.de.md">Deutsch</a> ·
  <b>Italiano</b> ·
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


## Perché ShizuWall

- **Nessuna VPN**: Evita l'intercettazione dei pacchetti e gli effetti collaterali di un tunnel VPN permanente.
- **Controllo di rete per singola app**: Attiva o disattiva la rete delle app tramite i controlli chain-3 di `connectivity` di Android.
- **Privacy per progettazione**: Offline-first, nessuna analisi, nessuna telemetria, nessun tracciamento.
- **Pronto per l'automazione**: Supporta i comandi `adb broadcast` per script e automazioni.
- **Metodi di controllo**: ShizuWall offre tre modi comodi per controllare il firewall: riquadro delle Impostazioni rapide, widget e pulsante firewall flottante.

## Schermate

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

## Requisiti

- Android 11 (API 30) o superiore
- Un backend di controllo: Shizuku, daemon ADB locale o accesso root

## Backend di controllo

ShizuWall supporta tre metodi per eseguire i comandi del firewall:

| Metodo | Descrizione | Configurazione |
|--------|---------|---------|
| **Shizuku** | API sicura che comunica con i servizi di sistema. Richiede l'app Shizuku. I fork sono supportati. | Installa e configura l'app Shizuku, concedi le autorizzazioni |
| **Root** | Accesso root diretto. | Esegui il root del dispositivo con i metodi standard |
| **LibADB (LADB)** | Usa la funzione integrata "Debug wireless" del telefono per comportarsi come un computer collegato via USB. Ciò consente all'app di effettuare modifiche di sistema avanzate senza bisogno di un computer, del root o di app aggiuntive come Shizuku. | Attiva il debug wireless ed esegui l'associazione nelle Opzioni sviluppatore (la guida è nell'app) |

## Come funziona

ShizuWall usa la **Chain 3** di Android (catena di connettività) per controllare la rete di ogni app. Questi sono i comandi di piattaforma eseguiti tramite Shizuku o il daemon locale:

### Comandi ADB Chain 3

```bash
# Attiva il framework del firewall
cmd connectivity set-chain3-enabled true

# Blocca un'app specifica
cmd connectivity set-package-networking-enabled false <package.name>

# Sblocca un'app specifica
cmd connectivity set-package-networking-enabled true <package.name>

# Disattiva il framework del firewall
cmd connectivity set-chain3-enabled false
```

**Chain 3** è un meccanismo della piattaforma Android che intercetta e controlla l'accesso alla rete per ogni pacchetto a livello di sistema, consentendo un controllo firewall granulare.

## Automazione (broadcast ADB)

Puoi controllare ShizuWall da script e strumenti di automazione.

**Azione**: `shizuwall.CONTROL`  
**Componente**: `com.arslan.shizuwall/.receivers.FirewallControlReceiver`  

**Extra**

- `state` (boolean, obbligatorio): `true` = attiva, `false` = disattiva
- `apps` (stringa, facoltativo): elenco di pacchetti in CSV. Se omesso, ShizuWall usa le app selezionate salvate.

### Esempi

```bash
# Attiva il firewall per le app selezionate salvate
adb shell am broadcast -a shizuwall.CONTROL -n com.arslan.shizuwall/.receivers.FirewallControlReceiver --ez state true

# Disattiva il firewall per le app selezionate salvate
adb shell am broadcast -a shizuwall.CONTROL -n com.arslan.shizuwall/.receivers.FirewallControlReceiver --ez state false

# Attiva il firewall per pacchetti specifici
adb shell am broadcast -a shizuwall.CONTROL -n com.arslan.shizuwall/.receivers.FirewallControlReceiver --ez state true --es apps "com.example.app1,com.example.app2"

# Disattiva il firewall per pacchetti specifici
adb shell am broadcast -a shizuwall.CONTROL -n com.arslan.shizuwall/.receivers.FirewallControlReceiver --ez state false --es apps "com.example.app1,com.example.app2"
```

> Perché i broadcast funzionino, uno dei backend di controllo (Shizuku, daemon ADB locale o Root) deve essere attivo.

## Note e limitazioni

- Le regole del firewall vengono cancellate al riavvio per il comportamento della piattaforma Android.
- Riavviare il dispositivo azzera tutti i blocchi di rete attivi applicati da ShizuWall.
- L'app richiede `android.permission.INTERNET` solo per l'associazione del debug wireless (connessione al daemon locale LibADB).

## Compilazione (sviluppatori)

### App

```bash
./gradlew assembleRelease
```

### Daemon

Il daemon sul dispositivo (`SystemDaemon.java`) viene compilato in un DEX (`daemon.bin`) automaticamente come parte della normale build Gradle tramite l'attività `compileDaemonDex`. Viene compilato dal sorgente con `javac` e `d8` dei build-tools dell'SDK Android e incluso come asset: nel repository non c'è alcun binario precompilato, così i server di build riproducibili (ad es. F-Droid) lo generano da soli.

Non è richiesto alcun passaggio manuale; `./gradlew assembleRelease` lo compila. L'attività può anche essere eseguita da sola:

```bash
./gradlew :app:compileDaemonDex
```

## Avvertenza su sicurezza e distribuzione

ShizuWall è fornito **"così com'è"**, senza garanzie di alcun tipo.

Usando questa app riconosci che si basa su autorizzazioni di sistema avanzate (Shizuku/ADB/Root) e accetti tutti i rischi correlati. Lo sviluppatore non è responsabile di danni quali instabilità del sistema, perdita di dati, interruzione di servizi o effetti collaterali dovuti al blocco della rete.

**Distribuzione ufficiale e rischi di terze parti**
Gli sviluppatori e i collaboratori di ShizuWall non si assumono alcuna responsabilità per danni, violazioni di sicurezza, infezioni da malware o perdite di dati derivanti dal download, dall'installazione o dall'uso di file APK ottenuti da fonti di terze parti. ShizuWall è sviluppato e distribuito ufficialmente solo tramite Google Play Store, il suo repository GitHub ufficiale e F-Droid. ShizuWall non possiede, gestisce né avalla alcun sito web ufficiale. Qualsiasi sito web o piattaforma di terze parti che dichiari di offrire download ufficiali di ShizuWall è del tutto estraneo e non autorizzato. Chi ottiene l'applicazione al di fuori dei canali ufficiali lo fa esclusivamente a proprio rischio.

**ShizuWall è un'applicazione open source. Lo sviluppatore non accetta alcuna responsabilità finanziaria, morale o legale per l'uso di questa applicazione. Ogni responsabilità è dell'utente.**

Verifica sempre quali app stai bloccando.

## Licenza

Distribuito con licenza **GNU General Public License v3.0 (GPLv3)**. Vedi [LICENSE.md](../LICENSE.md).

I dati delle firme dei tracker inclusi non sono codice e hanno una licenza propria (ODbL v1.0). Vedi [TRACKER_DATA_LICENSE.md](../TRACKER_DATA_LICENSE.md).

## Supporto

- ⭐ Metti una stella al progetto: [GitHub Stars](https://github.com/AhmetCanArslan/ShizuWall/stargazers)
- ☕ Fai una donazione: [Buy Me a Coffee](https://buymeacoffee.com/ahmetcanarslan)
- ⬇️ Scarica: [Google Play Store](https://play.google.com/store/apps/details?id=com.arslan.shizuwall)

## Riconoscimenti

- [Shizuku](https://github.com/RikkaApps/Shizuku) — API che rende possibile il flusso di esecuzione dei comandi privilegiati.
- [LibADB](https://github.com/MuntashirAkon/libadb-android) — Supporto per il debug wireless e la connessione al daemon.
- [Exodus Privacy](https://exodus-privacy.eu.org/) — Database di firme di tracker usato per rilevare gli SDK di tracciamento nelle app, con licenza [ODbL v1.0](https://opendatacommons.org/licenses/odbl/1-0/). ShizuWall include una copia ridotta e non contatta mai le loro API. Vedi [TRACKER_DATA_LICENSE.md](../TRACKER_DATA_LICENSE.md).
