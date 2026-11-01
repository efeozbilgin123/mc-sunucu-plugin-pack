# Tasarım: Polis 1.10 + Bekçi 1.0 + Yedek sistemi

Tarih: 1 Kasım 2026
Sunucu: Paper 26.3, Oracle A1 (45 GB disk, 34 GB boş), 6-7 kişi, Java + Bedrock (Geyser)

Bu belge sohbette onaylanan kararları toplar. "(varsayım)" yazan maddeleri ben önerdim, sen itiraz etmedin. Yanlışsa bu belgede düzeltelim.

---

## 0. Amaç

- Polis eklentisini sadeleştirmek. Üniformayı kaldırmak. Yaralı sistemini sadece PvP için çalıştırmak. Vergiyi cezadan ayırmak. Arama, kaçış ve idam gibi eksik rolplay parçalarını tamamlamak.
- Hırsızlık ve griefing için kayıt, inceleme ve geri alma (CoreProtect 26.3'te çalışmıyor).
- Saklanan sandıkları bulmak.
- Ölünce eşyaların dağılmaması (mezar).
- Dünyanın her saat yedeklenmesi.

Başarı ölçütü: Her şey (3 eklenti + yedek sistemi) tek bir `kurulum.sh` ile kurulur. Java'da tam, Bedrock'ta mümkün olduğunca çalışır. Bedrock'ta çalışmayan her şeyin komut karşılığı vardır.

Görsel iyileştirmeler de bu turda (bölüm 4). Kapsam dışı (istenmedi): sağdaki bilgi tablosu, çay içince parçacık, kaldırılınca kalp parçacığı, inceleme modunda blok çerçevesi.

---

## 1. Polis eklentisi 1.10.0

### 1.1 Yaralı sistemi sadece PvP'de
- Yere düşme sadece ölümcül vuruş bir oyuncudan gelirse olur: direkt vuruş ya da oyuncunun attığı ok, mızrak, tavşan vb. Yaratık, düşme, lav, açlık ve patlamada normal ölüm.
- Yaralıya oyuncu ya da yaratık vurursa ölür (değişmedi).
- Yaralıyken çıkan ölür, eşyaları mezara gider.
- İstisna: kelepçeliyse ölmez. Yaralılık durumu dosyaya kaydedilir. Girince yine yaralı ve kelepçeli olur, süre durmuş olarak kalır.
- Polis kaydına artık yaratık yaralanmaları düşmez (zaten oluşmayacak).

### 1.2 Savaştan çıkma engeli (combat log)
- Oyuncu başka bir oyuncuya hasar verince (ok dahil, iptal edilmemiş hasar) ikisi de 15 sn (ayar: `savas.sure`) "savaşta" sayılır. Her vuruşta süre yenilenir.
- Savaştayken ekranın altında "Savaştasın: 12 sn, çıkarsan ölürsün" yazar. Süre bitince "Savaştan çıktın".
- Çıkış nedenine göre:
  - Bilerek çıkış (Paper `QuitReason.DISCONNECTED`): ölür, eşyalar mezara gider. Polis kaydına "savaşta çıktı", vuran kişiye ve 48 blok içindekilere haber gider.
  - Bağlantı koptu (`TIMED_OUT`), atıldı (`KICKED`), hatalı durum: ölmez.
  - Sunucu kapanırken: ölmez.
- Kelepçeli biri çıkarsa savaş kuralı uygulanmaz, 1.6 uygulanır.
- Yaralıyken çıkma zaten 1.1'deki kurala tabidir.

### 1.3 `/vergi`
- Kullanım: `/vergi <oyuncu|@a> <miktar> <eşya> [süre] [sebep]`. `/ceza` ile aynı.
- Yazabilen: başkan ve op (polis yazamaz).
- Ödeme: `/ode` (ceza ve vergi aynı listede, "#12 vergi" diye etiketli).
- Fark:
  - Sicile işlemez.
  - [ARANIYOR] etiketi koymaz.
  - Makbuz kitabı vermez.
  - Bossbar: "Ödenmemiş vergi: 2g kaldı" (sarı). Gecikince "GECİKMİŞ VERGİ" (kırmızı), "aranıyorsun" yazmaz.
  - `/ceza borclular` ve karakol panosunda "vergi" etiketiyle görünür.
- Kayıt: `cezalar.yml` içindeki her kayda `tur: ceza|vergi` alanı eklenir. Eski kayıtlar `ceza` sayılır.
- Bossbar önceliği: ceza borcu > vergi borcu.

### 1.4 Üniforma ve `/mesai` kaldırılır
- `Mesai.java`, `/mesai` komutu ve üniforma eşyaları silinir.
- Geçiş güvenliği: Oyuncu girince üstünde eski üniforma eşyası (PDC `poliskelepce:uniforma`) varsa silinir. `mesai.yml`'de saklı gerçek zırhı varsa geri verilir. Sonra dosya silinir.
- Kafa üstü rol etiketi (scoreboard takımı):
  - Polis: aqua [Polis], başkan: altın [Başkan], doktor: pembe [Doktor]. Rol etiketi her zaman görünür.
  - (varsayım) Oyuncunun vanilla takımı da varsa (ör. [Mimar]) birleşik takım kurulur: önek "[Polis] [Mimar] ", renk eski takımın rengi. Takım adı `pk_<rol>_<eski takımın kısa özeti>`.
  - Öncelik: [GÖZALTI] > [YARALI] > [ARANIYOR] > rol etiketi. Durum etiketi varken rol etiketi görünmez.
  - Rol kalkınca ya da eklenti kapanınca eski takım geri verilir (mevcut mekanizma).
- Rol izni: Polis ve başkana `poliskelepce.rol.polis` izni eklenir (PermissionAttachment, girişte ve atamada). Bekçi'nin mezar kuralı bunu kullanır.

### 1.5 Üst aramada eşya alma
- Arama ekranı (54 yuva) artık sadece alma ekranı.
- Üst kısımda bir eşyaya tıklanınca (sol, sağ, Shift, sayı tuşu fark etmez):
  1. Mahkum hâlâ kelepçeli ve polisin 6 blok yakınında mı? Değilse ekran kapanır.
  2. Gösterilen eşya mahkumun gerçek yuvasında hâlâ aynı mı (tür, meta, miktar)? Değilse ekran yenilenir, hiçbir şey taşınmaz (kopyalama önlemi).
  3. Eşya mahkumdan silinir, polise verilir (sığmazsa ayağına düşer).
  4. Polis kaydına "Efe, Ahmet'in üstünden 5 elmas aldı" yazılır, mahkuma mesaj gider.
- Ekrana eşya koymak, sürüklemek ve alt envanterden Shift ile yollamak engellenir.
- Zırh yuvaları ve sol el de alınabilir.
- Ender sandığı araması:
  - Arama ekranının alt satırında "Ender sandığı" düğmesi (ender sandığı simgesi) olur.
  - Tıklanınca mahkumun ender sandığı aynı kurallarla (sadece alma, kopyalama önlemi, kayıt) açılır.
  - (varsayım) Sadece kelepçeli ve 6 blok yakındaki mahkumunki aranabilir, uzaktan ya da kelepçesiz kimseninki aranamaz.

### 1.6 Kelepçeliyken oyundan çıkma
- Çıkınca:
  - Oyundaki polislere "Ahmet gözaltındayken çıktı" mesajı gider.
  - Polis kaydına yazılır.
  - Sicilde yeni sayaç: "kaçma girişimi".
- Kalıcı kayıt (`veri.yml`): kelepçe kimliği (var), takan polis (var), son hücre konumu (yeni, `koy`/yerleştir ile ayarlanır).
- Girince (sırayla):
  1. Takan polis oyundaysa: polisin yanına ışınlanır, götürülme modunda başlar.
  2. Polis yoksa ve son hücre konumu varsa: oraya ışınlanır, bekletilir.
     - "Son hücre konumu": Polisin bu kelepçe takılıyken mahkumu en son "yerleştirdiği" nokta. Anahtarla Shift + bloğa sağ tık ya da `/koy` ile konduğu yer.
     - Gerçek bir hücre olması gerekmez, polis nereye koyduysa orası.
     - Sonradan tekrar götürülse de bu nokta hatırlanır. Ancak yeni bir yerleştirme ile değişir.
     - Kelepçe açılınca silinir.
  3. İkisi de yoksa: çıktığı yerde bekletilir.
  - Her durumda polislere "Ahmet geri geldi: x y z" mesajı gider.

### 1.7 İdam
- Karar (sadece komut adımı):
  - `/idam <oyuncu> [süre]`: başkan ya da op.
  - Süre = sunucuya girememe süresi. Varsayılan 30 dk, en fazla 1 gün (ayar: `idam.varsayilan-sure`, `idam.en-fazla-sure`).
  - Onay 10 dk geçerlidir. Mahkuma ve polislere haber gider.
  - `/idam iptal <oyuncu>`: bekleyen onayı kaldırır (başkan/op).
  - `/idam affet <oyuncu>`: sunucu yasağını erken kaldırır (op).
  - `/idam liste`: bekleyen onaylar ve süren yasaklar.
- Uygulama (komutsuz, elle):
  - Polis elinde sıradan bir kayış (PDC'siz; kelepçe değil) tutarken Shift + herhangi bir çite (`*_FENCE`, çit kapısı değil) sağ tıklar.
  - Hedef: polisin 4 blok içindeki, onaylı ve kelepçeli en yakın mahkum. Yoksa nedeni yazılır, hiçbir şey olmaz.
  - Yerleştirme: Çitin altında en az 3 blok boşluk olmalı (altında zemin şart değil). Yoksa "Darağacı için çitin altında en az 3 blok boşluk lazım" yazılır, hiçbir şey olmaz.
    - Mahkum önce ayakları yerde/en altta, çitin altına konur ve hareket edemez.
  - İp: Mahkumun boyun hizasına görünmez, sessiz, yapay zekası kapalı, hasar almayan küçük bir yaratık konur. Çite kayış düğümü (`LeashHitch`) bağlanır. Yaratık düğüme kayışla bağlanır. Sonuç: çitten boyna giden gerçek ip görüntüsü. Bedrock'ta da kayış çizilir. Kayış polisten harcanır.
  - Animasyon:
    1. 5 sn geri sayım: 32 blok içindekilere ekran yazısı "İDAM: Ahmet" ve geri sayım. Mahkum çitin altında ayakta.
    2. 1 sn: Mahkum yumuşakça 1 blok yukarı kaldırılır (her tick küçük adımla). Kayış gerilir, ayakları yerden kesilir.
    3. 2 sn: Hafifçe sallanır (gövde sağa sola döner), ses efekti.
    - Kan ya da vahşet efekti yok.
  - Sonunda:
    - Kelepçe çözülür, mahkum ölür (`setHealth(0)`; yaralı sistemi devreye girmez). Ölüm mesajı "Ahmet idam edildi".
    - Eşyalar oradaki mezara gider (Bekçi).
    - Sicile "idam", polis kaydına "Efe, Ahmet'i idam etti (onay: Başkan)" yazılır.
    - Görünmez yaratık ve düğüm silinir.
    - 2 sn sonra oyuncu "İdam edildin. 30 dk sonra tekrar girebilirsin." mesajıyla atılır.
- Yasak:
  - `idam.yml`: uuid → bitiş zamanı.
  - Giriş denemesi (`AsyncPlayerPreLoginEvent`) süre dolmadıysa "Kalan: 18 dk" mesajıyla reddedilir.
  - Atılma, 1.2 ve 1.6 kurallarını tetiklemez.
- Geri sayım sırasında:
  - Mahkum çıkarsa: idam yine sayılır (öldürülür, yasak başlar).
  - Polis çıkarsa ya da 8 bloktan fazla uzaklaşırsa: iptal edilir, onay geçerli kalır.

### 1.8 Değişmeyenler
Kelepçe, anahtar, sürükleme, hücreye koyma, yemek yedirme, ceza ve makbuz, sicil, polis kaydı, bossbar, ekran yazıları, siren, karakol panosu, doktor rolü, ikinci kelepçe engeli, kelepçeli çevre hasarı koruması.

- Karakol panosu Bedrock'ta görünsün diye TextDisplay yerine üst üste görünmez zırh standı isimleriyle yapılır. Satır başına bir stand, en fazla 14 satır.

---

## 2. Bekçi 1.0.0 (yeni eklenti, `tr.efe.bekci`)

Ayrı eklenti, polis eklentisine bağımlı değil. Polis bilgisi sadece `poliskelepce.rol.polis` izninden okunur.

### 2.1 Veri
- SQLite, `plugins/Bekci/kayit.db`.
- Sürücü: `plugin.yml` içinde `libraries: [org.xerial:sqlite-jdbc]` (en güncel kararlı sürüm, yazarken GitHub sürümlerinden doğrulanıp sabitlenir). Paper ilk açılışta Maven Central'dan indirir.
- Yazma: tek iş parçacıklı kuyruk, toplu ekleme (sunucuyu dondurmaz). Okuma da ayrı iş parçacığında. Sonuç ana iş parçacığında yazdırılır.
- Tablo `islem`: zaman, oyuncu uuid ve isim, dünya, x, y, z, tür, eski blok (BlockData metni), yeni blok, eşya, miktar, geri_alindi.
- Türler: `KIR`, `KOY`, `KOVA` (lav/su boşaltma), `ATES` (çakmak), `TNT` (tutuşturma), `AL` (kaptan), `KOY_KAP` (kaba), `KIR_ICERIK` (kırılan kabın içindekiler), `MEZAR_AL`, `MEZAR_KIR`, `MEZAR_SILINDI`, `GERI` (geri alma işlemi).
- 30 günden eski kayıtlar açılışta ve her 6 saatte silinir (ayar: `saklama-gun`).

### 2.2 Kayıt
- Oyuncu blok kırma/koyma, kova boşaltma, çakmak ve ateş topu ile ateş yakma, TNT tutuşturma.
- Kaplar: Sandık, çift sandık, varil, shulker, fırın türleri, huni, dağıtıcı, bırakıcı, simya standı, bakır sandık.
  - Oyuncu açınca içerik kopyalanır. Kapatınca karşılaştırılır. Fark "aldı/koydu" olarak yazılır, aynı eşyalar birleştirilir.
- Ender sandığı da kaydedilir: oyuncunun kendi ender sandığına ne koyup ne aldığı (konum: açtığı ender sandığı bloğu) ve polisin aramada ender sandığından aldıkları (1.5).
- Kaydedilmeyenler: huni aktarımı, yaratık/patlama kırması, kurulum öncesi.

### 2.3 Bakma (sadece op)
- `/bekci bak`: inceleme modu aç/kapa.
  - Sol tık: bloğun son 10 işlemi (blok kırılmaz).
  - Sağ tık: tıklanan yüzün önündeki konum (kırılmış blokların yeri).
- `/bekci oyuncu <isim> <süre> [sayfa]` ve `/bekci yakin <yarıçap> <süre> [sayfa]`. Süre biçimi: 30dk, 2sa, 1g. Sayfa başına 10 satır, en yeni üstte.

### 2.4 Geri alma (sadece op)
- `/bekci geri <oyuncu> <süre> [yarıçap]`: önce önizleme, "Ahmet'in 340 blok işlemi geri alınacak (12 atlanacak)". 60 sn içinde `/bekci onayla` yazılmazsa hiçbir şey olmaz.
- Uygulama (en yeniden eskiye):
  - `KIR`: blok şu an hâlâ boşsa (ya da Ahmet'in bıraktığı haldeyse) eski blok konur. Başkası sonradan değiştirdiyse atlanır.
  - `KOY`: blok şu an hâlâ Ahmet'in koyduğu bloksa havaya çevrilir.
  - `KOVA`: o konumda hâlâ sıvı kaynağı varsa silinir. `ATES`: hâlâ ateş varsa söndürülür.
  - Kap eşyaları geri konmaz. Kırılan sandık boş geri gelir, içindekiler kayıtta görünür.
- Tick başına en fazla 500 blok, sunucu takılmaz. Bitince "331 geri alındı, 9 atlandı" yazar.
- Geri alınan kayıtlar işaretlenir (iki kez geri alınmaz). İşlem `GERI` olarak kaydedilir.

### 2.5 Sandık bulucu (sadece op)
- `/bekci sandik yakin [yarıçap]` (64 varsayılan, 160 en fazla): yüklü chunk'lardaki bütün kaplar (ender sandığı hariç), en yakından.
  - Satır: numara, koordinat, uzaklık, içindeki eşya sayısı, koyan (biliniyorsa).
  - Java'da satıra tıklayınca ışınlar. Herkes için `/bekci git <numara>`.
  - Bulunanların üstünde 30 sn boyunca sadece op'un gördüğü parçacık sütunu.
- `/bekci sandik oyuncu <isim>`: o oyuncunun koyduğu ve hâlâ duran bütün kaplar, tüm dünyalarda. Yüklü olmayan chunk'lar "kontrol edilmedi" diye işaretlenir.
- `/bekci ender <oyuncu>`: oyundaki bir oyuncunun ender sandığını sadece bakmak için açar (op, hiçbir şey alınamaz).

### 2.6 Mezar
- Ölünce (keepInventory kapalıysa, düşen eşya ya da XP varsa) eşyalar ve XP mezara alınır, yere hiçbir şey düşmez.
- Konum:
  - Ölüm yeri uygunsa (hava, altı sağlam) oraya.
  - Değilse 5 blok içinde en yakın uygun yer.
  - Boşluk ya da bulunamazsa: oyuncunun son bastığı sağlam zemin (her saniye güncellenir).
- Görünüm: Ölenin kafası (oyuncu kafası bloğu, sahibinin skini). Üstünde görünmez zırh standı adı "Ahmet'in mezarı" (Bedrock'ta da görünür).
- Kaydı `mezarlar.yml`: kimlik, sahip, öldüren (PvP ise), zaman, konum, eşyalar, XP.
- Açma: Kafaya sağ tık. Kimler açabilir:
  - Sahibi, onu PvP'de öldüren ve polis/başkan (`poliskelepce.rol.polis`) her zaman.
  - 30 dk sonra herkes (ayar: `mezar.herkese-acik-dk`).
- Menü: 54 yuva, sadece alma. Kopyalama önlemi 1.5'tekiyle aynı. XP sahibi açınca sahibine verilir.
- Boşalınca kafa ve yazı silinir.
- Kırma: Açabilen kırabilir, diğerleri için iptal. Kırılınca eşyalar ve XP yere saçılır.
- Korunma: Patlama, piston ve su mezara işlemez.
- Süre: Mezar 24 saat sonra kendiliğinden silinir, içindeki eşyalar ve XP de gider (ayar: `mezar.silinme-saat`).
  - Silinmeden 1 saat önce sahibi oyundaysa uyarı alır: "Mezarın 1 saat sonra silinecek: x y z". `/mezar` listesinde kalan süre görünür.
  - Silinen mezarın içeriği Bekçi kaydına `MEZAR_SILINDI` olarak yazılır, ne kaybolduğu görülebilir.
  - Mezarın olduğu yer yüklü değilse kayıt silinir, kafa ve yazı o bölge ilk yüklendiğinde kaldırılır.
- Kayıt: Her alma `MEZAR_AL`, her kırma `MEZAR_KIR` olarak Bekçi kaydına.
- Komutlar:
  - `/mezar`: kendi açık mezarlarının koordinatları.
  - `/mezar liste` (op): hepsi.
  - `/mezar sil <no>` (op): eşyalar silinir, uyarı verilir.
- Ölünce sohbete "Mezarın: x y z" yazılır.
- İdam, savaşta çıkma ve yaralıyken ölme de mezar bırakır (ölüm olayı ortak).

---

## 3. Yedek sistemi (betik, eklenti değil)

- Kurulum: `kurulum.sh` içinde (ayrı betik yok).
  - `~/yedek/` altına `yedek.sh` ve `yedek-geri.sh` koyar.
  - Systemd zamanlayıcısı kurar: her saat başı (xx:00).
  - Hemen bir deneme yedeği alır.
- `yedek.sh`:
  1. Sunucu çalışıyorsa tmux konsoluna `save-off`, `save-all flush` yollar. Kaydın bitmesini log'dan bekler (en fazla 60 sn). Her saat olduğu için sohbete mesaj yazmaz, sadece konsola yazar.
  2. Yedeklenenler: `world*`, `plugins/` (jar'lar dahil), `config/`, `server.properties`, `*.json` (whitelist, ops, ban), `bukkit.yml`, `spigot.yml`, `start.sh`. Yeniden indirilebilenler (`libraries`, `versions`, `cache`, `logs`) hariç.
  3. Dosya: `~/yedek/arsiv/mc-YYYY-AA-GG_SS-DD.tar.gz`.
  4. Disk kontrolü: Gerekli alan = son yedeğin 1,5 katı (en az 1 GB). Yer yoksa en eski yedekler silinir. Yine yetmezse yedek atlanır, konsola ve sohbete "Yedek alınamadı: disk dolu" yazılır.
  5. `save-on`.
  6. Saklama (ayarlar betiğin başında):
     - Son 24 saatin saatlik yedeklerinin hepsi.
     - Daha eskiler için her günün sadece son yedeği, 14 gün geriye kadar.
     - Ötekiler silinir.
  - Bekçi'nin veritabanı açıkken kopyalanır. En kötü durumda son birkaç saniyenin kaydı eksik olur, kabul edilebilir.
- `yedek-geri.sh`:
  1. Yedekleri tarih ve boyutla numaralı listeler, numara ister, "evet" yazdırarak onaylatır.
  2. Sunucuyu durdurur.
  3. Yedekte olan yolların şu anki halini `~/yedek/geri-yukleme-oncesi-<tarih>/` altına taşır.
  4. Yedeği açar, sunucuyu başlatır.
  - Ne yaptığını ve önceki halin nerede durduğunu yazar.
- Bugünkü boyut: Dünya 83 MB. Toplam en fazla 24 + 14 = 38 yedek, muhtemelen 2-3 GB. 34 GB boş alanın yanında sorun değil.
- Saatlik `save-all flush` küçük bir dünyada 1 saniyeden kısa sürer, oyuncular fark etmez.

---

## 4. Görsel iyileştirmeler

Resource pack yok. ✓ Bedrock'ta çalışır, ~ muhtemelen çalışır (oyunda test), ✗ sadece Java.

| # | Ne | Eklenti | Bedrock |
|---|----|---------|---------|
| 1 | Sohbette rol öneki: "[Polis] Efe: mesaj", kafa etiketiyle aynı renk. Paper sohbet görüntüleyicisi ile, mesaj içeriğine dokunmadan. | Polis | ✓ |
| 3 | Etiketlerde simge: [★ Polis], [♛ Başkan], [✚ Doktor]. Hem kafa üstü hem sohbet hem Tab'da. Bedrock'ta kutu görünürse `gorsel.simgeler: false` ile kapatılır. | Polis | ~ |
| 4 | Semaver: Kazanın üstünde görünmez zırh standı yazısı "Demleniyor... %60", bitince "Çay hazır!". Demlenirken buhar parçacığı ve ara ara kaynama sesi. Semaver bitince ya da bozulunca yazı silinir. | Çay 1.3.0 | ✓ |
| 6 | Kelepçe: Takılınca zincir sesi + demir kıvılcımı parçacığı, açılınca kilit sesi. | Polis | ✓ |
| 7 | Yaralı: Sadece yaralının duyduğu kalp atışı sesi, süre azaldıkça sıklaşır. Ekran kararması (Karanlık etkisi), son 15 saniyede artar. | Polis | ✓ / ~ (karanlık) |
| 9 | İdam sahnesi: Başlarken üç kez çan, geri sayımın her saniyesinde davul, sonunda duman. Bütün sunucuya gri duyuru "Ahmet idam edildi". | Polis | ✓ |
| 10 | `/sicil` Java oyuncularına sayfalı kitap olarak açılır (envantere girmez). Bedrock oyuncularına (Floodgate UUID'si) kendiliğinden eski sohbet görünümüyle gösterilir. | Polis | ✓ (Bedrock'ta sohbet) |
| 11 | Mezar: İki satır yazı "☠ Ahmet'in mezarı" ve "Herkese açılmasına 12 dk / Silinmesine 23 sa". Etrafında ara ara ruh parçacıkları. | Bekçi | ✓ (☠ ~) |
| 12 | Sandık bulucu x-ray: Bulunan her kabın içine sadece op'a görünen, parlayan görünmez bir yaratık konur. Duvarın arkasından çerçeve görünür, 30 sn sonra silinir. | Bekçi | ✗ (Bedrock'ta sadece parçacık sütunu) |

- Bütün sesler ve parçacıklar `gorsel` ayarlarından tek tek kapatılabilir.
- Seslerin ve parçacıkların Paper adları yazarken Paper kaynağından doğrulanır.

---

## 5. Kurulum

- `kurulum.sh`: CayOcagi 1.3.0 + PolisKelepce 1.10.0 + Bekci 1.0.0 derlenip kurulur (mevcut sistem, listeye Bekçi eklenir). Bitiş mesajına yeni komutlar eklenir.
- Aynı `kurulum.sh` yedek sistemini de kurar (bölüm 3). Tekrar çalıştırmak güvenlidir: eklentiler güncellenir, zamanlayıcı ikinci kez eklenmez, mevcut yedekler silinmez.
- Ayar dosyaları `copyDefaults` ile yeni anahtarları kendiliğinden alır.

---

## 6. Doğrulama

- Burada: Her eklenti el yazımı API taklitlerine karşı derlenir. Yeni kullanılan her Paper API'si Paper'ın güncel kaynağından imza olarak kontrol edilir. Kurulum dosyasının içeriği kaynakla birebir karşılaştırılır. Betikler `bash -n` ile kontrol edilir.
- Paper kaynağından doğrulanacaklar: `PlayerQuitEvent.getReason()`, `plugin.yml libraries`, `LeashHitch` ve `setLeashHolder`, `Skull` sahip ayarı, `AsyncPlayerPreLoginEvent` reddetme, `InventoryOpenEvent`/`InventoryCloseEvent` ile kap konumu.
- Oyunda (bir arkadaşla 10 dk, Java + Bedrock):
  1. PvP'de yere düşme ve kaldırma. Yaratıktan normal ölüm.
  2. Savaşta çıkış: ölüm ve mezar. Wi-Fi kapatınca ölmeme.
  3. `/vergi @a` sonrası sicilin değişmediği.
  4. Rol etiketi ve [Mimar] birleşimi.
  5. Aramada eşya alma.
  6. Kelepçeliyken çıkıp girme.
  7. İdam, ip görüntüsü ve yasaklı girişin reddi.
  8. `/bekci bak`, geri alma önizlemesi ve uygulama.
  9. Sandık bulucu.
  10. Mezar açma ve kırma yetkileri.
  11. Görseller: Sohbet öneki, simgeler (Bedrock'ta kutu var mı), semaver yazısı, kalp atışı, idam sahnesi, sicil kitabı, mezar yazısı, x-ray.
  12. Elle `bash ~/yedek/yedek.sh`, saat başı otomatik yedeğin oluştuğu ve `yedek-geri.sh` ile geri yükleme.

---

## 7. Riskler

- **Bedrock görünümü:** Yaralının yatar görünmesi ve kelepçeli ellerin görünümü Bedrock'ta farklı olabilir. Burada test edilemez. Kurulumdan sonraki ilk oyun testinde bakılıp gerekirse bir sonraki sürümde düzeltilecek.
- **İp görüntüsü:** Görünmez yaratığa bağlı kayışın Bedrock'ta çizilmesi beklenir ama test edilmeli. Çizilmezse yedek plan: parçacıkla ip.
- **Bekçi veritabanı:** İlk açılışta internetten sürücü indirmesi gerekir. İnternet yoksa Bekçi açılmaz, diğer eklentiler etkilenmez.
- **Geri alma:** Blok verisini (yön, durum) geri koyar. Tabela yazısı ve kap içeriği geri gelmez.
- **Takım birleşimi:** Başka bir eklenti scoreboard takımlarını yönetirse çakışabilir (şu an yok).
