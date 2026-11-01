#!/usr/bin/env bash
# guncelle.sh testi: sahte bir git deposu ve sahte kurulum komutuyla.
set -uo pipefail
KOK="$(cd "$(dirname "$0")/.." && pwd)"
hata=0
dogrula() { if [ "$1" == "$2" ]; then echo "GEÇTİ  $3"; else echo "KALDI  $3"; echo "  beklenen: $1"; echo "  gerçek:   $2"; hata=$((hata+1)); fi; }

D=$(mktemp -d)
git init -q --bare "$D/uzak.git"
git clone -q "$D/uzak.git" "$D/gelistirme" 2>/dev/null
( cd "$D/gelistirme" && git config user.email t@t && git config user.name t && echo 1 > surum.txt && git add . && git commit -qm ilk && git push -q origin HEAD 2>/dev/null )

calistir() { # <mod> <oyuncu_sayisi>
  REPO_URL="$D/uzak.git" DIZIN="$D/sunucu-kopya" SURUM_DOSYASI="$D/kurulu-surum" \
  KURULUM_KOMUTU="echo kuruldu >> $D/kurulum.log; cat $D/sunucu-kopya/surum.txt >> $D/kurulum.log" \
  YEDEK_KOMUTU="echo yedek >> $D/yedek.log" OYUNCU_SAYISI="$2" \
  bash "$KOK/guncelle.sh" "$1" >/dev/null 2>&1
}

calistir elle 0
dogrula "1" "$(tail -1 "$D/kurulum.log")" "ilk çalıştırmada indirip kurar"
calistir elle 0
dogrula "2" "$(wc -l < "$D/kurulum.log" | tr -d ' ')" "değişiklik yoksa tekrar kurmaz"
dogrula "1" "$(wc -l < "$D/yedek.log" | tr -d ' ')" "kurmadan önce yedek alır (sadece kurarken)"

( cd "$D/gelistirme" && echo 2 > surum.txt && git commit -qam yeni && git push -q origin HEAD 2>/dev/null )
calistir gece 3
dogrula "2" "$(wc -l < "$D/kurulum.log" | tr -d ' ')" "gece: oyunda biri varsa kurmaz"
calistir gece 0
dogrula "2" "$(tail -1 "$D/kurulum.log")" "gece: kimse yoksa yeni sürümü kurar"
( cd "$D/gelistirme" && echo 3 > surum.txt && git commit -qam yeni2 && git push -q origin HEAD 2>/dev/null )
calistir elle 4
dogrula "3" "$(tail -1 "$D/kurulum.log")" "elle: oyuncu olsa da kurar"

# Kurulum başarısızsa sürüm kaydedilmez, sonraki çalıştırmada tekrar dener
( cd "$D/gelistirme" && echo 4 > surum.txt && git commit -qam yeni3 && git push -q origin HEAD 2>/dev/null )
REPO_URL="$D/uzak.git" DIZIN="$D/sunucu-kopya" SURUM_DOSYASI="$D/kurulu-surum" KURULUM_KOMUTU="false" \
  YEDEK_KOMUTU="true" OYUNCU_SAYISI=0 bash "$KOK/guncelle.sh" elle >/dev/null 2>&1
kod=$?
dogrula "1" "$([ $kod -ne 0 ] && echo 1 || echo 0)" "kurulum hatasında hata koduyla çıkar"
calistir elle 0
dogrula "4" "$(tail -1 "$D/kurulum.log")" "başarısız kurulumdan sonra tekrar dener"
rm -rf "$D"

if [ $hata -eq 0 ]; then echo "guncelle testleri: hepsi geçti"; else echo "guncelle testleri: $hata kaldı"; exit 1; fi
