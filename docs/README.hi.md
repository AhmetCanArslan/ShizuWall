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


## ShizuWall क्यों

- **कोई VPN नहीं**: पैकेट इंटरसेप्शन और स्थायी VPN टनल के दुष्प्रभावों से बचाता है।
- **प्रति-ऐप नेटवर्क नियंत्रण**: Android के `connectivity` chain-3 नियंत्रणों से ऐप्स की नेटवर्क पहुँच चालू/बंद करता है।
- **डिज़ाइन से ही गोपनीयता**: ऑफ़लाइन-प्रथम; कोई एनालिटिक्स, कोई टेलीमेट्री, कोई ट्रैकिंग नहीं।
- **ऑटोमेशन के लिए तैयार**: स्क्रिप्ट और टास्क ऑटोमेशन हेतु `adb broadcast` कमांड का समर्थन करता है।
- **नियंत्रण के तरीके**: ShizuWall फ़ायरवॉल को नियंत्रित करने के तीन सुविधाजनक तरीके देता है: क्विक सेटिंग्स टाइल, ऐप विजेट और फ़्लोटिंग फ़ायरवॉल बटन।

## स्क्रीनशॉट

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

## आवश्यकताएँ

- Android 11 (API 30) या उससे नया
- एक नियंत्रण बैकएंड: Shizuku, स्थानीय ADB डेमॉन या रूट पहुँच

## नियंत्रण बैकएंड

ShizuWall फ़ायरवॉल कमांड चलाने के तीन तरीकों का समर्थन करता है:

| तरीका | विवरण | सेटअप |
|--------|---------|---------|
| **Shizuku** | सिस्टम सेवाओं से संवाद करने वाला सुरक्षित API। Shizuku ऐप आवश्यक है। फ़ोर्क भी समर्थित हैं। | Shizuku ऐप इंस्टॉल और सेटअप करें, अनुमतियाँ दें |
| **रूट** | सीधी रूट पहुँच। | मानक तरीकों से अपने डिवाइस को रूट करें |
| **LibADB (LADB)** | आपके फ़ोन की अंतर्निहित "वायरलेस डीबगिंग" सुविधा का उपयोग करके यह USB से जुड़े कंप्यूटर जैसा व्यवहार करता है। इससे ऐप बिना कंप्यूटर, रूट या Shizuku जैसे अतिरिक्त ऐप्स के उन्नत सिस्टम बदलाव कर सकता है। | डेवलपर विकल्पों में वायरलेस डीबगिंग चालू करें और पेयर करें (मार्गदर्शिका ऐप में है) |

## यह कैसे काम करता है

ShizuWall प्रति-ऐप नेटवर्क नियंत्रण के लिए Android की **Chain 3** (कनेक्टिविटी चेन) का उपयोग करता है। Shizuku या स्थानीय डेमॉन के ज़रिए ये प्लेटफ़ॉर्म कमांड चलाए जाते हैं:

### ADB Chain 3 कमांड

```bash
# फ़ायरवॉल फ़्रेमवर्क चालू करें
cmd connectivity set-chain3-enabled true

# विशिष्ट ऐप ब्लॉक करें
cmd connectivity set-package-networking-enabled false <package.name>

# विशिष्ट ऐप अनब्लॉक करें
cmd connectivity set-package-networking-enabled true <package.name>

# फ़ायरवॉल फ़्रेमवर्क बंद करें
cmd connectivity set-chain3-enabled false
```

**Chain 3** Android प्लेटफ़ॉर्म का एक तंत्र है जो सिस्टम स्तर पर प्रति-पैकेज नेटवर्क पहुँच को रोकता और नियंत्रित करता है, जिससे बारीक फ़ायरवॉल नियंत्रण संभव होता है।

## ऑटोमेशन (ADB ब्रॉडकास्ट)

आप स्क्रिप्ट और ऑटोमेशन टूल से ShizuWall को नियंत्रित कर सकते हैं।

**एक्शन**: `shizuwall.CONTROL`  
**कंपोनेंट**: `com.arslan.shizuwall/.receivers.FirewallControlReceiver`  

**एक्स्ट्रा**

- `state` (boolean, आवश्यक): `true` = चालू, `false` = बंद
- `apps` (string, वैकल्पिक): CSV पैकेज सूची। न देने पर ShizuWall सहेजे गए चयनित ऐप्स उपयोग करता है।

### उदाहरण

```bash
# सहेजे गए चयनित ऐप्स के लिए फ़ायरवॉल चालू करें
adb shell am broadcast -a shizuwall.CONTROL -n com.arslan.shizuwall/.receivers.FirewallControlReceiver --ez state true

# सहेजे गए चयनित ऐप्स के लिए फ़ायरवॉल बंद करें
adb shell am broadcast -a shizuwall.CONTROL -n com.arslan.shizuwall/.receivers.FirewallControlReceiver --ez state false

# विशिष्ट पैकेजों के लिए फ़ायरवॉल चालू करें
adb shell am broadcast -a shizuwall.CONTROL -n com.arslan.shizuwall/.receivers.FirewallControlReceiver --ez state true --es apps "com.example.app1,com.example.app2"

# विशिष्ट पैकेजों के लिए फ़ायरवॉल बंद करें
adb shell am broadcast -a shizuwall.CONTROL -n com.arslan.shizuwall/.receivers.FirewallControlReceiver --ez state false --es apps "com.example.app1,com.example.app2"
```

> ब्रॉडकास्ट सफल होने के लिए नियंत्रण बैकएंड में से एक (Shizuku, स्थानीय ADB डेमॉन या रूट) सक्रिय होना चाहिए।

## नोट्स और सीमाएँ

- Android प्लेटफ़ॉर्म के व्यवहार के कारण रीबूट पर फ़ायरवॉल नियम मिट जाते हैं।
- डिवाइस रीबूट करने से ShizuWall द्वारा लगाए गए सभी सक्रिय नेटवर्क ब्लॉक रीसेट हो जाते हैं।
- ऐप `android.permission.INTERNET` केवल वायरलेस डीबगिंग पेयरिंग (LibADB स्थानीय डेमॉन कनेक्शन) के लिए माँगता है।

## बिल्ड (डेवलपर्स के लिए)

### ऐप

```bash
./gradlew assembleRelease
```

### डेमॉन

डिवाइस पर चलने वाला डेमॉन (`SystemDaemon.java`) सामान्य Gradle बिल्ड के हिस्से के रूप में `compileDaemonDex` टास्क द्वारा स्वतः एक DEX (`daemon.bin`) में कंपाइल होता है। इसे Android SDK build-tools के `javac` और `d8` से स्रोत से कंपाइल कर एसेट के रूप में पैकेज किया जाता है — रिपॉज़िटरी में कोई पूर्व-निर्मित बाइनरी नहीं है, ताकि पुनरुत्पादनीय बिल्ड सर्वर (जैसे F-Droid) उसे स्वयं बना सकें।

किसी मैन्युअल चरण की आवश्यकता नहीं; `./gradlew assembleRelease` इसे भी बनाता है। टास्क को अलग से भी चलाया जा सकता है:

```bash
./gradlew :app:compileDaemonDex
```

## सुरक्षा और वितरण अस्वीकरण

ShizuWall बिना किसी प्रकार की वारंटी के **"जैसा है"** प्रदान किया जाता है।

इस ऐप का उपयोग करके आप स्वीकार करते हैं कि यह उन्नत सिस्टम अनुमतियों (Shizuku/ADB/रूट) पर निर्भर है, और आप सभी संबंधित जोखिम स्वीकार करते हैं। डेवलपर सिस्टम अस्थिरता, डेटा हानि, सेवा बाधा या नेटवर्क ब्लॉक करने के दुष्प्रभावों जैसे नुकसान के लिए ज़िम्मेदार नहीं है।

**आधिकारिक वितरण और तृतीय-पक्ष जोखिम**
तृतीय-पक्ष स्रोतों से प्राप्त APK फ़ाइलों के डाउनलोड, इंस्टॉलेशन या उपयोग से होने वाले किसी भी नुकसान, सुरक्षा उल्लंघन, मैलवेयर संक्रमण या डेटा हानि के लिए ShizuWall के डेवलपर और योगदानकर्ता कोई दायित्व या ज़िम्मेदारी नहीं लेते। ShizuWall आधिकारिक रूप से केवल Google Play Store, इसके आधिकारिक GitHub रिपॉज़िटरी और F-Droid के माध्यम से विकसित और वितरित किया जाता है। ShizuWall किसी आधिकारिक वेबसाइट का स्वामी, संचालक या समर्थक नहीं है। ShizuWall के आधिकारिक डाउनलोड देने का दावा करने वाली कोई भी वेबसाइट या तृतीय-पक्ष प्लेटफ़ॉर्म पूरी तरह असंबद्ध और अनधिकृत है। आधिकारिक चैनलों के बाहर से ऐप प्राप्त करने वाले उपयोगकर्ता पूरी तरह अपने जोखिम पर ऐसा करते हैं।

**ShizuWall एक ओपन-सोर्स ऐप्लिकेशन है। डेवलपर इस ऐप्लिकेशन के उपयोग के लिए कोई वित्तीय, नैतिक या कानूनी ज़िम्मेदारी स्वीकार नहीं करता। सारी ज़िम्मेदारी उपयोगकर्ता की है।**

आप कौन-से ऐप्स ब्लॉक कर रहे हैं, हमेशा जाँच लें।

## लाइसेंस

**GNU General Public License v3.0 (GPLv3)** के अंतर्गत लाइसेंस प्राप्त। देखें [LICENSE.md](../LICENSE.md)।

साथ में शामिल ट्रैकर सिग्नेचर डेटा कोड नहीं है और उसका अपना लाइसेंस है (ODbL v1.0)। देखें [TRACKER_DATA_LICENSE.md](../TRACKER_DATA_LICENSE.md)।

## सहयोग

- ⭐ प्रोजेक्ट को स्टार दें: [GitHub Stars](https://github.com/AhmetCanArslan/ShizuWall/stargazers)
- ☕ दान करें: [Buy Me a Coffee](https://buymeacoffee.com/ahmetcanarslan)
- ⬇️ डाउनलोड करें: [Google Play Store](https://play.google.com/store/apps/details?id=com.arslan.shizuwall)

## आभार

- [Shizuku](https://github.com/RikkaApps/Shizuku) — विशेषाधिकार प्राप्त कमांड निष्पादन प्रवाह को संभव बनाने वाला API।
- [LibADB](https://github.com/MuntashirAkon/libadb-android) — वायरलेस डीबगिंग और डेमॉन कनेक्शन समर्थन।
- [Exodus Privacy](https://exodus-privacy.eu.org/) — ऐप्स के भीतर ट्रैकर SDK पहचानने के लिए उपयोग किया जाने वाला ट्रैकर सिग्नेचर डेटाबेस, [ODbL v1.0](https://opendatacommons.org/licenses/odbl/1-0/) के अंतर्गत लाइसेंस प्राप्त। ShizuWall एक संक्षिप्त प्रति साथ रखता है और उनके API से कभी संपर्क नहीं करता। देखें [TRACKER_DATA_LICENSE.md](../TRACKER_DATA_LICENSE.md)।
