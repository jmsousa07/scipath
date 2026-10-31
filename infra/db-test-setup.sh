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
#
# Card E1.12: além de recriar o banco, este script neutraliza o DONO dos objetos do banco de
# teste. As duas formas de migrar (`make test`, que usa a role de teste, e `make
# db:migrate:test`/scripts de evidência, que usam a role de dev) passam a produzir um banco
# utilizável pela outra role — antes disso, migrar pela role de dev deixava a suíte com
# "permission denied for table category". Detalhes no passo 4.
#
# Card E1.5-f1: "utilizável pela outra role" não bastou quando a suíte passou a fazer DDL
# (gatilho de injeção de falha, Etapa 4). Conceder privilégio não cobre criar/derrubar gatilho
# — isso é POSSE do objeto, que não se concede. O passo 4 passa a manter UMA role dona de tudo
# o que existe no banco de teste (a role de teste) e faz a role de dev ATUAR como ela aqui.
# Detalhes no passo 4 e em infra/README.md.
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

# --- card E1.12: o DONO dos objetos do banco de teste é irrelevante ---------------------
# Há DUAS formas de migrar este banco e elas usam roles diferentes:
#   (a) o Flyway da própria aplicação (`make test` → o contexto sobe com a role de teste
#       `scipath_test`, que fica dona das tabelas); e
#   (b) o plugin Flyway do Maven (`make db:migrate:test` e os scripts de evidência dos cards
#       E1.2/E1.3/E1.2a, que usam SPRING_DATASOURCE_USERNAME do .env = role de dev `scipath`).
# Sem o par de privilégios padrão abaixo, quem migrava por (b) deixava as tabelas com dono
# `scipath` e a suíte — que conecta como `scipath_test` — morria com "permission denied for
# table category" (achado reproduzível do card E1.8 → card E1.12).
# O sentido `scipath_test` → `scipath` já existia desde o E1.5; aqui fecha-se a recíproca,
# de modo que objetos criados por QUALQUER uma das duas roles sejam legíveis/graváveis pela
# outra, sem que ninguém precise adivinhar quem migrou por último.
psql_su -d "${DB}" -c "ALTER DEFAULT PRIVILEGES FOR ROLE ${DEV_ROLE} IN SCHEMA public GRANT ALL ON TABLES TO ${ROLE};" >/dev/null
psql_su -d "${DB}" -c "ALTER DEFAULT PRIVILEGES FOR ROLE ${DEV_ROLE} IN SCHEMA public GRANT ALL ON SEQUENCES TO ${ROLE};" >/dev/null

# --- card E1.5-f1: o dono do objeto decide DDL — UMA role só dona o banco de teste -------
# O `ALTER DEFAULT PRIVILEGES` acima cobre DML (SELECT/INSERT/UPDATE/DELETE/TRUNCATE). Era o
# que bastava enquanto o banco de teste só era LIDO e ESCRITO. A Etapa 4 trouxe testes de
# integração que fazem **DDL** aqui — `RollbackDaPublicacaoIntegrationTest` e
# `ColetaDeOrfaosIntegrationTest` criam FUNÇÃO e GATILHO para injetar a falha (RN008) — e DDL
# não se concede por GRANT: criar/derrubar gatilho, `DROP TABLE` e `ALTER TABLE` checam a
# POSSE do objeto (`pg_class_ownercheck` → `has_privs_of_role`). Com as tabelas no dono da
# outra role, a suíte morria em `ERROR: must be owner of relation material`.
#
# A associação mútua (cada role membro da outra) NÃO é caminho: o PostgreSQL 16 recusa o ciclo
# — `GRANT ${DEV_ROLE} TO ${ROLE}` depois de `GRANT ${ROLE} TO ${DEV_ROLE}` falha com
# `ERROR: role "${DEV_ROLE}" is a member of role "${ROLE}"` (medido em laboratório PostgreSQL
# 16.13, `docs/qa/e1-5-f1-mecanismo-*.log`). Só existe UM sentido possível.
#
# Decisão: **uma role só DONA o que existe em `${DB}`** — a role de teste, dona do banco. A role
# de dev continua AUTENTICANDO com a própria senha do `.env` (é o que mantém a evidência do
# E1.2b: senha fora do argv), mas toda sessão dela NO BANCO DE TESTE atua como a role de teste:
#   * `GRANT ${ROLE} TO ${DEV_ROLE}` — a role de dev é MEMBRO da role de teste (necessário para
#     o `SET role` abaixo ser permitido);
#   * `ALTER ROLE ${DEV_ROLE} IN DATABASE ${DB} SET role TO ${ROLE}` — `SET ROLE` automático na
#     abertura da sessão, restrito a ESTE banco. Efeito: qualquer DDL que a role de dev faça
#     aqui (o plugin do Maven em `make db:migrate:test`, os scripts de evidência do E1.2/E1.3)
#     cria objetos que já nascem com dono `${ROLE}`; e qualquer DROP/ALTER que ela faça funciona,
#     porque ela É a dona. O banco de desenvolvimento `${DEV_ROLE}` NÃO é afetado (o `SET` é por
#     banco), e a suíte não é afetada (ela conecta como `${ROLE}`, sem `SET` nenhum).
# Com isso o banco de teste é utilizável pelas duas roles em QUALQUER ordem, que é o requisito
# do card. Os `ALTER DEFAULT PRIVILEGES` acima continuam (são inofensivos) e passam a ser
# redundantes para a role de dev — quem herda o papel da dona já tem DML; ficam como piso
# explícito de DML para uma sessão que, por algum motivo, não receba o `SET role`.
# As duas linhas são idempotentes (GRANT repetido e ALTER repetido só emitem NOTICE).
# Decisão registrada e alternativa descartada em infra/README.md §E1.5-f1.
psql_su -c "GRANT ${ROLE} TO ${DEV_ROLE};" >/dev/null
psql_su -c "ALTER ROLE ${DEV_ROLE} IN DATABASE ${DB} SET role TO ${ROLE};" >/dev/null

echo "== 5. prova de conexão sem senha (trust) =="
PGPASSWORD="" psql -h 127.0.0.1 -p 5432 -U "${ROLE}" -d "${DB}" \
  -t -A -c "SELECT 'conexao sem senha ok: ' || current_user || ' @ ' || current_database();"

echo "== 5.1 uma role só dona o banco de teste (card E1.5-f1) =="
psql_su -t -A -c "SELECT '${DEV_ROLE} e membro de ${ROLE} = ' || pg_has_role('${DEV_ROLE}','${ROLE}','MEMBER');"
echo "-- 'SET role' automático: toda sessão de ${DEV_ROLE} em ${DB} atua como ${ROLE} --"
echo "   (pg_db_role_setting da role ${DEV_ROLE} no banco ${DB}; configuracao esperada: role=${ROLE})"
psql_su -t -A -c "SELECT coalesce((SELECT string_agg(c, ', ') FROM pg_db_role_setting s JOIN pg_database d ON d.oid = s.setdatabase JOIN pg_roles r ON r.oid = s.setrole, unnest(s.setconfig) c WHERE d.datname = '${DB}' AND r.rolname = '${DEV_ROLE}'), '<nenhuma>');"
echo "-- dono(s) dos objetos de public em ${DB} (esperado: só ${ROLE}) --"
psql_su -d "${DB}" -t -A -c "SELECT coalesce(string_agg(DISTINCT pg_get_userbyid(relowner), ', '), '<nenhum objeto>') FROM pg_class WHERE relnamespace = 'public'::regnamespace AND relkind IN ('r','S','v');"

echo "== 6. estado inicial do banco de teste (vazio, sem migração aplicada) =="
PGPASSWORD="" psql -h 127.0.0.1 -p 5432 -U "${ROLE}" -d "${DB}" -c "\dt"

echo "== pronto. banco de teste recriado e provisão de acesso concluída. =="
echo "   O banco ${DB} tem UMA dona: a role ${ROLE} (card E1.5-f1). A role ${DEV_ROLE}"
echo "   autentica com a senha dela, mas toda sessão dela NESTE banco atua como ${ROLE} — então"
echo "   migrar por qualquer das duas roles deixa objetos no dono certo, e DDL (gatilho,"
echo "   índice, tabela) funciona para a suíte e para a role de dev. Ver infra/README.md."