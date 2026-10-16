# ADR-0001 — Monorepo, contrato primeiro e backend em Java

- Data: 2026-10-15
- Status: aceita
- Decisores: João Mateus (dono), gerente (orquestração)

## Contexto

Começo do SciPath. Precisamos de um arranjo que permita vários agentes (backend, frontend,
QA) trabalharem em paralelo sem se atropelarem, e que não gere retrabalho quando o produto
crescer. Duas decisões estavam em aberto: monorepo vs multi-repo, e Java/Spring Boot vs
monolito Node/TypeScript.

## Decisão

1. **Monorepo** com `backend/`, `frontend/`, `e2e/`, `contracts/` e `docs/`.
2. **Contrato primeiro**: `contracts/openapi.yaml` e as migrações são escritos e commitados
   antes da implementação de cada fatia; backend e frontend implementam em paralelo contra o contrato.
3. **Backend em Java 21 + Spring Boot 3**, PostgreSQL 16. Não é a via mais rápida para um
   protótipo; é a via declarada pelo dono e alinhada ao objetivo profissional dele.

## Consequências

- Positivas: uma só árvore de trabalho (worktree por card funciona), contrato único elimina
  divergência de campos entre as pontas, um só `Makefile` e uma só suíte e2e.
- Negativas: Java exige JDK/Maven instalados e mais cerimônia (DTO, migração, build);
  monorepo exige disciplina de pasta para não acoplar front no backend.
- Mitigação: fatia 0 curta (esqueleto que anda) para validar o arranjo antes de investir.

## Alternativas descartadas

- **Monolito Node/TS** (Next.js com API routes): entrega mais rápida, uma linguagem só —
  descartada para não abandonar a stack-alvo do dono e a meta de vaga.
- **Multi-repo**: isolamento melhor, mas exige três clones por agente e sincronização
  manual de contrato — caro demais para um desenvolvedor e cinco agentes.
- **Contrato derivado do código** (gerar OpenAPI do backend): reverteria a ordem e faria o
  front esperar o backend a cada fatia. Descartado.
