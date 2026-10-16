# E2.8 — Explorar: listagem, busca, filtros e paginação

- **Perfil:** frontend · **Depende de:** E2.2, E2.3 · **Status:** pronto para execução

## Objetivo

Entregar `/explorar` (RF002, RF003, RF004) — a tela onde o visitante encontra material.

## Escopo

1. Listagem paginada consumindo `GET /api/materiais`, com filtros de categoria e tipo e ordenação
   conforme o contrato.
2. **Estado na URL** (`q`, `categoria`, `tipo`, `ordem`, `page`): a URL é compartilhável e
   reproduz a mesma lista; filtros sobrevivem à troca de página.
3. Busca com espera antes de disparar (não disparar a cada tecla) e indicação de que está buscando.
4. Filtros visíveis, removíveis um a um e com "limpar tudo".
5. Os **7 estados**: em especial `vazio` = "nenhum material encontrado" com sugestões (limpar
   filtro, ver categorias) — nunca tela branca; e `erro de rede` com ação de tentar de novo.

## Fora de escopo

Detalhe do material (E2.9); ordenação por relevância (não existe).

## Critérios de aceite

1. `next build` + lint + typecheck verdes; nenhum dado fictício na tela.
2. Colar a URL com filtros no navegador reproduz exatamente a mesma lista (evidência com as duas
   URLs e as duas listagens).
3. Navegação completa por teclado nos filtros e na paginação, com foco visível.
4. Sem rolagem horizontal em 375 px e 1440 px.

## Evidência esperada

Log de build/lint; par de URLs + listagens; registro dos estados exercitados.

## Rastreabilidade

RF002, RF003, RF004, RN013, RNF002, RNF004, RNF006, RNF021, CA001, CA009.
