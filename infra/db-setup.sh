#!/usr/bin/env bash
# Cria o banco local do SciPath e escreve o .env com uma senha gerada.
# NUNCA imprime a senha. Idempotente: pode rodar de novo sem quebrar nada.
set -euo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DB_NAME="${DB_NAME:-scipath}"
DB_USER="${DB_USER:-scipath}"
DB_HOST="127.0.0.1"; DB_PORT="5432"

if [[ "${1:-}" == "--reset" && "${2:-}" != "--yes-reset" ]]; then
  echo "Para recriar o banco (destrutivo) rode: infra/db-setup.sh --reset --yes-reset"
  exit 1
fi

if [[ "${1:-}" == "--reset" && "${2:-}" == "--yes-reset" ]]; then
  echo "== reset: derrubando $DB_NAME =="
  sudo -u postgres psql -v ON_ERROR_STOP=1 -c "DROP DATABASE IF EXISTS ${DB_NAME};"
fi

PW="$(python3 -c 'import secrets; print(secrets.token_urlsafe(24))')"

if sudo -u postgres psql -tAc "SELECT 1 FROM pg_roles WHERE rolname='${DB_USER}'" | grep -q 1; then
  echo "== role ${DB_USER} já existe: rotacionando senha =="
  sudo -u postgres psql -v ON_ERROR_STOP=1 -c "ALTER ROLE ${DB_USER} WITH LOGIN PASSWORD '${PW}';" >/dev/null
else
  echo "== criando role ${DB_USER} =="
  sudo -u postgres psql -v ON_ERROR_STOP=1 -c "CREATE ROLE ${DB_USER} WITH LOGIN PASSWORD '${PW}';" >/dev/null
fi

if sudo -u postgres psql -tAc "SELECT 1 FROM pg_database WHERE datname='${DB_NAME}'" | grep -q 1; then
  echo "== banco ${DB_NAME} já existe =="
else
  echo "== criando banco ${DB_NAME} =="
  sudo -u postgres createdb -O "${DB_USER}" "${DB_NAME}"
fi

sudo -u postgres psql -v ON_ERROR_STOP=1 -d "${DB_NAME}" -c "GRANT ALL ON SCHEMA public TO ${DB_USER};" >/dev/null
sudo -u postgres psql -v ON_ERROR_STOP=1 -c "CREATE DATABASE ${DB_NAME}_test OWNER ${DB_USER};" 2>/dev/null \
  || echo "== banco de teste ${DB_NAME}_test já existe =="

umask 077
cat > "${REPO}/.env" <<EOF
# Gerado por infra/db-setup.sh — NÃO versionar (está no .gitignore).
# Nunca imprima este arquivo em card, log de evidência ou conversa.
SPRING_DATASOURCE_URL=jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}
SPRING_DATASOURCE_USERNAME=${DB_USER}
SPRING_DATASOURCE_PASSWORD=${PW}
SPRING_DATASOURCE_URL_TEST=jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}_test
API_BASE_URL=http://localhost:8080/api
EOF

echo "== .env escrito em ${REPO}/.env (senha de 32+ chars, não exibida) =="
echo "== pronto. Teste de conexão: =="
PGPASSWORD="${PW}" psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${DB_NAME}" -tAc "SELECT 'conexao ok: ' || current_database();"
