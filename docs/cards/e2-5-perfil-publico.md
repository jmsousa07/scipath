# E2.5 — Perfil público do autor e navegação

- **Perfil:** backend · **Depende de:** E2.4 · **Status:** pronto para execução

## Objetivo

Servir `GET /api/autores/{slug}` e permitir a navegação material → autor → categoria (RF007).

## Escopo

1. Perfil público: nome público, bio, áreas de interesse, links, **afiliação declarada** —
   apresentada como declaração do usuário, jamais como verificação institucional (RN011).
2. Materiais publicados do autor, paginados com o envelope padrão (RF006, RNF021).
3. Perfil de conta não `ATIVA` → **404** para visitante.
4. **Nada privado**: o schema não expõe e-mail, hash de senha, tokens, nem dados de conta (RNF013).

## Fora de escopo

Editar o próprio perfil (RF011 — Etapa 3).

## Critérios de aceite

1. Teste que assere ausência de e-mail/token/hash no payload (por lista de campos proibidos).
2. Autor sem material publicado → 200 com lista vazia (não 404).
3. Teste de navegação: o `slug` de categoria devolvido no material é o mesmo que a listagem
   aceita como filtro (RN013 — consistência entre telas).

## Evidência esperada

Saída dos testes com payload completo; o teste de campos proibidos.

## Rastreabilidade

RF006, RF007, RN011, RN013, RNF013, RNF021, CA003, CA012.
