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

## Neden ShizuWall

- **VPN yok**: Paket yakalama ve kalıcı VPN tüneli yan etkilerinden kaçınır.
- **Uygulama bazlı ağ denetimi**: Uygulamaların ağ erişimini Android'in `connectivity` chain-3 denetimleriyle açıp kapatır.
- **Tasarımı gereği gizlilik odaklı**: Öncelikle çevrimdışı; analitik, telemetri veya izleme yok.
- **Otomasyona hazır**: Betikler ve görev otomasyonu için `adb broadcast` komutlarını destekler.
- **Kontrol yöntemleri**: ShizuWall güvenlik duvarını kontrol etmek için üç pratik yol sunar: Hızlı Ayarlar düğmesi, uygulama widget'ı ve yüzen güvenlik duvarı düğmesi.

## Ekran Görüntüleri

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

## Gereksinimler

- Android 11 (API 30) veya üzeri
- Bir kontrol arka ucu: Shizuku, yerel ADB arka plan servisi veya root erişimi

## Kontrol Arka Uçları

ShizuWall güvenlik duvarı komutlarını çalıştırmak için üç yöntemi destekler:

| Yöntem | Açıklama | Kurulum |
|--------|---------|---------|
| **Shizuku** | Sistem servisleriyle iletişim kuran güvenli API. Shizuku uygulaması gerekir. Çatallar (fork) da desteklenir. | Shizuku uygulamasını kurup ayarlayın, izinleri verin |
| **Root** | Doğrudan root erişimi. | Cihazınızı standart yöntemlerle root'layın |
| **LibADB (LADB)** | Telefonunuzun yerleşik "Kablosuz hata ayıklama" özelliğini kullanarak USB ile bağlı bir bilgisayar gibi davranır. Böylece uygulama; bilgisayar, root veya Shizuku gibi ek uygulamalar olmadan gelişmiş sistem değişiklikleri yapabilir. | Geliştirici seçeneklerinde kablosuz hata ayıklamayı etkinleştirip eşleştirin (rehber uygulama içinde) |

## Nasıl Çalışır

ShizuWall, uygulama bazlı ağ denetimi için Android'in **Chain 3** (bağlantı zinciri) mekanizmasını kullanır. Shizuku veya yerel arka plan servisi üzerinden çalıştırılan platform komutları şunlardır:

### ADB Chain 3 Komutları

```bash
# Güvenlik duvarı çerçevesini etkinleştir
cmd connectivity set-chain3-enabled true

# Belirli bir uygulamayı engelle
cmd connectivity set-package-networking-enabled false <package.name>

# Belirli bir uygulamanın engelini kaldır
cmd connectivity set-package-networking-enabled true <package.name>

# Güvenlik duvarı çerçevesini devre dışı bırak
cmd connectivity set-chain3-enabled false
```

**Chain 3**, paket bazında ağ erişimini sistem düzeyinde yakalayıp denetleyen bir Android platform mekanizmasıdır ve ince ayarlı güvenlik duvarı kontrolü sağlar.

## Otomasyon (ADB Broadcast)

ShizuWall'ı betiklerden ve otomasyon araçlarından kontrol edebilirsiniz.

**Eylem**: `shizuwall.CONTROL`  
**Bileşen**: `com.arslan.shizuwall/.receivers.FirewallControlReceiver`  

**Ekstralar**

- `state` (boolean, zorunlu): `true` = etkinleştir, `false` = devre dışı bırak
- `apps` (string, isteğe bağlı): Virgülle ayrılmış paket listesi. Belirtilmezse ShizuWall kayıtlı seçili uygulamaları kullanır.

### Örnekler

```bash
# Kayıtlı seçili uygulamalar için güvenlik duvarını etkinleştir
adb shell am broadcast -a shizuwall.CONTROL -n com.arslan.shizuwall/.receivers.FirewallControlReceiver --ez state true

# Kayıtlı seçili uygulamalar için güvenlik duvarını devre dışı bırak
adb shell am broadcast -a shizuwall.CONTROL -n com.arslan.shizuwall/.receivers.FirewallControlReceiver --ez state false

# Belirli paketler için güvenlik duvarını etkinleştir
adb shell am broadcast -a shizuwall.CONTROL -n com.arslan.shizuwall/.receivers.FirewallControlReceiver --ez state true --es apps "com.example.app1,com.example.app2"

# Belirli paketler için güvenlik duvarını devre dışı bırak
adb shell am broadcast -a shizuwall.CONTROL -n com.arslan.shizuwall/.receivers.FirewallControlReceiver --ez state false --es apps "com.example.app1,com.example.app2"
```

> Yayınların başarılı olması için kontrol arka uçlarından biri (Shizuku, yerel ADB arka plan servisi veya Root) etkin olmalıdır.

## Notlar ve Kısıtlamalar

- Güvenlik duvarı kuralları, Android platformunun davranışı gereği yeniden başlatmada temizlenir.
- Cihazı yeniden başlatmak, ShizuWall tarafından uygulanmış tüm etkin ağ engellemelerini sıfırlar.
- Uygulama `android.permission.INTERNET` iznini yalnızca kablosuz hata ayıklama eşleştirmesi (LibADB yerel arka plan servisi bağlantısı) için ister.

## Derleme (Geliştiriciler)

### Uygulama

```bash
./gradlew assembleRelease
```

### Arka plan servisi (daemon)

Cihaz üzerindeki arka plan servisi (`SystemDaemon.java`), normal Gradle derlemesinin bir parçası olarak `compileDaemonDex` göreviyle otomatik şekilde bir DEX dosyasına (`daemon.bin`) derlenir. Android SDK build-tools içindeki `javac` ve `d8` ile kaynaktan derlenip bir varlık (asset) olarak paketlenir; depoda önceden derlenmiş bir ikili dosya yoktur, böylece yeniden üretilebilir derleme sunucuları (ör. F-Droid) onu kendileri üretir.

Elle bir adım gerekmez; `./gradlew assembleRelease` bunu da derler. Görev tek başına da çalıştırılabilir:

```bash
./gradlew :app:compileDaemonDex
```

## Güvenlik ve Dağıtım Feragatnamesi

ShizuWall hiçbir garanti verilmeksizin **"olduğu gibi"** sunulur.

Bu uygulamayı kullanarak, gelişmiş sistem izinlerine (Shizuku/ADB/Root) dayandığını kabul eder ve ilgili tüm riskleri üstlenirsiniz. Geliştirici; sistem kararsızlığı, veri kaybı, hizmet kesintisi veya engellenen ağ erişiminden kaynaklanan yan etkiler gibi zararlardan sorumlu değildir.

**Resmî Dağıtım ve Üçüncü Taraf Riskleri**
ShizuWall geliştiricileri ve katkıda bulunanları; üçüncü taraf kaynaklardan edinilen APK dosyalarının indirilmesi, kurulması veya kullanılmasından doğan hiçbir zarardan, güvenlik ihlalinden, kötü amaçlı yazılım bulaşmasından veya veri kaybından sorumlu tutulamaz. ShizuWall resmî olarak yalnızca Google Play Store, resmî GitHub deposu ve F-Droid üzerinden geliştirilir ve dağıtılır. ShizuWall'ın sahip olduğu, işlettiği veya onayladığı herhangi bir resmî web sitesi yoktur. Resmî ShizuWall indirmeleri sunduğunu iddia eden her web sitesi veya üçüncü taraf platform tamamen ilgisiz ve yetkisizdir. Uygulamayı resmî kanalların dışından edinen kullanıcılar bunu tamamen kendi riskleriyle yapar.

**ShizuWall açık kaynaklı bir uygulamadır. Geliştirici, bu uygulamanın kullanımına ilişkin hiçbir mali, ahlaki veya hukuki sorumluluk kabul etmez. Tüm sorumluluk kullanıcıya aittir.**

Hangi uygulamaları engellediğinizi daima doğrulayın.

## Lisans

**GNU General Public License v3.0 (GPLv3)** ile lisanslanmıştır. Bkz. [LICENSE.md](../LICENSE.md).

Uygulamayla birlikte gelen izleyici imza verisi kod değildir ve kendi lisansına sahiptir (ODbL v1.0). Bkz. [TRACKER_DATA_LICENSE.md](../TRACKER_DATA_LICENSE.md).

## Destek

- ⭐ Projeye yıldız verin: [GitHub Stars](https://github.com/AhmetCanArslan/ShizuWall/stargazers)
- ☕ Bağış yapın: [Buy Me a Coffee](https://buymeacoffee.com/ahmetcanarslan)
- ⬇️ İndirin: [Google Play Store](https://play.google.com/store/apps/details?id=com.arslan.shizuwall)

## Teşekkürler

- [Shizuku](https://github.com/RikkaApps/Shizuku) — Ayrıcalıklı komut çalıştırma akışını mümkün kılan API.
- [LibADB](https://github.com/MuntashirAkon/libadb-android) — Kablosuz hata ayıklama ve arka plan servisi bağlantısı desteği.
- [Exodus Privacy](https://exodus-privacy.eu.org/) — Uygulamalardaki izleyici SDK'larını tespit etmek için kullanılan izleyici imza veritabanı; [ODbL v1.0](https://opendatacommons.org/licenses/odbl/1-0/) ile lisanslıdır. ShizuWall kırpılmış bir kopyayı paketler ve API'lerine hiçbir zaman bağlanmaz. Bkz. [TRACKER_DATA_LICENSE.md](../TRACKER_DATA_LICENSE.md).
