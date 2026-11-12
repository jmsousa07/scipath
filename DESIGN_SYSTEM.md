# DESIGN_SYSTEM.md — contrato visual do SciPath

> **Fonte primária:** `assets/design_system_2.html` (contrato visual, base Google Research).
> **Referência secundária:** `assets/design_system.html` (sistema anterior; não usar como base).
> Este documento **traduz** o DS2 para implementação. Em divergência de **valor** (cor, tamanho,
> easing), o DS2 vence: ele é o artefato executável. Em divergência de **intenção**, vale o DRS.
>
> Direção do DRS (§3.5): **Academic Modernism** — rigor e clareza com expressão contemporânea.
> Referências de princípio (Google Research, Are.na, Wellcome, DeepMind Science, Stanford,
> Vercel, Digital Architect) **não** são templates a copiar.

## 0. Regras para quem implementa

1. **Nenhum valor mágico.** Cor, fonte, raio, sombra e duração vêm de tokens (`tokens.css`),
   derivados do DS2. Proibido hex solto em componente (`RNF003`).
2. **Nenhum estilo por tela.** Componente equivalente tem o mesmo comportamento e os mesmos
   estados em todas as páginas; tela nova usa componente existente ou promove um ao sistema.
3. **Não altere o DS2 num card de produto.** Ajuste no contrato visual é card próprio (o DS2 foi
   construído antes do DRS e tem pendência conhecida de tagline).
4. **Toda página implementa os sete estados** (§15.3) — ver `PAGE_BLUEPRINTS.md`.
5. **Acessibilidade é critério de aceite**, não polimento: foco visível, navegação por teclado,
   contraste AA, rótulo associado ao campo, erro associado ao controle (`RNF006`, `RNF007`).

## 1. Tipografia

**Famílias** (variáveis do DS2, com fallback em cascata):

| Papel | Token | Stack |
|---|---|---|
| Títulos / display | `--font-display` | Google Sans Display → Google Sans Flex → Google Sans → Roboto → Arial |
| Texto primário / UI | `--font-primary` | Google Sans Flex → Google Sans → Google Sans Text → Roboto |
| Corpo editorial | `--font-text` | Google Sans Text → Google Sans Flex → Roboto |
| Código / dados | `--font-mono` | Roboto Mono → Geist Mono → Google Sans Flex → ui-monospace |
| Ícones | `--font-symbol` | stack de referência do DS2; não carregada no app, que usa SVG inline |

**Escala real do DS2** (extraída do arquivo; `h5`/`h6` existem e devem ser lidos de lá):

| Classe | Família | Peso | Tamanho | Line-height | Letter-spacing |
|---|---|---|---|---|---|
| `.sc-h1` | display | 500 | `clamp(2.75rem, 5.4vw, 4rem)` | 1.04 | −0.035em |
| `.sc-h2` | display | 500 | `clamp(2.25rem, 4.4vw, 3.25rem)` | 1.06 | −0.03em |
| `.sc-h3` | display | 500 | `2rem` | 1.12 | −0.025em |
| `.sc-h4` | display | 500 | `1.625rem` | 1.16 | −0.02em |
| `.sc-body-lg` | text | 400 | `1.125rem` | 1.65 | 0 |
| `.sc-body` | primary | 400 | `1rem` | 1.55 | 0 |
| `.sc-body-sm` | text | 400 | `.875rem` | 1.55 | 0.005em |
| `.sc-caption` | text | 400 | `.78125rem` | 1.45 | 0.01em (cor `--sc-ink-muted`) |
| `.sc-label` | — | — | — | — | `font-variant-numeric: tabular-nums` |

**Carregamento:** as famílias são servidas de **arquivos locais**, sem CDN. O gerador usa os
subconjuntos latinos licenciados em `assets/fonts/google-sans/source/`, copia os 12 arquivos
necessários para `frontend/public/fonts/` e gera `@font-face` com `font-display: swap`. Google Sans
(incluindo Flex, Display e Text) e Roboto Mono acompanham seus textos SIL Open Font License 1.1;
consulte `assets/fonts/OFL-*.txt`.

**Regenerar os valores em vez de adivinhar:**

```bash
grep -oE '\.sc-h[1-6]\s*\{[^}]*\}' assets/design_system_2.html   # escala de títulos
grep -oE '^\s*--[a-z0-9-]+:\s*[^;]+;' assets/design_system_2.html # tokens
```

## 2. Cores

**Paleta base** (`--glue-*`, importada do material do Google Research):

```
grey-0 #fff · grey-50 #e6eaf0 · grey-300 #b2bbc5 · grey-600 #80868b · grey-900 #2f3034 · grey-1200 #121317
blue-50 #bbe2ff · blue-500 #3186ff · blue-800 #2b4fda
green-100 #9be69a · green-700 #009954      red-50 #ffdeec · red-500 #fc413d
yellow-100 #fff350                          purple-100 #ea80fc
```

**Tokens semânticos** (`--sc-*`) — é **isto** que o componente usa:

| Papel | Token | Valor |
|---|---|---|
| Texto principal | `--sc-ink` | `--glue-grey-1200` |
| Texto suave | `--sc-ink-soft` | `--glue-grey-800` |
| Texto secundário | `--sc-ink-muted` | `--glue-grey-700` |
| Superfície / camadas | `--sc-surface`, `-2`, `-3` | `grey-0`, `grey-10`, `grey-20` |
| Borda | `--sc-line` / `--sc-line-strong` | `#dfe3e8` / `#c4cad2` |
| Marca | `--sc-blue` `#3186ff`, `--sc-green` `#00af57`, `--sc-red` `#fc413d`, `--sc-yellow` `#fbbc04`, `--sc-purple` `#681da8` | |
| Raio pequeno | `--sc-radius-s` | `8px` |
| Sombras | `--sc-shadow-s`, `--sc-shadow`, `--sc-shadow-l` | 3 níveis de elevação |
| Brilho de foco/estado | `--sc-glow-blue`, `--sc-glow-red` | anel + halo |

**Regras de uso:**
- Cor **nunca** é a única portadora de informação (`RNF007`): acompanhe com texto, ícone ou forma.
- Texto sobre fundo branco usa `--sc-ink` / `--sc-ink-soft`; `--sc-ink-muted` só para metadado
  secundário com contraste verificado (AA).
- Estado de erro usa `--sc-red` **com** mensagem associada ao campo; sucesso usa `--sc-green`
  com texto — nunca só a cor.
- As cores "decorativas" do DS2 (aurora, malha, holofote) são **fundo**, jamais transporte de
  significado; e não podem reduzir contraste do conteúdo.
- As cores de **marca** (`--sc-blue`, `--sc-green`, `--sc-red`, `--sc-yellow`, `--sc-purple`) são
  acento de superfície, estado e ícone — **não** são cor de texto por padrão. Quando um papel de
  texto precisa de azul, usa o token da paleta que passa AA (ver §7.7 e §7.8, os casos do
  rótulo-superior e do botão-texto).

## 3. Componentes (classes reais do DS2)

O DS2 tem 138 classes `.sc-*`; estas são as famílias que o produto usa:

| Família | Classes |
|---|---|
| Botões | `.sc-btn`, `--primary`, `--blue`, `--ghost`, `--text`, `--sm`, `--lg`, `--dis` (desabilitado), `.sc-btn-state` |
| Campos | `.sc-field`, `--float`, `--ok`, `--error`, `--mono` |
| Cards | `.sc-card`, `--pad`, `--hoverable`, `--spot`, `--dark`, `.sc-graph-card`, `.sc-font-card` |
| Cabeçalho / rodapé | `.sc-header` (fixo, com busca), `.sc-footer` |
| Navegação | `.sc-tabs`, `.sc-tab`, `.sc-tabpanel`, `.sc-bar` |
| Dados | `.sc-table`, `--dark`, `.sc-table-wrap` |
| Sinais | `.sc-badge` (`--blue/green/red/yellow/purple/dark/pulse`), `.sc-chip` (`--green/red/yellow`), `.sc-avatar` |
| Sobreposição | `.sc-modal`, `.sc-toast`, `.sc-toasts` |
| Movimento / demonstração | `.sc-anim-card`, `.sc-bench`, `.sc-bg` (`--aurora/--grid/--noise/--spot)` |

Do template Google Research vêm as classes `glue-*` (grade, cards, botões de ênfase) — usar como
estão; **não** redefinir.

> **Pendência conhecida:** `.text-heading-64`, `.text-copy-14`, `.sc-mono` e `.sc-label` não têm
> definição de tamanho no CSS do DS2 (são hooks do template). Ao consumir no frontend, mapeie para
> a escala acima ou complete o token — e registre no card, sem inventar valor solto.

## 4. Movimento

**Easings:** `--ease-google cubic-bezier(.058,.919,.184,.999)` · `--ease-io cubic-bezier(.4,0,.2,1)`
· `--ease-out-soft cubic-bezier(.18,.9,.22,1)`. **Duração base:** `--dur-fast .15s`.

**18 animações do DS2:** `scAuroraBreathe`, `scCue`, `scFade`, `scFloat`, `scGridDrift`, `scMarquee`,
`scMegaIn`, `scMeshIn`, `scModalIn`, `scNoiseShift`, `scNow`, `scPaperIn`, `scPulse`, `scRiseIn`,
`scSheen`, `scSpin`, `scTabIn`, `scToastIn`.

**Regras (RNF005):**
- Movimento serve a orientação e feedback (entrada de conteúdo, transição de estado), não a
  espetáculo em tela de leitura.
- `prefers-reduced-motion: reduce` desliga animação decorativa e mantém apenas transição de estado.
- Nada de animação que atrase a leitura ou bloqueie interação; sem animação obrigatória para
  conteúdo aparecer (o conteúdo deve existir sem JS).
- Backgrounds animados (aurora, malha, holofote, grão) são **camadas de fundo** de baixo custo,
  desligáveis, e nunca cobrem conteúdo.

## 5. Ícones

A stack `--font-symbol` de `Google Symbols` / `Material Symbols Outlined` preserva a referência do DS2; o frontend atual não carrega uma fonte de ícones e usa SVG inline (`frontend/README.md`). Regras: ícone decorativo tem `aria-hidden`; ícone com significado tem nome acessível (`RNF007`); tamanho e alinhamento pelos tokens de tipografia, não por largura fixa inventada.

## 6. Layout e responsividade

Unidades de container (`cqw`) no hero e nas seções de demonstração do DS2 fazem o layout escalar
sozinho; o conteúdo precisa quebrar bem em **375 px** e **1440 px** (viewports de verificação).
Sem rolagem horizontal (`RNF002`). Grade de 12 colunas do template (`glue-grid__col--span-*`).

## 7. Pendências deste contrato

1. **Tagline:** DS2 diz "Conhecimento científico, interligado"; o DRS usa "Conhecimento científico
   em contexto" / "Descobrir, compreender e compartilhar conhecimento". Decidir e ajustar o DS2 em
   card próprio.
2. `.sc-h5`, `.sc-h6` e os hooks `text-*` sem valor definido (ver §3).
3. O DS2 foi construído antes do DRS: as páginas do produto (`PAGE_BLUEPRINTS.md`) podem exigir
   componentes que ainda não existem no sistema — cada um vira card de design system, não
   improviso na tela.
4. **Tabela de dados:** o `.sc-table` do DS2 é a tabela de **especificação do próprio documento**
   (`min-width:660px`, `white-space:nowrap`, `th` fixo no topo). A variante de PRODUTO (lista de
   materiais, E1.7) vive em `frontend/src/styles/layout.css` com a mesma linguagem visual, sem
   largura mínima fixa, porque as telas precisam caber em 375 px (RNF002). As três divergências e
   o motivo estão comentados no próprio CSS.
5. **Hooks do DS2 sem definição:** `.sc-chip` usa `var(--font-sans, var(--font-primary))` e o DS2
   nunca define `--font-sans`. O alias está declarado em `frontend/src/app/globals.css`, apontando
   para o MESMO fallback que o contrato escreveu — nenhuma fonte nova foi inventada.
6. **Primitivos que o contrato não tem** (página/estado): paginação (`.sc-pagination`) e mensagem
   de estado (`.sc-notice`) foram criados na camada de projeto (E1.7). Se o DS2 ganhar os seus, a
   camada de componentes do DS2 passa a mandar e os de `layout.css` saem.
7. **Contraste do rótulo-superior azul (E2.14 — decidido em 2026-10-31).** O DS2 pinta
   `.sc-sec__num i` e `.sc-header__word i` com `color:var(--sc-blue)` (`assets/design_system_2.html`
   :369 e :318). Esse azul sobre branco mede **3.51:1** — abaixo do mínimo AA 1.4.3 (**4.5:1**) para
   texto de 11 px. Decisão: **saída (a)**. Aquele papel de texto passa a usar **`#2b4fda` =
   `var(--glue-blue-800)`**, que **já existe na paleta do DS2** (`tokens.css`), medido em **6.49:1**
   sobre branco e **6.17:1** sobre `--sc-surface-2`. Não é hex inventado nem matiz nova; a **marca**
   continua `--sc-blue` — muda o papel de TEXTO, não a cor da marca. As outras duas saídas foram
   descartadas com o número na mão: **(b)** exigiria levar o rótulo a ≥24 px (11 px não é "large
   text" na acepção da 1.4.3) e isso descaracteriza o papel, que é de 11 px por desenho; **(c)**
   significa aceitar um rótulo de seção ilegível para baixa visão — ele é texto de leitura, não
   metadado decorativo. O azul novo só poderia nascer no DS2: `tokens.css`/`design-system.css` são
   gerados (`check:tokens`) e `check:style` (A) proíbe hex de cor fora deles; por isso o override
   entra em `frontend/src/styles/layout.css` (camada de projeto, carregada depois de
   `design-system.css`) usando o token da paleta, com o comentário de divergência no próprio CSS.
   Medição reproduzível: `python3 e2-14-contraste.py` (instrumento e laudo vivem fora do
   repositório desde o ADR-0012). Rastreabilidade: RNF008, CA009, achado B do E2.11.
8. **Contraste dos papéis de texto FORA do `<main>` (E2.16 — corrigido em 2026-10-31).** A verificação
   do E2.14 mediu o documento INTEIRO e achou dois pares abaixo de 4.5:1 que a bateria do E2.11 não
   vê, porque ela varre só o `main` (achado na seção 4.5 de `docs/qa/e2-14-verificacao-2026-10-31.log`):
   **(a)** `.sc-btn--text` (o botão-texto "Verificar de novo" do cabeçalho, 13 px) usa
   `color:var(--sc-blue)`, o mesmo defeito do item 7 — cor de **marca** como cor de **texto** (§2):
   **3.51:1** sobre branco. O papel de texto passa a `var(--glue-blue-800)` (#2b4fda): **6.49:1**
   sobre branco e **6.17:1** sobre `--sc-surface-2`; a marca continua `--sc-blue`.
   **(b)** `.sc-mono` (papel extraído do DS2, com `color:var(--sc-ink-soft)` = #45474d, pensado para
   fundo claro) no rodapé escuro (`.sc-footer`, fundo `--glue-grey-1200`) mede **2:1** em 11 px.
   Dentro do rodapé o papel passa a **herdar a cor do bloco** (`color:inherit`), como o texto ao
   redor — a razão medida vira a do parágrafo, **8.87:1**.
   As duas regras vivem em `frontend/src/styles/layout.css` §16 (camada de projeto, depois do
   `design-system.css`), com o número medido no comentário; nenhum arquivo gerado
   (`tokens.css`/`design-system.css`) nem o DS2 foi tocado. Medição reproduzível:
   `bash docs/qa/e2-16-evidencia.sh`; conta em `python3 docs/qa/e2-16-contraste.py`; cor
   computada nó a nó em `node docs/qa/e2-16-sonda.mjs`; laudo em
   `docs/qa/e2-16-2026-10-31.log`. Rastreabilidade: RNF008, WCAG 2.1 AA 1.4.3; achado do E2.14.
