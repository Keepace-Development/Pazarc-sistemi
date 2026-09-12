# Pazarci

Pazarci, Paper sunucularinda oyuncularin sandik ve tabela uzerinden guvenli sekilde urun alip satmasini saglayan bir Minecraft eklentisidir. Oyuncular fiyat belirleyebilir, pazarlarini `SATIS` veya `ALIM` moduna alabilir ve islemler Vault ekonomi sistemi uzerinden gerceklestirilir.

## Desteklenen ortam

- Minecraft/Paper: `1.21.11`
- Java: `21` veya daha yeni bir Java 21 kurulumu
- Vault: Kurulu ve aktif olmali
- Ekonomi saglayicisi: EssentialsX Economy, CMI Economy veya Vault destekleyen baska bir ekonomi eklentisi
- Plugin API bagimliligi: VaultAPI `1.7.1`

Eklenti tek basina para sistemi saglamaz. Vault, ekonomi eklentileri arasinda kopru gorevi gorur. Sunucuda Vault kurulu olsa bile bir ekonomi saglayicisi yoksa alis ve satis islemleri gerceklestirilmez.

## Kurulum

### Gereksinimleri kur

Sunucunun `plugins` klasorunde su eklentiler bulunmalidir:

1. `Vault`
2. Vault ile uyumlu bir ekonomi eklentisi
3. Derlenen `Pazarci` eklentisi

### JAR dosyasini kopyala

Derleme sonrasinda olusan `target/pazarci-1.0.0.jar` dosyasini Paper sunucusunun `plugins` klasorune kopyala ve sunucuyu baslat.

Sunucu konsolunda buna benzer bir mesaj gorulmelidir:

```text
Pazarci aktif. Vault ekonomi: true
```

`Vault ekonomi: false` goruluyorsa Vault veya ekonomi saglayicisi dogru yuklenmemistir.

## Pazar olusturma

1. Bos bir sandik yerlestir.
2. Satmak veya pazarda urun olarak kullanmak istedigin esyayi ana eline al.
3. Sandiga **sol tikla**.
4. Plugin sadece senin gorebilecegin su soruyu gonderir: `Elindeki esyayi kac liraya pazarlamak istiyorsun?`
5. Sohbete pozitif bir tam sayi yaz. Ornek: `7`
6. Plugin, oyuncunun bulundugu tarafta sandiga yapisik bir tabelayi otomatik olusturur.

Fiyat sorusu 10 saniye boyunca cevapsiz kalirsa kurulum iptal edilir. Gecersiz, sifir veya negatif deger yazilirsa pazar kurulmaz.

Pazar kurulurken elindeki esya otomatik olarak sandiga aktarilmaz. Pazar sahibi, sandiga sag tiklayip envanter acildiktan sonra stok olarak satacagi urunleri kendisi koyar.

## Tabela bilgileri

Otomatik tabela su bilgileri gosterir:

- Pazar modu: `SATIS` veya `ALIM`
- Pazar sahibinin oyuncu adi
- Urun adi
- Birim fiyat
- Mevcut stok miktari

Sandikta ilgili urunden hic kalmadiginda tabelanin son satirinda `Stokta yok` yazisi gorunur. Uzun oyuncu adlari ve urun adlari tabela satirina sigacak sekilde kisaltilir.

## Pazar sahibinin islemleri

### Sandigi acma

Pazar sahibi kendi pazar sandigina **sag tiklarsa** sandik envanteri acilir. Sahip buradan urun stoklayabilir veya mevcut stoklari duzenleyebilir. Baska oyuncular pazar sandigini dogrudan acamaz.

### Yonetim panelini acma

Pazar sahibi otomatik tabelaya **sag tikladiginda** yonetim paneli bilgisi sadece kendi sohbetinde gorunur. Komutlarin secili pazar uzerinde calismasi icin once kendi pazar tabelasina sag tiklamak gerekir.

```text
/pazar kapat
/pazar al
/pazar sat
/pazar fiyat <sayi>
```

- `/pazar kapat`: Secili pazari kapatir ve kaydini siler.
- `/pazar al`: Pazari `ALIM` moduna getirir; pazar oyunculardan urun alir.
- `/pazar sat`: Pazari `SATIS` moduna getirir; pazar sandiktaki urunleri satar.
- `/pazar fiyat 7`: Birim fiyati 7 olarak gunceller.

Fiyat komutunda sadece pozitif tam sayilar kabul edilir.

## SATIS modu

`SATIS` modu, pazar sahibinin sandiktaki urunleri diger oyunculara sattigi moddur.

1. Musteri tabelaya **sag tiklarsa** guncel pazar bilgilerini sadece kendi sohbetinde gorur.
2. Musteri tabelaya **sol tiklarsa** `Bu urunden kac tane istersiniz?` sorusu gosterilir.
3. Musteri 5 saniye icinde pozitif bir tam sayi yazar. Ornek: `5`
4. Plugin sandikta yeterli stok olup olmadigini kontrol eder.
5. Toplam tutar `birim fiyat x miktar` olarak hesaplanir.
6. Tutar musterinin bakiyesinden cekilir ve pazar sahibinin hesabina aktarilir.
7. Urun sandiktan dusulur ve musterinin envanterine verilir.

Sandikta yeterli urun yoksa para transferi yapilmaz. Musterinin envanteri tamamen doluysa teslim edilemeyen urunler oyuncunun bulundugu yere dusurulur.

Ornek: Birim fiyat `7`, istenen miktar `5` ise musteriden `35` cekilir ve pazar sahibine `35` aktarilir.

## ALIM modu

`ALIM` modu, pazar sahibinin oyunculardan urun satin aldigi moddur. Bu modda pazar sahibinin Vault bakiyesinde yeterli para bulunmasi gerekir.

1. Oyuncu eline pazarin urununu alir.
2. Oyuncu pazar sandigina **sol tiklar**.
3. Plugin sadece oyuncunun gorebilecegi `Pazara kac tane urun vermek istiyorsun?` sorusunu gonderir.
4. Oyuncu 5 saniye icinde miktari yazar. Ornek: `10`
5. Plugin oyuncunun elinde ayni urunden yeterli miktar olup olmadigini kontrol eder.
6. Urunler sandiga aktarilir.
7. Birim fiyat ile miktar carpilir ve pazar sahibinin hesabindan oyuncuya odeme yapilir.

Pazar sahibinin bakiyesi yetersizse, sandikta yer yoksa veya oyuncunun elinde yeterli urun bulunmuyorsa islem tamamlanmaz.

## Guvenlik ve islem kontrolu

- Sohbet cevaplari oyuncu UUID'siyle takip edilir.
- Kurulum islemleri 10 saniye, alis ve satis cevaplari 5 saniye sonra otomatik silinir.
- Sohbet cevaplari diger oyunculara yayinlanmaz.
- Sandik ve tabela pazar kaydina bagliyken kirilamaz.
- Her sandik icin islem kilidi kullanilir; hizli tiklamalarda ayni stok iki kez kullanilamaz.
- Bos el ile pazar kurulamaz.
- Urun tipi ve urun meta bilgileri kontrol edilir.
- Bakiye yetersizse urun transferi yapilmaz.
- Basarisiz para transferlerinde ilgili islem geri alinmaya calisilir.
- Sandiga sigmayan kismi ekleme durumunda envanter eski haline geri yuklenir.

## Kayit ve kalicilik

Pazarlar `plugins/Pazarci/markets.yml` dosyasina kaydedilir. Kaydedilen bilgiler sandik ve tabela konumu, pazar sahibi UUID'si, urun, fiyat ve pazar modudur. Sunucu yeniden baslatildiginda pazarlar bu dosyadan tekrar yuklenir. Sandik veya tabela artik mevcut degilse ilgili kayit yukleme sirasinda atlanir.

## Derleme

Java 21 ve Maven kuruluysa Windows PowerShell uzerinden su komutla derlenebilir:

```powershell
Push-Location 'C:\Program Files\apache-maven-3.9.15\bin'
cmd.exe /c mvn.cmd -f C:\Users\aksoc\OneDrive\Desktop\PazarcıSistempl\pom.xml clean package -DskipTests
Pop-Location
```

Basarili derleme sonunda `target/pazarci-1.0.0.jar` olusur.

## Sorun giderme

### Plugin yuklenmiyor

- Sunucunun Paper `1.21.11` oldugunu kontrol et.
- Java surumunun 21 veya daha yeni oldugunu kontrol et.
- `plugins` klasorunde Vault jar dosyasinin bulundugunu kontrol et.
- Vault ile uyumlu ekonomi plugininin aktif oldugunu kontrol et.
- Sunucu konsolundaki hata mesajini incele.

### Para islemi yapilmiyor

Vault veya ekonomi saglayicisi aktif olmayabilir. Ayrica satin alan oyuncunun veya `ALIM` modundaki pazar sahibinin bakiyesi yetersiz olabilir.

### Tabela olusmuyor

Plugin tabelayi oyuncunun bulundugu yatay tarafta sandiga yapisik olusturur. Sandigin o tarafindaki blok doluysa tabela yerlestirilemez ve pazar kurulumu iptal edilir.

### Komutlar calismiyor

Once kendi pazar tabelana sag tikla. Komutlar sadece son secilen pazar sahibi tarafindan kullanilabilir.

## Proje dosyalari

- `src/main/java/tr/pazarci/PazarciPlugin.java`: Ana plugin ve pazar islemleri
- `src/main/resources/plugin.yml`: Plugin metadata ve komut tanimlari
- `src/main/resources/config.yml`: Oyuncuya gosterilen mesajlar
- `pom.xml`: Paper API, VaultAPI ve Maven ayarlari
- `target/pazarci-1.0.0.jar`: Derlenen plugin dosyasi

## 💡 Katkıda Bulunma ve Geliştirme

Bu proje tamamen açık kaynaklıdır! Eksik gördüğün, geliştirilebileceğini düşündüğün veya eklenmesini istediğin yeni özellikler varsa katkıda bulunabilirsin:

1. Bu depoyu çatallayabilirsin (**Fork**).
2. Kendi geliştirme dalını oluşturabilirsin (`git checkout -b ozellik/yeni-ozellik`).
3. Değişikliklerini kaydedebilirsin (`git commit -m 'Yeni özellik eklendi'`).
4. Depona gönderebilirsin (`git push origin ozellik/yeni-ozellik`).
5. Bir **Pull Request (İstek)** oluşturarak katkı sunabilirsin.

## 📞 İletişim ve Destek

Eklentiyle ilgili bir hata (bug) fark edersen, soru sormak istersen veya önerilerin varsa şu kanallardan ulaşabilirsin:

* **GitHub Issues:** Depo üzerindeki [Issues](https://github.com/Keepace-Development/Pazarci-sistemi/issues) sekmesini kullanarak hata bildiriminde bulunabilirsin.
* **Discord / Sosyal Medya:** Sunucu geliştirme süreçleri ve iletişim için bana GitHub profilim üzerinden ulaşabilirsin.
