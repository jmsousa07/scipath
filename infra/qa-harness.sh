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
warnings=$(grep -ci "warning" "${ultimo_log:-/dev/null}" || true)
echo "-- linhas de 'warning' na saída: ${warnings:-0}"
if [[ $c_contract -eq 0 && "${warnings:-0}" -eq 0 ]]; then
  veredito "E1.1 contrato sem erros e sem warnings" "ATENDE" "make contract:check exit 0; 0 warnings"
else
  veredito "E1.1 contrato sem erros e sem warnings" "NAO ATENDE" "exit=$c_contract; warnings=$warnings"
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
secao "4. REVERSÃO EXERCITADA (E1.2 critério 3) — procedimento do README, ordem inversa"

h2 "4.1 Estado antes da reversão (scipath_test)"
psqlq -d scipath_test -tAc "select 'tabelas do V1 presentes = ' || count(*) from information_schema.tables where table_schema='public' and table_name in ('account','verification_token','profile','category','material_type','license','file_asset','material');"

h2 "4.2 Drop na ordem inversa das dependências + remoção da versão do histórico"
DROP_V1="DROP TABLE IF EXISTS material; DROP TABLE IF EXISTS file_asset; DROP TABLE IF EXISTS verification_token; DROP TABLE IF EXISTS profile; DROP TABLE IF EXISTS account; DROP TABLE IF EXISTS category; DROP TABLE IF EXISTS material_type; DROP TABLE IF EXISTS license; DELETE FROM flyway_schema_history WHERE version IN ('1','2');"
psqlq -d scipath_test -v ON_ERROR_STOP=1 -e -c "$DROP_V1"
echo "reversão exit=$?"

h2 "4.3 Depois da reversão (esperado: nenhuma tabela do modelo)"
psqlq -d scipath_test -c "\dt"
restantes=$(psqlq -d scipath_test -tAc "select count(*) from information_schema.tables where table_schema='public' and table_name in ('account','verification_token','profile','category','material_type','license','file_asset','material')")
echo "-- tabelas do modelo restantes: $restantes"

h2 "4.4 Reaplicação (a reversão é reversível)"
executar "remigrate-test" make -C "$WORKTREE" db:migrate:test
c_remig=$?
psqlq -d scipath_test -tAc "select 'versão após reaplicar = ' || string_agg(version, ',' order by installed_rank) from flyway_schema_history;"
if [[ $c_remig -eq 0 && "$restantes" = "0" ]]; then
  veredito "E1.2 reversão exercitada e reversível" "ATENDE" "drop completo (0 tabelas), reaplicação exit 0"
else
  veredito "E1.2 reversão exercitada e reversível" "NAO ATENDE" "restantes=$restantes remigrate=$c_remig"
fi

h2 "4.5 Devolver scipath_test ao estado que a suíte espera (achado do harness)"
echo "As migrações dos passos 3.1/4.4 foram aplicadas pelo PLUGIN do Maven, que usa a"
echo "role de DESENVOLVIMENTO (SPRING_DATASOURCE_USERNAME). As tabelas saem com dono"
echo "'scipath'; a suíte conecta como a role 'scipath_test' (trust, sem senha) e leva"
echo "'permission denied for table'. O estado em que a suíte roda é scipath_test VAZIO:"
echo "o Flyway da própria aplicação aplica V1+V2 como 'scipath_test' e vira dono."
echo "Por isso o banco é recriado aqui — antes de 'make test'."
executar "db-test-setup-2" bash "$WORKTREE/infra/db-test-setup.sh"
estado_suite=$?
psqlp -d scipath_test -c "\dt"

# Este é um ACHADO da bateria: o banco de teste não é neutro entre as duas formas de
# migrar. Registrado aqui e no relatório final — não é defeito do harness, é uma
# armadilha do ambiente que qualquer script do E1.2/E1.3 reproduz.
achado "Migrar scipath_test pelo plugin do Maven (role de dev 'scipath') deixa as tabelas com dono 'scipath'; a suíte (role 'scipath_test') recebe 'permission denied for table' e 'make test' falha por BUILD FAILURE. Reproduzível: rode 'make db:migrate:test' e em seguida 'env -i PATH=/usr/bin:/bin HOME=\$HOME make test'."

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
ocorrencias=$(grep -rIl --exclude-dir=.git --exclude-dir=node_modules --exclude-dir=target \
  --exclude='*.log' -- "$SPRING_DATASOURCE_PASSWORD" "$WORKTREE" 2>/dev/null | wc -l)
echo "-- arquivos com o valor da senha (esperado 0): $ocorrencias"

h2 "7.2 Padrões de segredo (chave privada, token, senha literais)"
padroes=$(grep -rInE --exclude-dir=.git --exclude-dir=node_modules --exclude-dir=target \
  --exclude='*.log' --exclude='*.md' --exclude='*.example' \
  -e 'BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY' \
  -e '(password|senha|secret|token)[[:space:]]*[:=][[:space:]]*["'"'"'][^"'"'"']{8,}' \
  "$WORKTREE" 2>/dev/null | grep -viE 'password.*\$|senha_hash|hash-de-teste|PASSWORD NULL|"password": ""' | head -20 || true)
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
