# E1.6 — Base do frontend: tokens do DS2, fontes e layout global

- **Perfil:** frontend · **Depende de:** E1.1 · **Status:** pronto para execução

## Objetivo

Montar a aplicação Next.js com o contrato visual do SciPath aplicado de forma centralizada —
nenhum valor de estilo solto em componente.

## Escopo

1. Projeto Next.js (App Router) + TypeScript + Tailwind conforme a stack ratificada.
2. `tokens.css` derivado de `assets/design_system_2.html`: paleta `--glue-*`, tokens semânticos
   `--sc-*`, raios, sombras, easings, durações e a escala tipográfica (`.sc-h1`…`.sc-h6`,
   `.sc-body*`, `.sc-caption`) — os valores devem ser **extraídos do arquivo**, não redigitados
   de memória (comandos `grep` estão em `DESIGN_SYSTEM.md`).
3. Fontes **locais**: subconjunto de `.woff2` copiado para `public/fonts/`, `@font-face` próprio
   com `font-display: swap` e as pilhas de fallback do `DESIGN_SYSTEM.md` §1. Sem CDN de fontes.
4. Layout global: cabeçalho (com busca) e rodapé conforme o DS, responsivos, com foco visível.
5. Duas páginas estáticas de fundação que provam o layout **sem dados**: `/termos` e
   `/privacidade` (dependências de lançamento, ADR-0007) — nada de página com dado fictício
   (§20.1). As páginas de descoberta são da Etapa 2.

## Fora de escopo

Componentes de estado (E1.7), chamadas de API, páginas de material/perfil/login.

## Critérios de aceite

1. `next build`, `npm run lint` e typecheck verdes (evidência por build, não por `next dev` —
   AGENTS.md, higiene de recursos).
2. **Nenhum hex/px de cor solto** em componente: um `grep` de verificação colado no card.
3. Layout sem rolagem horizontal em **375 px** e **1440 px** (checagem por dimensões de container;
   a conferência visual é do card de QA).
4. `prefers-reduced-motion: reduce` desliga animação decorativa (RNF005).
5. Foco visível por teclado no cabeçalho, busca e rodapé; nenhum texto dependendo só de cor
   (RNF006, RNF007).

## Evidência esperada

Log de build/lint/typecheck; `grep` de tokens; inventário dos arquivos criados.

## Rastreabilidade

RNF002, RNF003, RNF005, RNF006, RNF007, RNF023, DESIGN_SYSTEM.md, DRS §15.
