# E2.10 — Perfil público e categoria

- **Perfil:** frontend · **Depende de:** E2.5, E2.7 · **Status:** pronto para execução

## Objetivo

Entregar `/u/[slug]` (RF006) e `/categoria/[slug]` (RF007) — as duas rotas que fecham a navegação
material ↔ autor ↔ categoria.

## Escopo

1. `/u/[slug]`: identidade pública (nome, bio, áreas, links) + materiais publicados do autor,
   paginados. A afiliação aparece **explicitamente como autodeclaração** ("declarado por", não
   "verificado") — RN011, CA012.
2. `/categoria/[slug]`: nome e descrição da categoria + materiais dela, com o mesmo componente de
   listagem do E2.8 (não duplicar tela).
3. Nenhum dado privado em nenhuma das duas (e-mail, conta, tokens) — RNF013.
4. Os 7 estados nas duas rotas; autor/categoria sem material → vazio explicativo.
5. SEO igual ao E2.9 (canonical, conteúdo no HTML).

## Fora de escopo

Editar o próprio perfil (Etapa 3); hierarquia profunda de categoria (o modelo tem um nível).

## Critérios de aceite

1. `curl` do perfil não contém e-mail nem qualquer identificador de conta (verificação colada).
2. O texto de afiliação não usa "verificado" em nenhum lugar (grep no código e no HTML).
3. Navegação por teclado e foco visível; 375 px e 1440 px sem rolagem horizontal.
4. `next build` + lint + typecheck verdes.

## Evidência esperada

Log de build/lint; `curl` das duas rotas (autor com e sem material); grep de "verificado" vazio.

## Rastreabilidade

RF006, RF007, RN011, RNF013, RNF023–RNF025, CA003, CA011, CA012.
