#!/usr/bin/env bash
# Yedek saklama kuralı ve yedek/geri yükleme betiklerinin testleri.
set -uo pipefail
KOK="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=/dev/null
source "$KOK/yedek/saklama.sh"

hata=0
dogrula() { # <beklenen> <gerçek> <ad>
  if [ "$1" == "$2" ]; then echo "GEÇTİ  $3"; else echo "KALDI  $3"; echo "  beklenen: $1"; echo "  gerçek:   $2"; hata=$((hata+1)); fi
}
zaman() { TZ=Europe/Istanbul date -d "$1" +%s; }

# --- 1) Elle hesaplanmış küçük örnek -------------------------------------------
D=$(mktemp -d)
SIMDI=$(zaman "2026-11-10 12:00")
for ad in mc-2026-11-10_11-00 mc-2026-11-09_13-00 mc-2026-11-09_11-00 mc-2026-11-08_23-00 \
          mc-2026-11-08_10-00 mc-2026-10-28_20-00 mc-2026-10-27_13-00 mc-2026-10-26_10-00; do
  : > "$D/$ad.tar.gz"
done
: > "$D/notlar.txt"
: > "$D/mc-2026-11-10_11-30.tar.gz.tmp"
SONUC=$(silinecekleri_listele "$D" "$SIMDI" | xargs -n1 basename | sort | tr '\n' ' ')
BEKLENEN=$(printf '%s\n' mc-2026-11-09_11-00.tar.gz mc-2026-11-08_10-00.tar.gz mc-2026-10-26_10-00.tar.gz | sort | tr '\n' ' ')
dogrula "$BEKLENEN" "$SONUC" "saklama: elle hesaplanan örnek"
rm -rf "$D"

# --- 2) Büyük örnek: kurallar ----------------------------------------------------
D=$(mktemp -d)
SIMDI=$(zaman "2026-11-20 15:30")
for s in $(seq 0 30); do : > "$D/$(TZ=Europe/Istanbul date -d "@$((SIMDI - s*3600))" +mc-%Y-%m-%d_%H-%M).tar.gz"; done
for g in $(seq 2 20); do for saat in 03 11 19; do
  : > "$D/$(TZ=Europe/Istanbul date -d "@$((SIMDI - g*86400))" +mc-%Y-%m-%d)_$saat-00.tar.gz"
done; done
: > "$D/notlar.txt"
SIL=$(silinecekleri_listele "$D" "$SIMDI")
echo "$SIL" | grep -q notlar.txt && dogrula "yok" "var" "notlar.txt listede olmamalı" || dogrula x x "notlar.txt listede değil"
# Kalanlar: 24 saattekilerin hepsi, daha eskilerden 14 güne kadar günde en fazla bir tane, 14 günden eski hiçbiri
KALAN=$(comm -23 <(ls "$D"/mc-*.tar.gz | sort) <(echo "$SIL" | sort))
eski=0; son24=0; declare -A gunler
for f in $KALAN; do
  b=$(basename "$f" .tar.gz); g=${b:3:10}; s=${b:14:2}; d=${b:17:2}
  t=$(zaman "$g $s:$d")
  if [ "$t" -lt $((SIMDI - 14*86400)) ]; then eski=$((eski+1)); fi
  if [ "$t" -ge $((SIMDI - 86400)) ]; then son24=$((son24+1)); else gunler[$g]=$(( ${gunler[$g]:-0} + 1 )); fi
done
dogrula 0 "$eski" "14 günden eski yedek kalmaz"
dogrula 25 "$son24" "son 24 saatteki (25 dosya) hepsi kalır"
cok=0; for g in "${!gunler[@]}"; do [ "${gunler[$g]}" -gt 1 ] && cok=$((cok+1)); done
dogrula 0 "$cok" "24 saatten eski günlerde günde en fazla bir yedek"
dogrula 13 "${#gunler[@]}" "24 saatten eski günler (2-14 gün önce) için birer yedek; dünün son yedeği son 24 saatte"
rm -rf "$D"

# --- 2b) Disk dolu: yer açmak için sadece işe yarayacaksa ve en yeni 2 yedeğe dokunmadan sil --------
D=$(mktemp -d)
for ad in mc-2026-11-01_01-00 mc-2026-11-02_01-00 mc-2026-11-03_01-00 mc-2026-11-04_01-00; do truncate -s 100K "$D/$ad.tar.gz"; done
truncate -s 900K "$D/notlar.txt"
SONUC=$(yer_acmak_icin_silinecekler "$D" 50 200 | xargs -n1 basename | tr '\n' ' ')
dogrula "mc-2026-11-01_01-00.tar.gz mc-2026-11-02_01-00.tar.gz " "$SONUC" "disk: yetecek kadar en eskiden sil"
SONUC=$(yer_acmak_icin_silinecekler "$D" 50 120 | xargs -n1 basename | tr '\n' ' ')
dogrula "mc-2026-11-01_01-00.tar.gz " "$SONUC" "disk: fazlasını silme"
SONUC=$(yer_acmak_icin_silinecekler "$D" 50 1000 | tr -d '\n')
dogrula "" "$SONUC" "disk: silmek yetmeyecekse hiçbirini silme"
rm -rf "$D"

# --- 3) yedek.sh sahte sunucu klasörüyle bir arşiv üretir --------------------------
D=$(mktemp -d)
mkdir -p "$D/mc/world/region" "$D/mc/world_nether" "$D/mc/plugins/Bekci" "$D/mc/logs" "$D/mc/libraries"
echo seviye > "$D/mc/world/level.dat"; echo x > "$D/mc/world/region/r.0.0.mca"
echo a > "$D/mc/server.properties"; echo '[]' > "$D/mc/ops.json"; echo b > "$D/mc/plugins/Bekci/config.yml"
echo log > "$D/mc/logs/latest.log"; echo lib > "$D/mc/libraries/x.jar"
mkdir -p "$D/yedek/arsiv"; : > "$D/yedek/arsiv/mc-2020-01-01_00-00.tar.gz.tmp"
MC_DIZIN="$D/mc" YEDEK_KOK="$D/yedek" TMUX_OTURUM="yok-$$" bash "$KOK/yedek/yedek.sh" >/dev/null 2>&1
[ -e "$D/yedek/arsiv/mc-2020-01-01_00-00.tar.gz.tmp" ] && dogrula silindi duruyor "yarım kalmış .tmp temizlenir" || dogrula x x "yarım kalmış .tmp temizlenir"
ARSIV=$(ls "$D"/yedek/arsiv/mc-*.tar.gz 2>/dev/null | head -1)
[ -n "$ARSIV" ] && dogrula x x "yedek.sh arşiv üretti" || dogrula "arşiv" "yok" "yedek.sh arşiv üretti"
if [ -n "$ARSIV" ]; then
  ICERIK=$(tar -tzf "$ARSIV")
  echo "$ICERIK" | grep -q '^world/level.dat$' && dogrula x x "arşivde world/level.dat var" || dogrula var yok "arşivde world/level.dat var"
  echo "$ICERIK" | grep -q '^plugins/Bekci/config.yml$' && dogrula x x "arşivde eklenti verisi var" || dogrula var yok "arşivde eklenti verisi var"
  echo "$ICERIK" | grep -q '^logs/\|^libraries/' && dogrula yok var "logs/libraries arşive girmez" || dogrula x x "logs/libraries arşive girmez"
fi

# --- 4) yedek-geri.sh: geri yükler, önceki hali kenara alır --------------------------
if [ -n "$ARSIV" ]; then
  echo bozuldu > "$D/mc/world/level.dat"
  printf '1\nevet\n' | MC_DIZIN="$D/mc" YEDEK_KOK="$D/yedek" SERVIS_YOK=1 bash "$KOK/yedek/yedek-geri.sh" >/dev/null 2>&1
  dogrula seviye "$(cat "$D/mc/world/level.dat")" "geri yükleme dünyayı eski haline getirir"
  ONCE=$(ls -d "$D"/yedek/geri-yukleme-oncesi-* 2>/dev/null | head -1)
  dogrula bozuldu "$(cat "$ONCE/world/level.dat" 2>/dev/null)" "önceki hal kenara alınır"
  dogrula log "$(cat "$D/mc/logs/latest.log")" "arşivde olmayanlara dokunulmaz"
  # Bozuk arşiv: geri yükleme hiçbir şeye dokunmadan durmalı
  echo bozuk > "$D/yedek/arsiv/mc-2099-01-01_00-00.tar.gz"
  echo simdiki > "$D/mc/world/level.dat"
  printf '1\nevet\n' | MC_DIZIN="$D/mc" YEDEK_KOK="$D/yedek" SERVIS_YOK=1 bash "$KOK/yedek/yedek-geri.sh" >/dev/null 2>&1
  dogrula simdiki "$(cat "$D/mc/world/level.dat")" "bozuk arşivde geri yükleme hiçbir şeye dokunmaz"
  # Yedek alınırken (kilit tutuluyken) geri yükleme başlamaz
  rm -f "$D/yedek/arsiv/mc-2099-01-01_00-00.tar.gz"
  ( exec 9>"$D/yedek/.kilit"; flock 9; sleep 3 ) &
  sleep 0.5
  printf '1\nevet\n' | MC_DIZIN="$D/mc" YEDEK_KOK="$D/yedek" SERVIS_YOK=1 bash "$KOK/yedek/yedek-geri.sh" >/dev/null 2>&1
  dogrula simdiki "$(cat "$D/mc/world/level.dat")" "yedek alınırken geri yükleme başlamaz"
  wait
fi
rm -rf "$D"

if [ $hata -eq 0 ]; then echo "yedek testleri: hepsi geçti"; else echo "yedek testleri: $hata kaldı"; exit 1; fi
