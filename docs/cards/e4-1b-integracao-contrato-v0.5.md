# E4.1b — Integração: contrato v0.5 (E4.1) no `main`

- **Perfil:** gerente · **Depende de:** E4.1 · **Status:** pronto para execução

## Contexto

O contrato é a fronteira: os três consumidores da Etapa 4 (E4.2, E4.3, E4.6) ramificam do `main`,
e o `main` só tem o contrato que foi **mergeado**. Sem esta integração, cada consumidor ramifica de
uma fronteira velha e o defeito só aparece no fim da etapa — foi o que aconteceu na Etapa 2 e o que
gerou o card de integração de contrato na Etapa 3.

## Escopo

1. Merge `--no-ff` da branch do E4.1 no `main`, com mensagem `merge(feat/e4-1-contrato-publicacao): ...`.
2. Conferir no `main`: `version: 0.5.0`, os dois caminhos novos (`POST /materiais`,
   `GET /materiais/{slug}/arquivo`) e nenhum caminho antigo removido.
3. `make contract:check` exit 0 no `main` pós-merge, saída colada.
4. Confirmar que `git rev-list --count main..feat/e4-1-contrato-publicacao` é 0.
5. Registrar em `docs/qa/integracao-contrato-v0.5-<data>.log` o antes/depois do `version` e a lista
   de caminhos do contrato.

## Fora de escopo

Implementar qualquer parte da publicação. Mudar o contrato (se o contrato tiver defeito, o card
volta para o E4.1).

## Critérios de aceite

1. `version: 0.5.0` no `main` (saída do `grep`).
2. `make contract:check` exit 0 no `main` pós-merge.
3. Zero commits fora do `main` na branch do E4.1.
4. Log versionado em `docs/qa/`.

## Rastreabilidade

ADR-0001 (contrato primeiro); AGENTS.md regra 1; defeito de integração observado na Etapa 2.
