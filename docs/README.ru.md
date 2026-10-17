<div align="center">
  <a href="https://play.google.com/store/apps/details?id=com.arslan.shizuwall">
    <img src="../app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png" alt="ShizuWall Icon" width="72" />
  </a>
  <h1>ShizuWall</h1>
  <strong>Файрвол для Android без VPN.</strong><br/>
  Приватность прежде всего, только локально, работает через Shizuku / локальный ADB-демон / root.
</div>

<p align="center">
  <a href="../README.md">English</a> ·
  <a href="README.tr.md">Türkçe</a> ·
  <a href="README.de.md">Deutsch</a> ·
  <a href="README.it.md">Italiano</a> ·
  <a href="README.pt.md">Português</a> ·
  <a href="README.cs.md">Čeština</a> ·
  <b>Русский</b> ·
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


## Работает с

Android 11+ и один из этих бэкендов:

- **Shizuku** (форки поддерживаются)
- **LibADB**: сопряжение по беспроводной отладке, компьютер не нужен
- **Root**

## Почему ShizuWall

- **Без VPN**: никакого перехвата пакетов и постоянного туннеля.
- **Управление по приложениям**: блокирует сеть для каждого приложения, включая копии в рабочем профиле и клоны.
- **Приватность**: работает офлайн, без аналитики и телеметрии.
- **Быстрый доступ**: плитка быстрых настроек, виджет и плавающая кнопка.
- **Автоматизация**: управление через broadcast-команды из adb, Tasker, MacroDroid и подобных.

## Скриншот

<p align="center">
  <img src="../assets/screenshots/v4.6/1.png" alt="ShizuWall" width="30%" />
</p>

## Как это работает

ShizuWall управляет **chain 3** службы connectivity в Android — механизмом платформы, который контролирует доступ к сети для каждого пакета:

```bash
cmd connectivity set-chain3-enabled true                                # файрвол включён
cmd connectivity set-package-networking-enabled false <package.name>   # заблокировать
cmd connectivity set-package-networking-enabled true <package.name>    # разблокировать
cmd connectivity set-chain3-enabled false                               # файрвол выключен
```

Android сбрасывает эти правила при перезагрузке.

## Автоматизация

Для каждого broadcast нужен активный бэкенд. `apps` — это CSV-список ключей приложений: простое имя пакета относится к пользователю 0, а ключ с префиксом, например `150:com.example.app`, — к копии в этом рабочем профиле или пространстве клонов.

### Файрвол: `shizuwall.CONTROL`

| Параметр | Тип | |
|---|---|---|
| `state` | boolean, обязательный | `true` = заблокировать, `false` = разблокировать |
| `apps` | string, необязательный | Приложения, которые нужно заблокировать или разблокировать. Без него broadcast переключает весь файрвол. |

```bash
# Включить файрвол (state false выключает)
adb shell am broadcast -a shizuwall.CONTROL -n com.arslan.shizuwall/.receivers.FirewallControlReceiver --ez state true

# Заблокировать приложение и его копию в рабочем профиле
adb shell am broadcast -a shizuwall.CONTROL -n com.arslan.shizuwall/.receivers.FirewallControlReceiver --ez state true --es apps "com.example.app,150:com.example.app"
```

- С `apps` выбор в приложении синхронизируется: `state true` отмечает приложения и включает файрвол, если он был выключен; `state false` снимает отметку и оставляет файрвол включённым. В режиме белого списка `apps` относится к списку разрешённых, поэтому два состояния меняются местами.
- Если **Показывать приложения из других профилей** выключено, простое имя пакета действует и на его клоны. Если включено, каждый профиль адресуется только через свой префикс.

### Новое приложение: `shizuwall.APP_INSTALLED`

Сообщает о только что установленном приложении, чтобы политика и уведомление для новых приложений применились так, как если бы ShizuWall сам обнаружил установку. ShizuWall видит установки только в своём профиле; так можно охватить остальные.

```bash
adb shell am broadcast -a shizuwall.APP_INSTALLED -n com.arslan.shizuwall/.receivers.AppInstalledReceiver --es apps "com.example.newapp"
```

Пока брандмауэр выключен, правила не применяются. Приложения без разрешения `INTERNET` игнорируются.

### Профиль: `shizuwall.PROFILE`

Активирует сохранённый профиль: записывает его выбор приложений, режим файрвола, режимы отдельных приложений и настройку *Показать системные приложения*.

| Параметр | Тип | |
|---|---|---|
| `profile` | string | Имя профиля, как оно показано в списке профилей. |
| `profile_id` | string | Внутренний идентификатор; проверяется первым и не зависит от переименований. Диалог **Автоматизация** профиля показывает готовую команду. |
| `force_enable` | boolean, необязательный | Включить файрвол после переключения, даже если он был выключен. |

Обязателен один из параметров `profile` или `profile_id`. Если ни один не найден, ничего не меняется, а всплывающее сообщение сообщает об этом.

```bash
adb shell am broadcast -a shizuwall.PROFILE -n com.arslan.shizuwall/.receivers.ProfileControlReceiver --es profile "Work"
```

## Отказ от ответственности

ShizuWall предоставляется **«как есть»**, без гарантий. Он использует расширенные системные разрешения, и разработчик не несёт ответственности за нестабильность системы, потерю данных или побочные эффекты блокировки сети. Всегда проверяйте, какие приложения вы блокируете.

**Официальные источники — только Google Play, F-Droid и этот репозиторий.** APK из других мест не связаны с проектом и используются на ваш риск. У ShizuWall нет официального сайта.

Разрешение `INTERNET` запрашивается только для сопряжения по беспроводной отладке (LibADB).

## Лицензия

[GPLv3](../LICENSE.md). Включённые данные сигнатур трекеров лицензируются отдельно по ODbL v1.0, см. [TRACKER_DATA_LICENSE.md](../TRACKER_DATA_LICENSE.md).

## Благодарности

- [Shizuku](https://github.com/RikkaApps/Shizuku): выполнение привилегированных команд.
- [LibADB](https://github.com/MuntashirAkon/libadb-android): отладка по Wi-Fi и подключение к демону.
- [Exodus Privacy](https://exodus-privacy.eu.org/): база сигнатур трекеров. ShizuWall включает её сокращённую копию и никогда не обращается к их API.
