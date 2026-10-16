# F0.3 — Backend: migração V1 + GET/POST /materiais conforme contrato

## Contexto

Com o esqueleto de pé (F0.1), entra a primeira fatia de domínio real: material.
É o que dá conteúdo à tela de lista (F0.4) e o primeiro uso do grafo de dependências
do time (backend → QA).

## Entrada

- `contracts/openapi.yaml` § `/materiais` (schemas `Material`, `NovoMaterial`, `PaginaMateriais`, `Problema`).
- `docs/01-arquitetura.md` — modelo de domínio v0 (tabelas `usuario`, `material`).
- `AGENTS.md` — regra "contrato primeiro" e DoD.
- `backend/` criado no F0.1.

## Saída

Migração `V1__material.sql` (tabelas `usuario` e `material`, com índice e `tsvector` preparado
para busca futura), entidade + DTO + repositório + serviço + controller, e os endpoints:

- `GET /api/materiais?page&size&tipo` — página ordenada por `criadoEm desc`.
- `POST /api/materiais` — valida, grava, devolve 201 com `Location` e o material criado.

Autor: como não há autenticação ainda, use um usuário fixo de desenvolvimento
(`autor@local`) criado na própria migração — **não** invente endpoint de auth.

## Critérios de aceite

1. `make db:migrate` cria as tabelas; rodar de novo não quebra (migração idempotente do Flyway).
2. `GET /api/materiais` devolve `{conteudo, page, size, total}` com a ordem estável
   (dois itens com mesmo timestamp não trocam de posição entre páginas — critério de desempate explícito).
3. `POST /api/materiais` com payload válido devolve 201, cabeçalho `Location` e o material com `id`/`criadoEm`.
4. `POST` inválido (título com 2 caracteres; `tipo` fora do enum) devolve 400 em
   `application/problem+json` com `errors[]` nomeando cada campo.
5. `GET /api/materiais?tipo=SLIDE` filtra corretamente; `tipo` inválido devolve 400.
6. Testes de integração cobrem os cinco critérios acima, contra o Postgres local.
7. Nenhuma entidade JPA exposta no JSON; erro sempre no formato do contrato.

## Fora de escopo

Upload de arquivo (entra na fatia 2), busca full-text (fatia 2), autenticação (fatia 1),
ligação material↔conceito (fatia 3), paginação por cursor.

## Evidência exigida

`docs/qa/f0-3-backend.log` com `make test`, o `curl` dos critérios 2/3/4/5 e o
`diff` do contrato (deve estar vazio: o contrato já descrevia tudo).
