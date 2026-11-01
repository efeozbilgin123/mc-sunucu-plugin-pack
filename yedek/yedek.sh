#!/usr/bin/env bash
# Minecraft sunucusunun saatlik yedeği. systemd zamanlayıcısı (minecraft-yedek.timer) her saat başı çalıştırır.
# Elle: bash ~/yedek/yedek.sh
set -uo pipefail

MC_DIZIN="${MC_DIZIN:-$HOME/minecraft}"
YEDEK_KOK="${YEDEK_KOK:-$HOME/yedek}"
YEDEK_DIZIN="${YEDEK_DIZIN:-$YEDEK_KOK/arsiv}"
TMUX_OTURUM="${TMUX_OTURUM:-mc}"
GUNLUK="$YEDEK_KOK/yedek.log"
BURASI="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=/dev/null
source "$BURASI/saklama.sh"

mkdir -p "$YEDEK_DIZIN"
yaz() { echo "[$(TZ=Europe/Istanbul date '+%d.%m.%Y %H:%M:%S')] $*" | tee -a "$GUNLUK"; }

# Aynı anda iki yedek çalışmasın
exec 9>"$YEDEK_KOK/.kilit"
if ! flock -n 9; then yaz "Başka bir yedek ya da geri yükleme çalışıyor, atlandı."; exit 0; fi
# Çökmüş bir yedekten kalan yarım dosyalar
rm -f "$YEDEK_DIZIN"/mc-*.tar.gz.tmp

konsol() { tmux send-keys -t "$TMUX_OTURUM" "$1" Enter 2>/dev/null; }
SUNUCU=0
if command -v tmux >/dev/null 2>&1 && tmux has-session -t "$TMUX_OTURUM" 2>/dev/null; then SUNUCU=1; fi

kayit_ac() { [ "$SUNUCU" = 1 ] && konsol "save-on"; }
trap kayit_ac EXIT

# 1) Dünya kaydını durdur ve diske yazdır
if [ "$SUNUCU" = 1 ]; then
  LOG="$MC_DIZIN/logs/latest.log"
  once=$(wc -l < "$LOG" 2>/dev/null || echo 0)
  konsol "save-off"
  konsol "save-all flush"
  for _ in $(seq 1 60); do
    sleep 1
    if tail -n +"$((once + 1))" "$LOG" 2>/dev/null | grep -q "Saved the game"; then break; fi
  done
fi

# 2) Disk kontrolü: son yedeğin 1,5 katı (en az 1 GB) boş yer olmalı
son=$(ls -t "$YEDEK_DIZIN"/mc-*.tar.gz 2>/dev/null | head -1)
sonKb=0; [ -n "$son" ] && sonKb=$(( $(stat -c %s "$son") / 1024 ))
gerekKb=$(( sonKb * 3 / 2 )); [ "$gerekKb" -lt 1048576 ] && gerekKb=1048576
bos() { df -Pk "$YEDEK_DIZIN" | awk 'NR==2{print $4}'; }
if [ "$(bos)" -lt "$gerekKb" ]; then
  while IFS= read -r eski; do
    [ -z "$eski" ] && continue
    yaz "Yer açmak için eski yedek silindi: $(basename "$eski")"
    rm -f "$eski"
  done < <(yer_acmak_icin_silinecekler "$YEDEK_DIZIN" "$(bos)" "$gerekKb")
fi
if [ "$(bos)" -lt "$gerekKb" ]; then
  yaz "Yedek alınamadı: disk dolu (gerekli $((gerekKb / 1024)) MB)."
  [ "$SUNUCU" = 1 ] && konsol "say Yedek alınamadı: disk dolu"
  exit 1
fi

# 3) Arşiv
cd "$MC_DIZIN" || { yaz "Sunucu klasörü yok: $MC_DIZIN"; exit 1; }
YOLLAR=()
for y in world* plugins config server.properties ./*.json bukkit.yml spigot.yml start.sh; do
  [ -e "$y" ] && YOLLAR+=("${y#./}")
done
if [ ${#YOLLAR[@]} -eq 0 ]; then yaz "Yedeklenecek bir şey bulunamadı."; exit 1; fi
AD="mc-$(TZ=Europe/Istanbul date +%Y-%m-%d_%H-%M).tar.gz"
HEDEF="$YEDEK_DIZIN/$AD"
tar -czf "$HEDEF.tmp" --exclude='plugins/*/logs' --exclude='plugins/.paper-remapped' --warning=no-file-changed "${YOLLAR[@]}"
kod=$?
if [ $kod -gt 1 ]; then rm -f "$HEDEF.tmp"; yaz "Yedek alınamadı (tar hata kodu $kod)."; exit 1; fi
mv -f "$HEDEF.tmp" "$HEDEF"

# 4) Kaydı geri aç (trap da açar), eskileri temizle
kayit_ac; trap - EXIT
silinen=0
while IFS= read -r f; do [ -n "$f" ] && rm -f "$f" && silinen=$((silinen + 1)); done < <(silinecekleri_listele "$YEDEK_DIZIN" "$(date +%s)")
yaz "Yedek tamam: $AD ($(du -h "$HEDEF" | cut -f1)), $silinen eski yedek silindi."
