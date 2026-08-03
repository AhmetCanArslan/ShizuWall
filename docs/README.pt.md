<div align="center">
  <a href="https://play.google.com/store/apps/details?id=com.arslan.shizuwall">
    <img src="../app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png" alt="ShizuWall Icon" width="72" />
  </a>
  <h1>ShizuWall</h1>
  <strong>Firewall para Android sem VPN.</strong><br/>
  Privacidade em primeiro lugar, apenas local, com Shizuku / daemon ADB local / Root.
</div>

<p align="center">
  <a href="../README.md">English</a> ·
  <a href="README.tr.md">Türkçe</a> ·
  <a href="README.de.md">Deutsch</a> ·
  <a href="README.it.md">Italiano</a> ·
  <b>Português</b> ·
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


## Por que o ShizuWall

- **Sem VPN**: Evita a interceptação de pacotes e os efeitos colaterais de um túnel VPN permanente.
- **Controle de rede por app**: Liga e desliga a rede dos apps pelos controles chain-3 do `connectivity` do Android.
- **Privacidade por design**: Offline em primeiro lugar, sem análises, sem telemetria, sem rastreamento.
- **Pronto para automação**: Suporta comandos `adb broadcast` para scripts e automação de tarefas.
- **Formas de controle**: O ShizuWall oferece três formas práticas de controlar o firewall: bloco de Configurações rápidas, widget e botão flutuante do firewall.

## Capturas de tela

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

## Requisitos

- Android 11 (API 30) ou superior
- Um backend de controle: Shizuku, daemon ADB local ou acesso root

## Backends de controle

O ShizuWall suporta três métodos para executar comandos de firewall:

| Método | Descrição | Configuração |
|--------|---------|---------|
| **Shizuku** | API segura que se comunica com os serviços do sistema. Requer o app Shizuku. Forks são suportados. | Instale e configure o app Shizuku e conceda as permissões |
| **Root** | Acesso root direto. | Faça root no aparelho pelos métodos padrão |
| **LibADB (LADB)** | Usa o recurso interno "Depuração sem fio" do seu telefone para agir como um computador conectado via USB. Isso permite ao app fazer alterações avançadas no sistema sem precisar de computador, root ou apps extras como o Shizuku. | Ative a depuração sem fio e faça o pareamento nas Opções do desenvolvedor (guia dentro do app) |

## Como funciona

O ShizuWall usa a **Chain 3** do Android (cadeia de conectividade) para controlar a rede de cada app. Estes são os comandos da plataforma executados via Shizuku ou pelo daemon local:

### Comandos ADB Chain 3

```bash
# Ativar a estrutura do firewall
cmd connectivity set-chain3-enabled true

# Bloquear um app específico
cmd connectivity set-package-networking-enabled false <package.name>

# Desbloquear um app específico
cmd connectivity set-package-networking-enabled true <package.name>

# Desativar a estrutura do firewall
cmd connectivity set-chain3-enabled false
```

A **Chain 3** é um mecanismo da plataforma Android que intercepta e controla o acesso à rede por pacote no nível do sistema, permitindo um controle de firewall refinado.

## Automação (broadcast ADB)

Você pode controlar o ShizuWall a partir de scripts e ferramentas de automação.

**Ação**: `shizuwall.CONTROL`  
**Componente**: `com.arslan.shizuwall/.receivers.FirewallControlReceiver`  

**Extras**

- `state` (booleano, obrigatório): `true` = ativar, `false` = desativar
- `apps` (string, opcional): lista de pacotes em CSV. Se omitido, o ShizuWall usa os apps selecionados salvos.

### Exemplos

```bash
# Ativar o firewall para os apps selecionados salvos
adb shell am broadcast -a shizuwall.CONTROL -n com.arslan.shizuwall/.receivers.FirewallControlReceiver --ez state true

# Desativar o firewall para os apps selecionados salvos
adb shell am broadcast -a shizuwall.CONTROL -n com.arslan.shizuwall/.receivers.FirewallControlReceiver --ez state false

# Ativar o firewall para pacotes específicos
adb shell am broadcast -a shizuwall.CONTROL -n com.arslan.shizuwall/.receivers.FirewallControlReceiver --ez state true --es apps "com.example.app1,com.example.app2"

# Desativar o firewall para pacotes específicos
adb shell am broadcast -a shizuwall.CONTROL -n com.arslan.shizuwall/.receivers.FirewallControlReceiver --ez state false --es apps "com.example.app1,com.example.app2"
```

> Um dos backends de controle (Shizuku, daemon ADB local ou Root) precisa estar ativo para que os broadcasts funcionem.

## Notas e limitações

- As regras do firewall são apagadas ao reiniciar, por comportamento da plataforma Android.
- Reiniciar o aparelho redefine todos os bloqueios de rede ativos aplicados pelo ShizuWall.
- O app solicita `android.permission.INTERNET` apenas para o pareamento da depuração sem fio (conexão com o daemon local LibADB).

## Compilação (desenvolvedores)

### App

```bash
./gradlew assembleRelease
```

### Daemon

O daemon do dispositivo (`SystemDaemon.java`) é compilado para um DEX (`daemon.bin`) automaticamente como parte da compilação normal do Gradle, pela tarefa `compileDaemonDex`. Ele é compilado a partir do código-fonte com `javac` e `d8` das build-tools do SDK do Android e empacotado como asset — não há binário pré-compilado no repositório, para que servidores de compilação reproduzível (por exemplo, o F-Droid) o produzam por conta própria.

Nenhum passo manual é necessário; `./gradlew assembleRelease` já o compila. A tarefa também pode ser executada isoladamente:

```bash
./gradlew :app:compileDaemonDex
```

## Aviso de segurança e distribuição

O ShizuWall é fornecido **"no estado em que se encontra"**, sem garantia de qualquer tipo.

Ao usar este app, você reconhece que ele depende de permissões avançadas do sistema (Shizuku/ADB/Root) e aceita todos os riscos relacionados. O desenvolvedor não se responsabiliza por danos como instabilidade do sistema, perda de dados, interrupção de serviços ou efeitos colaterais do bloqueio de rede.

**Distribuição oficial e riscos de terceiros**
Os desenvolvedores e colaboradores do ShizuWall não assumem qualquer responsabilidade por danos, violações de segurança, infecções por malware ou perdas de dados decorrentes do download, da instalação ou do uso de arquivos APK obtidos de fontes de terceiros. O ShizuWall é desenvolvido e distribuído oficialmente apenas pela Google Play Store, por seu repositório oficial no GitHub e pelo F-Droid. O ShizuWall não possui, opera nem endossa nenhum site oficial. Qualquer site ou plataforma de terceiros que afirme oferecer downloads oficiais do ShizuWall é totalmente independente e não autorizado. Usuários que obtiverem o aplicativo fora dos canais oficiais o fazem por sua própria conta e risco.

**O ShizuWall é um aplicativo de código aberto. O desenvolvedor não aceita nenhuma responsabilidade financeira, moral ou legal pelo uso deste aplicativo. Toda a responsabilidade é do usuário.**

Sempre verifique quais apps você está bloqueando.

## Licença

Licenciado sob a **GNU General Public License v3.0 (GPLv3)**. Consulte [LICENSE.md](../LICENSE.md).

Os dados de assinaturas de rastreadores incluídos não são código e têm licença própria (ODbL v1.0). Consulte [TRACKER_DATA_LICENSE.md](../TRACKER_DATA_LICENSE.md).

## Apoio

- ⭐ Dê uma estrela ao projeto: [GitHub Stars](https://github.com/AhmetCanArslan/ShizuWall/stargazers)
- ☕ Doe: [Buy Me a Coffee](https://buymeacoffee.com/ahmetcanarslan)
- ⬇️ Baixe: [Google Play Store](https://play.google.com/store/apps/details?id=com.arslan.shizuwall)

## Créditos

- [Shizuku](https://github.com/RikkaApps/Shizuku) — API que viabiliza o fluxo de execução de comandos privilegiados.
- [LibADB](https://github.com/MuntashirAkon/libadb-android) — Suporte a depuração sem fio e conexão com o daemon.
- [Exodus Privacy](https://exodus-privacy.eu.org/) — Banco de assinaturas de rastreadores usado para detectar SDKs de rastreamento dentro dos apps, licenciado sob a [ODbL v1.0](https://opendatacommons.org/licenses/odbl/1-0/). O ShizuWall inclui uma cópia reduzida e nunca contata a API deles. Consulte [TRACKER_DATA_LICENSE.md](../TRACKER_DATA_LICENSE.md).
