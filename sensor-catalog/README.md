# Sensör kataloğu veri bakımı

`expanded-1.0.10.json`, ana PDF algoritmasından bağımsız 138 yeni modelin incelenmiş veri kaydıdır. Temel 22 profil `SensorCatalog.kt` içinde korunur. Kaynak bağlantıları ve tüm profil sayfa eşlemeleri proje kökündeki `SENSOR_KAYNAKLARI.json` içindedir.

Her model için üreticinin PDF gövdesindeki tam parça adı, normal çalışma beslemesi, arabirim, ölçüm türü/aralığı, paket ve pin sayfası incelenmiştir. Kaynak SHA-256 ve fiziksel PDF sayfa sayısı kayıtlıdır. `EVIDENCE_AUDIT_1.0.10.json` yeni 138 belgenin yerel anlık görüntü denetim kaydıdır; tarihsel sürümü tanımlar, üreticinin gelecekte değişen PDF'sini garanti etmez. Belge ve alıntıları kaynak paketine eklemeyin.

Kayıt güncellenecekse önce gerçek üretici PDF'sindeki tam model ve elektriksel sınıfı doğrulayın. Mutlak maksimum beslemeyi çalışma aralığı yerine kullanmayın. Pin/sipariş son eki veya eksik doğruluk koşulunu tahmin etmeyin. Birden çok besleme rayı için koşulu SUPPLY metninde açıkça koruyun; `additional_voltage_ranges` tek ana rayın ayrık çalışma bantları içindir. IMU'nun yardımcı sıcaklık kanalını bağımsız sıcaklık ölçümü olarak eklemeyin. Sürekli alan/anahtar ve lux/ışınım türlerini karıştırmayın. Çok eksenli ölçümleri `axis_counts` ile belirtin. Tek bir genel hata değeri doğrulanamıyorsa koşullu kaynak tablosuna yönlendirin.

Derlenmiş veriyi üretmek için `python3 scripts/generate_sensor_catalog.py`, tutarlılık için aynı komuta `--check` ekleyin. Ardından `./gradlew :app:testDebugUnitTest :app:lintDebug` çalıştırın. Yeni kayıt tek başına doğrudan muadil kanıtı oluşturmaz; üreticinin açık yönlü beyanı ayrı incelenmelidir. Katalogta stok, fiyat veya üretim ömrü garantisi bulunmaz.
