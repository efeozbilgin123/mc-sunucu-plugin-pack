package tr.efe.bekci;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * SQLite kayıt deposu. Bütün SQL tek bir arka plan iş parçacığında çalışır; sunucu donmaz.
 * Eklemeler kuyrukta birikir, saniyede bir ya da 200 kayıtta toplu yazılır.
 */
public final class Veritabani {

    private final Logger log;
    private final ExecutorService is = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Bekci-DB");
        t.setDaemon(true);
        return t;
    });
    private final ConcurrentLinkedQueue<Islem> kuyruk = new ConcurrentLinkedQueue<>();
    private Connection baglanti;

    public Veritabani(Logger log) {
        this.log = log;
    }

    /** Veritabanını açar; sürücü yoksa false. */
    public boolean ac(File dosya) {
        try {
            Class.forName("org.sqlite.JDBC");
            dosya.getParentFile().mkdirs();
            baglanti = DriverManager.getConnection("jdbc:sqlite:" + dosya.getAbsolutePath());
            try (Statement st = baglanti.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL");
                st.execute("CREATE TABLE IF NOT EXISTS islem (id INTEGER PRIMARY KEY AUTOINCREMENT, zaman INTEGER NOT NULL, "
                        + "uuid TEXT, isim TEXT, dunya TEXT, x INTEGER, y INTEGER, z INTEGER, tur TEXT NOT NULL, eski TEXT, yeni TEXT, "
                        + "esya TEXT, miktar INTEGER, geri INTEGER NOT NULL DEFAULT 0)");
                st.execute("CREATE INDEX IF NOT EXISTS islem_zaman ON islem(zaman)");
                st.execute("CREATE INDEX IF NOT EXISTS islem_konum ON islem(dunya, x, y, z)");
                st.execute("CREATE INDEX IF NOT EXISTS islem_oyuncu ON islem(uuid, zaman)");
            }
            return true;
        } catch (ClassNotFoundException | SQLException e) {
            log.severe("Veritabanı açılamadı (SQLite sürücüsü indirilemedi mi? internet?): " + e.getMessage());
            return false;
        }
    }

    public void ekle(Islem i) {
        kuyruk.add(i);
        if (kuyruk.size() >= 200) is.execute(this::yaz);
    }

    /** Saniyede bir çağrılır. */
    public void bosalt() {
        if (!kuyruk.isEmpty()) is.execute(this::yaz);
    }

    private void yaz() {
        if (kuyruk.isEmpty() || baglanti == null) return;
        String sql = "INSERT INTO islem (zaman, uuid, isim, dunya, x, y, z, tur, eski, yeni, esya, miktar) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = baglanti.prepareStatement(sql)) {
            baglanti.setAutoCommit(false);
            Islem i;
            while ((i = kuyruk.poll()) != null) {
                ps.setLong(1, i.zaman());
                ps.setString(2, i.uuid() == null ? null : i.uuid().toString());
                ps.setString(3, i.isim());
                ps.setString(4, i.dunya());
                ps.setInt(5, i.x());
                ps.setInt(6, i.y());
                ps.setInt(7, i.z());
                ps.setString(8, i.tur().name());
                ps.setString(9, i.eski());
                ps.setString(10, i.yeni());
                ps.setString(11, i.esya());
                ps.setInt(12, i.miktar());
                ps.addBatch();
            }
            ps.executeBatch();
            baglanti.commit();
        } catch (SQLException e) {
            log.warning("Kayıt yazılamadı: " + e.getMessage());
        } finally {
            try {
                baglanti.setAutoCommit(true);
            } catch (SQLException ignored) {
            }
        }
    }

    /** where: SQL koşulu ("1=1" olabilir); param: ? değerleri. En yeni üstte. */
    public CompletableFuture<List<Islem>> sorgu(String where, List<Object> param, int limit, int offset) {
        return CompletableFuture.supplyAsync(() -> {
            yaz();
            List<Islem> sonuc = new ArrayList<>();
            if (baglanti == null) return sonuc;
            String sql = "SELECT * FROM islem WHERE " + where + " ORDER BY zaman DESC, id DESC LIMIT ? OFFSET ?";
            try (PreparedStatement ps = baglanti.prepareStatement(sql)) {
                int n = 1;
                for (Object o : param) ps.setObject(n++, o);
                ps.setInt(n++, limit);
                ps.setInt(n, offset);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String u = rs.getString("uuid");
                        Islem.Tur tur;
                        try {
                            tur = Islem.Tur.valueOf(rs.getString("tur"));
                        } catch (IllegalArgumentException e) {
                            continue;
                        }
                        sonuc.add(new Islem(rs.getLong("id"), rs.getLong("zaman"), u == null ? null : UUID.fromString(u),
                                rs.getString("isim"), rs.getString("dunya"), rs.getInt("x"), rs.getInt("y"), rs.getInt("z"), tur,
                                rs.getString("eski"), rs.getString("yeni"), rs.getString("esya"), rs.getInt("miktar"), rs.getInt("geri") != 0));
                    }
                }
            } catch (SQLException e) {
                log.warning("Sorgu hatası: " + e.getMessage());
            }
            return sonuc;
        }, is);
    }

    public void isaretle(List<Long> idler) {
        if (idler.isEmpty()) return;
        List<Long> kopya = new ArrayList<>(idler);
        is.execute(() -> {
            if (baglanti == null) return;
            try (PreparedStatement ps = baglanti.prepareStatement("UPDATE islem SET geri = 1 WHERE id = ?")) {
                baglanti.setAutoCommit(false);
                for (long id : kopya) {
                    ps.setLong(1, id);
                    ps.addBatch();
                }
                ps.executeBatch();
                baglanti.commit();
            } catch (SQLException e) {
                log.warning("Geri alma işaretlenemedi: " + e.getMessage());
            } finally {
                try {
                    baglanti.setAutoCommit(true);
                } catch (SQLException ignored) {
                }
            }
        });
    }

    public void temizle(long oncesiMs) {
        is.execute(() -> {
            if (baglanti == null) return;
            try (PreparedStatement ps = baglanti.prepareStatement("DELETE FROM islem WHERE zaman < ?")) {
                ps.setLong(1, oncesiMs);
                int n = ps.executeUpdate();
                if (n > 0) log.info(n + " eski kayıt silindi.");
            } catch (SQLException e) {
                log.warning("Eski kayıtlar silinemedi: " + e.getMessage());
            }
        });
    }

    public void kapat() {
        is.execute(this::yaz);
        is.shutdown();
        try {
            is.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        try {
            if (baglanti != null) baglanti.close();
        } catch (SQLException ignored) {
        }
    }
}
