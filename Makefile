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

.PHONY: help doctor up db\:migrate db\:reset api\:dev web\:dev test lint e2e contract\:check

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

db\:migrate:     ## aplica migrações Flyway (f0.2)
	@test -f backend/pom.xml && (cd backend && ./mvnw -q flyway:migrate) || echo "backend/ ainda não existe (card f0.2)"

db\:reset:       ## recria o banco (destrutivo, só local — precisa confirmar)
	@bash infra/db-setup.sh --reset

api\:dev:        ## API em :8080
	@test -f backend/pom.xml || { echo "backend/ ainda não existe (card F0.1)"; exit 1; }
	@$(LOAD_ENV) cd backend && exec ./mvnw -q spring-boot:run

web\:dev:        ## Next em :3000 (f0.3)
	@test -f frontend/package.json && (cd frontend && npm run dev) || echo "frontend/ ainda não existe (card f0.3)"

test:            ## testes do backend
	@test -f backend/pom.xml || { echo "backend/ ainda não existe (card F0.1)"; exit 1; }
	@$(LOAD_ENV) cd backend && ./mvnw test

lint:            ## lint + typecheck (f0.3)
	@test -f frontend/package.json && (cd frontend && npm run lint && npm run typecheck) || echo "frontend/ ainda não existe (card f0.3)"

e2e:             ## Playwright (f0.4)
	@test -f e2e/package.json && (cd e2e && npx playwright test) || echo "e2e/ ainda não existe (card f0.4)"

contract\:check: ## valida o contrato OpenAPI
	@npx --yes @stoplight/spectral-cli@6 lint contracts/openapi.yaml --ruleset .spectral.yaml
