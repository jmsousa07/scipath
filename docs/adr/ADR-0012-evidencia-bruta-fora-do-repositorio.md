# ADR-0012 — Evidência bruta fora do repositório; `docs/qa/` guarda só o aceite

- **Status:** aceita
- **Data:** 2026-11-12
- **Substitui parcialmente:** AGENTS.md, regra 4 e "Higiene de contexto"; `Definição de pronto` (DRS §17.5)
- **Relacionada:** ADR-0009 (a camada ponta a ponta é o harness de QA, não o Playwright)

## Contexto

O repositório passou a ser também peça de portfólio, e a árvore pública precisa ser
legível para quem chega de fora. Medido no HEAD anterior a esta decisão:

| Caminho | Rastreado | Arquivos |
|---|---|---|
| `docs/qa/` | 11,8 MB | 267 |
| `docs/cards/` | 0,2 MB | 63 |
| `docs/adr/` | 104 KB | 12 |
| todo o repositório | 16,1 MB | — |

Dentro de `docs/qa/`, o que ocupa espaço não é o aceite: são 142 logs de terminal
(7,83 MB), 72 scripts de evidência (1,12 MB), 17 capturas de tela (2,44 MB), 17 sondas
`.mjs` (0,23 MB) e 11 `.py` (0,05 MB). Os 7 documentos `.md` que resumem o aceite somam
70 KB.

O custo disso não é de tamanho — 16 MB é irrelevante para o Git — é de superfície e de
risco: 330 arquivos de processo competem com o produto na árvore pública, e o material
bruto é justamente o que mais carrega dado incidental (caminhos de máquina, tokens de
teste, cookies de sessão), exigindo saneamento a cada publicação.

A regra 4 do AGENTS.md ("Evidência ou não aconteceu") e o §17.5 do DRS existem
para impedir que alguém afirme qualidade sem prova. Esta decisão não abre mão da prova:
ela muda **onde** a prova mora e **o que** dela é publicado.

## Decisão

1. **`docs/qa/` passa a guardar só documento de aceite** — os relatórios legíveis
   (`aceite-*.md`, `p5-aceitacao-mvp.md`) e o `TEMPLATE.md`. Logs, scripts de evidência,
   sondas de navegador, capturas de tela e diffs brutos **não são versionados**.
2. **A evidência bruta vive fora do repositório**, em diretório configurável:
   `SCIPATH_EVIDENCIA_DIR`, com padrão `$HOME/.scipath/evidencia`. O `infra/qa-harness.sh`
   passa a escrever lá.
3. **O aceite continua versionado.** Quem fecha uma etapa publica o documento `.md` com o
   veredito, os comandos e os números; o log bruto fica como anexo local referenciável pelo
   nome do arquivo.
4. **Isso vale para o histórico também.** A árvore não basta: o material bruto anterior foi
   removido de todas as refs por reescrita, não apenas da ponta.

## Consequências

- `AGENTS.md` perde a exceção `docs/qa/*.log` do `.gitignore` e ganha a nova regra; o mapa
  do repositório e a definição de pronto passam a apontar para `docs/qa/` como **aceite**.
- A prova de uma etapa deixa de ser o log cru e passa a ser o documento de aceite. Quem
  quiser o log pede ao autor ou reproduz o comando registrado no aceite — o comando é
  versionado justamente para isso.
- Citações a arquivos de evidência que permanecem em comentários de código e em ADRs
  anteriores são **registro histórico**: apontam para artefatos que hoje vivem fora do
  repositório, e não links navegáveis.
- Repositório deixa de acumular megabytes de saída de terminal por etapa.
- Perda aceita e explícita: um clone do repositório não permite mais auditar a execução
  original linha a linha. O que se auditou antes de decidir está resumido abaixo.

## Registro da execução desta decisão

- Removidos de todas as refs: 142 logs, 72 scripts, 17 sondas `.mjs`, 11 `.py`,
  17 capturas, 1 diff, `docs/gerente-prompt.md`, `docs/como-rodar-local.md`,
  `docs/rodar-em-outra-maquina.md`.
- Mantidos: `docs/adr/`, `docs/00-visao.md`, `docs/01-arquitetura.md`, `docs/cards/` e os
  7 documentos de aceite de `docs/qa/`.
- Gates no commit publicado: `make test` (270 testes), `make lint`, `make contract:check`,
   `npm run verify` — verdes.
- Varredura de segredos (gitleaks 8.30.1, binário oficial com sha256 conferido) sobre todas
  as refs: 0 achados; 1 identidade de autoria (noreply); 0 caminhos de máquina.
