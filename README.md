# MC Sunucu Eklenti Paketi

Paper 26.3 sunucusu için üç eklenti ve yedek sistemi. Java ve Bedrock (Geyser) uyumlu, resource pack gerektirmez.

| Klasör | Ne |
|---|---|
| `CayOcagi/` | Çay yapma, çay ekme, semaver |
| `PolisKelepce/` | Polis, başkan, doktor; kelepçe, ceza/vergi, sicil, yaralı sistemi, savaştan kaçma engeli, idam |
| `Bekci/` | Blok/sandık kaydı, geri alma, sandık bulucu, mezar |
| `yedek/` | Saatlik yedek ve geri yükleme betikleri |
| `kurulum-sablon.sh` | Kurulum betiği (sunucuda derler ve kurar) |
| `guncelle.sh` | Sunucuda `guncelle` komutu: bu depodaki en son sürümü kurar |

## Sunucuda güncelleme

```
guncelle
```

Her gece 05:00'te (Türkiye saati) oyunda kimse yoksa sunucu kendisi de günceller. Kurmadan önce yedek alınır.

## Testler

```
bash testler/calistir.sh
bash testler/yedek_test.sh
bash testler/guncelle_test.sh
```

## Lisans

GNU GPL v3. Ayrıntı: `LICENSE`
