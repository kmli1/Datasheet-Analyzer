# Datasheet Analyzer 1.0.10 — GitHub tamamlama paketi

Hedef repo: https://github.com/kmli1/Datasheet-Analyzer

Bu paket, mevcut başlangıç commitinin üzerine tek commit ekler. Repo geçmişi ve önceden bulunan altı ek dosya korunur. Ana analiz algoritması değiştirilmez.

Düzeltmeler: .gitignore nedeniyle dışarıda kalan DocumentCache.kt, RecentSearches.kt ve 16 sentetik test PDF'si geri eklenir. README, Git satır sonu ayarları ve imza yönergeleri tamamlanır. Depoda olmayan test anahtarı yerine, yeni klonun Android'in yerel varsayılan debug imzasını kullanabilmesi sağlanır.

## Windows PowerShell

ZIP'i açın. Mevcut Datasheet-Analyzer Git repo klasörünüzde PowerShell'i açın. Aşağıdaki ilk komuttaki tam yolu, açtığınız bundle dosyasının gerçek yoluyla değiştirin:

```powershell
git pull --ff-only "C:\Indirilenler\DatasheetAnalyzer_1.0.10_GitHub.bundle" main
git push origin main
```

İlk komut düzeltmeleri yerel reponuza alır. İkinci komut GitHub'a yükler ve gerekiyorsa GitHub girişini ister. Bundle dosyasını GitHub'ın web dosya yükleme ekranına yüklemeyin; bu bir Git aktarım paketidir.

Yerel değişiklikler yüzünden ilk komut durursa çıktıyı paylaşın; bu komut geçmişi zorla değiştirmez. Repo henüz bilgisayarınızda yoksa önce `git clone https://github.com/kmli1/Datasheet-Analyzer.git` ve `cd Datasheet-Analyzer` komutlarını çalıştırın.

## Paket doğrulaması

- Önkoşul commit: d2bf7b043e8b6934d09374010d7091d197cbcad2
- Tamamlama commit: 490f2ef4995ea89c6b2d6d6b21c87cd8c4e15073
- İlk committen ayrı bir yerel repoda yukarıdaki `git pull --ff-only` komutu denenerek 249 dosyanın Git nesneleri ve dosya kipleri karşılaştırıldı.
- 144 korunan ana kaynak dosyasının SHA-256 değerleri eşleşti; özgün uygulama/test kaynaklarının 207 dosyası değişmedi.
- Sensör kataloğu kaynak ve üretilmiş Kotlin tutarlılık kontrolü geçti. 16 test PDF'si ve iki cache Kotlin kaynağı izleniyor; gradlew çalıştırılabilir.
- İmza anahtarı, APK, kişisel SDK ayarı ve seçilen gizli anahtar kalıpları pakete alınmadı.
- Bu tamamlama sırasında Android derlemesi ve Gradle testleri yeniden çalıştırılmadı. Önceki sürümün test sonuçları tarihsel kayıttır.

Bu dosyalar GitHub'a Codex tarafından yüklenmiş değildir: GitHub API yazma isteği 403 ile reddedildi; terminalde GitHub oturumu yoktu. İki komut yerelde uygulama ve yükleme için hazırlanmıştır.
