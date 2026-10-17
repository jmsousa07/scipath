# ADR-0009 — Camada ponta a ponta do MVP: o harness de QA do repositório, sem Playwright

- Data: 2026-10-17
- Status: **aceita** — decisão do dono (João Mateus) em 2026-10-17, registrada pela orquestração
- Decisores: João Mateus (dono) · orquestração (PO)
- Resolve: DRS §17.3 (testes de interface / ponta a ponta) e a lacuna do alvo `make e2e`
- Relaciona: DRS §17.5, §15.3, §14.2, §21.4; ADR-0002 (baseline que invalidou os cards F0.x);
  ADR-0007 (lançamento); AGENTS.md (Definição de pronto, higiene de recursos); E1.8, E2.11

## Contexto

O §17.3 pede testes de interface / ponta a ponta. O `Makefile` tem o alvo `e2e` (Playwright), mas
`e2e/` **nunca existiu**: o alvo apenas declara `frontend/e2e/ ainda não existe (card f0.4)`, e o
card F0.4 foi invalidado quando o DRS v1.0 virou a fonte #1 (ADR-0002). Nenhum card de e2e existe
em nenhuma etapa — medido em 2026-10-17: `ls e2e/` → inexistente; nenhum card `f0-4` no quadro.

Na prática, a cobertura de interface do MVP vem sendo feita pelo **harness de QA** construído no
E1.8 e usado em todos os cards de verificação: `infra/qa-harness.sh` mais baterias versionadas em
`docs/qa/`, que sobem a API real com o seed de desenvolvimento, usam Chromium **no card de QA**
(rodando sozinho, conforme a higiene de recursos), e medem estados, negativos, SEO, acessibilidade
e responsividade. O E2.11 mediu, no `main`, o que ninguém havia medido junto (API + seed + navegador
real) por esse caminho.

O §14.2 pede não adicionar biblioteca sem necessidade demonstrada. A máquina tem 954 MB e um worker
por vez; uma suíte Playwright traz navegador próprio, dependências e mais um passo de instalação.

## Decisão

**O harness de QA do repositório é a camada ponta a ponta do MVP.** Não se adiciona Playwright, e o
alvo `make e2e` permanece **declarando** que `e2e/` não existe — nunca passa em silêncio.

Consequências de processo:

1. O gate do §17.5 **não** inclui `make e2e`; inclui `make test`, `make lint` e `make contract:check`
   no `main`, mais o harness do card de QA da etapa sobre API real + seed.
2. Se uma etapa tiver jornada que o harness não cubra de forma honesta (formulário com arquivo,
   upload e retomada — candidatos da Etapa 4/5), isso vira **decisão nova**, não improviso: este
   ADR é reaberto com a alternativa Playwright ou equivalente.

## Alternativas descartadas

- **Playwright agora.** Dependência e passo de instalação novos, navegador próprio, RAM/CPU que a
  máquina não tem (§14.2, higiene de recursos), sem necessidade ainda demonstrada — o harness cobre
  o que as etapas 1–3 exigem.
- **Deixar `make e2e` como está, sem decisão escrita.** É o pior caso: um alvo que reprova, um gate
  que mente por omissão, e nenhum lugar dizendo quem cobre a interface.
- **Abrir card de e2e Playwright "para depois".** Card sem gatilho e sem dono vira dívida; a decisão
  fica registrada aqui e reabre por necessidade medida.

## Consequências

- **Positivas:** nenhuma dependência nova; evidência de interface continua versionada em `docs/qa/`,
  no mesmo lugar da evidência de backend; nenhum passo de instalação além do já existente.
- **Negativas / custos aceitos:** o harness é artesanal — mantê-lo tem custo por etapa, e ele não é
  uma suíte de e2e reutilizável por terceiros nem executável em CI de terceiros. Aceito porque o
  produto é local nesta fase e o caminho de lançamento (§ADR-0007) ainda não existe.

## Rastreabilidade

DRS §14.2, §15.3, §17.3, §17.5, §21.4; ADR-0002, ADR-0007; E1.8 (`infra/qa-harness.sh`), E2.9,
E2.10, E2.11; card de decisão registrado a partir do aceite da Etapa 2.
