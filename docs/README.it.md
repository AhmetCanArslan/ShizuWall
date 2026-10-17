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


## Funziona con

Android 11+ e uno di questi backend:

- **Shizuku** (i fork sono supportati)
- **LibADB**: si associa tramite Debug wireless, senza computer
- **Root**

## Perché ShizuWall

- **Niente VPN**: nessuna intercettazione dei pacchetti, nessun tunnel persistente.
- **Controllo per app**: blocca la rete app per app, comprese le copie nel profilo di lavoro e i cloni.
- **Privato**: offline-first, niente analytics, niente telemetria.
- **Accesso rapido**: riquadro delle Impostazioni rapide, widget e pulsante flottante.
- **Automazione**: controllabile tramite broadcast da adb, Tasker, MacroDroid e simili.

## Schermata

<p align="center">
  <img src="../assets/screenshots/v4.6/1.png" alt="ShizuWall" width="30%" />
</p>

## Come funziona

ShizuWall usa la **chain 3** di connectivity di Android, un meccanismo della piattaforma che controlla l'accesso alla rete per pacchetto:

```bash
cmd connectivity set-chain3-enabled true                                # firewall attivo
cmd connectivity set-package-networking-enabled false <package.name>   # blocca
cmd connectivity set-package-networking-enabled true <package.name>    # sblocca
cmd connectivity set-chain3-enabled false                               # firewall disattivo
```

Android cancella queste regole al riavvio.

## Automazione

Ogni broadcast richiede un backend attivo. `apps` è un elenco CSV di chiavi app: il solo nome del pacchetto indica l'utente 0, una chiave con prefisso come `150:com.example.app` indica la copia in quel profilo di lavoro o spazio clone.

### Firewall: `shizuwall.CONTROL`

| Extra | Tipo | |
|---|---|---|
| `state` | boolean, obbligatorio | `true` = blocca, `false` = sblocca |
| `apps` | string, facoltativo | App da bloccare o sbloccare. Senza questo extra il broadcast attiva o disattiva l'intero firewall. |

```bash
# Attiva il firewall (state false lo disattiva)
adb shell am broadcast -a shizuwall.CONTROL -n com.arslan.shizuwall/.receivers.FirewallControlReceiver --ez state true

# Blocca un'app e la sua copia nel profilo di lavoro
adb shell am broadcast -a shizuwall.CONTROL -n com.arslan.shizuwall/.receivers.FirewallControlReceiver --ez state true --es apps "com.example.app,150:com.example.app"
```

- Con `apps` la selezione nell'app si adegua: `state true` seleziona le app e attiva il firewall se era spento, `state false` le deseleziona e lascia il firewall attivo. In modalità whitelist `apps` indica l'elenco delle app consentite, quindi i due stati sono invertiti.
- Con **Mostra le app di altri profili** disattivato, il solo nome del pacchetto si applica anche ai suoi cloni. Con l'opzione attiva, ogni profilo si indica solo tramite il suo prefisso.

### Nuova app: `shizuwall.APP_INSTALLED`

Segnala un'app appena installata, così la policy e la notifica per le nuove app vengono applicate come se ShizuWall avesse rilevato l'installazione. ShizuWall vede solo le installazioni nel proprio profilo: questo è il modo per coprire gli altri.

```bash
adb shell am broadcast -a shizuwall.APP_INSTALLED -n com.arslan.shizuwall/.receivers.AppInstalledReceiver --es apps "com.example.newapp"
```

Nessuna regola viene applicata mentre il firewall è disattivato. Le app senza il permesso `INTERNET` vengono ignorate.

### Profilo: `shizuwall.PROFILE`

Attiva un profilo salvato: scrive la selezione delle app, la modalità del firewall, le modalità per app e l'impostazione *Mostra app di sistema*.

| Extra | Tipo | |
|---|---|---|
| `profile` | string | Nome del profilo, come mostrato nel pannello dei profili. |
| `profile_id` | string | ID interno; viene controllato per primo e non cambia con le rinomine. La finestra **Automazione** del profilo mostra il comando pronto. |
| `force_enable` | boolean, facoltativo | Attiva il firewall dopo il cambio anche se era spento. |

È richiesto uno tra `profile` e `profile_id`. Se nessuno corrisponde, non cambia nulla e un toast lo segnala.

```bash
adb shell am broadcast -a shizuwall.PROFILE -n com.arslan.shizuwall/.receivers.ProfileControlReceiver --es profile "Work"
```

## Avvertenza

ShizuWall è fornito **"così com'è"**, senza garanzie. Si basa su permessi di sistema avanzati e lo sviluppatore non è responsabile di instabilità del sistema, perdita di dati o effetti collaterali del blocco della rete. Verifica sempre quali app blocchi.

**Le fonti ufficiali sono solo Google Play, F-Droid e questo repository.** Gli APK di altra provenienza non sono affiliati e si usano a proprio rischio.

Il permesso `INTERNET` è richiesto solo per l'associazione del debug wireless (LibADB).

## Licenza

[GPLv3](../LICENSE.md). I dati delle firme dei tracker inclusi sono concessi in licenza separatamente con ODbL v1.0, vedi [TRACKER_DATA_LICENSE.md](../TRACKER_DATA_LICENSE.md).

## Riconoscimenti

- [Shizuku](https://github.com/RikkaApps/Shizuku): esecuzione di comandi privilegiati.
- [LibADB](https://github.com/MuntashirAkon/libadb-android): debug wireless e connessione al daemon.
- [Exodus Privacy](https://exodus-privacy.eu.org/): database delle firme dei tracker. ShizuWall ne include una copia ridotta e non contatta mai la loro API.
