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


## Почему ShizuWall

- **Без VPN**: Не перехватывает пакеты и избавляет от побочных эффектов постоянного VPN-туннеля.
- **Управление сетью для каждого приложения**: Включает и отключает сеть приложений через средства chain-3 в `connectivity` Android.
- **Приватность по умолчанию**: Работает офлайн, без аналитики, телеметрии и слежки.
- **Готов к автоматизации**: Поддерживает команды `adb broadcast` для скриптов и автоматизации задач.
- **Способы управления**: ShizuWall предлагает три удобных способа управлять файрволом: плитка в быстрых настройках, виджет и плавающая кнопка файрвола.

## Скриншоты

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

## Требования

- Android 11 (API 30) или новее
- Один из бэкендов управления: Shizuku, локальный ADB-демон или root-доступ

## Бэкенды управления

ShizuWall поддерживает три способа выполнения команд файрвола:

| Способ | Описание | Настройка |
|--------|---------|---------|
| **Shizuku** | Безопасный API, взаимодействующий с системными службами. Требуется приложение Shizuku. Форки поддерживаются. | Установите и настройте приложение Shizuku, выдайте разрешения |
| **Root** | Прямой root-доступ. | Получите root стандартными способами |
| **LibADB (LADB)** | Использует встроенную функцию «Беспроводная отладка», чтобы телефон вёл себя как компьютер, подключённый по USB. Это позволяет приложению вносить сложные системные изменения без компьютера, root-прав и дополнительных приложений вроде Shizuku. | Включите беспроводную отладку и выполните сопряжение в параметрах разработчика (руководство есть в приложении) |

## Как это работает

ShizuWall использует **Chain 3** Android (цепочку подключений) для управления сетью каждого приложения. Через Shizuku или локальный демон выполняются такие системные команды:

### Команды ADB Chain 3

```bash
# Включить механизм файрвола
cmd connectivity set-chain3-enabled true

# Заблокировать конкретное приложение
cmd connectivity set-package-networking-enabled false <package.name>

# Разблокировать конкретное приложение
cmd connectivity set-package-networking-enabled true <package.name>

# Отключить механизм файрвола
cmd connectivity set-chain3-enabled false
```

**Chain 3** — механизм платформы Android, который перехватывает и контролирует сетевой доступ каждого пакета на системном уровне, обеспечивая точное управление файрволом.

## Автоматизация (ADB broadcast)

Вы можете управлять ShizuWall из скриптов и средств автоматизации.

**Действие**: `shizuwall.CONTROL`  
**Компонент**: `com.arslan.shizuwall/.receivers.FirewallControlReceiver`  

**Дополнительные параметры**

- `state` (boolean, обязательный): `true` = включить, `false` = выключить
- `apps` (строка, необязательный): список пакетов через запятую. Если не указан, ShizuWall использует сохранённые выбранные приложения.

### Примеры

```bash
# Включить файрвол для сохранённых выбранных приложений
adb shell am broadcast -a shizuwall.CONTROL -n com.arslan.shizuwall/.receivers.FirewallControlReceiver --ez state true

# Выключить файрвол для сохранённых выбранных приложений
adb shell am broadcast -a shizuwall.CONTROL -n com.arslan.shizuwall/.receivers.FirewallControlReceiver --ez state false

# Включить файрвол для конкретных пакетов
adb shell am broadcast -a shizuwall.CONTROL -n com.arslan.shizuwall/.receivers.FirewallControlReceiver --ez state true --es apps "com.example.app1,com.example.app2"

# Выключить файрвол для конкретных пакетов
adb shell am broadcast -a shizuwall.CONTROL -n com.arslan.shizuwall/.receivers.FirewallControlReceiver --ez state false --es apps "com.example.app1,com.example.app2"
```

> Чтобы броадкасты срабатывали, должен быть активен один из бэкендов управления (Shizuku, локальный ADB-демон или root).

## Примечания и ограничения

- Правила файрвола сбрасываются при перезагрузке — так устроена платформа Android.
- Перезагрузка устройства снимает все активные блокировки сети, установленные ShizuWall.
- Приложение запрашивает `android.permission.INTERNET` только для сопряжения по беспроводной отладке (подключение к локальному демону LibADB).

## Сборка (для разработчиков)

### Приложение

```bash
./gradlew assembleRelease
```

### Демон

Демон, работающий на устройстве (`SystemDaemon.java`), автоматически компилируется в DEX (`daemon.bin`) в рамках обычной сборки Gradle задачей `compileDaemonDex`. Он собирается из исходников с помощью `javac` и `d8` из build-tools Android SDK и упаковывается как ресурс — готового бинарника в репозитории нет, поэтому серверы воспроизводимых сборок (например, F-Droid) создают его сами.

Никаких ручных действий не требуется; `./gradlew assembleRelease` собирает и его. Задачу можно запустить и отдельно:

```bash
./gradlew :app:compileDaemonDex
```

## Отказ от ответственности: безопасность и распространение

ShizuWall предоставляется **«как есть»**, без каких-либо гарантий.

Используя это приложение, вы подтверждаете, что оно опирается на расширенные системные разрешения (Shizuku/ADB/root), и принимаете все связанные риски. Разработчик не несёт ответственности за ущерб, включая нестабильность системы, потерю данных, перебои в работе служб или последствия блокировки сети.

**Официальное распространение и риски третьих сторон**
Разработчики и участники проекта ShizuWall не несут никакой ответственности за ущерб, нарушения безопасности, заражение вредоносным ПО или потерю данных, возникшие в результате загрузки, установки или использования APK-файлов, полученных из сторонних источников. ShizuWall официально разрабатывается и распространяется исключительно через Google Play Store, официальный репозиторий на GitHub и F-Droid. ShizuWall не владеет, не управляет и не поддерживает никакой официальный веб-сайт. Любой сайт или сторонняя платформа, утверждающие, что предлагают официальные загрузки ShizuWall, не имеют к проекту отношения и не авторизованы. Пользователи, получающие приложение вне официальных каналов, делают это строго на свой риск.

**ShizuWall — приложение с открытым исходным кодом. Разработчик не несёт финансовой, моральной или юридической ответственности за использование этого приложения. Вся ответственность лежит на пользователе.**

Всегда проверяйте, какие приложения вы блокируете.

## Лицензия

Распространяется по лицензии **GNU General Public License v3.0 (GPLv3)**. См. [LICENSE.md](../LICENSE.md).

Входящие в состав данные сигнатур трекеров не являются кодом и имеют собственную лицензию (ODbL v1.0). См. [TRACKER_DATA_LICENSE.md](../TRACKER_DATA_LICENSE.md).

## Поддержать

- ⭐ Поставьте звезду проекту: [GitHub Stars](https://github.com/AhmetCanArslan/ShizuWall/stargazers)
- ☕ Сделать пожертвование: [Buy Me a Coffee](https://buymeacoffee.com/ahmetcanarslan)
- ⬇️ Скачать: [Google Play Store](https://play.google.com/store/apps/details?id=com.arslan.shizuwall)

## Благодарности

- [Shizuku](https://github.com/RikkaApps/Shizuku) — API, обеспечивающий выполнение привилегированных команд.
- [LibADB](https://github.com/MuntashirAkon/libadb-android) — Поддержка беспроводной отладки и подключения к демону.
- [Exodus Privacy](https://exodus-privacy.eu.org/) — База сигнатур трекеров, используемая для обнаружения трекерных SDK внутри приложений, под лицензией [ODbL v1.0](https://opendatacommons.org/licenses/odbl/1-0/). ShizuWall включает сокращённую копию и никогда не обращается к их API. См. [TRACKER_DATA_LICENSE.md](../TRACKER_DATA_LICENSE.md).
