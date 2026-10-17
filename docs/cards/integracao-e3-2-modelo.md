# Integração — modelo de conta e perfil (E3.2) no `main`

- **Perfil:** gerente (merge é ato do orquestrador) · **Depende de:** E3.2
- **Por que existe:** o E3.3 (login/sessão), o E3.4 (tokens) e o E3.5 (editar perfil) usam as
  classes de conta/perfil que o E3.2 cria, e a migração `V4` que ele aplica. Cada card nasce de um
  worktree ramificado do `main`: sem este merge, os três compilam sobre um `main` sem a entidade e
  sem a tabela — o worker "conserta" criando uma segunda classe ou **declara um bloqueio** (uma run
  desperdiçada, nas duas hipóteses).

## O que fazer

No checkout principal `$HOME/scipath` (branch `main`), após o E3.2 estar `done`:

```bash
git merge --no-ff feat/e3-2-conta-cadastro \
  -m "merge(feat/e3-2-conta-cadastro): integra modelo de conta/perfil e cadastro (V4)"
```

Se houver conflito que exija decisão de modelo de dados, **não decidir**: registrar bloqueio e parar.

## Critérios de aceite

1. `main` com a migração `V4__conta_perfil.sql` e as classes de conta/perfil do E3.2.
2. `make test` no `main` verde, saída colada (a suíte aplica V1..V4 no banco de teste).
3. `git rev-list --count main..feat/e3-2-conta-cadastro` = 0.
4. Evidência commitada em `docs/qa/integracao-e3-2-<data>.log`.

## Consequência

Enquanto este card não fechar, `E3.3`, `E3.4` e `E3.5` ficam retidos.

## Fora de escopo

Implementação nova, correção do que falhar (vira card), telas, contrato.

## Rastreabilidade

E3.2, E3.3, E3.4, E3.5; AGENTS.md regras 1–2 e §17.5.
