# Integração — merge da Etapa 2 (E2.4 a E2.10) no `main`

- **Perfil:** gerente (aceite + merge são ato do orquestrador, não do worker que implementou)
- **Depende de:** E2.8, E2.9, E2.10 (com eles prontos, E2.4, E2.5 e E2.7 também estão)
- **Por que existe:** os cards de implementação commitam **apenas na própria branch** e nada
  integra sozinho. O `main` já tem E2.2, E2.3 (busca + ADR-0008) e E2.6; o resto da Etapa 2
  está fora dele. O E2.11 (QA da etapa) só pode medir um `main` íntegro.

## Estado verificado antes de escrever este card

- `main` = merge do E2.3 (`46329e0`) + ADR-0008 ratificado (`92fe253`); contrato v0.3.0; migração
  `V3__busca_texto_material.sql` aplicada no main.
- Branches a integrar: `feat/e2-4-detalhe`, `feat/e2-5-perfil-publico`, `feat/e2-7-pagina-inicial`,
  `feat/e2-8-explorar`, `feat/e2-9-detalhe-seo`, `feat/e2-10-perfil-categoria`.

## O que fazer

No checkout principal `$HOME/scipath` (branch `main`), na ordem de dependência:

```bash
for b in feat/e2-4-detalhe feat/e2-5-perfil-publico feat/e2-7-pagina-inicial \
         feat/e2-8-explorar feat/e2-9-detalhe-seo feat/e2-10-perfil-categoria; do
  git merge --no-ff "$b" -m "merge($b): integra Etapa 2 (descoberta publica)"
done
```

Conflito: resolver preservando o que o card dono do arquivo entregou, sem reescrever comportamento,
e **registrar cada conflito no laudo** (arquivo, lado escolhido, motivo). Se o conflito exigir
decisão de produto (o que a tela ou a API deve fazer), **não decidir**: registrar como bloqueio e
parar.

## Saída

1. `main` com as seis branches integradas.
2. `docs/qa/aceite-etapa-2.md` — uma linha por card: branch, commits, arquivos tocados pelo
   merge-base, o que foi conferido, veredito e ressalvas abertas.
3. `docs/qa/integracao-etapa-2-2026-10-17.log` — log bruto do pós-merge.

## Critérios de aceite

1. `git rev-list --count main..<branch>` = 0 para as seis branches.
2. No `main` pós-merge, com a saída colada: `make test`, `make lint`, `make contract:check`.
3. Nenhum commit de código autoral neste card além da resolução de conflito declarada no laudo.
4. `docs/qa/aceite-etapa-2.md` com veredito por card e a seção do que **não** foi verificado.

## Fora de escopo

Corrigir o que falhar (vira card novo), reescrever código dos cards, mudar o contrato, editar
migração já aplicada, integrar P1/P2 (documentação).

## Rastreabilidade

t_df6b24ea, t_428f779c, t_270e113f, t_4511bd3d, t_aa19ac87, t_ea75a1e9; AGENTS.md §17.5 e §21.3;
DRS §17.5.
