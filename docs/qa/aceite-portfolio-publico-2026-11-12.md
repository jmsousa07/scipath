# Aceite — portfólio público, saneamento do histórico e poda da evidência (2026-11-12)

Aceite consolidado de três frentes que fecharam no mesmo dia. Substitui, em forma distillada,
os logs brutos de card que acompanhavam cada uma (a evidência bruta passou a viver fora do
repositório — [ADR-0012](../adr/ADR-0012-evidencia-bruta-fora-do-repositorio.md)).

## 1. Preparação para portfólio (card `t_c10afd4a`)

- `README.md` na raiz: proposta, estado de MVP, o que faz, o que **não** faz, arquitetura,
  setup, testes e limites — sem alegar prontidão para produção.
- `LICENSE` MIT com escopo explícito: código original e API; `docs/`, DRS e material de
  terceiros fora do escopo. Fontes Google Sans/Flex e Roboto Mono sob SIL OFL 1.1, com os
  avisos oficiais preservados e comparados com as fontes upstream.
- Documentação corrigida contra o que o código faz: limite de upload em 30 MB, rota
  `/explorar`, local real das migrações, uso de SVG em vez de fonte de ícones.

Resultado dos gates: `make test` (270 testes), `make lint`, `make contract:check` e
`npm run verify` verdes.

## 2. Correção do limite de senha (card `t_18b925a6`)

O bcrypt processa no máximo 72 **bytes**; a validação contava **caracteres**. Senha multibyte
dentro do intervalo de caracteres estourava o limite e caía no handler genérico (500).

- Nova restrição `SenhaBcryptValida` (Bean Validation) aplicada em `CadastroContaRequisicao` e
  `RecuperacaoConfirmacaoRequisicao`: `>72 bytes` responde **422** no campo certo.
- Na recuperação, a recusa **não consome o token** — o mesmo link conclui a troca depois.
- Frontend mede bytes e o formulário informa o limite; contrato atualizado.
- Verificação: TDD (500 reproduzido antes, 422 depois); suíte completa com 270 testes verdes;
  revisão independente aprovada sem bloqueadores.

## 3. Saneamento e reescrita do histórico

Antes, nas 58 refs públicas: 51 achados do gitleaks em 15 caminhos (3 regras), 407 campos de
autoria em domínio de e-mail pessoal, 333 ocorrências de caminho de usuário local, 905 do
caminho da VPS e capturas de terceiros em `assets/templates/`. Nenhuma credencial do projeto:
`.env` nunca foi rastreado.

Ferramentas: **gitleaks 8.30.1** (binário oficial, sha256 conferido) e **git-filter-repo
v2.47.0**, sobre espelho local. Reescrita aplicada a **todas as refs**: caminhos, tokens,
cookies e CSRF substituídos; autoria e comitteria remapeadas para o noreply; `assets/templates/`
removido de toda a história.

Depois, verificado em clone limpo do GitHub: 58 refs, **0 achados**, **1 identidade de
autoria**, **0 caminhos de máquina**, domínios de e-mail publicados apenas reservados/fictícios.

## 4. Poda da evidência bruta (ADR-0012)

`docs/qa/` guardava 267 arquivos e 11,8 MB rastreados — 142 logs, 72 scripts, 17 capturas,
17 sondas, 1 diff. Ficaram apenas os 7 documentos de aceite (70 KB). Também saíram
`docs/gerente-prompt.md`, `docs/como-rodar-local.md` e `docs/rodar-em-outra-maquina.md`.

A partir daqui o log bruto, o print e o diff vivem em `SCIPATH_EVIDENCIA_DIR`
(padrão `$HOME/.scipath/evidencia`), o `infra/qa-harness.sh` escreve lá, e o repositório
publica só o aceite. A remoção foi aplicada também ao histórico, por segunda reescrita.

## Limites deste aceite

- Sem navegação visual ou teste manual da aplicação nesta rodada; a revisão independente não
  substitui um aceite de QA dedicado por etapa.
- Citações a arquivos de evidência em comentários de código e em ADRs anteriores permanecem
  como registro histórico: apontam para artefatos que hoje ficam fora do repositório.
- Objetos antigos podem seguir acessíveis no GitHub por SHA direto até a coleta de lixo do
  serviço; nenhuma ref os alcança.
- Os gates foram executados na máquina de desenvolvimento do autor (Linux, 8 vCPU), não em CI.
