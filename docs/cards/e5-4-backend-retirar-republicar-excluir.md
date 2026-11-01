# E5.4 — Backend: retirar, republicar e excluir material próprio

- **Perfil:** backend · **Depende de:** E5.3 · **Status:** pronto para execução

## Contexto

RF017 (E): o autor retira da publicação ou exclui um material próprio. A aceitação do MVP mediu o
**efeito** (material `RETIRADO`/`EXCLUIDO` sai da listagem, da busca, do perfil do autor, e detalhe e
arquivo respondem 404 indistinguível) e declarou o que falta: o **ato**. É este card. RF019 e
`EstadoPublicacao` já declaram as transições — aqui elas passam a ser executáveis, sem inventar uma
segunda regra.

## Entrada

`contracts/openapi.yaml` v0.6 (`PATCH` e `DELETE /perfil/atual/materiais/{slug}`),
`docs/adr/ADR-0011-...md` §5–§7, `EstadoPublicacao` (`podeTransicionarPara`, `transicoesValidas`),
`backend/src/main/resources/db/migration/` (V1 §material, README das migrações), E5.2/E5.3.

## Escopo

1. **`PATCH /api/perfil/atual/materiais/{slug}`** — corpo `{"estado": "RETIRADO" | "PUBLICADO"}`.
   - a transição é validada por `EstadoPublicacao.podeTransicionarPara` (fonte única);
   - pedir o estado em que o material **já está** → **200 idempotente, sem escrita** (nada de
     `atualizadoEm` mexido);
   - `EXCLUIDO` no corpo → **422** no campo, com mensagem que aponta o `DELETE` (nenhum estado
     arbitrário entra por requisição — RF019);
   - `RETIRADO → PUBLICADO` com conta `PENDENTE_VERIFICACAO` → **403** acionável ("confirme seu
     e-mail para republicar"): repor material no público é publicar (ADR-0003 (b)); retirar,
     editar e excluir **não** exigem verificação;
   - `publicadoEm` **não** muda na republicação; `atualizadoEm` sobe (ADR-0011 §4).
2. **`DELETE /api/perfil/atual/materiais/{slug}`** — exclusão **lógica** (ADR-0005 §3 (b)):
   `estado_publicacao = 'EXCLUIDO'` e `finalizado com excluido_em = now()`, **204** sem corpo. O
   binário **não** é removido aqui — quem purga é a rotina de retenção (E5.5).
3. **Migração nova (aditiva, imutável)** `V5__material_excluido_em.sql`: coluna
   `material.excluido_em timestamptz NULL` + comentário dizendo que é o instante da exclusão lógica e
   a base do prazo de retenção de 7 dias (ADR-0005 §3, ADR-0011 §6). Atualize o `README.md` das
   migrações. Não edite migração aplicada.
4. **Recusas iguais nas duas rotas**: material inexistente, de outra conta ou já `EXCLUIDO` → **404
   indistinguível** (a exclusão é terminal: pedir de novo responde 404, não 204); slug malformado
   400; sem sessão 401; sem `X-CSRF-Token` 403.
5. **Efeito imediato no público**: a mesma requisição que retira/exclui já muda as cinco superfícies
   (listagem, busca, detalhe, arquivo, perfil do autor) — a elegibilidade é derivada do estado
   persistido, não de cache (RF019, RN002).

## Fora de escopo

Purga do binário e anonimização de metadados (E5.5). Frontend (E5.6). Restauração de `EXCLUIDO`
(não existe no MVP — estado terminal).

## Critérios de aceite

1. Transições: `PUBLICADO→RETIRADO` e `RETIRADO→PUBLICADO` → 200 com o estado novo; mesmo estado →
   200 sem escrita (conferir `atualizadoEm` no banco); `EXCLUIDO` no corpo → 422; `RASCUNHO` → 422;
   campo `estado` ausente → 400; `RETIRADO→PUBLICADO` com conta pendente → 403.
2. Exclusão: `DELETE` → 204; no banco `estado_publicacao='EXCLUIDO'` e `excluido_em` preenchido;
   **segundo `DELETE` → 404**; material excluído responde 404 no detalhe e no arquivo públicos e sai
   da listagem e da busca no mesmo instante (RF017, CA008).
3. **Acesso cruzado** (§17.4, metade de CA007): A tenta retirar e excluir material de B → **404** nas
   duas rotas, corpo igual ao de slug inexistente, e **nenhuma alteração** no banco (conferir
   `estado_publicacao` e `excluido_em` de B depois).
4. `401` sem sessão e `403` sem `X-CSRF-Token` nas duas rotas.
5. `./mvnw -o test` verde com heap limitado, saída colada em `docs/qa/e5-4-<data>.log`, commitada.
   `make db:migrate` (dev e teste) e `make contract:check` verdes.

## Rastreabilidade

RF017, RF019, RN002, RNF004, RNF010; CA007, CA008; DRS §7.4, §8.3.6, §8.3.8, §17.1–§17.4; ADR-0003
(b), ADR-0005 §3, ADR-0011 §5–§7.
