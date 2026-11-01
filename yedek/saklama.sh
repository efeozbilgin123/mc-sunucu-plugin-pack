#!/usr/bin/env bash
# Yedek saklama kuralı (yedek.sh bunu "source" eder).
# Arşiv adı: mc-YYYY-AA-GG_SS-DD.tar.gz (saat Europe/Istanbul).
# Kalanlar: son 24 saatteki bütün yedekler + 14 güne kadar her takvim gününün en yeni yedeği.
# Silinecek dosyaların tam yolunu satır satır yazar; desene uymayan dosyalara dokunmaz.

SAKLA_SAAT="${SAKLA_SAAT:-24}"
SAKLA_GUN="${SAKLA_GUN:-14}"

silinecekleri_listele() {
  local dizin="$1" simdi="$2"
  local sinir24=$((simdi - SAKLA_SAAT * 3600))
  local sinirGun=$((simdi - SAKLA_GUN * 86400))
  local -a dosyalar=() zamanlar=() gunler=()
  local -A enYeni=()
  local f ad gun ts i
  for f in "$dizin"/mc-*.tar.gz; do
    [ -e "$f" ] || continue
    ad=$(basename "$f")
    [[ "$ad" =~ ^mc-([0-9]{4}-[0-9]{2}-[0-9]{2})_([0-9]{2})-([0-9]{2})\.tar\.gz$ ]] || continue
    gun="${BASH_REMATCH[1]}"
    ts=$(TZ=Europe/Istanbul date -d "$gun ${BASH_REMATCH[2]}:${BASH_REMATCH[3]}" +%s 2>/dev/null) || continue
    dosyalar+=("$f"); zamanlar+=("$ts"); gunler+=("$gun")
    if [ -z "${enYeni[$gun]:-}" ] || [ "$ts" -gt "${enYeni[$gun]}" ]; then enYeni[$gun]=$ts; fi
  done
  for i in "${!dosyalar[@]}"; do
    ts=${zamanlar[$i]}; gun=${gunler[$i]}
    [ "$ts" -ge "$sinir24" ] && continue                                   # son 24 saat: hepsi kalır
    [ "$ts" -eq "${enYeni[$gun]}" ] && [ "$ts" -ge "$sinirGun" ] && continue # günün son yedeği, 14 gün içinde
    echo "${dosyalar[$i]}"
  done
}

# Disk dolu: yer açmak için silinebilecek yedekler (en eskiden), en yeni SAKLA_EN_AZ yedeğe hiç dokunmadan.
# Silmek gereken boşluğu sağlamayacaksa hiçbir şey yazmaz (yedekleri boşuna yok etmemek için).
# Kullanım: yer_acmak_icin_silinecekler <klasör> <boş_kb> <gerekli_kb>
SAKLA_EN_AZ="${SAKLA_EN_AZ:-2}"
yer_acmak_icin_silinecekler() {
  local dizin="$1" bos="$2" gerek="$3"
  local -a adaylar=()
  local f
  while IFS= read -r f; do
    [[ "$(basename "$f")" =~ ^mc-[0-9]{4}-[0-9]{2}-[0-9]{2}_[0-9]{2}-[0-9]{2}\.tar\.gz$ ]] && adaylar+=("$f")
  done < <(ls "$dizin"/mc-*.tar.gz 2>/dev/null | sort)   # ad = tarih, sıralama en eskiden
  local n=$(( ${#adaylar[@]} - SAKLA_EN_AZ ))
  [ "$n" -le 0 ] && return 0
  local -a secilen=()
  local toplam=$bos i
  for (( i = 0; i < n && toplam < gerek; i++ )); do
    secilen+=("${adaylar[$i]}")
    toplam=$(( toplam + $(stat -c %s "${adaylar[$i]}") / 1024 ))
  done
  [ "$toplam" -ge "$gerek" ] && printf '%s\n' "${secilen[@]}"
  return 0
}
