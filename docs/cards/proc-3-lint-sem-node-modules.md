# P3 — `make lint` reprova em checkout sem `node_modules` (ambiente lido como defeito de código)

## Contexto

Medido em 2026-10-17 no `main` recém-integrado (E2.3 + E2.4 + E2.7 + E2.8):

```
$ make lint
> eslint .
Error [ERR_MODULE_NOT_FOUND]: Cannot find package 'eslint-config-next' imported from
  $HOME/scipath/frontend/eslint.config.mjs
make: *** [Makefile:128: lint] Error 2
```

O motivo não é código: `frontend/node_modules` tinha **0 entradas** no checkout principal. As
dependências estão declaradas (`eslint` 10.13.0, `eslint-config-next` 16.4.2) e o
`package-lock.json` está commitado desde o E1.10. Cada worker instala no **próprio worktree** —
o checkout principal nunca recebeu `npm ci`. Depois de `npm ci` no `frontend/`, o mesmo alvo saiu
verde (eslint + `tsc --noEmit`, exit 0) e o `make contract:check` deu 0 erros.

## Por que importa

O gate do DRS §17.5 roda no `main` (card de Integração e card de QA). Hoje esse gate reprova por
ambiente e o vermelho é indistinguível de defeito de merge — exatamente o inverso do problema do
E1.2a/E2.6-f1, em que o alvo passava verde sem testar nada. Um vermelho que não é defeito queima
uma run inteira e leva a "consertar" código que está certo.

## Pedido

1. Declarar a precondição **antes** de chamar o npm: no alvo `lint` do `Makefile`, se
   `frontend/node_modules` não existir, imprimir uma linha explícita (ex.: "frontend/node_modules
   ausente no checkout principal — rode `npm ci` em frontend/; a suíte de QA instala por conta
   própria") e sair com código 1 **declarando** o motivo, como já é feito quando o projeto não
   existe. **Não** rodar `npm ci` dentro do alvo: instalação de dependência é de card de
   dependência (AGENTS.md, higiene de recursos) e é cara.
2. Se a decisão for documentar em vez de mexer no alvo: a linha vai para o `AGENTS.md` — arquivo
   protegido, exige aprovação do dono; registre o pedido.

## Critério de pronto

Rodar `make lint` num clone sem `node_modules` imprime a precondição (não parece defeito de
código); com `node_modules` instalado, o alvo segue verde. Evidência: a saída de hoje e a saída
nova, coladas.

## Rastreabilidade

DRS §17.5, §21.3; AGENTS.md (Definição de pronto, higiene de recursos); E1.8, E1.10, E1.10-f1.
