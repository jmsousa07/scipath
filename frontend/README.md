# frontend — Next.js (App Router) + TypeScript + Tailwind

Base do frontend do SciPath (card E1.6). O contrato visual é `../assets/design_system_2.html`
(DS2); a tradução está em `../DESIGN_SYSTEM.md`; as páginas em `../PAGE_BLUEPRINTS.md`.

## Comandos

| Comando | O que faz |
|---|---|
| `npm run tokens` | **regenera** as três camadas derivadas do DS2 (tokens, fontes, componentes) |
| `npm run check:tokens` | falha se as camadas derivadas estiverem desatualizadas em relação ao DS2 |
| `npm run check:style` | hex/px fora do lugar e `var(--token)` sem definição |
| `npm run check:layout` | largura fixa, `fr` cru, `overflow-x` e matemática de container em 375/1440 px |
| `npm run typecheck` | `tsc --noEmit` |
| `npm run lint` | ESLint (flat config com `eslint-config-next`) |
| `npm run build` | build de produção (a evidência do card é build, não `next dev`) |
| `npm run verify` | a bateria inteira, na ordem |

## De onde vem o estilo (nenhum valor redigitado)

```
assets/design_system_2.html ──┬─→ src/styles/tokens.css        (paleta, escala de tipo, motion)
                              ├─→ src/styles/design-system.css  (76 regras de componente, verbatim)
assets/fonts/google-sans/source/css2_*.css ─→ src/styles/fonts.css + public/fonts/*.woff2 (subset latino)
src/styles/base.css            (fundação, valores por token)
src/styles/layout.css          (regra de projeto: breakpoints, container, toque)
src/app/globals.css            (importa tudo em camadas e mapeia token → utilitário Tailwind)
```

Os arquivos marcados como gerados começam com um cabeçalho dizendo isso; o gerador cita a
linha de origem de cada regra. Se o DS2 mudar, `npm run tokens` produz o diff.

## Decisões que valem a pena conhecer

1. **Tailwind 4 (CSS-first).** Não existe `tailwind.config.js`: o `@theme` de `globals.css`
   só aponta para os tokens (`--color-ink: var(--sc-ink)`), então o DS2 continua sendo a única
   fonte de valor.
2. **Fontes locais.** Nada de CDN: 12 arquivos `.woff2` (~391 KB) do subset latino, servidos de
   `/fonts`, `font-display: swap`. Ícone não usa fonte de ícone — é SVG inline.
3. **Sem `overflow-x: hidden`.** Esconder o estouro esconde o defeito. A correção está em
   `layout.css` e a verificação em `check:layout`.
4. **`/explorar`** é a descoberta pública: lista, busca textual, filtros e paginação. A tela
   usa os mesmos critérios de elegibilidade da API; o QA E2.11 e o aceite P5 registram essa jornada.
5. **`NEXT_PUBLIC_SITE_URL`** define o domínio canônico dos metadados; sem ela o build usa
   `http://localhost:3000` (ver `.env.example`).