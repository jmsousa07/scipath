# E1.3 — Migração V2: seed da taxonomia

- **Perfil:** backend · **Depende de:** E1.2 · **Status:** pronto para execução

## Objetivo

Carregar a taxonomia de referência como dado versionado, conforme o ADR-0006 — sem painel
administrativo no MVP.

## Escopo

Migração `V2__seed_taxonomia.sql` com `slug` estável como chave de referência:

- **6 tipos de material:** nota-de-aula, slides, artigo, apresentacao, guia-de-estudo, outro.
- **13 categorias:** computacao-e-tecnologia, matematica-e-estatistica, fisica-e-astronomia,
  quimica, ciencias-biologicas, saude, engenharias, ciencias-agrarias, ciencias-humanas,
  ciencias-sociais-aplicadas, linguistica-e-letras, artes, multidisciplinar — cada uma com nome
  de exibição e descrição curta.
- **7 licenças:** CC BY 4.0, CC BY-SA 4.0, CC BY-NC 4.0, CC BY-NC-SA 4.0, CC BY-ND 4.0,
  CC0 1.0, "Todos os direitos reservados" — cada uma com identificador, URL de referência e
  **descrição curta em linguagem simples do que permite** (RN006: licença nunca é decorativa nem
  sugere que estar público significa domínio público).

## Fora de escopo

Endpoints de leitura (E1.4 consome o contrato do E1.1), edição em tela, subcategorias.

## Critérios de aceite

1. Migração idempotente por versão e aplicada nos dois bancos.
2. Seed em **português**; o `slug` é a chave, o nome de exibição pode mudar sem quebrar dados.
3. `GET /api/categorias` (do E1.1) devolve as 13; tipos 6; licenças 7.
4. Teste de integração: material com categoria de `slug` inexistente é **recusado** (RN005).

## Evidência esperada

Log da migração; consultas `SELECT` mostrando 13/6/7 com `slug` e nome; log do teste negativo.

## Rastreabilidade

RF018, RN005, RN006, RNF028, ADR-0006, DRS §11.1.5–7.
