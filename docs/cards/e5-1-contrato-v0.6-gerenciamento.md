# E5.1 — Contrato v0.6: gerenciar o material próprio (listar, editar, retirar, excluir)

- **Perfil:** po · **Depende de:** — (primeira peça da Etapa 5) · **Status:** pronto para execução

## Contexto

O contrato está em **v0.5.0** e a Etapa 5 (DRS §18, "Gerenciamento: listar, editar e retirar
materiais próprios") não tem fronteira: `PUT`/`PATCH`/`DELETE /materiais/{slug}` respondem 405. É o
que deixa **CA007 e CA008 parcialmente NÃO VERIFICADOS** na aceitação do MVP (`t_f498b3aa`, §2.7 e
§2.8). Regra do projeto: a mudança de API começa aqui, no contrato, antes do código.

## Entrada

`contracts/openapi.yaml` (v0.5.0, schemas `Pagina`, `MaterialResumo`, `MaterialDetalhe`,
`PublicacaoMaterial`, `ProblemDetail`), `docs/adr/ADR-0011-gerenciamento-material-proprio.md`
(decisões), `docs/01-arquitetura.md` §2 e §5, `PAGE_BLUEPRINTS.md` §12–§13.

## Escopo — tag nova `gerenciamento`, cinco rotas

Superfície **`/perfil/atual/materiais`** (o material é do perfil da sessão — RN001). **Não** use
`/materiais/proprios`: o literal colidiria com `GET /materiais/{slug}` e um material com slug
`proprios` ficaria inalcançável.

| Rota | Ato |
|---|---|
| `GET /perfil/atual/materiais` | lista os próprios (RF015) |
| `GET /perfil/atual/materiais/{slug}` | lê um próprio, em qualquer estado (RF015) |
| `PUT /perfil/atual/materiais/{slug}` | edita metadados (RF016) |
| `PATCH /perfil/atual/materiais/{slug}` | retira / republica (RF017, RF019) |
| `DELETE /perfil/atual/materiais/{slug}` | exclui, logicamente (RF017) |

1. **Lista**: `page`/`size` do envelope `Pagina`; ordenação `atualizadoEm` DESC com desempate por
   `id` ASC; vazio = 200 com `items: []`; os três estados aparecem (`PUBLICADO`, `RETIRADO`,
   `EXCLUIDO`) — a leitura do dono não esconde o que ele excluiu.
2. **Editar**: corpo JSON `EdicaoDeMaterial` com exatamente `titulo` (1–200), `descricao` (1–2000),
   `categoriaSlug`, `tipoSlug`, `licencaSlug` — todos obrigatórios, mesmos limites de
   `PublicacaoMaterial`. `slug`, autoria, `estado` e arquivo **não existem** no schema (enviá-los não
   muda nada).
3. **Estado**: corpo `TransicaoDeMaterial` com `estado` obrigatório, `enum: [PUBLICADO, RETIRADO]`.
   Mesmo estado → 200 idempotente. `EXCLUIDO` no corpo → 422 (a exclusão é o `DELETE`).
4. **Excluir**: 204 sem corpo; exclusão lógica (metadados retidos; binário purgado 7 dias depois pelo
   E5.5).
5. **Falhas, iguais nas rotas de ação**: sem sessão 401 · sem `X-CSRF-Token` 403 · slug malformado
   400 · **inexistente, de outra conta ou `EXCLUIDO` → 404 indistinguível** (mesmo corpo do 404
   público — RN002, CA007/CA008) · `PATCH` para `PUBLICADO` com conta `PENDENTE_VERIFICACAO` → 403
   acionável (ADR-0003 (b)) · campo com valor inválido/taxonomia inexistente 422 · campo ausente 400.
6. **Schemas novos**: `MaterialProprio` (`slug`, `titulo`, `descricao`, `estado` com os três valores,
   `categoria{slug,nome}`, `tipo{slug,nome}`, `licenca{slug,nome}`,
   `arquivo{nomeOriginal,tamanhoBytes,disponivel}`, `publicadoEm`, `atualizadoEm`),
   `PaginaMateriaisProprios`, `EdicaoDeMaterial`, `TransicaoDeMaterial`. Sem `autor` (é sempre o da
   sessão), sem campo privado (RNF013), sem `acoes[]` (a tela deriva do estado).

## Fora de escopo

Implementar backend/frontend. Trocar o arquivo do material. Rascunho (não existe: V1 fixa três
estados).

## Critérios de aceite

1. `make contract:check` exit 0, saída colada.
2. As cinco rotas com todos os códigos de erro acima e exemplo de `ProblemDetail` em cada um.
3. `info.version` = **0.6.0**, com o parágrafo da versão registrando as decisões (inclusive que a
   rota do logout permanece `DELETE /sessoes/atual`, como publicado na v0.4 — a correção é de código,
   card F1).
4. Nenhuma rota, campo ou resposta da v0.5 muda de forma ou significado (mudança aditiva), e o diff
   mostra isso.

## Evidência exigida

`docs/qa/e5-1-<data>.log` commitado com: saída de `make contract:check` e o diff do contrato
(`git diff -- contracts/openapi.yaml`). Commit no branch do card.

## Rastreabilidade

RF015–RF017, RF019; RN001, RN002, RN013; RNF004, RNF013, RNF021, RNF025; CA007, CA008; DRS §7.4,
§15.2, §17.4; ADR-0003 (b), ADR-0005 §3, ADR-0011.
