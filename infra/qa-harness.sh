#!/usr/bin/env bash
# ============================================================================
# SciPath — harness de evidência de QA (card E1.8)
# ============================================================================
#
# Por que existe: a verificação da base nunca aconteceu porque não havia um
# instrumento repetível. Este script É o instrumento. Ele roda a bateria de
# ponta a ponta e imprime, ao fim, um veredito por critério — ATENDE /
# NÃO ATENDE / NÃO VERIFICADO — nunca "tudo ok" sem lista (DRS §21.4).
#
# Roda "do zero": recria o banco de teste, aplica as migrações, exercita a
# reversão, roda a suíte em shell limpo, valida o contrato, mede o frontend e
# faz a higiene. Não depende de estado deixado por outro agente.
#
# Uso (a evidência é a saída; grave-a versionada — AGENTS.md, DRS §17.5/§21.3):
#   bash infra/qa-harness.sh | tee docs/qa/e1-base-$(date -u +%Y-%m-%d).log
#
# Nunca imprime segredo: o .env é carregado com `set -a` só para psql/flyway, e
# a senha nunca vai para a saída (há conferência no fim).
#
# Ferramentas: bash, git, make, java, psql, node/npm/npx. Sem Docker (ADR-0007).
# ============================================================================
set -uo pipefail

# ---------------------------------------------------------------- raízes
WORKTREE="$(git rev-parse --show-toplevel)"
GIT_COMMON="$(git rev-parse --path-format=absolute --git-common-dir)"
MAIN_ROOT="$(cd "$GIT_COMMON/.." && pwd)"
ENV_FILE="$MAIN_ROOT/.env"
DOCS_QA="$WORKTREE/docs/qa"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

# A suíte de backend roda em PostgreSQL local, sem Docker.
PGHOST_=127.0.0.1; PGPORT_=5432

# ---------------------------------------------------------------- veredito
declare -a V_CRIT=() V_RES=() V_NOTA=()
veredito() { V_CRIT+=("$1"); V_RES+=("$2"); V_NOTA+=("${3:-}"); }
declare -a ACHADOS=()
achado() { ACHADOS+=("$1"); }

# ------------------------------------------------------- E5.10-f1: decisão do ACHADO do E1.8
# Único lugar onde se decide se o harness DECLARA o achado de permissão do E1.8. Fica em
# função justamente para que a evidência (docs/qa/e5-10-f1-evidencia.sh) extraia e exercite
# EXATAMENTE esta regra — sem uma segunda cópia que possa divergir do instrumento.
# Três estados, nunca dois: a ausência de medição não é prova da ausência do achado.
# >>> E5.10-f1-decisao >>>
medicao_disponivel() { # $1=exit do psql  $2=resultado  -> 'sim' (houve medição) | 'nao'
  local rc="$1" res="$2"
  if [[ "$rc" -ne 0 || -z "$res" ]]; then echo "nao"; else echo "sim"; fi
}
estado_achado_e18() { # -> REPRODUZIDO | NAO_REPRODUZIDO | INDISPONIVEL
  # $1 = medicao_disponivel · $2 = dono da tabela 'category' · $3 = has_table_privilege
  # da role da suíte em 'category' · $4 = tabelas de public que a suíte NÃO alcança
  local medicao_ok="$1" dono_categoria="$2" priv_categoria="$3" tabelas_sem_acesso="$4"
  if [[ "$medicao_ok" != "sim" ]]; then
    echo "INDISPONIVEL"; return
  fi
  # Correlação POR TABELA: a condição é um objeto concreto fora do alcance da suíte. A lista
  # vem de pg_class x dono x privilégio, um a um — estado MISTO (parte das tabelas com a role
  # da suíte, parte não) é declarado, não suprimido por um nome que "aparece no meio".
  if [[ -n "$tabelas_sem_acesso" ]]; then
    echo "REPRODUZIDO"; return
  fi
  # Prova direta pedida no card: se 'category' é de outra role E a suíte não a lê, o achado é o
  # do E1.8 ('permission denied for table category'), mesmo que a lista acima venha vazia.
  if [[ "$dono_categoria" != "scipath_test" && "$priv_categoria" = "f" ]]; then
    echo "REPRODUZIDO"; return
  fi
  echo "NAO_REPRODUZIDO"
}
# <<< E5.10-f1-decisao <<<

# ---------------------------------------------------------------- utilidades
secao() {
  printf '\n\n================================================================================\n%s\n================================================================================\n' "$*"
}
h2() {
  printf '\n--------------------------------------------------------------------------------\n%s\n--------------------------------------------------------------------------------\n' "$*"
}
# Executa, imprime a saída crua (sem cores) e devolve o exit code. Guarda a
# saída em $TMP/ultimo.log para greps posteriores.
ultimo_log=""
executar() {
  local rotulo="$1"; shift
  echo "\$ $*"
  local log="$TMP/$(echo "$rotulo" | tr -c '[:alnum:]' '_').log"
  "$@" >"$log" 2>&1
  local cod=$?
  sed -r 's/\x1B\[[0-9;]*[A-Za-z]//g' "$log"
  echo "---- [$rotulo] exit=$cod"
  ultimo_log="$log"
  return $cod
}

# ============================================================================
secao "SciPath — harness de QA da Etapa 1 (card E1.8)"
echo "data (UTC):   $(date -u +%Y-%m-%dT%H:%M:%SZ)"
echo "host:         $(uname -sr) · $(nproc) vCPU · $(free -m | awk '/^Mem:/{print $2" MB RAM"}')"
echo "worktree:     $WORKTREE"
echo "branch:       $(git -C "$WORKTREE" branch --show-current)"
echo "HEAD:         $(git -C "$WORKTREE" rev-parse --short HEAD) $(git -C "$WORKTREE" log -1 --pretty=%s)"
echo "raiz (.env):  $MAIN_ROOT"
echo "ferramentas:  java=$(java -version 2>&1 | head -1 | awk '{print $3}') · node=$(node -v 2>/dev/null || echo AUSENTE) · make=$(make --version | head -1)"
echo ".env:         $([ -f "$ENV_FILE" ] && echo presente || echo AUSENTE)"

if [[ ! -f "$ENV_FILE" ]]; then
  echo
  echo "ERRO: $ENV_FILE ausente. Rode infra/db-setup.sh antes de rodar o harness." >&2
  exit 1
fi

secao "0. Ponto de partida — higiene do ambiente"
echo "-- git status (esperado: limpo após o commit da evidência; o log é o que muda)"
git -C "$WORKTREE" status --short || true
echo "-- portas 8080/3000 (esperado: livres)"
if ss -ltn 2>/dev/null | grep -qE ':(8080|3000)\b'; then ss -ltn | grep -E ':(8080|3000)\b'; else echo "8080/3000 livres"; fi
echo "-- processos java deixados vivos (esperado: nenhum)"
pgrep -a java || echo "nenhum"

# ============================================================================
secao "1. CONTRATO (E1.1) — make contract:check"

h2 "1.1 Validação do OpenAPI com Spectral"
executar "contract-check" make -C "$WORKTREE" contract:check
c_contract=$?

# A contagem vem do RELATÓRIO do Spectral, não de linhas de texto. O Node imprime um
# DeprecationWarning (`punycode`) em toda execução; o antigo `grep -ci warning` contava
# essas linhas como achados do contrato e reprovava um repositório limpo. Aqui o mesmo
# Spectral roda com `--format json` e conta-se por severidade (0=error, 1=warning).
# `grep -ci` sobre o texto não serviria nem para o defeito real: `make contract:check`
# não usa `--fail-severity=warn` e nem LISTA os warnings — só diz "no results with a
# severity of 'error'". O JSON é a única fonte que enxerga um `warn`.
spectral_json="$TMP/spectral.json"
npx --yes @stoplight/spectral-cli@6 lint "$WORKTREE/contracts/openapi.yaml" \
  --ruleset "$WORKTREE/.spectral.yaml" --format json >"$spectral_json" 2>/dev/null
c_spectral=$?
sev=$(grep -oE '"severity":[[:space:]]*[0-9]+' "$spectral_json" | sed -E 's/[^0-9]//g')
erros=$(printf '%s\n' "$sev" | grep -cx '0' || true)
warns=$(printf '%s\n' "$sev" | grep -cx '1' || true)
echo "-- achados do Spectral (json): erros=$erros warnings=$warns  [spectral exit=$c_spectral]"
if [[ $c_contract -eq 0 && $c_spectral -eq 0 && "${erros:-0}" -eq 0 && "${warns:-0}" -eq 0 ]]; then
  veredito "E1.1 contrato sem erros e sem warnings" "ATENDE" "make contract:check exit 0; spectral 0 erros, 0 warnings"
else
  veredito "E1.1 contrato sem erros e sem warnings" "NAO ATENDE" "exit=$c_contract; spectral erros=$erros warnings=$warns"
fi

# ============================================================================
secao "2. BANCO DE TESTE DO ZERO (E1.5) — recriação determinística"
echo "Recria scipath_test do zero (role sem senha + trust local). É o que torna o"
echo "harness independente de estado de outro agente."
executar "db-test-setup" bash "$WORKTREE/infra/db-test-setup.sh"
if [[ $? -eq 0 ]]; then
  veredito "E1.5 banco de teste recriado do zero" "ATENDE" "infra/db-test-setup.sh exit 0"
else
  veredito "E1.5 banco de teste recriado do zero" "NAO ATENDE" "infra/db-test-setup.sh falhou"
fi

# A partir daqui o .env é carregado para psql/flyway (a senha nunca é impressa).
set -a; . "$ENV_FILE"; set +a
psqlq() { PGPASSWORD="$SPRING_DATASOURCE_PASSWORD" psql -h "$PGHOST_" -p "$PGPORT_" -U "$SPRING_DATASOURCE_USERNAME" "$@"; }
psqlp() { PGPASSWORD="" psql -h "$PGHOST_" -p "$PGPORT_" -U scipath_test "$@"; }

h2 "2.1 Prova de conexão sem senha (trust) na role scipath_test"
psqlp -d scipath_test -tAc "SELECT 'conexao sem senha ok: ' || current_user || ' @ ' || current_database();"

# ============================================================================
secao "3. MIGRAÇÕES NOS DOIS BANCOS (E1.2/E1.3)"

h2 "3.1 flyway migrate — banco de TESTE (scipath_test), a partir de banco vazio"
executar "migrate-test" make -C "$WORKTREE" db:migrate:test
c_mig_test=$?
echo "-- histórico do Flyway em scipath_test:"
psqlq -d scipath_test -c "select version, description, script, success from flyway_schema_history order by installed_rank;"
echo "-- tabelas do modelo (esperado: 8 + flyway_schema_history):"
psqlq -d scipath_test -c "\dt"

h2 "3.2 flyway migrate — banco de DESENVOLVIMENTO (scipath)"
executar "migrate-dev" make -C "$WORKTREE" db:migrate
c_mig_dev=$?
echo "-- histórico do Flyway em scipath:"
psqlq -d scipath -c "select version, description, script, success from flyway_schema_history order by installed_rank;"

# A prova de "aplicado a partir de vazio" no banco de teste é o histórico com V1 e V2
# logo após um banco recriado (passo 2). Verifica-se aqui, com a saída colada.
vazio_ok=$(psqlq -d scipath_test -tAc "select count(*) from flyway_schema_history where version in ('1','2') and success")
if [[ $c_mig_test -eq 0 && $c_mig_dev -eq 0 && "$vazio_ok" = "2" ]]; then
  veredito "E1.2/E1.3 migrações aplicadas nos dois bancos" "ATENDE" "V1+V2 success em scipath_test (banco recriado); scipath migrado sem erro"
else
  veredito "E1.2/E1.3 migrações aplicadas nos dois bancos" "NAO ATENDE" "migrate:test=$c_mig_test migrate:dev=$c_mig_dev historico=$vazio_ok"
fi

h2 "3.3 SEED — contagens 13 categorias / 6 tipos / 7 licenças (E1.3)"
for db in scipath scipath_test; do
  echo "-- $db"
  psqlq -d "$db" -c "select (select count(*) from category)      as categorias,
                            (select count(*) from material_type) as tipos,
                            (select count(*) from license)       as licencas,
                            (select count(*) from category      where ativo) as categorias_ativas;"
done
tax_ok=$(psqlq -d scipath_test -tAc "select (select count(*) from category)||'/'||(select count(*) from material_type)||'/'||(select count(*) from license)")
if [[ "$tax_ok" = "13/6/7" ]]; then
  veredito "E1.3 taxonomia 13/6/7 no banco de teste" "ATENDE" "SELECT = 13/6/7"
else
  veredito "E1.3 taxonomia 13/6/7 no banco de teste" "NAO ATENDE" "SELECT = $tax_ok"
fi

# ============================================================================
secao "4. REVERSÃO EXERCITADA (E1.2 critério 3) — ordem inversa derivada do catálogo"

h2 "4.1 Estado antes da reversão (scipath_test)"
psqlq -d scipath_test -tAc "select 'tabelas do modelo presentes = ' || count(*) from information_schema.tables where table_schema='public' and table_type='BASE TABLE' and table_name <> 'flyway_schema_history';"

h2 "4.2 Drop na ordem inversa das dependências + remoção das versões do histórico"
echo "A ordem NÃO é lista fixa: é derivada do catálogo (pg_constraint). Uma tabela só cai"
echo "quando nenhuma outra ainda existente a referencia; assim uma FK nova (como a de"
echo "'sessao' -> 'account' da V4) não quebra o critério de novo. O histórico perde todas"
echo "as versões aplicadas, senão o Flyway pularia a reaplicação."
REVERSAO_SQL=$(cat <<'SQL'
DO $$
DECLARE alvo text;
BEGIN
  LOOP
    SELECT c.relname INTO alvo
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'public'
      AND c.relkind = 'r'
      AND c.relname <> 'flyway_schema_history'
      AND NOT EXISTS (
        SELECT 1
        FROM pg_constraint con
        JOIN pg_class dep ON dep.oid = con.conrelid
        WHERE con.contype = 'f'
          AND con.confrelid = c.oid
          AND dep.oid <> c.oid          -- auto-referência não bloqueia (ex.: category.parent_id)
      )
    LIMIT 1;
    EXIT WHEN alvo IS NULL;
    RAISE NOTICE 'DROP TABLE % (ordem inversa de dependência)', alvo;
    EXECUTE format('DROP TABLE IF EXISTS public.%I', alvo);
  END LOOP;
END $$;
DELETE FROM flyway_schema_history;
SQL
)
psqlq -d scipath_test -v ON_ERROR_STOP=1 -e -c "$REVERSAO_SQL" 2>&1
echo "reversão exit=$?"

h2 "4.3 Depois da reversão (esperado: nenhuma tabela do modelo)"
psqlq -d scipath_test -c "\dt"
restantes=$(psqlq -d scipath_test -tAc "select count(*) from information_schema.tables where table_schema='public' and table_type='BASE TABLE' and table_name <> 'flyway_schema_history'")
echo "-- tabelas do modelo restantes (todas menos flyway_schema_history): $restantes"

h2 "4.4 Reaplicação (a reversão é reversível)"
executar "remigrate-test" make -C "$WORKTREE" db:migrate:test
c_remig=$?
psqlq -d scipath_test -tAc "select 'versão após reaplicar = ' || string_agg(version, ',' order by installed_rank) from flyway_schema_history;"
if [[ $c_remig -eq 0 && "$restantes" = "0" ]]; then
  veredito "E1.2 reversão exercitada e reversível" "ATENDE" "drop completo (0 tabelas), reaplicação exit 0"
else
  veredito "E1.2 reversão exercitada e reversível" "NAO ATENDE" "restantes=$restantes remigrate=$c_remig"
fi

h2 "4.4.1 Medição do dono que a migração do PLUGIN deixou (decide o ACHADO)"
echo "Os passos 3.1/4.4 migram pelo PLUGIN do Maven, que usa a role de DESENVOLVIMENTO"
echo "(SPRING_DATASOURCE_USERNAME). O ACHADO do E1.8 — 'as tabelas saem com dono scipath e a"
echo "suíte leva permission denied' — era impresso INCONDICIONALMENTE: o script declarava a"
echo "armadilha mesmo quando a medição dizia o contrário. Ela deixou de se reproduzir no E1.12"
echo "(DML) e no E1.5-f1 (DDL) — infra/README.md, 'Quem é o dono do banco/esquema de teste'."
echo "Aqui mede-se, NESTA execução, o dono de 'category' e, tabela a tabela, quais objetos de"
echo "public a role da suíte não alcança. Só a medição que prova a condição registra o achado"
echo "(passo 4.4.2); o resultado medido é impresso nos três desfechos — nunca se declara achado"
echo "que não ocorreu, e medição indisponível não vira 'não ocorreu'."
medir() { # $1=SQL ; deixa o resultado em MED_RES e o exit do psql em MED_RC
  MED_RES="$(psqlq -d scipath_test -tAc "$1" 2>"$TMP/4.4.1-psql.err")"
  MED_RC=$?
}
psqlp -d scipath_test -c "\dt"
# (1) dono da tabela 'category' — a prova direta do achado do E1.8
medir "select pg_get_userbyid(c.relowner) from pg_class c where c.relnamespace = 'public'::regnamespace and c.relname = 'category' and c.relkind = 'r'"
rc_cat=$MED_RC; dono_categoria="$MED_RES"
# (2) a suíte (role scipath_test) enxerga 'category'?
medir "select has_table_privilege('scipath_test', 'category', 'SELECT')"
rc_priv=$MED_RC; priv_categoria="$MED_RES"
# (3) correlação POR TABELA: objetos de public cujo dono não é a role da suíte E que a suíte
#     não alcança. Estado misto cai aqui — a lista não é um nome agregado.
medir "select coalesce(string_agg(c.relname, ', ' order by c.relname), '') from pg_class c where c.relnamespace = 'public'::regnamespace and c.relkind in ('r', 'S', 'v') and c.relname <> 'flyway_schema_history' and not pg_has_role('scipath_test', pg_get_userbyid(c.relowner), 'USAGE') and not has_table_privilege('scipath_test', c.oid, 'SELECT')"
rc_list=$MED_RC; tabelas_sem_acesso="$MED_RES"
# (4) informativo: a role de dev atua como a role da suíte neste banco? (card E1.5-f1)
medir "select coalesce((select string_agg(c, ', ') from pg_db_role_setting s join pg_database d ON d.oid = s.setdatabase join pg_roles r ON r.oid = s.setrole, unnest(s.setconfig) c where d.datname = 'scipath_test' and r.rolname = 'scipath'), '<nenhuma>')"
rc_role=$MED_RC; set_role_dev="$MED_RES"

medicao_ok="nao"
if [[ "$(medicao_disponivel "$rc_cat" "$dono_categoria")" = "sim" \
   && "$(medicao_disponivel "$rc_priv" "$priv_categoria")" = "sim" \
   && "$rc_list" -eq 0 ]]; then
  medicao_ok="sim"
fi
echo "-- dono da tabela category em scipath_test: ${dono_categoria:-<sem medicao>}   [role da suite: scipath_test]"
echo "-- has_table_privilege('scipath_test','category','SELECT'): ${priv_categoria:-<sem medicao>}"
echo "-- tabelas de public sem a role da suite E sem SELECT da suite: ${tabelas_sem_acesso:-<nenhuma>}"
echo "-- 'SET role' automático da role de dev NESTE banco (card E1.5-f1): ${set_role_dev:-<nenhuma>}"
echo "-- medição desta execução disponível: $medicao_ok  (rc: category=$rc_cat priv=$rc_priv lista=$rc_list)"
if [[ "$medicao_ok" != "sim" ]]; then
  echo "-- OBSERVAÇÃO CRUA da medição indisponível (stderr do psql em $TMP/4.4.1-psql.err):"
  if [[ -s "$TMP/4.4.1-psql.err" ]]; then sed 's/^/     /' "$TMP/4.4.1-psql.err"; else echo "     (stderr vazio — a query voltou sem resultado)"; fi
fi

h2 "4.4.2 Veredito do ACHADO do E1.8 — pela medição desta execução, não por expectativa"
estado_e18="$(estado_achado_e18 "$medicao_ok" "$dono_categoria" "$priv_categoria" "$tabelas_sem_acesso")"
case "$estado_e18" in
  REPRODUZIDO)
    achado "Migrar scipath_test pelo plugin do Maven (role de dev) deixa objetos fora do alcance da suíte: dono de 'category'='${dono_categoria}', has_table_privilege('scipath_test','category','SELECT')='${priv_categoria}', tabelas sem acesso='${tabelas_sem_acesso:-<nenhuma>}'. 'make test' falha com 'permission denied for table'. MEDIDO nesta execução no passo 4.4.1. Reprodução: rode 'make db:migrate:test' e, em seguida, a suíte em shell limpo (env -i)."
    echo "RESULTADO MEDIDO: ACHADO REPRODUZIDO — a medição do passo 4.4.1 prova a condição."
    echo "  dono de category='${dono_categoria}' · has_table_privilege='${priv_categoria}' · sem acesso='${tabelas_sem_acesso:-<nenhuma>}'"
    echo "  Registrado em ACHADOS no fim deste log."
    ;;
  NAO_REPRODUZIDO)
    echo "RESULTADO MEDIDO: ACHADO NÃO REPRODUZIDO nesta execução — a medição FOI feita e a"
    echo "  condição do E1.8 (objeto fora do dono da suíte E suíte sem acesso a ele) NÃO ocorreu:"
    echo "    dono de category=${dono_categoria} · has_table_privilege('scipath_test','category','SELECT')=${priv_categoria} · tabelas sem acesso=<nenhuma>"
    echo "  Sem a condição provada, o achado NÃO é declarado em ACHADOS — só o resultado observado."
    echo "  Coerente com o arranjo medido no passo 4.4.1 (infra/README.md §'Quem é o dono do"
    echo "  banco/esquema de teste', cards E1.12/E1.5-f1)."
    ;;
  *)
    echo "RESULTADO MEDIDO: MEDIÇÃO INDISPONÍVEL — as consultas do passo 4.4.1 não devolveram"
    echo "  resultado utilizável (rc: category=$rc_cat priv=$rc_priv lista=$rc_list), então NÃO há"
    echo "  prova nem da condição nem da sua ausência. O achado NÃO é declarado e NADA se afirma"
    echo "  sobre a causa; a observação crua está impressa no passo 4.4.1."
    ;;
esac

h2 "4.5 Devolver scipath_test ao estado que a suíte espera"
echo "O estado em que a suíte roda é scipath_test recriado do zero: o Flyway da própria"
echo "aplicação aplica as migrações como 'scipath_test' e vira dono. Por isso o banco é"
echo "recriado aqui — antes de 'make test'."
executar "db-test-setup-2" bash "$WORKTREE/infra/db-test-setup.sh"
estado_suite=$?
echo "-- db-test-setup-2 exit=$estado_suite (a recriação exige sudo; se falhar, a suíte roda"
echo "   sobre o banco que já existe, como nos passos anteriores)"
psqlp -d scipath_test -c "\dt"

# ============================================================================
secao "5. SUÍTE EM SHELL LIMPO (E1.5 critério 1) — env -i"
echo "Critério de reprodutibilidade: a suíte roda sem .env, sem SPRING_DATASOURCE_*"
echo "herdadas, sem HOME do projeto no PATH. Comando exato abaixo."
LOG_TESTES="$TMP/make-test.log"
echo "\$ env -i PATH=/usr/bin:/bin HOME=\$HOME make test   (capturado em $LOG_TESTES)"
env -i PATH=/usr/bin:/bin HOME="$HOME" make -C "$WORKTREE" test >"$LOG_TESTES" 2>&1
c_test=$?
sed -r 's/\x1B\[[0-9;]*[A-Za-z]//g' "$LOG_TESTES" | tail -60
echo "---- [make test shell limpo] exit=$c_test"
echo "-- resumo do surefire:"
grep -E "Tests run:.*Failures|BUILD (SUCCESS|FAILURE)" "$LOG_TESTES" | sed 's/^/   /'
if [[ $c_test -eq 0 ]] && grep -q "BUILD SUCCESS" "$LOG_TESTES"; then
  veredito "E1.5 make test verde em shell limpo (env -i)" "ATENDE" "$(grep -E 'Tests run:' "$LOG_TESTES" | tail -1 | tr -s ' ')"
else
  veredito "E1.5 make test verde em shell limpo (env -i)" "NAO ATENDE" "exit=$c_test"
fi

# ============================================================================
secao "6. ERROS (E1.4) — 400 com errors[] e 500 sem vazamento"
echo "As linhas [E1.4] e [E1.9] são impressas pelos próprios testes executados no"
echo "passo 5, com o corpo HTTP real. Elas são a saída que sustenta o veredito."
h2 "6.1 400 — validação de entrada em application/problem+json com errors[]"
grep -E "^\[E1\.(4|9)\] .*400" "$LOG_TESTES" | sed 's/^/   /' || echo "   (nenhuma linha 400 encontrada)"
n400=$(grep -cE "^\[E1\.(4|9)\] .*400" "$LOG_TESTES" || true)
h2 "6.2 500 — erro inesperado sem stack trace, SQL ou caminho de arquivo"
grep -E "^\[E1\.4\] 500" "$LOG_TESTES" | sed 's/^/   /' || echo "   (nenhuma linha 500 encontrada)"
corpo500=$(grep -E "^\[E1\.4\] 500" "$LOG_TESTES" | head -1)
vazou=0
for termo in "SELECT" "account_email_key" "/srv/scipath" "IllegalStateException" "at com.scipath"; do
  [[ "$corpo500" == *"$termo"* ]] && vazou=1
done
echo "-- termos internos vazados na resposta 500: $vazou (esperado 0)"
if [[ "${n400:-0}" -ge 1 && "$vazou" -eq 0 && "$corpo500" == *"Erro interno"* ]]; then
  veredito "E1.4 payload inválido 400 c/ errors[]; erro interno 500 sem vazamento" "ATENDE" "$n400 evidência(s) de 400; 500 sem termos internos"
else
  veredito "E1.4 payload inválido 400 c/ errors[]; erro interno 500 sem vazamento" "NAO ATENDE" "n400=$n400 vazou=$vazou"
fi

# ============================================================================
secao "7. SEGREDOS NO REPOSITÓRIO (E1.5 critério 2, RNF012)"

h2 "7.1 Valor real da senha do .env em qualquer arquivo versionado"
# A busca é no que é VERSIONADO, não na árvore de arquivos. `git grep` só olha arquivos
# rastreados, então o `.env` — não rastreado, no .gitignore — fica fora por construção:
# a busca antiga (`grep -rIl` pela árvore) reprovava toda máquina que tem `.env`, que é
# requisito. Cobre os dois casos reais de vazamento: o commit (HEAD) e os arquivos
# rastreados no disco (inclui o que está em stage ou modificado).
if [[ -z "${SPRING_DATASOURCE_PASSWORD:-}" ]]; then
  ocorrencias=-1
  echo "-- ATENÇÃO: SPRING_DATASOURCE_PASSWORD vazia em $ENV_FILE — nada a procurar"
else
  ocorrencias=$(
    { git -C "$WORKTREE" grep -F -I -l -e "$SPRING_DATASOURCE_PASSWORD" HEAD 2>/dev/null
      git -C "$WORKTREE" grep -F -I -l -e "$SPRING_DATASOURCE_PASSWORD" 2>/dev/null
    } | sort -u | wc -l
  )
fi
echo "-- arquivos VERSIONADOS com o valor da senha (esperado 0): $ocorrencias"

h2 "7.2 Padrões de segredo (chave privada, token, senha literais)"
echo "Escopo: TODO o repositório versionado, menos os lugares onde um literal com forma de"
echo "segredo é legítimo por construção — duplo de teste (backend/**/src/test/**), script de"
echo "verificação (frontend/scripts/check-*.mjs) e a própria evidência (docs/qa/**). Segredo"
echo "REAL não escapa: a senha do .env é procurada em arquivo algum com essas exclusões (7.1)."
padroes=$(grep -rInE --exclude-dir=.git --exclude-dir=node_modules --exclude-dir=target \
  --exclude='*.log' --exclude='*.md' --exclude='*.example' \
  -e 'BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY' \
  -e '(password|senha|secret|token)[[:space:]]*[:=][[:space:]]*["'"'"'][^"'"'"']{8,}' \
  "$WORKTREE" 2>/dev/null | grep -viE 'password.*\$|senha_hash|hash-de-teste|PASSWORD NULL|"password": ""' \
  | grep -vE '/(docs/qa/|src/test/|scripts/check-)' | head -20 || true)
echo "${padroes:-(0 ocorrências)}"
npad=$(printf '%s' "$padroes" | grep -c . || true)
if [[ "$ocorrencias" -eq 0 && "${npad:-0}" -eq 0 ]]; then
  veredito "E1.5 nenhum segredo no repositório" "ATENDE" "0 ocorrências da senha e de padrões de segredo"
else
  veredito "E1.5 nenhum segredo no repositório" "NAO ATENDE" "senha=$ocorrencias padroes=$npad"
fi

# ============================================================================
secao "8. FRONTEND (E1.6/E1.7) — npm run verify"

h2 "8.1 Dependências (instalação offline, lockfile — nenhuma dependência nova)"
if [[ -d "$WORKTREE/frontend/node_modules" ]]; then
  echo "node_modules já presente — npm ci --offline"
fi
executar "npm-ci" bash -c "cd '$WORKTREE/frontend' && npm ci --offline"
c_ci=$?
# npm ci substitui o package.json pelo do lockfile? não: não altera arquivos versionados.
if [[ $c_ci -ne 0 ]]; then
  echo "AVISO: npm ci falhou (rede? cache?). O passo de verify provavelmente vai falhar."
fi

h2 "8.2 Bateria do frontend: check:tokens, check:style, check:layout, typecheck, lint, build, check:states"
executar "npm-verify" bash -c "cd '$WORKTREE/frontend' && npm run verify"
c_verify=$?
verify_log="${ultimo_log:-/dev/null}"
echo "-- hex/px solto em componente (check:style, dentro do verify):"
grep -E "PASS  (A|B1|B2)\)" "$verify_log" | sed 's/^/   /' || true
if [[ $c_verify -eq 0 ]]; then
  veredito "E1.6 build+lint+typecheck, sem hex solto, sem rolagem horizontal" "ATENDE" "npm run verify exit 0"
else
  veredito "E1.6 build+lint+typecheck, sem hex solto, sem rolagem horizontal" "NAO ATENDE" "npm run verify exit=$c_verify"
fi

h2 "8.3 grep explícito de hex solto em componente (E1.6 critério 2)"
hex=$(grep -rInE '#[0-9a-fA-F]{3,8}\b' "$WORKTREE/frontend/src/app" "$WORKTREE/frontend/src/components" 2>/dev/null | grep -vE '\.css:' | head -20 || true)
echo "componentes (src/app, src/components), fora de .css:"
echo "${hex:-(0 ocorrências)}"
nhex=$(printf '%s' "$hex" | grep -c . || true)
[[ "${nhex:-0}" -eq 0 ]] && echo "-- veredito parcial: 0 hex em componente" || echo "-- ATENÇÃO: hex em componente detectado"

h2 "8.4 Os 13 estados dos componentes (E1.7) — check:states"
grep -E "^PASS +(loading|success|empty|network-error|not-found|unauthorized|session-expired|invalid-field|submitting|recoverable-failure|duplicate-attempt|invalid-file)|^resumo: 13 estados" "$verify_log" | sed 's/^/   /' || true
n_estados=$(grep -cE "^PASS +(loading|success|empty|network-error|not-found|unauthorized|session-expired|invalid-field|submitting|recoverable-failure|duplicate-attempt|invalid-file)$" "$verify_log" || true)
echo "-- estados marcados PASS: $n_estados/13"
if [[ "$n_estados" -eq 13 ]]; then
  veredito "E1.7 os 13 estados alcançáveis" "ATENDE" "check:states marca 13 estados PASS"
else
  veredito "E1.7 os 13 estados alcançáveis" "NAO ATENDE" "estados PASS = $n_estados/13"
fi

# ============================================================================
secao "9. RESPONSIVIDADE E MOVIMENTO REDUZIDO (E1.6 critérios 3 e 4)"

h2 "9.1 Rolagem horizontal a 375 px e 1440 px (check:layout, dentro do verify)"
grep -E "px · \.sc-|PASS  [1-4]\)" "$verify_log" | sed 's/^/   /' || true
layout_ok=$(grep -cE "^PASS  [1-4]\)" "$verify_log" || true)
echo "-- regras de layout PASS: $layout_ok/4"

h2 "9.2 prefers-reduced-motion: reduce desliga animação decorativa (RNF005)"
echo "-- ocorrências no CSS do frontend:"
grep -rn "prefers-reduced-motion" "$WORKTREE/frontend/src" | sed 's/^/   /' || echo "   (nenhuma)"
motion_ok=$(grep -rl "prefers-reduced-motion" "$WORKTREE/frontend/src" | wc -l)
if [[ "$layout_ok" -eq 4 && "$motion_ok" -ge 1 ]]; then
  veredito "E1.6 responsividade 375/1440 sem rolagem horizontal + reduced-motion" "ATENDE" "check:layout 4/4 PASS; $motion_ok arquivo(s) com prefers-reduced-motion"
else
  veredito "E1.6 responsividade 375/1440 sem rolagem horizontal + reduced-motion" "NAO ATENDE" "layout=$layout_ok/4 motion=$motion_ok"
fi

# ============================================================================
secao "10. HIGIENE FINAL — nada deixado vivo"

echo "-- portas 8080/3000:"
if ss -ltn 2>/dev/null | grep -qE ':(8080|3000)\b'; then ss -ltn | grep -E ':(8080|3000)\b'; else echo "livres"; fi
echo "-- processos java/surefire/next:"
pgrep -af 'surefire|spring-boot:run|next-server' | grep -v pgrep || echo "nenhum"
echo "-- senha do .env no log consolidado (esperado 0):"
# O log é o stdout deste script; a conferência final é feita pelo operador sobre o
# arquivo gravado. Aqui só confirmamos que a variável não é ecoada nesta sessão.

h2 "10.1 Higiene de schema: scipath_test fica pronto para a Etapa 2"
psqlq -d scipath_test -c "select (select count(*) from category) as categorias, (select count(*) from material) as materiais, (select count(*) from account) as contas;"

# ============================================================================
secao "VEREDITO POR CRITÉRIO (DRS §21.4)"
printf '\n%-6s %-62s %s\n' "CARD" "CRITÉRIO" "VEREDITO"
printf '%s\n' "----------------------------------------------------------------------------------------------------"
for i in "${!V_CRIT[@]}"; do
  printf '%-6s %-62s %s  [%s]\n' "$(echo "${V_CRIT[$i]}" | cut -d' ' -f1)" "${V_CRIT[$i]}" "${V_RES[$i]}" "${V_NOTA[$i]}"
done
printf '%s\n' "----------------------------------------------------------------------------------------------------"

n_atende=$(printf '%s\n' "${V_RES[@]}" | grep -c "^ATENDE" || true)
n_nao=$(printf '%s\n' "${V_RES[@]}" | grep -c "^NAO ATENDE" || true)
echo
echo "resumo: ${n_atende:-0} ATENDE · ${n_nao:-0} NÃO ATENDE · ${#V_CRIT[@]} critérios avaliados"
echo
if [[ ${#ACHADOS[@]} -gt 0 ]]; then
  echo "ACHADOS (defeitos/armadilhas reproduzíveis — viram card, o QA não corrige):"
  for a in "${ACHADOS[@]}"; do echo "  * $a"; done
  echo
fi
echo "NÃO VERIFICADO neste harness (registrado, nunca omitido):"
echo "  - conferência VISUAL humana (screenshot 375/1440, foco visível, contraste):"
echo "    feita pelo card E1.8 com o navegador, fora deste script — ver a seção"
echo "    'VERIFICAÇÃO VISUAL' anexada ao log de evidência."
echo "  - leitor de tela real (NVDA/VoiceOver): não executado; verifica-se a marcação"
echo "    que o leitor consome (check:states)."
echo "  - jornadas de ponta a ponta (descoberta, cadastro, publicação): dependem da"
echo "    Etapa 2; não existem ainda, logo não foram testadas."
echo "  - hospedagem, backup, termos/privacidade definitivos e canal de denúncia:"
echo "    ADR-0007 — fora do MVP desta etapa."
echo
echo "FIM DO HARNESS (card E1.8)."
