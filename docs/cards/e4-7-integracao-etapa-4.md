# E4.7 — Integração: merge da Etapa 4 (E4.1b a E4.6) no `main`

- **Perfil:** gerente · **Workspace:** `dir:$HOME/scipath` · **Status:** pronto para execução

## Contexto

Mesmo desenho do card de integração da Etapa 2/3: o QA da etapa precisa medir um `main` que já
contém **todas** as branches da etapa, e o laudo de aceite é artefato do repositório (§21.3).

## Escopo

1. Merge `--no-ff`, sem commit autoral, das branches pendentes da etapa: E4.2 (upload/validação),
   E4.3 (storage/acesso), E4.4 (publicar), E4.5 (coletor), E4.6 (formulário). O contrato (E4.1) já
   está no `main` pelo E4.1b.
2. Conflitos, se houver: resolver e **declarar** o lado escolhido com o motivo (regra do AGENTS.md);
   nenhuma resolução silenciosa.
3. Rodar no `main` pós-merge, com a saída crua: `make test`, `make lint`, `make contract:check`.
4. Conferir migração: nenhuma nova nesta etapa além das que os cards declaram; nenhuma migração já
   aplicada editada (`git diff` no diretório de migração).
5. Confirmar `git rev-list --count main..<branch>` = 0 nas cinco branches.
6. Produzir `docs/qa/aceite-etapa-4.md` no formato das etapas anteriores: uma linha por card com
   branch, commits, arquivos e veredito — e a seção "O QUE ESTE LOG NÃO PROVA".

## Fora de escopo

Implementar, corrigir ou verificar o produto (o QA é o E4.8). Mudar contrato.

## Critérios de aceite

1. Os cinco merges no `main` e zero commits fora dele.
2. `make test` (saída com contagem de testes e falhas), `make lint` e `make contract:check` verdes
   no `main`, saídas cruas versionadas em `docs/qa/integracao-etapa-4-<data>.log`.
3. `docs/qa/aceite-etapa-4.md` commitado, com a seção de não provado.
4. Nenhuma migração nova não declarada; nenhuma migração existente alterada.

## Rastreabilidade

AGENTS.md (Definição de pronto, §17.5); DRS §17.5, §21.3; E2/Etapa 3 (precedente de integração).
