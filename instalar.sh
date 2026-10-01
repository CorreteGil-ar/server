#!/bin/bash
# Instala el server TresModos (Paper 26.3 + plugin) en Ubuntu 24.04.
# Uso:  sudo bash instalar.sh [tu_usuario_de_minecraft] [amigos...]
# El primer usuario queda como operador; todos entran a la lista blanca (se pueden agregar después con mc).
set -euo pipefail

DIR=/opt/minecraft
AQUI="$(cd "$(dirname "$0")" && pwd)"
PAPER_URL="https://fill-data.papermc.io/v1/objects/98aabc113a80b9b5e183475e839a17cf99c39c915a1f46b8f35a5e89fd5de0f1/paper-26.3-140.jar"
PAPER_SHA="98aabc113a80b9b5e183475e839a17cf99c39c915a1f46b8f35a5e89fd5de0f1"
VIA_URL="https://hangarcdn.papermc.io/plugins/ViaVersion/ViaVersion/versions/5.12.0/PAPER/ViaVersion-5.12.0.jar"

if [ "$(id -u)" -ne 0 ]; then echo "Correlo con sudo."; exit 1; fi

# El plugin puede venir armado (TresModos.jar) o como clases sueltas en plugin/
if [ ! -f "$AQUI/TresModos.jar" ] && [ -d "$AQUI/plugin" ]; then
  (cd "$AQUI/plugin" && python3 -m zipfile -c "$AQUI/TresModos.jar" plugin.yml ar)
fi

echo "==> Instalando Java 25"
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -y -qq openjdk-25-jre-headless curl python3 >/dev/null

# El plugin puede venir armado (TresModos.jar) o como código fuente (pom.xml + src/)
if [ ! -f "$AQUI/TresModos.jar" ] && [ -f "$AQUI/pom.xml" ]; then
  echo "==> Compilando el plugin desde el código fuente"
  apt-get install -y -qq openjdk-25-jdk-headless maven >/dev/null
  (cd "$AQUI" && mvn -q -B package 2>&1 | grep -vE "WARNING|Unsafe" || true)
  cp "$AQUI/target/TresModos.jar" "$AQUI/TresModos.jar"
fi

echo "==> Preparando $DIR"
id minecraft >/dev/null 2>&1 || useradd -r -m -d "$DIR" -s /usr/sbin/nologin minecraft
mkdir -p "$DIR/plugins"
curl -fsSL -o "$DIR/paper.jar" "$PAPER_URL"
echo "$PAPER_SHA  $DIR/paper.jar" | sha256sum -c -
curl -fsSL -o "$DIR/plugins/ViaVersion.jar" "$VIA_URL"
cp "$AQUI/TresModos.jar" "$DIR/plugins/TresModos.jar"
echo "eula=true" > "$DIR/eula.txt"   # EULA de Mojang aceptado por el dueño del server

if [ ! -f "$DIR/server.properties" ]; then
  RCON_PASS="$(head -c 24 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 24)"
  cat > "$DIR/server.properties" <<EOF
motd=TresModos · GTA · COD · RPG
online-mode=true
white-list=true
enforce-whitelist=true
max-players=12
view-distance=8
simulation-distance=6
difficulty=normal
pvp=true
spawn-protection=0
server-port=25565
enable-rcon=true
rcon.port=25575
rcon.password=$RCON_PASS
broadcast-rcon-to-ops=false
EOF
fi
install -m 755 "$AQUI/mc" /usr/local/bin/mc
chown -R minecraft:minecraft "$DIR"

echo "==> Creando el servicio"
cat > /etc/systemd/system/minecraft.service <<'EOF'
[Unit]
Description=Minecraft TresModos (Paper)
After=network-online.target
Wants=network-online.target

[Service]
User=minecraft
WorkingDirectory=/opt/minecraft
ExecStart=/usr/bin/java -Xms1G -Xmx2560M -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:+AlwaysPreTouch -XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 -XX:G1HeapRegionSize=8M -XX:G1ReservePercent=20 -XX:InitiatingHeapOccupancyPercent=15 -jar paper.jar --nogui
Restart=on-failure
RestartSec=10
KillSignal=SIGTERM
TimeoutStopSec=120
SuccessExitStatus=0 143

[Install]
WantedBy=multi-user.target
EOF
systemctl daemon-reload
systemctl enable --now minecraft

echo "==> Apagado automático: desasigna la VM tras 30 min sin jugadores"
install -m 755 "$AQUI/apagado-inactivo.sh" /usr/local/bin/apagado-inactivo
cat > /etc/systemd/system/apagado-inactivo.service <<'EOF'
[Unit]
Description=Desasigna la VM si el server de Minecraft está vacío 30 minutos

[Service]
Type=oneshot
ExecStart=/usr/local/bin/apagado-inactivo
EOF
cat > /etc/systemd/system/apagado-inactivo.timer <<'EOF'
[Unit]
Description=Revisa cada 5 minutos si el server está vacío

[Timer]
OnBootSec=5min
OnUnitActiveSec=5min

[Install]
WantedBy=timers.target
EOF
systemctl daemon-reload
systemctl enable --now apagado-inactivo.timer

echo "==> Esperando que arranque (la primera vez tarda 1-2 minutos)"
for i in $(seq 1 90); do
  if journalctl -u minecraft --no-pager 2>/dev/null | grep -q "Done ("; then break; fi
  sleep 3
done
journalctl -u minecraft --no-pager | grep -E "TresModos listo|Done \(" | tail -2 || true

echo "==> Lista blanca y operador"
PRIMERO=1
for U in "$@"; do
  mc "whitelist add $U" || true
  if [ $PRIMERO -eq 1 ]; then mc "op $U" || true; PRIMERO=0; fi
done

IP="$(curl -fsS -4 https://api.ipify.org 2>/dev/null || hostname -I | awk '{print $1}')"
echo
echo "Listo. Conectate a:  $IP:25565"
echo "Consola:  mc \"comando\"      Logs:  journalctl -u minecraft -f"
