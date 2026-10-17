# Aceite da Etapa 2 (E2.4 a E2.10) — integração no `main`

- **Ato:** card `t_3038e169`, perfil **gerente** (aceite e merge são ato do orquestrador).
- **Data:** 2026-10-17 (UTC). **Checkout:** `$HOME/scipath`, branch `main`.
- **Log bruto:** `docs/qa/integracao-etapa-2-2026-10-17.log` (mesmo diretório).
- **Pais do card:** E2.8 (`t_4511bd3d`), E2.9 (`t_aa19ac87`), E2.10 (`t_ea75a1e9`).
- **Filho liberado por este aceite:** E2.11 (`t_b570b520`), verificação independente da Etapa 2.

## Estado medido do `main` antes e depois

| Medida | Antes deste card | Depois |
|---|---|---|
| `main` (topo) | `7fbbb60` | `1a6b293` |
| Commits de merge deste card | — | `9aaee2d` (E2.5), `8f84596` (E2.9), `1a6b293` (E2.10) |
| `git rev-list --count main..<branch>` | 0/2/0/0/3/2 | **0 para as seis** |
| `contracts/openapi.yaml` | v0.3.0 (`6002a17`, E2.1) | igual — nenhum card desta etapa tocou o contrato |
| `db/migration` | `V3` (E2.3, `ae0df1f`) | igual — nenhuma migração nova, nenhuma editada |

**Correção de premissa do card:** o corpo dizia que só E2.2, E2.3 e E2.6 estavam no `main`.
Quando este card rodou, **E2.4** (`939df0e`) e **E2.7+E2.8** (`e0fb4d6`, branch empilhada — nota do
E2.7 no card) **já estavam integrados** por outro ato. As branches a integrar eram três: E2.5, E2.9,
E2.10. Os `merge --no-ff` de E2.4/E2.7/E2.8 responderam `Already up to date`, e o critério 1
(janela de commits vazia para as seis) continua valendo sem exceção.

## Veredito por card — uma linha cada

| Card | Branch | Commits | Arquivos que o merge trouxe | Conferido neste aceite | Veredito |
|---|---|---|---|---|---|
| E2.4 `t_df6b24ea` | `feat/e2-4-detalhe` | `5e866b6`, `3decbde` | `material/{MateriaisController,MateriaisService,MateriaisRepositorio,MaterialDetalheResposta,LicencaDetalheResposta}`, `MateriaisDetalheIntegrationTest`, `docs/qa/e2-4-*` | suíte do backend no `main` (5 testes "detalhe público de material (RF005)", 0 falhas) + `make lint` + `make contract:check` | **ACEITO** |
| E2.5 `t_428f779c` | `feat/e2-5-perfil-publico` | `bef8ccc`, `07e2024` | `autor/{AutorController,AutorPublicoResposta,AutorRepositorio,AutorService,PerfilPublico}`, `material/MateriaisRepositorio`, `material/MateriaisService`, `AutorPublicoIntegrationTest`, `docs/qa/e2-5-*` | **bateria do próprio card re-executada no `main`**: 8 ATENDE · 0 NÃO ATENDE (8 critérios), incluindo `make test` em shell limpo (`env -i`, sem `.env`) | **ACEITO** |
| E2.7 `t_270e113f` | `feat/e2-7-pagina-inicial` | `f8a9c35`, `e0619d2` | `app/page.tsx`, `components/SearchForm.tsx`, `components/ui/Recarregar.tsx` + arquivos do E2.8 que a branch empilhou | bateria do E2.9 no `main`, seção 7.2: `/` com proposta de valor, busca, categorias e 6 recentes, `data-state="success"` | **ACEITO** |
| E2.8 `t_4511bd3d` | `feat/e2-8-explorar` | `e30c499` (entrou no `main` empilhado: `1ab02fa` dentro do E2.7, `e0fb4d6`) | `app/explorar/{page,BuscaEFiltros}.tsx`, `components/dominio/{CardMaterial,ListagemDeMateriais}.tsx`, `lib/api/{client,tipos}.ts`, `lib/explorar/consulta.ts`, `lib/format.ts`, `lib/rotas.ts`, `styles/layout.css`, `docs/qa/e2-8-*` | **bateria do próprio card re-executada no `main`**: 21 ATENDE · 0 NÃO ATENDE (mesmo resultado do log do card, incluindo a URL que reproduz a lista e os sete estados do DRS §15.3) | **ACEITO** |
| E2.9 `t_aa19ac87` | `feat/e2-9-detalhe-seo` | `faf29c3`, `bb30347` (a branch já trazia o E2.7 no próprio histórico: `537b5f3`) | `app/materiais/[slug]/page.tsx`, `app/not-found.tsx`, `lib/api/{client,tipos}.ts`, `lib/rotas.ts`, `docs/qa/e2-9-*` | **bateria do próprio card re-executada no `main`**: 25 ATENDE · 0 NÃO ATENDE (inclui regressão de `/explorar` e `/`, e as duas respostas 404 de HTML idêntico) | **ACEITO** |
| E2.10 `t_ea75a1e9` | `feat/e2-10-perfil-categoria` | `b020bef`, `08bead7` | `app/categoria/[slug]/page.tsx`, `app/u/[slug]/page.tsx`, `components/dominio/{CardMaterial,EstadoDaFalha}.tsx`, `lib/api/{client,tipos}.ts`, `lib/pagina.ts`, `lib/rotas.ts`, `PAGE_BLUEPRINTS.md` §5, `docs/qa/e2-10-*` | **bateria do próprio card re-executada no `main`**: 27 ATENDE · 0 NÃO ATENDE — incluindo as 7 sondas de dado privado (`id`, `contaId`, `email`, `senhaHash`, `token`) ausentes do HTML entregue | **ACEITO** |

Nenhum dos seis vereditos é por leitura de diff: cada um vem de bateria reproduzível ou da suíte.

## Gate do DRS §17.5, no `main` pós-merge (saída colada no log, seção 4–6)

- `make test` → `Tests run: 63, Failures: 0, Errors: 0, Skipped: 0` · `BUILD SUCCESS` · 03:09 min.
  O E2.5 re-rodou a suíte em **shell limpo** (`env -i PATH=/usr/bin:/bin HOME=$HOME LC_ALL=C.UTF-8`,
  sem `.env`): mesmo 63/0/0 — a receita não depende de variável de quem rodou.
- `make lint` → `eslint .` e `tsc --noEmit` sem saída, exit 0.
- `make contract:check` → `No results with a severity of 'error' found!`, exit 0.
- Migração: nenhuma nova; nenhuma já aplicada foi editada (AGENTS.md regra 1).
- Higiene de recursos: portas 8080, 3000 e 8099 livres ao fim; nenhum `java`, `surefire` ou
  `next-server` deixado vivo.

## Conflitos de merge — registro (AGENTS.md: cada conflito declarado no laudo)

Um único conflito, **em `docs/qa/e2-8-fixture-api.mjs`**, ao mergear E2.10 (os merges de E2.5 e E2.9
foram automáticos; em `frontend/src/lib/api/client.ts`, `api/tipos.ts` e `lib/rotas.ts` o `ort`
combinou as três adições de símbolos sem marcador).

| Arquivo | Lado escolhido | Motivo |
|---|---|---|
| `docs/qa/e2-8-fixture-api.mjs`, bloco de comentário do cabeçalho | **os dois** | E2.9 documenta `GET /api/materiais/{slug}`; E2.10 documenta `GET /api/autores/{slug}`, `descricao` das categorias e as sondas de dado privado. Texto aditivo, sem sobreposição. |
| `docs/qa/e2-8-fixture-api.mjs`, corpo do handler | **os dois**, na ordem E2.9 → E2.10 | `responderSlugInvalido(slug)` (400 de slug fora do formato, E2.9) e `responderNaoEncontrado()` (404 de corpo idêntico para inexistente e não público, E2.10) são funções independentes e ambas necessárias às suas baterias. |

Foi conferido que a resolução **não descartou nem reescreveu** lado nenhum:

- `git diff feat/e2-9-detalhe-seo -- docs/qa/e2-8-fixture-api.mjs` = exatamente as adições do E2.10;
- `git diff feat/e2-10-perfil-categoria -- docs/qa/e2-8-fixture-api.mjs` = exatamente as adições do E2.9;
- `node --check docs/qa/e2-8-fixture-api.mjs` → sintaxe OK;
- as duas funções aparecem definidas e **usadas** (linhas 295/318 definem; 418, 373 e 469 usam);
- as duas baterias que consomem o arquivo (E2.9 e E2.10) foram re-executadas e passaram.

**Nenhum conflito exigiu decisão de produto.** Não houve bloqueio. O arquivo em conflito é harness de
evidência (mora em `docs/qa/`, nenhum arquivo de produto o referencia — a própria bateria do E2.8
confere isso por `grep`), então a resolução não toca comportamento do produto.

**Commits autorais deste card:** nenhum além dos três `merge --no-ff` e do commit de documentação
(`docs(qa): aceite e log da integração da Etapa 2`), que só acrescenta `docs/qa/aceite-etapa-2.md` e
`docs/qa/integracao-etapa-2-2026-10-17.log`. Nenhuma linha de código de produto foi escrita aqui.

## Ressalvas abertas (não são bloqueios; cada uma tem dono)

1. **Rota dinâmica responde 200 com o estado no HTML.** Em `/materiais/[slug]`, `/u/[slug]` e
   `/categoria/[slug]`, perfil não público, material retirado ou categoria inexistente saem com
   **200 + `noindex, nofollow`** e o estado no documento; quem responde 404 é a **API**. É troca
   registrada e medida por E2.9 e E2.10 (o `notFound()` do App Router esconde o estado do HTML
   entregue). 404 de verdade na página é **card próprio**, não regressão desta integração.
2. **E2.10 mapeia 400 da API para "erro de rede/servidor", não para "não encontrado"**, porque `slug`
   e `page` são validados antes de a requisição sair; e categoria fora das ativas vira
   "não encontrado", não lista vazia. Escrito no código e coberto por verificação do card.
3. **E2.5 aceita por bateria de backend; as telas equivalentes são E2.10.** O E2.5 não tocava o
   contrato (os endpoints já estavam na v0.3.0) nem migração.
4. **Baterias próprias do E2.4 e do E2.7 não foram re-executadas neste card.** O comportamento do
   E2.4 é medido pela suíte do backend (5 testes verdes no `main`); o do E2.7 pela seção 7.2 da
   bateria do E2.9 (6 recentes e `data-state="success"` no `/`) e pela seção 7.1 (`/explorar` com 20
   cards, link do card resolvendo em `/materiais/[slug]`). Ambas as baterias originais continuam
   versionadas em `docs/qa/` e são reproduzíveis por comando.

## O que NÃO foi verificado (dono: E2.11 e Etapa 3)

- **Backend real + seed de desenvolvimento servindo as telas.** As baterias dos três cards de
  frontend usam o fixture de API (`docs/qa/e2-8-fixture-api.mjs`), porque AGENTS.md proíbe backend e
  frontend no mesmo card. Ninguém mediu, junta, a tela contra a API de verdade e o seed — é o E2.11.
- **Navegador de verdade:** percurso de teclado e foco, leitor de tela, contraste medido, as duas
  larguras com olho no pixel (as baterias medem a **estrutura** do HTML entregue e o `check:layout`
  estático). Chromium é do card de QA, rodando sozinho — é o E2.11.
- **`make e2e` (Playwright):** não é gate deste card e não foi rodado.
- **Verificação independente:** este laudo é o **aceite do integrador**, não a verificação de quem
  não implementou (AGENTS.md regra 5). O veredito soberano é o E2.11.
- **P1/P2 (documentação) e Etapa 3:** fora do escopo declarado deste card.

## Rastreabilidade

`t_3038e169` · pais `t_4511bd3d` (E2.8), `t_aa19ac87` (E2.9), `t_ea75a1e9` (E2.10) · cards da etapa
`t_df6b24ea` (E2.4), `t_428f779c` (E2.5), `t_270e113f` (E2.7) · filho `t_b570b520` (E2.11) ·
AGENTS.md §17.5 (definição de pronto), §21.3 (evidência versionada), regra 5 · DRS §17.5.
