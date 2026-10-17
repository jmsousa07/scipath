# SciPath — atalhos de trabalho. Cada alvo precisa funcionar no ambiente desta máquina
# (PostgreSQL 16 nativo, sem Docker). Alvos marcados com (f0.x) entram em vigor quando
# o card correspondente entregar o projeto.
#
# Nota de sintaxe: os alvos usam ':' no nome (padrão do projeto: `make db:migrate`).
# Em GNU make, o ':' dentro de nome de alvo/prerequisito precisa ser escapado com '\'
# — sem isso o make aborta com "multiple target patterns" / "target pattern contains no '%'".

SHELL := /bin/bash
REPO  := $(shell pwd)

# O .env mora na raiz do repositório principal (não é versionado). Como cada card roda
# num worktree, procuramos primeiro aqui e, se não existir, na raiz do repositório real.
GIT_COMMON_DIR := $(shell git rev-parse --path-format=absolute --git-common-dir 2>/dev/null)
MAIN_ROOT      := $(shell cd "$(GIT_COMMON_DIR)/.." 2>/dev/null && pwd)
ENV_FILE       := $(if $(wildcard .env),.env,$(wildcard $(MAIN_ROOT)/.env))

# Carrega o .env exportando cada variável (SPRING_DATASOURCE_*) sem imprimir segredo.
LOAD_ENV := set -a; if [ -f "$(ENV_FILE)" ]; then . "$(ENV_FILE)"; fi; set +a;

.PHONY: help doctor up db\:migrate db\:migrate\:test db\:seed\:dev db\:reset api\:dev web\:dev email\:outbox test lint e2e contract\:check

help:            ## lista os alvos
	@grep -E '^[a-zA-Z_:\\]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN{FS=" *## "}{gsub(/\\/,"",$$1); sub(/:[ 	]*$$/,"",$$1); printf "  \033[36m%-16s\033[0m %s\n", $$1, $$2}'

doctor:          ## confere ferramentas e banco
	@echo "== ferramentas =="
	@java -version 2>&1 | head -1 || echo "JDK: AUSENTE"
	@mvn -v 2>&1 | head -1 || echo "Maven: AUSENTE"
	@node -v || echo "Node: AUSENTE"
	@echo "== banco =="
	@pg_isready -h 127.0.0.1 -p 5432 || true
	@if [ -n "$(ENV_FILE)" ]; then echo ".env: presente ($(ENV_FILE))"; else echo ".env: AUSENTE (rode infra/db-setup.sh)"; fi
	@test -f backend/pom.xml && echo "backend/: esqueleto presente" || echo "backend/: ausente"

up: db\:migrate  ## sobe o que já existir (banco + API + web)
	@echo "Suba API e web em terminais separados: make api:dev / make web:dev"

# Comando compartilhado das migrações. $(1) = variável do .env com a URL do banco alvo.
# O plugin Flyway do Maven NÃO lê application.yml nem .env. Ele lê, como o CLI, as variáveis
# de ambiente FLYWAY_URL/FLYWAY_USER/FLYWAY_PASSWORD — conferido nesta máquina com o plugin
# 11.7.2: `flyway:info` conectou por essas três variáveis, sem nenhum -D de credencial
# (card E1.2b). Por isso a credencial vai pelo AMBIENTE e não pelo argv: /proc/<pid>/cmdline
# é -r--r--r-- (qualquer processo do host lê via `ps`), enquanto /proc/<pid>/environ é
# -r-------- (só o mesmo UID — que, nesta máquina, é todo mundo: os workers do kanban rodam
# como ubuntu. O argv é o vetor do card E1.2b e é ele que fica fechado; o environ é mais
# restrito que o cmdline, não um canal público). O argv do mvnw continua com um só -D:
# locations — nada de segredo.
# O .env da raiz segue como única fonte dos valores; aqui só se traduz o nome da variável.
# locations precisa ser filesystem: — com classpath:db/migration o plugin cria a tabela de
# histórico e não encontra migração nenhuma (exit 0, schema vazio). Procedimento completo em
# db/migration/README.md.
# Sem -q: a linha "Successfully applied N migrations" é a evidência exigida (AGENTS.md §17.5).
# A receita é UMA cadeia de shell, sem "|| echo": falha do Flyway tem de sair com exit != 0.
define flyway_migrate
	@test -f backend/pom.xml || { echo "backend/ ausente (card F0.1)"; exit 1; }
	@$(LOAD_ENV) \
	  url="$${$(1):?$(1) ausente no .env — rode infra/db-setup.sh}"; \
	  export FLYWAY_URL="$$url" \
	         FLYWAY_USER="$$SPRING_DATASOURCE_USERNAME" \
	         FLYWAY_PASSWORD="$$SPRING_DATASOURCE_PASSWORD"; \
	  cd backend && ./mvnw --batch-mode flyway:migrate \
	    -Dflyway.locations=filesystem:src/main/resources/db/migration
endef

db\:migrate:     ## aplica as migrações Flyway no banco de dev (.env: SPRING_DATASOURCE_URL)
	$(call flyway_migrate,SPRING_DATASOURCE_URL)

db\:migrate\:test: ## aplica as migrações Flyway no banco de teste (.env: SPRING_DATASOURCE_URL_TEST)
	$(call flyway_migrate,SPRING_DATASOURCE_URL_TEST)

# Seed de DESENVOLVIMENTO (card E2.6). Dado de exemplo não é conteúdo do produto: existe
# para dar o que listar, filtrar, buscar e paginar, e a marca é o prefixo `exemplo-` no
# slug/e-mail (infra/seed-dev.sql, infra/README.md).
# As duas guardas abaixo (host e banco) recusam antes de abrir conexão; a terceira guarda,
# dentro do próprio .sql, recusa de novo — quem chamar `psql -f` na mão não escapa.
# Sem "|| echo": falha do seed tem de sair com exit != 0.
db\:seed\:dev:   ## insere o seed de DESENVOLVIMENTO (recusa banco de teste e host remoto)
	@command -v psql >/dev/null || { echo "psql ausente (PostgreSQL 16 nativo, ADR-0007)"; exit 1; }
	@$(LOAD_ENV) \
	  url="$${SPRING_DATASOURCE_URL:?SPRING_DATASOURCE_URL ausente no .env — rode infra/db-setup.sh}"; \
	  rest="$${url#jdbc:postgresql://}"; rest="$${rest%%\?*}"; \
	  db="$${rest##*/}"; hp="$${rest%%/*}"; host="$${hp%%:*}"; port="$${hp##*:}"; \
	  if [ "$$port" = "$$hp" ]; then port=5432; fi; \
	  case "$$host" in 127.0.0.1|localhost|::1) ;; \
	    *) echo "seed-dev RECUSADO: $$host não é loopback — dado de exemplo é só de banco local (ADR-0007)"; exit 1;; \
	  esac; \
	  case "$$db" in *_test) \
	    echo "seed-dev RECUSADO: $$db é o banco de TESTE — a suíte precisa de estado limpo (card E2.6, critério 2)"; exit 1;; \
	  esac; \
	  echo "== seed-dev: banco $$db @ $$host:$$port (desenvolvimento local, não é produção) =="; \
	  PGPASSWORD="$$SPRING_DATASOURCE_PASSWORD" psql \
	    -h "$$host" -p "$$port" -U "$$SPRING_DATASOURCE_USERNAME" -d "$$db" \
	    -v ON_ERROR_STOP=1 -f infra/seed-dev.sql

db\:reset:       ## recria o banco (destrutivo, só local — precisa confirmar)
	@bash infra/db-setup.sh --reset

# email:outbox é o "servidor de e-mail" de desenvolvimento (card E3.4, ADR-0003 (f)): sem SMTP e
# sem Docker nesta máquina, o EmailSender grava a mensagem num arquivo e é daqui que quem
# desenvolve tira o token de confirmação/recuperação. O token aparece EM CLARO neste alvo de
# propósito — o outbox faz o papel da caixa de entrada, não é log da aplicação (RNF009).
# O caminho é o mesmo da configuração (SCIPATH_OUTBOX_DIR vence, como no application.yml).
email\:outbox:   ## mostra a última mensagem do outbox de e-mail (canal de desenvolvimento)
	@$(LOAD_ENV) dir="$${SCIPATH_OUTBOX_DIR:-$$HOME/.scipath/outbox}"; \
	arquivo="$$dir/outbox.jsonl"; \
	if [ ! -f "$$arquivo" ]; then \
	  echo "sem mensagens em $$arquivo"; \
	  echo "o outbox só é criado quando um cadastro (verificação) ou uma solicitação (recuperação) emite um token — card E3.4"; \
	  exit 1; \
	fi; \
	echo "== última mensagem do outbox ($$arquivo) =="; \
	tail -1 "$$arquivo"

api\:dev:        ## API em :8080
	@test -f backend/pom.xml || { echo "backend/ ainda não existe (card F0.1)"; exit 1; }
	@# Perfil dev: tolera banco fora na subida (ADR-0004 ajuste 3). Sem perfil o backend falha rápido.
	@$(LOAD_ENV) export SPRING_PROFILES_ACTIVE=dev; cd backend && exec ./mvnw -q spring-boot:run

# web:dev segue a mesma forma do lint/e2e (E2.6-f1): ausência do projeto declarada numa linha
# com exit 1, receita real SEM "|| echo" — mascarar faria o alvo sair 0 sobre um next dev quebrado.
web\:dev:        ## Next em :3000 (f0.3)
	@test -f frontend/package.json || { echo "frontend/ ainda não existe (card f0.3)"; exit 1; }
	@cd frontend && npm run dev

test:            ## testes do backend (reproduzível de shell limpo; datasource de scipath_test)
	@test -f backend/pom.xml || { echo "backend/ ainda não existe (card F0.1)"; exit 1; }
	@# NÃO carrega o .env de propósito (card E1.5): a suíte lê o datasource de scipath_test
	@# em backend/src/test/resources/application-test.yml — role de teste sem senha, com
	@# trust local (infra/db-test-setup.sh). Variável SPRING_DATASOURCE_* herdada do
	@# ambiente venceria o YAML e faria o teste rodar contra o banco de desenvolvimento
	@# `scipath`; por isso são limpas aqui. MAVEN_OPTS limitado à memória da máquina
	@# (954 MB, AGENTS.md); o heap do fork dos testes está no surefire (pom.xml).
	@unset SPRING_DATASOURCE_URL SPRING_DATASOURCE_USERNAME SPRING_DATASOURCE_PASSWORD SPRING_DATASOURCE_URL_TEST; \
	export MAVEN_OPTS="$${MAVEN_OPTS:--Xmx384m}"; \
	cd backend && ./mvnw -o test

# Alvos de frontend/e2e: a ausência do projeto é declarada numa linha e o alvo sai 1
# (mesma forma dos alvos de backend). A receita NÃO usa "|| echo": com o projeto presente,
# falha do lint/typecheck/teste tem de sair com exit != 0 — mascarar faria o alvo passar
# verde sobre um frontend quebrado (defeito do E2.6-f1; precedente do E1.2a em db:migrate).
lint:            ## lint + typecheck (f0.3)
	@test -f frontend/package.json || { echo "frontend/ ainda não existe (card f0.3)"; exit 1; }
	@cd frontend && npm run lint && npm run typecheck

e2e:             ## Playwright (f0.4) — NÃO faz parte do gate do MVP: ver ADR-0009
	@# Decisão registrada no ADR-0009: a camada ponta a ponta do MVP é o harness de QA
	@# (infra/qa-harness.sh + baterias em docs/qa/), rodado no card de QA da etapa. Playwright
	@# não entra sem necessidade medida. O alvo continua DECLARANDO a ausência de e2e/ em vez
	@# de passar em silêncio.
	@test -f e2e/package.json || { echo "e2e/ ainda não existe (decisão no ADR-0009: o harness de QA é a camada ponta a ponta)"; exit 1; }
	@cd e2e && npx playwright test

contract\:check: ## valida o contrato OpenAPI
	@npx --yes @stoplight/spectral-cli@6 lint contracts/openapi.yaml --ruleset .spectral.yaml
