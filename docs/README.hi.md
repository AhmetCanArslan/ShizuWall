<div align="center">
  <a href="https://play.google.com/store/apps/details?id=com.arslan.shizuwall">
    <img src="../app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png" alt="ShizuWall Icon" width="72" />
  </a>
  <h1>ShizuWall</h1>
  <strong>बिना VPN वाला Android फ़ायरवॉल।</strong><br/>
  गोपनीयता सर्वोपरि, पूरी तरह स्थानीय; Shizuku / स्थानीय ADB डेमॉन / रूट से संचालित।
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
  <b>हिन्दी</b> ·
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


## इनके साथ काम करता है

Android 11+ और इनमें से कोई एक बैकएंड:

- **Shizuku** (फ़ोर्क भी समर्थित)
- **LibADB**: वायरलेस डिबगिंग से पेयर होता है, कंप्यूटर की ज़रूरत नहीं
- **Root**

## ShizuWall क्यों

- **VPN नहीं**: न पैकेट इंटरसेप्शन, न स्थायी टनल।
- **प्रति-ऐप नियंत्रण**: हर ऐप का नेटवर्क अलग से ब्लॉक करता है, वर्क प्रोफ़ाइल और क्लोन कॉपी सहित।
- **निजी**: ऑफ़लाइन-फ़र्स्ट, न एनालिटिक्स, न टेलीमेट्री।
- **त्वरित पहुँच**: क्विक सेटिंग्स टाइल, विजेट और फ़्लोटिंग बटन।
- **ऑटोमेशन**: adb, Tasker, MacroDroid आदि से भेजे गए ब्रॉडकास्ट से नियंत्रित किया जा सकता है।

## स्क्रीनशॉट

<p align="center">
  <img src="../assets/screenshots/v4.6/1.png" alt="ShizuWall" width="30%" />
</p>

## यह कैसे काम करता है

ShizuWall Android की connectivity **chain 3** का उपयोग करता है, जो एक प्लेटफ़ॉर्म मैकेनिज़्म है और प्रति-पैकेज नेटवर्क एक्सेस नियंत्रित करता है:

```bash
cmd connectivity set-chain3-enabled true                                # फ़ायरवॉल चालू
cmd connectivity set-package-networking-enabled false <package.name>   # ब्लॉक
cmd connectivity set-package-networking-enabled true <package.name>    # अनब्लॉक
cmd connectivity set-chain3-enabled false                               # फ़ायरवॉल बंद
```

Android रीबूट पर ये नियम हटा देता है।

## ऑटोमेशन

हर ब्रॉडकास्ट के लिए एक सक्रिय बैकएंड चाहिए। `apps` ऐप कुंजियों की CSV सूची है: सादा पैकेज नाम यूज़र 0 को लक्षित करता है, और `150:com.example.app` जैसी प्रीफ़िक्स वाली कुंजी उस वर्क प्रोफ़ाइल या क्लोन स्पेस की कॉपी को।

### फ़ायरवॉल: `shizuwall.CONTROL`

| एक्स्ट्रा | प्रकार | |
|---|---|---|
| `state` | boolean, आवश्यक | `true` = ब्लॉक, `false` = अनब्लॉक |
| `apps` | string, वैकल्पिक | ब्लॉक या अनब्लॉक किए जाने वाले ऐप्स। इसके बिना ब्रॉडकास्ट पूरा फ़ायरवॉल चालू/बंद करता है। |

```bash
# फ़ायरवॉल चालू करें (state false इसे बंद करता है)
adb shell am broadcast -a shizuwall.CONTROL -n com.arslan.shizuwall/.receivers.FirewallControlReceiver --ez state true

# एक ऐप और उसकी वर्क प्रोफ़ाइल कॉपी को ब्लॉक करें
adb shell am broadcast -a shizuwall.CONTROL -n com.arslan.shizuwall/.receivers.FirewallControlReceiver --ez state true --es apps "com.example.app,150:com.example.app"
```

`apps` के साथ ऐप के अंदर का चयन भी बदलता है: `state true` ऐप्स को चुनता है और फ़ायरवॉल बंद हो तो उसे चालू करता है; `state false` उन्हें चयन से हटाता है और फ़ायरवॉल चालू रहने देता है। व्हाइटलिस्ट मोड में `apps` अनुमति सूची को संबोधित करता है, इसलिए दोनों स्थितियाँ उलट जाती हैं।

### नया ऐप: `shizuwall.APP_INSTALLED`

नए इंस्टॉल हुए ऐप की सूचना देता है, ताकि नए ऐप की नीति और सूचना उस पर वैसे ही लागू हो जैसे ShizuWall ने खुद इंस्टॉल पकड़ा हो। ShizuWall केवल अपनी प्रोफ़ाइल के इंस्टॉल देखता है; बाकी प्रोफ़ाइलों को कवर करने का यही तरीका है।

```bash
adb shell am broadcast -a shizuwall.APP_INSTALLED -n com.arslan.shizuwall/.receivers.AppInstalledReceiver --es apps "com.example.newapp"
```

फ़ायरवॉल बंद होने पर कोई नियम लागू नहीं होता। `INTERNET` अनुमति के बिना वाले ऐप्स को अनदेखा किया जाता है।

### प्रोफ़ाइल: `shizuwall.PROFILE`

सहेजी गई प्रोफ़ाइल सक्रिय करता है: उसका ऐप चयन, फ़ायरवॉल मोड, प्रति-ऐप मोड और *सिस्टम ऐप्स दिखाएँ* सेटिंग लिखता है।

| एक्स्ट्रा | प्रकार | |
|---|---|---|
| `profile` | string | प्रोफ़ाइल का नाम, जैसा प्रोफ़ाइल शीट में दिखता है। |
| `profile_id` | string | आंतरिक id; पहले इसे जाँचा जाता है और नाम बदलने का इस पर असर नहीं पड़ता। प्रोफ़ाइल का **ऑटोमेशन** डायलॉग तैयार कमांड दिखाता है। |
| `force_enable` | boolean, वैकल्पिक | स्विच के बाद फ़ायरवॉल चालू करें, भले ही वह बंद था। |

`profile` या `profile_id` में से एक आवश्यक है। कोई भी मेल न खाए तो कुछ नहीं बदलता और एक टोस्ट इसकी सूचना देता है।

```bash
adb shell am broadcast -a shizuwall.PROFILE -n com.arslan.shizuwall/.receivers.ProfileControlReceiver --es profile "Work"
```

## अस्वीकरण

ShizuWall **"जैसा है"** के आधार पर, बिना किसी वारंटी के उपलब्ध है। यह उन्नत सिस्टम अनुमतियों पर निर्भर है, और सिस्टम अस्थिरता, डेटा हानि या नेटवर्क ब्लॉक के दुष्प्रभावों के लिए डेवलपर ज़िम्मेदार नहीं है। हमेशा जाँच लें कि आप कौन-से ऐप्स ब्लॉक कर रहे हैं।

**आधिकारिक स्रोत केवल Google Play, F-Droid और यह रिपॉज़िटरी हैं।** कहीं और से मिले APK परियोजना से संबद्ध नहीं हैं और उनका उपयोग आपके अपने जोखिम पर है।

`INTERNET` अनुमति केवल वायरलेस डिबगिंग पेयरिंग (LibADB) के लिए माँगी जाती है।

## लाइसेंस

[GPLv3](../LICENSE.md)। साथ में दिया गया ट्रैकर सिग्नेचर डेटा अलग से ODbL v1.0 के तहत लाइसेंस प्राप्त है, देखें [TRACKER_DATA_LICENSE.md](../TRACKER_DATA_LICENSE.md)।

## आभार

- [Shizuku](https://github.com/RikkaApps/Shizuku): विशेषाधिकार प्राप्त कमांड चलाना।
- [LibADB](https://github.com/MuntashirAkon/libadb-android): वायरलेस डीबगिंग और डेमॉन कनेक्शन।
- [Exodus Privacy](https://exodus-privacy.eu.org/): ट्रैकर सिग्नेचर डेटाबेस। ShizuWall इसकी छोटी की गई कॉपी साथ रखता है और उनके API से कभी संपर्क नहीं करता।
