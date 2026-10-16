# Aceite do orquestrador — Etapa 1 (Base do produto)

> **Contexto:** em 2026-10-16 o dono (João Mateus) delegou ao orquestrador as revisões —
> *"não vai dar tempo, continue por aí mesmo e faça as revisões por mim"* — por ter saído.
> Este arquivo existe porque revisão que só está no chat é revisão perdida (AGENTS.md).

## Regra mantida

O aceite do orquestrador **não substitui a verificação independente** do card `E1.8`
(RNF027, §5: quem implementa não verifica; aqui, quem integra também não é quem verifica).
O que o orquestrador fez foi **revisão documental por amostragem** — inspeção dos artefatos no
disco (migrações, contrato, código, evidências anexadas) — e a integração em `main`. O que não
foi executado está listado ao final.

## Decisões

| Card | Branch / commit | Base do aceite (o que foi conferido) | Veredito |
|---|---|---|---|
| E1.1 | `feat/e1-1-contrato` `cec41a1` | contrato v0.2 com `ProblemDetail`+`errors[]`, os 3 endpoints de taxonomia, envelope de paginação, endpoints de material removidos; +488/−92 | aceito |
| E1.2 | `feat/e1-2-migracao-v1` `813135d` | constraints reais (e-mail normalizado + `UNIQUE`, `slug` único e formato, `CHECK` de estados e de auto-pai), índices de listagem, comentários citando RN009/ADR-0003/RNF009 | aceito |
| E1.3 | `b47ab26` + `fbed351` | seed V2 com 13 categorias / 6 tipos / 7 licenças e recusa de categoria inexistente (RN005) | aceito |
| E1.4 | `feat/e1-4-erros-validacao` `e5cdbbf` | `ApiExceptionHandler` + `ErroDeCampo`, `FlywayConfig` por perfil, testes unitário e de integração, evidência anexada; não tocou o contrato | aceito |
| E1.6 | `feat/e1-6-frontend-base` `b1f7013` | tokens do DS2, fontes locais, layout global | aceito **condicional** — ver ressalva 1 |
| E1.7 | `feat/e1-7-componentes` `1488d8d` (empilhado no E1.6) | componentes base e os 13 estados do §15.3 | aceito condicional — ver ressalva 1 |
| E1.9 | `wt/t_3e3679f9` `ccc3c43` | `TaxonomiaController/Service/Repositorio` + records de resposta + teste de integração; fecha a fronteira que o E1.1 publicou; não tocou o contrato | aceito |
| E1.2a | `feat/e1-2a-db-migrate` `590cf3b` + `7f2f237` | `db:migrate` carrega o `.env`, usa `locations=filesystem:` e não mascara falha (exit ≠ 0) | aceito **com ressalva 2** |
| E1.11 | `97b25e2` + `5ad240b` | `!docs/qa/*.log` no `.gitignore` + convenção no `AGENTS.md` | aceito (executado pelo próprio orquestrador, com autorização do dono para o `AGENTS.md`) |

## Ressalvas registradas (não são "tudo ok")

1. ~~"Lint verde" do frontend não foi confirmado.~~ **Resolvido por leitura em 2026-10-16.** O
   `npm run lint` roda `eslint .` com flat config de `eslint-config-next` — e o comentário no
   `eslint.config.mjs` explica que `next lint` foi removido no Next 16, então o lint é **real**,
   não vacuidade. Melhor que isso: o projeto traz `scripts/check-*.mjs` com modo `--check`, e o
   `check:tokens` **reextrai os tokens do `design_system_2.html` e compara** com
   `src/styles/tokens.css` — provando que os valores não foram inventados. Conferido por
   amostragem: `--sc-blue:#3186ff` e `.sc-h1 clamp(2.75rem,5.4vw,4rem)` / lh 1.04 / ls −.035em
   batem com o que extraí do DS2. Há um alvo `npm run verify` que encadeia tokens, estilo,
   layout, typecheck, lint, build e estados — o card `E1.8` foi instruído a usá-lo. O card
   `E1.10` continua válido para travar as versões das dependências.
2. **O E1.2a expõe a senha do banco no `argv`** (`-Dflyway.password=<valor-omitido> visível em `ps`).
   Regressão de segurança introduzida pela própria correção; já registrada no card `E1.2b`,
   que precisa fechar isso antes de qualquer uso fora desta máquina (RNF012).

## O que o aceite NÃO cobriu (escopo do E1.8)

Execução da suíte de testes em ambiente limpo · build do frontend · contrato verificado contra a
implementação · acessibilidade e responsividade reais · comportamento dos estados com dados
reais · desempenho. Nada disso foi declarado pronto.

## Pendências que continuam exigindo o dono (não são revisão)

As listas do **ADR-0006** (categorias, tipos e licenças) e os itens operacionais do **ADR-0007**
(backup com teste de restauração, hospedagem, canal de denúncia). Seguem como **proposta**:
o orquestrador não as declara aprovadas (§21.5).
