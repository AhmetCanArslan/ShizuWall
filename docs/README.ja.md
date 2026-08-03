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


## ShizuWall を選ぶ理由

- **VPN 不要**: パケットの傍受や、常時 VPN トンネルによる副作用を避けられます。
- **アプリごとのネットワーク制御**: Android の `connectivity` chain-3 制御でアプリの通信をオン・オフします。
- **設計段階からプライバシー重視**: オフライン優先。解析・テレメトリ・トラッキングは一切ありません。
- **自動化に対応**: スクリプトやタスク自動化のための `adb broadcast` コマンドに対応しています。
- **操作方法**: ShizuWall はファイアウォールを操作する 3 つの便利な方法（クイック設定タイル、ウィジェット、フローティングボタン）を用意しています。

## スクリーンショット

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

## 動作要件

- Android 11 (API 30) 以上
- いずれかの制御バックエンド: Shizuku、ローカル ADB デーモン、または root 権限

## 制御バックエンド

ShizuWall はファイアウォールコマンドの実行方法を 3 つ用意しています。

| 方法 | 説明 | セットアップ |
|--------|---------|---------|
| **Shizuku** | システムサービスと通信する安全な API。Shizuku アプリが必要です。フォーク版も利用できます。 | Shizuku アプリをインストールして設定し、権限を付与する |
| **Root** | root 権限を直接使用します。 | 標準的な方法で端末を root 化する |
| **LibADB (LADB)** | 端末に内蔵された「ワイヤレスデバッグ」機能を使い、USB 接続したパソコンのように振る舞います。これによりパソコン・root・Shizuku などの追加アプリなしで高度なシステム変更が行えます。 | 開発者向けオプションでワイヤレスデバッグを有効にしてペア設定する（ガイドはアプリ内にあります） |

## 仕組み

ShizuWall は Android の **Chain 3**（接続チェーン）を使ってアプリごとの通信を制御します。Shizuku またはローカルデーモン経由で実行されるプラットフォームコマンドは次のとおりです。

### ADB Chain 3 コマンド

```bash
# ファイアウォール機構を有効化
cmd connectivity set-chain3-enabled true

# 特定のアプリをブロック
cmd connectivity set-package-networking-enabled false <package.name>

# 特定のアプリのブロックを解除
cmd connectivity set-package-networking-enabled true <package.name>

# ファイアウォール機構を無効化
cmd connectivity set-chain3-enabled false
```

**Chain 3** は、パッケージ単位のネットワークアクセスをシステムレベルで捕捉・制御する Android プラットフォームの仕組みで、きめ細かなファイアウォール制御を可能にします。

## 自動化（ADB ブロードキャスト）

スクリプトや自動化ツールから ShizuWall を操作できます。

**アクション**: `shizuwall.CONTROL`  
**コンポーネント**: `com.arslan.shizuwall/.receivers.FirewallControlReceiver`  

**エクストラ**

- `state` （boolean、必須）: `true` = 有効化、`false` = 無効化
- `apps` （文字列、任意）: カンマ区切りのパッケージ一覧。省略した場合、ShizuWall は保存済みの選択アプリを使用します。

### 例

```bash
# 保存済みの選択アプリに対してファイアウォールを有効化
adb shell am broadcast -a shizuwall.CONTROL -n com.arslan.shizuwall/.receivers.FirewallControlReceiver --ez state true

# 保存済みの選択アプリに対してファイアウォールを無効化
adb shell am broadcast -a shizuwall.CONTROL -n com.arslan.shizuwall/.receivers.FirewallControlReceiver --ez state false

# 特定のパッケージに対してファイアウォールを有効化
adb shell am broadcast -a shizuwall.CONTROL -n com.arslan.shizuwall/.receivers.FirewallControlReceiver --ez state true --es apps "com.example.app1,com.example.app2"

# 特定のパッケージに対してファイアウォールを無効化
adb shell am broadcast -a shizuwall.CONTROL -n com.arslan.shizuwall/.receivers.FirewallControlReceiver --ez state false --es apps "com.example.app1,com.example.app2"
```

> ブロードキャストを成功させるには、いずれかの制御バックエンド（Shizuku、ローカル ADB デーモン、Root）が有効である必要があります。

## 注意事項と制限

- Android プラットフォームの仕様により、ファイアウォールのルールは再起動時に消去されます。
- 端末を再起動すると、ShizuWall が適用した有効なネットワークブロックはすべてリセットされます。
- 本アプリが `android.permission.INTERNET` を要求するのは、ワイヤレスデバッグのペア設定（LibADB ローカルデーモン接続）のためだけです。

## ビルド（開発者向け）

### アプリ

```bash
./gradlew assembleRelease
```

### デーモン

端末側デーモン（`SystemDaemon.java`）は、通常の Gradle ビルドの一部として `compileDaemonDex` タスクにより自動的に DEX（`daemon.bin`）へコンパイルされます。Android SDK の build-tools に含まれる `javac` と `d8` を用いてソースからコンパイルし、アセットとして同梱します。リポジトリにビルド済みバイナリは含まれないため、再現可能ビルドのサーバー（F-Droid など）は自身でこれを生成できます。

手動の作業は不要で、`./gradlew assembleRelease` でビルドされます。タスク単体で実行することもできます。

```bash
./gradlew :app:compileDaemonDex
```

## セキュリティおよび配布に関する免責事項

ShizuWall はいかなる保証もなく**「現状のまま」**提供されます。

本アプリを使用することで、Shizuku/ADB/Root といった高度なシステム権限に依存していることを理解し、関連するすべてのリスクを受け入れたものとみなされます。システムの不安定化、データの消失、サービスの中断、通信ブロックによる副作用などの損害について、開発者は責任を負いません。

**公式配布とサードパーティのリスク**
ShizuWall の開発者および貢献者は、サードパーティから入手した APK ファイルのダウンロード・インストール・使用に起因する損害、セキュリティ侵害、マルウェア感染、データ損失について、一切の責任を負いません。ShizuWall は Google Play ストア、公式 GitHub リポジトリ、F-Droid のみを通じて公式に開発・配布されています。ShizuWall は公式ウェブサイトを所有・運営・承認していません。ShizuWall の公式ダウンロードを提供すると称するウェブサイトやサードパーティのプラットフォームは、いずれも本プロジェクトとは無関係かつ非公認です。公式チャネル以外で本アプリを入手する場合は、利用者自身の責任となります。

**ShizuWall はオープンソースアプリケーションです。開発者は本アプリの使用について、金銭的・道義的・法的な責任を一切負いません。すべての責任は利用者にあります。**

ブロックするアプリを必ず確認してください。

## ライセンス

**GNU General Public License v3.0 (GPLv3)** の下でライセンスされています。[LICENSE.md](../LICENSE.md) を参照してください。

同梱のトラッカー署名データはコードではなく、独自のライセンス（ODbL v1.0）が適用されます。[TRACKER_DATA_LICENSE.md](../TRACKER_DATA_LICENSE.md) を参照してください。

## サポート

- ⭐ プロジェクトに Star を付ける: [GitHub Stars](https://github.com/AhmetCanArslan/ShizuWall/stargazers)
- ☕ 寄付する: [Buy Me a Coffee](https://buymeacoffee.com/ahmetcanarslan)
- ⬇️ ダウンロード: [Google Play ストア](https://play.google.com/store/apps/details?id=com.arslan.shizuwall)

## クレジット

- [Shizuku](https://github.com/RikkaApps/Shizuku) — 特権コマンドの実行フローを可能にする API。
- [LibADB](https://github.com/MuntashirAkon/libadb-android) — ワイヤレスデバッグとデーモン接続のサポート。
- [Exodus Privacy](https://exodus-privacy.eu.org/) — アプリ内のトラッカー SDK を検出するために使用するトラッカー署名データベース。[ODbL v1.0](https://opendatacommons.org/licenses/odbl/1-0/) でライセンスされています。ShizuWall は縮小版のコピーを同梱しており、彼らの API に接続することはありません。[TRACKER_DATA_LICENSE.md](../TRACKER_DATA_LICENSE.md) を参照してください。
