# SciPath — atalhos de trabalho. Cada alvo precisa funcionar no ambiente desta máquina
# (PostgreSQL 16 nativo, sem Docker). Alvos marcados com (f0.x) entram em vigor quando
# o card correspondente entregar o projeto.

SHELL := /bin/bash
REPO  := $(shell pwd)

.PHONY: help doctor up db:migrate db:reset api:dev web:dev test lint e2e contract:check

help:            ## lista os alvos
	@grep -E '^[a-zA-Z_:]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN{FS=":.*?## "}{printf "  \033[36m%-16s\033[0m %s\n", $$1, $$2}'

doctor:          ## confere ferramentas e banco
	@echo "== ferramentas =="
	@java -version 2>&1 | head -1 || echo "JDK: AUSENTE"
	@mvn -v 2>&1 | head -1 || echo "Maven: AUSENTE"
	@node -v || echo "Node: AUSENTE"
	@echo "== banco =="
	@pg_isready -h 127.0.0.1 -p 5432 || true
	@test -f .env && echo ".env: presente" || echo ".env: AUSENTE (rode infra/db-setup.sh)"

up: db:migrate   ## sobe o que já existir (banco + API + web)
	@echo "Suba API e web em terminais separados: make api:dev / make web:dev"

db:migrate:      ## aplica migrações Flyway (f0.2)
	@test -f backend/pom.xml && (cd backend && ./mvnw -q flyway:migrate) || echo "backend/ ainda não existe (card f0.2)"

db:reset:        ## recria o banco (destrutivo, só local — precisa confirmar)
	@bash infra/db-setup.sh --reset

api:dev:         ## API em :8080 (f0.2)
	@test -f backend/pom.xml && (cd backend && ./mvnw -q spring-boot:run) || echo "backend/ ainda não existe (card f0.2)"

web:dev:         ## Next em :3000 (f0.3)
	@test -f frontend/package.json && (cd frontend && npm run dev) || echo "frontend/ ainda não existe (card f0.3)"

test:            ## testes do backend (f0.2)
	@test -f backend/pom.xml && (cd backend && ./mvnw -q test) || echo "backend/ ainda não existe (card f0.2)"

lint:            ## lint + typecheck (f0.3)
	@test -f frontend/package.json && (cd frontend && npm run lint && npm run typecheck) || echo "frontend/ ainda não existe (card f0.3)"

e2e:             ## Playwright (f0.4)
	@test -f e2e/package.json && (cd e2e && npx playwright test) || echo "e2e/ ainda não existe (card f0.4)"

contract:check:  ## valida o contrato OpenAPI
	@npx --yes @stoplight/spectral-cli lint contracts/openapi.yaml --ruleset .spectral.yaml 2>/dev/null \
	  || echo "spectral não configurado ainda — validação de contrato entra no card f0.1"
