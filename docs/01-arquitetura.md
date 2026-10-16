# Arquitetura (v0 — a ser refinada por ADR)

## Forma

Monorepo com três projetos e duas fronteiras explícitas.

```
backend/    Java 21 + Spring Boot 3 (Maven)  — API REST, regra de domínio
frontend/   Next.js + React + TS + Tailwind  — consome a API via contrato
e2e/        Playwright                       — verifica as duas pontas juntas
contracts/  openapi.yaml + migrations        — as fronteiras
```

**Fronteira 1 — API** (`contracts/openapi.yaml`): backend e frontend conversam por contrato.
O front tipa a partir dele. Mudou a API, mudou o contrato no mesmo card.

**Fronteira 2 — dados** (`contracts/db/migrations/`): o schema é versionado por migração
imutável. O domínio conhece o schema; o front nunca conhece o banco.

## Modelo de domínio (v0)

```
usuario     id, nome, email, papel (LEITOR|AUTOR|CURADOR), criado_em
material    id, titulo, tipo (NOTA_SLIDE_ARTIGO_EXPLICACAO_AULA), resumo, autor_id,
            tags[], arquivo_ref (StorageService), criado_em, atualizado_em
conceito    id, nome, descricao, area_id
material_conceito   material_id, conceito_id, papel (ENSINA|MENCIONA)
relacao     id, origem_conceito_id, destino_conceito_id, tipo (PREREQUISITO|RELACIONADO),
            forca (0..1), justificativa
trilha      id, titulo, objetivo, autor_id, publicada
trilha_item id, trilha_id, conceito_id, posicao
progresso   usuario_id, trilha_id, conceito_id, estado (BLOQUEADO|EM_CURSO|CONCLUIDO)
curadoria   id, material_id, revisor_id, parecer, veredito, criado_em
area        id, nome, slug
```

Decisões de modelagem importantes:
- Pré-requisito é **aresta de conceito→conceito**, não atributo de material. Assim uma
  trilha pode ser montada e verificada matematicamente (ordem topológica).
- `progresso` é derivado: BLOQUEADO quando existe aresta de pré-requisito sem CONCLUIDO.
  Não armazenar "bloqueado" como estado editável — é cálculo.
- Ligação material↔conceito tem **papel** (ENSINA vs MENCIONA): sem isso o grafo fica
  ruidoso e a busca por conceito devolve tudo.

## Consultas que definem a viabilidade

1. Vizinhos de um conceito em N saltos → `WITH RECURSIVE` + índice nos dois lados da aresta.
2. Ordem topológica de uma trilha → validação antes de publicar (ciclo = trilha inválida).
3. "Para entender X, falta o quê?" → arestas de entrada não concluídas para o usuário.
4. Busca de material → `tsvector` + GIN + `unaccent` (acento importa em português).

## Riscos técnicos conhecidos

- Grafo profundo sem limite de salto mata a consulta: toda navegação tem `depth` explícito e teto.
- Ordem implícita do Postgres em listagem quebra paginação: `sort` sempre explícito.
- Sem Docker, a integração de teste usa o Postgres local com schema isolado (nunca H2).
