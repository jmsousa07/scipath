# F0.1 — Backend: esqueleto Spring Boot que sobe e responde /health

## Contexto

Primeiro projeto do SciPath: precisamos de um backend que suba nesta máquina
(PostgreSQL 16 nativo em 127.0.0.1:5432, sem Docker) e prove que o gate funciona.
Sem isso nenhuma outra fatia do backend pode começar.

## Entrada

- `AGENTS.md` (regras), `docs/01-arquitetura.md`, `docs/adr/ADR-0001-*`.
- `contracts/openapi.yaml` — já contém `/health` e `/materiais` (não mude o formato do `/health`).
- `.env` na raiz (gerado por `infra/db-setup.sh`) — nunca imprima o conteúdo.

## Saída

`backend/` com: projeto Maven (Java 21, Spring Boot 3.x, wrapper `./mvnw`),
`GET /api/health` respondendo `{status, version, database}` conforme o contrato,
conexão com o Postgres via variáveis do `.env`, e Flyway configurado (sem migração ainda).
O `Makefile` da raiz passa a funcionar para `make test` e `make api:dev`.

## Critérios de aceite

1. `make doctor` mostra JDK e Maven detectados e `pg_isready` respondendo.
2. `make api:dev` sobe a API e `curl -s localhost:8080/api/health` devolve JSON com
   `status: "UP"` e `database: "UP"`.
3. `make test` roda verde (pelo menos um teste que chama `/health` e valida o JSON).
4. Com o Postgres parado (`sudo systemctl stop postgresql`) o campo `database` vira `"DOWN"`
   e a API continua respondendo (não quebra na subida).
5. Nenhuma senha, URL de banco ou token no código — só via `.env`.

## Fora de escopo

Criar tabelas/migrações (é o card F0.3), autenticação, qualquer endpoint além de `/health`,
e configuração de deploy.

## Evidência exigida

Arquivo `docs/qa/f0-1-backend.log` (o QA preenche) com: `make doctor`, `make test`,
`curl /api/health` e o caso do critério 4. Anexe o caminho no card.
