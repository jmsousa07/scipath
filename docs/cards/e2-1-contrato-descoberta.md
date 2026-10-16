# E2.1 — Contrato OpenAPI v0.3: descoberta pública

- **Perfil:** po · **Depende de:** E1.1a · **Status:** pronto para execução

## Objetivo

Publicar a fronteira da descoberta — o que o visitante sem conta pode consumir.

## Escopo

1. `GET /api/materiais` — parâmetros `q`, `categoria`, `tipo`, `ordem`, `page`, `size`, com o
   envelope de paginação do v0.2. Documentar a **ordem padrão** e o critério de desempate estável
   (§12.2); **não** existirá ordenação por "relevância" nem contagem de popularidade.
2. `GET /api/materiais/{slug}` — detalhe com autor, categoria, tipo, licença (com o que permite) e
   estado; **404** para material inexistente ou não público.
3. `GET /api/autores/{slug}` — perfil público e seus materiais publicados (paginados).
4. Em todos: exemplos que respeitam RN002 (só público), RN006 (licença sempre presente) e RNF013
   (nenhum campo privado no schema — e-mail, hash, token).
5. Documentar nos exemplos os campos de SEO (título, descrição,canonical) que o frontend deve
   emitir (RNF024).

## Fora de escopo

Endpoints de conta, perfil próprio e upload (Etapas 3 e 4).

## Critérios de aceite

1. `make contract:check` sem erros e sem warnings.
2. Nenhum campo privado em nenhum schema público.
3. Cada parâmetro de filtro e ordenação com descrição e exemplo; parâmetro inválido → 400 no
   `ProblemDetail` padrão.

## Evidência esperada

Diff do `contracts/openapi.yaml` + saída de `make contract:check`.

## Rastreabilidade

RF002–RF007, RN002, RN006, RN013, RNF013, RNF021, RNF023–RNF025, DRS §12, §15.
