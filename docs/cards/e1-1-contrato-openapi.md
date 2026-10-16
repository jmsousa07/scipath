# E1.1 — Contrato OpenAPI v0.2: base do produto

- **Perfil:** po · **Depende de:** — · **Status:** pronto para execução

## Objetivo

Fixar a fronteira HTTP da base antes de qualquer código: estados operacionais, taxonomia de
referência, formato único de erro e envelope de paginação.

## Escopo

1. Revisar `GET /api/health` (RF022 — estados operacionais claros).
2. Adicionar leitura da taxonomia: `GET /api/categorias`, `GET /api/tipos-material`,
   `GET /api/licencas` (RF018 — selecionar categorias e licenças; RN005, RN006).
3. Definir **o schema de erro único** (RFC 7807 / `ProblemDetail` com `errors[]`) e usá-lo em
   todas as respostas de erro do contrato.
4. Definir o **envelope de paginação** padrão (RF002/RNF021) para reúso na Etapa 2.
5. **Remover do contrato os endpoints de material** herdados de antes do DRS: eles não têm
   requisito implementado e uma fronteira publicada sem implementação faz o frontend codificar
   contra ficção (§21.1). Serão reescritos no card da Etapa 2 que os especifica.

## Fora de escopo

Endpoints de material, conta, perfil, upload e arquivo. Modelo de dados (card E1.2).

## Critérios de aceite

1. `make contract:check` **sem erros e sem warnings** (hoje há 4 warnings): toda operação com
   `description` não vazia e `info.contact` preenchido.
2. Todo erro do contrato referencia **um** schema (`ProblemDetail`), com exemplos.
3. Os três endpoints de taxonomia documentam filtro por estado ativo e ordenação estável.
4. Envelope de paginação documentado com `page`, `size`, `total`, `items` e exemplo.

## Evidência esperada

`contracts/openapi.yaml` no diff + saída colada de `make contract:check`.

## Rastreabilidade

RF018, RF022, RN005, RN006, RNF021, RNF026, ADR-0002, ADR-0006, DRS §14.3.
