# E2.4 — Detalhe do material

- **Perfil:** backend · **Depende de:** E2.2 · **Status:** pronto para execução

## Objetivo

Servir `GET /api/materiais/{slug}` com o que o visitante precisa para entender o material e
confiar no que está vendo.

## Escopo

1. Resposta: título, descrição, tipo, categoria, **licença com a explicação do que permite e do que
   não permite** (RN006), autor público (nome, slug — sem e-mail), datas e estado de publicação.
2. Visibilidade: material inexistente, `RETIRADO` ou `EXCLUIDO` de conta alheia responde **404**
   para visitante, indistinguivelmente (não revelar que existe — RN002, CA008).
3. **Nenhuma** marca de revisão, certificação, curadoria ou verificação científica (RN010, RN011,
   CA012) — nem campo que sugira isso no schema.
4. Nenhum dado privado no payload (RNF013).

## Fora de escopo

Acesso ao arquivo (RF014, Etapa 4 — a Etapa 2 não serve arquivo).

## Critérios de aceite

1. Teste: material retirado → 404; material publicado → 200 com licença explicada.
2. Teste que assere a **ausência** dos campos privados na resposta (não só a presença dos públicos).
3. Slug inválido → 400 no `ProblemDetail` padrão; slug válido inexistente → 404.

## Evidência esperada

Saída dos testes (200, 404, 400), com o payload completo colado.

## Rastreabilidade

RF005, RN002, RN006, RN010, RN011, RNF013, CA002, CA008, CA012.
