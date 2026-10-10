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
