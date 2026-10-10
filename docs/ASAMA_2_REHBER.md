# Datasheet Analyzer — Aşama 2 geliştirme, test ve tam kod rehberi

## Teslimin kapsamı

Bu paket Aşama 1'in üzerine CameraX ve cihaz üzerinde OCR ekler. Kaynak ZIP tam projedir; önceki ZIP'i üstüne kopyalamak gerekmez. Hazır APK bu ortamda üretilemedi. APK komutu Gradle dağıtımını indirirken ağ erişimi hatası aldı; bu nedenle Android derlemesi ve cihaz testleri hâlâ bekliyor.

Yapılan bağımsız doğrulama: **15 Kotlin/JVM testi geçti.** XML dosyalarının sözdizimi, paket yolları ve ZIP bütünlüğü de kontrol edildi. Bu kontroller Android derlemesi veya gerçek kamerada OCR başarısı anlamına gelmez.

## 1. Android Studio'da aç

Önerilen örnek konum: `C:\AndroidProjects\DatasheetAnalyzer-Asama2\DatasheetAnalyzer`. Bu rehberdeki tam Windows yolları bu örnek köke göre verilir.

1. `DatasheetAnalyzer_Asama2.zip` arşivini çıkar.
2. Android Studio > Open ile içindeki `DatasheetAnalyzer` klasörünü aç. `app` alt klasörünü seçme.
3. AGP 9.0.1 ile uyumlu Android Studio kullan. SDK Manager'dan Platform 36, Build-Tools 36.0.0 ve Platform-Tools kur.
4. Settings > Build, Execution, Deployment > Build Tools > Gradle > Gradle JDK: JDK 17 seç.
5. SDK konumu sorulursa kendi SDK kurulumunu göster; başka bir bilgisayarın local.properties dosyasını kopyalama.
6. Gradle Sync yap. Offline mode kapalı olsun. CameraX ve gömülü OCR modelinin geliştirme bilgisayarına indirilmesi internet gerektirir.
7. API 26 veya üstü gerçek Android telefonu USB hata ayıklamasıyla bağla. Ekrandaki bilgisayar yetkilendirmesini onayla.
8. `app` yapılandırmasını ve telefonunu seçip Run'a bas.

API 26+ emülatörde de açılabilir, fakat emülatör arka kamerasının web kamerası veya test görüntüsüne bağlı olması gerekir. Gerçek komponent okuma kabul testi telefonda yapılmalıdır.

Aşama 1'de kendi değişikliklerini yaptıysan önce ayrı yedek al. Kodu gereksiz yeniden yazmadık: Home ekranı/tema/kriter modeli korunmuştur; kameranın geçici açıklama ekranı gerçek ScannerScreen ile değiştirilmiştir. `ScannerPlaceholderScreen.kt` artık kullanılmaz ve bu pakette yoktur.

## 2. APK'yı kendi bilgisayarında üret

Proje kökünde PowerShell:

```powershell
.\gradlew.bat :app:assembleDebug
```

Alternatif: Android Studio ile SDK ayarını ve ilk Sync'i yaptıktan sonra `APK_OLUSTUR.bat` dosyasına çift tıkla. Betik Gradle debug derlemesini çağırır, başarılıysa APK klasörünü açar. JAVA_HOME tanımlı değilse varsayılan Android Studio kurulumunun jbr klasörünü kullanmayı dener. Windows betiği burada çalıştırılmadı.

Başarılı derlemede beklenen dosya:

`C:\AndroidProjects\DatasheetAnalyzer-Asama2\DatasheetAnalyzer\app\build\outputs\apk\debug\app-debug.apk`

Bu dosyayı telefonuna kopyalayarak açabilirsin. Telefon istediğinde kullandığın dosya yöneticisi için uygulama yükleme izni vermen gerekir. Alternatif olarak Run düğmesi doğrudan cihazına yükler.

Bu teslimatta APK dosyası yoktur. İlgili gerçek hata `docs/APK_BUILD_ATTEMPT.txt` içinde kayıtlıdır. Derleme kaynak kod aşamasına ulaşmadığı için “Android kodu hatasız derlendi” denemez.

## 3. Kullanım akışı

1. Ana ekrandan istediğin kriterleri seç veya yaz.
2. SCAN COMPONENT'e dokun.
3. İlk girişte kamera izni sorulur. İzin verirsen arka kamera açılması beklenir.
4. Komponent yazısını ortadaki açık çerçeve içine getir. Işık yansımalarını azalt; odak için görüntüye dokun. Telefonu yazıya aşırı yaklaştırıp minimum odak mesafesinin altına düşürme.
5. Algılanan satırlar ve kod adayları ekranın altında görünür.
6. Bir adaya dokun. Tarama durur ve aday düzenleme alanına geçer.
7. Yanlış karakteri elle düzelt. Örneğin LM3B3'ü LM383 yap.
8. CONTINUE · KODU KULLAN düğmesine bas.
9. Ana ekranda komponent kodu güncellenir; önceden seçtiğin kriterler korunur.
10. SEARCH şimdilik girişlerinin özetini gösterir. Gerçek datasheet araması Aşama 3'tedir.

Aday çıkmasa bile düzeltme alanına kodu elle yazabilirsin. Yazmaya başladığında tarama durur. Yeniden Tara son yazdığın taslağı koruyarak aday toplamaya yeniden başlar. Ana ekrana dön düğmesi onaylanmamış tarayıcı taslağını ana forma uygulamaz.

## 4. Gradle ve Manifest değişiklikleri

Dosya: `app/build.gradle.kts`.

| Değişiklik | Değer |
|---|---|
| versionCode / versionName | 2 / 0.2.0 |
| Core KTX | androidx.core:core-ktx:1.17.0 |
| CameraX | camera-core, camera-camera2, camera-lifecycle, camera-view:1.5.3 |
| OCR | com.google.mlkit:text-recognition:16.0.1 — bundled Latin |
| Birim testi | junit:junit:4.13.2 |

Kök `build.gradle.kts`, Gradle wrapper, Compose ve mevcut Navigation/Lifecycle sürümleri korunmuştur. CameraX'in tüm modülleri aynı sürüme sabitlenmiştir. ML Kit sürümü geliştirme sırasında resmi Android rehberindeki bundled örneğinden doğrulanmıştır. Bu sürüm Play Services üzerinden sonradan model indiren `play-services-mlkit-text-recognition` değildir.

Dosya: `app/src/main/AndroidManifest.xml`.

- CAMERA izni eklendi; uygulama çalışırken ayrıca runtime izin sorulur.
- Kamera donanımı ve autofocus `required=false`: manuel giriş kamera donanımından bağımsız kalır.
- INTERNET için `tools:node="remove"` tanımlandı; Aşama 2'de kütüphane manifest'i üzerinden de ağ izni eklenmesin diye.
- Depolama veya mikrofon izni eklenmedi.
- Fotoğraf/video dosyası üretilmez; geçici bitmap yalnızca OCR işi boyunca tutulur.

API anahtarı, bulut LLM, cloud OCR veya Firebase projesi kullanılmaz. İleride ağ üzerinden datasheet bulma ayrı aşamadır.

## 5. Modüller neden ayrıldı?

| Dosya | Sorumluluk |
|---|---|
| domain/model/PartNumberNormalizer.kt | Manuel ve kamera girişine ortak normalizasyon |
| ocr/PartCandidateExtractor.kt | OCR satırlarından aday çıkarıp sıralama |
| ocr/AnalysisGate.kt | Minimum aralık, tek aktif işlem ve kapanma koordinasyonu |
| ocr/ComponentTextAnalyzer.kt | CameraX karesini alma, ML Kit çağrısı ve kaynak bırakma |
| camera/ScanRegion.kt | Önizleme çerçevesi ve gerçek OCR kırpması için ortak oranlar |
| camera/FrameCropper.kt | Viewport kırpması, yön düzeltmesi ve merkez bölge bitmap'i |
| camera/CameraPreview.kt | Kamera bağlama, odaklama, önizleme ve yaşam döngüsü temizliği |
| ui/scanner/ScannerViewModel.kt | Adaylar, taslak, duraklama, hata ve onay |
| ui/scanner/ScannerScreen.kt | İzin ekranı, kamera ekranı, adaylar ve elle düzeltme |
| ui/navigation/DatasheetApp.kt | Onaylanan kodu mevcut HomeViewModel'e aktarma |
| ui/home/HomeViewModel.kt | Manuel/kamera yolunun aynı form doğrulamasını kullanması |

### Normalizasyon ve adaylar

` lm383 ` → `LM383`, `LM 383` → `LM383`. Büyük harf dönüşümü `Locale.ROOT` ile yapılır; Türkçe sistem dilindeki i/İ farkı IRLZ44N gibi kodları bozmasın diye.

`LM3B3`, `LMO83`, `OI1S5` gibi belirsiz karakterler değiştirilmez. Paket/tür son eklerindeki `-` ve `/` korunur. Desteklenmeyen karakterler sessizce silinmez; son onayda doğrulama hatası verilir.

Aday filtrelemesi harf+rakam içeren 3–32 karakterlik dizileri önceliklendirir. Saf üretici adı NATIONAL ve saf sayı/tarih 2026 aday olmaz. J94 gibi kısa harf+rakam kodları düşük sırada kalabilir; bunların üretim tarihi mi parça kodu mu olduğu bu aşamada kesin belirlenemez. Sıralama sayısı olasılık veya doğrulanmış kimlik değildir ve kullanıcıya güven yüzdesi olarak sunulmaz.

`LM 383` gibi ayrılmış kısa önekler birleştirilir. Bütün üretici satırı birleştirilmez. Her kare için en fazla 24 satır değerlendirilir ve 12 aday gösterilir. Ham OCR alanında ilk 8 satır görünür. Listeler o anki kareyi yansıtır; seçim sonrasında dondurulur.

### Performans ve görüntü bölgesi

AnalysisGate yeni OCR başlangıçları arasında en az 400 ms bırakır. Bu bir cihaz hız garantisi değildir. Bir tanıma 400 ms'den uzun sürerse bitmeden yeni tanıma başlamaz. CameraX KEEP_ONLY_LATEST ile eski kareleri kuyrukta biriktirmez.

Önizleme ve ImageAnalysis ortak UseCaseGroup/ViewPort kullanır. FrameCropper önce bu viewport'un cropRect alanını alır, imageInfo.rotationDegrees ile görüntüyü düzleştirir ve ortak ScanRegion oranlarıyla ortadaki alanı kırpar. OCR'ye bütün kamera karesi yerine bu bitmap gönderilir. Çerçeve oranları yatayda %8–%92, dikeyde %30–%70'tir. Dönüştürme maliyeti yalnızca seçilen karelerde ödenir.

1280×960 tercih edilir; cihaz desteklerine göre yakın çözünürlüğe düşebilir veya yükselebilir. Gerçek çözünürlük, kırpmanın önizleme ile örtüşmesi ve döndürme davranışı cihazda kabul testi gerektirir.

Kamera ImageProxy'si bitmap hazır olur olmaz finally içinde kapatılır. ML Kit bağımsız bitmap'i kullanır; görev tamamlanınca bitmap recycle edilir. Ekran kapanırken yeni iş durdurulur; süren OCR işi bitmeden recognizer kapatılmaz. Gecikmiş callback'ler kapanmış ekrana sonuç uygulamaz. Ekranın kendi kamera use case'leri unbind edilir ve executor kapanır.

### İzin ve yaşam döngüsü

İzin reddinde kamera kurulmaz. Kullanıcı yeniden izin isteyebilir, uygulama ayarlarını açabilir veya manuel girişe dönebilir. Ayarlardan döndüğünde izin yeniden kontrol edilir. Android ekranı RESUMED durumunda değilken kamera bileşeni kaldırılır; ön plana dönünce izin ve tarama durumu uygunsa yeniden kurulur.

Bir adaya dokunmak veya düzenleme alanına yazmak taramayı durdurur. Bu tercih, canlı OCR'nin kullanıcının düzeltmesini ezmesini önler. Ekran döndürülünce seçilen taslak SavedStateHandle ile korunmaya çalışılır; bu kalıcı dosya/geçmiş sistemi değildir.

## 6. Yerel testler ve sınırları

Çalıştırılan üç sınıf, toplam 15 test:

- PartNumberNormalizerTest: boşluk/büyük harf, belirsiz karakter, paket son eki, Türkçe locale ve hatalı giriş.
- PartCandidateExtractorTest: parça/lot sıralaması, bölünmüş önek, tekilleştirme, üretici/tarih filtresi ve OCR hatasını değiştirmeme.
- AnalysisGateTest: 400 ms sınırı, yavaş OCR'de üst üste binmeme, aktif iş sırasında kapanma, boşta kapanma ve eşzamanlı karelerin yalnızca birinin kabul edilmesi.

Sonuç: `OK (15 tests)`. Ham çıktı: `docs/JVM_TEST_RESULT.txt`.

Testler yerel Kotlin 2.0.21 compiler + JUnit 4.13.2 ile saf sınıflara karşı çalıştırıldı. Android SDK/ML Kit/Compose sınıfları bu bağımsız test çalıştırmasına dahil edilmedi. Projenin asıl Kotlin sürümü 2.2.10'dur ve tam Gradle Android derlemesi beklemektedir.

Bilgisayarında gerçek Gradle test görevi:

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

## 7. Telefonda kabul testleri — henüz çalıştırılmadı

| Test | İşlem | Beklenen sonuç |
|---|---|---|
| İlk açılış | Uygulamayı aç | Ana form ve kriterler görünür |
| İlk kamera izni | SCAN COMPONENT > izin ver | Arka kamera ve merkez çerçeve |
| İzin reddi | Kamera iznini reddet | Açıklama, izin/ayarlar ve manuel giriş seçenekleri |
| Kalıcı izin reddi | Tekrarlanan redden sonra ayarlardan izin ver | Dönüşte izin yeniden değerlendirilir |
| Kamera yok/meşgul | Kamerasız emülatör veya başka kamera uygulaması | Anlaşılır hata ve manuel giriş |
| Basılı kod | Kağıda LM383 yaz, çerçeveye al | LM383 aday olarak görünür; kesin tanıma garanti edilmez |
| Karışık satırlar | LM383 / NATIONAL / J94 yaz | NATIONAL aday olmaz; LM383, J94'ten üstte |
| Bölünmüş kod | LM 383 yaz | LM383 adayı oluşur |
| Merkez kırpma | Kod çerçevenin tamamen dışında, sonra içinde | Dışarıdaki yazı OCR'ye girmemeli; içeride tanınabilmeli |
| Yatay/dikey | Cihazı 90 derece çevir | Önizleme ve yazı yönü doğru; taslak korunur |
| 180 derece | Cihazı ters çevir | OCR yön/hizalamasını ayrıca doğrula; cihaz davranışını kaydet |
| Yanlış OCR | LM3B3 adayını seç, elle LM383 yap | OCR taslağın üzerine yazmaz |
| Kamera-manuel ortak yol | Düzeltilen kodu CONTINUE ile onayla | Ana komponent alanı LM383 olur |
| Kriter korunması | Önceden Supply Voltage + Pinout seç | Kamera dönüşünde iki seçim de durur |
| Onaysız geri | Farklı kodu seç, CONTINUE yerine geri | Ana formun önceki komponent kodu korunur |
| Yeniden tara | Taslak düzenle, yeniden taramaya bas | Son taslak korunur, yeni adaylar alınır |
| Manuel normalizasyon | Ana ekranda lm 383 + bir kriter, SEARCH | Özette LM383 görünür |
| Belirsiz karakter | LMO83 veya LM3B3 yaz | O→0 veya B→8 kendiliğinden yapılmaz |
| Arka plan | Home'a bas, uygulamaya dön | Kamera arka planda bırakılır, dönüşte yeniden bağlanır |
| Çık-gir | Tarayıcıdan geri dön, tekrar aç | Kamera yeniden açılabilir; eski işler ekrana karışmaz |
| Uçak modunda ilk OCR | İlk OCR denemesinden önce uçak modunu aç | Gömülü modelle tanıma; model indirme beklemesi olmamalı |
| Küçük ekran/klavye | Yazı boyutunu büyüt, düzeltme yap | Kaydırarak bütün kontrollere ulaşılır |
| Gerçek komponent | LM358, NE555, BC547 vb. üzerinde dene | Yansıma/odak ve küçük baskı sınırlılıklarını kaydet |

Bunlar başarıyla yapıldı diye işaretlenmedi. Cihaz modeli, Android sürümü, denenen kod ve gözlenen hatayı not et. Özellikle gerçek çiplerde ışık yansıması, silik baskı ve kısa SMD kodları tanımayı zorlaştırabilir.

## 8. Olası hatalar ve çözüm

| Belirti | Öncelikli kontrol |
|---|---|
| Gradle indirme hatası | Geliştirme bilgisayarının interneti, proxy ve offline mode |
| Unresolved reference CameraX / ML Kit | app/build.gradle.kts bağımlılıkları ve başarılı Sync |
| Kotlin plugin/AGP çakışması | AGP 9'da org.jetbrains.kotlin.android ekleme; mevcut kök Gradle dosyasını koru |
| JDK veya SDK hatası | JDK 17, SDK Platform 36 ve Build-Tools 36.0.0 |
| Kamera izni yok | Uygulama ayarları > İzinler > Kamera |
| Kamera açılamadı | Arka kamera donanımı, başka kamera uygulaması, yeniden tara |
| Önizleme siyah | Kamera donanımı/izni; emülatör kamera kaynağı; uygulamayı ön plana getir |
| Kod bulunamıyor | Çerçeve içi konum, odak, ışık, mesafe, baskı büyüklüğü |
| Yanlış kod adayı | Adaları kimlik doğrulaması sayma; düzeltip onayla |
| Döndürmede kırpma kaymış | Cihaz modeli ve yönüyle ekran görüntüsü gönder; viewport/crop doğrulanmalı |
| Kodu seçince kamera kapanıyor | Beklenen davranış; elle düzeltme sırasında sonuç sabitlenir |
| Gerilim sonucu görünmüyor | Beklenen; datasheet araması henüz yok |

Derleme hatası çıkarsa Build Output'taki ilk asıl hata bloğunu paylaş. Mevcut çalışan dosyaları koruyup sadece gereken yeri düzelteceğiz.

## 9. Git ve aşama sınırı

Yerelde kabul testlerinden sonra:

```powershell
git add .
git status
git commit -m "a02: add CameraX and on-device component OCR"
```

Bu oturumda uzak depoya yazılmadı. Kaynak kod, testler ve rehberler commit edilebilir. local.properties, .gradle, build çıktıları, APK ve cache dosyaları commit edilmez.

Aşama 3 başlamadı. Arama motoru, güvenilir domain seçici, PDF indirme ve güvenlik kontrolleri bu pakete eklenmedi.

## 10. Resmi kaynaklar

- [ML Kit Android text recognition — bundled bağımlılığı](https://developers.google.com/ml-kit/vision/text-recognition/v2/android)
- [CameraX sürüm notları — 1.5.3](https://developer.android.com/jetpack/androidx/releases/camera)
- [CameraX ImageAnalysis ve backpressure](https://developer.android.com/media/camera/camerax/analyze)
- [CameraX viewport ve use case yapılandırması](https://developer.android.com/media/camera/camerax/configuration)
- [ImageProxy.toBitmap](https://developer.android.com/reference/androidx/camera/core/ImageProxy)

Kaynaklar 01.10.2026 tarihinde kontrol edildi. CameraX 1.5.3 kararlı sürüm olarak doğrulandı; en son sürüm olduğu iddia edilmez.

## 11. Tam dosya yolları ve kodlar

Aşağıdaki tüm metin dosyaları güncel Aşama 2 içeriğidir. Kendi proje kökün farklıysa yalnızca önerilen kök kısmını değiştir. Wrapper betikleri, wrapper JAR ve lisansı ZIP'te bulunur; JAR bir kod bloğu olarak yapıştırılmaz.

### settings.gradle.kts

Tam yol: `C:\AndroidProjects\DatasheetAnalyzer-Asama2\DatasheetAnalyzer\settings.gradle.kts`

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "DatasheetAnalyzer"
include(":app")
```

### build.gradle.kts

Tam yol: `C:\AndroidProjects\DatasheetAnalyzer-Asama2\DatasheetAnalyzer\build.gradle.kts`

```kotlin
plugins {
    id("com.android.application") version "9.0.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
}
```

### gradle.properties

Tam yol: `C:\AndroidProjects\DatasheetAnalyzer-Asama2\DatasheetAnalyzer\gradle.properties`

```text
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
```

### gradle/wrapper/gradle-wrapper.properties

Tam yol: `C:\AndroidProjects\DatasheetAnalyzer-Asama2\DatasheetAnalyzer\gradle\wrapper\gradle-wrapper.properties`

```text
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-9.1.0-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

### app/build.gradle.kts

Tam yol: `C:\AndroidProjects\DatasheetAnalyzer-Asama2\DatasheetAnalyzer\app\build.gradle.kts`

```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.datasheetanalyzer"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.datasheetanalyzer"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.01.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.activity:activity-compose:1.12.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate:2.10.0")
    implementation("androidx.navigation:navigation-compose:2.9.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    implementation("androidx.core:core-ktx:1.17.0")
    val cameraX = "1.5.3"
    implementation("androidx.camera:camera-core:$cameraX")
    implementation("androidx.camera:camera-camera2:$cameraX")
    implementation("androidx.camera:camera-lifecycle:$cameraX")
    implementation("androidx.camera:camera-view:$cameraX")
    // Bundled Latin OCR: no Play Services model download, cloud key or account.
    implementation("com.google.mlkit:text-recognition:16.0.1")

    testImplementation("junit:junit:4.13.2")
}
```

### app/src/main/AndroidManifest.xml

Tam yol: `C:\AndroidProjects\DatasheetAnalyzer-Asama2\DatasheetAnalyzer\app\src\main\AndroidManifest.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">
    <uses-permission android:name="android.permission.CAMERA" />
    <!-- Stage 2 runs locally, including any merged library manifests. -->
    <uses-permission android:name="android.permission.INTERNET" tools:node="remove" />
    <uses-feature android:name="android.hardware.camera" android:required="false" />
    <uses-feature android:name="android.hardware.camera.autofocus" android:required="false" />
    <application
        android:allowBackup="false"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.DatasheetAnalyzer"
        android:usesCleartextTraffic="false">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

### app/src/main/res/values/strings.xml

Tam yol: `C:\AndroidProjects\DatasheetAnalyzer-Asama2\DatasheetAnalyzer\app\src\main\res\values\strings.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">Datasheet Analyzer</string>
</resources>
```

### app/src/main/res/values/themes.xml

Tam yol: `C:\AndroidProjects\DatasheetAnalyzer-Asama2\DatasheetAnalyzer\app\src\main\res\values\themes.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.DatasheetAnalyzer" parent="android:style/Theme.Material.Light.NoActionBar">
        <item name="android:fontFamily">sans</item>
        <item name="android:windowLightStatusBar">true</item>
        <item name="android:windowActionModeOverlay">true</item>
    </style>
</resources>
```

### APK_OLUSTUR.bat

Tam yol: `C:\AndroidProjects\DatasheetAnalyzer-Asama2\DatasheetAnalyzer\APK_OLUSTUR.bat`

```bat
@echo off
setlocal
cd /d "%~dp0"
if not defined JAVA_HOME (
    if exist "%ProgramFiles%\Android\Android Studio\jbr\bin\java.exe" (
        set "JAVA_HOME=%ProgramFiles%\Android\Android Studio\jbr"
    )
)
echo Android Studio ile projeyi en az bir kez acip SDK yolunu ve Gradle Sync islemini tamamlayin.
echo Debug APK derleniyor...
call gradlew.bat :app:assembleDebug
if errorlevel 1 (
    echo Derleme basarisiz. Yukaridaki ilk hata mesajini kontrol edin.
    pause
    exit /b 1
)
echo APK: %CD%\app\build\outputs\apk\debug\app-debug.apk
start "" explorer.exe "%CD%\app\build\outputs\apk\debug"
pause
endlocal
```

### .gitignore

Tam yol: `C:\AndroidProjects\DatasheetAnalyzer-Asama2\DatasheetAnalyzer\.gitignore`

```text
.gradle/
.kotlin/
.idea/
*.iml
local.properties
**/build/
captures/
.externalNativeBuild/
.cxx/
*.apk
*.aab
*.jks
*.keystore
keystore.properties
**/cache/
**/quarantine/
**/downloads/
*.pdf
.DS_Store
Thumbs.db
```

### app/src/main/java/com/example/datasheetanalyzer/MainActivity.kt

Tam yol: `C:\AndroidProjects\DatasheetAnalyzer-Asama2\DatasheetAnalyzer\app\src\main\java\com\example\datasheetanalyzer\MainActivity.kt`

```kotlin
package com.example.datasheetanalyzer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.datasheetanalyzer.ui.navigation.DatasheetApp
import com.example.datasheetanalyzer.ui.theme.DatasheetAnalyzerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DatasheetAnalyzerTheme {
                DatasheetApp()
            }
        }
    }
}
```

### app/src/main/java/com/example/datasheetanalyzer/camera/CameraPreview.kt

Tam yol: `C:\AndroidProjects\DatasheetAnalyzer-Asama2\DatasheetAnalyzer\app\src\main\java\com\example\datasheetanalyzer\camera\CameraPreview.kt`

```kotlin
package com.example.datasheetanalyzer.camera

import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.util.Size
import android.view.MotionEvent
import android.view.Surface
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as ComposeSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.datasheetanalyzer.ocr.ComponentTextAnalyzer
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    onLines: (List<String>) -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val currentOnLines by rememberUpdatedState(onLines)
    val currentOnError by rememberUpdatedState(onError)
    val previewView = remember(context) {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    var displayRotation by remember { mutableIntStateOf(Surface.ROTATION_0) }

    // A 180-degree turn may not recreate the Activity. Rebind the shared viewport.
    DisposableEffect(previewView) {
        var active = true
        val manager = context.getSystemService(DisplayManager::class.java)
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = Unit
            override fun onDisplayRemoved(displayId: Int) = Unit
            override fun onDisplayChanged(displayId: Int) {
                if (active && previewView.display?.displayId == displayId) {
                    displayRotation = previewView.display?.rotation ?: Surface.ROTATION_0
                }
            }
        }
        manager?.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
        previewView.doOnLayout {
            if (active) displayRotation = previewView.display?.rotation ?: Surface.ROTATION_0
        }
        onDispose {
            active = false
            manager?.unregisterDisplayListener(listener)
        }
    }

    DisposableEffect(previewView, owner, displayRotation) {
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val analysisExecutor = Executors.newSingleThreadExecutor()
        var disposed = false
        var provider: ProcessCameraProvider? = null
        var preview: Preview? = null
        var analysis: ImageAnalysis? = null
        var analyzer: ComponentTextAnalyzer? = null
        var camera: Camera? = null
        val cameraObserver = Observer<CameraState> { cameraState ->
            if (!disposed && cameraState.error != null) {
                currentOnError("Kamera kullanılamıyor. Başka bir kamera uygulamasını kapatıp yeniden dene.")
            }
        }

        previewView.doOnLayout {
            if (!disposed) {
                try {
                    val future = ProcessCameraProvider.getInstance(context)
                    future.addListener({
                        if (!disposed) {
                            try {
                                val readyProvider = future.get()
                                provider = readyProvider
                                require(readyProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA))
                                val rotation = previewView.display?.rotation ?: Surface.ROTATION_0
                                val resolution = ResolutionSelector.Builder()
                                    .setAspectRatioStrategy(AspectRatioStrategy(
                                        AspectRatio.RATIO_4_3, AspectRatioStrategy.FALLBACK_RULE_AUTO
                                    ))
                                    .setResolutionStrategy(ResolutionStrategy(
                                        Size(1280, 960),
                                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
                                    )).build()
                                val readyPreview = Preview.Builder()
                                    .setResolutionSelector(resolution)
                                    .setTargetRotation(rotation).build()
                                preview = readyPreview
                                readyPreview.setSurfaceProvider(previewView.surfaceProvider)

                                val readyAnalyzer = ComponentTextAnalyzer(
                                    callbackExecutor = mainExecutor,
                                    onLines = { if (!disposed) currentOnLines(it) },
                                    onError = { if (!disposed) currentOnError(it) }
                                )
                                analyzer = readyAnalyzer
                                val readyAnalysis = ImageAnalysis.Builder()
                                    .setResolutionSelector(resolution)
                                    .setTargetRotation(rotation)
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                                    .build()
                                analysis = readyAnalysis
                                readyAnalysis.setAnalyzer(analysisExecutor, readyAnalyzer)

                                // Shared viewport makes analysis.cropRect match visible preview.
                                val viewport = requireNotNull(previewView.viewPort)
                                val group = UseCaseGroup.Builder().setViewPort(viewport)
                                    .addUseCase(readyPreview).addUseCase(readyAnalysis).build()
                                val readyCamera = readyProvider.bindToLifecycle(
                                    owner, CameraSelector.DEFAULT_BACK_CAMERA, group
                                )
                                camera = readyCamera
                                readyCamera.cameraInfo.cameraState.observe(owner, cameraObserver)
                                previewView.setOnTouchListener { view, event ->
                                    if (event.action == MotionEvent.ACTION_UP) {
                                        view.performClick()
                                        val point = previewView.meteringPointFactory
                                            .createPoint(event.x, event.y)
                                        val focus = FocusMeteringAction.Builder(point,
                                            FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
                                            .setAutoCancelDuration(3, TimeUnit.SECONDS).build()
                                        // Unsupported focus is non-fatal; automatic focus remains.
                                        runCatching { readyCamera.cameraControl.startFocusAndMetering(focus) }
                                    }
                                    true
                                }
                            } catch (_: Exception) {
                                if (!disposed) currentOnError(
                                    "Arka kamera başlatılamadı. İzni kontrol et veya kodu elle gir."
                                )
                            }
                        }
                    }, mainExecutor)
                } catch (_: Exception) {
                    currentOnError("Kamera servisi başlatılamadı. Yeniden dene veya kodu elle gir.")
                }
            }
        }

        onDispose {
            disposed = true
            previewView.setOnTouchListener(null)
            camera?.cameraInfo?.cameraState?.removeObserver(cameraObserver)
            analysis?.clearAnalyzer()
            analyzer?.close()
            // Unbind this screen's use cases only, never another screen's camera.
            val ownedUseCases = listOfNotNull(preview, analysis).toTypedArray()
            if (ownedUseCases.isNotEmpty()) runCatching { provider?.unbind(*ownedUseCases) }
            analysisExecutor.shutdown()
        }
    }

    Box(modifier) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        Canvas(Modifier.fillMaxSize()) {
            val left = size.width * ScanRegion.LEFT
            val right = size.width * ScanRegion.RIGHT
            val top = size.height * ScanRegion.TOP
            val bottom = size.height * ScanRegion.BOTTOM
            val shade = Color.Black.copy(alpha = 0.48f)
            drawRect(shade, size = ComposeSize(size.width, top))
            drawRect(shade, Offset(0f, bottom), ComposeSize(size.width, size.height - bottom))
            drawRect(shade, Offset(0f, top), ComposeSize(left, bottom - top))
            drawRect(shade, Offset(right, top), ComposeSize(size.width - right, bottom - top))
            drawRect(Color(0xFF80D5C3), Offset(left, top),
                ComposeSize(right - left, bottom - top), style = Stroke(2.dp.toPx()))
        }
    }
}
```

### app/src/main/java/com/example/datasheetanalyzer/camera/FrameCropper.kt

Tam yol: `C:\AndroidProjects\DatasheetAnalyzer-Asama2\DatasheetAnalyzer\app\src\main\java\com\example\datasheetanalyzer\camera\FrameCropper.kt`

```kotlin
package com.example.datasheetanalyzer.camera

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import androidx.camera.core.ImageProxy

object FrameCropper {
    /** Caller owns the returned bitmap. ImageProxy is closed by the analyzer. */
    fun crop(image: ImageProxy): Bitmap {
        val allocated = mutableListOf<Bitmap>()
        var result: Bitmap? = null
        try {
            val full = image.toBitmap().also(allocated::add)
            val viewportRect = Rect(image.cropRect)
            require(viewportRect.intersect(0, 0, full.width, full.height))
            val viewport = Bitmap.createBitmap(
                full, viewportRect.left, viewportRect.top,
                viewportRect.width(), viewportRect.height()
            ).also(allocated::add)
            val matrix = Matrix().apply { postRotate(image.imageInfo.rotationDegrees.toFloat()) }
            val upright = Bitmap.createBitmap(
                viewport, 0, 0, viewport.width, viewport.height, matrix, true
            ).also(allocated::add)
            val left = (upright.width * ScanRegion.LEFT).toInt()
            val top = (upright.height * ScanRegion.TOP).toInt()
            val right = (upright.width * ScanRegion.RIGHT).toInt().coerceAtLeast(left + 1)
            val bottom = (upright.height * ScanRegion.BOTTOM).toInt().coerceAtLeast(top + 1)
            result = Bitmap.createBitmap(upright, left, top, right - left, bottom - top)
            return result
        } finally {
            // createBitmap can return its input; recycle each distinct object once.
            allocated.distinct().filter { it !== result }.forEach { it.recycle() }
        }
    }
}
```

### app/src/main/java/com/example/datasheetanalyzer/camera/ScanRegion.kt

Tam yol: `C:\AndroidProjects\DatasheetAnalyzer-Asama2\DatasheetAnalyzer\app\src\main\java\com\example\datasheetanalyzer\camera\ScanRegion.kt`

```kotlin
package com.example.datasheetanalyzer.camera

/** Same fractional rectangle for the preview overlay and the upright viewport crop. */
object ScanRegion {
    const val LEFT = 0.08f
    const val TOP = 0.30f
    const val RIGHT = 0.92f
    const val BOTTOM = 0.70f
}
```

### app/src/main/java/com/example/datasheetanalyzer/domain/model/Criterion.kt

Tam yol: `C:\AndroidProjects\DatasheetAnalyzer-Asama2\DatasheetAnalyzer\app\src\main\java\com\example\datasheetanalyzer\domain\model\Criterion.kt`

```kotlin
package com.example.datasheetanalyzer.domain.model

enum class Criterion(val label: String) {
    SUPPLY_VOLTAGE("Supply Voltage"),
    PINOUT("Pinout"),
    MAXIMUM_CURRENT("Maximum Current"),
    OPERATING_TEMPERATURE("Operating Temperature"),
    POWER_DISSIPATION("Power Dissipation"),
    ELECTRICAL_CHARACTERISTICS("Electrical Characteristics"),
    FEATURES("Features"),
    APPLICATIONS("Applications")
}
```

### app/src/main/java/com/example/datasheetanalyzer/domain/model/PartNumberNormalizer.kt

Tam yol: `C:\AndroidProjects\DatasheetAnalyzer-Asama2\DatasheetAnalyzer\app\src\main\java\com\example\datasheetanalyzer\domain\model\PartNumberNormalizer.kt`

```kotlin
package com.example.datasheetanalyzer.domain.model

import java.util.Locale

object PartNumberNormalizer {
    const val MAX_LENGTH = 64
    private val spaces = Regex("\\s+")
    private val syntax = Regex("[A-Z0-9][A-Z0-9/-]{0,63}")

    fun normalize(raw: String): String =
        raw.trim().uppercase(Locale.ROOT).replace(spaces, "")

    fun isValid(normalized: String): Boolean =
        normalized.length in 1..MAX_LENGTH && syntax.matches(normalized)
}
```

### app/src/main/java/com/example/datasheetanalyzer/ocr/AnalysisGate.kt

Tam yol: `C:\AndroidProjects\DatasheetAnalyzer-Asama2\DatasheetAnalyzer\app\src\main\java\com\example\datasheetanalyzer\ocr\AnalysisGate.kt`

```kotlin
package com.example.datasheetanalyzer.ocr

/** One in-flight recognition; throttles start times using a monotonic clock. */
class AnalysisGate(private val intervalMs: Long = 400L) {
    private var busy = false
    private var stopped = false
    private var lastStart: Long? = null

    init { require(intervalMs >= 0) }

    @Synchronized
    fun tryAcquire(nowMs: Long): Boolean {
        val last = lastStart
        if (stopped || busy || (last != null && nowMs - last < intervalMs)) return false
        busy = true
        lastStart = nowMs
        return true
    }

    /** Returns true when the recognizer can now be closed. */
    @Synchronized
    fun complete(): Boolean {
        busy = false
        return stopped
    }

    /** Stops new work; returns true if no task is currently using the recognizer. */
    @Synchronized
    fun stop(): Boolean {
        stopped = true
        return !busy
    }

    @Synchronized
    fun isStopped(): Boolean = stopped
}
```

### app/src/main/java/com/example/datasheetanalyzer/ocr/ComponentTextAnalyzer.kt

Tam yol: `C:\AndroidProjects\DatasheetAnalyzer-Asama2\DatasheetAnalyzer\app\src\main\java\com\example\datasheetanalyzer\ocr\ComponentTextAnalyzer.kt`

```kotlin
package com.example.datasheetanalyzer.ocr

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.datasheetanalyzer.camera.FrameCropper
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

class ComponentTextAnalyzer(
    private val callbackExecutor: Executor,
    private val onLines: (List<String>) -> Unit,
    private val onError: (String) -> Unit
) : ImageAnalysis.Analyzer, AutoCloseable {
    private val gate = AnalysisGate(intervalMs = 400L)
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val recognizerClosed = AtomicBoolean(false)

    override fun analyze(image: ImageProxy) {
        if (!gate.tryAcquire(SystemClock.elapsedRealtime())) {
            image.close()
            return
        }

        val bitmap: Bitmap
        try {
            bitmap = FrameCropper.crop(image)
        } catch (_: Exception) {
            callbackExecutor.execute {
                if (!gate.isStopped()) onError("Kamera görüntüsü okunamadı. Yeniden taramayı dene.")
            }
            complete()
            return
        } finally {
            // ML Kit receives an owned bitmap, so the camera buffer is released now.
            image.close()
        }

        if (gate.isStopped()) {
            bitmap.recycle()
            complete()
            return
        }

        try {
            recognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnCompleteListener(callbackExecutor) { task ->
                    try {
                        if (!gate.isStopped()) {
                            if (task.isSuccessful) {
                                val lines = task.result.textBlocks.flatMap { it.lines }
                                    .take(24).map { it.text.take(256) }
                                onLines(lines)
                            } else {
                                onError("Yazı tanıma tamamlanamadı. Yeniden dene veya kodu elle gir.")
                            }
                        }
                    } finally {
                        bitmap.recycle()
                        complete()
                    }
                }
        } catch (_: Exception) {
            bitmap.recycle()
            callbackExecutor.execute {
                if (!gate.isStopped()) onError("Yazı tanıma başlatılamadı. Yeniden taramayı dene.")
            }
            complete()
        }
    }

    private fun complete() {
        if (gate.complete()) closeRecognizer()
    }

    override fun close() {
        if (gate.stop()) closeRecognizer()
    }

    private fun closeRecognizer() {
        if (recognizerClosed.compareAndSet(false, true)) recognizer.close()
    }
}
```

### app/src/main/java/com/example/datasheetanalyzer/ocr/PartCandidateExtractor.kt

Tam yol: `C:\AndroidProjects\DatasheetAnalyzer-Asama2\DatasheetAnalyzer\app\src\main\java\com\example\datasheetanalyzer\ocr\PartCandidateExtractor.kt`

```kotlin
package com.example.datasheetanalyzer.ocr

import com.example.datasheetanalyzer.domain.model.PartNumberNormalizer

data class PartCandidate(val code: String, val rank: Int)

class PartCandidateExtractor {
    private val split = Regex("[\\s,;:()\\[\\]]+")
    private val shortPrefix = Regex("[A-Z]{1,5}")
    private val numericSuffix = Regex("[0-9][A-Z0-9/-]{1,23}")

    fun extract(lines: List<String>): List<PartCandidate> {
        val candidates = linkedSetOf<String>()
        lines.take(24).forEach { line ->
            val tokens = line.trim().take(256).split(split)
                .map { PartNumberNormalizer.normalize(it.trim('.', '!', '?', '"')) }
                .filter { it.isNotEmpty() }
            candidates.addAll(tokens)
            // Join split prefixes such as "LM 383", not entire manufacturer lines.
            tokens.zipWithNext().forEach { (first, second) ->
                if (shortPrefix.matches(first) && numericSuffix.matches(second)) {
                    candidates.add(first + second)
                }
            }
        }
        return candidates.asSequence()
            .filter { it.length in 3..32 && PartNumberNormalizer.isValid(it) }
            .filter { code -> code.any { it in 'A'..'Z' } && code.any { it in '0'..'9' } }
            .map { code ->
                // A ranking heuristic, never a probability or identity verification.
                val rank = (if (code.length >= 4) 20 else 0) +
                    (if (code.count { it in '0'..'9' } >= 2) 10 else 0) +
                    code.length.coerceAtMost(12)
                PartCandidate(code, rank)
            }
            .sortedWith(compareByDescending<PartCandidate> { it.rank }.thenBy { it.code })
            .take(12)
            .toList()
    }
}
```

### app/src/main/java/com/example/datasheetanalyzer/ui/home/HomeScreen.kt

Tam yol: `C:\AndroidProjects\DatasheetAnalyzer-Asama2\DatasheetAnalyzer\app\src\main\java\com\example\datasheetanalyzer\ui\home\HomeScreen.kt`

```kotlin
package com.example.datasheetanalyzer.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.datasheetanalyzer.domain.model.Criterion
import com.example.datasheetanalyzer.ui.theme.DatasheetAnalyzerTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onPartNumberChanged: (String) -> Unit,
    onCustomCriterionChanged: (String) -> Unit,
    onCriterionToggled: (Criterion) -> Unit,
    onSearch: () -> Unit,
    onScan: () -> Unit
) {
    val focusManager = LocalFocusManager.current

    Scaffold { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Datasheet Analyzer", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Komponentini yaz, öğrenmek istediğin bilgileri seç.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Card(colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )) {
                Text(
                    "Kodu elle yaz veya kamerayla oku.\nDatasheet araması bir sonraki aşamada eklenecek.",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            OutlinedTextField(
                value = state.partNumber,
                onValueChange = onPartNumberChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Component Part Number") },
                placeholder = { Text("Örnek: LM383") },
                supportingText = { Text("Komponent kodu · ${state.partNumber.length}/64") },
                singleLine = true,
                enabled = !state.loading,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Next
                )
            )

            OutlinedTextField(
                value = state.customCriterion,
                onValueChange = onCustomCriterionChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("What information do you need?") },
                placeholder = { Text("supply voltage veya besleme gerilimi") },
                supportingText = { Text("Serbest kriter · ${state.customCriterion.length}/500") },
                minLines = 2,
                maxLines = 4,
                enabled = !state.loading,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
            )

            Text("Hızlı seçim · Birden fazla seçebilirsin",
                style = MaterialTheme.typography.titleMedium)

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Criterion.entries.forEach { criterion ->
                    FilterChip(
                        selected = criterion in state.selectedCriteria,
                        onClick = { onCriterionToggled(criterion) },
                        enabled = !state.loading,
                        label = { Text(criterion.label) },
                        leadingIcon = if (criterion in state.selectedCriteria) {
                            { Text("✓") }
                        } else null
                    )
                }
            }

            state.error?.let { error ->
                Text(
                    error,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
            }

            Button(
                onClick = { focusManager.clearFocus(); onSearch() },
                enabled = !state.loading,
                modifier = Modifier.fillMaxWidth()
            ) { Text("SEARCH") }

            OutlinedButton(
                onClick = { focusManager.clearFocus(); onScan() },
                enabled = !state.loading,
                modifier = Modifier.fillMaxWidth()
            ) { Text("SCAN COMPONENT") }
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 900)
@Composable
private fun HomeScreenPreview() {
    DatasheetAnalyzerTheme {
        HomeScreen(
            state = HomeUiState(partNumber = "LM383", selectedCriteria = setOf(Criterion.SUPPLY_VOLTAGE)),
            onPartNumberChanged = {},
            onCustomCriterionChanged = {},
            onCriterionToggled = {},
            onSearch = {},
            onScan = {}
        )
    }
}
```

### app/src/main/java/com/example/datasheetanalyzer/ui/home/HomeUiState.kt

Tam yol: `C:\AndroidProjects\DatasheetAnalyzer-Asama2\DatasheetAnalyzer\app\src\main\java\com\example\datasheetanalyzer\ui\home\HomeUiState.kt`

```kotlin
package com.example.datasheetanalyzer.ui.home

import com.example.datasheetanalyzer.domain.model.Criterion

data class HomeUiState(
    val partNumber: String = "",
    val customCriterion: String = "",
    val selectedCriteria: Set<Criterion> = emptySet(),
    val loading: Boolean = false,
    val error: String? = null
)
```

### app/src/main/java/com/example/datasheetanalyzer/ui/home/HomeViewModel.kt

Tam yol: `C:\AndroidProjects\DatasheetAnalyzer-Asama2\DatasheetAnalyzer\app\src\main\java\com\example\datasheetanalyzer\ui\home\HomeViewModel.kt`

```kotlin
package com.example.datasheetanalyzer.ui.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.example.datasheetanalyzer.domain.model.Criterion
import com.example.datasheetanalyzer.domain.model.PartNumberNormalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class HomeViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {
    private val restoredCriteria =
        savedStateHandle.get<ArrayList<String>>(KEY_CRITERIA).orEmpty()

    private val _uiState = MutableStateFlow(
        HomeUiState(
            partNumber = savedStateHandle[KEY_PART] ?: "",
            customCriterion = savedStateHandle[KEY_CUSTOM] ?: "",
            selectedCriteria = Criterion.entries.filter { it.name in restoredCriteria }.toSet()
        )
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // UI callbacks run on the main thread; persist only small form values.
    private fun updateForm(next: HomeUiState) {
        savedStateHandle[KEY_PART] = next.partNumber
        savedStateHandle[KEY_CUSTOM] = next.customCriterion
        savedStateHandle[KEY_CRITERIA] = ArrayList(next.selectedCriteria.map { it.name })
        _uiState.value = next
    }

    fun onPartNumberChanged(value: String) {
        updateForm(_uiState.value.copy(partNumber = value.take(64), error = null))
    }

    fun onCustomCriterionChanged(value: String) {
        updateForm(_uiState.value.copy(customCriterion = value.take(500), error = null))
    }

    fun onCriterionToggled(criterion: Criterion) {
        val current = _uiState.value
        val next = if (criterion in current.selectedCriteria) {
            current.selectedCriteria - criterion
        } else {
            current.selectedCriteria + criterion
        }
        updateForm(current.copy(selectedCriteria = next, error = null))
    }

    // Both typed and OCR-confirmed inputs use this same form and normalization.
    fun prepareSearch(): Boolean {
        val current = _uiState.value
        if (current.loading) return false
        val normalizedPart = PartNumberNormalizer.normalize(current.partNumber)

        val error = when {
            current.partNumber.isBlank() -> "Lütfen komponent kodunu gir. Örnek: LM383"
            !PartNumberNormalizer.isValid(normalizedPart) ->
                "Komponent kodunda yalnızca A–Z, 0–9, - ve / kullan."
            current.selectedCriteria.isEmpty() && current.customCriterion.isBlank() ->
                "En az bir kriter seç veya öğrenmek istediğin bilgiyi yaz."
            else -> null
        }

        if (error != null) {
            _uiState.value = current.copy(error = error)
            return false
        }

        updateForm(
            current.copy(
                partNumber = normalizedPart,
                customCriterion = current.customCriterion.trim(),
                error = null
            )
        )
        return true
    }

    private companion object {
        const val KEY_PART = "part_number"
        const val KEY_CUSTOM = "custom_criterion"
        const val KEY_CRITERIA = "selected_criteria"
    }
}
```

### app/src/main/java/com/example/datasheetanalyzer/ui/navigation/DatasheetApp.kt

Tam yol: `C:\AndroidProjects\DatasheetAnalyzer-Asama2\DatasheetAnalyzer\app\src\main\java\com\example\datasheetanalyzer\ui\navigation\DatasheetApp.kt`

```kotlin
package com.example.datasheetanalyzer.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.datasheetanalyzer.ui.home.HomeScreen
import com.example.datasheetanalyzer.ui.home.HomeViewModel
import com.example.datasheetanalyzer.ui.scanner.ScannerScreen
import com.example.datasheetanalyzer.ui.scanner.ScannerViewModel
import com.example.datasheetanalyzer.ui.search.SearchPreviewScreen

private object Routes {
    const val HOME = "home"
    const val SEARCH = "search_preview"
    const val SCANNER = "scanner"
}

@Composable
fun DatasheetApp(homeViewModel: HomeViewModel = viewModel()) {
    // Created above NavHost: one Activity-scoped ViewModel for this form.
    val navController = rememberNavController()
    val state by homeViewModel.uiState.collectAsStateWithLifecycle()

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                state = state,
                onPartNumberChanged = homeViewModel::onPartNumberChanged,
                onCustomCriterionChanged = homeViewModel::onCustomCriterionChanged,
                onCriterionToggled = homeViewModel::onCriterionToggled,
                onSearch = {
                    if (homeViewModel.prepareSearch()) {
                        navController.navigate(Routes.SEARCH) { launchSingleTop = true }
                    }
                },
                onScan = {
                    navController.navigate(Routes.SCANNER) { launchSingleTop = true }
                }
            )
        }
        composable(Routes.SEARCH) {
            SearchPreviewScreen(state = state, onBack = { navController.popBackStack() })
        }
        composable(Routes.SCANNER) {
            val scannerViewModel: ScannerViewModel = viewModel()
            ScannerScreen(
                viewModel = scannerViewModel,
                onConfirm = { code ->
                    homeViewModel.onPartNumberChanged(code)
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() }
            )
        }
    }
}
```

### app/src/main/java/com/example/datasheetanalyzer/ui/scanner/ScannerScreen.kt

Tam yol: `C:\AndroidProjects\DatasheetAnalyzer-Asama2\DatasheetAnalyzer\app\src\main\java\com\example\datasheetanalyzer\ui\scanner\ScannerScreen.kt`

```kotlin
package com.example.datasheetanalyzer.ui.scanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.datasheetanalyzer.camera.CameraPreview

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScannerScreen(
    viewModel: ScannerViewModel,
    onConfirm: (String) -> Unit,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    fun hasCameraPermission() = ContextCompat.checkSelfPermission(
        context, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    var granted by remember { mutableStateOf(hasCameraPermission()) }
    var requested by rememberSaveable { mutableStateOf(false) }
    var resumed by remember(owner) {
        mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { allowed -> granted = allowed }

    DisposableEffect(owner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = hasCameraPermission()
                resumed = true
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                resumed = false
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) {
        if (!granted && !requested) {
            requested = true
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold { contentPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(contentPadding).imePadding()
                .verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Komponent tara", style = MaterialTheme.typography.headlineMedium)
            Text("Yazıyı çerçevenin içine getir. Netlemek için görüntüye dokun.")

            if (!granted) {
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Kamera izni gerekli. İzin vermeden manuel giriş kullanabilirsin.")
                        Button(onClick = {
                            requested = true
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        }) { Text("KAMERA İZNİ VER") }
                        OutlinedButton(onClick = {
                            try {
                                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:${context.packageName}")))
                            } catch (_: Exception) {
                                viewModel.onFailure("Uygulama ayarları açılamadı. Telefonun Ayarlar menüsünü kullan.")
                            }
                        }) { Text("UYGULAMA AYARLARI") }
                    }
                }
            } else if (state.scanning && resumed) {
                CameraPreview(
                    modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f),
                    onLines = viewModel::onLines,
                    onError = viewModel::onFailure
                )
                Text(
                    if (!state.hasProcessedFrame) "Kamera ve yazı tanıma hazırlanıyor…"
                    else if (state.candidates.isEmpty()) "Henüz kod adayı bulunamadı. Işığı ve mesafeyi ayarla."
                    else "Bir aday seç; ardından kodu kontrol et.",
                    style = MaterialTheme.typography.bodySmall
                )
            } else if (!state.scanning) {
                Text("Tarama durduruldu. Kodu aşağıdan düzeltebilirsin.")
            }

            if (state.candidates.isNotEmpty()) {
                Text("Kod adayları", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.candidates.forEach { candidate ->
                        AssistChip(onClick = { viewModel.selectCandidate(candidate.code) },
                            label = { Text(candidate.code) })
                    }
                }
            }
            if (state.detectedLines.isNotEmpty()) {
                Text("Algılanan yazı", style = MaterialTheme.typography.titleSmall)
                Text(state.detectedLines.take(8).joinToString("\n"),
                    style = MaterialTheme.typography.bodySmall)
            }

            OutlinedTextField(
                value = state.draft,
                onValueChange = viewModel::editDraft,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Kodu kontrol et / düzelt") },
                placeholder = { Text("Örnek: LM383") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                supportingText = { Text("O/0, I/1 ve S/5 otomatik değiştirilmez.") }
            )
            Text("Adaylar doğrulanmış parça kimliği değildir; tarih veya üretim kodu da olabilir.",
                style = MaterialTheme.typography.bodySmall)
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Button(
                onClick = { viewModel.confirmedCode()?.let(onConfirm) },
                enabled = state.draft.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("CONTINUE · KODU KULLAN") }
            if (!state.scanning) {
                OutlinedButton(onClick = viewModel::rescan, modifier = Modifier.fillMaxWidth()) {
                    Text("YENİDEN TARA")
                }
            }
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("MANUEL GİRİŞE DÖN")
            }
        }
    }
}
```

### app/src/main/java/com/example/datasheetanalyzer/ui/scanner/ScannerViewModel.kt

Tam yol: `C:\AndroidProjects\DatasheetAnalyzer-Asama2\DatasheetAnalyzer\app\src\main\java\com\example\datasheetanalyzer\ui\scanner\ScannerViewModel.kt`

```kotlin
package com.example.datasheetanalyzer.ui.scanner

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.example.datasheetanalyzer.domain.model.PartNumberNormalizer
import com.example.datasheetanalyzer.ocr.PartCandidate
import com.example.datasheetanalyzer.ocr.PartCandidateExtractor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ScannerUiState(
    val scanning: Boolean = true,
    val candidates: List<PartCandidate> = emptyList(),
    val detectedLines: List<String> = emptyList(),
    val draft: String = "",
    val error: String? = null,
    val hasProcessedFrame: Boolean = false
)

class ScannerViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {
    private val extractor = PartCandidateExtractor()
    private val _state = MutableStateFlow(
        ScannerUiState(
            scanning = savedStateHandle["scanning"] ?: true,
            draft = savedStateHandle["draft"] ?: ""
        )
    )
    val state = _state.asStateFlow()

    private fun update(next: ScannerUiState) {
        savedStateHandle["draft"] = next.draft
        savedStateHandle["scanning"] = next.scanning
        _state.value = next
    }

    fun onLines(lines: List<String>) {
        val current = _state.value
        if (!current.scanning) return
        update(current.copy(
            candidates = extractor.extract(lines),
            detectedLines = lines,
            hasProcessedFrame = true,
            error = null
        ))
    }

    fun selectCandidate(code: String) {
        update(_state.value.copy(scanning = false, draft = code, error = null))
    }

    fun editDraft(value: String) {
        // Freeze results when the user starts editing; OCR must never overwrite edits.
        update(_state.value.copy(scanning = false, draft = value.take(64), error = null))
    }

    fun onFailure(message: String) {
        update(_state.value.copy(scanning = false, error = message))
    }

    fun rescan() {
        // Preserve the user's draft while collecting new candidates.
        update(_state.value.copy(scanning = true, candidates = emptyList(),
            detectedLines = emptyList(), error = null, hasProcessedFrame = false))
    }

    fun confirmedCode(): String? {
        val normalized = PartNumberNormalizer.normalize(_state.value.draft)
        if (!PartNumberNormalizer.isValid(normalized)) {
            update(_state.value.copy(error = "Bir kod seç veya A–Z, 0–9, - ve / ile kodu düzelt."))
            return null
        }
        return normalized
    }
}
```

### app/src/main/java/com/example/datasheetanalyzer/ui/search/SearchPreviewScreen.kt

Tam yol: `C:\AndroidProjects\DatasheetAnalyzer-Asama2\DatasheetAnalyzer\app\src\main\java\com\example\datasheetanalyzer\ui\search\SearchPreviewScreen.kt`

```kotlin
package com.example.datasheetanalyzer.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.datasheetanalyzer.domain.model.Criterion
import com.example.datasheetanalyzer.ui.home.HomeUiState

@Composable
fun SearchPreviewScreen(state: HomeUiState, onBack: () -> Unit) {
    Scaffold { contentPadding ->
        Column(
            Modifier.fillMaxSize().padding(contentPadding)
                .verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Arama özeti", style = MaterialTheme.typography.headlineMedium)
            Text(state.partNumber, style = MaterialTheme.typography.headlineSmall)
            Text("Seçilen kriterler", style = MaterialTheme.typography.titleMedium)
            Criterion.entries.filter { it in state.selectedCriteria }.forEach {
                Text("• ${it.label}")
            }
            if (state.customCriterion.isNotBlank()) {
                Text("Serbest kriter", style = MaterialTheme.typography.titleMedium)
                Text(state.customCriterion)
            }
            Card {
                Text(
                    "Girişlerin alındı. Bu ekran yalnızca formu ve ekran geçişini doğrular. " +
                        "Datasheet araması Aşama 3'te eklenecek. Henüz teknik sonuç üretilmedi.",
                    modifier = Modifier.padding(16.dp)
                )
            }
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("GİRDİLERİ DÜZENLE")
            }
        }
    }
}
```

### app/src/main/java/com/example/datasheetanalyzer/ui/theme/Theme.kt

Tam yol: `C:\AndroidProjects\DatasheetAnalyzer-Asama2\DatasheetAnalyzer\app\src\main\java\com\example\datasheetanalyzer\ui\theme\Theme.kt`

```kotlin
package com.example.datasheetanalyzer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF006B5F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF9CF2DF),
    onPrimaryContainer = Color(0xFF00201B),
    secondary = Color(0xFF466179),
    background = Color(0xFFF6FAF8),
    surface = Color(0xFFF6FAF8)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF80D5C3),
    onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF005046),
    onPrimaryContainer = Color(0xFF9CF2DF),
    secondary = Color(0xFFADC9E5),
    background = Color(0xFF101513),
    surface = Color(0xFF101513)
)

@Composable
fun DatasheetAnalyzerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}
```

### app/src/test/java/com/example/datasheetanalyzer/AnalysisGateTest.kt

Tam yol: `C:\AndroidProjects\DatasheetAnalyzer-Asama2\DatasheetAnalyzer\app\src\test\java\com\example\datasheetanalyzer\AnalysisGateTest.kt`

```kotlin
package com.example.datasheetanalyzer

import com.example.datasheetanalyzer.ocr.AnalysisGate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisGateTest {
    @Test fun respectsFourHundredMillisecondBoundary() {
        val gate = AnalysisGate(400)
        assertTrue(gate.tryAcquire(0))
        assertFalse(gate.complete())
        assertFalse(gate.tryAcquire(399))
        assertTrue(gate.tryAcquire(400))
    }

    @Test fun slowRecognitionNeverOverlaps() {
        val gate = AnalysisGate(400)
        assertTrue(gate.tryAcquire(0))
        assertFalse(gate.tryAcquire(2000))
        gate.complete()
        assertTrue(gate.tryAcquire(2000))
    }

    @Test fun stoppingAnInFlightTaskDefersClose() {
        val gate = AnalysisGate()
        assertTrue(gate.tryAcquire(0))
        assertFalse(gate.stop())
        assertTrue(gate.complete())
        assertFalse(gate.tryAcquire(5000))
    }

    @Test fun stoppingIdleTaskAllowsImmediateClose() {
        val gate = AnalysisGate()
        assertTrue(gate.stop())
        assertTrue(gate.isStopped())
        assertFalse(gate.tryAcquire(0))
    }

    @Test fun concurrentFramesOnlyAcquireOnce() {
        val gate = AnalysisGate(0)
        val pool = Executors.newFixedThreadPool(4)
        val start = CountDownLatch(1)
        val finished = CountDownLatch(12)
        val count = AtomicInteger()
        try {
            repeat(12) {
                pool.execute {
                    start.await()
                    if (gate.tryAcquire(0)) count.incrementAndGet()
                    finished.countDown()
                }
            }
            start.countDown()
            assertTrue(finished.await(5, TimeUnit.SECONDS))
            assertEquals(1, count.get())
        } finally { pool.shutdownNow() }
    }
}
```

### app/src/test/java/com/example/datasheetanalyzer/PartCandidateExtractorTest.kt

Tam yol: `C:\AndroidProjects\DatasheetAnalyzer-Asama2\DatasheetAnalyzer\app\src\test\java\com\example\datasheetanalyzer\PartCandidateExtractorTest.kt`

```kotlin
package com.example.datasheetanalyzer

import com.example.datasheetanalyzer.ocr.PartCandidateExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PartCandidateExtractorTest {
    private val extractor = PartCandidateExtractor()

    @Test fun ranksPartAboveShortLotCodeAndExcludesLogo() {
        val codes = extractor.extract(listOf("LM383", "NATIONAL", "J94")).map { it.code }
        assertEquals("LM383", codes.first())
        assertFalse("NATIONAL" in codes)
        assertTrue("J94" in codes) // Ambiguous short codes remain unverified candidates.
    }

    @Test fun joinsSplitPrefixWithoutJoiningManufacturer() {
        val codes = extractor.extract(listOf("LM 383", "NATIONAL LM383")).map { it.code }
        assertTrue("LM383" in codes)
        assertFalse("NATIONALLM383" in codes)
    }

    @Test fun deduplicatesCaseVariantsAndKeepsSupportedParts() {
        val codes = extractor.extract(listOf("lm358 LM358", "NE555 BC547", "1N4007 IRLZ44N")).map { it.code }
        assertEquals(1, codes.count { it == "LM358" })
        assertTrue(codes.containsAll(listOf("NE555", "BC547", "1N4007", "IRLZ44N")))
    }

    @Test fun rejectsPlainTextAndPureDates() {
        assertTrue(extractor.extract(listOf("NATIONAL", "2026", "?", "123456")).isEmpty())
    }

    @Test fun keepsOcrMistakesForHumanCorrection() {
        val codes = extractor.extract(listOf("LM3B3", "LMO83")).map { it.code }
        assertTrue("LM3B3" in codes)
        assertTrue("LMO83" in codes)
        assertFalse("LM383" in codes)
    }
}
```

### app/src/test/java/com/example/datasheetanalyzer/PartNumberNormalizerTest.kt

Tam yol: `C:\AndroidProjects\DatasheetAnalyzer-Asama2\DatasheetAnalyzer\app\src\test\java\com\example\datasheetanalyzer\PartNumberNormalizerTest.kt`

```kotlin
package com.example.datasheetanalyzer

import com.example.datasheetanalyzer.domain.model.PartNumberNormalizer
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PartNumberNormalizerTest {
    @Test fun removesSpacesAndUppercases() {
        assertEquals("LM383", PartNumberNormalizer.normalize(" lm 383 \n"))
    }

    @Test fun keepsAmbiguousCharacters() {
        assertEquals("LM3B3-OI1S5", PartNumberNormalizer.normalize("lm3b3-OI1S5"))
    }

    @Test fun keepsPackageSuffixes() {
        assertEquals("LM358-N/P", PartNumberNormalizer.normalize(" lm358-n/p "))
    }

    @Test fun independentOfTurkishLocale() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals("IRLZ44N", PartNumberNormalizer.normalize("irlz44n"))
        } finally { Locale.setDefault(original) }
    }

    @Test fun rejectsUnsupportedInputWithoutSilentlyRemovingIt() {
        assertFalse(PartNumberNormalizer.isValid(PartNumberNormalizer.normalize("LM_383")))
        assertFalse(PartNumberNormalizer.isValid(""))
        assertFalse(PartNumberNormalizer.isValid("A".repeat(65)))
        assertTrue(PartNumberNormalizer.isValid("1N4007"))
    }
}
```

AŞAMA 2 kaynak kodu ve rehberi tamamlandı. Tam Android derlemesi ve cihaz kabul testleri bekliyor. Uygulama sende de doğru çalışıyorsa DEVAM yaz; ardından Aşama 3'e geçeceğiz.
