import java.util.UUID;
import tr.efe.polis.Gozalti;
import tr.efe.polis.Kayitlar;

public class GirisTest {
    public static void main(String[] a) {
        Dogrula.esit("polis", Gozalti.girisYeri(true, true), "polis oyunda: polisin yanı");
        Dogrula.esit("polis", Gozalti.girisYeri(true, false), "polis oyunda, hücre yok");
        Dogrula.esit("hucre", Gozalti.girisYeri(false, true), "polis yok: hücre");
        Dogrula.esit("yerinde", Gozalti.girisYeri(false, false), "ikisi de yok");
        Dogrula.esit(true, Kayitlar.bedrockMu(new UUID(0, 5)), "Floodgate UUID");
        Dogrula.esit(false, Kayitlar.bedrockMu(UUID.randomUUID()), "Java UUID");
    }
}
