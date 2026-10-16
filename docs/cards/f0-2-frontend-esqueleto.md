# F0.2 — Frontend: esqueleto Next.js com os tokens do design system

## Contexto

Segundo projeto do SciPath. Precisa existir uma base de front que suba, já com os
tokens e a tipografia do contrato visual, para que a tela de materiais (F0.4) seja
só montagem e não invenção de estilo.

## Entrada

- `assets/design_system_2.html` — contrato visual (classes `sc-*`, paleta `--glue-*`,
  tipografia Google Sans/Display/Text + Roboto Mono em
  `assets/templates/research.google/assets/*.woff2`).
- `docs/00-visao.md` (tom do produto), `AGENTS.md` (regras).
- `contracts/openapi.yaml` — ainda não consumido neste card.

## Saída

`frontend/` com Next.js (App Router) + TypeScript + Tailwind configurado a partir de
`tokens.css` (cores, raios, escala tipográfica e easings do design system),
fontes locais copiadas para `frontend/public/fonts/` e uma rota `/` de demonstração
que exiba a escala tipográfica e a paleta. Nada de chamadas de API ainda.

## Critérios de aceite

1. `make web:dev` sobe em :3000 e a página inicial carrega sem erro no console.
2. Os tokens estão em `frontend/tokens.css` como CSS vars (nenhum hex solto em componente);
   a paleta e a escala do print conferem com `assets/design_system_2.html`.
3. Tipografia: as três famílias carregam **locais** (sem requisição a fonts.googleapis.com),
   com `font-display: swap`.
4. `make lint` roda verde (eslint + typecheck).
5. Responsivo: 375 px e 1440 px sem rolagem horizontal.

## Fora de escopo

Consumir a API, autenticação, componentes de domínio (card de material, trilha, grafo),
tema escuro.

## Evidência exigida

Prints em `docs/qa/f0-2-<tela>-375.png` e `docs/qa/f0-2-<tela>-1440.png` + arquivo
`docs/qa/f0-2-frontend.log` com `make lint` e a lista de arquivos de fonte carregados.
