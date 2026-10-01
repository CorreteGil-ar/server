#!/bin/bash
# Apaga (desasigna) la VM si el server estuvo vacío 30 minutos seguidos.
# Lo corre un timer de systemd cada 5 minutos. Desasignar = Azure deja de cobrar la VM.
ESTADO=/var/lib/tresmodos-vacio-desde
LIMITE=1800

UPTIME_MIN=$(awk '{print int($1/60)}' /proc/uptime)
if [ "$UPTIME_MIN" -lt 15 ]; then exit 0; fi   # recién prendida: margen para entrar

# Cantidad de jugadores; si el server no responde, cuenta como vacío
N=$(/usr/local/bin/mc list 2>/dev/null | grep -oE '[0-9]+' | head -1)
N=${N:-0}
if [ "$N" -gt 0 ]; then
  rm -f "$ESTADO"
  exit 0
fi

[ -f "$ESTADO" ] || date +%s > "$ESTADO"
DESDE=$(cat "$ESTADO")
AHORA=$(date +%s)
if [ $((AHORA - DESDE)) -lt $LIMITE ]; then exit 0; fi

rm -f "$ESTADO"
logger -t tresmodos "Server vacío 30 min: guardo el mundo y desasigno la VM"
systemctl stop minecraft

TOKEN=$(curl -fsS -H Metadata:true \
  "http://169.254.169.254/metadata/identity/oauth2/token?api-version=2018-02-01&resource=https://management.azure.com/" \
  | python3 -c 'import sys, json; print(json.load(sys.stdin)["access_token"])')
ID=$(curl -fsS -H Metadata:true \
  "http://169.254.169.254/metadata/instance/compute/resourceId?api-version=2021-02-01&format=text")
curl -fsS -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Length: 0" \
  "https://management.azure.com${ID}/deallocate?api-version=2024-07-01" \
  && logger -t tresmodos "Pedido de desasignación enviado" \
  || { logger -t tresmodos "No pude desasignar: reviso la identidad administrada"; systemctl start minecraft; }
