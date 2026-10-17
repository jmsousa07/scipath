# E2.12 — 404 de verdade nas páginas dinâmicas (estado hoje é 200 + `noindex`)

- **Perfil:** frontend · **Depende de:** E2.11 (gate da Etapa 2) · **Status:** pronto para execução

## Contexto

O aceite da Etapa 2 (`docs/qa/aceite-etapa-2.md`, ressalva 1) registrou uma troca consciente: em
`/materiais/[slug]`, `/u/[slug]` e `/categoria/[slug]`, os estados "não encontrado" de **perfil não
público**, **material retirado/excluído** e **categoria inexistente** são servidos com **HTTP 200**,
`noindex, nofollow` e o estado no HTML entregue. Quem responde 404 é a **API**. Motivo medido: o
`notFound()` do App Router entrega página sem o estado no HTML, o que quebrava RNF023 (conteúdo no
HTML) e o §15.3 (estado explícito na tela).

O laudo declarou que 404 de verdade na página é **card próprio**, não regressão daquela integração.
Este é o card.

## Escopo

1. Fazer as três rotas responderem **404 real** quando o recurso não existe ou não é público —
   mantendo, no corpo, o mesmo estado explícito que existe hoje (§15.3) e sem perder o conteúdo no
   HTML entregue (RNF023). O `noindex` continua.
2. Manter o comportamento de SEO já verificado: robô que pedir essas URLs não recebe conteúdo
   indexável (CA011) e a resposta é a mesma para "não existe" e "não é público" — indistinguível
   (RN002, CA008).
3. Não mudar o que a API responde: o 404 de página é decisão de apresentação; a API segue com o
   contrato v0.3.
4. Registrar em `PAGE_BLUEPRINTS.md` a decisão final (200 vs 404 por rota) — hoje está registrada
   como ressalva, não como decisão.

## Fora de escopo

Mudar contrato, API ou migração. Refazer as telas. Mexer no `noindex` das demais rotas privadas.

## Critérios de aceite

1. Bateria reproduzível (reuse a forma das baterias em `docs/qa/`) provando 404 nas três rotas, com
   o estado visível no corpo e o `noindex` presente.
2. Teste de indistinção: recurso inexistente e recurso privado devolvem **o mesmo** status e o mesmo
   corpo.
3. Regressão: `/`, `/explorar`, `/materiais/[slug]` e `/u/[slug]` **existentes** continuam 200 com o
   conteúdo no HTML.
4. `next build` + `npm run lint` + typecheck verdes, saída colada.
5. `PAGE_BLUEPRINTS.md` atualizado com a decisão.

## Evidência esperada

Log da bateria (status e corpo por rota), log de build/lint/typecheck. Renomeie o arquivo de
evidência com o id do card (regra P1).

## Rastreabilidade

RN002, RNF023, CA008, CA011, DRS §15.3, §12.3; ressalva 1 de `docs/qa/aceite-etapa-2.md`.
