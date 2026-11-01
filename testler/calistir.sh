#!/usr/bin/env bash
# Saf mantık testleri: taklitler + eklenti kaynakları + testler derlenir, *Test sınıfları çalıştırılır.
set -uo pipefail
KOK="$(cd "$(dirname "$0")/.." && pwd)"
# Bekçi veritabanı testi için (varsa): SQLITE_JAR=/tmp/sqlitejar/sqlite-jdbc.jar
[ -z "${SQLITE_JAR:-}" ] && [ -f /tmp/sqlitejar/sqlite-jdbc.jar ] && SQLITE_JAR=/tmp/sqlitejar/sqlite-jdbc.jar
CIKTI=$(mktemp -d)
trap 'rm -rf "$CIKTI"' EXIT
mapfile -t KAYNAK < <(find "$KOK/CayOcagi/src/main/java" "$KOK/PolisKelepce/src/main/java" "$KOK/Bekci/src/main/java" -name '*.java' 2>/dev/null)
mapfile -t TEST < <(find "$KOK/testler" -maxdepth 1 -name '*.java')
if ! javac -encoding UTF-8 -proc:none -nowarn -d "$CIKTI" -sourcepath "$KOK/stubs" "${KAYNAK[@]}" "${TEST[@]}" 2>"$CIKTI/derleme.txt"; then
  grep -E "error" -A2 "$CIKTI/derleme.txt" | head -40
  echo "DERLEME HATASI"
  exit 1
fi
toplam=0; hata=0
for t in "${TEST[@]}"; do
  ad=$(basename "$t" .java)
  [[ "$ad" == *Test ]] || continue
  toplam=$((toplam+1))
  if java -cp "$CIKTI${SQLITE_JAR:+:$SQLITE_JAR}" "$ad" >"$CIKTI/$ad.txt" 2>&1; then
    echo "GEÇTİ  $ad"
  else
    hata=$((hata+1)); echo "KALDI  $ad"; head -5 "$CIKTI/$ad.txt"
  fi
done
if [ $hata -eq 0 ]; then echo "$toplam test, hepsi geçti"; else echo "$toplam testten $hata kaldı"; exit 1; fi
