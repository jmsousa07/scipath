# E5.9 — Integração: merge da Etapa 5 (E5.2 a E5.7) no `main`

- **Perfil:** gerente · **Depende de:** E5.2, E5.3, E5.4, E5.5, E5.6, E5.7 · **Status:** pronto para execução

## Contexto

Gate de integração da Etapa 5, como nas etapas anteriores (E4.7). O QA (E5.10) mede o `main` — se as
branches da etapa não estiverem nele, a verificação mede um produto que não existe. Este card
também fecha, **de passagem**, a divergência herdada da rodada de aceitação: a correção do **F1**
(`t_3b46f764`, commit `b087938`) está aprovada e **não** integrada.

## Entrada

Branches dos cards E5.2–E5.7 (`wt/<task-id>`), branch do F1 `wt/t_3b46f764` (commit `b087938`),
`main`, `Makefile`, `infra/`.

## Escopo

1. Para cada branch da etapa (E5.2, E5.3, E5.4, E5.5, E5.6, E5.7): confirmar que **não** está
   integrado (`git merge-base --is-ancestor <head> main`), registrar o veredito e mergear com merge
   commit (sem squash), resolvendo só conflito trivial de arquivo de evidência — conflito de
   conteúdo real: comentar no card e **não** escolher lado.
2. **F1 — `b087938`** (`wt/t_3b46f764`): verificar se já está no `main`; não estando, mergear. É a
   correção que faz o logout atender a rota publicada (`DELETE /sessoes/atual`), pendência apontada
   pelo próprio card F4; o merge dela é o que fecha a divergência contrato × código da rodada.
3. **P5 — `wt/t_f498b3aa`** (laudo da aceitação do MVP, `docs/qa/p5-aceitacao-mvp.md` e log):
   verificar se já está no `main`; não estando, mergear. É o "antes" que o QA da etapa (E5.10) cita
   ao reabrir CA007/CA008, e registro que não pode viver só num branch — evidência fora do `main` não
   é auditável (§17.5, §21.3).
4. Estado final do `main`: **árvore limpa**, nenhum processo deixado no ar (portas 8080 e 3000
   livres), e as evidências dos cards presentes (`docs/qa/e5-*`).
5. Não corrija código, não ajuste contrato, não "aproveite para limpar": achado vira card.

## Fora de escopo

Medir a etapa (é o card E5.10, perfil `qa`). Merge de outros branches fora da lista.

## Critérios de aceite

1. `git log --oneline -20` do `main` colado, mostrando os merges da etapa e o do F1 (ou a prova de
   que este já estava integrado).
2. `make test`, `make lint` e `make contract:check` verdes **no `main`** após os merges, cada um com
   a saída crua colada (um alvo por linha — nada de encadear por pipe: o status do `tail` mente).
3. `git status` limpo e portas 8080/3000 livres (comando colado).

## Evidência exigida

`docs/qa/integracao-etapa-5-<data>.log` commitado no `main`.

## Rastreabilidade

AGENTS.md (evidência ou não aconteceu; quem implementa não verifica); DRS §17.5, §21.3; cards F1
(`t_3b46f764`), F4 (`t_a8e75a19`), E5.2–E5.7, E5.10.
