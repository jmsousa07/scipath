# E5.8 — Integração: contrato v0.6 e a especificação da Etapa 5 no `main`

- **Perfil:** gerente · **Depende de:** E5.1 · **Status:** pronto para execução

## Contexto

O contrato v0.6 nasce no branch do card E5.1 e a especificação da Etapa 5 (cards, ADR-0011,
`docs/01-arquitetura.md`) no branch do card F4 (`t_a8e75a19`). Nenhum worker mergeia nada sozinho, e
todo card a partir daqui nasce de `main`: sem este merge, os cards E5.2–E5.7 executam **sem** a
fronteira e **sem** as decisões que os governam (o worker lê `main`, não o branch do vizinho).

## Entrada

`contracts/openapi.yaml` (branch do card E5.1), `docs/adr/ADR-0011-gerenciamento-material-proprio.md`,
`docs/cards/e5-*.md` e `docs/01-arquitetura.md` (branch `wt/t_a8e75a19`), `Makefile`.

## Escopo

1. Merge no `main`, **preservando o histórico** (merge commit, não squash), de:
   - o branch do card **E5.1** (contrato v0.6 + evidência `docs/qa/e5-1-*`);
   - o branch **`wt/t_a8e75a19`** (specs `docs/cards/e5-*.md`, ADR-0011, atualização de
     `docs/01-arquitetura.md`) — confirme antes que o card `t_a8e75a19` está `done`.
2. Antes de mergear qualquer um dos dois, confirme que **não** já está integrado
   (`git merge-base --is-ancestor <head> main`) e registre o veredito. Merge repetido de branch já
   integrado só cria ruído.
3. Nenhuma outra alteração neste card: se a integração revelar conflito de conteúdo real, **não**
   resolva por conta própria escolhendo um lado — registre no card (comentário) e bloqueie, dizendo
   o que conflitou.
4. Confirme o estado do `main` **depois** do merge: versão do contrato, presença dos arquivos de
   spec e do ADR, árvore limpa.

## Fora de escopo

Código de backend/frontend (branches de E5.2–E5.7 — é o card E5.9). Corrigir defeito de outro card.
Ajustar o contrato (o contrato é o do E5.1; se estiver errado, o achado vira card).

## Critérios de aceite

1. `main` contém `info.version: 0.6.0`, `docs/adr/ADR-0011-*.md`, os dez `docs/cards/e5-*.md` e a
   atualização de `docs/01-arquitetura.md` — provado por `git log --oneline` e `git show` colados.
2. `make contract:check` exit 0 **no `main`** depois do merge, saída colada.
3. Nenhum processo deixado no ar e nenhuma alteração fora do escopo do card (`git status` limpo).

## Evidência exigida

`docs/qa/integracao-contrato-v0.6-<data>.log` commitado no `main`, com: os `merge-base
--is-ancestor` (antes), os merges, `git log --oneline` do `main`, `grep` da versão do contrato e o
`make contract:check`.

## Rastreabilidade

AGENTS.md (contrato primeiro; evidência ou não aconteceu); DRS §14.3, §21.3; ADR-0011; cards E5.1–E5.10.
