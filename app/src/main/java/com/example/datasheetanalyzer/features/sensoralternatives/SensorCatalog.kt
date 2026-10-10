package com.example.datasheetanalyzer.features.sensoralternatives

// Manufacturer document snapshots reviewed on 2026-10-09. No PDFs are distributed.
object SensorCatalog {
    private val lm35=SensorSource("https://www.ti.com/lit/ds/symlink/lm35.pdf","SNIS159H / Rev. H",38,"673b1c075b8f5a0653f77906c897ea5aec41a1dec02514770587100587c05eb9")
    private val tmp36=SensorSource("https://www.analog.com/media/en/technical-documentation/data-sheets/TMP35_36_37.pdf","TMP35/36/37 / Rev. H",19,"6ce062fb73f0bebb61f82775aca0be56903c2b8484a7fc89a0cf71961189f8ee")
    private val tmp102=SensorSource("https://www.ti.com/lit/ds/symlink/tmp102.pdf","SBOS397I / Rev. I",33,"c9da6620ef95f75ce94372f670a84d168e05922dd32cebfaa6517083f15ebfaf")
    private val tmp112=SensorSource("https://www.ti.com/lit/ds/symlink/tmp112.pdf","SBOS473L / Rev. L",46,"c70ca5900fabd8990e735aff3bb5cddb0b6da113b4a472d208c9181e3eb1b385")
    private val tmp117=SensorSource("https://www.ti.com/lit/ds/symlink/tmp117.pdf","SNOSD82D / Rev. D",50,"afe974873c80a7a4e1d342e9044e71c6f905caf4f04c1f0ea0df5c6ba35f980e")
    private val sht3x=SensorSource("https://admin.sensirion.com/media/documents/213E6A3B/63A5A569/Datasheet_SHT3x_DIS.pdf","SHT3x-DIS / V7, 12/2022",22,"095b1853e7f4328f5897c9ca6c392a7dd8b0202eda66b0a2629f9cb840dd496d")
    private val sht4x=SensorSource("https://sensirion.com/media/documents/33FD6951/661CD142/HT_DS_Datasheet_SHT4x.pdf","SHT4x / V6.5, 04/2024",24,"2d471eb9d9f57f619f697118a579240eaf160b8aa49275e0170ccf33eda16e93")
    private val bmp280=SensorSource("https://www.bosch-sensortec.com/media/boschsensortec/downloads/datasheets/bst-bmp280-ds001.pdf","BST-BMP280-DS001-26 / V1.26",49,"473ff27d9df698b4757e36b36209f83b9f637b592c999d5fabe2a9453a488da6")
    private val bme280=SensorSource("https://www.bosch-sensortec.com/media/boschsensortec/downloads/datasheets/bst-bme280-ds002.pdf","BST-BME280-DS001-24 / V1.24",60,"a2ccdb449fec94380742fe8eec851a11d9bd4142252d332b34682b4deecd7d89")
    private val bmp390=SensorSource("https://www.bosch-sensortec.com/media/boschsensortec/downloads/datasheets/bst-bmp390-ds002.pdf","BST-BMP390-DS002-07 / V1.7",59,"8dcf0f74449cb1627e865c7f24d328b76c204d8b9621091ede8c6869fb393e48")
    private val lis3dh=SensorSource("https://www.st.com/resource/en/datasheet/lis3dh.pdf","DocID17530 / Rev. 2",54,"852bfcf1a993f04cc147f26a26612a22fdc307c234f3e53d2c24c850ad421533")
    private val lis2dh12=SensorSource("https://www.st.com/resource/en/datasheet/lis2dh12.pdf","DocID025056 / Rev. 6",53,"eb79e6ee9ca70ef91f17c695fd5d2c6c6b24525b87b9ae41537bcb348f6cad56")
    private val vl53l0x=SensorSource("https://www.st.com/resource/en/datasheet/vl53l0x.pdf","DS11555 / Rev. 6",38,"f0ae51414474d311bc24c60cd77bfff968ea5c2b3765b2fb2a390b3078dd49be")
    private val vl53l1x=SensorSource("https://www.st.com/resource/en/datasheet/vl53l1x.pdf","DS12385 / Rev. 8",36,"b7f4174afaff82a82b5af68746e2df6f946a0de6733608e376b320a8cee6034a")
    private val bme680=SensorSource("https://www.bosch-sensortec.com/media/boschsensortec/downloads/datasheets/bst-bme680-ds001.pdf","BST-BME680-DS001-09 / V1.9",53,"8e42b591a9cabd84ae3377f2ea11e794120e4140dc4f0e4f29a30be3d1f6c9f6")
    private val bme688=SensorSource("https://www.bosch-sensortec.com/media/boschsensortec/downloads/datasheets/bst-bme688-ds000.pdf","BST-BME688-DS000-03 / V1.3",60,"fce73ae028a985b3265ce3dbc17ab5b7bea1ae31b7ea84c6698c7694083e2478")
    val profiles: List<SensorProfile> = listOf(
        SensorProfile("LM35","Texas Instruments",setOf(),setOf(SensorMeasurement.TEMPERATURE),setOf(SensorInterface.ANALOG),OperatingVoltage(4.0,30.0),lm35,mapOf(
            SensorField.SCOPE to SensorFact("LM35; C/CA/A sınıfları dahil değil",listOf(4)),
            SensorField.MEASUREMENT to SensorFact("Sıcaklık",listOf(1)),
            SensorField.SUPPLY to SensorFact("4…30 V",listOf(4)),
            SensorField.INTERFACE to SensorFact("Analog; 10 mV/°C, 0°C ofseti 0 V",listOf(1)),
            SensorField.RANGE to SensorFact("−55…150°C; negatif sıcaklık için uygun devre gerekir",listOf(1,4)),
            SensorField.ACCURACY to SensorFact("Sınıfa ve sıcaklığa bağlı; limit tablosu",listOf(8)),
            SensorField.PACKAGE to SensorFact("Paket seçenekleri; tam sipariş kodunu kontrol et",listOf(3)),
            SensorField.PINOUT to SensorFact("Paketlere göre pin sırası ve bakış yönü değişir",listOf(3)),
        )),
        SensorProfile("LM35D","Texas Instruments",setOf("LM35DZ","LM35DT"),setOf(SensorMeasurement.TEMPERATURE),setOf(SensorInterface.ANALOG),OperatingVoltage(4.0,30.0),lm35,mapOf(
            SensorField.SCOPE to SensorFact("LM35D; C/CA/A sınıfları dahil değil",listOf(4)),
            SensorField.MEASUREMENT to SensorFact("Sıcaklık",listOf(1)),
            SensorField.SUPPLY to SensorFact("4…30 V",listOf(4)),
            SensorField.INTERFACE to SensorFact("Analog; 10 mV/°C, 0°C ofseti 0 V",listOf(1)),
            SensorField.RANGE to SensorFact("0…100°C; negatif sıcaklık için uygun devre gerekir",listOf(1,4)),
            SensorField.ACCURACY to SensorFact("Sınıfa ve sıcaklığa bağlı; limit tablosu",listOf(8)),
            SensorField.PACKAGE to SensorFact("Paket seçenekleri; tam sipariş kodunu kontrol et",listOf(3)),
            SensorField.PINOUT to SensorFact("Paketlere göre pin sırası ve bakış yönü değişir",listOf(3)),
        )),
        SensorProfile("TMP35","Analog Devices",setOf(),setOf(SensorMeasurement.TEMPERATURE),setOf(SensorInterface.ANALOG),OperatingVoltage(2.7,5.5),tmp36,mapOf(
            SensorField.SCOPE to SensorFact("TMP35; F/G doğruluk sınıfları farklı",listOf(3,19)),
            SensorField.MEASUREMENT to SensorFact("Sıcaklık",listOf(1)),
            SensorField.SUPPLY to SensorFact("2.7…5.5 V",listOf(3)),
            SensorField.INTERFACE to SensorFact("Analog; 10 mV/°C, 0°C ofseti 0 V",listOf(1)),
            SensorField.RANGE to SensorFact("10…125°C",listOf(1)),
            SensorField.ACCURACY to SensorFact("25°C: F ±2°C maks / G ±3°C maks",listOf(3)),
            SensorField.PACKAGE to SensorFact("TO-92 / SOIC / SOT-23",listOf(1)),
            SensorField.PINOUT to SensorFact("Paketin çizimini ve pin sırasını kontrol et",listOf(1,10)),
        )),
        SensorProfile("TMP36","Analog Devices",setOf(),setOf(SensorMeasurement.TEMPERATURE),setOf(SensorInterface.ANALOG),OperatingVoltage(2.7,5.5),tmp36,mapOf(
            SensorField.SCOPE to SensorFact("TMP36; F/G doğruluk sınıfları farklı",listOf(3,19)),
            SensorField.MEASUREMENT to SensorFact("Sıcaklık",listOf(1)),
            SensorField.SUPPLY to SensorFact("2.7…5.5 V",listOf(3)),
            SensorField.INTERFACE to SensorFact("Analog; 10 mV/°C, 0°C ofseti 500 mV",listOf(1)),
            SensorField.RANGE to SensorFact("−40…125°C",listOf(1)),
            SensorField.ACCURACY to SensorFact("25°C: F ±2°C maks / G ±3°C maks",listOf(3)),
            SensorField.PACKAGE to SensorFact("TO-92 / SOIC / SOT-23",listOf(1)),
            SensorField.PINOUT to SensorFact("Paketin çizimini ve pin sırasını kontrol et",listOf(1,10)),
        )),
        SensorProfile("TMP102","Texas Instruments",setOf(),setOf(SensorMeasurement.TEMPERATURE),setOf(SensorInterface.I2C),OperatingVoltage(1.4,3.6),tmp102,mapOf(
            SensorField.SCOPE to SensorFact("TMP102; Q1 dahil değil",listOf(3)),
            SensorField.MEASUREMENT to SensorFact("Sıcaklık",listOf(1)),
            SensorField.SUPPLY to SensorFact("1.4…3.6 V",listOf(4)),
            SensorField.INTERFACE to SensorFact("I²C / SMBus; 12-bit, 0.0625°C",listOf(1)),
            SensorField.RANGE to SensorFact("−40…125°C",listOf(4)),
            SensorField.ACCURACY to SensorFact("±3°C maks, −40…125°C",listOf(5)),
            SensorField.PACKAGE to SensorFact("6 pin SOT563",listOf(3)),
            SensorField.PINOUT to SensorFact("SOT563: 1 SCL, 2 GND, 3 ALERT, 4 ADD0, 5 V+, 6 SDA",listOf(3)),
        )),
        SensorProfile("TMP112","Texas Instruments",setOf("TMP112A"),setOf(SensorMeasurement.TEMPERATURE),setOf(SensorInterface.I2C),OperatingVoltage(1.4,3.6),tmp112,mapOf(
            SensorField.SCOPE to SensorFact("TMP112A, DRL/SOT563; B/D/N/X2SON kapsam dışı",listOf(4)),
            SensorField.MEASUREMENT to SensorFact("Sıcaklık",listOf(1)),
            SensorField.SUPPLY to SensorFact("1.4…3.6 V",listOf(5)),
            SensorField.INTERFACE to SensorFact("I²C / SMBus; 12-bit, 0.0625°C",listOf(1)),
            SensorField.RANGE to SensorFact("−40…125°C",listOf(5)),
            SensorField.ACCURACY to SensorFact("TMP112A: ±1°C maks, −40…125°C",listOf(6)),
            SensorField.PACKAGE to SensorFact("6 pin SOT563",listOf(4)),
            SensorField.PINOUT to SensorFact("SOT563: 1 SCL, 2 GND, 3 ALERT, 4 ADD0, 5 V+, 6 SDA",listOf(4)),
        )),
        SensorProfile("TMP117","Texas Instruments",setOf(),setOf(SensorMeasurement.TEMPERATURE),setOf(SensorInterface.I2C),OperatingVoltage(1.8,5.5),tmp117,mapOf(
            SensorField.SCOPE to SensorFact("TMP117; M varyantı dahil değil",listOf(6)),
            SensorField.MEASUREMENT to SensorFact("Sıcaklık",listOf(1)),
            SensorField.SUPPLY to SensorFact("1.8…5.5 V, tam sıcaklık aralığında",listOf(5)),
            SensorField.INTERFACE to SensorFact("I²C / SMBus",listOf(1)),
            SensorField.RANGE to SensorFact("−55…150°C",listOf(5)),
            SensorField.ACCURACY to SensorFact("±0.1°C maks, −20…50°C; s.6 ölçüm koşulları",listOf(6)),
            SensorField.PACKAGE to SensorFact("WSON / DSBGA",listOf(4)),
            SensorField.PINOUT to SensorFact("WSON: 1 SCL, 2 GND, 3 ALERT, 4 ADD0, 5 V+, 6 SDA; SOT563 ile aynı paket değil",listOf(4)),
        )),
        SensorProfile("SHT30","Sensirion",setOf("SHT30-DIS"),setOf(SensorMeasurement.TEMPERATURE,SensorMeasurement.HUMIDITY),setOf(SensorInterface.I2C),OperatingVoltage(2.15,5.5),sht3x,mapOf(
            SensorField.SCOPE to SensorFact("SHT30-DIS; koruyucu kapak/membran seçeneklerini kontrol et",listOf(1)),
            SensorField.MEASUREMENT to SensorFact("Sıcaklık + bağıl nem",listOf(1)),
            SensorField.SUPPLY to SensorFact("2.15…5.5 V",listOf(6)),
            SensorField.INTERFACE to SensorFact("I²C",listOf(1)),
            SensorField.RANGE to SensorFact("−40…125°C / 0…100 %RH; önerilen normal bölge s.6",listOf(2,6)),
            SensorField.ACCURACY to SensorFact("Tipik: ±2 %RH / ±0.2°C; sınıfın sıcaklık koşulları s.2",listOf(2)),
            SensorField.PACKAGE to SensorFact("8 pin DFN, 2.5 × 2.5 mm",listOf(1)),
            SensorField.PINOUT to SensorFact("1 SDA, 2 ADDR, 3 ALERT, 4 SCL, 5 VDD, 6 nRESET, 7 R, 8 VSS",listOf(8)),
        )),
        SensorProfile("SHT31","Sensirion",setOf("SHT31-DIS"),setOf(SensorMeasurement.TEMPERATURE,SensorMeasurement.HUMIDITY),setOf(SensorInterface.I2C),OperatingVoltage(2.15,5.5),sht3x,mapOf(
            SensorField.SCOPE to SensorFact("SHT31-DIS; koruyucu kapak/membran seçeneklerini kontrol et",listOf(1)),
            SensorField.MEASUREMENT to SensorFact("Sıcaklık + bağıl nem",listOf(1)),
            SensorField.SUPPLY to SensorFact("2.15…5.5 V",listOf(6)),
            SensorField.INTERFACE to SensorFact("I²C",listOf(1)),
            SensorField.RANGE to SensorFact("−40…125°C / 0…100 %RH; önerilen normal bölge s.6",listOf(2,6)),
            SensorField.ACCURACY to SensorFact("Tipik: ±2 %RH / ±0.2°C; sınıfın sıcaklık koşulları s.2",listOf(2)),
            SensorField.PACKAGE to SensorFact("8 pin DFN, 2.5 × 2.5 mm",listOf(1)),
            SensorField.PINOUT to SensorFact("1 SDA, 2 ADDR, 3 ALERT, 4 SCL, 5 VDD, 6 nRESET, 7 R, 8 VSS",listOf(8)),
        )),
        SensorProfile("SHT35","Sensirion",setOf("SHT35-DIS"),setOf(SensorMeasurement.TEMPERATURE,SensorMeasurement.HUMIDITY),setOf(SensorInterface.I2C),OperatingVoltage(2.15,5.5),sht3x,mapOf(
            SensorField.SCOPE to SensorFact("SHT35-DIS; koruyucu kapak/membran seçeneklerini kontrol et",listOf(1)),
            SensorField.MEASUREMENT to SensorFact("Sıcaklık + bağıl nem",listOf(1)),
            SensorField.SUPPLY to SensorFact("2.15…5.5 V",listOf(6)),
            SensorField.INTERFACE to SensorFact("I²C",listOf(1)),
            SensorField.RANGE to SensorFact("−40…125°C / 0…100 %RH; önerilen normal bölge s.6",listOf(2,6)),
            SensorField.ACCURACY to SensorFact("Tipik: ±1.5 %RH / ±0.1°C (20…60°C)",listOf(2)),
            SensorField.PACKAGE to SensorFact("8 pin DFN, 2.5 × 2.5 mm",listOf(1)),
            SensorField.PINOUT to SensorFact("1 SDA, 2 ADDR, 3 ALERT, 4 SCL, 5 VDD, 6 nRESET, 7 R, 8 VSS",listOf(8)),
        )),
        SensorProfile("SHT40","Sensirion",setOf("SHT40-AD1B"),setOf(SensorMeasurement.TEMPERATURE,SensorMeasurement.HUMIDITY),setOf(SensorInterface.I2C),OperatingVoltage(1.08,3.6),sht4x,mapOf(
            SensorField.SCOPE to SensorFact("SHT40-AD1B; diğer adres/koruma seçenekleri dahil değil",listOf(1)),
            SensorField.MEASUREMENT to SensorFact("Sıcaklık + bağıl nem",listOf(1)),
            SensorField.SUPPLY to SensorFact("1.08…3.6 V",listOf(9)),
            SensorField.INTERFACE to SensorFact("I²C",listOf(1)),
            SensorField.RANGE to SensorFact("−40…125°C / 0…100 %RH; önerilen bölge s.8",listOf(4,6,8)),
            SensorField.ACCURACY to SensorFact("T: ±0.2°C tipik; RH koşullu eğriler s.5",listOf(5,6)),
            SensorField.PACKAGE to SensorFact("4 pin DFN, 1.5 × 1.5 mm",listOf(15)),
            SensorField.PINOUT to SensorFact("1 SDA, 2 SCL, 3 VDD, 4 VSS",listOf(16)),
        )),
        SensorProfile("SHT41","Sensirion",setOf("SHT41-AD1B"),setOf(SensorMeasurement.TEMPERATURE,SensorMeasurement.HUMIDITY),setOf(SensorInterface.I2C),OperatingVoltage(1.08,3.6),sht4x,mapOf(
            SensorField.SCOPE to SensorFact("SHT41-AD1B; diğer adres/koruma seçenekleri dahil değil",listOf(1)),
            SensorField.MEASUREMENT to SensorFact("Sıcaklık + bağıl nem",listOf(1)),
            SensorField.SUPPLY to SensorFact("1.08…3.6 V",listOf(9)),
            SensorField.INTERFACE to SensorFact("I²C",listOf(1)),
            SensorField.RANGE to SensorFact("−40…125°C / 0…100 %RH; önerilen bölge s.8",listOf(4,6,8)),
            SensorField.ACCURACY to SensorFact("T: ±0.2°C tipik; RH koşullu eğriler s.5",listOf(5,6)),
            SensorField.PACKAGE to SensorFact("4 pin DFN, 1.5 × 1.5 mm",listOf(15)),
            SensorField.PINOUT to SensorFact("1 SDA, 2 SCL, 3 VDD, 4 VSS",listOf(16)),
        )),
        SensorProfile("SHT45","Sensirion",setOf("SHT45-AD1B"),setOf(SensorMeasurement.TEMPERATURE,SensorMeasurement.HUMIDITY),setOf(SensorInterface.I2C),OperatingVoltage(1.08,3.6),sht4x,mapOf(
            SensorField.SCOPE to SensorFact("SHT45-AD1B; diğer adres/koruma seçenekleri dahil değil",listOf(1)),
            SensorField.MEASUREMENT to SensorFact("Sıcaklık + bağıl nem",listOf(1)),
            SensorField.SUPPLY to SensorFact("1.08…3.6 V",listOf(9)),
            SensorField.INTERFACE to SensorFact("I²C",listOf(1)),
            SensorField.RANGE to SensorFact("−40…125°C / 0…100 %RH; önerilen bölge s.8",listOf(4,6,8)),
            SensorField.ACCURACY to SensorFact("T: ±0.1°C tipik; RH koşullu eğriler s.5",listOf(5,6)),
            SensorField.PACKAGE to SensorFact("4 pin DFN, 1.5 × 1.5 mm",listOf(15)),
            SensorField.PINOUT to SensorFact("1 SDA, 2 SCL, 3 VDD, 4 VSS",listOf(16)),
        )),
        SensorProfile("BMP280","Bosch Sensortec",setOf(),setOf(SensorMeasurement.TEMPERATURE,SensorMeasurement.PRESSURE),setOf(SensorInterface.I2C,SensorInterface.SPI),OperatingVoltage(1.71,3.6),bmp280,mapOf(
            SensorField.SCOPE to SensorFact("BMP280 sensör IC; taşıyıcı kartın beslemesi farklı olabilir",listOf(1)),
            SensorField.MEASUREMENT to SensorFact("Sıcaklık + basınç",listOf(3)),
            SensorField.SUPPLY to SensorFact("1.71…3.6 V VDD; VDDIO 1.2…3.6 V",listOf(7)),
            SensorField.INTERFACE to SensorFact("I²C / SPI",listOf(3)),
            SensorField.RANGE to SensorFact("300…1100 hPa / −40…85°C; tam doğruluk 0…65°C",listOf(7)),
            SensorField.ACCURACY to SensorFact("Basınç: ±1 hPa tipik, 300…1100 hPa, 0…65°C",listOf(8)),
            SensorField.PACKAGE to SensorFact("8 pin LGA, 2.0 × 2.5 mm",listOf(3)),
            SensorField.PINOUT to SensorFact("VDD ve VDDIO ayrı; numaralı bağlantı tablosunu aç",listOf(34)),
        )),
        SensorProfile("BME280","Bosch Sensortec",setOf(),setOf(SensorMeasurement.TEMPERATURE,SensorMeasurement.HUMIDITY,SensorMeasurement.PRESSURE),setOf(SensorInterface.I2C,SensorInterface.SPI),OperatingVoltage(1.71,3.6),bme280,mapOf(
            SensorField.SCOPE to SensorFact("BME280 sensör IC; taşıyıcı kartın beslemesi farklı olabilir",listOf(1)),
            SensorField.MEASUREMENT to SensorFact("Sıcaklık + basınç + nem",listOf(2)),
            SensorField.SUPPLY to SensorFact("1.71…3.6 V VDD; VDDIO 1.2…3.6 V",listOf(8)),
            SensorField.INTERFACE to SensorFact("I²C / SPI",listOf(2)),
            SensorField.RANGE to SensorFact("300…1100 hPa / 0…100 %RH / −40…85°C",listOf(9,10,11,12)),
            SensorField.ACCURACY to SensorFact("RH: ±3 %RH, 20…80 %RH, 25°C; basınç/ısı koşulları s.11–12",listOf(9)),
            SensorField.PACKAGE to SensorFact("8 pin LGA, 2.5 × 2.5 mm",listOf(2)),
            SensorField.PINOUT to SensorFact("VDD ve VDDIO ayrı; numaralı bağlantı tablosunu aç",listOf(38)),
        )),
        SensorProfile("BMP390","Bosch Sensortec",setOf(),setOf(SensorMeasurement.TEMPERATURE,SensorMeasurement.PRESSURE),setOf(SensorInterface.I2C,SensorInterface.SPI),OperatingVoltage(1.65,3.6),bmp390,mapOf(
            SensorField.SCOPE to SensorFact("BMP390 sensör IC; taşıyıcı kartın beslemesi farklı olabilir",listOf(1)),
            SensorField.MEASUREMENT to SensorFact("Sıcaklık + basınç",listOf(3)),
            SensorField.SUPPLY to SensorFact("1.65…3.6 V VDD; VDDIO 1.2…3.6 V",listOf(7)),
            SensorField.INTERFACE to SensorFact("I²C / SPI",listOf(3)),
            SensorField.RANGE to SensorFact("300…1250 hPa / −40…85°C; tam doğruluk 0…65°C",listOf(7)),
            SensorField.ACCURACY to SensorFact("Basınç: ±0.50 hPa tipik, 300…1100 hPa, 0…65°C",listOf(8)),
            SensorField.PACKAGE to SensorFact("10 pin LGA, 2.0 × 2.0 mm",listOf(3)),
            SensorField.PINOUT to SensorFact("VDD ve VDDIO ayrı; numaralı bağlantı tablosunu aç",listOf(46)),
        )),
        SensorProfile("BME680","Bosch Sensortec",setOf(),setOf(SensorMeasurement.TEMPERATURE,SensorMeasurement.HUMIDITY,SensorMeasurement.PRESSURE,SensorMeasurement.GAS_RESISTANCE),setOf(SensorInterface.I2C,SensorInterface.SPI),OperatingVoltage(1.71,3.6),bme680,mapOf(
            SensorField.SCOPE to SensorFact("BME680 IC; gaz kanalı doğrudan gaz türü/ppm muadili değildir",listOf(2,22)),
            SensorField.MEASUREMENT to SensorFact("Sıcaklık + nem + basınç + gaz direnci",listOf(2,22)),
            SensorField.SUPPLY to SensorFact("1.71…3.6 V VDD; VDDIO 1.2…3.6 V",listOf(3)),
            SensorField.INTERFACE to SensorFact("I²C / SPI; modelin sürücüsü ve gaz profili farklı olabilir",listOf(3)),
            SensorField.RANGE to SensorFact("300…1100 hPa / 0…100 %RH / −40…85°C; gaz kanalı koşula bağlı",listOf(3)),
            SensorField.ACCURACY to SensorFact("RH: ±3 %RH, 20…80 %RH, 25°C; diğer kanalların koşulları kaynakta",listOf(10)),
            SensorField.PACKAGE to SensorFact("LGA8, 3 × 3 mm",listOf(2)),
            SensorField.PINOUT to SensorFact("Numaralı pin çizimi, besleme ve PCB alanı kontrol edilmeli",listOf(44)),
        )),
        SensorProfile("BME688","Bosch Sensortec",setOf(),setOf(SensorMeasurement.TEMPERATURE,SensorMeasurement.HUMIDITY,SensorMeasurement.PRESSURE,SensorMeasurement.GAS_RESISTANCE),setOf(SensorInterface.I2C,SensorInterface.SPI),OperatingVoltage(1.71,3.6),bme688,mapOf(
            SensorField.SCOPE to SensorFact("BME688 IC; gaz kanalı doğrudan gaz türü/ppm muadili değildir",listOf(2,28)),
            SensorField.MEASUREMENT to SensorFact("Sıcaklık + nem + basınç + gaz direnci",listOf(2,28)),
            SensorField.SUPPLY to SensorFact("1.71…3.6 V VDD; VDDIO 1.2…3.6 V",listOf(3)),
            SensorField.INTERFACE to SensorFact("I²C / SPI; modelin sürücüsü ve gaz profili farklı olabilir",listOf(3)),
            SensorField.RANGE to SensorFact("300…1100 hPa / 0…100 %RH / −40…85°C; gaz kanalı koşula bağlı",listOf(3)),
            SensorField.ACCURACY to SensorFact("RH: ±3 %RH, 20…80 %RH, 25°C; diğer kanalların koşulları kaynakta",listOf(12)),
            SensorField.PACKAGE to SensorFact("LGA8, 3 × 3 mm",listOf(2)),
            SensorField.PINOUT to SensorFact("Numaralı pin çizimi, besleme ve PCB alanı kontrol edilmeli",listOf(51)),
        )),
        SensorProfile("LIS3DH","STMicroelectronics",setOf(),setOf(SensorMeasurement.ACCELERATION),setOf(SensorInterface.I2C,SensorInterface.SPI),OperatingVoltage(1.71,3.6),lis3dh,mapOf(
            SensorField.SCOPE to SensorFact("LIS3DH; dahili sıcaklık kanalı bağımsız sıcaklık sensörü yerine önerilmez",listOf(1,11)),
            SensorField.MEASUREMENT to SensorFact("3 eksen ivme",listOf(1)),
            SensorField.SUPPLY to SensorFact("1.71…3.6 V Vdd; Vdd_IO ayrıca kontrol edilmeli",listOf(12)),
            SensorField.INTERFACE to SensorFact("I²C / SPI",listOf(1)),
            SensorField.RANGE to SensorFact("Seçilebilir ±2 / ±4 / ±8 / ±16 g",listOf(10)),
            SensorField.ACCURACY to SensorFact("Sıfır-g ofseti ±40 mg tipik; 25°C, Vdd=2.5 V",listOf(10)),
            SensorField.PACKAGE to SensorFact("LGA12, 3 × 3 mm",listOf(1)),
            SensorField.PINOUT to SensorFact("Pin tablosu ve farklı PCB alanı kontrol edilmeli",listOf(9)),
        )),
        SensorProfile("LIS2DH12","STMicroelectronics",setOf(),setOf(SensorMeasurement.ACCELERATION),setOf(SensorInterface.I2C,SensorInterface.SPI),OperatingVoltage(1.71,3.6),lis2dh12,mapOf(
            SensorField.SCOPE to SensorFact("LIS2DH12; dahili sıcaklık kanalı bağımsız sıcaklık sensörü yerine önerilmez",listOf(1,11)),
            SensorField.MEASUREMENT to SensorFact("3 eksen ivme",listOf(1)),
            SensorField.SUPPLY to SensorFact("1.71…3.6 V Vdd; Vdd_IO ayrıca kontrol edilmeli",listOf(12)),
            SensorField.INTERFACE to SensorFact("I²C / SPI",listOf(1)),
            SensorField.RANGE to SensorFact("Seçilebilir ±2 / ±4 / ±8 / ±16 g",listOf(10)),
            SensorField.ACCURACY to SensorFact("Sıfır-g ofseti ±40 mg tipik; 25°C, Vdd=2.5 V",listOf(10)),
            SensorField.PACKAGE to SensorFact("LGA12, 2 × 2 mm",listOf(1)),
            SensorField.PINOUT to SensorFact("Pin tablosu ve farklı PCB alanı kontrol edilmeli",listOf(9)),
        )),
        SensorProfile("VL53L0X","STMicroelectronics",setOf(),setOf(SensorMeasurement.DISTANCE),setOf(SensorInterface.I2C),OperatingVoltage(2.6,3.5),vl53l0x,mapOf(
            SensorField.SCOPE to SensorFact("VL53L0X optik sensör; modül kartı kapsam dışı",listOf(3)),
            SensorField.MEASUREMENT to SensorFact("ToF mesafe",listOf(1)),
            SensorField.SUPPLY to SensorFact("2.6…3.5 V AVDD; 1V8/2V8 I/O modu ayrı",listOf(19)),
            SensorField.INTERFACE to SensorFact("I²C; ayrı sürücü ve kalibrasyon gerekir",listOf(3)),
            SensorField.RANGE to SensorFact("2 m’ye kadar; hedef ve ışık koşullarına bağlı",listOf(1)),
            SensorField.ACCURACY to SensorFact("Hedef, ortam ışığı ve zaman bütçesine bağlı; performans tabloları",listOf(21)),
            SensorField.PACKAGE to SensorFact("Optical LGA12; boyutlar farklı",listOf(3)),
            SensorField.PINOUT to SensorFact("XSHUT / GPIO / besleme / I²C pin çizimini aç",listOf(5)),
        )),
        SensorProfile("VL53L1X","STMicroelectronics",setOf(),setOf(SensorMeasurement.DISTANCE),setOf(SensorInterface.I2C),OperatingVoltage(2.6,3.5),vl53l1x,mapOf(
            SensorField.SCOPE to SensorFact("VL53L1X optik sensör; modül kartı kapsam dışı",listOf(4)),
            SensorField.MEASUREMENT to SensorFact("ToF mesafe",listOf(1)),
            SensorField.SUPPLY to SensorFact("2.6…3.5 V AVDD; 1V8/2V8 I/O modu ayrı",listOf(18)),
            SensorField.INTERFACE to SensorFact("I²C; ayrı sürücü ve kalibrasyon gerekir",listOf(4)),
            SensorField.RANGE to SensorFact("4 m’ye kadar; karanlık, beyaz hedef, uzun mod, 140 ms",listOf(9,10)),
            SensorField.ACCURACY to SensorFact("Hedef, ortam ışığı ve zaman bütçesine bağlı; performans tabloları",listOf(19)),
            SensorField.PACKAGE to SensorFact("Optical LGA12; boyutlar farklı",listOf(4)),
            SensorField.PINOUT to SensorFact("XSHUT / GPIO / besleme / I²C pin çizimini aç",listOf(6)),
        )),
    ).map { sensor ->
        if(sensor.code in setOf("LIS3DH","LIS2DH12")) sensor.copy(axisCounts=mapOf(SensorMeasurement.ACCELERATION to 3)) else sensor
    } + ExpandedSensorCatalog.profiles
    val proofs = listOf(
        SensorReplacementProof("TMP102","TMP112","https://www.ti.com/product/TMP102",
            "TI üretici muadil beyanı ve TMP102 s.8: yalnız TMP112A DRL/SOT563 için; Q1/D/N/X2SON ve taşıyıcı kartlar kapsam dışı. Uygulamanın eşik, zamanlama ve doğruluk koşullarını kontrol et.")
    )
}
