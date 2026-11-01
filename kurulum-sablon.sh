#!/usr/bin/env bash
# Efe'nin sunucusu: Çay Ocağı + Polis Kelepçe + Bekçi eklentileri ve saatlik yedek sistemi
# Sunucuda derler, plugins klasörüne koyar, yedek zamanlayıcısını kurar, sunucuyu yeniden başlatır (Paper 26.3).
# Tekrar çalıştırmak güvenli: eklentiler güncellenir, yedekler silinmez, zamanlayıcı çiftlenmez.
# Kullanım:  bash kurulum.sh        (sudo ile DEĞİL, normal kullanıcınla)
# Java 25 JDK başka bir yerdeyse:  JDK=/yol/jdk-25 bash kurulum.sh
set -euo pipefail

if [ "$(id -u)" -eq 0 ]; then
  echo "Bu script'i root/sudo ile değil, normal kullanıcınla çalıştır: bash kurulum.sh"
  exit 1
fi

MC_DIR="$HOME/minecraft"
SRC_ROOT="$HOME/eklentiler-kaynak"
PLUGINLER="CayOcagi PolisKelepce Bekci"
YEDEK_DIR="$HOME/yedek"

renk() { printf "\n\033[1;32m==> %s\033[0m\n" "$1"; }

jdk25_mu() { # $1 = JDK kök dizini
  [ -x "$1/bin/javac" ] && "$1/bin/javac" -version 2>&1 | grep -qE 'javac (2[5-9]|[3-9][0-9])'
}

renk "1/6 Java 25 JDK aranıyor"
JDK="${JDK:-}"
if [ -n "$JDK" ] && ! jdk25_mu "$JDK"; then
  echo "Verilen JDK=$JDK bir Java 25+ JDK değil, otomatik aranacak."
  JDK=""
fi
if [ -z "$JDK" ] && command -v javac >/dev/null 2>&1; then
  ADAY="$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")"
  jdk25_mu "$ADAY" && JDK="$ADAY"
fi
if [ -z "$JDK" ]; then
  for d in /usr/lib/jvm/*; do
    if jdk25_mu "$d"; then JDK="$d"; break; fi
  done
fi
if [ -z "$JDK" ]; then
  echo "Java 25 JDK bulunamadı, kuruluyor..."
  sudo apt-get update -y
  if apt-cache show openjdk-25-jdk-headless >/dev/null 2>&1; then
    sudo apt-get install -y openjdk-25-jdk-headless
  else
    sudo apt-get install -y wget gnupg ca-certificates
    wget -qO - https://packages.adoptium.net/artifactory/api/gpg/key/public \
      | sudo gpg --dearmor -o /usr/share/keyrings/adoptium.gpg --yes
    echo "deb [signed-by=/usr/share/keyrings/adoptium.gpg] https://packages.adoptium.net/artifactory/deb $(. /etc/os-release && echo "$VERSION_CODENAME") main" \
      | sudo tee /etc/apt/sources.list.d/adoptium.list >/dev/null
    sudo apt-get update -y
    sudo apt-get install -y temurin-25-jdk
  fi
  for d in /usr/lib/jvm/*; do
    if jdk25_mu "$d"; then JDK="$d"; break; fi
  done
fi
[ -n "$JDK" ] || { echo "Java 25 JDK kurulamadı. JDK=/yol ile elle göster."; exit 1; }
echo "JDK: $JDK"

renk "2/6 Maven kontrol ediliyor"
if ! command -v mvn >/dev/null 2>&1; then
  sudo apt-get update -y
  sudo apt-get install -y maven
fi
JAVA_HOME="$JDK" mvn -v | head -1

renk "3/6 Kaynak kod çıkarılıyor"
rm -rf "$SRC_ROOT"
mkdir -p "$SRC_ROOT"
if [ -n "${KAYNAK:-}" ]; then
  # guncelle komutu GitHub'dan indirilen klasörü verir
  for d in CayOcagi PolisKelepce Bekci yedek; do cp -r "$KAYNAK/$d" "$SRC_ROOT/"; done
  cp "$KAYNAK/guncelle.sh" "$SRC_ROOT/"
else
  echo '__PAYLOAD__' | base64 -d | tar -C "$SRC_ROOT" -xzf -
fi

renk "4/6 Derleniyor (ilk seferde bağımlılıklar iner, birkaç dakika sürebilir)"
for p in $PLUGINLER; do
  echo "--- $p"
  (cd "$SRC_ROOT/$p" && JAVA_HOME="$JDK" mvn -B -q package)
  ls -lh "$SRC_ROOT/$p/target/$p.jar"
done

renk "5/6 Sunucuya kuruluyor"
mkdir -p "$MC_DIR/plugins"
for p in $PLUGINLER; do
  # Aynı plugin'in farklı adlı eski jar'ı kalırsa Paper "ambiguous plugin" der
  rm -f "$MC_DIR/plugins/$p"*.jar
  cp "$SRC_ROOT/$p/target/$p.jar" "$MC_DIR/plugins/$p.jar"
done
renk "6/6 Saatlik yedek sistemi kuruluyor"
command -v tmux >/dev/null 2>&1 || sudo apt-get install -y tmux
mkdir -p "$YEDEK_DIR/arsiv"
cp "$SRC_ROOT/yedek/"*.sh "$YEDEK_DIR/"
chmod +x "$YEDEK_DIR/"*.sh
sudo tee /etc/systemd/system/minecraft-yedek.service >/dev/null <<BIRIM
[Unit]
Description=Minecraft saatlik yedek

[Service]
Type=oneshot
User=$(id -un)
ExecStart=/bin/bash $YEDEK_DIR/yedek.sh
BIRIM
sudo tee /etc/systemd/system/minecraft-yedek.timer >/dev/null <<BIRIM
[Unit]
Description=Minecraft yedeği her saat başı

[Timer]
OnCalendar=hourly
Persistent=true

[Install]
WantedBy=timers.target
BIRIM
sudo systemctl daemon-reload
sudo systemctl enable --now minecraft-yedek.timer >/dev/null
# Güncelleyici: 'guncelle' komutu ve her gece 05:00 (Türkiye) oyunda kimse yoksa otomatik güncelleme
sudo install -m 755 "$SRC_ROOT/guncelle.sh" /usr/local/bin/guncelle
sudo tee /etc/systemd/system/minecraft-guncelle.service >/dev/null <<BIRIM
[Unit]
Description=Minecraft eklentilerini GitHub'dan güncelle (oyunda kimse yoksa)
After=network-online.target

[Service]
Type=oneshot
User=$(id -un)
ExecStart=/usr/local/bin/guncelle gece
BIRIM
sudo tee /etc/systemd/system/minecraft-guncelle.timer >/dev/null <<BIRIM
[Unit]
Description=Her gece 05:00 güncelleme kontrolü

[Timer]
OnCalendar=*-*-* 05:00:00 Europe/Istanbul
RandomizedDelaySec=300

[Install]
WantedBy=timers.target
BIRIM
sudo systemctl daemon-reload
sudo systemctl enable --now minecraft-guncelle.timer >/dev/null

echo "Deneme yedeği alınıyor..."
if bash "$YEDEK_DIR/yedek.sh"; then
  ls -lh "$YEDEK_DIR/arsiv" | tail -1
else
  echo "Deneme yedeği alınamadı, ayrıntı: $YEDEK_DIR/yedek.log"
fi

if systemctl list-unit-files minecraft.service >/dev/null 2>&1 && systemctl list-unit-files minecraft.service | grep -q minecraft; then
  sudo systemctl restart minecraft
  echo
  echo "Tamam! Sunucu yeniden başlıyor (30-60 sn)."
else
  echo
  echo "Jar'lar plugins klasörüne kopyalandı. 'minecraft' servisi bulunamadı; sunucuyu kendin yeniden başlat."
fi
echo "  Çay:    /cay"
echo "  Polis:  /polis   (op olarak: /polis ekle OyuncuAdı)"
echo "  Başkan: /baskan  (op olarak: /baskan ata OyuncuAdı)"
echo "  Ceza:   /ceza"
echo "  Doktor: /doktor  (op olarak: /doktor ekle OyuncuAdı)"
echo "  Vergi:  /vergi   (başkan/op)     İdam: /idam OyuncuAdı [süre]  (başkan/op)"
echo "  Gıyabi idam (op): darağacının altında /idam cevrimdisi OyuncuAdı [süre]   Deneme: /idam test [OyuncuAdı]"
echo "  Diğer:  /sicil, /polis log, /siren, /pano kur, /kaldir, /ol, /mezar"
echo "  Bekçi:  /bekci   (op: kayıt, geri alma, sandık bulucu)"
echo "  Bedrock: /cay yap ..., /kelepce, /anahtar, /ara, /polis yap, /bekci git <no>"
echo
echo "  Yedek:  her saat başı -> $YEDEK_DIR/arsiv   (geri yükleme: bash $YEDEK_DIR/yedek-geri.sh)"
echo "          zamanlayıcı:  systemctl list-timers minecraft-yedek.timer"
echo
echo "  Güncelleme: sunucuda 'guncelle' yaz. Her gece 05:00'te oyunda kimse yoksa kendisi de günceller."
