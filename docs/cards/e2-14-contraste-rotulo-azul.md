# E2.14 — Contraste do rótulo azul do DS2 sobre branco: 3.51:1 (WCAG AA pede 4.5:1)

**Card no quadro:** `t_8368fb6c` · **Perfil:** frontend · **Depende de:** E2.11 (achado da verificação independente) · **Status:** pronto para execução

## Contexto

A verificação independente da Etapa 2 (E2.11, `docs/qa/e2-descoberta-2026-10-17.log`, achado B) mediu, no render real via CDP, o contraste de cada par (cor do texto, fundo efetivo) em `/`, `/explorar` e `/materiais/[slug]`, em 375 px e 1440 px.

Sete pares foram medidos. Seis passam (18.56:1, 18.56:1, 9.29:1, 8.82:1, 6.05:1, 5.75:1). **Um não passa:**

    par  rgb(49,134,255) sobre rgb(255,255,255)  ->  3.51:1   (mínimo 4.5:1 para texto normal)
    exemplos: <i>SciPath</i> em `/`, <i>Descoberta</i> em /explorar, <i>Material</i> em /materiais/[slug]

O elemento é o rótulo-superior de seção (`<i>` dentro de `.sc-sec__num`, e o irmão `.sc-header__word i`): 11 px (`.6875rem`), mono, caixa alta, `color:var(--sc-blue)` = `#3186ff`.

**Origem do valor:** o DS2 — `assets/design_system_2.html:369` (`.sc-sec__num i { color:var(--sc-blue) }`) e `:318` (`.sc-header__word i`). Não foi invenção do frontend: `frontend/src/styles/tokens.css` e `design-system.css` são gerados do DS2 (`npm run tokens` / `check:tokens`). Reprodução:

    node docs/qa/e2-11-navegador.mjs     # com o frontend em :3000

## Escopo

1. Escolher e aplicar UMA saída, com a medida que a sustenta (as duas são legítimas; o card quer a decisão registrada, não a mais bonita):
   - **(a)** usar, para esse papel, um azul que atenda 4.5:1 sobre branco — e registrar a divergência do DS2 do mesmo jeito que `base.css` já registra as duas divergências intencionais (origem → aqui), com o novo valor por token em `tokens.css`;
   - **(b)** manter o valor do DS2 e provar, com medida, que o texto deixa de ser "texto normal" na acepção da WCAG 1.4.3 (>= 24 px, ou >= 18.66 px em negrito) — se for esse o caminho, a mudança é de tipografia, não de cor.
2. O par corrigido precisa valer nas **duas** larguras e nas rotas medidas (`/`, `/explorar`, `/materiais/[slug]`), e nas demais rotas que usam o mesmo papel (`/u/[slug]`, `/categoria/[slug]`, `/_estados`, `/termos`, `/privacidade`).
3. Se a saída for (a), `npm run check:tokens` não pode acusar divergência não registrada.
4. Não regredir os pares que já passam.

**Terceira saída, que é decisão de PO/gerente:** registrar a divergência como **aceita**, com o número medido — se o custo de mudar o rótulo do DS2 for maior que o ganho. A decisão precisa ficar escrita, não implícita.

Rastreabilidade da regra violada: `DESIGN_SYSTEM.md` §2 ("Texto sobre fundo branco usa `--sc-ink` / `--sc-ink-soft`; `--sc-ink-muted` só para metadado secundário com contraste verificado (AA)") e a tabela de papéis, que lista `--sc-blue` sob **Marca**, não sob texto. Ou seja: o valor é do DS2; o **uso como texto** contraria a regra de uso do próprio DESIGN_SYSTEM.md.

## Fora de escopo

Refazer a identidade visual, mexer em `assets/design_system_2.html` (o DS2 é o contrato visual; a divergência entra em `DESIGN_SYSTEM.md`/`base.css`, como já acontece) e o achado A do E2.11 (cabeçalho fixo — card próprio, E2.13).

## Critérios de aceite

1. `node docs/qa/e2-11-navegador.mjs` com o frontend no ar: o critério `RNF008 contraste WCAG AA dos pares de cor` **ATENDE** nas seis combinações (3 rotas × 2 larguras), com a lista de pares no log.
2. Nenhum outro par cai abaixo do mínimo depois da mudança (a bateria imprime todos).
3. `npm run build` + `npm run lint` + `npm run typecheck` (+ `check:tokens`/`check:style` se a saída for (a)) verdes, saída colada.
4. A decisão (a) ou (b) — ou a aceitação registrada —, com o número que a sustenta, em `DESIGN_SYSTEM.md` (ou `base.css`, se for divergência do DS2).
5. Evidência versionada em `docs/qa/e2-14-<data>.log`.

## Rastreabilidade

RNF008, CA009, WCAG 2.1 AA 1.4.3; ADR-0002; achado B de `docs/qa/e2-descoberta-2026-10-17.log`.
