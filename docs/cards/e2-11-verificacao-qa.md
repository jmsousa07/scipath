# E2.11 — Verificação independente da Etapa 2

- **Perfil:** qa · **Depende de:** E2.6, E2.8, E2.9, E2.10 · **Status:** pronto para execução

## Objetivo

Verificar a descoberta pública com o harness do E1.8, incluindo os **casos negativos** — que é
onde a descoberta pública costuma quebrar.

> Este card não escreve código de produto. O QA mede e reporta (§5).

## Escopo

1. Rodar o harness contra a Etapa 2 com o seed de desenvolvimento (E2.6) e registrar em
   `docs/qa/e2-descoberta-<data>.log`, com veredito por critério.
2. Consistência da busca (RN013): mesmo termo → mesmo conjunto; com e sem acento conforme a
   decisão do ADR-0008; combinada com cada filtro.
3. Negativos obrigatórios:
   - material `RETIRADO` e `EXCLUIDO` → **404** na API e estado correto na tela (CA008);
   - termo sem resultado e filtro sem resultado → 200 vazio / tela de vazio explicativa;
   - parâmetro inválido (`page=-1`, `ordem` inexistente, `slug` com formato inválido) → 400 no
     `ProblemDetail` padrão;
   - paginação além do fim → 200 vazio, nunca erro;
   - autor sem materiais → lista vazia.
4. Vazamento de dado privado: consultar as respostas públicas e o HTML por e-mail, hash, token e
   identificador de conta (RNF013).
5. SEO: HTML entregue contém o conteúdo (sem JS), `canonical` correto, rotas privadas `noindex`
   (RNF023–RNF025, CA011).
6. Acessibilidade e responsividade: teclado, foco visível, contraste dos pares de cor usados,
   375 px e 1440 px, `prefers-reduced-motion` (RNF002, RNF006–RNF008, CA009).
7. Desempenho percebido (RNF020): tempo da listagem com o seed, medido e registrado — sem meta
   inventada, apenas o número e a máquina.

## Fora de escopo

Corrigir o que falhar (vira card) e verificar telas de etapas futuras.

## Critérios de aceite

1. Cada item de escopo com veredito explícito: atende / não atende / não verificado.
2. Todo "não atende" reproduzível (comando + saída + esperado).
3. Lista explícita do que **não** foi verificado — nunca "tudo ok" sem lista.

## Evidência esperada

`docs/qa/e2-descoberta-<data>.log`, mais artefatos brutos (respostas HTTP, HTML coletado).

## Rastreabilidade

RN002, RN013, RNF013, RNF020, RNF023–RNF025, CA001–CA003, CA008, CA009, CA011, §17.5, §21.4.
