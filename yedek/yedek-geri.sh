#!/usr/bin/env bash
# Yedekten geri yükleme. Kullanım: bash ~/yedek/yedek-geri.sh
# Sunucuyu durdurur, yedekteki yolların şu anki halini kenara alır, yedeği açar, sunucuyu başlatır.
set -uo pipefail

MC_DIZIN="${MC_DIZIN:-$HOME/minecraft}"
YEDEK_KOK="${YEDEK_KOK:-$HOME/yedek}"
YEDEK_DIZIN="${YEDEK_DIZIN:-$YEDEK_KOK/arsiv}"
TMUX_OTURUM="${TMUX_OTURUM:-mc}"

mkdir -p "$YEDEK_KOK"
# Saatlik yedek aynı anda çalışmasın (yarım geri yüklenmiş klasörü yedeklemesin)
exec 9>"$YEDEK_KOK/.kilit"
if ! flock -n 9; then echo "Şu an yedek alınıyor, birkaç saniye sonra tekrar dene."; exit 1; fi

mapfile -t ARSIVLER < <(ls -t "$YEDEK_DIZIN"/mc-*.tar.gz 2>/dev/null)
if [ ${#ARSIVLER[@]} -eq 0 ]; then echo "Hiç yedek yok ($YEDEK_DIZIN)."; exit 1; fi

echo "Yedekler (en yeni üstte):"
for i in "${!ARSIVLER[@]}"; do
  ad=$(basename "${ARSIVLER[$i]}" .tar.gz)
  tarih="${ad:3:10} ${ad:14:2}:${ad:17:2}"
  printf "  %2d) %s  (%s)\n" "$((i + 1))" "$tarih" "$(du -h "${ARSIVLER[$i]}" | cut -f1)"
done
read -r -p "Hangi yedek? (numara): " NO
if ! [[ "$NO" =~ ^[0-9]+$ ]] || [ "$NO" -lt 1 ] || [ "$NO" -gt ${#ARSIVLER[@]} ]; then echo "Geçersiz numara."; exit 1; fi
ARSIV="${ARSIVLER[$((NO - 1))]}"
echo "Seçilen: $(basename "$ARSIV")"
echo "Sunucu durdurulacak, dünya ve eklenti verileri bu yedeğe dönecek."
read -r -p "Emin misin? Devam için 'evet' yaz: " ONAY
[ "$ONAY" = "evet" ] || { echo "Vazgeçildi."; exit 0; }

servis_var() { [ -z "${SERVIS_YOK:-}" ] && systemctl list-unit-files minecraft.service 2>/dev/null | grep -q minecraft; }

# 1) Sunucuyu durdur
if servis_var; then
  echo "Sunucu durduruluyor..."
  sudo systemctl stop minecraft
elif [ -z "${SERVIS_YOK:-}" ] && tmux has-session -t "$TMUX_OTURUM" 2>/dev/null; then
  echo "Sunucu durduruluyor..."
  tmux send-keys -t "$TMUX_OTURUM" "stop" Enter
  for _ in $(seq 1 60); do tmux has-session -t "$TMUX_OTURUM" 2>/dev/null || break; sleep 1; done
  if tmux has-session -t "$TMUX_OTURUM" 2>/dev/null; then
    echo "Sunucu 60 saniyede kapanmadı, geri yükleme yapılmadı. Sunucuyu kapatıp tekrar dene."
    exit 1
  fi
fi

# Arşiv sağlam mı? Bozuksa hiçbir şeye dokunma
if ! gzip -t "$ARSIV" 2>/dev/null || ! tar -tzf "$ARSIV" >/dev/null 2>&1; then
  echo "HATA: bu yedek bozuk görünüyor, hiçbir şey değiştirilmedi. Başka bir yedek seç."
  servis_var && sudo systemctl start minecraft
  exit 1
fi

# 2) Yedekteki üst düzey yolların şu anki halini kenara al
ONCE="$YEDEK_KOK/geri-yukleme-oncesi-$(TZ=Europe/Istanbul date +%Y-%m-%d_%H-%M-%S)"
mkdir -p "$ONCE"
mapfile -t UST < <(tar -tzf "$ARSIV" | cut -d/ -f1 | sort -u)
for y in "${UST[@]}"; do
  [ -n "$y" ] && [ -e "$MC_DIZIN/$y" ] && mv "$MC_DIZIN/$y" "$ONCE/"
done

# 3) Yedeği aç
if ! tar -xzf "$ARSIV" -C "$MC_DIZIN"; then
  echo "HATA: yedek açılamadı. Önceki hal burada duruyor: $ONCE"
  exit 1
fi
echo "Yedek geri yüklendi: $(basename "$ARSIV")"
echo "Önceki hal (gerekirse): $ONCE"

# 4) Sunucuyu başlat
if servis_var; then
  sudo systemctl start minecraft
  echo "Sunucu başlatılıyor (30-60 sn)."
elif [ -z "${SERVIS_YOK:-}" ]; then
  echo "Sunucuyu kendin başlat: ~/minecraft/start.sh"
fi
