#!/usr/bin/env bash
# Prepara o banco de TESTE do SciPath para a suíte de backend (card E1.5).
#
# Por que existe: o card E1.5 exige que `make test` seja reproduzível de um shell limpo,
# sem depender de estado invisível (o `.env` do implementador). A suíte fala com
# `scipath_test` e precisa AUTENTICAR sem colocar senha nenhuma no repositório (RNF012).
#
# Escolha registrada (opção (i) do card): role de banco `scipath_test`, sem senha, com
# `trust` no pg_hba — e a regra é restrita ao banco `scipath_test`, à role `scipath_test`
# e ao loopback (127.0.0.1/::1). Nenhum segredo é versionado; o `.env` com a senha do
# banco de desenvolvimento deixa de ser pré-requisito para rodar a suíte.
#
# Idempotente: pode rodar de novo sem quebrar nada. NUNCA imprime senha (não há nenhuma).
# O banco `scipath_test` é RECRIADO do zero — é banco de teste, não guarda nada durável,
# e recriá-lo é o que permite a suíte provar que as migrações são aplicadas a partir vazio.
set -euo pipefail

HBA="/etc/postgresql/16/main/pg_hba.conf"
PGVER_DIR="/etc/postgresql/16/main"
DB="scipath_test"
ROLE="scipath_test"
# Role dona do banco de desenvolvimento (criada por infra/db-setup.sh). Ela continua com
# acesso ao banco de teste para não quebrar os scripts de evidência dos cards E1.2/E1.3,
# que usam o .env (SPRING_DATASOURCE_URL_TEST).
DEV_ROLE="${DB_USER:-scipath}"

psql_su() { sudo -u postgres psql -v ON_ERROR_STOP=1 "$@"; }

echo "== 1. role de teste ($ROLE) sem senha =="
if [ "$(sudo -u postgres psql -t -A -c "select 1 from pg_roles where rolname='${ROLE}'")" = "1" ]; then
  echo "role ${ROLE} já existe — garantindo LOGIN e ausência de senha"
  psql_su -c "ALTER ROLE ${ROLE} WITH LOGIN PASSWORD NULL;" >/dev/null
else
  echo "criando role ${ROLE} (LOGIN, PASSWORD NULL)"
  psql_su -c "CREATE ROLE ${ROLE} WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD NULL;" >/dev/null
fi

echo "== 2. pg_hba: regra trust restrita (banco + role + loopback) =="
if sudo grep -q "scipath-test" "$HBA"; then
  echo "regra scipath-test já presente em $HBA — não duplicando"
else
  backup="${HBA}.scipath.orig"
  if ! sudo test -f "$backup"; then
    echo "guardando cópia original em ${backup}"
    sudo cp "$HBA" "$backup"
  fi
  tmp="$(mktemp)"
  sudo cat "$HBA" > "$tmp"
  awk '
    /^#[[:space:]]*TYPE[[:space:]]+DATABASE/ && !done {
      print
      print ""
      print "# BEGIN scipath-test — role de teste sem senha (gerado por infra/db-test-setup.sh)."
      print "# Restrito ao banco, à role e ao loopback. Precisa vir ANTES da regra genérica"
      print "# scram-sha-256 de 127.0.0.1: no pg_hba a primeira regra que casa é a que vale."
      print "host    scipath_test  scipath_test  127.0.0.1/32    trust"
      print "host    scipath_test  scipath_test  ::1/128         trust"
      print "# END scipath-test"
      done=1
      next
    }
    { print }
  ' "$tmp" > "${tmp}.novo"
  sudo cp "${tmp}.novo" "$HBA"
  rm -f "$tmp" "${tmp}.novo"
  echo "regra inserida"
fi

echo "== 3. recarregar a configuração do Postgres =="
psql_su -c "SELECT pg_reload_conf();" >/dev/null
echo "recarregado"

echo "== 4. recriar o banco ${DB}, dono ${ROLE} =="
psql_su -c "DROP DATABASE IF EXISTS ${DB} WITH (FORCE);" >/dev/null
psql_su -c "CREATE DATABASE ${DB} OWNER ${ROLE};" >/dev/null
# A role de desenvolvimento também precisa do banco de teste (scripts de evidência E1.2/E1.3).
psql_su -c "GRANT CONNECT, TEMPORARY ON DATABASE ${DB} TO ${DEV_ROLE};" >/dev/null
psql_su -d "${DB}" -c "GRANT ALL ON SCHEMA public TO ${DEV_ROLE};" >/dev/null
psql_su -d "${DB}" -c "ALTER DEFAULT PRIVILEGES FOR ROLE ${ROLE} IN SCHEMA public GRANT ALL ON TABLES TO ${DEV_ROLE};" >/dev/null
psql_su -d "${DB}" -c "ALTER DEFAULT PRIVILEGES FOR ROLE ${ROLE} IN SCHEMA public GRANT ALL ON SEQUENCES TO ${DEV_ROLE};" >/dev/null

echo "== 5. prova de conexão sem senha (trust) =="
PGPASSWORD="" psql -h 127.0.0.1 -p 5432 -U "${ROLE}" -d "${DB}" \
  -t -A -c "SELECT 'conexao sem senha ok: ' || current_user || ' @ ' || current_database();"

echo "== 6. estado inicial do banco de teste (vazio, sem migração aplicada) =="
PGPASSWORD="" psql -h 127.0.0.1 -p 5432 -U "${ROLE}" -d "${DB}" -c "\dt"

echo "== pronto. A suíte (make test) aplica as migrações em ${DB} com a role ${ROLE}. =="