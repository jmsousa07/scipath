# P2 — Card que toca o repositório precisa nascer com workspace de repositório

## Contexto

O card `t_748bc02e` (`E2.6-f3`) nasceu com `workspace_kind=scratch` — um diretório temporário
**vazio, sem o monorepo**. O corpo do próprio card pedia que, antes de despachar, o workspace
fosse trocado para um worktree de `$HOME/scipath`.

Sem essa troca, o worker não teria o `Makefile` nem o `infra/db-setup.sh` — os arquivos que o
card manda corrigir. Ele reproduziria a falha por não encontrar o arquivo, ou (pior) declararia
que não há defeito, e a evidência seria o registro de um beco sem saída.

## Por que isso importa

`kanban_create` não expõe a escolha de worktree; quem cria o card precisa passar `--workspace`
explicitamente. Quando esquece, o defeito não aparece na hora: aparece **um worker depois**,
como uma run desperdiçada (custa RAM, tempo e um turno da fila serial) e, no limite, como um
card fechado por engano.

## Pedido

1. Registrar no `AGENTS.md`: **todo card que toca arquivos do monorepo nasce com workspace de
   repositório** (worktree/branch própria), nunca `scratch`. `scratch` fica para investigação
   que não toca o repo.
2. Se um card nascer com o workspace errado, o gerente corrige **antes** de promover — o
   `promoted` de `t_748bc02e` aconteceu com o workspace ainda vazio.
3. Vale uma checagem simples no despacho: card com `scratch` cujo corpo cita caminho de arquivo
   do repo é sinal de erro de criação.

## Critério de pronto

`AGENTS.md` com a regra e o motivo, sem tocar código. Evidência: diff do `AGENTS.md` + o
registro do `t_748bc02e` (que nasceu `scratch` e citava `Makefile` e `infra/db-setup.sh`).
