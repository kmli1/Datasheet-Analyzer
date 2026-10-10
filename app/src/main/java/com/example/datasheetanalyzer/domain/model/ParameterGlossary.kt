package com.example.datasheetanalyzer.domain.model

data class TermExplanation(val title: String,val definition: String,val example: String)
object ParameterGlossary {
    fun criterion(value: Criterion)=when(value) {
        Criterion.SUPPLY_VOLTAGE -> TermExplanation("Besleme gerilimi","Parçanın güç aldığı uçlar arasındaki gerilimdir. Önerilen çalışma aralığı ile mutlak maksimum sınır farklıdır.","5–12 V çalışma aralığı, bu aralıktaki her gerilimde tüm performans değerlerinin aynı olacağı anlamına gelmez.")
        Criterion.SUPPLY_CURRENT -> TermExplanation("Besleme / boşta akım","Parçanın beslemeden çektiği akımdır. Quiescent current genellikle yükü sürmek için gereken akımı kapsamaz.","Boşta 4 mA tüketen bir yükselteç, hoparlör bağlıyken daha fazla akım çekebilir.")
        Criterion.MAXIMUM_CURRENT -> TermExplanation("Maksimum akım","Belirtilen akım sınırıdır. Sürekli akım, kısa süreli tepe akımı ve test sıcaklığı ayrı değerlendirilir.","10 A tepe akımı yazması, parçanın sürekli 10 A taşıyabileceği anlamına gelmez.")
        Criterion.OUTPUT_CURRENT -> TermExplanation("Çıkış akımı","Çıkıştan verilebilen veya çekilebilen akımdır. Gerilim, sıcaklık, yük ve soğutma koşullarına bağlı olabilir.","Bir regülatörün çıkış akımını değerlendirirken giriş–çıkış farkı ve güç kaybını da incele.")
        Criterion.INPUT_VOLTAGE -> TermExplanation("Giriş gerilimi","Sinyal veya güç girişine uygulanabilecek gerilimdir. Besleme gerilimiyle aynı parametre olmayabilir.","Op-amp girişinin uygunluğu için giriş ortak-mod aralığı da gerekir.")
        Criterion.OUTPUT_VOLTAGE -> TermExplanation("Çıkış gerilimi","Çıkışın gerilim değeri veya ulaşabildiği aralıktır. Yük ve besleme test koşulları önemlidir.","5 V ile beslenen bir yükseltecin çıkışı her zaman tam 0–5 V aralığına ulaşmaz.")
        Criterion.POWER_DISSIPATION -> TermExplanation("Güç kaybı","Parçanın ısıya dönüştürdüğü güç veya izin verilen güç kaybı sınırıdır. Çıkış gücüyle aynı değildir.","Doğrusal regülatörde yaklaşık kayıp: (giriş gerilimi − çıkış gerilimi) × yük akımı.")
        Criterion.OPERATING_TEMPERATURE -> TermExplanation("Çalışma sıcaklığı","Parçanın çalışması için belirtilen sıcaklık aralığıdır. Ortam (TA) ve jonksiyon (TJ) sıcaklığı farklıdır.","TA = 25 °C koşulunda ölçülen bir değer, TJ = 150 °C sınırıyla aynı bilgi değildir.")
        Criterion.STORAGE_TEMPERATURE -> TermExplanation("Depolama sıcaklığı","Parça çalıştırılmadan saklanırken izin verilen sıcaklık aralığıdır.","Depolama aralığı geniş olsa bile aynı sıcaklıkta çalıştırma uygun olmayabilir.")
        Criterion.FREQUENCY -> TermExplanation("Frekans","Bir sinyalin veya çalışma döngüsünün saniyedeki tekrar sayısıdır. Hz, kHz ve MHz birimleri kullanılır.","1 kHz = 1000 Hz. Test frekansı ile izin verilen çalışma frekansı farklı olabilir.")
        Criterion.BANDWIDTH -> TermExplanation("Bant genişliği","Belirtilen performans ölçütüne göre kullanılabilen frekans aralığıdır. Kazanç-bant genişliği çarpımıyla aynı değildir.","Bant genişliği, kazanç ve yük değiştiğinde değişebilir; ölçüm koşulunu oku.")
        Criterion.GAIN -> TermExplanation("Kazanç","Çıkışın girişe oranını ifade eder. Gerilim kazancı, güç kazancı ve dB cinsinden kazanç ayrılmalıdır.","Gerilim kazancı 10, yaklaşık 20 dB'dir; güç kazancı 10 ise 10 dB'dir.")
        Criterion.INPUT_OFFSET_VOLTAGE -> TermExplanation("Giriş ofset gerilimi","Op-amp girişleri eşit olsa bile çıkışta oluşan hatayı dengelemek için gereken küçük giriş gerilimidir.","Düşük seviyeli sensör ölçümlerinde ofset hatası önem kazanır.")
        Criterion.PINOUT -> TermExplanation("Pin bağlantıları","Paketin pin numaralarını ve her pinin görevini gösterir. Üstten/alttan görünüm ve paket çeşidi önemlidir.","Aynı parça ailesinin farklı paketlerinde pin yerleşimi değişebilir.")
        Criterion.PACKAGE -> TermExplanation("Paket / kılıf","Parçanın fiziksel gövdesini, pin sayısını ve boyutlarını belirtir.","SOIC ve DIP aynı işlevi taşıyabilir ancak PCB ayak izleri farklıdır.")
        Criterion.FEATURES -> TermExplanation("Özellikler","Üreticinin öne çıkardığı işlev ve performans özellikleridir. Ayrıntılı garanti koşulları için tablolar gerekir.","Rail-to-rail ifadesinin girişe mi çıkışa mı ait olduğunu kontrol et.")
        Criterion.APPLICATIONS -> TermExplanation("Uygulamalar","Parçanın önerilen kullanım alanları ve örnek devreleridir.","Örnek devredeki besleme, direnç ve kondansatör değerlerini kendi devrene göre değerlendir.")
        Criterion.ELECTRICAL_CHARACTERISTICS -> TermExplanation("Elektriksel özellikler","Belirli test koşullarındaki minimum, tipik ve maksimum performans değerlerini içerir.","Bir değerle birlikte besleme, sıcaklık ve yük koşullarını da oku.")
        Criterion.ABSOLUTE_MAXIMUMS -> TermExplanation("Mutlak maksimum sınırlar","Aşılması hasara yol açabilecek sınırları belirtir. Bu sınırlarda normal çalışma veya performans garanti edilmez.","Mutlak maksimum 30 V bilgisi, 30 V'un önerilen besleme olduğu anlamına gelmez.")
        Criterion.RECOMMENDED_OPERATING_CONDITIONS -> TermExplanation("Önerilen çalışma koşulları","Üreticinin normal çalışma için belirttiği gerilim, sıcaklık ve diğer koşullardır.","Çalışma aralığını seçerken mutlak maksimum tablosu yerine bu bölümü kullan.")
    }
    val tableTerms=listOf(
        TermExplanation("Min / minimum","Belirtilen koşullardaki alt sınırdır. Hücre boşsa alt sınır bu satırda verilmemiştir.","Boş minimum hücresinden sıfır değeri çıkarılamaz."),
        TermExplanation("Tipik / typical","Belirtilen koşullardaki temsilî değerdir; garanti edilen sınır değildir.","Tipik 12 V bilgisi, her parçada kesin 12 V ölçüleceği anlamına gelmez."),
        TermExplanation("Maks / maximum","Belirtilen koşullardaki üst sınırdır. Hangi bölümde yazıldığına göre anlamı değişir.","Elektriksel özellikler tablosundaki maksimum ile mutlak maksimum farklıdır."),
        TermExplanation("Test koşulları","Değerin hangi besleme, yük, frekans ve sıcaklıkta ölçüldüğünü açıklar.","Farklı test koşullarındaki iki değeri doğrudan üstünlük sırasına koyma."),
        TermExplanation("Kanıt puanı","Uygulamanın eşleşme için kullandığı göstergedir; doğruluk yüzdesi veya üretici garantisi değildir.","80/100 puan, değerin %80 doğru olduğu anlamına gelmez.")
    )
}
