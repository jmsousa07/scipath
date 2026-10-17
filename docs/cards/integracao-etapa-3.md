# Integração — merge da Etapa 3 (E3.3 a E3.7) no `main`

- **Perfil:** gerente (aceite + merge são ato do orquestrador, não do worker que implementou)
- **Depende de:** E3.3, E3.4, E3.5, E3.6, E3.7
- **Por que existe:** os cards de implementação commitam apenas na própria branch e nada integra
  sozinho. O E3.8 (QA da Etapa 3) só pode medir um `main` íntegro.

## Estado verificado antes de escrever este card

Já integrados por cards anteriores desta etapa: contrato **v0.4** (integração do E3.1) e modelo de
conta/perfil + `V4` + cadastro (integração do E3.2). Falta integrar:

`feat/e3-3-login-sessao`, `feat/e3-4-tokens-verificacao`, `feat/e3-5-editar-perfil`,
`feat/e3-6-cadastro-login`, `feat/e3-7-editar-perfil-web`.

## O que fazer

No checkout principal `$HOME/scipath` (branch `main`), na ordem de dependência:

```bash
for b in feat/e3-3-login-sessao feat/e3-4-tokens-verificacao feat/e3-5-editar-perfil \
         feat/e3-6-cadastro-login feat/e3-7-editar-perfil-web; do
  git merge --no-ff "$b" -m "merge($b): integra Etapa 3 (identidade)"
done
```

Conflito: preservar o que o card dono do arquivo entregou, sem reescrever comportamento, e
**registrar cada conflito no laudo** (arquivo, lado escolhido, motivo). Conflito que exija decisão
de produto → registrar bloqueio e parar.

## Saída

1. `main` com as cinco branches integradas (contrato v0.4 e V4 já presentes).
2. `docs/qa/aceite-etapa-3.md` — uma linha por card da etapa (E3.1 a E3.7): branch, commits,
   arquivos pelo merge-base, o que foi conferido, veredito e ressalvas.
3. `docs/qa/integracao-etapa-3-<data>.log` — log bruto do pós-merge.

## Critérios de aceite

1. `git rev-list --count main..<branch>` = 0 para as cinco branches.
2. No `main` pós-merge, com saída colada: `make test`, `make lint`, `make contract:check`.
3. Nenhum commit de código autoral além da resolução de conflito declarada no laudo.
4. Laudo com a seção explícita do que **não** foi verificado.

## Fora de escopo

Corrigir o que falhar (vira card), reescrever código dos cards, mudar o contrato, editar migração
aplicada, integração das etapas seguintes.

## Rastreabilidade

E3.3–E3.7; AGENTS.md §17.5 e §21.3; DRS §17.5.
