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

.PHONY: help doctor up db\:migrate db\:migrate\:test db\:reset api\:dev web\:dev test lint e2e contract\:check

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
# O plugin Flyway do Maven NÃO lê application.yml nem .env: url/user/password vêm por
# -Dflyway.*, sempre a partir do .env carregado (SPRING_DATASOURCE_*). locations precisa ser
# filesystem: — com classpath:db/migration o plugin cria a tabela de histórico e não encontra
# migração nenhuma (exit 0, schema vazio). Procedimento completo em db/migration/README.md.
# Sem -q: a linha "Successfully applied N migrations" é a evidência exigida (AGENTS.md §17.5).
# A receita é UMA cadeia de shell, sem "|| echo": falha do Flyway tem de sair com exit != 0.
define flyway_migrate
	@test -f backend/pom.xml || { echo "backend/ ausente (card F0.1)"; exit 1; }
	@$(LOAD_ENV) \
	  url="$${$(1):?$(1) ausente no .env — rode infra/db-setup.sh}"; \
	  cd backend && ./mvnw --batch-mode flyway:migrate \
	    -Dflyway.url="$$url" \
	    -Dflyway.user="$$SPRING_DATASOURCE_USERNAME" \
	    -Dflyway.password=<valor-omitido> \
	    -Dflyway.locations=filesystem:src/main/resources/db/migration
endef

db\:migrate:     ## aplica as migrações Flyway no banco de dev (.env: SPRING_DATASOURCE_URL)
	$(call flyway_migrate,SPRING_DATASOURCE_URL)

db\:migrate\:test: ## aplica as migrações Flyway no banco de teste (.env: SPRING_DATASOURCE_URL_TEST)
	$(call flyway_migrate,SPRING_DATASOURCE_URL_TEST)

db\:reset:       ## recria o banco (destrutivo, só local — precisa confirmar)
	@bash infra/db-setup.sh --reset

api\:dev:        ## API em :8080
	@test -f backend/pom.xml || { echo "backend/ ainda não existe (card F0.1)"; exit 1; }
	@# Perfil dev: tolera banco fora na subida (ADR-0004 ajuste 3). Sem perfil o backend falha rápido.
	@$(LOAD_ENV) export SPRING_PROFILES_ACTIVE=dev; cd backend && exec ./mvnw -q spring-boot:run

web\:dev:        ## Next em :3000 (f0.3)
	@test -f frontend/package.json && (cd frontend && npm run dev) || echo "frontend/ ainda não existe (card f0.3)"

test:            ## testes do backend
	@test -f backend/pom.xml || { echo "backend/ ainda não existe (card F0.1)"; exit 1; }
	@$(LOAD_ENV) cd backend && ./mvnw test

# Alvos de frontend/e2e: a ausência do projeto é declarada numa linha e o alvo sai 1
# (mesma forma dos alvos de backend). A receita NÃO usa "|| echo": com o projeto presente,
# falha do lint/typecheck/teste tem de sair com exit != 0 — mascarar faria o alvo passar
# verde sobre um frontend quebrado (defeito do E2.6-f1; precedente do E1.2a em db:migrate).
lint:            ## lint + typecheck (f0.3)
	@test -f frontend/package.json || { echo "frontend/ ainda não existe (card f0.3)"; exit 1; }
	@cd frontend && npm run lint && npm run typecheck

e2e:             ## Playwright (f0.4)
	@test -f e2e/package.json || { echo "e2e/ ainda não existe (card f0.4)"; exit 1; }
	@cd e2e && npx playwright test

contract\:check: ## valida o contrato OpenAPI
	@npx --yes @stoplight/spectral-cli@6 lint contracts/openapi.yaml --ruleset .spectral.yaml
