package tr.efe.polis;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.Particle;
import org.bukkit.Color;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Polis rolü ve kelepçe (gözaltı).
 * - Polisleri op'lar (ya da belediye başkanı) /polis ekle ile atar. Başkanı op /baskan ata ile atar. Kelepçe ve anahtarı sadece polisler craftlayabilir.
 * - Polis kelepçeyle oyuncuya sağ tıklar: kelepçe takılır, oyuncu polisi takip eder (sürüklenir).
 * - Tekrar sağ tık: bırakır, oyuncu olduğu yerde kelepçeli kalır. Bir daha sağ tık: tekrar sürükler.
 * - Takılan her kelepçenin kendi anahtarı polise gelir; sadece o anahtar o kelepçeyi açar. Süre sınırı yok.
 * - Polis kelepçeliye Shift + sağ tık ile üst arama yapar (salt okunur).
 * - Kelepçeli oyuncu blok kıramaz/koyamaz, vuramaz, kapı/sandık kullanamaz, eşya atamaz, komut kullanamaz.
 * - Oyundan çıkıp girmek ya da sunucu restart'ı kelepçeyi açmaz. Acil durumda op'lar /serbest kullanır.
 */
public final class Gozalti implements Listener, CommandExecutor, TabCompleter {

    private static final Set<String> SERBEST_KOMUTLAR = Set.of(
            "login", "l", "register", "reg", "logout", "msg", "tell", "w", "r", "ode", "ceza", "sicil");

    /** capa: bekletilmeye başladığı yer (bilgi amaçlı). */
    private record Kelepce(UUID polis, boolean surukleniyor, String id, Location capa) {}

    private final PolisPlugin plugin;
    private final PolisItems items;
    private final File dosya;

    private final Map<UUID, String> polisler = new LinkedHashMap<>();
    private final Map<UUID, Kelepce> kelepceliler = new HashMap<>();
    /** Kelepçeliyken oyundan çıkanlar (oyuncu -> kelepçe kimliği); girince kelepçe geri takılır. */
    private final Map<UUID, String> cevrimdisiKelepceli = new HashMap<>();
    /** Çevrimdışı kelepçelinin kelepçesini takan polis (girince geri yüklenir). */
    private final Map<UUID, UUID> cevrimdisiPolis = new HashMap<>();
    /** Mahkumun bu kelepçe takılıyken en son yerleştirildiği yer (girişte polis yoksa buraya konur). */
    private final Map<UUID, Location> sonHucre = new HashMap<>();
    private CezaSistemi ceza;

    /** Belediye başkanı: polisin her şeyi + op'un plugin yetkileri (polis atama, /serbest, /cay ver ...). */
    private UUID baskan;
    private String baskanIsim;
    /** Polis ve başkanın rol izinleri (poliskelepce.rol.polis; başkana ayrıca admin izinleri). */
    private final Map<UUID, PermissionAttachment> rolYetkileri = new HashMap<>();
    private static final String[] BASKAN_IZINLERI = {"poliskelepce.admin", "cayocagi.ver"};

    public Gozalti(PolisPlugin plugin, PolisItems items) {
        this.plugin = plugin;
        this.items = items;
        this.dosya = new File(plugin.getDataFolder(), "veri.yml");
    }

    public void cezaBagla(CezaSistemi ceza) {
        this.ceza = ceza;
    }

    public boolean polisMi(HumanEntity p) {
        return polisler.containsKey(p.getUniqueId()) || baskanMi(p);
    }

    public boolean baskanMi(HumanEntity p) {
        return baskan != null && baskan.equals(p.getUniqueId());
    }

    /** Oyuncunun rolüne göre izinlerini baştan kurar. */
    public void yetkileriGuncelle(Player p) {
        PermissionAttachment eski = rolYetkileri.remove(p.getUniqueId());
        if (eski != null) p.removeAttachment(eski);
        if (!polisMi(p)) return;
        PermissionAttachment ek = p.addAttachment(plugin);
        ek.setPermission("poliskelepce.rol.polis", true);
        if (baskanMi(p)) for (String izin : BASKAN_IZINLERI) ek.setPermission(izin, true);
        rolYetkileri.put(p.getUniqueId(), ek);
    }

    private void yetkiYenile(UUID id) {
        Player p = Bukkit.getPlayer(id);
        if (p != null) yetkileriGuncelle(p);
        else rolYetkileri.remove(id);
    }

    /** "baskan", "polis" ya da null. */
    public String rolu(Player p) {
        if (baskanMi(p)) return "baskan";
        if (polisler.containsKey(p.getUniqueId())) return "polis";
        return null;
    }

    public boolean kelepceliMi(Player p) {
        return kelepceliler.containsKey(p.getUniqueId());
    }

    /** Kelepçeli girişte nereye konur: "polis" (yanına), "hucre" (son yerleştirildiği yer), "yerinde". */
    public static String girisYeri(boolean polisCevrimici, boolean hucreVar) {
        if (polisCevrimici) return "polis";
        return hucreVar ? "hucre" : "yerinde";
    }

    /** Kelepçeyi takan polisin adı; kelepçeli değilse null. */
    public String polisIsmi(Player h) {
        Kelepce k = kelepceliler.get(h.getUniqueId());
        if (k == null) return null;
        if (k.polis().equals(h.getUniqueId())) return "?";
        Player p = Bukkit.getPlayer(k.polis());
        if (p != null) return p.getName();
        if (polisler.containsKey(k.polis())) return polisler.get(k.polis());
        if (k.polis().equals(baskan)) return baskanIsim;
        return "?";
    }

    public boolean surukleniyorMu(Player h) {
        Kelepce k = kelepceliler.get(h.getUniqueId());
        return k != null && k.surukleniyor();
    }

    /** Oyundaki kelepçelilerin adları (karakol panosu için). */
    public List<String> kelepceliIsimleri() {
        List<String> l = new ArrayList<>();
        for (UUID id : kelepceliler.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) l.add(p.getName());
        }
        return l;
    }

    private Kayitlar kayit() {
        return plugin.kayit();
    }

    // ------------------------------------------------------------------
    // Kelepçe ve anahtar kullanımı
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onOyuncuyaTik(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();

        if (kelepceliMi(p)) {
            e.setCancelled(true);
            return;
        }

        ItemStack elde = p.getInventory().getItemInMainHand();
        PolisItems.Tur tur = items.turu(elde);

        // Üst arama: polis, kelepçeli oyuncuya Shift + sağ tık
        if (p.isSneaking() && tur != PolisItems.Tur.ANAHTAR && polisMi(p)
                && e.getRightClicked() instanceof Player aranan && kelepceliMi(aranan)) {
            e.setCancelled(true);
            ustAra(p, aranan);
            return;
        }

        // Kelepçeliye yemek yedirme: elinde yiyecekle kelepçeliye sağ tık (herkes yapabilir)
        if (tur == null && !p.isSneaking() && e.getRightClicked() instanceof Player ac && kelepceliMi(ac)
                && yenebilir(elde.getType())) {
            e.setCancelled(true);
            yemekYedir(p, ac, elde);
            return;
        }

        if (tur == null) return;
        e.setCancelled(true); // kayış moba bağlanmasın vs.

        if (!(e.getRightClicked() instanceof Player hedef)) return;

        int slot = p.getInventory().getHeldItemSlot();
        if (tur == PolisItems.Tur.ANAHTAR) {
            // Polis, kelepçelediği kişinin anahtarıyla: sağ tık = bırak / götür, Shift + sağ tık = aç
            Kelepce k = kelepceliler.get(hedef.getUniqueId());
            boolean dogruAnahtar = k != null && k.id().equals(items.anahtarKelepceId(elde));
            if (polisMi(p) && dogruAnahtar && !p.isSneaking()) {
                kelepceyle(p, hedef, -1);
            } else {
                anahtarla(p, hedef, slot);
            }
        } else {
            kelepceyle(p, hedef, slot);
        }
    }

    private void anahtarla(Player p, Player hedef, int slot) {
        ItemStack anahtar = p.getInventory().getItem(slot);
        Kelepce k = kelepceliler.get(hedef.getUniqueId());
        if (k == null) {
            plugin.mesaj(p, hedef.getName() + " kelepçeli değil.", NamedTextColor.YELLOW);
            return;
        }
        String id = items.anahtarKelepceId(anahtar);
        if (id == null || !id.equals(k.id())) {
            plugin.mesaj(p, "Bu anahtar " + hedef.getName() + " adlı oyuncunun kelepçesine uymuyor.", NamedTextColor.RED);
            return;
        }
        slotEksilt(p, slot);
        kelepceCikar(hedef, p.getName() + " anahtarla kelepçeni açtı.");
        kayit().log(p.getName() + ", " + hedef.getName() + " adlı oyuncunun kelepçesini anahtarla açtı" + (polisMi(p) ? "" : " (polis değil!)"));
        ver(p, items.olustur(PolisItems.Tur.KELEPCE, 1));
        plugin.mesaj(p, hedef.getName() + " adlı oyuncunun kelepçesini açtın, kelepçe sende.", NamedTextColor.GREEN);
        Player polis = Bukkit.getPlayer(k.polis());
        if (polis != null && !polis.equals(p) && !polis.equals(hedef)) {
            plugin.mesaj(polis, p.getName() + ", " + hedef.getName() + " adlı oyuncunun kelepçesini anahtarla açtı!", NamedTextColor.RED);
        }
    }

    private void kelepceyle(Player p, Player hedef, int slot) {
        if (!polisMi(p)) {
            plugin.mesaj(p, "Kelepçeyi sadece polisler kullanabilir.", NamedTextColor.RED);
            return;
        }
        Kelepce mevcut = kelepceliler.get(hedef.getUniqueId());
        if (mevcut != null && slot >= 0) {
            // Kelepçeliye ikinci kelepçe takılamaz; götürmek/bırakmak anahtarla olur
            plugin.mesaj(p, hedef.getName() + " zaten kelepçeli (takan: " + polisIsmi(hedef)
                    + "). Götürmek ya da bırakmak için onun anahtarıyla sağ tıkla.", NamedTextColor.YELLOW);
            return;
        }
        if (mevcut != null) {
            // Başka polisin tutuklusunu anahtarı olmadan devralamazsın
            boolean takan = mevcut.polis().equals(p.getUniqueId());
            boolean anahtarVar = slotBul(p, is -> mevcut.id().equals(items.anahtarKelepceId(is))) >= 0;
            if (!takan && !anahtarVar) {
                plugin.mesaj(p, hedef.getName() + " başka bir polisin gözaltında. Anahtarı olmadan götüremezsin.", NamedTextColor.RED);
                return;
            }
            if (mevcut.surukleniyor() && mevcut.polis().equals(p.getUniqueId())) {
                kelepceliler.put(hedef.getUniqueId(), new Kelepce(p.getUniqueId(), false, mevcut.id(), hedef.getLocation()));
                kaydet();
                plugin.mesaj(p, hedef.getName() + " adlı oyuncuyu bıraktın. Olduğu yerde kelepçeli bekliyor.", NamedTextColor.AQUA);
                plugin.mesaj(hedef, p.getName() + " seni burada bıraktı. Hâlâ kelepçelisin, çok yavaş hareket edebilirsin.", NamedTextColor.YELLOW);
            } else {
                kelepceliler.put(hedef.getUniqueId(), new Kelepce(p.getUniqueId(), true, mevcut.id(), null));
                plugin.mesaj(p, hedef.getName() + " adlı oyuncuyu götürüyorsun.", NamedTextColor.AQUA);
                plugin.mesaj(hedef, p.getName() + " seni götürüyor.", NamedTextColor.YELLOW);
            }
            return;
        }
        if (slot < 0 || items.turu(p.getInventory().getItem(slot)) != PolisItems.Tur.KELEPCE) {
            plugin.mesaj(p, "Üstünde kelepçe yok.", NamedTextColor.RED);
            return;
        }
        String id = UUID.randomUUID().toString().substring(0, 8);
        slotEksilt(p, slot); // kelepçe artık hedefin bileğinde
        kelepceliler.put(hedef.getUniqueId(), new Kelepce(p.getUniqueId(), true, id, null));
        ver(p, items.anahtar(hedef.getName(), id));
        etkiVer(hedef);
        if (plugin.getConfig().getBoolean("gorsel.kelepce-efekt", true)) {
            hedef.getWorld().playSound(hedef.getLocation(), Sound.BLOCK_CHAIN_PLACE, 1f, 1.2f);
            hedef.getWorld().spawnParticle(Particle.CRIT, hedef.getLocation().add(0, 0.9, 0), 12, 0.2, 0.1, 0.2, 0.05);
        }
        kaydet();
        plugin.gorsel().baslik(hedef, "GÖZALTINA ALINDIN", "Polis: " + p.getName(), NamedTextColor.RED, Sound.BLOCK_ANVIL_LAND, 1.6f);
        kayit().say(hedef.getUniqueId(), hedef.getName(), Kayitlar.Alan.GOZALTI);
        kayit().log(p.getName() + ", " + hedef.getName() + " adlı oyuncuyu kelepçeledi (" + konum(hedef.getLocation()) + ")");
        plugin.mesaj(p, hedef.getName() + " kelepçelendi, seni takip ediyor. Kelepçe onun bileğinde, anahtarı sende.", NamedTextColor.AQUA);
        plugin.mesaj(p, "Anahtarla sağ tık: bırak / götür. Shift + anahtarla sağ tık: aç. Boş elle Shift + sağ tık: üst ara.", NamedTextColor.GRAY);
        plugin.mesaj(hedef, p.getName() + " seni kelepçeledi! Kelepçeni sadece senin anahtarın açar.", NamedTextColor.RED);
    }

    // ------------------------------------------------------------------
    // Üst arama (salt okunur)
    // ------------------------------------------------------------------

    private static final class AramaEkrani implements InventoryHolder {
        private final UUID hedef;
        private final boolean ender;
        private Inventory envanter;

        private AramaEkrani(UUID hedef, boolean ender) {
            this.hedef = hedef;
            this.ender = ender;
        }

        @Override
        public Inventory getInventory() {
            return envanter;
        }
    }

    private void ustAra(Player polis, Player hedef) {
        aramaAc(polis, hedef, false);
        plugin.mesaj(hedef, polis.getName() + " üstünü arıyor.", NamedTextColor.YELLOW);
    }

    private void aramaAc(Player polis, Player hedef, boolean ender) {
        AramaEkrani ekran = new AramaEkrani(hedef.getUniqueId(), ender);
        ekran.envanter = Bukkit.createInventory(ekran, 54,
                Component.text((ender ? "Ender sandığı: " : "Üst arama: ") + hedef.getName()));
        aramaCiz(ekran, hedef);
        polis.openInventory(ekran.envanter);
    }

    private static ItemStack dugme(org.bukkit.Material m, String ad) {
        ItemStack is = new ItemStack(m);
        org.bukkit.inventory.meta.ItemMeta meta = is.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(ad, NamedTextColor.LIGHT_PURPLE));
            is.setItemMeta(meta);
        }
        return is;
    }

    private void aramaCiz(AramaEkrani ekran, Player hedef) {
        Inventory inv = ekran.envanter;
        for (int i = 0; i < 54; i++) {
            int g = ekran.ender ? AramaKurali.enderYuva(i) : AramaKurali.gercekYuva(i);
            ItemStack is;
            if (g == AramaKurali.ENDER_DUGME) is = dugme(org.bukkit.Material.ENDER_CHEST, "Ender sandığını ara");
            else if (g == AramaKurali.GERI_DUGME) is = dugme(org.bukkit.Material.CHEST, "Üst aramaya dön");
            else is = gercekEsya(hedef, ekran.ender, g);
            inv.setItem(i, is == null ? null : is.clone());
        }
    }

    private static ItemStack gercekEsya(Player h, boolean ender, int g) {
        if (g < 0) return null;
        if (ender) return h.getEnderChest().getItem(g);
        if (g <= 35) return h.getInventory().getStorageContents()[g];
        if (g == AramaKurali.SOL_EL) return h.getInventory().getItemInOffHand();
        return h.getInventory().getArmorContents()[g - AramaKurali.ZIRH_BOT];
    }

    private static void gercekSil(Player h, boolean ender, int g) {
        if (ender) {
            h.getEnderChest().setItem(g, null);
        } else if (g <= 35) {
            h.getInventory().setItem(g, null);
        } else if (g == AramaKurali.SOL_EL) {
            h.getInventory().setItemInOffHand(null);
        } else {
            ItemStack[] zirh = h.getInventory().getArmorContents();
            zirh[g - AramaKurali.ZIRH_BOT] = null;
            h.getInventory().setArmorContents(zirh);
        }
    }

    // Arama ekranı sadece alma ekranı: polise eşya geçer, mahkuma eşya konamaz
    @EventHandler(priority = EventPriority.LOWEST)
    public void onAramaTik(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof AramaEkrani ekran)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player polis)) return;
        int ham = e.getRawSlot();
        if (ham < 0 || ham >= 54) return; // alt envanter: hiçbir şey taşınmaz
        Player h = Bukkit.getPlayer(ekran.hedef);
        if (h == null || !kelepceliMi(h) || !polisMi(polis) || !h.getWorld().equals(polis.getWorld())
                || h.getLocation().distance(polis.getLocation()) > 6) {
            Bukkit.getScheduler().runTask(plugin, () -> polis.closeInventory()); // tıklama olayında kapatma bir tick sonra
            plugin.mesaj(polis, "Arama bitti: mahkum yanında ve kelepçeli olmalı.", NamedTextColor.YELLOW);
            return;
        }
        int g = ekran.ender ? AramaKurali.enderYuva(ham) : AramaKurali.gercekYuva(ham);
        if (g == AramaKurali.ENDER_DUGME || g == AramaKurali.GERI_DUGME) {
            final boolean ender = g == AramaKurali.ENDER_DUGME;
            Bukkit.getScheduler().runTask(plugin, () -> aramaAc(polis, h, ender));
            return;
        }
        if (g < 0) return;
        ItemStack goster = e.getView().getTopInventory().getItem(ham);
        ItemStack gercek = gercekEsya(h, ekran.ender, g);
        if (goster == null || goster.getType() == org.bukkit.Material.AIR) return;
        if (!goster.equals(gercek)) {
            aramaCiz(ekran, h); // mahkumun envanteri değişmiş: hiçbir şey taşıma, ekranı yenile
            return;
        }
        gercekSil(h, ekran.ender, g);
        ver(polis, gercek.clone());
        aramaCiz(ekran, h);
        String ne = gercek.getAmount() + " " + CezaSistemi.esyaAdi(gercek.getType());
        plugin.kayit().log(polis.getName() + ", " + h.getName() + " adlı oyuncunun " + (ekran.ender ? "ender sandığından " : "üstünden ") + ne + " aldı");
        plugin.mesaj(h, polis.getName() + " " + (ekran.ender ? "ender sandığından " : "üstünden ") + ne + " aldı.", NamedTextColor.RED);
        BekciKopru.kaydet(polis, h.getLocation(), ekran.ender ? "ENDER_AL" : "AL",
                CezaSistemi.esyaAdi(gercek.getType()) + " (aramada, " + h.getName() + (ekran.ender ? "'in ender sandığından)" : "'in üstünden)"),
                gercek.getAmount());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAramaSurukle(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof AramaEkrani) e.setCancelled(true);
    }

    /** Mahkumu verilen yerde bekletir (idam düzeneği). */
    public void sabitle(Player h, Location yer) {
        Kelepce k = kelepceliler.get(h.getUniqueId());
        if (k == null) return;
        kelepceliler.put(h.getUniqueId(), new Kelepce(k.polis(), false, k.id(), yer.clone()));
    }

    /** Mesajsız, sessiz açma (idam). */
    public void kelepceCikarSessiz(Player hedef) {
        if (kelepceliler.remove(hedef.getUniqueId()) == null) return;
        sonHucre.remove(hedef.getUniqueId());
        kaydet();
        etkileriSil(hedef);
        bilekleriKaldir(hedef.getUniqueId());
    }

    private void kelepceCikar(Player hedef, String neden) {
        if (kelepceliler.remove(hedef.getUniqueId()) == null) return;
        sonHucre.remove(hedef.getUniqueId());
        kaydet(); // çökme olursa kelepçe geri gelmesin
        etkileriSil(hedef);
        bilekleriKaldir(hedef.getUniqueId());
        if (plugin.getConfig().getBoolean("gorsel.kelepce-efekt", true)) {
            hedef.getWorld().playSound(hedef.getLocation(), Sound.BLOCK_IRON_TRAPDOOR_CLOSE, 1f, 1.6f);
        }
        plugin.gorsel().baslik(hedef, "SERBESTSİN", neden != null ? neden : "", NamedTextColor.GREEN, Sound.ENTITY_PLAYER_LEVELUP, 1.4f);
        if (neden != null) plugin.mesaj(hedef, neden, NamedTextColor.GREEN);
    }

    // ------------------------------------------------------------------
    // Animasyon (resource pack gerektirmez)
    // - Bileklerde demir kelepçe halkaları (blok görüntüsü; Java'da görünür)
    // - Bileklerde demir parıltısı (Bedrock'ta da görünür), götürülürken polisle arasında zincir çizgisi
    // ------------------------------------------------------------------

    /** Kelepçelinin iki bileğindeki halka görüntüleri: [sağ, sol]. */
    private final Map<UUID, org.bukkit.entity.BlockDisplay[]> bilekler = new HashMap<>();

    private void bilekleriGuncelle(Player h) {
        if (!animasyonAcik()) { bilekleriKaldir(h.getUniqueId()); return; }
        org.bukkit.entity.BlockDisplay[] d = bilekler.get(h.getUniqueId());
        if (d != null && (!d[0].isValid() || !d[1].isValid() || !d[0].getWorld().equals(h.getWorld())
                || d[0].getLocation().distanceSquared(h.getLocation()) > 9)) { // ışınlandıysa havada süzülmesin, yeniden oluştur
            bilekleriKaldir(h.getUniqueId());
            d = null;
        }
        float yaw = h.getBodyYaw();
        Location ayak = h.getLocation();
        double y = KelepceKurali.bilekYuksekligi(h.isSneaking());
        if (d == null) {
            d = new org.bukkit.entity.BlockDisplay[] { halkaOlustur(h, bilekYeri(ayak, yaw, y, true)),
                    halkaOlustur(h, bilekYeri(ayak, yaw, y, false)) };
            bilekler.put(h.getUniqueId(), d);
            return;
        }
        d[0].teleport(bilekYeri(ayak, yaw, y, true));
        d[1].teleport(bilekYeri(ayak, yaw, y, false));
    }

    private static Location bilekYeri(Location ayak, float yaw, double y, boolean sag) {
        double[] o = KelepceKurali.bilekOfseti(yaw, sag);
        Location l = ayak.clone().add(o[0], y, o[1]);
        l.setYaw(yaw);
        l.setPitch(0);
        return l;
    }

    private org.bukkit.entity.BlockDisplay halkaOlustur(Player h, Location yer) {
        org.bukkit.entity.BlockDisplay d = yer.getWorld().spawn(yer, org.bukkit.entity.BlockDisplay.class, bd -> {
            bd.setBlock(org.bukkit.Material.IRON_BLOCK.createBlockData());
            bd.setPersistent(false);
            // Kolu saran ince demir bant (kol 0.25 blok; bant biraz daha geniş), merkezde
            bd.setTransformation(new org.bukkit.util.Transformation(
                    new org.joml.Vector3f(-0.15f, -0.05f, -0.15f), new org.joml.AxisAngle4f(),
                    new org.joml.Vector3f(0.3f, 0.1f, 0.3f), new org.joml.AxisAngle4f()));
            // Başka oyuncuların ekranında oyuncu modeli ~3 tick geriden gelir; halka da aynı hızda kaysın
            bd.setTeleportDuration(3);
            bd.setViewRange(0.5f);
        });
        // Kendi ekranında (F5) gecikmeli kayan halka görmesin
        h.hideEntity(plugin, d);
        return d;
    }

    private void bilekleriKaldir(UUID id) {
        org.bukkit.entity.BlockDisplay[] d = bilekler.remove(id);
        if (d == null) return;
        for (org.bukkit.entity.BlockDisplay x : d) if (x != null) x.remove();
    }

    private boolean animasyonAcik() {
        return plugin.getConfig().getBoolean("animasyon", true);
    }

    private void animasyon(Player h, Kelepce k) {
        if (!animasyonAcik()) return;
        World w = h.getWorld();
        Location ayak = h.getLocation();
        // Bileklerde küçük demir parıltısı (Bedrock'ta blok görüntüsü olmayabilir; parçacık her yerde görünür)
        float yaw = h.getBodyYaw();
        double by = KelepceKurali.bilekYuksekligi(h.isSneaking());
        Particle.DustOptions demir = new Particle.DustOptions(Color.fromRGB(0xB0B0B0), 0.6f);
        w.spawnParticle(Particle.DUST, bilekYeri(ayak, yaw, by, true), 1, 0.04, 0.03, 0.04, 0, demir);
        w.spawnParticle(Particle.DUST, bilekYeri(ayak, yaw, by, false), 1, 0.04, 0.03, 0.04, 0, demir);
        Location bilek = ayak.clone().add(0, by, 0);
        // Götürülürken zincir çizgisi
        if (k.surukleniyor()) {
            Player polis = Bukkit.getPlayer(k.polis());
            if (polis != null && polis.getWorld().equals(w)) {
                Location a = bilek;
                Location b = polis.getLocation().add(0, 0.9, 0);
                double mesafe = a.distance(b);
                if (mesafe > 0.5 && mesafe < 12) {
                    org.bukkit.util.Vector adim = b.toVector().subtract(a.toVector()).normalize().multiply(0.4);
                    Location nokta = a.clone();
                    Particle.DustOptions zincir = new Particle.DustOptions(Color.fromRGB(0x707070), 0.5f);
                    for (double d = 0; d < mesafe; d += 0.4) {
                        w.spawnParticle(Particle.DUST, nokta, 1, 0, 0, 0, 0, zincir);
                        nokta.add(adim);
                    }
                }
            }
        }
    }

    private static void etkiVer(Player p) {
        // Kelepçeli: hem yürüme hem koşma çok yavaş (Yavaşlık V), götürülürken de
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 4, false, false, true));
        p.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 40, 2, false, false, true));
        p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 40, 2, false, false, true));
    }

    private static void etkileriSil(Player p) {
        p.removePotionEffect(PotionEffectType.SLOWNESS);
        p.removePotionEffect(PotionEffectType.MINING_FATIGUE);
        p.removePotionEffect(PotionEffectType.WEAKNESS);
    }

    // ------------------------------------------------------------------
    // Craft: sadece polisler
    // ------------------------------------------------------------------

    @EventHandler
    public void onCraftHazirla(PrepareItemCraftEvent e) {
        Recipe r = e.getRecipe();
        boolean bizimTarif = r instanceof Keyed k && plugin.bizimTarif(k.getKey());
        if (bizimTarif) {
            HumanEntity yapan = e.getView().getPlayer();
            if (!polisMi(yapan)) e.getInventory().setResult(null);
            return;
        }
        // Kelepçe/anahtar başka tariflerde malzeme olarak kullanılmasın
        for (ItemStack is : e.getInventory().getMatrix()) {
            if (items.turu(is) != null) {
                e.getInventory().setResult(null);
                return;
            }
        }
    }

    // Crafter bloğunda oyuncu yok: kelepçe tarifi hiç çalışmasın, kelepçe/anahtar malzeme olmasın
    @EventHandler(ignoreCancelled = true)
    public void onCrafter(org.bukkit.event.block.CrafterCraftEvent e) {
        if (e.getRecipe() instanceof Keyed k && plugin.bizimTarif(k.getKey())) {
            e.setCancelled(true);
            return;
        }
        if (!(e.getBlock().getState() instanceof org.bukkit.block.Crafter c)) return;
        for (ItemStack is : c.getInventory().getContents()) {
            if (items.turu(is) != null) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCraft(CraftItemEvent e) {
        if (e.getRecipe() instanceof Keyed k && plugin.bizimTarif(k.getKey()) && !polisMi(e.getWhoClicked())) {
            e.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------
    // Kısıtlamalar
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent e) {
        if (kelepceliMi(e.getPlayer())) {
            // Kelepçeli yemek yiyebilsin (kapı/sandık/blok kullanamaz); kaçmaya yarayanlar hariç
            ItemStack yiyecek = e.getItem();
            if (yiyecek != null && yenebilir(yiyecek.getType())
                    && (e.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_AIR
                    || e.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK)) {
                e.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
                e.setUseItemInHand(org.bukkit.event.Event.Result.ALLOW);
                return;
            }
            e.setCancelled(true);
            return;
        }
        // Yemek yedirdikten hemen sonra yediren kişi aynı tıkla kendisi yemeye başlamasın
        Long yedirdi = sonYediren.get(e.getPlayer().getUniqueId());
        if (yedirdi != null && System.currentTimeMillis() - yedirdi < 400) {
            e.setCancelled(true);
            return;
        }
        // Hücreye koyma: polis, götürdüğü kişinin anahtarıyla Shift + bloğa sağ tık → mahkum o bloğa konur ve orada bekler
        if (e.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK && e.getPlayer().isSneaking()
                && items.turu(e.getItem()) == PolisItems.Tur.ANAHTAR && e.getClickedBlock() != null
                && e.getHand() == EquipmentSlot.HAND) {
            e.setCancelled(true);
            String id = items.anahtarKelepceId(e.getItem());
            for (Map.Entry<UUID, Kelepce> g : kelepceliler.entrySet()) {
                if (g.getValue().id().equals(id)) {
                    Player mahkum = Bukkit.getPlayer(g.getKey());
                    if (mahkum != null) yerlestir(e.getPlayer(), mahkum, e.getClickedBlock());
                    return;
                }
            }
            plugin.mesaj(e.getPlayer(), "Bu anahtarın kelepçesi şu an kimsede takılı değil.", NamedTextColor.YELLOW);
            return;
        }
        // Kayış çite bağlanmasın, anahtar (tuzak kancası) duvara takılmasın
        if (items.turu(e.getItem()) != null) {
            e.setCancelled(true);
        }
    }

    /**
     * Mahkumu tıklanan bloğun üstüne (ya da tıklanan boşluğa) koyar ve bekletir.
     * Hücre kapısında sıkışmadan içeri koymak için.
     */
    private void yerlestir(Player polis, Player mahkum, org.bukkit.block.Block tiklanan) {
        if (!polisMi(polis)) {
            plugin.mesaj(polis, "Mahkumu sadece polis yerleştirebilir.", NamedTextColor.RED);
            return;
        }
        if (!mahkum.getWorld().equals(polis.getWorld()) || mahkum.getLocation().distance(polis.getLocation()) > 16) {
            plugin.mesaj(polis, mahkum.getName() + " çok uzakta.", NamedTextColor.RED);
            return;
        }
        if (tiklanan.getLocation().add(0.5, 0.5, 0.5).distance(polis.getEyeLocation()) > 6) {
            plugin.mesaj(polis, "Çok uzağa koyamazsın (en fazla 6 blok).", NamedTextColor.RED);
            return;
        }
        // Tıklanan blok doluysa üstüne, boşsa (halı, ot vb.) kendisine koy
        org.bukkit.block.Block ayak = tiklanan.isPassable() ? tiklanan : tiklanan.getRelative(org.bukkit.block.BlockFace.UP);
        org.bukkit.block.Block bas = ayak.getRelative(org.bukkit.block.BlockFace.UP);
        org.bukkit.block.Block zemin = ayak.getRelative(org.bukkit.block.BlockFace.DOWN);
        if (!ayak.isPassable() || !bas.isPassable() || zemin.isPassable()) {
            plugin.mesaj(polis, "Oraya sığmaz. Altı dolu, üstünde 2 blok boşluk olan bir yere tıkla.", NamedTextColor.RED);
            return;
        }
        Location hedef = ayak.getLocation().add(0.5, 0, 0.5);
        hedef.setYaw(mahkum.getLocation().getYaw());
        hedef.setPitch(mahkum.getLocation().getPitch());
        Kelepce k = kelepceliler.get(mahkum.getUniqueId());
        kelepceliler.put(mahkum.getUniqueId(), new Kelepce(k.polis(), false, k.id(), hedef));
        sonHucre.put(mahkum.getUniqueId(), hedef.clone());
        mahkum.teleport(hedef);
        kaydet();
        kayit().log(polis.getName() + ", " + mahkum.getName() + " adlı mahkumu yerleştirdi (" + konum(hedef) + ")");
        plugin.mesaj(polis, mahkum.getName() + " yerleştirildi, orada bekliyor.", NamedTextColor.AQUA);
        plugin.mesaj(mahkum, polis.getName() + " seni buraya koydu.", NamedTextColor.YELLOW);
    }

    private final Map<UUID, Long> sonYedirme = new HashMap<>();
    private final Map<UUID, Long> sonYediren = new HashMap<>();

    /** Açlık ve doygunluk değerleri (vanilla). Listede olmayan yiyecekler için 4 / 2.4. */
    private static final Map<org.bukkit.Material, float[]> BESIN = new HashMap<>();

    static {
        String[][] tablo = {
                {"BREAD", "5", "6.0"}, {"COOKED_BEEF", "8", "12.8"}, {"COOKED_PORKCHOP", "8", "12.8"},
                {"COOKED_MUTTON", "6", "9.6"}, {"COOKED_CHICKEN", "6", "7.2"}, {"COOKED_RABBIT", "5", "6.0"},
                {"COOKED_COD", "5", "6.0"}, {"COOKED_SALMON", "6", "9.6"}, {"BAKED_POTATO", "5", "6.0"},
                {"GOLDEN_CARROT", "6", "14.4"}, {"PUMPKIN_PIE", "8", "4.8"}, {"APPLE", "4", "2.4"},
                {"CARROT", "3", "3.6"}, {"POTATO", "1", "0.6"}, {"BEETROOT", "1", "1.2"},
                {"MELON_SLICE", "2", "1.2"}, {"SWEET_BERRIES", "2", "0.4"}, {"GLOW_BERRIES", "2", "0.4"},
                {"COOKIE", "2", "0.4"}, {"DRIED_KELP", "1", "0.6"}, {"MUSHROOM_STEW", "6", "7.2"},
                {"RABBIT_STEW", "10", "12.0"}, {"BEETROOT_SOUP", "6", "7.2"}, {"BEEF", "3", "1.8"},
                {"PORKCHOP", "3", "1.8"}, {"CHICKEN", "2", "1.2"}, {"MUTTON", "2", "1.2"},
                {"COD", "2", "0.4"}, {"SALMON", "2", "0.4"}, {"GOLDEN_APPLE", "4", "9.6"},
                {"HONEY_BOTTLE", "6", "1.2"}, {"ROTTEN_FLESH", "4", "0.8"}, {"SPIDER_EYE", "2", "3.2"},
                {"POISONOUS_POTATO", "2", "1.2"}, {"PUFFERFISH", "1", "0.2"}, {"TROPICAL_FISH", "1", "0.2"},
                {"RABBIT", "3", "1.8"}, {"ENCHANTED_GOLDEN_APPLE", "4", "9.6"}, {"SUSPICIOUS_STEW", "6", "7.2"},
                {"CAKE", "2", "0.4"}, {"PUMPKIN_PIE", "8", "4.8"},
        };
        for (String[] t : tablo) {
            org.bukkit.Material m = org.bukkit.Material.matchMaterial(t[0]);
            if (m != null) BESIN.put(m, new float[]{Float.parseFloat(t[1]), Float.parseFloat(t[2])});
        }
    }

    private void yemekYedir(Player veren, Player ac, ItemStack yiyecek) {
        long simdi = System.currentTimeMillis();
        Long son = sonYedirme.get(ac.getUniqueId());
        if (son != null && simdi - son < 1500) return; // art arda tıklamayla israf olmasın
        sonYedirme.put(ac.getUniqueId(), simdi);
        sonYediren.put(veren.getUniqueId(), simdi);

        if (ac.getFoodLevel() >= 20) {
            plugin.mesaj(veren, ac.getName() + " tok, yemek istemiyor.", NamedTextColor.YELLOW);
            return;
        }
        org.bukkit.Material m = yiyecek.getType();
        float[] deger = BESIN.getOrDefault(m, new float[]{4f, 2.4f});
        int yeniAclik = Math.min(20, ac.getFoodLevel() + (int) deger[0]);
        ac.setFoodLevel(yeniAclik);
        ac.setSaturation(Math.min(yeniAclik, ac.getSaturation() + deger[1]));

        // Yiyeceği verenin elinden düş; çorba/bal ise kabı geri ver
        if (veren.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            int slot = veren.getInventory().getHeldItemSlot();
            slotEksilt(veren, slot);
            String ad = m.name();
            if (ad.endsWith("_STEW") || ad.endsWith("_SOUP")) ver(veren, new ItemStack(org.bukkit.Material.BOWL));
            if (m.name().equals("HONEY_BOTTLE")) ver(veren, new ItemStack(org.bukkit.Material.GLASS_BOTTLE));
        }
        yemekEtkisi(ac, yiyecek);
        ac.getWorld().playSound(ac.getLocation(), Sound.ENTITY_GENERIC_EAT, 1f, 1f);
        plugin.mesaj(veren, ac.getName() + " adlı oyuncuya yemek yedirdin.", NamedTextColor.GREEN);
        plugin.mesaj(ac, veren.getName() + " sana yemek yedirdi.", NamedTextColor.GREEN);
    }

    /** Yedirilen yiyeceğin vanilla etkileri: çürük et açlık, örümcek gözü zehir, altın elma yenilenme... */
    private static void yemekEtkisi(Player ac, ItemStack yiyecek) {
        java.util.concurrent.ThreadLocalRandom r = java.util.concurrent.ThreadLocalRandom.current();
        switch (yiyecek.getType().name()) {
            case "ROTTEN_FLESH" -> { if (r.nextDouble() < 0.8) etki(ac, PotionEffectType.HUNGER, 30, 0); }
            case "CHICKEN" -> { if (r.nextDouble() < 0.3) etki(ac, PotionEffectType.HUNGER, 30, 0); }
            case "SPIDER_EYE" -> etki(ac, PotionEffectType.POISON, 5, 0);
            case "POISONOUS_POTATO" -> { if (r.nextDouble() < 0.6) etki(ac, PotionEffectType.POISON, 5, 0); }
            case "PUFFERFISH" -> {
                etki(ac, PotionEffectType.HUNGER, 15, 2);
                etki(ac, PotionEffectType.NAUSEA, 15, 0);
                etki(ac, PotionEffectType.POISON, 60, 1);
            }
            case "GOLDEN_APPLE" -> {
                etki(ac, PotionEffectType.REGENERATION, 5, 1);
                etki(ac, PotionEffectType.ABSORPTION, 120, 0);
            }
            case "ENCHANTED_GOLDEN_APPLE" -> {
                etki(ac, PotionEffectType.REGENERATION, 20, 1);
                etki(ac, PotionEffectType.ABSORPTION, 120, 3);
                etki(ac, PotionEffectType.RESISTANCE, 300, 0);
                etki(ac, PotionEffectType.FIRE_RESISTANCE, 300, 0);
            }
            case "HONEY_BOTTLE" -> ac.removePotionEffect(PotionEffectType.POISON);
            case "SUSPICIOUS_STEW" -> {
                if (yiyecek.getItemMeta() instanceof org.bukkit.inventory.meta.SuspiciousStewMeta sm) {
                    for (PotionEffect pe : sm.getCustomEffects()) ac.addPotionEffect(pe);
                }
            }
            default -> { }
        }
    }

    private static void etki(Player p, PotionEffectType tur, int saniye, int seviye) {
        p.addPotionEffect(new PotionEffect(tur, saniye * 20, seviye));
    }

    /** Kelepçeliyken yenebilecekler: normal yiyecekler. Koro meyvesi (ışınlar) ve süt (yavaşlığı siler) hariç. */
    private static boolean yenebilir(org.bukkit.Material m) {
        return m.isEdible() && m != org.bukkit.Material.CHORUS_FRUIT;
    }

    // Yine de bir yolla yenmeye çalışılırsa: koro meyvesi ve süt kelepçeliyken tüketilemez
    @EventHandler(priority = EventPriority.LOWEST)
    public void onTuket(org.bukkit.event.player.PlayerItemConsumeEvent e) {
        if (!kelepceliMi(e.getPlayer())) return;
        org.bukkit.Material m = e.getItem().getType();
        if (m == org.bukkit.Material.CHORUS_FRUIT || m == org.bukkit.Material.MILK_BUCKET) {
            e.setCancelled(true);
            plugin.mesaj(e.getPlayer(), "Kelepçeliyken bunu kullanamazsın.", NamedTextColor.RED);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBreak(BlockBreakEvent e) {
        if (kelepceliMi(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlace(BlockPlaceEvent e) {
        if (kelepceliMi(e.getPlayer()) || items.turu(e.getItemInHand()) != null) e.setCancelled(true);
    }

    // Anahtar yanlışlıkla kelepçeliye atılırsa: kelepçeli anahtarı yerden alamaz
    @EventHandler(priority = EventPriority.LOWEST)
    public void onAl(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player p && kelepceliMi(p)
                && items.turu(e.getItem().getItemStack()) == PolisItems.Tur.ANAHTAR) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrop(PlayerDropItemEvent e) {
        if (kelepceliMi(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageByEntityEvent e) {
        Entity vuran = e.getDamager();
        if (vuran instanceof Projectile pr && pr.getShooter() instanceof Player atan) {
            vuran = atan;
        }
        if (vuran instanceof Player p && kelepceliMi(p)) {
            e.setCancelled(true);
        }
    }

    // Kelepçeli kendini öldüremesin (boğulma, lav, ateş, düşme, açlık, sıkışma...): çevreden hasar almaz.
    // Oyuncu ve yaratık hasarı, boşluk ve /kill normal işler.
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCevreHasari(org.bukkit.event.entity.EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p) || !kelepceliMi(p)) return;
        if (e instanceof EntityDamageByEntityEvent) return;
        switch (e.getCause()) {
            case VOID, KILL, WORLD_BORDER -> { return; }
            default -> { }
        }
        e.setCancelled(true);
        p.setFireTicks(0);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (!kelepceliMi(p)) return;
        String msg = e.getMessage();
        String komut = msg.length() > 1 ? msg.substring(1).split(" ")[0].toLowerCase(java.util.Locale.ROOT) : "";
        int iki = komut.indexOf(':');
        if (iki >= 0) komut = komut.substring(iki + 1);
        boolean izinli = SERBEST_KOMUTLAR.contains(komut);
        if (komut.equals("ceza")) {
            String[] parca = msg.trim().split("\\s+");
            izinli = parca.length >= 2 && parca[1].equalsIgnoreCase("liste");
        }
        if (!izinli) {
            e.setCancelled(true);
            plugin.mesaj(p, "Kelepçeliyken bu komutu kullanamazsın.", NamedTextColor.RED);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        rolYetkileri.remove(p.getUniqueId());
        bilekleriKaldir(p.getUniqueId());
        Kelepce cikan = kelepceliler.remove(p.getUniqueId());
        if (cikan != null) {
            cevrimdisiKelepceli.put(p.getUniqueId(), cikan.id());
            cevrimdisiPolis.put(p.getUniqueId(), cikan.polis());
            Location l = p.getLocation();
            plugin.kayit().log(p.getName() + " gözaltındayken oyundan çıktı (" + konum(l) + ")");
            plugin.kayit().say(p.getUniqueId(), p.getName(), Kayitlar.Alan.KACMA);
            for (Player o : Bukkit.getOnlinePlayers()) {
                if (!o.equals(p) && polisMi(o)) plugin.mesaj(o, p.getName() + " gözaltındayken oyundan çıktı! Girince geri gelecek.", NamedTextColor.RED);
            }
        }
        // Polis çıkarsa sürüklediği oyuncular olduğu yerde kelepçeli kalır
        for (Map.Entry<UUID, Kelepce> g : kelepceliler.entrySet()) {
            Kelepce v = g.getValue();
            if (v.surukleniyor() && v.polis().equals(p.getUniqueId())) {
                Player h = Bukkit.getPlayer(g.getKey());
                g.setValue(new Kelepce(v.polis(), false, v.id(), h != null ? h.getLocation() : null));
                if (h != null) plugin.mesaj(h, "Polis oyundan çıktı, olduğun yerde kelepçeli kaldın.", NamedTextColor.YELLOW);
            }
        }
        kaydet();
    }

    // Kelepçeliyken ölen: hücrede bekletiliyorsa konduğu yerde, götürülüyorsa polisin yanında doğar
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDogus(org.bukkit.event.player.PlayerRespawnEvent e) {
        Kelepce k = kelepceliler.get(e.getPlayer().getUniqueId());
        if (k == null) return;
        if (!k.surukleniyor() && k.capa() != null && k.capa().getWorld() != null) {
            e.setRespawnLocation(k.capa());
        } else if (k.surukleniyor()) {
            Player polis = Bukkit.getPlayer(k.polis());
            if (polis != null) e.setRespawnLocation(polis.getLocation());
        }
        plugin.mesaj(e.getPlayer(), "Hâlâ kelepçelisin.", NamedTextColor.RED);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        yetkileriGuncelle(p);
        sekmeIsmi(p);
        String id = cevrimdisiKelepceli.remove(p.getUniqueId());
        UUID takan = cevrimdisiPolis.remove(p.getUniqueId());
        if (id != null) {
            Player polis = takan != null ? Bukkit.getPlayer(takan) : null;
            Location hucre = sonHucre.get(p.getUniqueId());
            boolean hucreVar = hucre != null && hucre.getWorld() != null;
            UUID sahip = takan != null ? takan : p.getUniqueId();
            Location yer;
            switch (girisYeri(polis != null, hucreVar)) {
                case "polis" -> {
                    yer = polis.getLocation();
                    kelepceliler.put(p.getUniqueId(), new Kelepce(sahip, true, id, null));
                }
                case "hucre" -> {
                    yer = hucre.clone();
                    kelepceliler.put(p.getUniqueId(), new Kelepce(sahip, false, id, hucre.clone()));
                }
                default -> {
                    yer = p.getLocation();
                    kelepceliler.put(p.getUniqueId(), new Kelepce(sahip, false, id, yer));
                }
            }
            final Location hedefYer = yer;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (p.isOnline() && kelepceliMi(p)) p.teleport(hedefYer);
            });
            etkiVer(p);
            plugin.mesaj(p, "Hâlâ kelepçelisin. Sadece senin anahtarın açabilir.", NamedTextColor.RED);
            for (Player o : Bukkit.getOnlinePlayers()) {
                if (!o.equals(p) && polisMi(o)) plugin.mesaj(o, p.getName() + " geri geldi, gözaltında: " + konum(hedefYer), NamedTextColor.AQUA);
            }
            kaydet();
        }
    }

    // ------------------------------------------------------------------
    // Zamanlayıcı (her 5 tick): etkiler ve sürükleme
    // ------------------------------------------------------------------

    public void tik() {
        Iterator<Map.Entry<UUID, Kelepce>> it = kelepceliler.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Kelepce> giris = it.next();
            Player h = Bukkit.getPlayer(giris.getKey());
            if (h == null) {
                // normalde onQuit halleder; yine de kaybolmasın
                cevrimdisiKelepceli.put(giris.getKey(), giris.getValue().id());
                cevrimdisiPolis.put(giris.getKey(), giris.getValue().polis());
                bilekleriKaldir(giris.getKey());
                it.remove();
                continue;
            }
            etkiVer(h);
            anahtarlariDusur(h);
            animasyon(h, giris.getValue());
        }
    }

    // ------------------------------------------------------------------
    // Her tick: bilek halkaları ve sürükleme
    // Sürükleme ışınlama yerine hızla (velocity) yapılır: istemci hareketi kendisi
    // yumuşatır, başkalarına "pingli/takılıyor" gibi görünmez. Köşeye takılırsa
    // ya da çok uzaklaşırsa son çare olarak polisin izine ışınlanır.
    // ------------------------------------------------------------------

    private final Map<UUID, Location> oncekiYer = new HashMap<>();
    private final Map<UUID, Integer> takili = new HashMap<>();

    public void surukle() {
        double cekme = plugin.getConfig().getDouble("cekme-mesafesi", 3.0);
        double isinla = plugin.getConfig().getDouble("isinlama-mesafesi", 16.0);
        izKaydet();
        bilekler.keySet().removeIf(id -> {
            if (kelepceliler.containsKey(id)) return false;
            org.bukkit.entity.BlockDisplay[] d = bilekler.get(id);
            for (org.bukkit.entity.BlockDisplay x : d) if (x != null) x.remove();
            return true;
        });
        oncekiYer.keySet().retainAll(kelepceliler.keySet());
        takili.keySet().retainAll(kelepceliler.keySet());

        for (Map.Entry<UUID, Kelepce> giris : kelepceliler.entrySet()) {
            Player h = Bukkit.getPlayer(giris.getKey());
            if (h == null) continue;
            bilekleriGuncelle(h);
            Kelepce k = giris.getValue();
            UUID id = h.getUniqueId();
            Location hl = h.getLocation();
            Location once = oncekiYer.put(id, hl.clone());
            if (!k.surukleniyor()) { takili.remove(id); continue; } // gözaltında: kendisi çok yavaş yürüyebilir
            Player polis = Bukkit.getPlayer(k.polis());
            if (polis == null) continue;
            Location pl = polis.getLocation();
            if (!hl.getWorld().equals(pl.getWorld())) {
                h.teleport(yuzunuDondur(pl.clone(), hl));
                takili.remove(id);
                continue;
            }
            double mesafe = hl.distance(pl);
            double hiz = KelepceKurali.cekmeHizi(mesafe, cekme);
            // Çekilmesi gerekirken neredeyse hiç kımıldamadıysa takılmıştır
            boolean kimildadi = once == null || !once.getWorld().equals(hl.getWorld()) || once.distanceSquared(hl) > 0.0025;
            int t = (hiz > 0 && !kimildadi && mesafe > cekme + 0.5) ? takili.getOrDefault(id, 0) + 1 : 0;
            takili.put(id, t);
            if (KelepceKurali.isinlanmali(mesafe, isinla, t)) {
                h.teleport(yuzunuDondur(izNoktasi(polis, cekme), hl));
                takili.remove(id);
                continue;
            }
            if (hiz <= 0) continue;
            boolean suda = h.isInWater();
            if (!h.isOnGround() && !suda) continue; // havada: istemci zıplamanın momentumunu kendisi taşır
            Location hedef = izNoktasi(polis, cekme);
            org.bukkit.util.Vector yon = hedef.toVector().subtract(hl.toVector()).setY(0);
            if (yon.lengthSquared() < 0.09) yon = pl.toVector().subtract(hl.toVector()).setY(0);
            if (yon.lengthSquared() < 1e-4) continue;
            yon.normalize().multiply(hiz);
            double dy = hedef.getY() - hl.getY();
            if (suda) yon.setY(dy > 0.2 ? 0.12 : 0.02);
            else yon.setY(dy > 0.6 ? 0.42 : 0); // bir blok yukarı: zıpla
            h.setVelocity(yon);
        }
    }

    /** Sürükleyen polislerin son konumları (iz). Mahkum bu izi takip eder. */
    private final Map<UUID, java.util.ArrayDeque<Location>> izler = new HashMap<>();

    private void izKaydet() {
        java.util.Set<UUID> surukleyenler = new java.util.HashSet<>();
        for (Kelepce k : kelepceliler.values()) if (k.surukleniyor()) surukleyenler.add(k.polis());
        izler.keySet().retainAll(surukleyenler);
        for (UUID id : surukleyenler) {
            Player polis = Bukkit.getPlayer(id);
            if (polis == null) continue;
            java.util.ArrayDeque<Location> iz = izler.computeIfAbsent(id, x -> new java.util.ArrayDeque<>());
            Location simdi = polis.getLocation();
            Location son = iz.peekLast();
            if (son == null || !son.getWorld().equals(simdi.getWorld()) || son.distanceSquared(simdi) > 0.25) {
                // havadaki noktalara (zıplama, düşme) ışınlama; ama suda yüzerken de iz bırak
                if (polis.isOnGround() || polis.isInWater()) iz.addLast(simdi);
                while (iz.size() > 30) iz.pollFirst();
            }
        }
    }

    /** Polisin izinde, polisten yaklaşık "mesafe - 1" blok gerideki en yeni nokta; yoksa polisin kendi yeri. */
    private Location izNoktasi(Player polis, double mesafe) {
        Location pl = polis.getLocation();
        java.util.ArrayDeque<Location> iz = izler.get(polis.getUniqueId());
        if (iz != null) {
            double hedefUzaklik = Math.max(1.0, mesafe - 1.0);
            java.util.Iterator<Location> geri = iz.descendingIterator();
            while (geri.hasNext()) {
                Location n = geri.next();
                if (!n.getWorld().equals(pl.getWorld())) break;
                if (n.distance(pl) >= hedefUzaklik) return n.clone();
            }
        }
        return pl.clone();
    }

    /** Işınlanan mahkum polise doğru baksın. */
    private static Location yuzunuDondur(Location hedef, Location eski) {
        hedef.setYaw(eski.getYaw());
        hedef.setPitch(eski.getPitch());
        return hedef;
    }

    /** Kelepçelinin üstünde (nasıl olursa olsun) kelepçe anahtarı varsa ayağının dibine bırakılır. */
    private void anahtarlariDusur(Player h) {
        ItemStack[] depo = h.getInventory().getStorageContents();
        boolean degisti = false;
        for (int i = 0; i < depo.length; i++) {
            if (items.turu(depo[i]) != PolisItems.Tur.ANAHTAR) continue;
            org.bukkit.entity.Item yer = h.getWorld().dropItem(h.getLocation(), depo[i]);
            yer.setPickupDelay(20);
            depo[i] = null;
            degisti = true;
        }
        if (degisti) {
            h.getInventory().setStorageContents(depo);
            plugin.mesaj(h, "Kelepçeliyken anahtar tutamazsın, yere düştü.", NamedTextColor.YELLOW);
        }
        ItemStack sol = h.getInventory().getItemInOffHand();
        if (items.turu(sol) == PolisItems.Tur.ANAHTAR) {
            h.getWorld().dropItem(h.getLocation(), sol).setPickupDelay(20);
            h.getInventory().setItemInOffHand(null);
        }
    }

    // ------------------------------------------------------------------
    // Komutlar: /polis, /serbest
    // ------------------------------------------------------------------

    @Override
    public boolean onCommand(CommandSender s, Command command, String label, String[] args) {
        switch (command.getName().toLowerCase(java.util.Locale.ROOT)) {
            case "polis" -> polisKomut(s, args);
            case "baskan" -> baskanKomut(s, args);
            case "serbest" -> serbestKomut(s, args);
            case "kelepce", "anahtar", "ara" -> etkilesimKomut(command.getName().toLowerCase(java.util.Locale.ROOT), s, args);
            case "koy" -> koyKomut(s, args);
            default -> { return false; }
        }
        return true;
    }

    private void polisKomut(CommandSender s, String[] args) {
        String alt = args.length > 0 ? args[0].toLowerCase(java.util.Locale.ROOT) : "yardim";
        switch (alt) {
            case "log", "kayit", "kayıt" -> {
                if (!(s.hasPermission("poliskelepce.admin") || (s instanceof Player lp && polisMi(lp)))) {
                    plugin.mesaj(s, "Polis kaydını sadece polis ve başkan görebilir.", NamedTextColor.RED);
                    return;
                }
                kayit().logGoster(s, args);
            }
            case "ekle" -> {
                if (!admin(s)) return;
                if (args.length < 2) { plugin.mesaj(s, "Kullanım: /polis ekle <oyuncu>", NamedTextColor.YELLOW); return; }
                for (Player p : Secici.coklu(s, args[1], false)) {
                    polisler.put(p.getUniqueId(), p.getName());
                    yetkileriGuncelle(p);
                    sekmeIsmi(p);
                    plugin.mesaj(s, p.getName() + " artık polis.", NamedTextColor.GREEN);
                    plugin.mesaj(p, "Polis oldun! Kelepçe craftlayabilirsin. Ayrıntılar için: /polis", NamedTextColor.AQUA);
                    kayit().log(s.getName() + ", " + p.getName() + " adlı oyuncuyu polis yaptı");
                }
                kaydet();
            }
            case "cikar", "çıkar" -> {
                if (!admin(s)) return;
                if (args.length < 2) { plugin.mesaj(s, "Kullanım: /polis cikar <oyuncu>", NamedTextColor.YELLOW); return; }
                List<UUID> bulunanlar = new ArrayList<>();
                if (args[1].startsWith("@")) {
                    for (Player p : Secici.coklu(s, args[1], false)) {
                        if (polisler.containsKey(p.getUniqueId())) bulunanlar.add(p.getUniqueId());
                    }
                } else {
                    // oyunda olmayan polis de adıyla çıkarılabilsin
                    for (Map.Entry<UUID, String> g : polisler.entrySet()) {
                        if (g.getValue().equalsIgnoreCase(args[1])) { bulunanlar.add(g.getKey()); break; }
                    }
                }
                if (bulunanlar.isEmpty()) { plugin.mesaj(s, "Çıkarılacak polis bulunamadı: " + args[1], NamedTextColor.RED); return; }
                for (UUID id : bulunanlar) {
                    String isim = polisler.remove(id);
                    Player p = Bukkit.getPlayer(id);
                    if (p != null) {
                        yetkileriGuncelle(p);
                        sekmeIsmi(p);
                        plugin.mesaj(p, "Artık polis değilsin.", NamedTextColor.YELLOW);
                    }
                    plugin.mesaj(s, isim + " polislikten çıkarıldı.", NamedTextColor.GREEN);
                    kayit().log(s.getName() + ", " + isim + " adlı oyuncuyu polislikten çıkardı");
                }
                kaydet();
            }
            case "yap" -> {
                if (!(s instanceof Player p)) return;
                if (!polisMi(p)) { plugin.mesaj(p, "Kelepçeyi sadece polisler yapabilir.", NamedTextColor.RED); return; }
                int istenen = 1;
                if (args.length >= 2) {
                    try { istenen = Math.max(1, Math.min(16, Integer.parseInt(args[1]))); } catch (NumberFormatException ignored) { }
                }
                int n = Math.min(istenen, Math.min(duzSay(p, org.bukkit.Material.IRON_INGOT) / 2, duzSay(p, org.bukkit.Material.IRON_NUGGET)));
                if (n == 0) { plugin.mesaj(p, "Her kelepçe için 2 demir külçe + 1 demir parçacığı lazım.", NamedTextColor.RED); return; }
                duzAl(p, org.bukkit.Material.IRON_INGOT, n * 2);
                duzAl(p, org.bukkit.Material.IRON_NUGGET, n);
                ver(p, items.olustur(PolisItems.Tur.KELEPCE, n));
                plugin.mesaj(p, n + " kelepçe yapıldı.", NamedTextColor.GREEN);
            }
            case "anahtar" -> {
                if (!admin(s)) return;
                if (args.length < 2) { plugin.mesaj(s, "Kullanım: /polis anahtar <kelepçeli> [kime]", NamedTextColor.YELLOW); return; }
                List<Player> kelepceliOyuncular = Secici.coklu(s, args[1], false);
                if (kelepceliOyuncular.isEmpty()) return;
                Player kime;
                if (args.length >= 3) {
                    kime = Secici.tekli(s, args[2]);
                    if (kime == null) return;
                } else if (s instanceof Player ben) {
                    kime = ben;
                } else {
                    plugin.mesaj(s, "Konsoldan kullanırken kime verileceğini yaz: /polis anahtar <kelepçeli> <kime>", NamedTextColor.YELLOW);
                    return;
                }
                for (Player h : kelepceliOyuncular) {
                    Kelepce k = kelepceliler.get(h.getUniqueId());
                    if (k == null) {
                        if (!args[1].startsWith("@")) plugin.mesaj(s, h.getName() + " kelepçeli değil.", NamedTextColor.YELLOW);
                        continue;
                    }
                    ver(kime, items.anahtar(h.getName(), k.id()));
                    plugin.mesaj(s, h.getName() + " adlı oyuncunun yedek anahtarı " + kime.getName() + " adlı oyuncuya verildi.", NamedTextColor.GREEN);
                    kayit().log(s.getName() + ", " + h.getName() + " adlı oyuncunun yedek anahtarını " + kime.getName() + " adlı oyuncuya verdi");
                }
            }
            case "liste" -> {
                if (baskan != null) plugin.mesaj(s, "Belediye başkanı: " + baskanIsim, NamedTextColor.GOLD);
                if (polisler.isEmpty()) { plugin.mesaj(s, "Henüz polis yok.", NamedTextColor.GRAY); return; }
                plugin.mesaj(s, "Polisler: " + String.join(", ", polisler.values()), NamedTextColor.AQUA);
            }
            case "ver" -> {
                if (!admin(s)) return;
                if (args.length < 2) { plugin.mesaj(s, "Kullanım: /polis ver <oyuncu> [adet]", NamedTextColor.YELLOW); return; }
                int adet = 1;
                if (args.length >= 3) {
                    try { adet = Math.max(1, Math.min(16, Integer.parseInt(args[2]))); } catch (NumberFormatException ignored) { }
                }
                for (Player p : Secici.coklu(s, args[1], false)) {
                    ver(p, items.olustur(PolisItems.Tur.KELEPCE, adet));
                    plugin.mesaj(s, p.getName() + " adlı oyuncuya " + adet + " kelepçe verildi.", NamedTextColor.GREEN);
                }
            }
            default -> {
                plugin.mesaj(s, "Polis sistemi:", NamedTextColor.AQUA);
                satir(s, "Kelepçe tarifi (sadece polis): demir külçe + demir parçacığı + demir külçe, yan yana");
                satir(s, "  ya da çalışma masasız: /polis yap [adet]");
                satir(s, "Kelepçeyle oyuncuya sağ tık: tak. Kelepçe onun bileğine geçer, anahtarı sana gelir");
                satir(s, "Anahtarla sağ tık: bırak (olduğu yerde kelepçeli kalır) / tekrar götür");
                satir(s, "Shift + anahtarla sağ tık: kelepçeyi aç, kelepçe sana geri gelir");
                satir(s, "Boş elle (ya da başka eşyayla) Shift + sağ tık: üst ara (sadece bakılır)");
                satir(s, "Elinde yiyecekle kelepçeliye sağ tık: yemek yedir (herkes yapabilir)");
                satir(s, "Hücreye koyma: anahtarla Shift + bloğa sağ tık, ya da baktığın yere /koy <oyuncu>");
                satir(s, "Anahtar kaybolursa (yanarsa, lavda erirse...): başkan/op /polis anahtar <kelepçeli> ya da /serbest");
                satir(s, "Kelepçeliyken ölen: hücredeyse konduğu yerde, götürülüyorsa polisin yanında doğar");
                satir(s, "Anahtar çalınırsa: polis olmayan biri anahtarla sağ tıklayınca kelepçe açılır");
                satir(s, "Kelepçeli anahtar tutamaz: ona atılan anahtar yerde kalır, sen alırsın");
                satir(s, "Oyuncu yerine @a, @p, @r, @s de yazabilirsin (örn: /kelepce @p = en yakındaki)");
                satir(s, "Dokunmak yerine komutla (Bedrock için): /kelepce <oyuncu>, /anahtar <oyuncu>, /ara <oyuncu>");
                satir(s, "/ceza <oyuncu> <miktar> <eşya> [sebep] - ceza / vergi yaz. Ayrıntı: /ceza");
                satir(s, "/polis liste - polisleri gösterir, /baskan - belediye başkanını gösterir");
                satir(s, "/polis log [oyuncu] [sayfa] - (polis) kim kimi kelepçeledi, ceza yazdı, ödedi...");
                satir(s, "/sicil <oyuncu> - kaç kez gözaltı, kaç ceza, kaçı geç ödendi");
                satir(s, "/siren - kırmızı-mavi ışık ve siren");
                satir(s, "/pano kur|kaldir - karakol panosu (borçlular, gözaltındakiler)");
                satir(s, "Doktor ve yaralı sistemi: /doktor");
                if (s.hasPermission("poliskelepce.admin")) {
                    satir(s, "/polis ekle|cikar <oyuncu> - (op) polis atar / alır");
                    satir(s, "/polis ver <oyuncu> [adet] - (op) kelepçe verir");
                    satir(s, "/polis anahtar <kelepçeli> [kime] - (op/başkan) o kelepçenin yedek anahtarını verir");
                    satir(s, "/serbest <oyuncu> - (op) kelepçeyi anahtarsız açar");
                }
            }
        }
    }

    private void baskanKomut(CommandSender s, String[] args) {
        String alt = args.length > 0 ? args[0].toLowerCase(java.util.Locale.ROOT) : "";
        switch (alt) {
            case "ata" -> {
                if (!s.hasPermission("poliskelepce.baskan-ata")) {
                    plugin.mesaj(s, "Belediye başkanını sadece op atayabilir.", NamedTextColor.RED);
                    return;
                }
                if (args.length < 2) { plugin.mesaj(s, "Kullanım: /baskan ata <oyuncu>", NamedTextColor.YELLOW); return; }
                List<Player> adaylar = Secici.coklu(s, args[1], false);
                if (adaylar.isEmpty()) return;
                if (adaylar.size() > 1) { plugin.mesaj(s, "Başkan tek kişi olabilir; seçici " + adaylar.size() + " oyuncuya uydu.", NamedTextColor.RED); return; }
                Player p = adaylar.get(0);
                if (baskan != null) {
                    UUID eski = baskan;
                    baskan = null;
                    yetkiYenile(eski);
                    Player ep = Bukkit.getPlayer(eski);
                    if (ep != null) sekmeIsmi(ep);
                }
                baskan = p.getUniqueId();
                baskanIsim = p.getName();
                yetkileriGuncelle(p);
                sekmeIsmi(p);
                kaydet();
                Bukkit.broadcast(Component.text("[Belediye] ", NamedTextColor.GOLD)
                        .append(Component.text(p.getName() + " yeni belediye başkanı oldu!", NamedTextColor.WHITE)));
                plugin.mesaj(p, "Artık belediye başkanısın: polis yetkileri + polis atama, /serbest, /cay ver, her cezayı iptal.", NamedTextColor.GOLD);
                kayit().log(s.getName() + ", " + p.getName() + " adlı oyuncuyu belediye başkanı yaptı");
            }
            case "kaldir", "kaldır" -> {
                if (!s.hasPermission("poliskelepce.baskan-ata")) {
                    plugin.mesaj(s, "Belediye başkanını sadece op görevden alabilir.", NamedTextColor.RED);
                    return;
                }
                if (baskan == null) { plugin.mesaj(s, "Şu an belediye başkanı yok.", NamedTextColor.GRAY); return; }
                UUID eski = baskan;
                String eskiIsim = baskanIsim;
                baskan = null;
                baskanIsim = null;
                yetkiYenile(eski);
                Player ep = Bukkit.getPlayer(eski);
                if (ep != null) sekmeIsmi(ep);
                kaydet();
                plugin.mesaj(s, eskiIsim + " görevden alındı.", NamedTextColor.GREEN);
                kayit().log(s.getName() + ", belediye başkanı " + eskiIsim + " adlı oyuncuyu görevden aldı");
            }
            default -> {
                if (baskan == null) {
                    plugin.mesaj(s, "Şu an belediye başkanı yok.", NamedTextColor.GRAY);
                } else {
                    plugin.mesaj(s, "Belediye başkanı: " + baskanIsim, NamedTextColor.GOLD);
                }
                if (s.hasPermission("poliskelepce.baskan-ata")) {
                    satir(s, "/baskan ata <oyuncu> - (op) başkan atar (eskisinin yerine)");
                    satir(s, "/baskan kaldir - (op) başkanı görevden alır");
                }
            }
        }
    }

    /** /koy <oyuncu>: baktığın bloğa mahkumu koy (Bedrock ve komutla kullanım için). */
    private void koyKomut(CommandSender s, String[] args) {
        if (!(s instanceof Player p)) return;
        if (args.length < 1) {
            plugin.mesaj(p, "Kullanım: /koy <oyuncu>  (koymak istediğin yere bakarak)", NamedTextColor.YELLOW);
            return;
        }
        Player h = Secici.tekli(p, args[0]);
        if (h == null) return;
        Kelepce k = kelepceliler.get(h.getUniqueId());
        if (k == null) { plugin.mesaj(p, h.getName() + " kelepçeli değil.", NamedTextColor.YELLOW); return; }
        boolean takan = k.polis().equals(p.getUniqueId());
        boolean anahtarVar = slotBul(p, is -> k.id().equals(items.anahtarKelepceId(is))) >= 0;
        if (!takan && !anahtarVar) {
            plugin.mesaj(p, "Bu mahkumun anahtarı sende değil.", NamedTextColor.RED);
            return;
        }
        org.bukkit.block.Block bakilan = p.getTargetBlockExact(6);
        if (bakilan == null) { plugin.mesaj(p, "Koymak istediğin yere bak (en fazla 6 blok).", NamedTextColor.RED); return; }
        yerlestir(p, h, bakilan);
    }

    // Sağ tık yerine komut (Bedrock'ta oyuncuya dokunma çalışmazsa)
    private void etkilesimKomut(String komut, CommandSender s, String[] args) {
        if (!(s instanceof Player p)) return;
        if (args.length < 1) {
            plugin.mesaj(p, "Kullanım: /" + komut + " <oyuncu>", NamedTextColor.YELLOW);
            return;
        }
        Player h = yakindakiHedef(p, args[0]);
        if (h == null) return;
        switch (komut) {
            case "kelepce" -> {
                if (!polisMi(p)) { plugin.mesaj(p, "Kelepçeyi sadece polisler kullanabilir.", NamedTextColor.RED); return; }
                int slot = kelepceliMi(h) ? -1 : slotBul(p, is -> items.turu(is) == PolisItems.Tur.KELEPCE);
                kelepceyle(p, h, slot);
            }
            case "anahtar" -> {
                Kelepce k = kelepceliler.get(h.getUniqueId());
                if (k == null) { plugin.mesaj(p, h.getName() + " kelepçeli değil.", NamedTextColor.YELLOW); return; }
                int slot = slotBul(p, is -> k.id().equals(items.anahtarKelepceId(is)));
                if (slot < 0) { plugin.mesaj(p, "Üstünde " + h.getName() + " adlı oyuncunun kelepçe anahtarı yok.", NamedTextColor.RED); return; }
                anahtarla(p, h, slot);
            }
            case "ara" -> {
                if (!polisMi(p)) { plugin.mesaj(p, "Üst aramayı sadece polisler yapabilir.", NamedTextColor.RED); return; }
                if (!kelepceliMi(h)) { plugin.mesaj(p, "Üst arama için önce kelepçelemelisin.", NamedTextColor.RED); return; }
                ustAra(p, h);
            }
            default -> { }
        }
    }

    private void serbestKomut(CommandSender s, String[] args) {
        if (!admin(s)) return;
        if (args.length < 1) { plugin.mesaj(s, "Kullanım: /serbest <oyuncu>", NamedTextColor.YELLOW); return; }
        for (Player h : Secici.coklu(s, args[0], false)) {
            if (kelepceliMi(h)) {
                kelepceCikar(h, "Kelepçen çıkarıldı.");
                plugin.mesaj(s, h.getName() + " serbest bırakıldı. O kelepçenin anahtarı artık işe yaramaz.", NamedTextColor.GREEN);
                kayit().log(s.getName() + ", " + h.getName() + " adlı oyuncuyu anahtarsız serbest bıraktı (/serbest)");
            } else if (!args[0].startsWith("@")) {
                plugin.mesaj(s, h.getName() + " kelepçeli değil.", NamedTextColor.YELLOW);
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command command, String alias, String[] args) {
        List<String> sonuc = new ArrayList<>();
        String ad = command.getName().toLowerCase(java.util.Locale.ROOT);
        if (ad.equals("polis")) {
            if (args.length == 1) eslesen(sonuc, args[0], List.of("liste", "log", "yap", "ekle", "cikar", "ver", "anahtar"));
            else if ((args.length == 2 || args.length == 3) && args[0].equalsIgnoreCase("anahtar")) eslesen(sonuc, args[args.length - 1], Secici.oneriler());
            else if (args.length == 2 && (args[0].equalsIgnoreCase("ekle") || args[0].equalsIgnoreCase("ver"))) eslesen(sonuc, args[1], Secici.oneriler());
            else if (args.length == 2 && args[0].equalsIgnoreCase("cikar")) {
                List<String> l = new ArrayList<>(List.of("@a", "@p", "@r"));
                l.addAll(polisler.values());
                eslesen(sonuc, args[1], l);
            }
        } else if (ad.equals("baskan")) {
            if (args.length == 1) eslesen(sonuc, args[0], List.of("ata", "kaldir"));
            else if (args.length == 2 && args[0].equalsIgnoreCase("ata")) eslesen(sonuc, args[1], Secici.oneriler());
        } else if ((ad.equals("kelepce") || ad.equals("anahtar") || ad.equals("ara") || ad.equals("koy")) && args.length == 1) {
            eslesen(sonuc, args[0], Secici.oneriler());
        } else if (ad.equals("serbest") && args.length == 1) {
            List<String> kelepceli = new ArrayList<>(List.of("@a", "@p", "@r"));
            for (UUID id : kelepceliler.keySet()) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) kelepceli.add(p.getName());
            }
            eslesen(sonuc, args[0], kelepceli);
        }
        return sonuc;
    }

    // ------------------------------------------------------------------
    // Yardımcılar
    // ------------------------------------------------------------------

    private boolean admin(CommandSender s) {
        if (s.hasPermission("poliskelepce.admin")) return true;
        plugin.mesaj(s, "Bu komut için yetkin yok.", NamedTextColor.RED);
        return false;
    }

    public void sekmeIsmi(Player p) {
        CezaSistemi.BorcOzet borc = ceza != null ? ceza.borcOzet(p.getUniqueId()) : null;
        boolean borclu = borc != null;
        Component isim = Component.text(p.getName(), borclu && !borc.vergi() ? NamedTextColor.RED : NamedTextColor.WHITE);
        if (borclu) {
            // Son ödemeye kalan süre (yazılalı geçen süre değil); vergi sarı, ceza kırmızı
            long kalan = borc.enYakinSon() == Long.MAX_VALUE ? -1 : borc.enYakinSon() - System.currentTimeMillis();
            NamedTextColor renk = borc.gec() ? NamedTextColor.DARK_RED : (borc.vergi() ? NamedTextColor.YELLOW : NamedTextColor.RED);
            isim = isim.append(Component.text(CezaSistemi.sekmeEki(borc.vergi(), borc.gec(), kalan), renk));
        }
        String rol = plugin.etiket() != null ? plugin.etiket().rol(p) : null;
        if (rol != null) {
            p.playerListName(Component.text(Etiket.rolOneki(rol, plugin.etiket().simgeli()), Etiket.rolRengi(rol)).append(isim));
        } else if (borclu) {
            p.playerListName(isim);
        } else {
            p.playerListName(null);
        }
    }

    private static void slotEksilt(Player p, int slot) {
        if (p.getGameMode() == org.bukkit.GameMode.CREATIVE) return;
        ItemStack is = p.getInventory().getItem(slot);
        if (is == null) return;
        is.setAmount(is.getAmount() - 1);
        p.getInventory().setItem(slot, is.getAmount() > 0 ? is : null);
    }

    /** Envanterde koşula uyan ilk eşyanın yuvası, yoksa -1. */
    private static int slotBul(Player p, java.util.function.Predicate<ItemStack> kosul) {
        ItemStack[] depo = p.getInventory().getStorageContents();
        for (int i = 0; i < depo.length; i++) {
            if (depo[i] != null && kosul.test(depo[i])) return i;
        }
        return -1;
    }

    /** Komutla etkileşimde hedef oyunda ve yakında mı? */
    private Player yakindakiHedef(Player p, String ad) {
        Player h = Secici.tekli(p, ad);
        if (h == null) return null;
        if (h.equals(p)) {
            plugin.mesaj(p, "Bunu kendine yapamazsın.", NamedTextColor.RED);
            return null;
        }
        if (!h.getWorld().equals(p.getWorld()) || h.getLocation().distance(p.getLocation()) > 4) {
            plugin.mesaj(p, h.getName() + " yakınında olmalı (4 blok).", NamedTextColor.RED);
            return null;
        }
        return h;
    }

    private static int duzSay(Player p, org.bukkit.Material m) {
        int t = 0;
        for (ItemStack is : p.getInventory().getStorageContents()) if (is != null && is.getType() == m && !is.hasItemMeta()) t += is.getAmount();
        return t;
    }

    private static void duzAl(Player p, org.bukkit.Material m, int miktar) {
        ItemStack[] icerik = p.getInventory().getStorageContents();
        for (int i = 0; i < icerik.length && miktar > 0; i++) {
            ItemStack is = icerik[i];
            if (is == null || is.getType() != m || is.hasItemMeta()) continue;
            int al = Math.min(miktar, is.getAmount());
            is.setAmount(is.getAmount() - al);
            if (is.getAmount() <= 0) icerik[i] = null;
            miktar -= al;
        }
        p.getInventory().setStorageContents(icerik);
    }

    static void ver(Player p, ItemStack item) {
        for (ItemStack artan : p.getInventory().addItem(item).values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), artan);
        }
    }

    private static String konum(Location l) {
        return (l.getWorld() != null ? l.getWorld().getName() + " " : "") + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ();
    }

    private static void satir(CommandSender s, String metin) {
        s.sendMessage(Component.text("  " + metin, NamedTextColor.GRAY));
    }

    private static void eslesen(List<String> hedef, String yazilan, List<String> secenekler) {
        String y = yazilan.toLowerCase(java.util.Locale.ROOT);
        for (String s : secenekler) if (s.toLowerCase(java.util.Locale.ROOT).startsWith(y)) hedef.add(s);
    }

    // ------------------------------------------------------------------
    // Kayıt
    // ------------------------------------------------------------------

    public void yukle() {
        if (!dosya.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(dosya);
        ConfigurationSection ps = yml.getConfigurationSection("polisler");
        if (ps != null) {
            for (String k : ps.getKeys(false)) {
                try {
                    polisler.put(UUID.fromString(k), ps.getString(k, "?"));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        String b = yml.getString("baskan.uuid");
        if (b != null && !b.isEmpty()) {
            try {
                baskan = UUID.fromString(b);
                baskanIsim = yml.getString("baskan.isim", "?");
            } catch (IllegalArgumentException ignored) {
            }
        }
        ConfigurationSection hs = yml.getConfigurationSection("son-hucre");
        if (hs != null) {
            for (String k : hs.getKeys(false)) {
                try {
                    String y = "son-hucre." + k + ".";
                    World w = Bukkit.getWorld(yml.getString(y + "dunya", ""));
                    if (w == null) continue;
                    sonHucre.put(UUID.fromString(k), new Location(w, yml.getDouble(y + "x", 0), yml.getDouble(y + "y", 0),
                            yml.getDouble(y + "z", 0), (float) yml.getDouble(y + "yaw", 0), 0f));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        ConfigurationSection ks = yml.getConfigurationSection("kelepceliler");
        if (ks != null) {
            for (String k : ks.getKeys(false)) {
                try {
                    cevrimdisiKelepceli.put(UUID.fromString(k), ks.getString(k, "eski"));
                    String pol = yml.getString("kelepce-polis." + k);
                    if (pol != null && !pol.isEmpty()) cevrimdisiPolis.put(UUID.fromString(k), UUID.fromString(pol));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    public void kaydet() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Map.Entry<UUID, String> g : polisler.entrySet()) yml.set("polisler." + g.getKey(), g.getValue());
        if (baskan != null) {
            yml.set("baskan.uuid", baskan.toString());
            yml.set("baskan.isim", baskanIsim);
        }
        for (Map.Entry<UUID, String> g : cevrimdisiKelepceli.entrySet()) yml.set("kelepceliler." + g.getKey(), g.getValue());
        for (Map.Entry<UUID, Kelepce> g : kelepceliler.entrySet()) yml.set("kelepceliler." + g.getKey(), g.getValue().id());
        for (Map.Entry<UUID, UUID> g : cevrimdisiPolis.entrySet()) yml.set("kelepce-polis." + g.getKey(), g.getValue().toString());
        for (Map.Entry<UUID, Location> g : sonHucre.entrySet()) {
            Location l = g.getValue();
            if (l.getWorld() == null) continue;
            String y = "son-hucre." + g.getKey() + ".";
            yml.set(y + "dunya", l.getWorld().getName());
            yml.set(y + "x", l.getX());
            yml.set(y + "y", l.getY());
            yml.set(y + "z", l.getZ());
            yml.set(y + "yaw", (double) l.getYaw());
        }
        for (Map.Entry<UUID, Kelepce> g : kelepceliler.entrySet()) yml.set("kelepce-polis." + g.getKey(), g.getValue().polis().toString());
        try {
            if (!dosya.getParentFile().exists()) dosya.getParentFile().mkdirs();
            yml.save(dosya);
        } catch (IOException e) {
            plugin.getLogger().warning("veri.yml kaydedilemedi: " + e.getMessage());
        }
    }

    /** Plugin kapanırken kelepçelileri kaydet, etkileri kaldır. */
    public void kapat() {
        for (UUID id : new java.util.ArrayList<>(bilekler.keySet())) bilekleriKaldir(id);
        for (Map.Entry<UUID, Kelepce> g : kelepceliler.entrySet()) {
            cevrimdisiKelepceli.put(g.getKey(), g.getValue().id());
            cevrimdisiPolis.put(g.getKey(), g.getValue().polis());
            Player p = Bukkit.getPlayer(g.getKey());
            if (p != null) etkileriSil(p);
        }
        kelepceliler.clear();
        for (Map.Entry<UUID, PermissionAttachment> g : new ArrayList<>(rolYetkileri.entrySet())) {
            Player p = Bukkit.getPlayer(g.getKey());
            if (p != null) p.removeAttachment(g.getValue());
        }
        rolYetkileri.clear();
        kaydet();
    }

    /** Restart sonrası oyunda olanlardan kelepçeli kalmış olanları geri kelepçele. */
    public void acilistaGeriYukle() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            yetkileriGuncelle(p);
            String id = cevrimdisiKelepceli.remove(p.getUniqueId());
            UUID takan = cevrimdisiPolis.remove(p.getUniqueId());
            if (id != null) {
                kelepceliler.put(p.getUniqueId(), new Kelepce(takan != null ? takan : p.getUniqueId(), false, id, p.getLocation()));
            }
            sekmeIsmi(p);
        }
    }
}
