<div align="center">
  <a href="https://play.google.com/store/apps/details?id=com.arslan.shizuwall">
    <img src="../app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png" alt="ShizuWall Icon" width="72" />
  </a>
  <h1>ShizuWall</h1>
  <strong>VPN 不要の Android ファイアウォール。</strong><br/>
  プライバシー第一・完全ローカル。Shizuku / ローカル ADB デーモン / Root で動作します。
</div>

<p align="center">
  <a href="../README.md">English</a> ·
  <a href="README.tr.md">Türkçe</a> ·
  <a href="README.de.md">Deutsch</a> ·
  <a href="README.it.md">Italiano</a> ·
  <a href="README.pt.md">Português</a> ·
  <a href="README.cs.md">Čeština</a> ·
  <a href="README.ru.md">Русский</a> ·
  <a href="README.ar.md">العربية</a> ·
  <a href="README.hi.md">हिन्दी</a> ·
  <a href="README.zh.md">中文</a> ·
  <b>日本語</b>
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


## 対応環境

Android 11 以上と、次のいずれかのバックエンド:

- **Shizuku**（フォークにも対応）
- **LibADB**: ワイヤレスデバッグでペアリング、パソコン不要
- **Root**

## ShizuWall を選ぶ理由

- **VPN 不要**: パケットの傍受も常駐トンネルもありません。
- **アプリ単位の制御**: 仕事用プロファイルやクローンのコピーも含め、アプリごとに通信をブロックします。
- **プライベート**: オフラインファースト。解析もテレメトリもありません。
- **クイックアクセス**: クイック設定タイル、ウィジェット、フローティングボタン。
- **自動化**: adb、Tasker、MacroDroid などからのブロードキャストで制御できます。

## スクリーンショット

<p align="center">
  <img src="../assets/screenshots/v4.6/1.png" alt="ShizuWall" width="30%" />
</p>

## 仕組み

ShizuWall は Android の connectivity **chain 3** を利用します。これはパッケージ単位でネットワークアクセスを制御するプラットフォームの仕組みです:

```bash
cmd connectivity set-chain3-enabled true                                # ファイアウォール ON
cmd connectivity set-package-networking-enabled false <package.name>   # ブロック
cmd connectivity set-package-networking-enabled true <package.name>    # ブロック解除
cmd connectivity set-chain3-enabled false                               # ファイアウォール OFF
```

これらのルールは再起動時に Android によって消去されます。

## 自動化

どのブロードキャストにも有効なバックエンドが必要です。`apps` はアプリキーの CSV リストです。パッケージ名だけの場合はユーザー 0、`150:com.example.app` のようにプレフィックス付きの場合はその仕事用プロファイルまたはクローン領域のコピーを指します。

### ファイアウォール: `shizuwall.CONTROL`

| Extra | 型 | |
|---|---|---|
| `state` | boolean、必須 | `true` = ブロック、`false` = ブロック解除 |
| `apps` | string、任意 | ブロックまたはブロック解除するアプリ。省略するとファイアウォール全体を切り替えます。 |

```bash
# ファイアウォールを ON にする（state false で OFF）
adb shell am broadcast -a shizuwall.CONTROL -n com.arslan.shizuwall/.receivers.FirewallControlReceiver --ez state true

# アプリとその仕事用プロファイルのコピーをブロックする
adb shell am broadcast -a shizuwall.CONTROL -n com.arslan.shizuwall/.receivers.FirewallControlReceiver --ez state true --es apps "com.example.app,150:com.example.app"
```

`apps` を付けるとアプリ内の選択も追従します。`state true` はアプリを選択し、ファイアウォールが OFF なら ON にします。`state false` は選択を解除し、ファイアウォールは ON のままです。ホワイトリストモードでは `apps` は許可リストを指すため、2 つの状態は逆になります。

### 新しいアプリ: `shizuwall.APP_INSTALLED`

新しくインストールされたアプリを通知し、ShizuWall 自身がインストールを検出した場合と同じように新規アプリのポリシーと通知を適用させます。ShizuWall は自身のプロファイル内のインストールしか検出できないため、他のプロファイルはこの方法でカバーします。

```bash
adb shell am broadcast -a shizuwall.APP_INSTALLED -n com.arslan.shizuwall/.receivers.AppInstalledReceiver --es apps "com.example.newapp"
```

ファイアウォールがオフの間はルールは適用されません。`INTERNET` 権限のないアプリは無視されます。

### プロファイル: `shizuwall.PROFILE`

保存済みのプロファイルを適用します。アプリの選択、ファイアウォールモード、アプリごとのモード、*システムアプリを表示* の設定を書き込みます。

| Extra | 型 | |
|---|---|---|
| `profile` | string | プロファイル名。プロファイル一覧に表示されるものです。 |
| `profile_id` | string | 内部 ID。先に確認され、名前を変更しても影響を受けません。プロファイルの **自動化** ダイアログにそのまま使えるコマンドが表示されます。 |
| `force_enable` | boolean、任意 | OFF だった場合でも、切り替え後にファイアウォールを ON にします。 |

`profile` と `profile_id` のいずれかが必須です。どちらも一致しない場合は何も変更されず、トーストで通知されます。

```bash
adb shell am broadcast -a shizuwall.PROFILE -n com.arslan.shizuwall/.receivers.ProfileControlReceiver --es profile "Work"
```

## 免責事項

ShizuWall は**現状有姿**で、いかなる保証もなく提供されます。高度なシステム権限を利用するため、システムの不安定化、データ損失、ネットワーク遮断による副作用について開発者は責任を負いません。ブロックするアプリは必ず確認してください。

**公式の入手先は Google Play、F-Droid、およびこのリポジトリのみです。** それ以外の APK は無関係であり、自己責任でご利用ください。ShizuWall には公式ウェブサイトはありません。

`INTERNET` 権限はワイヤレスデバッグのペアリング（LibADB）にのみ使用します。

## ライセンス

[GPLv3](../LICENSE.md)。同梱のトラッカーシグネチャデータは別途 ODbL v1.0 でライセンスされています。[TRACKER_DATA_LICENSE.md](../TRACKER_DATA_LICENSE.md) を参照してください。

## クレジット

- [Shizuku](https://github.com/RikkaApps/Shizuku): 特権コマンドの実行。
- [LibADB](https://github.com/MuntashirAkon/libadb-android): ワイヤレスデバッグとデーモン接続。
- [Exodus Privacy](https://exodus-privacy.eu.org/): トラッカーシグネチャデータベース。ShizuWall は縮小版を同梱しており、彼らの API には一切接続しません。
