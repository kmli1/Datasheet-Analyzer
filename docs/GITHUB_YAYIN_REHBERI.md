# GitHub görünüm ve yayın rehberi

[← Proje tanıtımı](../README.md)

Bu belgede repo sahibinin GitHub arayüzünde tamamlayabileceği alanlar yer alır. README, kapak, belgeler ve şablonlar kaynak dosyalarıdır; About ve sosyal önizleme GitHub ayarlarıdır.

## About

Repo ana sayfasında **About** yanındaki düzenleme simgesini kullan.

**Description** için hazır metin:

> Android datasheet analysis with source-linked technical tables, local OCR, PDF/CSV/PNG export and a 160-profile sensor catalogue. Built with Kotlin and Jetpack Compose.

Önerilen konular:

`android` · `kotlin` · `jetpack-compose` · `electronics` · `datasheet` · `ocr` · `pdf` · `sensors`

## Görseller

- README kapağı `docs/assets/banner.svg` içindedir; metin ve devre çizgileri düzenlenebilir vektördür.
- Mevcut uygulama simgesi `store-assets/icon-512.png` içindedir.
- Mevcut mağaza tanıtımı `store-assets/feature-1024x500.png` içindedir.
- Gerçek uygulama ekran görüntüleri eklenirse `docs/screenshots/` altında tutulabilir. Boş telefon çerçevesi veya temsili tablo, gerçek uygulama ekran görüntüsü olarak etiketlenmemelidir.

## APK paylaşımı

Derlenmiş APK'yı ve kaynak dağıtım arşivini bir GitHub Release'e sürüm adıyla eklemek, kaynak dosyalarından ayırır. Bu güncellemede bir Release oluşturulmaz ve yeni APK derlenmez. Sürümün imzası, kurulumu, test kapsamı ve checksum'u yayınlamadan önce doğrulanmalıdır.

## Bu paket neyi değiştiriyor?

Uygulamayı tanıtan README, Türkçe rehber, mimari/doğrulama belgeleri, katkı ve hata şablonları eklenir. Önceki paketteki iki cache kaynağı ve 16 test PDF'si özgün halleriyle geri alınır. Eksik debug anahtarı için Gradle yerel/varsayılan imza seçimi kullanır.

Mevcut PDF/OCR algoritması, katalog verisi ve kaynak doğrulama davranışı değiştirilmez. Proje lisansı için yayıncı seçimi yapılmadığından otomatik MIT veya başka bir lisans eklenmez.
