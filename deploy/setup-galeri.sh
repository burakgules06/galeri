#!/usr/bin/env bash
# setup-galeri.sh — Galeri uygulamasini sunucuya ilk kez kurar. root olarak calistir.
# Tekrar calistirmak guvenli: var olani bozmaz, eksik kalan adimi tamamlar.
#
#  1) randevu-db container'inda ayri "galeri" veritabani + galeri_app kullanicisi
#  2) /opt/galeri/.env (sifre ve JWT anahtari burada uretilir, ekrana basilmaz)
#  3) galeri.service (dugun.service ornek alinir), 8082 / yonetim 9093
#  4) Nginx: galeri.akillirandevum.com (panel) + api-galeri.akillirandevum.com (API) + SSL
set -euo pipefail

PANEL_HOST=galeri.akillirandevum.com
API_HOST=api-galeri.akillirandevum.com
DB_CONTAINER=randevu-db
APP_PORT=8082
DIR=/opt/galeri

ok()   { echo -e "\e[32m[ok]\e[0m $*"; }
info() { echo -e "\e[34m[..]\e[0m $*"; }
warn() { echo -e "\e[33m[!!]\e[0m $*"; }
die()  { echo -e "\e[31m[xx]\e[0m $*"; exit 1; }

[[ $EUID -eq 0 ]] || die "root olarak calistir."

# --- On kontroller -----------------------------------------------------------
docker inspect "$DB_CONTAINER" >/dev/null 2>&1 || die "$DB_CONTAINER container'i bulunamadi."
[[ -f /etc/systemd/system/dugun.service ]] || die "dugun.service bulunamadi (ornek olarak kullaniliyor)."
[[ -f /opt/dugun/.env ]] || die "/opt/dugun/.env bulunamadi."
grep -q 'galeri)' /usr/local/bin/naiad-deploy 2>/dev/null \
  || warn "naiad-deploy galeri'yi tanimiyor: once update-scripts.sh calistir."
if ss -ltnH "sport = :$APP_PORT" | grep -q . && ! systemctl is-active -q galeri.service; then
  die "$APP_PORT portu baska bir surec tarafindan kullaniliyor: $(ss -ltnpH "sport = :$APP_PORT")"
fi

# --- 1) Veritabani -----------------------------------------------------------
PG_SUPER=$(docker exec "$DB_CONTAINER" printenv POSTGRES_USER)
psql_c() { docker exec -i "$DB_CONTAINER" psql -v ON_ERROR_STOP=1 -qtA -U "$PG_SUPER" -d postgres "$@"; }

if [[ -f $DIR/.env ]] && grep -q '^DB_PASSWORD=.\+' "$DIR/.env"; then
  DB_PASSWORD=$(grep '^DB_PASSWORD=' "$DIR/.env" | cut -d= -f2-)
else
  DB_PASSWORD=$(openssl rand -hex 24)
fi

if [[ $(psql_c -c "SELECT 1 FROM pg_roles WHERE rolname='galeri_app'") == 1 ]]; then
  psql_c -c "ALTER ROLE galeri_app WITH LOGIN PASSWORD '$DB_PASSWORD'"
  ok "galeri_app kullanicisi zaten var, sifresi .env ile esitlendi"
else
  psql_c -c "CREATE ROLE galeri_app WITH LOGIN PASSWORD '$DB_PASSWORD'"
  ok "galeri_app kullanicisi olusturuldu"
fi
if [[ $(psql_c -c "SELECT 1 FROM pg_database WHERE datname='galeri'") == 1 ]]; then
  ok "galeri veritabani zaten var"
else
  psql_c -c "CREATE DATABASE galeri OWNER galeri_app ENCODING 'UTF8'"
  ok "galeri veritabani olusturuldu (sahibi galeri_app)"
fi
# Diger uygulamalarin veritabanlarina baglanamasin
psql_c -c "REVOKE CONNECT ON DATABASE galeri FROM PUBLIC" >/dev/null

# --- 2) /opt/galeri ve .env ---------------------------------------------------
# Servis kullanicisi dugun'unkinden turetilir (User=dugun -> galeri, yoksa root)
DUGUN_USER=$(sed -n 's/^User=//p' /etc/systemd/system/dugun.service | head -n1)
SVC_USER=${DUGUN_USER//dugun/galeri}
SVC_USER=${SVC_USER:-root}
if ! id "$SVC_USER" >/dev/null 2>&1; then
  useradd --system --no-create-home --shell /usr/sbin/nologin "$SVC_USER"
  ok "Servis kullanicisi olusturuldu: $SVC_USER"
fi
mkdir -p "$DIR"
DUGUN_URL=$(grep '^DB_URL=' /opt/dugun/.env | cut -d= -f2-)
DB_URL="${DUGUN_URL%/*}/galeri"        # ayni host:port, sadece veritabani adi farkli

if [[ -f $DIR/.env ]]; then
  ok "$DIR/.env zaten var, dokunulmadi"
else
  cat > "$DIR/.env" <<EOF
DB_URL=$DB_URL
DB_USER=galeri_app
DB_PASSWORD=$DB_PASSWORD
JWT_SECRET=$(openssl rand -hex 32)

SERVER_ADDRESS=127.0.0.1
SERVER_PORT=$APP_PORT
CORS_ALLOWED_ORIGINS=https://$PANEL_HOST

MANAGEMENT_SERVER_PORT=9093
MANAGEMENT_SERVER_ADDRESS=127.0.0.1
EOF
  chown root:root "$DIR/.env"
  chmod 600 "$DIR/.env"            # systemd EnvironmentFile'i root olarak okur
  ok "$DIR/.env olusturuldu (DB: $DB_URL)"
fi
# naiad-deploy ilk seferde yedek alabilsin diye bos yer tutucu
[[ -e $DIR/app.jar ]] || { touch "$DIR/app.jar"; chmod 644 "$DIR/app.jar"; }
chown "$SVC_USER": "$DIR"

# --- 3) systemd servisi -------------------------------------------------------
if [[ -f /etc/systemd/system/galeri.service ]]; then
  ok "galeri.service zaten var"
else
  sed -e 's/dugun/galeri/g' -e 's/Dugun/Galeri/g' -e 's/Düğün/Galeri/g' \
    /etc/systemd/system/dugun.service > /etc/systemd/system/galeri.service
  systemctl daemon-reload
  systemctl enable galeri.service >/dev/null 2>&1
  ok "galeri.service olusturuldu (kullanici: $SVC_USER), acilista baslayacak; ilk deploy'a kadar calismaz"
  echo "------ /etc/systemd/system/galeri.service ------"; cat /etc/systemd/system/galeri.service; echo "------------------------------------------------"
fi

# --- 4) Nginx + SSL -----------------------------------------------------------
mkdir -p /var/www/galeri-panel
[[ -f /var/www/galeri-panel/index.html ]] || echo '<!doctype html><meta charset="utf-8"><title>Galeri</title><p>Galeri paneli yakinda.</p>' > /var/www/galeri-panel/index.html
chmod -R a+rX /var/www/galeri-panel

if [[ -d /etc/nginx/sites-available ]]; then
  CONF=/etc/nginx/sites-available/galeri; LINK=/etc/nginx/sites-enabled/galeri
else
  CONF=/etc/nginx/conf.d/galeri.conf; LINK=""
fi

if [[ -f $CONF ]]; then
  ok "Nginx ayari zaten var: $CONF"
else
  cat > "$CONF" <<EOF
# Galeri paneli (statik)
server {
    listen 80;
    server_name $PANEL_HOST;
    root /var/www/galeri-panel;
    index index.html;

    location /assets/ {
        expires 1y;
        add_header Cache-Control "public, immutable";
    }
    location / {
        try_files \$uri /index.html;
        add_header Cache-Control "no-cache";
    }
}

# Galeri API
server {
    listen 80;
    server_name $API_HOST;
    client_max_body_size 20m;

    location / {
        proxy_pass http://127.0.0.1:$APP_PORT;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
    }
    # Actuator disariya kapali (izleme 127.0.0.1:9093'ten)
    location /actuator { return 404; }
}
EOF
  [[ -n $LINK ]] && ln -sf "$CONF" "$LINK"
  nginx -t && systemctl reload nginx
  ok "Nginx ayari yazildi: $CONF"
fi

SERVER_IP=$(curl -fsS --max-time 5 https://api.ipify.org || true)
dns_ok=1
for h in "$PANEL_HOST" "$API_HOST"; do
  ip=$(getent ahostsv4 "$h" | awk 'NR==1{print $1}')
  if [[ -z $ip ]]; then warn "$h DNS kaydi yok"; dns_ok=0
  elif [[ -n $SERVER_IP && $ip != "$SERVER_IP" ]]; then warn "$h -> $ip (bu sunucu $SERVER_IP)"; dns_ok=0
  else ok "$h -> $ip"; fi
done

if [[ $dns_ok == 1 ]]; then
  if grep -q 'ssl_certificate' "$CONF"; then
    ok "SSL zaten kurulu"
  else
    certbot --nginx --non-interactive --redirect -d "$PANEL_HOST" -d "$API_HOST"
    ok "SSL kuruldu"
  fi
else
  warn "SSL atlandi. DNS'e iki A kaydi ekle ($PANEL_HOST ve $API_HOST -> ${SERVER_IP:-sunucu IP}), sonra bu script'i tekrar calistir."
fi

echo
ok "Kurulum tamam. Siradaki: GitHub'da galeri repo'sunda Actions > Deploy (prod) > Run workflow."
echo "   Deploy sonrasi kontrol:  curl -s 127.0.0.1:9093/actuator/health"
