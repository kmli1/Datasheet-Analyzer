# Datasheet Analyzer — Aşama 3 (0.3.0)

Komponent kodunu elle veya cihaz üzerinde OCR ile alır; API anahtarı gerektirmeyen HTML aramasıyla datasheet kaynak adaylarını bulur, alan adı ve parça eşleşmesine göre sıralar.

## Bu sürümde

- Siyah zemin, turuncu vurgular, devre izi detayları ve yıldırım simgesiyle yenilenen arayüz.
- DuckDuckGo HTML üzerinden aday URL keşfi; anahtar, arama API'si veya LLM yok.
- Üretici/distribütör alan adı kaydı, alt alan adı sınır kontrolü ve kaynak puanlama.
- Tam parça kodu / aile-varyant ayrımı, yinelenen URL eleme ve açıklanabilir aday seçimi.
- Bağlantı, zaman aşımı, doğrulama sayfası, hız sınırı ve değişen HTML için hata durumları.
- Önceki kamera/OCR girişi ve kriter seçimleri korunur; iki giriş aynı aramayı kullanır.

Bu aşama yalnızca kaynak adaylarını bulur. PDF indirme/açma, PDF güvenlik taraması, belge kimlik doğrulaması ve teknik parametre çıkarımı henüz uygulanmadı. Alan adı ve arama başlığı, PDF içeriğinin doğruluğunu veya güvenliğini kanıtlamaz. Uygulama antivirus değildir.

## Kurulum

1. ZIP'i yeni klasöre çıkar: `C:\AndroidProjects\DatasheetAnalyzer-Asama3\DatasheetAnalyzer`.
2. Android Studio > Open ile `settings.gradle.kts` bulunan klasörü aç. Bu tam projedir; eski sürümü üzerine kopyalamak gerekmez.
3. AGP 8.12.0 destekleyen Android Studio, Gradle JDK 17, Android SDK Platform 36, Build-Tools 35.0.0 ve Platform-Tools kullan.
4. Gradle Sync'i tamamla. İlk bağımlılık indirmeleri için internet gerekir.
5. Android 8.0/API 26 veya üstü cihazını bağlayıp Run app çalıştır.

AGP **8.12.0**, Gradle **8.13**, Kotlin/Compose compiler **2.2.10** sabitlendi; önceki AGP 9.0.1 uyumsuzluğu bu pakette korunmuyor. `gradle-wrapper.jar` pakete dahildir.

## APK ve test

Proje kökünde PowerShell:

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
```

macOS/Linux: `chmod +x gradlew` ardından `./gradlew :app:testDebugUnitTest :app:assembleDebug`.

Başarılı derleme APK yolu: `app/build/outputs/apk/debug/app-debug.apk`.
Windows için `APK_OLUSTUR.bat` da dahildir; önce Android Studio Sync ve SDK ayarını tamamla. Terminalin Java sürümünü `java -version` ile kontrol et. Betik Windows'ta bu ortamda çalıştırılmadı.

**Bu teslimatta hazır APK yok.** Yerel Gradle 8.13 denemesi, önbellekteki bozuk JAR nedeniyle Android derlemesine ulaşmadan durdu (`zip END header not found`). Tam Android derlemesi, hedef bağımlılık sürümleriyle Gradle testleri, canlı web araması ve telefon/emülatör kontrolleri doğrulanamadı.

**44 bağımsız JVM testi geçti.** Bu kontrol Kotlin 2.0.21, JUnit 4.13.2, jsoup 1.15.3 ve coroutines 1.6.4 ile çalıştırıldı. Projenin hedef sürümleri farklıdır; sonuç tam Android derlemesinin yerine geçmez. Ayrıntılar `docs/VERIFICATION.md` ve ham sonuç `docs/JVM_TEST_RESULT.txt` içindedir.

## İlk deneme

`LM383` yaz, Supply Voltage ve Pinout seç, **DATASHEET BUL** düğmesine bas. Arama tamamlanırsa adayların alan adı/eşleşme bilgisini incele. Uygun aday seçilebilir; seçim dosya indirmez. Gerçek kaynağın erişimine göre sonuçsuzluk veya doğrulama/hız sınırı hatası görülebilir. Sonuç garantisi yoktur.

## Ağ ve gizlilik

Arama sırasında normalize edilmiş parça kodu DuckDuckGo HTML kaynağına gönderilir; kaynak IP adresini de görür. Kriterler sorguya eklenmez. Kamera görüntüleri OCR için cihazda işlenir; fotoğraf/video kaydı ve buluta görüntü gönderimi uygulanmadı. INTERNET izni bu aşamada eklendi. HTTPS zorunludur; CAPTCHA aşma ve otomatik tekrar deneme yoktur.

## Belgeler ve sonraki adım

Kurulum, test matrisi, mimari, sınırlamalar ve tam dosya kodları: `docs/ASAMA_3_REHBER.md`.
Bu sürüm Aşama 3'te durur. Aşama 4: karantina indirmesi ve PDF güvenlik hattı için ayrıca DEVAM onayı beklenir.

Önerilen commit: `a03: add keyless datasheet resolver and source scoring`. Bu teslimatta uzak repo verilmediği için commit/push yapılmadı.


## Aşama 3 mimarisi

HomeScreen/ScannerScreen aynı HomeViewModel.partNumber alanını besler. SearchScreen → SearchViewModel → DatasheetRepository → SearchProvider üzerinden HTML adayları alınır; CandidateSelector adayları sınıflandırır ve sıralar. SearchProvider değiştirilebilir bir arayüzdür. Arama motoru güvenilir datasheet kaynağı olarak kaydedilmez.

| Dosya/modül | Görev |
| --- | --- |
| domain/model/SearchModels.kt | Aday, sıralama, eşleşme ve hata modelleri |
| domain/resolver/SearchProvider.kt | Askıya alınabilir arama arayüzü |
| domain/resolver/TrustedDomainRegistry.kt | Üretici, distribütör, bilinmeyen ve engellenen alan adı sınıfları |
| domain/resolver/CandidateUrlPolicy.kt | HTTPS, kimlik bilgisi/port/IP kontrolleri; URL normalizasyonu |
| domain/resolver/PartNumberMatcher.kt | Tam kod ve aile/varyant ayrımı |
| domain/resolver/CandidateSelector.kt | Puanlama, uygunluk, tekilleştirme ve önerilen aday |
| data/network/KeylessHtmlSearchProvider.kt | Tırnaklı sorgu ve istek başlangıçları arasında en az 3 saniye |
| data/network/DuckDuckGoHtmlParser.kt | Sonuç, reklam, boş yanıt ve doğrulama sayfası ayrımı |
| data/network/OkHttpHtmlFetcher.kt | Sınırlı HTML yanıtı, HTTPS, zaman aşımı ve iptal |
| data/repository/DatasheetRepository.kt | Arama ve seçiciyi IO bağlamında birleştirme |
| ui/search/SearchViewModel.kt | Yükleniyor, hata, yeniden deneme ve aday seçimi durumları |

Yukarıdaki dosyalar `app/src/main/java/com/example/datasheetanalyzer/` altındadır. Tam yollar ve içerikleri rehberin sonunda yer alır.

## Kaynak kayıtları ve puanlar

Level A: ti.com, st.com, analog.com, onsemi.com, nxp.com, infineon.com, microchip.com, renesas.com, vishay.com, rohm.com, toshiba-semicon-storage.com.

Level B: digikey.com, mouser.com. Nokta sınırıyla bu alanların alt alanları desteklenir. `evilti.com` veya `ti.com.evil.test` üretici kabul edilmez. Bu kaydın dışındaki ayrı CDN alanları bilinmeyen sayılır. Doğrulanmış arşiv listesi bulunmadığından Level C etkin değildir. Forum/blog/community yolları ve URL kısaltıcıları seçime kapalıdır.

| Sinyal | Puan |
| --- | ---: |
| Üretici | +40 |
| Distribütör | +30 |
| Başlıkta tam kod | +25 |
| URL'de tam kod | +15 |
| Datasheet ifadesi | +10 |
| URL yolunda .pdf uzantısı | +5 |
| Bilinmeyen alan adı | −30 |
| Engellenen kaynak türü | −50 |

Seçilebilir olmak için A/B kaynak, tam kod eşleşmesi, datasheet/PDF ipucu ve en az 65 puan gerekir. Başlık tam kod verirken URL yalnızca başka varyantı gösteriyorsa seçim engellenir. Sıralama önce seçilebilir adayları, sonra tam eşleşmeyi ve puanı dikkate alır. Ham aramanın ilk sonucu otomatik seçilip indirilmez.

`LM383` tam; `LM383A` ve `LM383T` aile/varyant; `TPS3831`, `OPA383`, `LM2833` eşleşme yok olarak değerlendirilir. Yalnızca açıklama parçasında geçen kod kimlik doğrulaması sayılmaz. Bu metin tabanlı ön eşleştirme muhafazakârdır; bazı geçerli aile belgeleri seçime kapalı kalabilir. Doküman kimliği sonraki aşamada belge içinden doğrulanmalıdır.

95 gibi bir puan olasılık/yüzde değildir. `.pdf` uzantısı dosya türünü kanıtlamaz. Üretici alan adı, o alanın her sayfasının ilgili ve güvenli bir datasheet olduğunu garanti etmez. PDF MIME/magic-byte/içerik kontrolleri henüz yoktur; dosya bu sürümde indirilmez veya açılmaz.

## Ağ davranışı

Örnek sorgu: `"LM383" datasheet pdf`. Kullanıcı eylemi başına tek sorgu vardır. Arama kaynağının HTML yapısı veya erişim politikası değişebilir. CAPTCHA, erişim engeli veya HTML değişikliği hata olarak gösterilir; otomatik aşılmaya çalışılmaz. Alternatif API/anahtar gerektiren bir servise geçiş yapılmaz.

Bağlantı 6 saniye, okuma 8 saniye, tek çağrı 10 saniye ve arama işlemi 20 saniye ile sınırlandırılmıştır. En fazla iki yönlendirme yalnızca izinli arama alanları arasında izlenir. HTML yanıtı 512 KiB ile sınırlıdır; parser en fazla 25 aday alır. Otomatik retry kapalıdır. 429/503 yanıtları Retry-After ile, 202/403 yanıtları bekleme hatasıyla ele alınır. Gerçek ağ davranışı henüz cihazda doğrulanmadı.

## Tema

| Öğe | Renk / davranış |
| --- | --- |
| Ana zemin | #0B0B0E |
| Kartlar | #141417 |
| Elektrik turuncusu | #FF8A32 |
| Ana metin | #F3F0EB |
| İkincil metin | #B9B6B0 |
| Vurgu zemini | #3B2314 |

Tema cihazın açık/koyu ayarından bağımsız olarak koyudur. Ana ekranda devre izi, yıldırım simgesi, numaralı giriş kartları ve turuncu seçim vurguları bulunur. Kamera çerçevesi ve kaynak kartları da aynı paleti kullanır. Compose Preview eklendi; bu ortamda ekran görüntüsü veya telefon üstünde görsel kontrol yapılmadı.

## Telefonda uygulanacak test matrisi

Bu liste uygulanacak testlerdir; tamamlandı iddiası değildir.

| Deneme | Beklenen davranış |
| --- | --- |
| Uygulamayı aç | Siyah zemin, turuncu vurgu, okunaklı girişler |
| Büyük yazı boyutu/küçük ekran | Kaydırma çalışır, giriş ve arama düğmesine ulaşılır |
| ` lm383 ` yaz | Arama için LM383 normalizasyonu |
| LM383 + Supply Voltage + Pinout | Kaynaklar ekranında kod ve kriterler korunur |
| LM358, NE555, BC547, 1N4007, IRLZ44N | Gerçek adaylar veya açıklanmış sonuçsuzluk/hata; uydurulmuş değer yok |
| Aday ayrıntısını aç | Alan adı, eşleşme, URL ve puan gerekçeleri görünür |
| Uygun adayı seç | Seçildi durumu; dosya indirilmez/açılmaz |
| Yalnızca bilinmeyen/varyant sonuçlar | Seçim ölçütlerini karşılayan kaynak yok durumu |
| İnterneti kapatıp ara | Bağlantı hatası ve yeniden dene seçeneği |
| Arama sırasında geri/iptal | Ana ekrana dön; istek iptal edilir |
| Hızlı arka arkaya yeniden deneme | Bekleme/hız sınırı mesajı, istek yağmuru yok |
| Arama sırasında ekran çevir | Aynı ViewModel korunur; gereksiz ikinci sorgu başlamaz |
| Kamera iznini reddet | Manuel giriş kullanılabilir |
| Kamerayla LM383 oku, düzelt, onayla | Ana giriş güncellenir, kriterler korunur |
| OCR düzenleme sırasında yeni görüntü | Kullanıcının yazdığı kodun üzerine yazılmaz |
| Çevrimdışı kamera/OCR | Gömülü modelle OCR denenebilir; web araması bağlantı ister |

Canlı kaynak doğrulama istediğinde sonuç yerine açık hata görmek meşru bir durumdur. LM383 dahil hiçbir kod için çevrimiçi sonuç bulunması bu teslimatta doğrulanmadı.

## Hata gönderme ve değişiklikler

Gradle Sync/Build başarısız olursa Build Output içindeki **ilk hata bloğunu**, Android Studio sürümünü ve Gradle JDK bilgisini gönder. Bütün projeyi yeniden kurmak yerine hata veren dosya/ayar üzerinden ilerlenir. Kamerada sorun olursa telefon modeli, Android sürümü ve izin durumunu da ekle.

Temel Gradle değişiklikleri: kök `build.gradle.kts` AGP 8.12.0 ve Kotlin eklentileri; `gradle/wrapper/gradle-wrapper.properties` Gradle 8.13; `app/build.gradle.kts` OkHttp 4.12.0, jsoup 1.23.2, desugar_jdk_libs 2.1.5 ve sürüm 0.3.0. CameraX/ML Kit önceki aşamadan korunmuştur. Bu sürümler en güncel oldukları iddiasıyla verilmemiştir.

Resmî başvuru adresleri:

- https://developer.android.com/build/releases/agp-8-12-0-release-notes
- https://jsoup.org/download
- https://github.com/google/desugar_jdk_libs
- https://square.github.io/okhttp/

## Aşama sınırı

Aşama 3 kaynak kodu ve bağımsız JVM testleri hazırdır; cihaz kabul testi beklenir. Aşama 4'e geçilmedi. Sonraki aşamada karantina indirmesi, PDF tür/boyut/aktif içerik kontrolleri ele alınacak; PDF kimliği, izole ayrıştırma, kriter motoru ve önbellek kendi aşamalarında uygulanacaktır.

## Test kaydı

# Aşama 3 doğrulama kaydı

## Çalıştırılan kontrol

44 JUnit testi geçti. Gerçek proje kaynaklarının Android'den bağımsız bölümleri yerel Kotlin JVM derleyicisi ile derlendi; sentetik HTML ve sahte ağ yanıtları kullanıldı.

| Kapsam | Test sayısı | Sonuç |
| --- | ---: | --- |
| Parça normalizasyonu, OCR aday çıkarımı, OCR işlem kapısı | 15 | Geçti |
| Alan adı, URL politikası, parça eşleşmesi, aday sıralama | 19 | Geçti |
| HTML ayrıştırma, hız sınırı, iptal, repository entegrasyonu | 10 | Geçti |
| Toplam | 44 | Geçti |

Yerel test araçları: Kotlin 2.0.21, JUnit 4.13.2, jsoup 1.15.3, coroutines-core 1.6.4; JVM hedefi 17. Projede yapılandırılan sürümler: Kotlin 2.2.10, jsoup 1.23.2, coroutines 1.10.2. Bağımlılık erişim kısıtı nedeniyle yerelde mevcut araçlar kullanıldı; hedef sürümlerle Gradle testi yapılmış sayılmaz.

Testler; sahte üretici alan adını, güvenilmeyen kaynakları, URL kullanıcı bilgisini/HTTP/port/IP girişlerini, varyantları, reklamı, CAPTCHA işaretini, beklenmeyen HTML'yi, boş sonucu, yinelenen URL'yi ve sorgu normalizasyonunu kapsar. Repository entegrasyon testi gerçek parser/provider/selector sınıflarını sahte HtmlFetcher ile bağlar. Canlı arama değildir.

## Derleme engeli

Gradle 8.13 ile offline `:app:assembleDebug` denemesi önbellek JAR hatasında durdu. Android kaynakları derlenmedi; APK oluşmadı. Kaynak kodunun Android API uyumluluğu bu kontrolle kanıtlanmadı. İlk tam doğrulama için internet erişimi olan Android Studio ortamında README komutlarını çalıştır.

## Henüz doğrulanmayanlar

- Hedef sürümlerle tam Android/Compose/OkHttp derlemesi ve manifest birleştirme.
- Canlı DuckDuckGo erişimi, gerçek sonuç HTML'si ve gerçek datasheet bulunabilirliği.
- OkHttp yönlendirme, TLS, HTTP durum kodları, stream boyut sınırı ve iptal davranışının gerçek ağ testi.
- Telefon/emülatörde kamera, OCR, ekran döndürme, izin, TalkBack, yazı ölçeği ve tema görünümü.
- Windows APK betiği ve APK kurulumu.
- Gerçek cihaz hız/performance ölçümü.

XML dosyaları temel sözdizimi bakımından ayrıştırıldı; ZIP bütünlüğü kontrol edildi. Bu işlemler Android derlemesi veya görsel doğrulama değildir.

## Tam dosya yolları ve kodlar


### C:/AndroidProjects/DatasheetAnalyzer-Asama3/DatasheetAnalyzer/.gitignore

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

### C:/AndroidProjects/DatasheetAnalyzer-Asama3/DatasheetAnalyzer/APK_OLUSTUR.bat

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

### C:/AndroidProjects/DatasheetAnalyzer-Asama3/DatasheetAnalyzer/app/build.gradle.kts

```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.datasheetanalyzer"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.datasheetanalyzer"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "0.3.0"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
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
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jsoup:jsoup:1.23.2")
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

### C:/AndroidProjects/DatasheetAnalyzer-Asama3/DatasheetAnalyzer/app/src/main/AndroidManifest.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-feature android:name="android.hardware.camera" android:required="false" />
    <uses-feature android:name="android.hardware.camera.autofocus" android:required="false" />
    <application
        android:allowBackup="false"
        android:label="@string/app_name"
        android:icon="@drawable/ic_launcher"
        android:roundIcon="@drawable/ic_launcher"
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

### C:/AndroidProjects/DatasheetAnalyzer-Asama3/DatasheetAnalyzer/app/src/main/java/com/example/datasheetanalyzer/MainActivity.kt

```kotlin
package com.example.datasheetanalyzer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.datasheetanalyzer.ui.navigation.DatasheetApp
import com.example.datasheetanalyzer.ui.theme.DatasheetAnalyzerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.rgb(11, 11, 14))
        )
        setContent {
            DatasheetAnalyzerTheme {
                DatasheetApp()
            }
        }
    }
}
```

### C:/AndroidProjects/DatasheetAnalyzer-Asama3/DatasheetAnalyzer/app/src/main/java/com/example/datasheetanalyzer/camera/CameraPreview.kt

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
            drawRect(Color(0xFFFF8A32), Offset(left, top),
                ComposeSize(right - left, bottom - top), style = Stroke(2.dp.toPx()))
        }
    }
}
```

### C:/AndroidProjects/DatasheetAnalyzer-Asama3/DatasheetAnalyzer/app/src/main/java/com/example/datasheetanalyzer/camera/FrameCropper.kt

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

### C:/AndroidProjects/DatasheetAnalyzer-Asama3/DatasheetAnalyzer/app/src/main/java/com/example/datasheetanalyzer/camera/ScanRegion.kt

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

### C:/AndroidProjects/DatasheetAnalyzer-Asama3/DatasheetAnalyzer/app/src/main/java/com/example/datasheetanalyzer/data/network/DuckDuckGoHtmlParser.kt

```kotlin
package com.example.datasheetanalyzer.data.network

import com.example.datasheetanalyzer.domain.model.SearchCandidate
import com.example.datasheetanalyzer.domain.model.SearchFailure
import com.example.datasheetanalyzer.domain.model.SearchFailureKind
import java.net.URI
import java.net.URLDecoder
import org.jsoup.Jsoup

class DuckDuckGoHtmlParser {
    fun parse(html: String): List<SearchCandidate> {
        val doc = Jsoup.parse(html, "https://html.duckduckgo.com/html/")
        if (doc.select("#challenge-form, .anomaly-modal, form[action*=anomaly]").isNotEmpty()) {
            throw SearchFailure(SearchFailureKind.BLOCKED, "Arama kaynağı doğrulama istedi. Daha sonra yeniden dene.")
        }
        val candidates = doc.select(".result").filter {
            !it.hasClass("result--ad") && it.select(".result__badge--ad, .result__ad").isEmpty()
        }.mapNotNull { block ->
            val link = block.selectFirst("a.result__a") ?: return@mapNotNull null
            val url = unwrap(link.absUrl("href")) ?: return@mapNotNull null
            SearchCandidate(clean(link.text(), 240), url,
                clean(block.selectFirst(".result__snippet")?.text().orEmpty(), 600))
        }.take(25)
        if (candidates.isNotEmpty()) return candidates
        if (doc.select(".no-results, .result--no-result").isNotEmpty()) return emptyList()
        // An unexpected/challenge page must not be reported as a genuine zero-result query.
        throw SearchFailure(SearchFailureKind.FORMAT_CHANGED,
            "Arama sayfası okunamadı veya biçimi değişti. Daha sonra yeniden dene.")
    }

    private fun unwrap(raw: String): String? = runCatching {
        val uri = URI(raw)
        val host = uri.host.orEmpty().lowercase()
        if (host == "duckduckgo.com" || host == "html.duckduckgo.com") {
            require(uri.path == "/l/" || uri.path == "/l")
            val value = uri.rawQuery.orEmpty().split('&').firstOrNull { it.startsWith("uddg=") }
                ?.substringAfter('=') ?: return null
            URLDecoder.decode(value, "UTF-8")
        } else raw
    }.getOrNull()

    private fun clean(value: String, limit: Int) = value.filter {
        !it.isISOControl() && Character.getType(it) != Character.FORMAT.toInt()
    }.take(limit)
}
```

### C:/AndroidProjects/DatasheetAnalyzer-Asama3/DatasheetAnalyzer/app/src/main/java/com/example/datasheetanalyzer/data/network/HtmlFetcher.kt

```kotlin
package com.example.datasheetanalyzer.data.network

interface HtmlFetcher {
    suspend fun fetch(url: String): String
}
```

### C:/AndroidProjects/DatasheetAnalyzer-Asama3/DatasheetAnalyzer/app/src/main/java/com/example/datasheetanalyzer/data/network/KeylessHtmlSearchProvider.kt

```kotlin
package com.example.datasheetanalyzer.data.network

import com.example.datasheetanalyzer.domain.model.PartNumberNormalizer
import com.example.datasheetanalyzer.domain.model.SearchCandidate
import com.example.datasheetanalyzer.domain.model.SearchFailure
import com.example.datasheetanalyzer.domain.model.SearchFailureKind
import com.example.datasheetanalyzer.domain.resolver.SearchProvider
import java.net.URLEncoder
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class KeylessHtmlSearchProvider(
    private val fetcher: HtmlFetcher,
    private val parser: DuckDuckGoHtmlParser = DuckDuckGoHtmlParser(),
    private val clockMs: () -> Long = { System.nanoTime() / 1_000_000L }
) : SearchProvider {
    private val mutex = Mutex()
    private var lastRequest: Long? = null

    override suspend fun searchDatasheets(partNumber: String): List<SearchCandidate> = mutex.withLock {
        val part = PartNumberNormalizer.normalize(partNumber)
        require(PartNumberNormalizer.isValid(part))
        val now = clockMs()
        val last = lastRequest
        if (last != null && now - last < 3000) {
            throw SearchFailure(SearchFailureKind.RATE_LIMITED, "Yeni arama için birkaç saniye bekle.")
        }
        lastRequest = now
        val query = URLEncoder.encode("\"$part\" datasheet pdf", "UTF-8")
        parser.parse(fetcher.fetch("https://html.duckduckgo.com/html/?q=$query"))
    }
}
```

### C:/AndroidProjects/DatasheetAnalyzer-Asama3/DatasheetAnalyzer/app/src/main/java/com/example/datasheetanalyzer/data/network/OkHttpHtmlFetcher.kt

```kotlin
package com.example.datasheetanalyzer.data.network

import com.example.datasheetanalyzer.domain.model.SearchFailure
import com.example.datasheetanalyzer.domain.model.SearchFailureKind
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class OkHttpHtmlFetcher : HtmlFetcher {
    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false)
        .retryOnConnectionFailure(false).build()
    private val hosts = setOf("html.duckduckgo.com", "duckduckgo.com")
    @Volatile private var retryAtMs = 0L

    override suspend fun fetch(url: String): String {
        if (System.currentTimeMillis() < retryAtMs) {
            throw SearchFailure(SearchFailureKind.RATE_LIMITED, "Arama kaynağı bekleme istedi. Daha sonra yeniden dene.")
        }
        var current = url
        repeat(3) { redirectIndex ->
            val uri = URI(current)
            require(uri.scheme == "https" && uri.host in hosts && uri.rawUserInfo == null &&
                (uri.port == -1 || uri.port == 443))
            val result = request(current)
            if (result.status in setOf(301, 302, 303, 307, 308)) {
                val location = result.location ?: throw SearchFailure(SearchFailureKind.HTTP, "Arama yönlendirmesi eksik.")
                if (redirectIndex == 2) throw SearchFailure(SearchFailureKind.HTTP, "Arama kaynağı çok fazla yönlendirme yaptı.")
                current = uri.resolve(location).toString()
            } else {
                when (result.status) {
                    200 -> return result.html
                    202, 403 -> {
                        retryAtMs = System.currentTimeMillis() + 60_000
                        throw SearchFailure(SearchFailureKind.BLOCKED,
                            "Arama kaynağı erişimi sınırladı veya doğrulama istedi. En az bir dakika sonra yeniden dene.")
                    }
                    429, 503 -> {
                        val seconds = retrySeconds(result.retryAfter)
                        retryAtMs = System.currentTimeMillis() + seconds * 1000
                        throw SearchFailure(SearchFailureKind.RATE_LIMITED,
                            "Arama kaynağı yoğun. Yaklaşık $seconds saniye sonra yeniden dene.")
                    }
                    else -> throw SearchFailure(SearchFailureKind.HTTP, "Arama kaynağı yanıt vermedi (HTTP ${result.status}).")
                }
            }
        }
        throw SearchFailure(SearchFailureKind.HTTP, "Arama yönlendirmesi tamamlanamadı.")
    }

    private fun retrySeconds(header: String?): Long {
        val numeric = header?.toLongOrNull()
        val dateSeconds = runCatching {
            (ZonedDateTime.parse(header, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() -
                System.currentTimeMillis()) / 1000
        }.getOrNull()
        return (numeric ?: dateSeconds ?: 60L).coerceIn(3L, 3600L)
    }

    private suspend fun request(url: String): HtmlResponse = suspendCancellableCoroutine { continuation ->
        val request = Request.Builder().url(url)
            .header("User-Agent", "DatasheetAnalyzer/0.3 (Android; datasheet discovery)")
            .header("Accept", "text/html").get().build()
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                val failure = when (e) {
                    is UnknownHostException -> SearchFailure(SearchFailureKind.CONNECTION, "İnternet veya DNS bağlantısı kurulamadı.")
                    is SocketTimeoutException -> SearchFailure(SearchFailureKind.TIMEOUT, "Arama zaman aşımına uğradı.")
                    is SSLException -> SearchFailure(SearchFailureKind.CONNECTION, "Güvenli bağlantı kurulamadı.")
                    else -> SearchFailure(SearchFailureKind.CONNECTION, "Arama bağlantısı kesildi. İnternetini kontrol et.")
                }
                if (continuation.isActive) continuation.resumeWithException(failure)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val result = response.use {
                        val html = if (it.code == 200) readHtml(it) else ""
                        HtmlResponse(it.code, it.header("Location"), it.header("Retry-After"), html)
                    }
                    if (continuation.isActive) continuation.resume(result)
                } catch (e: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
            }
        })
    }

    private fun readHtml(response: Response): String {
        val body = response.body ?: throw SearchFailure(SearchFailureKind.HTTP, "Arama yanıtı boş.")
        val type = body.contentType()
        if (type?.subtype != "html" && type?.subtype != "xhtml+xml") {
            throw SearchFailure(SearchFailureKind.FORMAT_CHANGED, "Arama kaynağı HTML sayfası döndürmedi.")
        }
        val limit = 512 * 1024
        if (body.contentLength() > limit) throw SearchFailure(SearchFailureKind.RESPONSE_TOO_LARGE, "Arama sayfası boyut sınırını aştı.")
        val output = ByteArrayOutputStream()
        body.byteStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count == -1) break
                if (output.size() + count > limit) throw SearchFailure(SearchFailureKind.RESPONSE_TOO_LARGE, "Arama sayfası boyut sınırını aştı.")
                output.write(buffer, 0, count)
            }
        }
        return output.toString(type?.charset(Charsets.UTF_8)?.name() ?: "UTF-8")
    }

    private data class HtmlResponse(val status: Int, val location: String?, val retryAfter: String?, val html: String)
}
```

### C:/AndroidProjects/DatasheetAnalyzer-Asama3/DatasheetAnalyzer/app/src/main/java/com/example/datasheetanalyzer/data/repository/DatasheetRepository.kt

```kotlin
package com.example.datasheetanalyzer.data.repository

import com.example.datasheetanalyzer.domain.model.CandidateSelection
import com.example.datasheetanalyzer.domain.resolver.CandidateSelector
import com.example.datasheetanalyzer.domain.resolver.SearchProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DatasheetRepository(
    private val provider: SearchProvider,
    private val selector: CandidateSelector = CandidateSelector()
) {
    suspend fun search(partNumber: String): CandidateSelection = withContext(Dispatchers.IO) {
        selector.select(partNumber, provider.searchDatasheets(partNumber))
    }
}
```

### C:/AndroidProjects/DatasheetAnalyzer-Asama3/DatasheetAnalyzer/app/src/main/java/com/example/datasheetanalyzer/data/repository/ResolverDependencies.kt

```kotlin
package com.example.datasheetanalyzer.data.repository

import com.example.datasheetanalyzer.data.network.KeylessHtmlSearchProvider
import com.example.datasheetanalyzer.data.network.OkHttpHtmlFetcher

object ResolverDependencies {
    val repository: DatasheetRepository by lazy {
        DatasheetRepository(KeylessHtmlSearchProvider(OkHttpHtmlFetcher()))
    }
}
```

### C:/AndroidProjects/DatasheetAnalyzer-Asama3/DatasheetAnalyzer/app/src/main/java/com/example/datasheetanalyzer/domain/model/Criterion.kt

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

### C:/AndroidProjects/DatasheetAnalyzer-Asama3/DatasheetAnalyzer/app/src/main/java/com/example/datasheetanalyzer/domain/model/PartNumberNormalizer.kt

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

### C:/AndroidProjects/DatasheetAnalyzer-Asama3/DatasheetAnalyzer/app/src/main/java/com/example/datasheetanalyzer/domain/model/SearchModels.kt

```kotlin
package com.example.datasheetanalyzer.domain.model

data class SearchCandidate(val title: String, val url: String, val snippet: String = "")
enum class PartMatch { EXACT, FAMILY_VARIANT, NONE }
enum class TrustLevel { MANUFACTURER, DISTRIBUTOR, UNKNOWN, BLOCKED }
data class SourceTrust(val level: TrustLevel, val label: String)
data class RankedCandidate(
    val candidate: SearchCandidate,
    val canonicalUrl: String,
    val host: String,
    val source: SourceTrust,
    val match: PartMatch,
    val score: Int,
    val selectable: Boolean,
    val reasons: List<String>
)
data class CandidateSelection(
    val candidates: List<RankedCandidate>,
    val recommendedUrl: String?,
    val discardedCount: Int
)
enum class SearchFailureKind { CONNECTION, TIMEOUT, BLOCKED, RATE_LIMITED, FORMAT_CHANGED, RESPONSE_TOO_LARGE, HTTP }
class SearchFailure(val kind: SearchFailureKind, message: String) : Exception(message)
```

### C:/AndroidProjects/DatasheetAnalyzer-Asama3/DatasheetAnalyzer/app/src/main/java/com/example/datasheetanalyzer/domain/resolver/CandidateSelector.kt

```kotlin
package com.example.datasheetanalyzer.domain.resolver

import com.example.datasheetanalyzer.domain.model.CandidateSelection
import com.example.datasheetanalyzer.domain.model.PartMatch
import com.example.datasheetanalyzer.domain.model.RankedCandidate
import com.example.datasheetanalyzer.domain.model.SearchCandidate
import com.example.datasheetanalyzer.domain.model.TrustLevel
import java.util.Locale

class CandidateSelector(
    private val registry: TrustedDomainRegistry = TrustedDomainRegistry(),
    private val matcher: PartNumberMatcher = PartNumberMatcher()
) {
    fun select(partNumber: String, candidates: List<SearchCandidate>): CandidateSelection {
        var discarded = 0
        val ranked = candidates.take(50).mapNotNull { candidate ->
            val url = CandidateUrlPolicy.parse(candidate.url)
            if (url == null) { discarded++; return@mapNotNull null }
            val source = registry.classify(url)
            val titleMatch = matcher.match(partNumber, candidate.title)
            val urlMatch = matcher.matchUrl(partNumber, url)
            val exact = titleMatch == PartMatch.EXACT || urlMatch == PartMatch.EXACT
            val partMatch = when {
                exact -> PartMatch.EXACT
                titleMatch == PartMatch.FAMILY_VARIANT || urlMatch == PartMatch.FAMILY_VARIANT -> PartMatch.FAMILY_VARIANT
                else -> PartMatch.NONE
            }
            val isPdfHint = url.path.lowercase(Locale.ROOT).endsWith(".pdf")
            val datasheetHint = Regex("\\b(?:datasheet|data\\s+sheet)\\b", RegexOption.IGNORE_CASE)
                .containsMatchIn(candidate.title + " " + url.path + " " + candidate.snippet)
            val trusted = source.level == TrustLevel.MANUFACTURER || source.level == TrustLevel.DISTRIBUTOR
            val reasons = mutableListOf<String>()
            var score = when (source.level) {
                TrustLevel.MANUFACTURER -> 40.also { reasons += "Üretici alan adı +40" }
                TrustLevel.DISTRIBUTOR -> 30.also { reasons += "Distribütör alan adı +30" }
                TrustLevel.BLOCKED -> (-50).also { reasons += "Bu kaynak türü seçime kapalı −50" }
                TrustLevel.UNKNOWN -> (-30).also { reasons += "Kaynak kayıtlı değil −30" }
            }
            if (titleMatch == PartMatch.EXACT) { score += 25; reasons += "Başlıkta tam kod +25" }
            if (urlMatch == PartMatch.EXACT) { score += 15; reasons += "URL'de tam kod +15" }
            if (datasheetHint) { score += 10; reasons += "Datasheet ipucu +10" }
            if (isPdfHint) { score += 5; reasons += "PDF uzantısı ipucu +5" }
            if (partMatch == PartMatch.FAMILY_VARIANT) reasons += "Farklı varyant; tam kodla yeniden ara"
            if (partMatch == PartMatch.NONE) reasons += "Başlık/URL içinde parça eşleşmesi yok"
            if (!isPdfHint && !datasheetHint) reasons += "Datasheet olduğuna ilişkin ipucu yok"
            val variantConflict = urlMatch == PartMatch.FAMILY_VARIANT && titleMatch == PartMatch.EXACT
            if (variantConflict) reasons += "Başlık ile URL varyantı uyuşmuyor"
            val selectable = trusted && exact && !variantConflict && (isPdfHint || datasheetHint) && score >= 65
            RankedCandidate(candidate, url.canonical, url.host, source, partMatch, score, selectable, reasons)
        }.sortedWith(compareByDescending<RankedCandidate> { it.selectable }
            .thenByDescending { it.match == PartMatch.EXACT }
            .thenByDescending { it.score }.thenBy { it.canonicalUrl })
            .distinctBy { it.canonicalUrl }
        return CandidateSelection(ranked, ranked.firstOrNull { it.selectable }?.canonicalUrl, discarded)
    }
}
```

### C:/AndroidProjects/DatasheetAnalyzer-Asama3/DatasheetAnalyzer/app/src/main/java/com/example/datasheetanalyzer/domain/resolver/CandidateUrlPolicy.kt

```kotlin
package com.example.datasheetanalyzer.domain.resolver

import java.net.URI
import java.util.Locale

data class CandidateUrl(val canonical: String, val host: String, val path: String, val query: String?)

object CandidateUrlPolicy {
    fun parse(raw: String): CandidateUrl? = runCatching {
        require(raw.length <= 2048)
        val uri = URI(raw.trim()).normalize()
        require(uri.scheme.equals("https", ignoreCase = true))
        require(uri.rawUserInfo == null && (uri.port == -1 || uri.port == 443))
        val host = requireNotNull(uri.host).lowercase(Locale.ROOT).trimEnd('.')
        require(host.contains('.') && host.matches(Regex("[a-z0-9.-]+")))
        require(!host.matches(Regex("[0-9.]+")))
        require(host.split('.').all { it.isNotBlank() && !it.startsWith('-') && !it.endsWith('-') })
        val path = uri.rawPath.orEmpty().ifEmpty { "/" }
        val canonical = "https://$host$path" + (uri.rawQuery?.let { "?$it" } ?: "")
        CandidateUrl(canonical, host, uri.path.orEmpty(), uri.query)
    }.getOrNull()
}
```

### C:/AndroidProjects/DatasheetAnalyzer-Asama3/DatasheetAnalyzer/app/src/main/java/com/example/datasheetanalyzer/domain/resolver/PartNumberMatcher.kt

```kotlin
package com.example.datasheetanalyzer.domain.resolver

import com.example.datasheetanalyzer.domain.model.PartMatch
import com.example.datasheetanalyzer.domain.model.PartNumberNormalizer
import java.util.Locale

class PartNumberMatcher {
    private val tokens = Regex("[A-Z0-9]+(?:[-/][A-Z0-9]+)*")
    private val suffix = Regex("(?:[A-Z][A-Z0-9-]{0,15}|[-/][A-Z0-9/-]{1,16})")

    fun match(partNumber: String, text: String): PartMatch {
        val part = PartNumberNormalizer.normalize(partNumber)
        if (!PartNumberNormalizer.isValid(part)) return PartMatch.NONE
        val found = tokens.findAll(text.uppercase(Locale.ROOT)).map { it.value }.toList()
        if (part in found) return PartMatch.EXACT
        return if (found.any { it.startsWith(part) && suffix.matches(it.removePrefix(part)) }) {
            PartMatch.FAMILY_VARIANT
        } else PartMatch.NONE
    }

    fun matchUrl(partNumber: String, url: CandidateUrl): PartMatch {
        val segments = url.path.split('/').filter { it.isNotBlank() }
        val fields = listOf(segments.lastOrNull().orEmpty(), segments.takeLast(2).joinToString("/"))
        val matches = fields.map { match(partNumber, it) }
        return when {
            PartMatch.EXACT in matches -> PartMatch.EXACT
            PartMatch.FAMILY_VARIANT in matches -> PartMatch.FAMILY_VARIANT
            else -> {
                val queryMatches = url.query.orEmpty().split('&', '=').map { match(partNumber, it) }
                when {
                    PartMatch.EXACT in queryMatches -> PartMatch.EXACT
                    PartMatch.FAMILY_VARIANT in queryMatches -> PartMatch.FAMILY_VARIANT
                    else -> PartMatch.NONE
                }
            }
        }
    }
}
```

### C:/AndroidProjects/DatasheetAnalyzer-Asama3/DatasheetAnalyzer/app/src/main/java/com/example/datasheetanalyzer/domain/resolver/SearchProvider.kt

```kotlin
package com.example.datasheetanalyzer.domain.resolver

import com.example.datasheetanalyzer.domain.model.SearchCandidate

interface SearchProvider {
    suspend fun searchDatasheets(partNumber: String): List<SearchCandidate>
}
```

### C:/AndroidProjects/DatasheetAnalyzer-Asama3/DatasheetAnalyzer/app/src/main/java/com/example/datasheetanalyzer/domain/resolver/TrustedDomainRegistry.kt

```kotlin
package com.example.datasheetanalyzer.domain.resolver

import com.example.datasheetanalyzer.domain.model.SourceTrust
import com.example.datasheetanalyzer.domain.model.TrustLevel
import java.util.Locale

class TrustedDomainRegistry {
    private val manufacturers = linkedMapOf(
        "ti.com" to "Texas Instruments", "st.com" to "STMicroelectronics",
        "analog.com" to "Analog Devices", "onsemi.com" to "onsemi",
        "nxp.com" to "NXP", "infineon.com" to "Infineon",
        "microchip.com" to "Microchip", "renesas.com" to "Renesas",
        "vishay.com" to "Vishay", "rohm.com" to "ROHM",
        "toshiba-semicon-storage.com" to "Toshiba"
    )
    private val distributors = linkedMapOf("digikey.com" to "DigiKey", "mouser.com" to "Mouser")
    private val shorteners = setOf("bit.ly", "t.co", "tinyurl.com", "goo.gl", "ow.ly")
    private val discussionLabels = setOf("forum", "forums", "blog", "blogs", "community", "e2e")

    fun classify(url: CandidateUrl): SourceTrust {
        val pathSegments = url.path.lowercase(Locale.ROOT).split('/').filter { it.isNotBlank() }
        if (shorteners.any { matchesDomain(url.host, it) }) {
            return SourceTrust(TrustLevel.BLOCKED, "URL kısaltıcı")
        }
        if (url.host.split('.').any { it in discussionLabels } ||
            pathSegments.any { it in discussionLabels }) {
            return SourceTrust(TrustLevel.BLOCKED, "Forum / blog / topluluk")
        }
        manufacturers.entries.firstOrNull { matchesDomain(url.host, it.key) }?.let {
            return SourceTrust(TrustLevel.MANUFACTURER, it.value)
        }
        distributors.entries.firstOrNull { matchesDomain(url.host, it.key) }?.let {
            return SourceTrust(TrustLevel.DISTRIBUTOR, it.value)
        }
        return SourceTrust(TrustLevel.UNKNOWN, "Tanımlanmamış kaynak")
    }

    private fun matchesDomain(host: String, domain: String) = host == domain || host.endsWith(".$domain")
}
```

### C:/AndroidProjects/DatasheetAnalyzer-Asama3/DatasheetAnalyzer/app/src/main/java/com/example/datasheetanalyzer/ocr/AnalysisGate.kt

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

### C:/AndroidProjects/DatasheetAnalyzer-Asama3/DatasheetAnalyzer/app/src/main/java/com/example/datasheetanalyzer/ocr/ComponentTextAnalyzer.kt

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

### C:/AndroidProjects/DatasheetAnalyzer-Asama3/DatasheetAnalyzer/app/src/main/java/com/example/datasheetanalyzer/ocr/PartCandidateExtractor.kt

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

### C:/AndroidProjects/DatasheetAnalyzer-Asama3/DatasheetAnalyzer/app/src/main/java/com/example/datasheetanalyzer/ui/home/HomeScreen.kt

```kotlin
package com.example.datasheetanalyzer.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.datasheetanalyzer.domain.model.Criterion
import com.example.datasheetanalyzer.ui.theme.DatasheetAnalyzerTheme
import com.example.datasheetanalyzer.ui.theme.ElectricOrange

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
    val focus = LocalFocusManager.current
    Scaffold { insets ->
        Column(
            Modifier.fillMaxSize().padding(insets).imePadding()
                .verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
                    .background(ElectricOrange), contentAlignment = Alignment.Center) {
                    Text("ϟ", color = Color(0xFF211005), fontSize = 34.sp, fontWeight = FontWeight.Bold)
                }
                Column {
                    Text("DATASHEET", fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                    Text("ANALYZER", color = ElectricOrange, style = MaterialTheme.typography.labelSmall,
                        letterSpacing = 4.sp)
                }
            }
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF241C16), Color(0xFF151518))))) {
                Canvas(Modifier.matchParentSize()) {
                    val wire = ElectricOrange.copy(alpha = 0.18f)
                    val y = size.height * 0.22f
                    drawLine(wire, Offset(size.width * 0.65f, 0f), Offset(size.width * 0.65f, y), 2.dp.toPx())
                    drawLine(wire, Offset(size.width * 0.65f, y), Offset(size.width * 0.88f, y), 2.dp.toPx())
                    drawLine(wire, Offset(size.width * 0.88f, y), Offset(size.width * 0.88f, size.height), 2.dp.toPx())
                    drawCircle(wire, 5.dp.toPx(), Offset(size.width * 0.65f, y))
                }
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("KOMPONENTTEN KAYNAĞA", color = ElectricOrange,
                        style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
                    Text("Devreni tanı.", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Text("Kodunu gir, datasheet kaynaklarını bul.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            InputCard {
                SectionLabel("01", "Komponent kodu")
                OutlinedTextField(
                    value = state.partNumber, onValueChange = onPartNumberChanged,
                    modifier = Modifier.fillMaxWidth(), label = { Text("Parça numarası") },
                    placeholder = { Text("LM383") }, singleLine = true,
                    textStyle = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace),
                    supportingText = { Text("Elle yaz veya kamerayla oku · ${state.partNumber.length}/64") },
                    enabled = !state.loading,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Next)
                )
                OutlinedButton(onClick = { focus.clearFocus(); onScan() },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !state.loading,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Text("KAMERAYLA TARA")
                }
            }
            InputCard {
                SectionLabel("02", "Ne öğrenmek istiyorsun?")
                OutlinedTextField(
                    value = state.customCriterion, onValueChange = onCustomCriterionChanged,
                    modifier = Modifier.fillMaxWidth(), label = { Text("Kendi kriterini yaz") },
                    placeholder = { Text("Besleme gerilimi, pinout…") }, minLines = 2, maxLines = 4,
                    enabled = !state.loading,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focus.clearFocus() })
                )
                Text("HIZLI SEÇİM  /  ${state.selectedCriteria.size} SEÇİLİ",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Criterion.entries.forEach { criterion ->
                        FilterChip(
                            selected = criterion in state.selectedCriteria, onClick = { onCriterionToggled(criterion) },
                            label = { Text(criterion.label) }, enabled = !state.loading,
                            shape = RoundedCornerShape(12.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            leadingIcon = if (criterion in state.selectedCriteria) { { Text("✓") } } else null
                        )
                    }
                }
            }
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
            Button(onClick = { focus.clearFocus(); onSearch() }, enabled = !state.loading,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(16.dp)) {
                Text("DATASHEET BUL  →", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
            Text("Bu sürüm kaynakları bulur. Teknik parametre çıkarımı henüz etkin değil.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun InputCard(content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    }
}

@Composable
private fun SectionLabel(number: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(number, color = ElectricOrange, fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.labelLarge)
        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 1000)
@Composable
private fun HomeScreenPreview() {
    DatasheetAnalyzerTheme {
        HomeScreen(HomeUiState(partNumber = "LM383", selectedCriteria = setOf(Criterion.SUPPLY_VOLTAGE)), {}, {}, {}, {}, {})
    }
}
```

### C:/AndroidProjects/DatasheetAnalyzer-Asama3/DatasheetAnalyzer/app/src/main/java/com/example/datasheetanalyzer/ui/home/HomeUiState.kt

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

### C:/AndroidProjects/DatasheetAnalyzer-Asama3/DatasheetAnalyzer/app/src/main/java/com/example/datasheetanalyzer/ui/home/HomeViewModel.kt

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

### C:/AndroidProjects/DatasheetAnalyzer-Asama3/DatasheetAnalyzer/app/src/main/java/com/example/datasheetanalyzer/ui/navigation/DatasheetApp.kt

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
import com.example.datasheetanalyzer.ui.search.SearchScreen
import com.example.datasheetanalyzer.ui.search.SearchViewModel
import com.example.datasheetanalyzer.data.repository.ResolverDependencies

private object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
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
            val searchViewModel: SearchViewModel = viewModel(
                factory = SearchViewModel.Factory(ResolverDependencies.repository)
            )
            val criteria = (state.selectedCriteria.map { it.label } +
                listOf(state.customCriterion).filter { it.isNotBlank() }).joinToString(" · ")
            SearchScreen(state.partNumber, criteria, searchViewModel,
                onBack = { navController.popBackStack() })
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

### C:/AndroidProjects/DatasheetAnalyzer-Asama3/DatasheetAnalyzer/app/src/main/java/com/example/datasheetanalyzer/ui/scanner/ScannerScreen.kt

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

### C:/AndroidProjects/DatasheetAnalyzer-Asama3/DatasheetAnalyzer/app/src/main/java/com/example/datasheetanalyzer/ui/scanner/ScannerViewModel.kt

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

### C:/AndroidProjects/DatasheetAnalyzer-Asama3/DatasheetAnalyzer/app/src/main/java/com/example/datasheetanalyzer/ui/search/SearchScreen.kt

```kotlin
package com.example.datasheetanalyzer.ui.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.datasheetanalyzer.domain.model.PartMatch
import com.example.datasheetanalyzer.domain.model.RankedCandidate
import com.example.datasheetanalyzer.domain.model.TrustLevel
import com.example.datasheetanalyzer.ui.theme.ElectricOrange

@Composable
fun SearchScreen(part: String, criteria: String, viewModel: SearchViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(part) { viewModel.start(part) }
    val back = { viewModel.cancel(); onBack() }
    BackHandler(onBack = back)
    Scaffold { insets ->
        LazyColumn(
            Modifier.fillMaxSize().padding(insets).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = back) { Text("← GERİ") }
                    Text("Kaynaklar", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Text(part, color = ElectricOrange, fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.headlineSmall)
                    Text(criteria, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Kriterlerin saklandı; bu adım yalnızca datasheet kaynağını bulur.",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            if (state.loading) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        LinearProgressIndicator(Modifier.fillMaxWidth(), color = ElectricOrange)
                        Text("Datasheet adayları aranıyor…")
                        OutlinedButton(onClick = back) { Text("İPTAL ET") }
                    }
                }
            }
            state.error?.let { error ->
                item {
                    Card {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Arama tamamlanamadı", style = MaterialTheme.typography.titleMedium)
                            Text(error, color = MaterialTheme.colorScheme.error)
                            Button(onClick = { viewModel.start(part, retry = true) }) { Text("YENİDEN DENE") }
                        }
                    }
                }
            }
            state.selection?.let { selection ->
                item {
                    val available = selection.candidates.count { it.selectable }
                    Text(if (available > 0) "$available seçilebilir kaynak adayı"
                        else "Seçim ölçütlerini karşılayan kaynak bulunamadı.",
                        style = MaterialTheme.typography.titleMedium)
                    Text("Alan adı ve kod eşleşmesi ön kontroldür. Belge içeriği ve PDF güvenliği henüz denetlenmedi.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (selection.discardedCount > 0) Text(
                        "${selection.discardedCount} uygun olmayan URL elendi.",
                        style = MaterialTheme.typography.bodySmall)
                }
                items(selection.candidates, key = { it.canonicalUrl }) { candidate ->
                    CandidateCard(candidate,
                        recommended = candidate.canonicalUrl == selection.recommendedUrl,
                        selected = candidate.canonicalUrl == state.selectedUrl,
                        onSelect = { viewModel.select(candidate.canonicalUrl) })
                }
                item {
                    OutlinedButton(onClick = { viewModel.start(part, retry = true) },
                        modifier = Modifier.fillMaxWidth()) { Text("ARAMAYI YENİLE") }
                    Text("Bağlantılar otomatik açılmaz. Bu sürümde PDF indirme kapalıdır.",
                        modifier = Modifier.padding(vertical = 16.dp),
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun CandidateCard(candidate: RankedCandidate, recommended: Boolean, selected: Boolean, onSelect: () -> Unit) {
    var details by rememberSaveable(candidate.canonicalUrl) { mutableStateOf(false) }
    Card(
        border = BorderStroke(1.dp, if (selected || recommended) ElectricOrange
            else MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (recommended) Text("ÖNE ÇIKAN ADAY", color = ElectricOrange,
                style = MaterialTheme.typography.labelSmall)
            Text(candidate.candidate.title.ifBlank { "Başlıksız bağlantı" },
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(candidate.host, color = ElectricOrange, fontFamily = FontFamily.Monospace)
            val type = when (candidate.source.level) {
                TrustLevel.MANUFACTURER -> "Üretici alan adı"
                TrustLevel.DISTRIBUTOR -> "Distribütör alan adı"
                TrustLevel.UNKNOWN -> "Kayıtlı olmayan alan adı"
                TrustLevel.BLOCKED -> "Seçime kapalı kaynak"
            }
            Text("$type · ${candidate.source.label}", style = MaterialTheme.typography.bodySmall)
            val matchText = when (candidate.match) {
                PartMatch.EXACT -> "Başlık/URL'de tam kod eşleşmesi"
                PartMatch.FAMILY_VARIANT -> "Aile / varyant eşleşmesi"
                PartMatch.NONE -> "Kod eşleşmesi yok"
            }
            Text(matchText, style = MaterialTheme.typography.bodySmall)
            if (candidate.selectable) {
                Button(onClick = onSelect, enabled = !selected, modifier = Modifier.fillMaxWidth()) {
                    Text(if (selected) "ADAY SEÇİLDİ" else "BU ADAYI SEÇ")
                }
            } else {
                Text("Seçim ölçütlerini karşılamıyor.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selected) Text("Kaynak seçildi. Dosya indirilmedi veya açılmadı.",
                style = MaterialTheme.typography.bodySmall, color = ElectricOrange)
            TextButton(onClick = { details = !details }) {
                Text(if (details) "AYRINTILARI GİZLE" else "EŞLEŞME VE BAĞLANTI")
            }
            if (details) {
                Text("Sıralama puanı: ${candidate.score} — doğruluk yüzdesi değildir.",
                    style = MaterialTheme.typography.bodySmall)
                candidate.reasons.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                SelectionContainer { Text(candidate.canonicalUrl, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}
```

### C:/AndroidProjects/DatasheetAnalyzer-Asama3/DatasheetAnalyzer/app/src/main/java/com/example/datasheetanalyzer/ui/search/SearchViewModel.kt

```kotlin
package com.example.datasheetanalyzer.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.datasheetanalyzer.data.repository.DatasheetRepository
import com.example.datasheetanalyzer.domain.model.CandidateSelection
import com.example.datasheetanalyzer.domain.model.SearchFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

data class SearchUiState(
    val loading: Boolean = false,
    val selection: CandidateSelection? = null,
    val error: String? = null,
    val selectedUrl: String? = null
)

class SearchViewModel(private val repository: DatasheetRepository) : ViewModel() {
    private val _state = MutableStateFlow(SearchUiState())
    val state = _state.asStateFlow()
    private var requestedPart: String? = null
    private var job: Job? = null

    fun start(part: String, retry: Boolean = false) {
        if (_state.value.loading || (requestedPart == part && !retry)) return
        requestedPart = part
        _state.value = SearchUiState(loading = true)
        job = viewModelScope.launch {
            try {
                val result = withTimeout(20_000) { repository.search(part) }
                _state.value = SearchUiState(selection = result)
            } catch (_: TimeoutCancellationException) {
                _state.value = SearchUiState(error = "Arama zaman aşımına uğradı. Yeniden deneyebilirsin.")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: SearchFailure) {
                _state.value = SearchUiState(error = failure.message)
            } catch (_: Exception) {
                _state.value = SearchUiState(error = "Arama tamamlanamadı. Bağlantını kontrol edip tekrar dene.")
            }
        }
    }

    fun select(url: String) {
        if (_state.value.selection?.candidates?.any { it.canonicalUrl == url && it.selectable } == true) {
            _state.value = _state.value.copy(selectedUrl = url)
        }
    }

    fun cancel() {
        job?.cancel()
        _state.value = _state.value.copy(loading = false)
    }

    class Factory(private val repository: DatasheetRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(SearchViewModel::class.java))
            return SearchViewModel(repository) as T
        }
    }
}
```

### C:/AndroidProjects/DatasheetAnalyzer-Asama3/DatasheetAnalyzer/app/src/main/java/com/example/datasheetanalyzer/ui/theme/Theme.kt

```kotlin
package com.example.datasheetanalyzer.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val ElectricOrange = Color(0xFFFF8A32)
private val ElectricColors = darkColorScheme(
    primary = ElectricOrange,
    onPrimary = Color(0xFF211005),
    primaryContainer = Color(0xFF3B2314),
    onPrimaryContainer = Color(0xFFFFC496),
    secondary = Color(0xFFFFB36F),
    onSecondary = Color(0xFF241407),
    secondaryContainer = Color(0xFF30251D),
    onSecondaryContainer = Color(0xFFFFDEC3),
    tertiary = Color(0xFFE5C264),
    background = Color(0xFF0B0B0E),
    onBackground = Color(0xFFF3F0EB),
    surface = Color(0xFF141417),
    onSurface = Color(0xFFF3F0EB),
    surfaceVariant = Color(0xFF222226),
    onSurfaceVariant = Color(0xFFB9B6B0),
    surfaceContainerLowest = Color(0xFF09090B),
    surfaceContainerLow = Color(0xFF141417),
    surfaceContainer = Color(0xFF1B1B1F),
    surfaceContainerHigh = Color(0xFF252529),
    surfaceContainerHighest = Color(0xFF303035),
    outline = Color(0xFF68615A),
    outlineVariant = Color(0xFF37322E),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

@Composable
fun DatasheetAnalyzerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ElectricColors,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp),
            extraLarge = RoundedCornerShape(28.dp)
        ),
        content = content
    )
}
```

### C:/AndroidProjects/DatasheetAnalyzer-Asama3/DatasheetAnalyzer/app/src/main/res/drawable/ic_launcher.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
    <path android:fillColor="#0B0B0E"
        android:pathData="M24,0 L84,0 Q108,0 108,24 L108,84 Q108,108 84,108 L24,108 Q0,108 0,84 L0,24 Q0,0 24,0 Z" />
    <path android:fillColor="#FF8A32"
        android:pathData="M62,18 L30,60 L50,60 L43,90 L80,45 L59,45 Z" />
</vector>
```

### C:/AndroidProjects/DatasheetAnalyzer-Asama3/DatasheetAnalyzer/app/src/main/res/values/strings.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">Datasheet Analyzer</string>
</resources>
```

### C:/AndroidProjects/DatasheetAnalyzer-Asama3/DatasheetAnalyzer/app/src/main/res/values/themes.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.DatasheetAnalyzer" parent="android:style/Theme.Material.NoActionBar">
        <item name="android:fontFamily">sans</item>
        <item name="android:windowLightStatusBar">false</item>
        <item name="android:windowLightNavigationBar">false</item>
        <item name="android:windowBackground">#0B0B0E</item>
        <item name="android:colorAccent">#FF8A32</item>
        <item name="android:windowActionModeOverlay">true</item>
    </style>
</resources>
```

### C:/AndroidProjects/DatasheetAnalyzer-Asama3/DatasheetAnalyzer/app/src/test/java/com/example/datasheetanalyzer/AnalysisGateTest.kt

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

### C:/AndroidProjects/DatasheetAnalyzer-Asama3/DatasheetAnalyzer/app/src/test/java/com/example/datasheetanalyzer/HtmlSearchTest.kt

```kotlin
package com.example.datasheetanalyzer

import com.example.datasheetanalyzer.data.network.DuckDuckGoHtmlParser
import com.example.datasheetanalyzer.data.network.HtmlFetcher
import com.example.datasheetanalyzer.data.network.KeylessHtmlSearchProvider
import com.example.datasheetanalyzer.data.repository.DatasheetRepository
import com.example.datasheetanalyzer.domain.model.SearchFailure
import com.example.datasheetanalyzer.domain.model.SearchFailureKind
import java.net.URI
import java.net.URLDecoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class HtmlSearchTest {
    private val html = """<html><body><div class="result">
        <a class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fti.com%2FLM383.pdf&amp;rut=abc">LM383 datasheet</a>
        <div class="result__snippet">Amplifier data sheet</div></div></body></html>"""
    private val parser = DuckDuckGoHtmlParser()

    @Test fun unwrapsDdgRedirectAndExtractsText() {
        val found = parser.parse(html).single()
        assertEquals("https://ti.com/LM383.pdf", found.url)
        assertEquals("LM383 datasheet", found.title)
        assertEquals("Amplifier data sheet", found.snippet)
    }
    @Test fun keepsDirectLinks() {
        val direct = html.replace("//duckduckgo.com/l/?uddg=https%3A%2F%2Fti.com%2FLM383.pdf&amp;rut=abc", "https://ti.com/LM383.pdf")
        assertEquals("https://ti.com/LM383.pdf", parser.parse(direct).single().url)
    }
    @Test fun skipsAdvertisingBlocks() {
        val ad = """<div class="result result--ad"><a class="result__a" href="https://ad.test/LM383.pdf">Ad</a></div>"""
        assertEquals(1, parser.parse(html.replace("</body>", "$ad</body>")).size)
    }
    @Test fun distinguishesNoResultsFromChangedHtml() {
        assertTrue(parser.parse("<div class='no-results'>No results</div>").isEmpty())
        try { parser.parse("<html>Unexpected page</html>"); fail("Expected format error") }
        catch (e: SearchFailure) { assertEquals(SearchFailureKind.FORMAT_CHANGED, e.kind) }
    }
    @Test fun detectsChallengeWithoutAttemptingBypass() {
        try { parser.parse("<form id='challenge-form'></form>"); fail("Expected block") }
        catch (e: SearchFailure) { assertEquals(SearchFailureKind.BLOCKED, e.kind) }
    }
    @Test fun doesNotTrustFakeDdgHostForRedirectUnwrapping() {
        val fake = html.replace("//duckduckgo.com/l/", "https://duckduckgo.com.evil.test/l/")
        assertTrue(parser.parse(fake).single().url.startsWith("https://duckduckgo.com.evil.test/"))
    }
    @Test fun queryUsesNormalizedQuotedPart() = runBlocking {
        var requested = ""
        val fetcher = object : HtmlFetcher { override suspend fun fetch(url: String): String { requested = url; return html } }
        val provider = KeylessHtmlSearchProvider(fetcher)
        assertEquals(1, provider.searchDatasheets(" lm 383 ").size)
        assertEquals("q=\"LM383\" datasheet pdf", URLDecoder.decode(URI(requested).rawQuery, "UTF-8"))
    }
    @Test fun throttlesRepeatedSearchWithoutExtraRequest() = runBlocking {
        var calls = 0
        var clock = 0L
        val fetcher = object : HtmlFetcher { override suspend fun fetch(url: String): String { calls++; return html } }
        val provider = KeylessHtmlSearchProvider(fetcher, clockMs = { clock })
        provider.searchDatasheets("LM383")
        try { provider.searchDatasheets("LM383"); fail("Expected throttle") }
        catch (e: SearchFailure) { assertEquals(SearchFailureKind.RATE_LIMITED, e.kind) }
        assertEquals(1, calls)
        clock = 3000
        provider.searchDatasheets("LM383")
        assertEquals(2, calls)
    }
    @Test fun cancellationPropagates() = runBlocking {
        val fetcher = object : HtmlFetcher { override suspend fun fetch(url: String): String { throw CancellationException("test") } }
        try { KeylessHtmlSearchProvider(fetcher).searchDatasheets("LM383"); fail("Expected cancellation") }
        catch (_: CancellationException) { /* Must not turn cancellation into empty results. */ }
    }
    @Test fun fixtureFlowsThroughParserProviderAndSelector() = runBlocking {
        val fetcher = object : HtmlFetcher { override suspend fun fetch(url: String): String = html }
        val result = DatasheetRepository(KeylessHtmlSearchProvider(fetcher)).search("LM383")
        assertEquals("https://ti.com/LM383.pdf", result.recommendedUrl)
        assertTrue(result.candidates.single().selectable)
    }
}
```

### C:/AndroidProjects/DatasheetAnalyzer-Asama3/DatasheetAnalyzer/app/src/test/java/com/example/datasheetanalyzer/PartCandidateExtractorTest.kt

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

### C:/AndroidProjects/DatasheetAnalyzer-Asama3/DatasheetAnalyzer/app/src/test/java/com/example/datasheetanalyzer/PartNumberNormalizerTest.kt

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

### C:/AndroidProjects/DatasheetAnalyzer-Asama3/DatasheetAnalyzer/app/src/test/java/com/example/datasheetanalyzer/ResolverTest.kt

```kotlin
package com.example.datasheetanalyzer

import com.example.datasheetanalyzer.domain.model.PartMatch
import com.example.datasheetanalyzer.domain.model.SearchCandidate
import com.example.datasheetanalyzer.domain.model.TrustLevel
import com.example.datasheetanalyzer.domain.resolver.CandidateSelector
import com.example.datasheetanalyzer.domain.resolver.CandidateUrlPolicy
import com.example.datasheetanalyzer.domain.resolver.PartNumberMatcher
import com.example.datasheetanalyzer.domain.resolver.TrustedDomainRegistry
import org.junit.Assert.*
import org.junit.Test

class ResolverTest {
    private val registry = TrustedDomainRegistry()
    private val matcher = PartNumberMatcher()
    private val selector = CandidateSelector()
    private fun url(raw: String) = requireNotNull(CandidateUrlPolicy.parse(raw))

    @Test fun recognizesManufacturerAndSubdomains() {
        assertEquals(TrustLevel.MANUFACTURER, registry.classify(url("https://www.ti.com/a.pdf")).level)
        assertEquals(TrustLevel.MANUFACTURER, registry.classify(url("https://st.com/a.pdf")).level)
    }
    @Test fun recognizesDistributorMediaSubdomain() {
        assertEquals(TrustLevel.DISTRIBUTOR, registry.classify(url("https://mm.digikey.com/a.pdf")).level)
    }
    @Test fun rejectsLookalikeAndAppendedDomains() {
        for (host in listOf("evilti.com", "ti.com.evil.test", "mouser.com.attacker.test")) {
            assertEquals(TrustLevel.UNKNOWN, registry.classify(url("https://$host/a.pdf")).level)
        }
    }
    @Test fun blocksForumsEvenOnManufacturerDomain() {
        assertEquals(TrustLevel.BLOCKED, registry.classify(url("https://e2e.ti.com/a.pdf")).level)
        assertEquals(TrustLevel.BLOCKED, registry.classify(url("https://ti.com/blog/LM383.pdf")).level)
    }
    @Test fun blocksShorteners() {
        assertEquals(TrustLevel.BLOCKED, registry.classify(url("https://bit.ly/LM383")).level)
    }
    @Test fun rejectsUnsafeUrlForms() {
        for (raw in listOf("http://ti.com/a.pdf", "javascript:alert(1)",
            "https://ti.com@evil.test/a.pdf", "https://ti.com:8443/a.pdf", "https://127.0.0.1/a.pdf")) {
            assertNull(raw, CandidateUrlPolicy.parse(raw))
        }
    }
    @Test fun canonicalizesCasePortAndFragment() {
        assertEquals("https://ti.com/a.pdf", url("https://TI.COM:443/a.pdf#page=2").canonical)
    }
    @Test fun recognizesExactCodeWithoutSubstringConfusion() {
        assertEquals(PartMatch.EXACT, matcher.match("LM383", "LM383 datasheet"))
        for (other in listOf("TPS3831", "OPA383", "LM2833", "LM3831", "XLM383")) {
            assertEquals(other, PartMatch.NONE, matcher.match("LM383", other))
        }
    }
    @Test fun classifiesVariantsSeparately() {
        for (part in listOf("LM383A", "LM383T", "LM383-N")) {
            assertEquals(PartMatch.FAMILY_VARIANT, matcher.match("LM383", part))
        }
    }
    @Test fun matchesDecodedPdfPath() {
        assertEquals(PartMatch.EXACT, matcher.matchUrl("LM383", url("https://ti.com/lit/ds/%6cm383.pdf")))
    }
    @Test fun supportsSlashInRequestedPackageCode() {
        assertEquals(PartMatch.EXACT, matcher.matchUrl("LM358-N/P", url("https://ti.com/docs/LM358-N/P.pdf")))
    }
    @Test fun variantFilenameCannotBeOverruledByQuery() {
        assertEquals(PartMatch.FAMILY_VARIANT, matcher.matchUrl("LM383", url("https://ti.com/LM383A.pdf?q=LM383")))
    }
    @Test fun ranksManufacturerAboveUnknownFirstResult() {
        val result = selector.select("LM383", listOf(
            SearchCandidate("LM383 datasheet", "https://unknown.test/LM383.pdf"),
            SearchCandidate("LM383 datasheet", "https://ti.com/LM383.pdf")))
        assertEquals("https://ti.com/LM383.pdf", result.recommendedUrl)
        assertEquals(95, result.candidates.first().score)
        assertFalse(result.candidates.last().selectable)
    }
    @Test fun variantIsNeverAutoRecommended() {
        val result = selector.select("LM383", listOf(SearchCandidate("LM383A datasheet", "https://ti.com/LM383A.pdf")))
        assertNull(result.recommendedUrl)
        assertFalse(result.candidates.single().selectable)
    }
    @Test fun conflictingTitleAndFilenameCannotBeSelected() {
        val result = selector.select("LM383", listOf(SearchCandidate("LM383 datasheet", "https://ti.com/LM383A.pdf")))
        assertFalse(result.candidates.single().selectable)
    }
    @Test fun snippetAloneDoesNotVerifyPartIdentity() {
        val result = selector.select("LM383", listOf(SearchCandidate("Datasheet", "https://ti.com/other.pdf", "LM383")))
        assertFalse(result.candidates.single().selectable)
    }
    @Test fun duplicateFragmentsDoNotCreateDuplicateCandidates() {
        val result = selector.select("LM383", listOf(
            SearchCandidate("LM383 datasheet", "https://ti.com/LM383.pdf#page=1"),
            SearchCandidate("LM383 datasheet", "https://ti.com/LM383.pdf#page=2")))
        assertEquals(1, result.candidates.size)
    }
    @Test fun dropsInvalidUrlsAndCountsThem() {
        val result = selector.select("LM383", listOf(SearchCandidate("LM383 datasheet", "http://ti.com/LM383.pdf")))
        assertEquals(1, result.discardedCount)
        assertTrue(result.candidates.isEmpty())
    }
    @Test fun genericProductPageWithoutDocumentHintIsNotSelectable() {
        val result = selector.select("LM383", listOf(SearchCandidate("LM383", "https://ti.com/product/LM383")))
        assertFalse(result.candidates.single().selectable)
    }
}
```

### C:/AndroidProjects/DatasheetAnalyzer-Asama3/DatasheetAnalyzer/build.gradle.kts

```kotlin
plugins {
    id("com.android.application") version "8.12.0" apply false
    id("org.jetbrains.kotlin.android") version "2.2.10" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
}
```

### C:/AndroidProjects/DatasheetAnalyzer-Asama3/DatasheetAnalyzer/gradle/wrapper/gradle-wrapper.properties

```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.13-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

### C:/AndroidProjects/DatasheetAnalyzer-Asama3/DatasheetAnalyzer/gradle.properties

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
```

### C:/AndroidProjects/DatasheetAnalyzer-Asama3/DatasheetAnalyzer/gradlew

```text
#!/bin/sh

#
# Copyright © 2015-2021 the original authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# SPDX-License-Identifier: Apache-2.0
#

##############################################################################
#
#   Gradle start up script for POSIX generated by Gradle.
#
#   Important for running:
#
#   (1) You need a POSIX-compliant shell to run this script. If your /bin/sh is
#       noncompliant, but you have some other compliant shell such as ksh or
#       bash, then to run this script, type that shell name before the whole
#       command line, like:
#
#           ksh Gradle
#
#       Busybox and similar reduced shells will NOT work, because this script
#       requires all of these POSIX shell features:
#         * functions;
#         * expansions «$var», «${var}», «${var:-default}», «${var+SET}»,
#           «${var#prefix}», «${var%suffix}», and «$( cmd )»;
#         * compound commands having a testable exit status, especially «case»;
#         * various built-in commands including «command», «set», and «ulimit».
#
#   Important for patching:
#
#   (2) This script targets any POSIX shell, so it avoids extensions provided
#       by Bash, Ksh, etc; in particular arrays are avoided.
#
#       The "traditional" practice of packing multiple parameters into a
#       space-separated string is a well documented source of bugs and security
#       problems, so this is (mostly) avoided, by progressively accumulating
#       options in "$@", and eventually passing that to Java.
#
#       Where the inherited environment variables (DEFAULT_JVM_OPTS, JAVA_OPTS,
#       and GRADLE_OPTS) rely on word-splitting, this is performed explicitly;
#       see the in-line comments for details.
#
#       There are tweaks for specific operating systems such as AIX, CygWin,
#       Darwin, MinGW, and NonStop.
#
#   (3) This script is generated from the Groovy template
#       https://github.com/gradle/gradle/blob/HEAD/platforms/jvm/plugins-application/src/main/resources/org/gradle/api/internal/plugins/unixStartScript.txt
#       within the Gradle project.
#
#       You can find Gradle at https://github.com/gradle/gradle/.
#
##############################################################################

# Attempt to set APP_HOME

# Resolve links: $0 may be a link
app_path=$0

# Need this for daisy-chained symlinks.
while
    APP_HOME=${app_path%"${app_path##*/}"}  # leaves a trailing /; empty if no leading path
    [ -h "$app_path" ]
do
    ls=$( ls -ld "$app_path" )
    link=${ls#*' -> '}
    case $link in             #(
      /*)   app_path=$link ;; #(
      *)    app_path=$APP_HOME$link ;;
    esac
done

# This is normally unused
# shellcheck disable=SC2034
APP_BASE_NAME=${0##*/}
# Discard cd standard output in case $CDPATH is set (https://github.com/gradle/gradle/issues/25036)
APP_HOME=$( cd -P "${APP_HOME:-./}" > /dev/null && printf '%s\n' "$PWD" ) || exit

# Use the maximum available, or set MAX_FD != -1 to use that value.
MAX_FD=maximum

warn () {
    echo "$*"
} >&2

die () {
    echo
    echo "$*"
    echo
    exit 1
} >&2

# OS specific support (must be 'true' or 'false').
cygwin=false
msys=false
darwin=false
nonstop=false
case "$( uname )" in                #(
  CYGWIN* )         cygwin=true  ;; #(
  Darwin* )         darwin=true  ;; #(
  MSYS* | MINGW* )  msys=true    ;; #(
  NONSTOP* )        nonstop=true ;;
esac

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar


# Determine the Java command to use to start the JVM.
if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
        # IBM's JDK on AIX uses strange locations for the executables
        JAVACMD=$JAVA_HOME/jre/sh/java
    else
        JAVACMD=$JAVA_HOME/bin/java
    fi
    if [ ! -x "$JAVACMD" ] ; then
        die "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME

Please set the JAVA_HOME variable in your environment to match the
location of your Java installation."
    fi
else
    JAVACMD=java
    if ! command -v java >/dev/null 2>&1
    then
        die "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.

Please set the JAVA_HOME variable in your environment to match the
location of your Java installation."
    fi
fi

# Increase the maximum file descriptors if we can.
if ! "$cygwin" && ! "$darwin" && ! "$nonstop" ; then
    case $MAX_FD in #(
      max*)
        # In POSIX sh, ulimit -H is undefined. That's why the result is checked to see if it worked.
        # shellcheck disable=SC2039,SC3045
        MAX_FD=$( ulimit -H -n ) ||
            warn "Could not query maximum file descriptor limit"
    esac
    case $MAX_FD in  #(
      '' | soft) :;; #(
      *)
        # In POSIX sh, ulimit -n is undefined. That's why the result is checked to see if it worked.
        # shellcheck disable=SC2039,SC3045
        ulimit -n "$MAX_FD" ||
            warn "Could not set maximum file descriptor limit to $MAX_FD"
    esac
fi

# Collect all arguments for the java command, stacking in reverse order:
#   * args from the command line
#   * the main class name
#   * -classpath
#   * -D...appname settings
#   * --module-path (only if needed)
#   * DEFAULT_JVM_OPTS, JAVA_OPTS, and GRADLE_OPTS environment variables.

# For Cygwin or MSYS, switch paths to Windows format before running java
if "$cygwin" || "$msys" ; then
    APP_HOME=$( cygpath --path --mixed "$APP_HOME" )
    CLASSPATH=$( cygpath --path --mixed "$CLASSPATH" )

    JAVACMD=$( cygpath --unix "$JAVACMD" )

    # Now convert the arguments - kludge to limit ourselves to /bin/sh
    for arg do
        if
            case $arg in                                #(
              -*)   false ;;                            # don't mess with options #(
              /?*)  t=${arg#/} t=/${t%%/*}              # looks like a POSIX filepath
                    [ -e "$t" ] ;;                      #(
              *)    false ;;
            esac
        then
            arg=$( cygpath --path --ignore --mixed "$arg" )
        fi
        # Roll the args list around exactly as many times as the number of
        # args, so each arg winds up back in the position where it started, but
        # possibly modified.
        #
        # NB: a `for` loop captures its iteration list before it begins, so
        # changing the positional parameters here affects neither the number of
        # iterations, nor the values presented in `arg`.
        shift                   # remove old arg
        set -- "$@" "$arg"      # push replacement arg
    done
fi


# Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'

# Collect all arguments for the java command:
#   * DEFAULT_JVM_OPTS, JAVA_OPTS, and optsEnvironmentVar are not allowed to contain shell fragments,
#     and any embedded shellness will be escaped.
#   * For example: A user cannot expect ${Hostname} to be expanded, as it is an environment variable and will be
#     treated as '${Hostname}' itself on the command line.

set -- \
        "-Dorg.gradle.appname=$APP_BASE_NAME" \
        -classpath "$CLASSPATH" \
        org.gradle.wrapper.GradleWrapperMain \
        "$@"

# Stop when "xargs" is not available.
if ! command -v xargs >/dev/null 2>&1
then
    die "xargs is not available"
fi

# Use "xargs" to parse quoted args.
#
# With -n1 it outputs one arg per line, with the quotes and backslashes removed.
#
# In Bash we could simply go:
#
#   readarray ARGS < <( xargs -n1 <<<"$var" ) &&
#   set -- "${ARGS[@]}" "$@"
#
# but POSIX shell has neither arrays nor command substitution, so instead we
# post-process each arg (as a line of input to sed) to backslash-escape any
# character that might be a shell metacharacter, then use eval to reverse
# that process (while maintaining the separation between arguments), and wrap
# the whole thing up as a single "set" statement.
#
# This will of course break if any of these variables contains a newline or
# an unmatched quote.
#

eval "set -- $(
        printf '%s\n' "$DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS" |
        xargs -n1 |
        sed ' s~[^-[:alnum:]+,./:=@_]~\\&~g; ' |
        tr '\n' ' '
    )" '"$@"'

exec "$JAVACMD" "$@"
```

### C:/AndroidProjects/DatasheetAnalyzer-Asama3/DatasheetAnalyzer/gradlew.bat

```bat
@rem
@rem Copyright 2015 the original author or authors.
@rem
@rem Licensed under the Apache License, Version 2.0 (the "License");
@rem you may not use this file except in compliance with the License.
@rem You may obtain a copy of the License at
@rem
@rem      https://www.apache.org/licenses/LICENSE-2.0
@rem
@rem Unless required by applicable law or agreed to in writing, software
@rem distributed under the License is distributed on an "AS IS" BASIS,
@rem WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@rem See the License for the specific language governing permissions and
@rem limitations under the License.
@rem
@rem SPDX-License-Identifier: Apache-2.0
@rem

@if "%DEBUG%"=="" @echo off
@rem ##########################################################################
@rem
@rem  Gradle startup script for Windows
@rem
@rem ##########################################################################

@rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
@rem This is normally unused
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

@rem Resolve any "." and ".." in APP_HOME to make it shorter.
for %%i in ("%APP_HOME%") do set APP_HOME=%%~fi

@rem Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
set DEFAULT_JVM_OPTS="-Xmx64m" "-Xms64m"

@rem Find java.exe
if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if %ERRORLEVEL% equ 0 goto execute

echo. 1>&2
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH. 1>&2
echo. 1>&2
echo Please set the JAVA_HOME variable in your environment to match the 1>&2
echo location of your Java installation. 1>&2

goto fail

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe

if exist "%JAVA_EXE%" goto execute

echo. 1>&2
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME% 1>&2
echo. 1>&2
echo Please set the JAVA_HOME variable in your environment to match the 1>&2
echo location of your Java installation. 1>&2

goto fail

:execute
@rem Setup the command line

set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar


@rem Execute Gradle
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% "-Dorg.gradle.appname=%APP_BASE_NAME%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*

:end
@rem End local scope for the variables with windows NT shell
if %ERRORLEVEL% equ 0 goto mainEnd

:fail
rem Set variable GRADLE_EXIT_CONSOLE if you need the _script_ return code instead of
rem the _cmd.exe /c_ return code!
set EXIT_CODE=%ERRORLEVEL%
if %EXIT_CODE% equ 0 set EXIT_CODE=1
if not ""=="%GRADLE_EXIT_CONSOLE%" exit %EXIT_CODE%
exit /b %EXIT_CODE%

:mainEnd
if "%OS%"=="Windows_NT" endlocal

:omega
```

### C:/AndroidProjects/DatasheetAnalyzer-Asama3/DatasheetAnalyzer/settings.gradle.kts

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
