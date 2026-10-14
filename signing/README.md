# Yerel geliştirme imzası

Anahtar dosyaları Git deposuna eklenmez. Yeni klonda `signing/debug.keystore` yoksa Android'in varsayılan yerel debug imzası kullanılır.

Daha önce paylaşılan 1.0.10 APK'sını aynı uygulama kimliğiyle güncellemek için önceki kaynak RAR'ındaki ortak **test** anahtarını yalnız kendi bilgisayarınızda `signing/debug.keystore` konumuna kopyalayabilirsiniz. Dosya bulunduğunda mevcut test imzası seçilir. Bu dosya `.gitignore` kapsamındadır; `git add -f` ile eklemeyin.

Yeni bilgisayarda üretilen farklı debug sertifikası, önceki sertifikayla kurulmuş APK'nın üzerine güncelleme olarak kurulamaz. İmzayı değiştirmeden ilerlemek istiyorsanız yukarıdaki yerel test anahtarını kullanın. Mevcut uygulamayı kaldırmak yerel kayıtları silebileceğinden bunun yerine aynı imzayı koruyun.

Üretim için ayrı bir anahtar ve depo dışındaki `keystore.properties` dosyası kullanılır. `-PreleaseSigningFile=/private/keystore.properties` ve kendi `-PreleaseApplicationId=...` değerinizle derleyin. Ortak test anahtarı üretim imzası olarak kullanılmaz.

Resmî açıklama: https://developer.android.com/studio/publish/app-signing
