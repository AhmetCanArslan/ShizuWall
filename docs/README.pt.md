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


## Funciona com

Android 11+ e um destes backends:

- **Shizuku** (forks são suportados)
- **LibADB**: pareia por Depuração sem fio, sem precisar de computador
- **Root**

## Por que o ShizuWall

- **Sem VPN**: sem interceptação de pacotes, sem túnel persistente.
- **Controle por app**: bloqueia a rede por app, incluindo cópias no perfil de trabalho e clones.
- **Privado**: offline-first, sem analytics, sem telemetria.
- **Acesso rápido**: bloco das Configurações rápidas, widget e botão flutuante.
- **Automação**: controlável por broadcasts a partir do adb, Tasker, MacroDroid e similares.

## Captura de tela

<p align="center">
  <img src="../assets/screenshots/v4.6/1.png" alt="ShizuWall" width="30%" />
</p>

## Como funciona

O ShizuWall usa a **chain 3** de connectivity do Android, um mecanismo da plataforma que controla o acesso à rede por pacote:

```bash
cmd connectivity set-chain3-enabled true                                # firewall ligado
cmd connectivity set-package-networking-enabled false <package.name>   # bloquear
cmd connectivity set-package-networking-enabled true <package.name>    # desbloquear
cmd connectivity set-chain3-enabled false                               # firewall desligado
```

O Android apaga essas regras ao reiniciar.

## Automação

Todo broadcast precisa de um backend ativo. `apps` é uma lista CSV de chaves de app: um nome de pacote simples aponta para o usuário 0, e uma chave com prefixo como `150:com.example.app` aponta para a cópia naquele perfil de trabalho ou espaço de clone.

### Firewall: `shizuwall.CONTROL`

| Extra | Tipo | |
|---|---|---|
| `state` | boolean, obrigatório | `true` = bloquear, `false` = desbloquear |
| `apps` | string, opcional | Apps a bloquear ou desbloquear. Sem ele, o broadcast liga ou desliga o firewall inteiro. |

```bash
# Liga o firewall (state false desliga)
adb shell am broadcast -a shizuwall.CONTROL -n com.arslan.shizuwall/.receivers.FirewallControlReceiver --ez state true

# Bloqueia um app e sua cópia no perfil de trabalho
adb shell am broadcast -a shizuwall.CONTROL -n com.arslan.shizuwall/.receivers.FirewallControlReceiver --ez state true --es apps "com.example.app,150:com.example.app"
```

- Com `apps`, a seleção no app acompanha: `state true` seleciona os apps e liga o firewall se estava desligado; `state false` remove a seleção e deixa o firewall ligado. No modo whitelist, `apps` se refere à lista de permitidos, então os dois estados se invertem.
- Com **Mostrar apps de outros perfis** desativado, um nome de pacote simples também vale para seus clones. Com a opção ativada, cada perfil é endereçado apenas pelo seu prefixo.

### Novo app: `shizuwall.APP_INSTALLED`

Informa um app recém-instalado, para que a política e a notificação de novos apps sejam aplicadas como se o próprio ShizuWall tivesse detectado a instalação. O ShizuWall só vê instalações no próprio perfil; é assim que se cobrem os demais.

```bash
adb shell am broadcast -a shizuwall.APP_INSTALLED -n com.arslan.shizuwall/.receivers.AppInstalledReceiver --es apps "com.example.newapp"
```

Nenhuma regra é aplicada enquanto o firewall está desligado. Apps sem a permissão `INTERNET` são ignorados.

### Perfil: `shizuwall.PROFILE`

Ativa um perfil salvo: grava a seleção de apps, o modo do firewall, os modos por app e a configuração *Mostrar apps do sistema*.

| Extra | Tipo | |
|---|---|---|
| `profile` | string | Nome do perfil, como aparece na lista de perfis. |
| `profile_id` | string | ID interno; é verificado primeiro e não muda com renomeações. O diálogo **Automação** do perfil mostra o comando pronto. |
| `force_enable` | boolean, opcional | Liga o firewall após a troca mesmo que estivesse desligado. |

É obrigatório informar `profile` ou `profile_id`. Se nenhum corresponder, nada muda e um toast avisa.

```bash
adb shell am broadcast -a shizuwall.PROFILE -n com.arslan.shizuwall/.receivers.ProfileControlReceiver --es profile "Work"
```

## Aviso legal

O ShizuWall é fornecido **"como está"**, sem garantia. Ele depende de permissões avançadas do sistema, e o desenvolvedor não se responsabiliza por instabilidade do sistema, perda de dados ou efeitos colaterais do bloqueio de rede. Sempre verifique quais apps você bloqueia.

**As fontes oficiais são apenas Google Play, F-Droid e este repositório.** APKs de qualquer outro lugar não têm vínculo com o projeto e são usados por sua conta e risco.

A permissão `INTERNET` é solicitada apenas para o pareamento da depuração sem fio (LibADB).

## Licença

[GPLv3](../LICENSE.md). Os dados de assinaturas de rastreadores incluídos são licenciados separadamente sob ODbL v1.0, veja [TRACKER_DATA_LICENSE.md](../TRACKER_DATA_LICENSE.md).

## Créditos

- [Shizuku](https://github.com/RikkaApps/Shizuku): execução de comandos privilegiados.
- [LibADB](https://github.com/MuntashirAkon/libadb-android): depuração sem fio e conexão com o daemon.
- [Exodus Privacy](https://exodus-privacy.eu.org/): banco de dados de assinaturas de rastreadores. O ShizuWall inclui uma cópia reduzida e nunca acessa a API deles.
