# E2.2 — Listagem paginada com filtros e ordenação

- **Perfil:** backend · **Depende de:** E2.1, E1.8 · **Status:** pronto para execução

## Objetivo

Servir `GET /api/materiais` conforme o contrato v0.3, com SQL explícito e resultado previsível.

## Escopo

1. Consulta com `JdbcClient` (sem ORM): filtros `categoria` e `tipo` por `slug`, paginação do
   envelope, apenas materiais **PUBLICADOS** e de contas **ATIVAS** (RN002), nunca `RETIRADO` nem
   `EXCLUIDO` (CA008).
2. Ordens: `recentes` (padrão) com desempate por `id` — ordenação determinística, sem "relevância"
   inventada; e `titulo`.
3. Conjunto vazio responde **200 com `items: []`** — vazio não é erro (RNF004).
4. Índices: usar os do V1; colar `EXPLAIN` da consulta de listagem mais comum.

## Fora de escopo

Busca por texto (E2.3) e detalhe (E2.4).

## Critérios de aceite

1. Teste de integração com dados controlados no `scipath_test`: ordem e paginação asseridas
   página a página, provando ausência de duplicata e de omissão entre páginas (RN013).
2. Filtro por categoria/tipo com `slug` inexistente → 200 vazio; parâmetro inválido → 400.
3. `EXPLAIN` colado mostrando uso de índice (RNF020).
4. `make test` verde em shell limpo (convenção do E1.5).

## Evidência esperada

Saída dos testes de integração, `EXPLAIN`, e o log do `make test` limpo.

## Rastreabilidade

RF002, RF004, RN002, RN013, RNF020, RNF021, CA008, ADR-0005.
