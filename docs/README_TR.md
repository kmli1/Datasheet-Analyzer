# Datasheet Analyzer — Türkçe kullanım rehberi

[← English overview](../README.md) · [Mimari](ARCHITECTURE.md) · [Doğrulama](VALIDATION.md)

**Datasheet Analyzer**, elektronik bileşenlerin datasheet belgelerini bulmak ve teknik değerleri kaynak kanıtlarıyla incelemek için geliştirilen Android uygulamasıdır. Ana ekran siyah/turuncu, analiz ekranı turkuaz temayı kullanır.

## Özellikler

| İşlem | Uygulamadaki karşılığı |
| --- | --- |
| Parça kodunu gir | Kodu elle yaz veya kameranın cihaz içi OCR özelliğini kullan. |
| Belgeyi seç | Üretici kaynakları üzerinden ara ya da telefondan yerel PDF aç. |
| Teknik kriterleri belirle | Hazır kriterleri seç veya Türkçe/İngilizce kendi terimini yaz. |
| Sonucu incele | Min/tipik/maks, birim, koşullar, kanıt satırı ve kaynak sayfasını birlikte gör. |
| Grafiği bul | En fazla üç grafik başlığını ayrı satırlarda gir; eşleşen kaynak görselini incele. |
| Dışa aktar | Analizi PDF/CSV olarak; kaynak sayfayı veya seçilen alanı PNG olarak kaydet. |
| Çalışmanı düzenle | Analizi projeye kaydet, not ekle ve kayıtlı komponentleri karşılaştır. |
| Sensör adaylarını araştır | Beş üreticiden 160 sensör profilinde filtreleme ve karşılaştırma yap. |

## İlk analiz

1. Ana ekranda parça kodunu yaz veya kamerayla tara.
2. İlgilendiğin teknik kriterleri seç. Örneğin `input offset voltage` veya `recommended operating voltage` yazabilirsin.
3. İstersen grafiğin datasheet'teki başlığını grafik alanına ekle.
4. Bir kaynak adayı seç veya yerel PDF aç.
5. Analiz ekranında sonucu, koşulları ve kaynak sayfasını birlikte incele.
6. İlgili kaynak sayfasını açarak belgeyle karşılaştır; ardından raporu veya görseli kaydet.

## Sonuçları okuma

- **Min / tipik / maks:** Aynı parametrenin farklı sınırlarını veya tipik değerini gösterir. Test koşullarını birlikte değerlendirmek gerekir.
- **Recommended Operating Conditions:** Normal kullanım için önerilen çalışma koşullarıdır.
- **Absolute Maximum Ratings:** Mutlak sınırları anlatır; normal çalışma aralığı olarak kullanılmaz.
- **Kanıt ve sayfa:** Yazılımın eşleştirdiği kaynak satırına ulaşmanı sağlar.
- **İnceleme / olası sonuç:** Sayısal kesinlik veya yeterli doğrulama bulunmadığında belirsizliği açıklar.
- **Bulunamadı:** İlgili değerin belgede kesinlikle olmadığı anlamına gelmez; OCR, başlık veya tablo yapısı okumayı etkilemiş olabilir.

Grafikler yeniden çizilmez; kaynak sayfanın görüntüsü kullanılır. Sensör kataloğundaki adayların besleme, arabirim, paket, pin dizilimi ve ölçüm koşulları ayrıca incelenmelidir.

## Android Studio ile kurulum

JDK **17**, Android SDK **36**, AGP **8.12.0** ve Gradle wrapper **8.13** kullanılır. Minimum cihaz sürümü Android **8.0**'dır.

```powershell
git clone https://github.com/kmli1/Datasheet-Analyzer.git
cd Datasheet-Analyzer
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug
```

Projeyi Android Studio'da açıp Gradle JDK olarak 17'yi seç ve SDK platform 36'yı yükle. APK yolu `app/build/outputs/apk/debug/app-debug.apk` olur. Temiz klon varsayılan yerel debug imzasını kullanır; önceki test APK'sını güncellemek için [imza notlarını](../signing/README.md) oku.

## Gizlilik ve sürüm durumu

Kamera ve yerel PDF içeriği uygulamanın kendi sunucusuna yüklenmez. İnternet üzerinden parça ararken sorgu ve kaynak bağlantıları ilgili sitelerle paylaşılır. ML Kit tanılama açıklaması [uygulama gizlilik metninde](../app/src/main/assets/privacy-tr.txt) bulunur.

Kaynak sürüm **1.0.10**'dur. Önceki teslimin kaydı 358 geçen ve 7 atlanan test içerir; GitHub düzenlemesinde tam Android derlemesi tekrar çalıştırılmadı. Ayrıntılar ve kalan cihaz kontrolleri [doğrulama belgesindedir](VALIDATION.md).
