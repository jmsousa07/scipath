# E1.5 — Suíte reproduzível de shell limpo + integração com Postgres real

- **Perfil:** backend · **Depende de:** E1.2, E1.4 · **Status:** pronto para execução

## Objetivo

Fechar os ajustes 4 e 5 do ADR-0004: a evidência de teste precisa ser **reproduzível por outra
pessoa, num clone limpo**, sem depender do ambiente de quem rodou.

## Contexto (fato verificado, não hipótese)

`./mvnw test` num shell sem `SPRING_DATASOURCE_*` termina em `BUILD FAILURE` com
`Failed to determine a suitable driver class`: o contexto Spring não sobe porque
`spring.datasource.url` está vazio. O alvo `make test` **não** carrega o `.env`, e não existe
`src/test/resources/application-test.yml`. O log do F0.1 registra 5/5 verdes no ambiente do
implementador — ou seja, hoje a evidência depende de estado invisível.

## Escopo

1. `src/test/resources/application-test.yml` com datasource explícito para o banco **`scipath_test`**
   (nunca o banco de desenvolvimento).
2. Definir e **registrar** como o teste se autentica sem colocar segredo no repositório
   (RNF012). Escolher uma: (i) role de teste com autenticação `trust` local no `pg_hba` — sem
   senha alguma versionada; ou (ii) `.env.test` documentado como credencial local e restrita a
   127.0.0.1. Preferência registrada: (i). A escolha vai para `infra/` e para o `README`.
3. Heap limitado no surefire (`argLine`) para caber na máquina (AGENTS.md — higiene de recursos).
4. Teste de integração contra o Postgres real: migrações aplicadas, e para `scipath_test` os
   casos de integridade já existentes (categoria inválida do E1.3, enum inválido do E1.2).

## Fora de escopo

Testcontainers (não há Docker nesta máquina — ADR-0007).

## Critérios de aceite

1. `env -i PATH=/usr/bin:/bin HOME=$HOME make test` fica **verde**, com a saída colada — é este o
   critério que prova reprodutibilidade.
2. Nenhuma senha, URL de produção ou token no repositório (`grep` de verificação colado).
3. O teste de integração roda contra `scipath_test`, não contra `scipath`.

## Evidência esperada

Saída completa do `make test` em shell limpo; `grep` de segredos; `\dt` do `scipath_test`.

## Rastreabilidade

RNF012, RNF027, RNF030, §17.2, §17.5, ADR-0004 (ajustes 4 e 5).
