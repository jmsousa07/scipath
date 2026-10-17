# E2.13 — Cabeçalho fixo cobre o título da página em 375 px

**Card no quadro:** `t_e0bc8b4d` · **Perfil:** frontend · **Depende de:** E2.11 (achado da verificação independente) · **Status:** pronto para execução

## Contexto

A verificação independente da Etapa 2 (E2.11, `docs/qa/e2-descoberta-2026-10-17.log`, achado A) mediu, em navegador real via CDP, que **em 375 px o cabeçalho fixo cobre o título da página**. Não é impressão: é medida.

Medição no main, frontend em `next build` + `next start`, API com o seed do E2.6:

    node docs/qa/e2-11-navegador.mjs        # com o frontend em :3000

| rota | cabeçalho (bottom) | h1 top | resultado |
|---|---|---|---|
| `/explorar` | 132 px | 79 | h1 inteiro (79–125) sob o cabeçalho; `elementFromPoint` na borda de cima do h1 devolve `INPUT` (a busca do cabeçalho) |
| `/` | 132 px | 95 | 37 px do título sob o cabeçalho |
| `/materiais/[slug]` | 132 px | 79 | 53 px do título sob o cabeçalho |
| as três, 1440 px | 64 px | 127 | ATENDE |

**Causa medida:** `frontend/src/styles/layout.css` reduz `.sc-page` a `padding-top:3rem` (48 px) em `@media (max-width:60rem)`, mas é exatamente nessa faixa que o `.sc-header` cresce de 64 px para 132 px (a busca desce para a linha de baixo: `min-height:4rem` + `flex-wrap`). O recuo de 3rem foi dimensionado para o cabeçalho de 64 px.

## Escopo

1. Fazer o conteúdo começar abaixo do cabeçalho fixo nas duas larguras do card (375 px e 1440 px), em `/`, `/explorar`, `/materiais/[slug]`, `/u/[slug]` e `/categoria/[slug]`.
2. A correção não pode depender de JavaScript para o conteúdo aparecer no HTML entregue (RNF023) nem introduzir rolagem horizontal (RNF002).
3. Se o cabeçalho mudar de altura ao rolar (`.is-scrolled`), o recuo precisa continuar correto.

## Fora de escopo

Refazer o cabeçalho, mexer no DS2 (`assets/design_system_2.html`) ou no contraste (achado B do E2.11 — card próprio, E2.14).

## Critérios de aceite

1. `node docs/qa/e2-11-navegador.mjs` com o frontend no ar: os seis critérios `CA009 cabeçalho fixo não cobre o título` (3 rotas × 2 larguras) **ATENDE**.
2. `RNF002 sem rolagem horizontal` continua **ATENDE** nas seis combinações.
3. `npm run build` + `npm run lint` + `npm run typecheck` verdes, saída colada.
4. Evidência versionada em `docs/qa/e2-13-<data>.log` (log da bateria + capturas 375/1440).

## Rastreabilidade

CA009, RNF002, DRS §15.3; achado A de `docs/qa/e2-descoberta-2026-10-17.log` (§21.4).
