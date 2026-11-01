# E5.3 — Backend: editar metadados do próprio material

- **Perfil:** backend · **Depende de:** E5.2 · **Status:** pronto para execução

## Contexto

RF016 (E): o autor edita os metadados permitidos de um material próprio. Quatro critérios de
aceite, e três deles são do servidor: **propriedade verificada no servidor**, alterações
**validadas e persistidas**, e a alteração de metadados **não troca o proprietário**. O quarto (a
página pública refletir a mudança) é consequência direta de persistir no mesmo registro.

## Entrada

`contracts/openapi.yaml` v0.6 (`PUT /perfil/atual/materiais/{slug}`, `EdicaoDeMaterial`,
`MaterialProprio`), `docs/adr/ADR-0011-...md` §4, `PublicacaoDeMaterialService` (validação de
taxonomia e limites que já existe), `EstadoPublicacao`, E5.2 (leitura própria).

## Escopo

1. **`PUT /api/perfil/atual/materiais/{slug}`** — substitui o conjunto editável:
   `titulo` (1–200), `descricao` (1–2000), `categoriaSlug`, `tipoSlug`, `licencaSlug`; todos
   obrigatórios. Campo ausente → 400; campo presente com valor inválido ou taxonomia inexistente/
   inativa → 422 **no campo** (`errors[]`), o mesmo vocabulário da publicação (RF018).
2. **Imutáveis por esta rota**: `slug` (URL canônica estável, RNF025/RN009), autoria/propriedade
   (vem da sessão, RN001 — um `autorSlug`/`perfilId`/`contaId` no corpo é ignorado), `estado` (é o
   `PATCH`, E5.4), arquivo (trocar binário é outra operação) e `declaracaoDireito` (pertence ao ato
   de publicar, RF012). O schema do contrato não tem esses campos.
3. **Propriedade e estado**: material inexistente, de outra conta ou `EXCLUIDO` → **404
   indistinguível** (ADR-0011 §3), sem escrita nenhuma. `PUBLICADO` e `RETIRADO` são editáveis.
4. `atualizadoEm` sobe com a escrita; **`publicadoEm` não muda** — nem aqui, nem na republicação
   (ADR-0011 §4). Editar **não** exige conta `ATIVO`.
5. Resposta `200` com o `MaterialProprio` atualizado (o mesmo schema da leitura), para a tela
   confirmar sem uma segunda chamada.
6. A escrita exige `X-CSRF-Token`: sem sessão 401, sem o cabeçalho (com sessão) 403.

## Fora de escopo

Retirar/republicar/excluir (E5.4). Trocar o arquivo. Editar `slug`. Frontend (E5.7).

## Critérios de aceite

1. Testes: edição válida persiste e devolve o corpo atualizado; `slug` e `profile_id` **não** mudam
   (conferir no banco, antes e depois); `publicadoEm` preservado e `atualizadoEm` maior; corpo com
   `autorSlug`/`contaId`/`estado` de outra pessoa **ignorado** (§17.4);
   taxonomia inexistente → 422 no campo; `titulo` vazio → 422; campo ausente → 400.
2. **Acesso cruzado** (§17.4, metade de CA007): A tenta editar material de B → **404**, com o corpo
   igual ao do material inexistente, e **nenhuma alteração** no banco (conferido depois).
3. Material `EXCLUIDO` → 404, sem alteração. `401` sem sessão; `403` sem `X-CSRF-Token`.
4. A alteração aparece no detalhe público (`GET /materiais/{slug}`) — é o critério "a página pública
   reflete a alteração" (RF016).
5. `./mvnw -o test` verde com heap limitado, saída colada em `docs/qa/e5-3-<data>.log`, commitada.
   `make contract:check` e `make lint` verdes.

## Rastreabilidade

RF016, RF018, RN001, RNF004, RNF013, RNF025; CA007; DRS §8.3.5, §17.1–§17.2, §17.4; ADR-0006,
ADR-0011 §4.
