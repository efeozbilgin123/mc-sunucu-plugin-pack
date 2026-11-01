#!/usr/bin/env bash
# Sunucu güncelleyici: GitHub'daki en son sürümü indirir ve kurar.
#   guncelle        -> yeni sürüm varsa hemen kurar (oyuncu olsa da; sunucu yeniden başlar)
#   guncelle gece   -> zamanlayıcı için: oyunda kimse yoksa kurar, biri varsa o geceyi atlar
# Kurmadan önce bir yedek alınır. Kurulum başarısız olursa sürüm kaydedilmez, sonraki sefer tekrar denenir.
set -uo pipefail

MOD="${1:-elle}"
REPO_URL="${REPO_URL:-https://github.com/efeozbilgin123/mc-sunucu-plugin-pack.git}"
DIZIN="${DIZIN:-$HOME/mc-sunucu-plugin-pack}"
SURUM_DOSYASI="${SURUM_DOSYASI:-$HOME/.mc-kurulu-surum}"
TMUX_OTURUM="${TMUX_OTURUM:-mc}"
MC_DIZIN="${MC_DIZIN:-$HOME/minecraft}"
KURULUM_KOMUTU="${KURULUM_KOMUTU:-KAYNAK=\"$DIZIN\" bash \"$DIZIN/kurulum-sablon.sh\"}"
YEDEK_KOMUTU="${YEDEK_KOMUTU:-bash \"$HOME/yedek/yedek.sh\"}"

yaz() { echo "[$(TZ=Europe/Istanbul date '+%d.%m.%Y %H:%M')] $*"; }

# Oyundaki oyuncu sayısı: konsola "list" yollanır, log'daki cevap okunur. Sunucu kapalıysa 0.
oyuncu_sayisi() {
  if [ -n "${OYUNCU_SAYISI:-}" ]; then echo "$OYUNCU_SAYISI"; return; fi
  tmux has-session -t "$TMUX_OTURUM" 2>/dev/null || { echo 0; return; }
  local log="$MC_DIZIN/logs/latest.log" once
  once=$(wc -l < "$log" 2>/dev/null || echo 0)
  tmux send-keys -t "$TMUX_OTURUM" "list" Enter
  sleep 3
  local satir
  satir=$(tail -n +"$((once + 1))" "$log" 2>/dev/null | grep -o "There are [0-9]* of a max" | tail -1)
  if [ -z "$satir" ]; then echo 99; return; fi # cevap okunamadı: güvenli tarafta kal, kurma
  echo "$satir" | grep -o "[0-9]*" | head -1
}

# 1) En son sürümü indir
if [ ! -d "$DIZIN/.git" ]; then
  git clone -q "$REPO_URL" "$DIZIN" || { yaz "İndirilemedi: $REPO_URL"; exit 1; }
else
  git -C "$DIZIN" fetch -q origin || { yaz "GitHub'a ulaşılamadı."; exit 1; }
  git -C "$DIZIN" reset -q --hard "origin/HEAD" 2>/dev/null || git -C "$DIZIN" reset -q --hard FETCH_HEAD
fi
YENI=$(git -C "$DIZIN" rev-parse HEAD)
KURULU=$(cat "$SURUM_DOSYASI" 2>/dev/null || true)

if [ "$YENI" = "$KURULU" ]; then
  yaz "Zaten güncel (sürüm ${YENI:0:7})."
  exit 0
fi

# 2) Gece modunda oyunda biri varsa kurma
if [ "$MOD" = "gece" ]; then
  n=$(oyuncu_sayisi)
  if [ "$n" != "0" ]; then
    yaz "Yeni sürüm var (${YENI:0:7}) ama oyunda $n kişi var; bu gece kurulmadı."
    exit 0
  fi
fi

yaz "Yeni sürüm kuruluyor: ${KURULU:0:7} -> ${YENI:0:7}"
git -C "$DIZIN" log --oneline -5 2>/dev/null | sed 's/^/  /'

# 3) Önce yedek, sonra kurulum
eval "$YEDEK_KOMUTU" || yaz "Uyarı: yedek alınamadı, yine de devam ediliyor."
if eval "$KURULUM_KOMUTU"; then
  echo "$YENI" > "$SURUM_DOSYASI"
  yaz "Güncelleme tamam (sürüm ${YENI:0:7})."
else
  yaz "HATA: kurulum başarısız oldu. Sürüm kaydedilmedi, bir sonraki 'guncelle' tekrar dener."
  exit 1
fi
