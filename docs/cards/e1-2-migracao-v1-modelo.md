# E1.2 — Migração V1: modelo de dados do MVP

- **Perfil:** backend · **Depende de:** E1.1 · **Status:** pronto para execução

## Objetivo

Criar o schema do MVP em migração Flyway versionada, refletindo o modelo de domínio do DRS e as
decisões dos ADRs 0003 e 0005 — sem implementar nenhuma funcionalidade.

## Escopo

Migração `V1__modelo_mvp.sql` com as tabelas do DRS §11: `account` (e-mail único normalizado,
`senha_hash`, estado da conta, `email_verificado_em`), `verification_token` (hash do token, tipo,
expiração, consumo — uso único), `profile` (separado da conta: nome público, slug único, bio,
afiliação **declarada**, links), `category`, `material_type`, `license`, `material` (título,
descrição, tipo, categoria, licença, autor, `estado_publicacao` em
`PUBLICADO | RETIRADO | EXCLUIDO`, datas), `file_asset` (chave interna, nome original
sanitizado, tipo detectado, tamanho, checksum SHA-256).

Inclui: chaves estrangeiras, `UNIQUE` de e-mail e slug, índices para busca/listagem
(categoria, tipo, estado, data), `CHECK` nos enums de estado, `NOT NULL` nos metadados
obrigatórios (RN004) e comentários nas colunas cujo sentido não é óbvio.

## Fora de escopo

Seed de taxonomia (E1.3). Qualquer endpoint, service ou repositório.

## Critérios de aceite

1. `flyway migrate` limpo em `scipath` **e** em `scipath_test`.
2. Nenhuma migração editada depois de aplicada (RNF028).
3. Procedimento de reversão documentado em `db/migration/README.md` e **exercitado** no banco de
   teste (drop das tabelas criadas, na ordem inversa) — "reversível" no DoD significa procedimento
   existente e testado, não promessa.
4. Categoria inexistente em `material` é recusada pela FK (base para RN005).
5. `material` não aceita `estado_publicacao` fora do enum.

## Evidência esperada

Log do `flyway migrate` nos dois bancos; saída de `\d+` das tabelas; log do procedimento de
reversão executado; um `INSERT` inválido recusado (erro colado).

## Rastreabilidade

RF002 (depende de modelo de material, categoria, tipo e estado — §19.2), RN004, RN009, RNF016,
RNF028, ADR-0003, ADR-0005, ADR-0006, DRS §11.
