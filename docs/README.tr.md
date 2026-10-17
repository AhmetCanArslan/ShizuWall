<div align="center">
  <a href="https://play.google.com/store/apps/details?id=com.arslan.shizuwall">
    <img src="../app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png" alt="ShizuWall Icon" width="72" />
  </a>
  <h1>ShizuWall</h1>
  <strong>VPN kullanmayan Android güvenlik duvarı.</strong><br/>
  Gizlilik odaklı, tamamen yerel; Shizuku / yerel ADB arka plan servisi / Root ile çalışır.
</div>

<p align="center">
  <a href="../README.md">English</a> ·
  <b>Türkçe</b> ·
  <a href="README.de.md">Deutsch</a> ·
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

## Şunlarla çalışır

Android 11+ ve şu arka uçlardan biri:

- **Shizuku** (fork'lar desteklenir)
- **LibADB**: Kablosuz Hata Ayıklama ile eşleşir, bilgisayar gerekmez
- **Root**

## Neden ShizuWall

- **VPN yok**: paket yakalama yok, kalıcı tünel yok.
- **Uygulama bazlı denetim**: iş profili ve klon kopyaları dahil, ağ erişimini uygulama bazında engeller.
- **Gizlilik**: öncelikle çevrimdışı; analitik yok, telemetri yok.
- **Hızlı erişim**: Hızlı Ayarlar kutucuğu, widget ve yüzen düğme.
- **Otomasyon**: adb, Tasker, MacroDroid ve benzerlerinden gönderilen yayınlarla (broadcast) kontrol edilebilir.

## Ekran Görüntüsü

<p align="center">
  <img src="../assets/screenshots/v4.6/1.png" alt="ShizuWall" width="30%" />
</p>

## Nasıl Çalışır

ShizuWall, ağ erişimini paket bazında denetleyen bir platform mekanizması olan Android'in connectivity **chain 3** zincirini kullanır:

```bash
cmd connectivity set-chain3-enabled true                                # güvenlik duvarı açık
cmd connectivity set-package-networking-enabled false <package.name>   # engelle
cmd connectivity set-package-networking-enabled true <package.name>    # engeli kaldır
cmd connectivity set-chain3-enabled false                               # güvenlik duvarı kapalı
```

Android bu kuralları yeniden başlatmada temizler.

## Otomasyon

Her yayın için etkin bir arka uç gerekir. `apps`, virgülle ayrılmış uygulama anahtarları listesidir: yalın paket adı kullanıcı 0'ı, `150:com.example.app` gibi ön ekli bir anahtar ise o iş profilindeki veya klon alanındaki kopyayı hedefler.

### Güvenlik duvarı: `shizuwall.CONTROL`

| Ekstra | Tür | |
|---|---|---|
| `state` | boolean, zorunlu | `true` = engelle, `false` = engeli kaldır |
| `apps` | string, isteğe bağlı | Engellenecek veya engeli kaldırılacak uygulamalar. Verilmezse yayın tüm güvenlik duvarını açıp kapatır. |

```bash
# Güvenlik duvarını aç (state false kapatır)
adb shell am broadcast -a shizuwall.CONTROL -n com.arslan.shizuwall/.receivers.FirewallControlReceiver --ez state true

# Bir uygulamayı ve iş profilindeki kopyasını engelle
adb shell am broadcast -a shizuwall.CONTROL -n com.arslan.shizuwall/.receivers.FirewallControlReceiver --ez state true --es apps "com.example.app,150:com.example.app"
```

`apps` verildiğinde uygulamadaki seçim de güncellenir: `state true` uygulamaları seçer ve kapalıysa güvenlik duvarını açar, `state false` seçimi kaldırır ve güvenlik duvarını açık bırakır. Beyaz liste modunda `apps` izin listesini hedefler, bu yüzden iki durum ters çalışır.

### Yeni uygulama: `shizuwall.APP_INSTALLED`

Yeni kurulan bir uygulamayı bildirir; böylece yeni uygulama politikası ve bildirimi, kurulumu ShizuWall kendisi algılamış gibi uygulanır. ShizuWall yalnızca kendi profilindeki kurulumları görür; diğer profilleri kapsamanın yolu budur.

```bash
adb shell am broadcast -a shizuwall.APP_INSTALLED -n com.arslan.shizuwall/.receivers.AppInstalledReceiver --es apps "com.example.newapp"
```

Güvenlik duvarı kapalıyken kural uygulanmaz. `INTERNET` izni olmayan uygulamalar yok sayılır.

### Profil: `shizuwall.PROFILE`

Kayıtlı bir profili etkinleştirir: uygulama seçimini, güvenlik duvarı modunu, uygulama bazlı modları ve *Sistem uygulamalarını göster* ayarını yazar.

| Ekstra | Tür | |
|---|---|---|
| `profile` | string | Profil adı, profiller sayfasında göründüğü gibi. |
| `profile_id` | string | Dahili kimlik; önce buna bakılır ve yeniden adlandırmadan etkilenmez. Profilin **Otomasyon** penceresi hazır komutu gösterir. |
| `force_enable` | boolean, isteğe bağlı | Kapalı olsa bile geçişten sonra güvenlik duvarını açar. |

`profile` veya `profile_id` değerlerinden biri zorunludur. İkisi de eşleşmezse hiçbir şey değişmez ve bir bildirim (toast) bunu haber verir.

```bash
adb shell am broadcast -a shizuwall.PROFILE -n com.arslan.shizuwall/.receivers.ProfileControlReceiver --es profile "Work"
```

## Feragatname

ShizuWall hiçbir garanti verilmeksizin **"olduğu gibi"** sunulur. Gelişmiş sistem izinlerine dayanır; geliştirici sistem kararsızlığı, veri kaybı veya engellenen ağ erişiminin yan etkilerinden sorumlu değildir. Hangi uygulamaları engellediğinizi daima doğrulayın.

**Resmî kaynaklar yalnızca Google Play, F-Droid ve bu depodur.** Başka yerlerden edinilen APK'lar projeyle ilişkili değildir ve kendi riskinizle kullanılır. ShizuWall'ın herhangi bir web sitesi yoktur.

`INTERNET` izni yalnızca kablosuz hata ayıklama eşleştirmesi (LibADB) için istenir.

## Lisans

[GPLv3](../LICENSE.md). Uygulamayla gelen izleyici imza verisi ayrıca ODbL v1.0 ile lisanslıdır, bkz. [TRACKER_DATA_LICENSE.md](../TRACKER_DATA_LICENSE.md).

## Teşekkürler

- [Shizuku](https://github.com/RikkaApps/Shizuku): ayrıcalıklı komut çalıştırma.
- [LibADB](https://github.com/MuntashirAkon/libadb-android): kablosuz hata ayıklama ve arka plan servisi bağlantısı.
- [Exodus Privacy](https://exodus-privacy.eu.org/): izleyici imza veritabanı. ShizuWall kırpılmış bir kopyayı paketler ve API'lerine hiçbir zaman bağlanmaz.
