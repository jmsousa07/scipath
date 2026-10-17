# Aceite da Etapa 3 — identidade (E3.1 a E3.7)

- **Card:** t_49b14011 · **perfil:** gerente · **data (UTC):** 2026-10-17
- **Repositório:** `$HOME/scipath` · **branch:** `main`
- **pre-merge HEAD:** `114f88e` (evidência da integração do E3.2) · **pos-merge HEAD:** `7864ab3`
- **Log bruto do pós-merge:** `docs/qa/integracao-etapa-3-2026-10-17.log`
- **Rastreabilidade:** E3.1–E3.7; AGENTS.md §17.5 e §21.3; DRS §17.5.

## Veredito

Etapa 3 **integrada** no `main`: os cinco merges restantes (E3.3 a E3.7) foram feitos com
`--no-ff` **sem nenhum conflito** e **sem nenhum commit autoral** — só os cinco merge commits.
Contrato v0.4.0 e a migração `V4__sessao.sql` (integrados por cards anteriores) estão no `main`.
`make test` (124 testes, 0 falhas), `make lint` e `make contract:check` verdes no `main` pós-merge,
com a saída colada no log. O `main` está íntegro para o E3.8 (QA da Etapa 3).

## Uma linha por card

| Card | Branch | Commits | Arquivos (diff contra o merge-base) | Conferido por mim neste card | Veredito | Ressalvas |
|---|---|---|---|---|---|---|
| E3.1 | `feat/e3-1-contrato-identidade` | 2 (`4584e1b` código, `d4658bb` evidência) | 3 contra `5753c24^1` | `contracts/openapi.yaml` em `version: 0.4.0` no `main`; `make contract:check` exit 0 | aceito (integrado antes, por t_07623a59) | — |
| E3.2 | `feat/e3-2-conta-cadastro` | 4 (`663963b`, `7beb751`, `c39f186`, `f4e2ba6`) | 20 contra `081908f^1` | camada de conta/perfil presente e exercitada pela suíte do `main` (`cadastro de conta`, `perfil público do autor`); `V1`..`V4` na pasta de migração | aceito (integrado antes, por t_5f44ea9c) | — |
| E3.3 | `feat/e3-3-login-sessao` | 2 (`af4ea57`, `375641b`) | 30 contra `114f88e` | merge `--no-ff` `c58211e` sem conflito; suíte do `main` roda login/logout/sessão/CSRF/expiração; `V4__sessao.sql` no `main` | aceito | evidência do worker não repetida aqui; interação de navegador fica no E3.8 |
| E3.4 | `feat/e3-4-tokens-verificacao` | 2 próprios (`d945c23`, `81de258`) + merge do pré-requisito `0cbc4f9` | 36 próprios (60 com o E3.3 herdado) contra `0cbc4f9` | merge `--no-ff` `44f5bfa` sem conflito; suíte do `main` cobre verificação, recuperação, cooldown 429 e falha de envio; alvo `email:outbox` no `Makefile` | aceito | `make lint` do worker saiu 2 por worktree sem `node_modules`; resolvido no `main` (ver abaixo) |
| E3.5 | `feat/e3-5-editar-perfil` | 2 próprios (`0aa2a6d`, `f39abc9`) + merge do pré-requisito `3a3f813` | 11 próprios (40 com o E3.3 herdado) contra `3a3f813` | merge `--no-ff` `b0b10c7` sem conflito; suíte do `main` cobre `PUT /api/perfil/atual` (401/403/400/409/422, privacidade, reflexo no perfil público) | aceito | mesma ressalva de lint; sem migração nova |
| E3.6 | `feat/e3-6-cadastro-login` | 2 (`d89278d`, `803db4b`) | 27 contra `114f88e` | merge `--no-ff` `9cae36a` sem conflito; rotas `/entrar` e `/cadastro` no build do `main`; `next build` exit 0 | aceito | critério 5 (teclado/foco no navegador) declarado pendente para o E3.8, como o próprio card registrou |
| E3.7 | `feat/e3-7-editar-perfil-web` | 2 próprios (`69f5480`, `ce0fd12`) + merge do pré-requisito `12fbfb4` | 17 próprios (38 com o E3.6 herdado) contra `12fbfb4` | merge `--no-ff` `7864ab3` sem conflito; rotas `/painel` e `/painel/perfil` no build do `main`; `next build` exit 0 | aceito | mesma ressalva de teclado/navegador; idem E3.6 |

## Critérios de aceite do card

1. `git rev-list --count main..<branch>` = 0 nas cinco branches (e `git merge-base --is-ancestor`
   OK nas cinco). Saída na seção 1 do log.
2. `make test` exit 0 — 124 testes, 0 falhas, `BUILD SUCCESS` (seção 2); `make lint` exit 0
   (seção 6); `make contract:check` exit 0 (seção 7). Todas as saídas cruas no log.
3. Nenhum commit autoral: `git log 114f88e..HEAD` traz os cinco `merge(...)` e os commits que já
   pertenciam às branches; nenhuma edição manual, nenhum arquivo resolvido à mão (não houve conflito).
4. Seção "Não verificado" abaixo.

## O ponto do `make lint` (achado de ambiente, não de código)

No primeiro `make lint` do `main` o alvo saiu **2**: 14 erros `TS2322` de rota não atribuível
(`/entrar`, `/cadastro`, `/painel`, `/painel/perfil`) em `frontend/src/app/**`. Causa medida: o
`frontend/.next/types/routes.d.ts` sobreviveu do build da Etapa 2 (arquivo de 2026-10-17 05:51, sem
as rotas novas) e o `tsconfig.json` inclui `.next/types/**/*.ts`. Não é código: com o `frontend/.next`
ausente, `npm run typecheck` no mesmo tree sai 0 (seção 4 do log); depois de `next build` regenerar
os tipos (seção 5, exit 0, com `/entrar`, `/cadastro`, `/painel` e `/painel/perfil` no mapa de rotas),
`make lint` sai 0 (seção 6). Conclusão: o defeito é artefato gerado e não versionado — um `make lint`
num clone limpo não o encontra, e num checkout velho ele se resolve com um build. Fica registrado
porque, sem essa leitura, a reprovação seria confundida com código quebrado.

## Não verificado (declarado, não presumido)

- **Interação de navegador** em nada desta etapa: teclado, foco visível, erro associado ao campo,
  clique/Enter repetido. Os cards E3.6 e E3.7 já declaram isso como pendente; é trabalho do E3.8 (QA
  da etapa, card filho `t_76729dda`), com o navegador rodando sozinho.
- **Backend real contra o frontend**: as evidências de E3.6/E3.7 usam um duplo das respostas do
  contrato (`docs/qa/e3-6-stub-api.mjs`, `docs/qa/e3-7-stub-api.mjs`). Nenhuma chamada real
  requisição-Next→Spring foi feita neste card.
- **Fluxo de e-mail de ponta a ponta** (cadastro → receber link → confirmar): a suíte cobre o serviço e
  o outbox; nenhum envio real de e-mail foi observado.
- **`npm run verify` e o harness de QA** (`infra/qa-harness.sh`) não foram rodados no `main` neste
  card — fora do critério 2 do card; é o que o E3.8 mede.
- **E3.1 e E3.2** não foram reauditados neste card: foram integrados por t_07623a59 e t_5f44ea9c, e
  aqui só confirmei os artefatos que eles entregaram (contrato v0.4.0, migrações, suíte que os
  exercita no `main`).
- Nada foi rodado contra a **API no ar** (`make api:dev`/`web:dev`): prova de funcionamento desta
  etapa é a suíte e o build, como AGENTS.md exige em card de implementação/integração.

## Riscos e hotspots para o E3.8

- Os arquivos de sessão do frontend são compartilhados entre E3.6 e E3.7
  (`frontend/src/lib/sessao/{erros,validacao,navegador}.ts`, `frontend/src/lib/api/tipos.ts`); o
  E3.7 já continha o E3.6, então o merge não teve conflito, mas qualquer mudança futura nesses
  arquivos colide com os dois cards.
- `Makefile` foi alterado em `main` pelo E3.4 (alvo `email:outbox`): a `lint`/`e2e` continuam nas
  linhas originais, mas quem for editar o `Makefile` deve olhar o alvo novo.
