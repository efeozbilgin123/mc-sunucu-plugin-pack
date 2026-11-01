import java.util.Map;
import java.util.UUID;
import tr.efe.bekci.KapIzleyici;

public class KapIzleyiciTest {
    public static void main(String[] a) {
        UUID A = UUID.randomUUID(), B = UUID.randomUUID();
        KapIzleyici k = new KapIzleyici();
        // İkisi aynı sandığı açık tutuyor, B 5 elmas alıyor: suç B'ye yazılmalı, A'ya değil
        k.ac(A, "world;1;64;1", Map.of("elmas", 5));
        k.ac(B, "world;1;64;1", Map.of("elmas", 5));
        Dogrula.esit(Map.of("elmas", -5), k.degisti(B, Map.of()), "B'nin aldığı B'ye yazılır");
        Dogrula.esit(Map.of(), k.kapat(A, Map.of()), "A kapatınca ona bir şey yazılmaz");
        Dogrula.esit(Map.of(), k.kapat(B, Map.of()), "B'nin işi zaten yazıldı, tekrar yazılmaz");
        // Huni açıkken sandığa demir doldurdu: kimseye yazılmaz
        k.ac(A, "world;2;64;2", Map.of("demir", 2));
        k.disaridan("world;2;64;2", Map.of("demir", 3));
        Dogrula.esit(Map.of(), k.kapat(A, Map.of("demir", 3)), "huni aktarımı açanın üstüne yazılmaz");
        // Tek kişi: açılış-kapanış farkı
        k.ac(B, "world;3;64;3", Map.of());
        Dogrula.esit(Map.of("tas", 4), k.kapat(B, Map.of("tas", 4)), "tek kişide kapanışta fark");
        // İzlenmeyen oyuncu
        Dogrula.esit(Map.of(), k.kapat(A, Map.of("x", 1)), "açmamış oyuncu için boş");
        Dogrula.esit(Map.of(), k.degisti(A, Map.of("x", 1)), "açmamış oyuncu değişikliği boş");
    }
}
