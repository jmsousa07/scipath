# Aceite da Etapa 4 — publicação de material (E4.1 a E4.6)

- **Card:** t_6f69d6e4 · **perfil:** gerente · **data (UTC):** 2026-10-19
- **Repositório:** `$HOME/scipath` · **branch:** `main`
- **pre-merge HEAD:** `fc9faca` (evidência da integração do contrato v0.5, E4.1b) · **pós-merge HEAD:** `75f6adc`
- **Log bruto do pós-merge:** `docs/qa/integracao-etapa-4-2026-10-19.log` (script: `docs/qa/integracao-etapa-4-evidencia.sh`)
- **Rastreabilidade:** E4.1–E4.6 e E4.1b; AGENTS.md §17.5 e §21.3; DRS §17.5.

## Veredito

Etapa 4 **integrada** no `main`: os cinco merges `--no-ff` (`4e7b56f`, `f78b626`, `8aedf9c`,
`7b15d37`, `75f6adc`) sobre `fc9faca` foram feitos **sem nenhum conflito** e **sem nenhum commit
autoral** — só os merge commits, cada um de dois pais. O contrato v0.5 (E4.1) já estava no `main`
pelo E4.1b. No `main` pós-merge, com a saída crua no log: `make test` exit 0 (226 testes, 0
falhas), `make lint` exit 0 e `make contract:check` exit 0. Nenhuma migração nova e nenhuma
migração existente alterada. O `main` está íntegro para o E4.8 (QA da Etapa 4).

## Uma linha por card

| Card | Branch | Commits | Arquivos próprios (diff contra o pré-requisito) | Merge | Conferido por mim neste card | Veredito | Ressalvas |
|---|---|---|---|---|---|---|---|
| E4.1 | `feat/e4-1-contrato-publicacao` | 2 (`f8b9459`, `f3f046b`) | 4 contra `77adc9e` | integrado antes, por t_9f67b214 (`76b2326`) | `contracts/openapi.yaml` em `version: 0.5.0` no `main`; `make contract:check` exit 0 (seção 5 do log) | aceito (integrado antes) | — |
| E4.2 | `feat/e4-2-upload-validacao` | 2 (`c009847` código, `175561e` evidência) | 19 contra `fc9faca` | `4e7b56f` (pais `fc9faca` + `175561e`), sem conflito | suíte do `main` roda as classes do card (seção 3): recepção e validação do arquivo enviado (13), recepção pela borda HTTP (9), nome original do arquivo recebido (6); 0 arquivos de migração tocados (seção 6) | aceito | infra de arquivos entra sem migração nova: usa o modelo de `V1`; o log do card declara `lint exit=2` no worktree (precondição do P3) |
| E4.3 | `feat/e4-3-storage-acesso` | 2 próprios (`7e6ab51`, `23335f2`) + merge do pré-requisito `8c7402c` (E4.2) | 25 próprios (40 com o E4.2 herdado) | `f78b626` (pais `4e7b56f` + `23335f2`), sem conflito | suíte do `main` roda as classes do card (seção 3): download do arquivo de material público (8), armazenamento local do arquivo (8), configuração do armazenamento definitivo (4), guarda da remoção de arquivo (3) | aceito | divergência de contrato × código já registrada (campo `arquivo` do `MaterialDetalhe`) — dono é o E4.9 (`t_4c5ee9d9`), não corrigida de passagem |
| E4.4 | `feat/e4-4-publicar-material` | 2 próprios (`6c19a3d`, `04a76cb`) + merge do pré-requisito `e9ff7a4` (E4.3) | 19 próprios (57 com a cadeia herdada) | `8aedf9c` (pais `f78b626` + `04a76cb`), sem conflito | suíte do `main` publica pela borda HTTP com 201/400/401/403/413/415/422 (14 casos) e prova rollback com gatilho real no banco (2 casos) (seção 3) | aceito | `make lint` saiu 2 no worktree (P3); o campo `arquivo` no detalhe segue pendente no E4.9 |
| E4.5 | `feat/e4-5-coletor-orfaos` | 2 próprios (`293bac9`, `b25759b`) + merge do pré-requisito `865ab2e` (E4.4) | 16 próprios (71 com a cadeia herdada) | `7b15d37` (pais `8aedf9c` + `b25759b`), sem conflito | suíte do `main` roda os 8 casos do coletor e os 4 da configuração, incluindo a falha forçada por gatilho e a retomada (seção 3); `Makefile` e `docs/01-arquitetura.md` do card estão no `main` | aceito | `make lint` saiu 2 no worktree (P3); o alvo `make coleta:orfaos` não foi executado pelo card (proibição de `spring-boot:run` como evidência) |
| E4.6 | `feat/e4-6-publicar-web` | 2 (`8c4abb1`, `60123fe`) | 17 contra `fc9faca` | `75f6adc` (pais `7b15d37` + `60123fe`), sem conflito | `make lint` (eslint + `tsc --noEmit`) exit 0 no `main` com o frontend inteiro, incluindo `/painel/publicar`, `FormularioPublicacao` e os scripts novos (seção 4c) | aceito | `make lint` do worktree foi resolvido por ambiente (bind mount de `node_modules`); ver seção própria; interação de navegador fica no E4.8 |
| E4.1b | `feat/e4-1-contrato-publicacao` (evidência) | 1 (`fc9faca`) | 2 | integrado antes, por t_9f67b214 | HEAD pré-merge desta etapa; contrato v0.5.0 conferido (seção 5) | aceito (integrado antes) | — |

## Critérios de aceite do card

1. **Os cinco merges no `main` e zero commits fora dele.** `git rev-list --count --first-parent
   --merges fc9faca..HEAD` = **5** (seção 1); `git rev-list --count main..<branch>` = **0** nas
   cinco e `git merge-base --is-ancestor` OK nas cinco (seção 2). `git log --merges` conta **8**
   merges alcançáveis: os 5 da etapa mais os **3 merges de pré-requisito** que os próprios cards
   declararam dentro das branches (E4.3 contém E4.2, E4.4 contém E4.3, E4.5 contém E4.4) — estão
   declarados na seção 1 e não são obra deste card.
2. **`make test`, `make lint` e `make contract:check` verdes no `main`, saídas cruas.** Seção 3:
   exit 0, **226 testes, 0 falhas, 0 erros** (uma linha por classe + total + `BUILD SUCCESS`,
   filtro declarado no log); seção 4c: exit 0; seção 5: exit 0. Log versionado em
   `docs/qa/integracao-etapa-4-2026-10-19.log`.
3. **`docs/qa/aceite-etapa-4.md` commitado, com a seção de não provado.** Este arquivo, com a
   seção "O QUE ESTE LOG NÃO PROVA" abaixo.
4. **Nenhuma migração nova não declarada; nenhuma existente alterada.** Seção 6: a pasta de
   migração segue `V1__modelo_mvp.sql`, `V2__seed_taxonomia.sql`, `V3__busca_texto_material.sql`,
   `V4__sessao.sql`; `git diff --name-status fc9faca..HEAD` e `git log --name-status` restritos à
   pasta saem **vazios**. Nenhum dos cinco cards declarou migração; as mudanças de esquema que a
   Etapa 4 precisava (`file_asset`) já existiam em `V1`.

## Conflitos

**Nenhum.** Os cinco merges foram `ort` sem conflito, e nenhum arquivo foi resolvido à mão. Os
hotspots de arquivo que existiam entre cards (e que explicam por que a ordem de dependência
importa) foram absorvidos pela cadeia de pré-requisitos das próprias branches:

- `backend/src/main/resources/application.yml` e `application-test.yml` — tocados por E4.2, E4.3,
  E4.4 e E4.5 (limites de upload, armazenamento, coleta);
- `Makefile` — tocado por E4.5 (alvo `coleta:orfaos`);
- `frontend/src/lib/sessao/*` e `frontend/src/lib/api/*` — tocados por E4.6 (que herdou o estado
  do E3.6/E3.7 pela base `fc9faca`).

## O `make lint` no checkout principal (achado de ambiente, não de código)

O `frontend/node_modules` do checkout principal está **vazio (0 pacotes)** — o estado que o card
P3 (`t_5aed6b33`, branch `proc/p3-lint-sem-node-modules`, **não integrada**) existe para declarar.
Com ele vazio, `make lint` sai **2** com `ERR_MODULE_NOT_FOUND: Cannot find package
'eslint-config-next'` (seção 4a, saída crua). A leitura de que isso é ambiente e não código está na
própria mensagem: o ESLint não chega a ler um arquivo do produto.

Para que o critério 2 (lint verde no `main`) pudesse ser medido de verdade, o log declara uma
**provisão de ambiente**: `sudo mount --bind` do tree de dependências do worktree `t_8563e92b`
(seção 4b), precedido do `diff` dos `package.json` que mostra que a única diferença são **scripts**
(`check:publicar`, `check:publicar-erros` e o `verify`) — `dependencies` e `devDependencies` são
idênticas, então o tree satisfaz o `package.json` pós-merge. Com ele no lugar, `make lint` sai **0**
(seção 4c: eslint + `tsc --noEmit`). Nada disso é versionado; o mount continua no lugar para o E4.8
e se desfaz com `sudo umount frontend/node_modules`.

Nota de processo: a primeira execução do script (descartada) contava os merges de pré-requisito
como se fossem da etapa; o defeito estava no **script de evidência**, não no repositório, e a
contagem foi corrigida para `--first-parent` antes do log versionado. Os cinco merge commits e a
árvore do `main` não foram tocados.

## O QUE ESTE LOG NÃO PROVA

- **O produto.** Nada aqui é verificação independente: `make test`, `make lint` e
  `make contract:check` verdes dizem que a árvore integrada compila, passa a suíte e respeita a
  fronteira do contrato. A verificação da etapa é o **E4.8** (card filho `t_25ba3ec9`) — quem
  implementa não verifica (AGENTS.md regra 5), e este card é integração, não QA.
- **O conteúdo de cada card não foi reauditado.** Confirmei que os commits e arquivos de cada
  branch estão no `main`, que as migrações foram respeitadas e que a suíte do `main` exercita as
  classes de cada card (seção 3). Não reexecutei as baterias de cada card
  (`docs/qa/e4-2..e4-6-*`) contra o `main`, e não repeti os veredictos que eles próprios assinam.
- **Nada rodou contra a API no ar.** `make api:dev`/`web:dev` são proibidos como evidência em card
  desta família (AGENTS.md, higiene de recursos): a prova é a suíte e o build.
- **Fluxo ponta a ponta navegador → Next → Spring → arquivo.** Nenhuma requisição real
  Next→Spring foi feita aqui; as evidências do E4.6 usam duplo das respostas do contrato
  (`docs/qa/e4-6-stub-api.mjs`). Interação de teclado/foco/erro por campo também não foi medida.
- **`npm run verify` e o harness de QA** não foram rodados neste card — são do E4.8.
- **Operação real do coletor agendado.** A suíte exercita a rotina e a configuração do cron
  (inclusive `CronExpression.parse` na subida), mas nenhuma passada real às 03:30 foi observada, e o
  alvo manual `make coleta:orfaos` não foi executado neste card.
- **Reconciliação de órfãos em produção.** O comportamento sob processo morto no meio da publicação
  é provado por teste com gatilho real; não houve falha real em ambiente vivo.
- **`make lint` num clone limpo.** O lint verde da seção 4c pressupõe o tree de dependências
  provisionado; num checkout sem `node_modules` o alvo continua saindo 2 **sem dizer por quê** —
  é exatamente o que o P3 corrige. Este log não prova que o P3 está resolvido (não está integrado).

## Riscos e hotspots para o E4.8

- **Divergência contrato × código com dono:** o campo obrigatório `arquivo` de `MaterialDetalhe`
  (contrato v0.5) não é publicado pelo `GET /api/materiais/{slug}` — card E4.9 (`t_4c5ee9d9`),
  aberto pelo E4.3/E4.4. A bateria do E4.8 vai encontrá-la; é achado, não regressão desta etapa.
- **Rótulo do 415:** o `title` publicado é "Tipo de conteúdo não suportado" e o exemplo do contrato
  mostra "Tipo de mídia não suportado" (o `type` é idêntico). Registrado pelo E4.2 para virar card.
- **`/painel` ainda afirma não existir tela de publicação** — pendência declarada pelo E4.6, card
  `t_c83f7952`.
- **Hotspots de colisão** para quem for editar em paralelo: `application.yml` /
  `application-test.yml` (quatro cards), `Makefile` (E4.5 + o P3, que ainda não está integrado),
  `frontend/src/lib/sessao/*` e `frontend/src/lib/api/*` (E3.6, E3.7 e E4.6), e
  `docs/qa/e4-6-stub-api.mjs` (duplo do contrato usado por baterias de frontend).
- **Ambiente:** `frontend/node_modules` no `main` é um bind mount declarado (seção 4b) até que o
  P3 seja integrado; qualquer `npm install` no `main` escreveria dentro do worktree `t_8563e92b`.
- **954 MB de RAM e 2 vCPU:** o E4.8 deve rodar sozinho, como o E4.7 rodou (nenhum processo ou porta
  deixado vivo — seção 7).
