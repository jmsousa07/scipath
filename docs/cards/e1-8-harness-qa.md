# E1.8 — Harness de QA e verificação independente da base

- **Perfil:** qa · **Depende de:** E1.5, E1.7 · **Status:** pronto para execução

## Objetivo

Criar o harness de evidência que o projeto não tem — foi a falta dele que deixou a verificação do
F0.1 nunca acontecer — e usá-lo para verificar a Etapa 1 de ponta a ponta.

> Este card **não** escreve código de produto. O QA não corrige: ele mede e reporta (§5).

## Escopo

1. `infra/qa-harness.sh`: roda a bateria (build, testes, lint, contrato, migrações) e grava tudo em
   `docs/qa/<card>-<data>.log` no formato do DRS §21.4 — comando, saída, veredito por critério.
2. Template de evidência em `docs/qa/TEMPLATE.md`: o que a evidência precisa conter para valer
   (comando exato, saída completa, ambiente, e o que **não** foi verificado).
3. Verificação da base, com os negativos:
   - `make test` em **shell limpo** (`env -i`) — reproduzibilidade (E1.5);
   - migrações aplicadas nos dois bancos e procedimento de reversão exercitado (E1.2);
   - `make contract:check` sem warnings (E1.1);
   - payload inválido → 400 `ProblemDetail` com `errors[]`; erro interno → 500 sem vazamento (E1.4);
   - `SELECT` da taxonomia: 13/6/7 (E1.3);
   - `grep` de segredo no repositório e de hex solto no frontend (E1.5, E1.6);
   - 13 estados dos componentes alcançáveis (E1.7);
   - responsividade 375/1440 sem rolagem horizontal e `prefers-reduced-motion` respeitado.
4. Relatório final com **veredito por critério**: atende / não atende / não verificado — e o que
   ficou sem verificação (nunca "tudo ok" sem lista).

## Fora de escopo

Corrigir o que falhar (vira card) e testar funcionalidades que ainda não existem.

## Critérios de aceite

1. `infra/qa-harness.sh` roda do zero, sem depender de estado deixado por outro agente.
2. Existe `docs/qa/e1-base-<data>.log` com a bateria completa **executada**.
3. Cada critério dos cards E1.1–E1.7 tem veredito explícito, com a saída que o sustenta.
4. Todo "não atende" está descrito de forma reproduzível (comando + saída + esperado).

## Evidência esperada

O próprio `docs/qa/e1-base-<data>.log` e o script `infra/qa-harness.sh` versionados.

## Rastreabilidade

RNF027, CA009, CA010, DRS §17, §17.5, §21.3, §21.4.
