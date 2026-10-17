# Integração — contrato v0.4 (E3.1) no `main`

- **Perfil:** gerente (merge é ato do orquestrador) · **Depende de:** E3.1
- **Por que existe:** mesmo caso do card de integração dos contratos v0.2.1/v0.3.0 na Etapa 2. O
  E3.2, o E3.6 e o E3.7 implementam **contra o contrato v0.4**, e cada um nasce de um worktree
  ramificado do `main`. Sem este merge, os três recebem o `contracts/openapi.yaml` **sem** os
  endpoints de identidade — e vão inventar rota e schema por conta própria.

## O que fazer

No checkout principal `$HOME/scipath` (branch `main`), após o E3.1 estar `done`:

```bash
git merge --no-ff feat/e3-1-contrato-identidade \
  -m "merge(feat/e3-1-contrato-identidade): integra contrato v0.4 (identidade, sessao e perfil)"
```

Se o merge exigir decisão de contrato (rota, campo, código de erro), **não decidir**: registrar
bloqueio e parar.

## Critérios de aceite

1. `main` com `contracts/openapi.yaml` em `version: 0.4.0` e todos os endpoints de identidade,
   recuperação e verificação presentes.
2. `make contract:check` no `main` com **0 erros e 0 warnings**, saída colada.
3. `git rev-list --count main..feat/e3-1-contrato-identidade` = 0.
4. Evidência commitada em `docs/qa/integracao-contrato-v0.4-<data>.log`.

## Consequência

Enquanto este card não fechar, `E3.2`, `E3.6` e `E3.7` ficam retidos — estão ligados a ele como
dependentes, para não ramificarem do contrato v0.3.

## Fora de escopo

Implementação, migração, telas. Alterar o conteúdo do contrato (se estiver errado, o card volta).

## Rastreabilidade

E3.1, E3.2, E3.6, E3.7; AGENTS.md regras 1–2; DRS §14.3, §18.
