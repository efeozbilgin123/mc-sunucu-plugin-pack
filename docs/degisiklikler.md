# Bu turda değişecekler (şu anki sürüme göre)

Şu an: Çay 1.2.3, Polis 1.9.1
Sonra: Çay 1.3.0, Polis 1.10.0, Bekçi 1.0.0 (yeni), Yedek sistemi (yeni)
Kurulum: hepsi tek `kurulum.sh` ile

---

## Polis 1.10.0

### Değişen
- **Yaralı sistemi:** Sadece bir oyuncu öldürürse çalışır. Yaratık, düşme, lav gibi ölümler normal ölüm olur.
- **Yaralıyken çıkma:** Yaralıyken çıkan ölür. Kelepçeliyse ölmez, girince yine yaralı ve kelepçeli olur.
- **Üst arama:** Polis artık eşya alabiliyor (zırh, sol el dahil). Mahkuma eşya koyamaz. Alınan her şey kayda geçer.
- **Ender sandığı:** Aramada bir "Ender sandığı" düğmesi var. Polis mahkumun ender sandığından da eşya alabilir (sadece kelepçeli ve yakındaki mahkum).
- **Kelepçeliyken çıkma:**
  - Polislere haber gider, kayda ve sicile "kaçma girişimi" yazılır.
  - Girince sırayla şuraya konur: takan polisin yanı, yoksa son yerleştirildiği yer, o da yoksa çıktığı yer.
- **Kafa üstü etiket:** [★ Polis], [♛ Başkan] ve [✚ Doktor] her zaman görünür. [Mimar] gibi takımla yan yana yazar.
- **Karakol panosu:** Bedrock'ta da görünsün diye zırh standı yazılarıyla yapılır.

### Yeni
- **`/vergi`:**
  - Kullanımı `/ceza` gibi, sadece başkan ve op yazar.
  - Sicile işlemez, [ARANIYOR] koymaz, makbuz vermez. Bossbar'da "Ödenmemiş vergi" yazar.
- **Savaştan çıkma engeli:**
  - Vuran ve vurulan 15 sn "savaşta" sayılır.
  - Bilerek çıkan ölür, eşyaları mezara gider.
  - Bağlantısı kopan ve restart'ta atılan ölmez.
- **İdam:**
  - Başkan karar verir: `/idam Ahmet [süre]` (varsayılan 30 dk, en fazla 1 gün).
  - Polis elle uygular: elinde sıradan kayışla Shift + çite sağ tık. Çitin altında en az 3 blok boşluk olmalı.
  - Geri sayım, kaldırılma ve sallanma animasyonu var. Kayış çitten boyna bağlı görünür.
  - Eşyalar mezara gider. İdam edilen o süre boyunca sunucuya giremez.
  - Ek komutlar: `/idam iptal`, `/idam affet`, `/idam liste`.
- **Sohbette rol:** "[★ Polis] Efe: mesaj"
- **Kelepçe sesleri:** Takılınca zincir sesi ve kıvılcım, açılınca kilit sesi.
- **Yaralıyken:** Kalp atışı sesi ve ekranın kararması, süre azaldıkça artar.
- **İdam sahnesi:** Çan, davul ve duman. Bütün sunucuya duyuru gider.
- **`/sicil`:** Java'da kitap olarak açılır, Bedrock'ta sohbette görünür.

### Kaldırılan
- `/mesai` ve üniforma. Polis kendi zırhıyla görünür. Eski üniforma eşyaları girişte silinir, saklı zırh geri verilir.

---

## Çay 1.3.0

### Yeni
- **Semaver yazısı:** Semaverin üstünde "Demleniyor... %60", bitince "Çay hazır!" yazar. Demlenirken buhar ve kaynama sesi.

---

## Bekçi 1.0.0 (yeni eklenti)

- **Kayıt:**
  - Blok kırma ve koyma, lav/su boşaltma, ateş ve TNT yakma.
  - Sandık, varil, shulker ve fırın gibi kaplardan kim ne aldı, ne koydu.
  - Ender sandığına konanlar ve alınanlar.
  - Kayıtlar 30 gün tutulur.
- **Bakma (op):** `/bekci bak` (bloğa tıkla, geçmişini gör), `/bekci oyuncu Ahmet 1g`, `/bekci yakin 10 2sa`.
- **Geri alma (op):**
  - `/bekci geri Ahmet 2sa`, önce önizleme gösterir, sonra `/bekci onayla` gerekir.
  - Sadece bloklar geri gelir, başkasının sonradan yaptığı değişikliklere dokunmaz.
- **Sandık bulucu (op):**
  - `/bekci sandik yakin [blok]`: koordinatlar, eşya sayısı, koyan kişi. Tıklayınca ışınlanırsın (Bedrock'ta `/bekci git <no>`).
  - Java'da bulunan sandıklar duvar arkasından parlayan bir çerçeveyle görünür.
  - `/bekci sandik oyuncu Ahmet`: Ahmet'in koyduğu bütün sandıklar.
- **Ender sandığına bakma (op):** `/bekci ender Ahmet`, sadece bakılır, bir şey alınamaz.
- **Mezar:**
  - Ölünce eşyalar ve XP, ölenin kafası şeklindeki bir mezara girer.
  - Üstünde iki satır yazı ve ruh parçacıkları var.
  - Kimler açabilir: sahibi, PvP'de öldüren, polis ve başkan. 30 dk sonra herkes.
  - Açabilen kırabilir, kırılınca eşyalar saçılır.
  - Patlama mezara işlemez.
  - Mezar 24 saat sonra silinir. 1 saat kala sahibine uyarı gider, içeriği kayda yazılır.
  - `/mezar` kendi mezarlarını listeler. Op için `/mezar liste` ve `/mezar sil`.

---

## Yedek sistemi (yeni)

- **Ne zaman:** Her saat başı, sessizce. Sunucu kapanmaz.
- **Neler:** Dünyalar, eklentiler ve verileri, sunucu ayarları.
- **Kaç tane:** Son 24 saatin yedeklerinin hepsi, sonra 14 gün boyunca her günden bir yedek. Toplam yaklaşık 2-3 GB.
- **Disk dolarsa:** En eskiler silinir, yine yetmezse o yedek atlanır.
- **Geri yükleme:** `bash ~/yedek/yedek-geri.sh` ile listeden seçersin. Mevcut hal önce kenara kopyalanır.

---

## Değişmeyenler
Çay yapımı, kelepçe/anahtar/sürükleme, hücreye koyma, yemek yedirme, ceza ve makbuz, polis kaydı, bossbar, ekran yazıları, siren, doktor rolü, ikinci kelepçe engeli, kelepçelinin çevre hasarından korunması.
