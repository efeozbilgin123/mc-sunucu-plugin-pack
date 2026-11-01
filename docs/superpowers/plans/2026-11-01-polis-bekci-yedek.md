# Polis 1.10 + Bekçi 1.0 + Çay 1.3 + Yedek — Yapım Planı

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Onaylanan tasarımı üç Paper eklentisi (Çay, Polis, yeni Bekçi) ve saatlik yedek betikleri olarak yazmak. Hepsi tek `kurulum.sh` ile kurulur.

**Architecture:** Bukkit'e bağlı olmayan karar mantığı küçük, saf `static` sınıflarda tutulur ve burada düz Java testleriyle sınanır. Olay dinleyicileri ve komutlar bu sınıfları çağırır. Bekçi kayıtları SQLite'ta tutar, yazma/okuma tek iş parçacıklı bir kuyrukta yapılır. Yedek, systemd zamanlayıcısıyla saat başı çalışan bir bash betiğidir. Saklama kararı ayrı bir bash fonksiyonudur ve burada sahte dosyalarla test edilir.

**Tech Stack:** Paper API 26.3 (Java 25), Adventure, Maven (sunucuda derlenir), SQLite (`org.xerial:sqlite-jdbc`, `plugin.yml libraries:` ile), bash + systemd + tmux, tar/gzip.

**Spec:** `docs/superpowers/specs/2026-11-01-polis-bekci-yedek-design.md` (özet: `docs/degisiklikler.md`)

## Global Constraints

- Paper 26.3, `paper-api [26.3.build,26.4)`, `maven.compiler.release 25`. Maven eklentileri sabit: compiler 3.14.0, resources 3.3.1, jar 3.4.2, surefire 3.5.2 (skipTests).
- Resource pack yok. Özel eşyalar = vanilla malzeme + PDC (`cayocagi`, `poliskelepce`, `bekci` ad alanları).
- Bütün oyuncu mesajları Türkçe. Bütün `toLowerCase` çağrıları `Locale.ROOT` kullanır.
- Bedrock (Geyser/Floodgate): Bedrock'ta çalışmayan her etkileşimin komut karşılığı var. Bedrock oyuncusu = `uuid.getMostSignificantBits() == 0`.
- Sürümler: Çay `1.3.0`, Polis `1.10.0`, Bekçi `1.0.0`.
- Yeni ayar anahtarları `config.yml`'e açıklamalı yazılır, `copyDefaults(true)` ile eski kurulumlara eklenir.
- Kalıcı veriler: Polis `veri.yml`, `cezalar.yml`, `sicil.yml`, `doktorlar.yml`, `gorsel.yml`, `idam.yml`, `yarali.yml`. Bekçi `kayit.db`, `mezarlar.yml`.
- Burada derleme: el yazımı API taklitleri (`/home/claude/stubs`) + `javac`. Kullanılan her yeni Paper API'si önce `/tmp/paperrepo` (Paper main, sparse clone) içinde imza olarak doğrulanır, sonra taklide eklenir.
- Tek kurulum: `kurulum.sh` = 3 eklenti + yedek. Tekrar çalıştırmak güvenli, yedekleri silmez, zamanlayıcıyı çiftlemez.

## Review Focus

1. **Arama/mezar/ender menüsünden kopyalama:** Mahkum ya da başka biri menü açıkken eşyayı yerinden oynatırsa tıklama hiçbir şey taşımamalı, ekran yenilenmeli. (Task 5: `AramaKurali.gercekYuva` testi + gerçek yuva `equals` kontrolü; Task 13 aynı kural.)
2. **Geri alma başkasının sonraki değişikliğini ezmemeli:** Ahmet'in kırdığı yere sonra Mehmet blok koyduysa geri alma atlamalı. (Task 11: `GeriAlmaKurali.karar` testleri.)
3. **Bağlantı kopması ve restart'ta ölüm olmamalı:** `TIMED_OUT`, `KICKED`, sunucu kapanırken çıkış öldürmemeli. Kelepçeli ve yaralı çift öldürülmemeli. (Task 3: `SavasKurali.olmeliMi` testleri.)
4. **Yedek saklama yanlış dosyayı silmemeli:** Son 24 saatlik yedeklerin hepsi ve 14 güne kadar her günün son yedeği kalmalı. Arşiv olmayan dosyalara dokunulmamalı. (Task 14: `yedek_test.sh`.)
5. **Mezar yetkisi:** Yabancı 30 dk dolmadan açamamalı ve kıramamalı. PvP olmayan ölümde "öldüren" boş olmalı, polis her zaman açabilmeli. (Task 12: `MezarKurali` testleri.)

---

## Dosya yapısı

```
/home/claude/
  CayOcagi/                         (var) Semaverler.java, CayListener.java değişir
  PolisKelepce/src/main/java/tr/efe/polis/
    Etiket.java            YENİ  rol + durum kafa etiketi, birleşik takım
    SohbetOneki.java       YENİ  AsyncChatEvent önek
    EskiMesaiGocu.java     YENİ  eski üniformayı silip zırhı geri verir
    SavasKurali.java       YENİ  saf: çıkışta ölmeli mi
    Savas.java             YENİ  savaş etiketi dinleyicisi
    AramaKurali.java       YENİ  saf: ekran yuvası -> gerçek yuva
    Idam.java              YENİ  /idam, darağacı, giriş yasağı
    IdamKurali.java        YENİ  saf: süre ayrıştırma ve tavan
    Mesai.java             SİLİNİR
    Gozalti.java, Yarali.java, CezaSistemi.java, Gorsel.java, Kayitlar.java, PolisPlugin.java  değişir
  Bekci/                            YENİ eklenti (pom.xml, plugin.yml, config.yml)
    src/main/java/tr/efe/bekci/
      BekciPlugin.java  Islem.java  Veritabani.java  KayitDinleyici.java  KapFarki.java
      Inceleme.java  GeriAlma.java  GeriAlmaKurali.java  SandikBulucu.java
      Mezar.java  MezarKurali.java  Sure.java
  yedek/yedek.sh  yedek/yedek-geri.sh  yedek/saklama.sh (saklama fonksiyonu)
  testler/                          saf mantık testleri (sunucuya gitmez)
    Dogrula.java  calistir.sh  yedek_test.sh  *Test.java
  kurulum-sablon.sh                 değişir
```

---

### Task 0: Altyapı — git, test koşucusu, API doğrulama

**Files:**
- Create: `/home/claude/.gitignore`, `testler/Dogrula.java`, `testler/calistir.sh`

**Interfaces:**
- Produces: `Dogrula.esit(Object beklenen, Object gercek, String ad)`, `Dogrula.dogru(boolean, String ad)`. Başarısızlıkta `AssertionError`.
- Produces: `bash testler/calistir.sh`: taklitler + üç eklentinin kaynakları + `testler/*.java` birlikte derlenir, adı `*Test` olan her sınıfın `main`'i çalıştırılır. Herhangi biri patlarsa sıfırdan farklı çıkış kodu.

- [ ] **Step 1:** `git init` ve `.gitignore` (izlenecekler: `CayOcagi PolisKelepce Bekci yedek testler docs kurulum-sablon.sh stubs`, `target/` hariç). İlk commit: "temel: 1.9.1 durumu".
- [ ] **Step 2:** `Dogrula.java` ve `calistir.sh` yazılır. Boş test listesiyle `bash testler/calistir.sh` çalıştırılır. Beklenen: "0 test, hepsi geçti", çıkış 0.
- [ ] **Step 3:** `/tmp/paperrepo` sparse checkout'a `event/player`, `event/inventory`, `event/block`, `block`, `entity`, `inventory`, `io/papermc/paper/event` (Paper ek API yolu) eklenir. Şu imzalar grep ile doğrulanır ve sonuç `docs/api-dogrulama.md` dosyasına satır satır yazılır:
  - `PlayerQuitEvent.getReason()` ve `QuitReason` değerleri
  - `AsyncChatEvent.renderer(ChatRenderer)`
  - `Player.openBook(ItemStack)`
  - `Entity.setVisibleByDefault`, `Player.showEntity(Plugin, Entity)`
  - `LeashHitch`, `LivingEntity.setLeashHolder`
  - `Skull.setOwningPlayer`
  - `AsyncPlayerPreLoginEvent.disallow(Result, Component)`
  - `InventoryOpenEvent` / `InventoryCloseEvent` ve `InventoryHolder` → `BlockState`/`DoubleChest`
  - `Player.getEnderChest()`, `ArmorStand.setMarker`, `setCustomNameVisible`
  - `BlockExplodeEvent`/`EntityExplodeEvent.blockList()`, `BlockPistonExtendEvent.getBlocks()`
  - `PlayerBucketEmptyEvent`, `BlockIgniteEvent`, `TNTPrimeEvent`
  - Ses adları: `ENTITY_WARDEN_HEARTBEAT`, `BLOCK_BELL_USE`, `BLOCK_NOTE_BLOCK_BASEDRUM`, `BLOCK_CHAIN_PLACE`, `BLOCK_IRON_TRAPDOOR_CLOSE`, `BLOCK_BUBBLE_COLUMN_UPWARDS_AMBIENT`
  - Parçacıklar: `SOUL`, `CAMPFIRE_COSY_SMOKE`, `LARGE_SMOKE`, `CRIT`, `CLOUD`
  - Etki: `PotionEffectType.DARKNESS`
  - Paper `plugin.yml` `libraries` desteği
  - Doğrulanamayan her satır için yedek yol yazılır (ör. ses yoksa yakın bir ses).
- [ ] **Step 4:** `xerial/sqlite-jdbc` için GitHub'dan en son kararlı sürüm alınır (`git ls-remote --tags https://github.com/xerial/sqlite-jdbc`) ve aynı dosyaya yazılır.
- [ ] **Step 5:** Commit: "altyapı: test koşucusu, API doğrulama".

---

### Task 1: Rol etiketi, sohbet öneki, simgeler, mesai kaldırma (Polis)

**Files:**
- Create: `Etiket.java`, `SohbetOneki.java`, `EskiMesaiGocu.java`, `testler/EtiketTest.java`
- Delete: `Mesai.java`
- Modify: `Gorsel.java` (etiket kodu `Etiket`'e taşınır), `Gozalti.java` (`polis`/`baskan` atanınca izin), `PolisPlugin.java`, `plugin.yml` (`mesai` silinir), `config.yml` (`gorsel.simgeler: true`)

**Interfaces:**
- Produces:
  - `Etiket.rolOneki(String rol, boolean simge) -> String`. rol ∈ {`polis`,`baskan`,`doktor`}. Simgeli: `"[★ Polis] "`, `"[♛ Başkan] "`, `"[✚ Doktor] "`. Simgesiz: `"[Polis] "` vb.
  - `Etiket.takimAdi(String durum, String rol, String eskiTakim) -> String`. Durum ∈ {`gozalti`,`yarali`,`araniyor`, null}. Durum varsa `"pk_" + durum`. Yoksa rol varsa ve eski takım yoksa `"pk_" + rol`, eski takım varsa `"pk_" + rol + "_" + kisaOzet(eskiTakim)`. Hiçbiri yoksa null.
  - `Etiket.guncelle(Player)`: Gorsel'in saniyelik döngüsünden çağrılır.
  - `Gozalti.rolu(Player) -> String` (`"baskan"`/`"polis"`/null). `Yarali.doktorMu` mevcut.
  - İzin `poliskelepce.rol.polis`: polis ve başkana PermissionAttachment ile girişte, atamada ve `acilistaGeriYukle`'de verilir, alınınca kaldırılır.
- Birleşik takım: öneki `rolOneki + eskiTakım.prefix()`, rengi eski takımın rengi. Takım yoksa oluşturulur, varsa öneki tazelenir.

- [ ] **Step 1:** `EtiketTest`:
  - `takimAdi("gozalti","polis","mimar") == "pk_gozalti"`
  - `takimAdi(null,"polis",null) == "pk_polis"`
  - `takimAdi(null,"polis","mimar")` `"pk_polis_"` ile başlar ve aynı girdide hep aynı sonucu verir
  - `takimAdi(null,null,"mimar") == null`
  - `rolOneki("baskan",true) == "[♛ Başkan] "`
  - `rolOneki("doktor",false) == "[Doktor] "`
- [ ] **Step 2:** `bash testler/calistir.sh`. Beklenen: derleme hatası (Etiket yok).
- [ ] **Step 3:** `Etiket` yazılır. `Gorsel.etiketGuncelle` ve `takim()` kaldırılır, `eskiTakim` haritası ve `gorsel.yml` kaydı Etiket'e geçer.
- [ ] **Step 4:** `SohbetOneki`: `AsyncChatEvent` dinleyicisi. Rolü varsa render'da isim önüne renkli `rolOneki` eklenir (polis aqua, başkan altın, doktor açık mor). Mesaj içeriği değiştirilmez.
- [ ] **Step 5:** `EskiMesaiGocu`: `PlayerJoinEvent` ve açılışta çevrimiçi oyuncular.
  - Envanterde, zırhta ve sol elde PDC `poliskelepce:uniforma` olanları siler.
  - `mesai.yml` varsa oyuncunun saklı zırhını boş zırh yuvalarına koyar, sığmayanları envantere verir.
  - Herkes işlendikten sonra dosya `mesai.yml.eski` olarak yeniden adlandırılır (oyunda olmayanlar için silinmez, girişte okunmaya devam eder).
  - `Mesai.java` silinir.
- [ ] **Step 6:** `bash testler/calistir.sh` geçer. Taklitlerle derleme hatasız.
- [ ] **Step 7:** Commit: "polis: rol etiketi, sohbet öneki, mesai kaldırıldı".

---

### Task 2: Yaralı sadece PvP, çıkış kuralı, kalp atışı ve kararma (Polis)

**Files:**
- Modify: `Yarali.java`, `config.yml`
- Create: `testler/YaraliTest.java`

**Interfaces:**
- Produces:
  - `Yarali.pvpMi(Entity vuran) -> boolean` (static): Player ise ya da `Projectile` atanı Player ise true.
  - `Yarali.kalpAraligiTik(long kalanMs, long toplamMs) -> int` (static): kalan oran 1→0 iken 30 tick'ten 8 tick'e doğrusal, en az 8.
  - `Yarali.yaraliCikti(Player) -> boolean`: Task 3 çağırır. Yaralıysa ve kelepçeli değilse öldürüp true döner. Kelepçeliyse kalan süreyi `yarali.yml`'e yazıp yaralılık kaydını bırakır.
  - `Yarali.yaraliGirdi(Player)`: Girişte `yarali.yml`'de kayıt varsa yere yatırır, süre o kalan süreyle başlar.

- [ ] **Step 1:** `YaraliTest`:
  - `kalpAraligiTik(60000,60000) == 30`
  - `kalpAraligiTik(0,60000) == 8`
  - `kalpAraligiTik(30000,60000)` 15 ile 23 arasında
  - `pvpMi(null) == false`
- [ ] **Step 2:** Test çalıştırılır, derleme hatasıyla düşmesi beklenir.
- [ ] **Step 3:** `onHasar`: yaralı değilken yere düşürme yalnızca `e instanceof EntityDamageByEntityEvent ed && pvpMi(ed.getDamager())` iken. Diğer ölümcül hasarlar vanilla'ya bırakılır. Yaralıyken bitirme kuralı aynı kalır.
- [ ] **Step 4:** `onCik` kaldırılır, çıkış Task 3'teki `Savas` dinleyicisi üzerinden `yaraliCikti` ile yönetilir. Kelepçeli yaralının kalan süresi `yarali.yml`'e yazılır, `onJoin` → `yaraliGirdi`.
- [ ] **Step 5:** Tik:
  - Yaralıya `kalpAraligiTik` aralığıyla yalnızca kendisine `ENTITY_WARDEN_HEARTBEAT` (doğrulanamazsa Task 0'daki yedek ses) çalınır.
  - Kalan ≤ 15 sn iken `DARKNESS` etkisi verilir, kaldırılınca silinir.
  - Ayar `gorsel.yarali-kalp: true`.
- [ ] **Step 6:** Testler geçer, derleme hatasız. Commit: "polis: yaralı sadece PvP, kalp atışı".

---

### Task 3: Savaştan çıkma engeli (Polis)

**Files:**
- Create: `SavasKurali.java`, `Savas.java`, `testler/SavasTest.java`
- Modify: `PolisPlugin.java`, `config.yml` (`savas.sure: 15`)

**Interfaces:**
- Consumes: `Yarali.yaraliCikti(Player)`, `Gozalti.kelepceliMi(Player)`, `Kayitlar.log`.
- Produces:
  - `SavasKurali.olmeliMi(String cikisNedeni, boolean sunucuKapaniyor, boolean kelepceli, boolean yarali, boolean savasta) -> boolean`. True yalnızca: neden `"DISCONNECTED"` && !kapanıyor && !kelepçeli && !yaralı && savaşta. Yaralı durumu `Yarali` yönetir.
  - `Savas.savastaMi(Player) -> boolean`, `Savas.etiketle(Player a, Player b)`.

- [ ] **Step 1:** `SavasTest`:
  - `olmeliMi("DISCONNECTED",false,false,false,true) == true`
  - `olmeliMi("TIMED_OUT",false,false,false,true) == false`
  - `olmeliMi("KICKED",false,false,false,true) == false`
  - `olmeliMi("DISCONNECTED",true,false,false,true) == false`
  - `olmeliMi("DISCONNECTED",false,true,false,true) == false`
  - `olmeliMi("DISCONNECTED",false,false,true,true) == false`
  - `olmeliMi("DISCONNECTED",false,false,false,false) == false`
- [ ] **Step 2:** Düştüğü görülür.
- [ ] **Step 3:** `SavasKurali` ve `Savas` yazılır:
  - `EntityDamageByEntityEvent` MONITOR, ignoreCancelled. İki taraf da oyuncuysa (`Yarali.pvpMi`) ikisine bitiş = şimdi + `savas.sure` sn.
  - Her 5 tick: savaştakilere action bar "Savaştasın: N sn, çıkarsan ölürsün". Bitince bir kez "Savaştan çıktın".
  - `PlayerQuitEvent` HIGHEST:
    - Önce `yaraliCikti`.
    - Sonra `olmeliMi(e.getReason().name(), kapaniyor, kelepceli, false, savasta)` true ise `setHealth(0)`, log "X savaşta çıktı (öldü)", son vurana ve 48 blok içindekilere mesaj.
    - `kapaniyor` = `PolisPlugin.onDisable` başında set edilen bayrak ya da `Bukkit.isStopping()` (Task 0'da doğrulanan).
- [ ] **Step 4:** Testler geçer, derleme hatasız. Commit: "polis: savaştan çıkma engeli".

---

### Task 4: `/vergi` (Polis)

**Files:**
- Modify: `CezaSistemi.java`, `Gorsel.java` (bossbar), `plugin.yml` (`vergi` komutu)
- Create: `testler/VergiTest.java`

**Interfaces:**
- Produces:
  - `Kayit` kaydına `boolean vergi` alanı. YAML'da `tur: ceza|vergi`, yoksa ceza.
  - `CezaSistemi.BorcOzet`'e `boolean sadeceVergi` eklenir (bütün borçları vergi mi).
  - `CezaSistemi.gecikmisVar(UUID)` yalnızca vergi olmayanlara bakar ([ARANIYOR] buradan gelir).
  - `static String turEtiketi(boolean vergi) -> "vergi"|"ceza"`.

- [ ] **Step 1:** `VergiTest`: `turEtiketi(true) == "vergi"`. Kayıt okuma yardımcısı `static boolean turOku(String yamlDegeri)`: `turOku(null) == false`, `turOku("vergi") == true`, `turOku("ceza") == false`.
- [ ] **Step 2:** Düşer.
- [ ] **Step 3:** Uygulama:
  - `/vergi` aynı `yaz()` yolunu `vergi=true` ile kullanır. Yetki: `baskanMi || poliskelepce.admin`.
  - Vergide `kayit().say(...CEZA)` ve makbuz yok, geç ödemede `GEC` sayılmaz.
  - Mesajlar ve liste satırları "#12 vergi" / "#12 ceza".
  - Bossbar önceliği ceza > vergi. Vergi metinleri "Ödenmemiş vergi: X kaldı /ode" (sarı) ve "GECİKMİŞ VERGİ (X) /ode" (kırmızı).
  - Pano ve borçlular satırlarına "(vergi)" etiketi.
- [ ] **Step 4:** Testler geçer, derleme hatasız. Commit: "polis: ayrı vergi".

---

### Task 5: Aramada eşya alma ve ender sandığı (Polis)

**Files:**
- Create: `AramaKurali.java`, `testler/AramaTest.java`
- Modify: `Gozalti.java` (`AramaEkrani`, `ustAra`, `onAramaTik`, `onAramaSurukle`)

**Interfaces:**
- Produces:
  - `AramaKurali.gercekYuva(int ekranYuvasi) -> int`. Ekran düzeni mevcut koddaki gibi: 0-26 → envanter 9-35, 27-35 → 0-8, 45-48 → zırh, 50 → sol el, 53 → ender düğmesi. Zırh/sol el/düğme için özel kodlar: `ZIRH_BOT=100` … `ZIRH_KASK=103`, `SOL_EL=104`, `ENDER_DUGME=-2`, boş = -1.
  - Ender görünümünde `gercekYuva` yerine birebir eşleme (0-26).
  - `AramaEkrani` alanları: `UUID hedef`, `boolean ender`, `Inventory envanter`.

- [ ] **Step 1:** `AramaTest`:
  - `gercekYuva(0) == 9`, `gercekYuva(26) == 35`, `gercekYuva(27) == 0`, `gercekYuva(35) == 8`
  - `gercekYuva(48) == 103`, `gercekYuva(45) == 100`, `gercekYuva(50) == 104`
  - `gercekYuva(53) == -2`, `gercekYuva(40) == -1`
- [ ] **Step 2:** Düşer.
- [ ] **Step 3:** Tıklama işleyişi:
  - Her tıklamada önce iptal edilir. Tıklanan yuva üst envanterde değilse ya da işlem eşyayı üst envantere taşıyacaksa (Shift-tık alttan, sayı tuşu, bırakma) bir şey yapılmaz.
  - Üst yuva için:
    - Mahkum kelepçeli değilse ya da 6 bloktan uzaksa ekran kapanır.
    - Gerçek yuvadaki eşya ekrandakine `equals` değilse ekran yeniden çizilir.
    - Eşitse gerçek yuva boşaltılır, eşya `ver(polis, ...)` ile verilir, ekran yeniden çizilir.
  - Log "Efe, Ahmet'in üstünden 5 elmas aldı" ya da "...ender sandığından...". Mahkuma mesaj gider.
  - Düğme (53, `ENDER_CHEST` simgesi) ender görünümünü açar.
- [ ] **Step 4:** Testler geçer, derleme hatasız. Commit: "polis: aramada eşya alma ve ender sandığı".

---

### Task 6: Kelepçeliyken çıkış, son hücre, kelepçe sesleri, pano, sicil kitabı (Polis)

**Files:**
- Modify: `Gozalti.java`, `Gorsel.java` (pano), `Kayitlar.java` (sicil kitabı, `KACMA` alanı)
- Create: `testler/GirisTest.java`

**Interfaces:**
- Produces:
  - `Gozalti.girisYeri(boolean polisCevrimici, boolean hucreVar) -> String` (static): `"polis"`, `"hucre"` ya da `"yerinde"`.
  - `Kayitlar.Alan.KACMA` (sicilde "Kaçma girişimi: N").
  - `Kayitlar.bedrockMu(UUID) -> boolean` (static): `getMostSignificantBits() == 0`.
  - `veri.yml` yeni anahtar `son-hucre.<uuid>`: dünya, x, y, z, yaw. `yerlestir()` içinde ayarlanır, `kelepceCikar` içinde silinir.

- [ ] **Step 1:** `GirisTest`:
  - `girisYeri(true,true) == "polis"`, `girisYeri(false,true) == "hucre"`, `girisYeri(false,false) == "yerinde"`
  - `bedrockMu(new UUID(0, 5)) == true`, `bedrockMu(UUID.randomUUID()) == false`
- [ ] **Step 2:** Düşer.
- [ ] **Step 3:** Çıkış ve giriş:
  - `onQuit`: kelepçeliyse polislere mesaj, log, `KACMA` sayılır.
  - `onJoin` ve `acilistaGeriYukle`:
    - `polis` → polisin yanına ışınla, `surukleniyor=true`.
    - `hucre` → son hücreye ışınla, bekletme.
    - Polislere "Ahmet geri geldi: x y z".
- [ ] **Step 4:** Sesler:
  - Kelepçe takılınca `BLOCK_CHAIN_PLACE` + `CRIT` parçacığı (bilekte 12 adet).
  - Açılınca `BLOCK_IRON_TRAPDOOR_CLOSE`. Ad doğrulanamazsa Task 0'daki yedek.
- [ ] **Step 5:** Pano zırh standlarıyla yeniden yazılır:
  - Her pano bir ana konum. Altında satır başına 0,28 blok aralıkla marker zırh standı (`setMarker`, görünmez, `customNameVisible`). PDC `poliskelepce:pano=<panoNo>`.
  - Güncellemede satır sayısı değişirse standlar silinip yeniden kurulur.
  - En fazla 14 satır.
  - Eski TextDisplay panoları açılışta PDC ile bulunup silinir, yerine standlı pano kurulur.
- [ ] **Step 6:** Sicil kitabı:
  - `/sicil` Java oyuncusuna `openBook` ile açılır. Sayfa 1: sayaçlar, sayfa 2: şu anki durum.
  - Bedrock oyuncusuna mevcut sohbet çıktısı gider.
  - `/sicil <ad> yazi` herkese sohbet çıktısı verir.
- [ ] **Step 7:** Testler geçer, derleme hatasız. Commit: "polis: kaçış, son hücre, sesler, standlı pano, sicil kitabı".

---

### Task 7: İdam (Polis)

**Files:**
- Create: `IdamKurali.java`, `Idam.java`, `testler/IdamTest.java`
- Modify: `PolisPlugin.java`, `plugin.yml` (`idam` komutu), `config.yml` (`idam.varsayilan-sure: 30dk`, `idam.en-fazla-sure: 1g`, `idam.onay-dk: 10`), `Kayitlar.java` (`Alan.IDAM`)

**Interfaces:**
- Consumes: `CezaSistemi.sureOku(String) -> long`, `Gozalti.kelepceliMi`, `Gozalti.kelepceCikarSessiz(Player)` (yeni, mesajsız ve başlıksız açma), `Kayitlar`, `Gorsel.baslik`.
- Produces:
  - `IdamKurali.sure(String arg, long varsayilanMs, long enFazlaMs) -> long`: arg null ya da okunamazsa varsayılan, aşarsa tavan.
  - `IdamKurali.bosYeterli(int altBosBlok) -> boolean`: ≥ 3.
  - `Idam.yasakliMi(UUID) -> long` (kalan ms, 0 = değil).

- [ ] **Step 1:** `IdamTest`:
  - `sure(null, 30dk, 1g) == 30dk`
  - `sure("2sa", 30dk, 1g) == 2sa`
  - `sure("3g", 30dk, 1g) == 1g`
  - `sure("abc", 30dk, 1g) == 30dk`
  - `bosYeterli(2) == false`, `bosYeterli(3) == true`
- [ ] **Step 2:** Düşer.
- [ ] **Step 3:** Komutlar:
  - `/idam <oyuncu> [süre]` (başkan/op): onay haritası (uuid → bitiş, süre, onaylayan), `idam.onay-dk` dk geçerli. Mahkuma ve polislere mesaj gider.
  - `iptal`, `affet` (op), `liste` alt komutları.
  - Kalıcılık `idam.yml`: `yasak.<uuid>: bitisMs`, `onay.<uuid>: ...`.
- [ ] **Step 4:** Darağacı:
  - `PlayerInteractEvent` RIGHT_CLICK_BLOCK, el HAND, polis Shift, ana elde PDC'siz `LEAD`, blok `*_FENCE` (çit kapısı değil).
  - Hedef: onaylı + kelepçeli + 4 blok içinde en yakın mahkum. Yoksa neden yazılır.
  - Çitin altındaki ardışık boş blok sayısı `bosYeterli` değilse hata mesajı.
  - Kurulum: mahkum ayakları çit−3'te olacak şekilde konur. Görünmez, sessiz, AI'sız, hasar almayan bir yaratık oluşturulur (`Chicken`, `setVisibleByDefault` yerine `setInvisible(true)` ile; Bedrock'ta kayış çizilsin diye yaratık görünürlüğü sunucudan kalkmaz). Çite `LeashHitch` konur, `setLeashHolder` ile bağlanır. Kayış polisten 1 eksilir.
- [ ] **Step 5:** Animasyon, her tick bir görev:
  - 0-100 tick geri sayım: her 20 tick'te davul (`BLOCK_NOTE_BLOCK_BASEDRUM`) ve 32 blok içine başlık "İDAM: Ahmet" / "N". Başta üç kez `BLOCK_BELL_USE` (0, 10, 20. tick).
  - 100-120 tick: mahkum y'si tick başına 0,05 artırılarak 1 blok yükselir, yaratık boyun hizasında takip eder.
  - 120-160 tick: yaw = başlangıç ± 8·sin(tick·0,3).
  - Son: `kelepceCikarSessiz`, ölüm mesajı "Ahmet idam edildi" (`PlayerDeathEvent` bu oyuncu için işaretliyse `deathMessage` değiştirilir). `setHealth(0)`, `LARGE_SMOKE` 30 adet, sunucuya gri duyuru, log ve sicile `IDAM`, yaratık ve düğüm silinir, yasak yazılır, 40 tick sonra `kick` ile "İdam edildin. X sonra tekrar girebilirsin."
  - İptal: polis çıkarsa ya da 8 bloktan uzaklaşırsa animasyon durur, düzenek silinir, mahkum yerinde bekletilir, onay geçerli kalır.
  - Mahkum geri sayımda çıkarsa: `setHealth(0)` + yasak + log.
- [ ] **Step 6:** `AsyncPlayerPreLoginEvent`: yasak varsa `KICK_BANNED` ile "İdam edildin. Kalan: 18 dk".
- [ ] **Step 7:** Testler geçer, derleme hatasız. Commit: "polis: idam".

---

### Task 8: Semaver yazısı ve buharı (Çay 1.3.0)

**Files:**
- Modify: `CayOcagi/.../Semaverler.java`, `CayListener.java` (tik), `CayOcagi/pom.xml`, `plugin.yml` (1.3.0), `config.yml` (`gorsel.semaver-yazisi: true`)
- Create: `testler/SemaverTest.java`

**Interfaces:**
- Produces:
  - `Semaverler.demYazisi(long kalanMs, long toplamMs) -> String` (static): kalan > 0 iken `"Demleniyor... %" + floor((toplam-kalan)*100/toplam)`, değilse `"Çay hazır!"`.
  - Her semaver için bir marker zırh standı (PDC `cayocagi:etiket=<anahtar>`), kazanın 1,2 blok üstünde.

- [ ] **Step 1:** `SemaverTest`:
  - `demYazisi(60000,60000) == "Demleniyor... %0"`
  - `demYazisi(24000,60000) == "Demleniyor... %60"`
  - `demYazisi(0,60000) == "Çay hazır!"`
- [ ] **Step 2:** Düşer.
- [ ] **Step 3:** Saniyelik tik:
  - Stand yoksa oluşturulur, isim güncellenir.
  - Demlenirken `CAMPFIRE_COSY_SMOKE` 2 adet ve her 4 sn'de `BLOCK_BUBBLE_COLUMN_UPWARDS_AMBIENT`.
  - Semaver kaydı silinince ya da kazan bozulunca stand silinir. Açılışta sahipsiz `cayocagi:etiket` standları temizlenir.
- [ ] **Step 4:** Testler geçer, derleme hatasız. Commit: "çay: semaver yazısı ve buharı".

---

### Task 9: Bekçi iskeleti, veritabanı ve kayıt (Bekçi)

**Files:**
- Create: `Bekci/pom.xml` (PolisKelepce'ninkiyle aynı yapı, artifact `Bekci`), `plugin.yml` (`libraries: [org.xerial:sqlite-jdbc:<Task 0 sürümü>]`, komutlar `bekci`, `mezar`, izin `bekci.admin` op), `config.yml` (`saklama-gun: 30`, `mezar.herkese-acik-dk: 30`, `mezar.silinme-saat: 24`, `sandik.varsayilan: 64`, `sandik.en-fazla: 160`), `BekciPlugin.java`, `Islem.java`, `Veritabani.java`, `KayitDinleyici.java`, `KapFarki.java`, `Sure.java`, `testler/KapFarkiTest.java`, `testler/SureTest.java`

**Interfaces:**
- Produces:
  - `record Islem(long id, long zaman, UUID uuid, String isim, String dunya, int x, int y, int z, Tur tur, String eski, String yeni, String esya, int miktar, boolean geriAlindi)`
  - `enum Islem.Tur { KIR, KOY, KOVA, ATES, TNT, AL, KOY_KAP, KIR_ICERIK, ENDER_AL, ENDER_KOY, MEZAR_AL, MEZAR_KIR, MEZAR_SILINDI, GERI }`
  - `Veritabani`:
    - `ekle(Islem)`: kuyruğa, 1 sn'de bir ya da 200 kayıtta toplu yazılır.
    - `sorgu(String where, List<Object> param, int limit, int offset) -> CompletableFuture<List<Islem>>`
    - `isaretle(List<Long> ids)`, `temizle(long oncesiMs)`, `kapat()`.
    - Tablo `islem` ve indeksler (zaman), (dunya,x,y,z), (uuid,zaman).
  - `KapFarki.fark(Map<String,Integer> once, Map<String,Integer> sonra) -> Map<String,Integer>` (pozitif = koydu, negatif = aldı, sıfırlar yok). Anahtar: `Material` adı + (meta varsa `#` + `getItemMeta().hashCode()`).
  - `Sure.oku(String) -> long` (Polis `sureOku` ile aynı biçim), `Sure.yaz(long) -> String`.

- [ ] **Step 1:** Testler:
  - `KapFarkiTest`: `fark({elmas:5},{elmas:2}) == {elmas:-3}`, `fark({},{demir:4}) == {demir:4}`, `fark({a:1},{a:1}) == {}`.
  - `SureTest`: `oku("2sa") == 7_200_000`, `oku("1g12sa") == 129_600_000`, `oku("x") == -1`.
- [ ] **Step 2:** Düşer.
- [ ] **Step 3:** `Veritabani`:
  - JDBC `jdbc:sqlite:` + dosya yolu, `PRAGMA journal_mode=WAL`.
  - Bütün SQL tek `Executors.newSingleThreadExecutor` üzerinde.
  - Sürücü yüklenemezse eklenti kendini kapatır ve konsola "SQLite sürücüsü indirilemedi (internet?)" yazar.
- [ ] **Step 4:** `KayitDinleyici` (MONITOR, ignoreCancelled):
  - Olaylar: `BlockBreakEvent` (KIR, kapsa içerik satırları KIR_ICERIK), `BlockPlaceEvent` (KOY), `PlayerBucketEmptyEvent` (KOVA), `BlockIgniteEvent` oyuncu kaynaklı (ATES), `TNTPrimeEvent` oyuncu kaynaklı (TNT).
  - Kaplar: `InventoryOpenEvent` holder blok tabanlı kap ya da `DoubleChest` ise anlık görüntü (oyuncu → konum + sayım haritası). Ender sandığı için görüntü `getEnderChest()` + açılan blok konumu. `InventoryCloseEvent`'te `KapFarki.fark`, her eşya için AL/KOY_KAP ya da ENDER_AL/ENDER_KOY.
  - Açılışta ve 6 saatte bir `temizle(şimdi − saklama-gun)`.
- [ ] **Step 5:** Testler geçer, derleme hatasız. Commit: "bekçi: iskelet, veritabanı, kayıt".

---

### Task 10: İnceleme ve sorgular (Bekçi)

**Files:**
- Create: `Inceleme.java`
- Modify: `BekciPlugin.java`

**Interfaces:**
- Consumes: `Veritabani.sorgu`, `Sure`.
- Produces:
  - `Inceleme.satir(Islem, long simdi) -> String` (static): `"[12 dk önce] Ahmet KIRDI stone (10 64 -3)"`. Tür fiilleri: KIRDI, KOYDU, KOVA, YAKTI, TNT, ALDI, KOYDU(kap), ENDER ALDI/KOYDU, MEZARDAN ALDI, MEZAR KIRDI, MEZAR SİLİNDİ, GERİ ALDI.
  - `/bekci bak` (aç/kapa), `/bekci oyuncu <ad> <süre> [sayfa]`, `/bekci yakin <yarıçap> <süre> [sayfa]`.

- [ ] **Step 1:** `testler/IncelemeTest`: Elle kurulan bir `Islem(KIR, "minecraft:stone")` için `satir(...,simdi)` "Ahmet KIRDI" ve "(10 64 -3)" içerir. 12 dk önceki zaman "12 dk önce" içerir.
- [ ] **Step 2:** Düşer.
- [ ] **Step 3:** İnceleme modu:
  - Sol tık (iptal): o konumun son 10 işlemi.
  - Sağ tık: `getRelative(blockFace)` konumu.
  - Sayfalı sorgular: 10 satır, en yeni üstte, sonuç ana iş parçacığında yazılır.
- [ ] **Step 4:** Testler geçer, derleme hatasız. Commit: "bekçi: inceleme".

---

### Task 11: Geri alma (Bekçi)

**Files:**
- Create: `GeriAlmaKurali.java`, `GeriAlma.java`, `testler/GeriAlmaTest.java`

**Interfaces:**
- Produces:
  - `GeriAlmaKurali.karar(Islem.Tur tur, String kayitEski, String kayitYeni, String simdiki) -> String`: dönüş konulacak BlockData metni ya da `null` (= atla).
    - `KIR`: simdiki == kayitYeni (genelde hava) ise kayitEski, değilse null.
    - `KOY`: simdiki == kayitYeni ise `"minecraft:air"`, değilse null.
    - `KOVA`: simdiki su/lav kaynağı ise `"minecraft:air"`, değilse null.
    - `ATES`: simdiki ateş ise `"minecraft:air"`, değilse null.
    - Diğer türler: null.
  - `/bekci geri <ad> <süre> [yarıçap]`, `/bekci onayla`.

- [ ] **Step 1:** `GeriAlmaTest`:
  - `karar(KIR,"minecraft:stone","minecraft:air","minecraft:air") == "minecraft:stone"`
  - `karar(KIR,"minecraft:stone","minecraft:air","minecraft:dirt") == null` (başkası koymuş)
  - `karar(KOY,"minecraft:air","minecraft:tnt","minecraft:tnt") == "minecraft:air"`
  - `karar(KOY,"minecraft:air","minecraft:tnt","minecraft:air") == null`
  - `karar(AL,…) == null`
- [ ] **Step 2:** Düşer.
- [ ] **Step 3:** `GeriAlma`:
  - `geri`: sorgu (oyuncu, zaman ≥ şimdi−süre, geriAlindi=0, türler KIR/KOY/KOVA/ATES, isteğe bağlı yarıçap). Önizleme için her kayıt ana iş parçacığında `karar` ile sınanır ("N uygulanacak, M atlanacak"). Bekleyen iş (oyuncu başına) 60 sn.
  - `onayla`: en yeniden eskiye, tick başına 500 kayıt, `block.setBlockData(Bukkit.createBlockData(x), false)`. Uygulananlar `isaretle`. Sonunda özet ve `GERI` kaydı.
  - Bekleyen yokken `onayla` "Onaylanacak işlem yok" der.
- [ ] **Step 4:** Testler geçer, derleme hatasız. Commit: "bekçi: geri alma".

---

### Task 12: Mezar (Bekçi)

**Files:**
- Create: `MezarKurali.java`, `Mezar.java`, `testler/MezarTest.java`

**Interfaces:**
- Consumes: `Veritabani.ekle`, izin `poliskelepce.rol.polis` (Task 1).
- Produces:
  - `MezarKurali.acabilir(UUID bakan, boolean polis, UUID sahip, UUID olduren, long olusma, long simdi, long herkeseAcikMs) -> boolean`
  - `MezarKurali.silinmeli(long olusma, long simdi, long omurMs) -> boolean` (≥ ömür)
  - `MezarKurali.uyarmali(long olusma, long simdi, long omurMs, boolean uyarildi) -> boolean` (kalan ≤ 1 sa ve uyarılmadıysa)
  - `/mezar`, `/mezar liste` (op), `/mezar sil <no>` (op).
  - `mezarlar.yml`: `<no>`: sahip, sahip-isim, olduren, olusma, dunya, x, y, z, xp, uyarildi, esyalar (liste).

- [ ] **Step 1:** `MezarTest` (A sahip, K öldüren, Y yabancı, 30 dk = 1 800 000):
  - `acabilir(A,false,A,null,0,1,1800000)`
  - `acabilir(K,false,A,K,0,1,1800000)`
  - `acabilir(Y,true,A,null,0,1,1800000)` (polis)
  - `!acabilir(Y,false,A,K,0,1799999,1800000)`
  - `acabilir(Y,false,A,null,0,1800000,1800000)`
  - `silinmeli(0, 86_400_000, 86_400_000)`, `!silinmeli(0, 86_399_999, 86_400_000)`
  - `uyarmali(0, 82_800_000, 86_400_000, false)`, `!uyarmali(0, 82_800_000, 86_400_000, true)`
- [ ] **Step 2:** Düşer.
- [ ] **Step 3:** Ölüm (`PlayerDeathEvent` HIGH):
  - keepInventory değilse ve düşen ya da XP varsa: eşyalar ve `getDroppedExp` alınır, düşenler temizlenir.
  - Öldüren: `getKiller()`.
  - Konum: ölüm bloğu hava ve altı katıysa orası. Değilse 5 blok küpünde en yakın uygun yer. O da yoksa son sağlam zemin (her saniye `isOnGround` iken saklanır).
  - `PLAYER_HEAD` + `Skull.setOwningPlayer`. 2 marker stand: "☠ Ahmet'in mezarı" ve durum satırı. Sohbete "Mezarın: x y z".
- [ ] **Step 4:** Etkileşim:
  - Sağ tık: `acabilir` ise 54'lük al-sadece menü (Task 5'teki kural: gösterilen `equals` gerçek ise taşı). Sahip ilk açışta XP alır.
  - Boşalınca blok ve standlar silinir.
  - Kırma: yetkiliyse eşyalar ve XP saçılır, değilse iptal.
  - Patlama listelerinden ve piston olaylarından mezar blokları çıkarılır.
  - Kayıt: her alma `MEZAR_AL`, kırma `MEZAR_KIR`.
- [ ] **Step 5:** Dakikalık tik:
  - Durum satırı "Herkese açılmasına N dk / Silinmesine N sa".
  - `uyarmali` → sahibe mesaj.
  - `silinmeli` → `MEZAR_SILINDI` (içerik metni) kaydı, kayıt silinir. Blok yüklüyse kaldırılır, değilse `ChunkLoadEvent`'te koordinat listesine bakılarak kaldırılır.
  - Ruh parçacıkları (`SOUL`, 3 adet) her 3 sn.
- [ ] **Step 6:** Testler geçer, derleme hatasız. Commit: "bekçi: mezar".

---

### Task 13: Sandık bulucu, x-ray, ender bakma (Bekçi)

**Files:**
- Create: `SandikBulucu.java`

**Interfaces:**
- Consumes: `Veritabani.sorgu` (koyan kişi ve oyuncunun KOY kayıtları).
- Produces:
  - `/bekci sandik yakin [yarıçap]`, `/bekci sandik oyuncu <ad>`, `/bekci git <no>`, `/bekci ender <oyuncu>`.
  - `SandikBulucu.kapMi(Material) -> boolean` (static): sandık, kapanlı sandık, varil, shulker türleri, fırın türleri, huni, dağıtıcı, bırakıcı, simya standı, bakır sandıklar. Ender sandığı hariç.

- [ ] **Step 1:** `testler/SandikTest`: `kapMi(CHEST)`, `kapMi(BARREL)`, `kapMi(SHULKER_BOX)`, `!kapMi(ENDER_CHEST)`, `!kapMi(STONE)`. Taklit `Material` enum'una ilgili değerler eklenir.
- [ ] **Step 2:** Düşer.
- [ ] **Step 3:** `yakin`:
  - Yarıçaptaki yüklü chunk'larda `getTileEntities()`, `kapMi`. Uzaklığa göre sıralanır, ilk 30.
  - Satır: no, koordinat, uzaklık, dolu yuva sayısı, koyan (DB'de son KOY).
  - Java'da `ClickEvent.runCommand("/bekci git N")`. Liste oyuncu başına 10 dk saklanır.
  - 30 sn boyunca her sn `END_ROD` sütunu (yalnızca o oyuncuya, `player.spawnParticle`).
  - Java oyuncusuna her kabın içine `Shulker` (AI'sız, sessiz, görünmez, `setGlowing(true)`, `setVisibleByDefault(false)` + `showEntity`). 30 sn sonra ve eklenti kapanınca silinir.
- [ ] **Step 4:** `oyuncu`: KOY kayıtları (`kapMi` olan `yeni` değerleri). Yüklü chunk'ta blok hâlâ aynı türse "duruyor", yüklü değilse "kontrol edilmedi", değişmişse listelenmez.
- [ ] **Step 5:** `ender`: hedefin `getEnderChest()` içeriği 27'lik salt okunur kopya menüde açılır (bütün tıklamalar iptal).
- [ ] **Step 6:** Testler geçer, derleme hatasız. Commit: "bekçi: sandık bulucu".

---

### Task 14: Yedek betikleri

**Files:**
- Create: `yedek/saklama.sh`, `yedek/yedek.sh`, `yedek/yedek-geri.sh`, `testler/yedek_test.sh`

**Interfaces:**
- Produces:
  - `saklama.sh` içinde `silinecekleri_listele <klasör> <şimdi_epoch>`. Arşiv adı `mc-YYYY-MM-DD_HH-MM.tar.gz`. Çıktı: silinecek dosyaların tam yolları, satır başına bir.
  - Saklama kuralı: son 24 saat içindekilerin hepsi kalır. Daha eskilerden her takvim gününün (Europe/Istanbul) en yeni arşivi, şimdi − 14 gün'e kadar kalır. Gerisi silinir. Desene uymayan dosyalar listelenmez.
  - `yedek.sh`, ortam değişkenleri: `MC_DIZIN` (varsayılan `~/minecraft`), `YEDEK_DIZIN` (`~/yedek/arsiv`), `TMUX_OTURUM` (`mc`).
  - `yedek-geri.sh`.

- [ ] **Step 1:** `yedek_test.sh`:
  - Geçici klasöre sahte arşivler: şimdi−0..30 saat her saat, şimdi−2..20 gün her gün 3 tane. Ayrıca `notlar.txt`.
  - `ŞİMDİ` sabit verilir, beklenen silinecek kümesi elle hesaplanmış listeyle karşılaştırılır.
  - Ayrıca: `notlar.txt` asla listede yok. 14 günden eskilerin hepsi listede.
- [ ] **Step 2:** `bash testler/yedek_test.sh`. Beklenen: fonksiyon yok hatası.
- [ ] **Step 3:** `saklama.sh` yazılır (GNU `date -d`, `TZ=Europe/Istanbul`).
- [ ] **Step 4:** `yedek.sh`:
  1. Kilit dosyası (`flock`).
  2. tmux oturumu varsa `save-off`, `save-all flush`, log'da "Saved the game" ya da 60 sn beklenir.
  3. Disk kontrolü: gerekli = son arşivin 1,5 katı, en az 1 GB. Yetmezse en eski arşivler silinir, yine yetmezse `say Yedek alınamadı: disk dolu` ve çıkış 1.
  4. `tar -czf` ile `world*`, `plugins`, `config`, `server.properties`, `*.json`, `bukkit.yml`, `spigot.yml`, `start.sh` (var olanlar). `plugins/*/logs` ve `libraries`, `versions`, `cache`, `logs` hariç. Önce `.tmp` adıyla yazılır, bitince yeniden adlandırılır.
  5. Her durumda `trap` ile `save-on`.
  6. `silinecekleri_listele` çıktısı silinir. Sonuç `~/yedek/yedek.log`'a yazılır.
- [ ] **Step 5:** `yedek-geri.sh`:
  - Numaralı liste (tarih, boyut), numara, "evet" onayı.
  - `sudo systemctl stop minecraft` (servis yoksa tmux `stop` ve bekleme).
  - Arşivdeki üst düzey yollar `~/yedek/geri-yukleme-oncesi-<tarih>/` altına taşınır, arşiv açılır, servis başlatılır, özet yazılır.
- [ ] **Step 6:** `bash testler/yedek_test.sh` geçer. `bash -n` üç betikte de temiz. Sahte bir `MC_DIZIN` ile (tmux yok) `yedek.sh` bir arşiv üretir ve `tar -tzf` içinde `world/level.dat` görünür.
- [ ] **Step 7:** Commit: "yedek: saatlik yedek ve geri yükleme".

---

### Task 15: Tek kurulum, bütünleştirme, son kontrol

**Files:**
- Modify: `kurulum-sablon.sh`, `PolisKelepce/pom.xml` + `plugin.yml` (1.10.0), `Bekci` sürümleri
- Create: `kurulum.sh` (üretilir)

**Interfaces:**
- Consumes: bütün önceki görevler.

- [ ] **Step 1:** `kurulum-sablon.sh`:
  - `PLUGINLER="CayOcagi PolisKelepce Bekci"`. Yük `yedek/` klasörünü de içerir.
  - Eklentilerden sonra:
    - `~/yedek/` kurulur (betikler kopyalanır, `arsiv/` oluşturulur, mevcutlar silinmez).
    - `sudo tee /etc/systemd/system/minecraft-yedek.service` (Type=oneshot, User=ubuntu, ExecStart=/home/ubuntu/yedek/yedek.sh).
    - `minecraft-yedek.timer` (`OnCalendar=hourly`, `Persistent=true`).
    - `daemon-reload`, `enable --now minecraft-yedek.timer`. Dosyalar her seferinde aynı içerikle yazıldığı için tekrar çalıştırmak güvenli.
    - Kurulum sonunda bir deneme yedeği alınır.
  - Bitiş mesajına yeni komutlar ve `systemctl list-timers minecraft-yedek.timer` eklenir.
- [ ] **Step 2:** Bütün testler: `bash testler/calistir.sh && bash testler/yedek_test.sh`. Beklenen: hepsi geçti.
- [ ] **Step 3:** Üç eklenti taklitlerle ayrı ayrı derlenir, hata 0.
- [ ] **Step 4:** Yük üretilir (Python: `CayOcagi PolisKelepce Bekci yedek`, `target/` hariç). `/tmp/kk`'ye açılıp `diff -r` ile kaynakla karşılaştırılır (beklenen: fark yok). `bash -n kurulum.sh` temiz.
- [ ] **Step 5:** Bütün dalın bağımsız incelemesi (taze bir inceleyici, spec + `git diff` ilk commit..HEAD). Bulunanlar düzeltilir, testler tekrar.
- [ ] **Step 6:** Commit: "sürüm: Çay 1.3.0, Polis 1.10.0, Bekçi 1.0.0, yedek". `kurulum.sh` kullanıcıya gönderilir, kurulum sonrası test listesi (spec §6) ile birlikte.
