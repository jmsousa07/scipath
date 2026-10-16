# Migrações Flyway do SciPath

Migrações versionadas (`V<n>__<slug>.sql`), **aditivas e imutáveis** (RNF028). Uma migração
já aplicada nunca é editada: correção entra como `V2__...`, `V3__...`. O `slug` é curto e
descritivo; o número é a ordem.

- `V1__modelo_mvp.sql` — modelo de dados do MVP (card E1.2): conta, identidade, taxonomia
  de referência, arquivo e material.
- `V2__seed_taxonomia.sql` — seed da taxonomia (card E1.3): 6 tipos de material,
  13 categorias e 7 licenças, com `slug` como chave estável e `ativo = true`.

## Como aplicar

O plugin Flyway do Maven **não** lê o `application.yml` nem o `.env`: a URL, o usuário e a
senha vêm de `-Dflyway.*` (ou de um `flyway.conf` fora do repositório). O `.env` da raiz
tem as variáveis e é a fonte dos valores:

```bash
cd backend
set -a; . ../.env; set +a
./mvnw flyway:migrate \
  -Dflyway.url="$SPRING_DATASOURCE_URL" \
  -Dflyway.user="$SPRING_DATASOURCE_USERNAME" \
  -Dflyway.password=<valor-omitido> \
  -Dflyway.locations=filesystem:src/main/resources/db/migration
```

Notas de operação (custaram uma execução silenciosa vazia):

- Use `filesystem:src/main/resources/db/migration`. Com `classpath:db/migration` o plugin
  cria a tabela de histórico e **não encontra nenhuma migração** — sai com código 0 e não
  aplica nada. O `classpath:` só faz sentido para o Flyway dentro da aplicação, não para o
  plugin.
- `-Dflyway.password` nunca vai para log de evidência; o valor sai do `.env` por expansão
  da própria shell.
- O banco de teste é `scipath_test` (`SPRING_DATASOURCE_URL_TEST`). Migrar os **dois** bancos
  é requisito do card E1.2.

## Banco de teste e a suíte (`make test`)

A suíte de backend roda contra `scipath_test`, e o datasource dela **não** vem do `.env`:
vem de `backend/src/test/resources/application-test.yml` (perfil `test`, ativado pelo
surefire). O alvo `make test` não carrega o `.env` — se carregasse, `SPRING_DATASOURCE_URL`
venceria o YAML e a suíte rodaria contra o banco de desenvolvimento `scipath`.

A autenticação do banco de teste não usa senha nenhuma: role `scipath_test` sem senha, com
`trust` no `pg_hba` restrito ao banco, à role e ao loopback. Quem prepara isso é
`infra/db-test-setup.sh` (detalhes em `infra/README.md`, card E1.5). Consequência prática:
`make test` funciona num shell limpo, sem `.env` e sem variável exportada, e as migrações
são aplicadas pelo próprio teste quando o banco está vazio.

## Reversão (rollback)

O DoD chama "reversível" o que tem **procedimento existente e exercitado**, não promessa. A
`V1` não usa `DROP`/`ALTER`: é uma criação de tabelas limpa, então a reversão é o drop das
tabelas na ordem inversa das dependências (filhas antes das mães). Isto **destrói dados** —
vale para banco de desenvolvimento/teste, nunca para produção com dados reais.

A `V2` só **insere** linhas de referência (`INSERT ... ON CONFLICT (slug) DO NOTHING`), sem
DDL. Revertê-la é apagar as linhas semeadas e a versão no histórico — nada de schema:

```sql
DELETE FROM material WHERE category_id      IN (SELECT id FROM category      WHERE slug IN (...));
DELETE FROM material WHERE material_type_id IN (SELECT id FROM material_type WHERE slug IN (...));
DELETE FROM material WHERE license_id       IN (SELECT id FROM license       WHERE slug IN (...));
DELETE FROM category      WHERE slug IN ('computacao-e-tecnologia', 'matematica-e-estatistica', ...);
DELETE FROM material_type WHERE slug IN ('nota-de-aula', 'slides', ...);
DELETE FROM license       WHERE slug IN ('cc-by-4.0', ..., 'todos-os-direitos-reservados');
DELETE FROM flyway_schema_history WHERE version = '2';
```

Os `DELETE FROM material` existem porque a FK é `ON DELETE NO ACTION`: em banco com
material publicado apontando para uma categoria da seed, apagar a categoria falha — de
propósito, porque apagar taxonomia em uso nunca é operação de reversão de migração, é
decisão de produto. Em banco de teste vazio as linhas de `material` não existem e os
deletes são no-ops. O `DELETE` no histórico é o que permite reaplicar a `V2` depois.

Ordem inversa de `V1__modelo_mvp.sql`:

```
1. material            (depende de profile, category, material_type, license, file_asset)
2. file_asset          (depende de nada; mãe de material)
3. verification_token  (depende de account)
4. profile             (depende de account)
5. account
6. category            (auto-referência parent_id: cai junto)
7. material_type
8. license
```

Procedimento, banco de teste:

```bash
cd backend
set -a; . ../.env; set +a
PGPASSWORD="$SPRING_DATASOURCE_PASSWORD" psql \
  -h 127.0.0.1 -U "$SPRING_DATASOURCE_USERNAME" -d scipath_test \
  -v ON_ERROR_STOP=1 <<'SQL'
DROP TABLE IF EXISTS material;
DROP TABLE IF EXISTS file_asset;
DROP TABLE IF EXISTS verification_token;
DROP TABLE IF EXISTS profile;
DROP TABLE IF EXISTS account;
DROP TABLE IF EXISTS category;
DROP TABLE IF EXISTS material_type;
DROP TABLE IF EXISTS license;
DELETE FROM flyway_schema_history WHERE version = '1';
SQL
```

O `DELETE` na tabela de histórico é o que permite reaplicar a `V1` depois; sem ele o Flyway
recusa ("relation already exists") porque ainda considera a versão aplicada. Apagar o
histórico **não** é editar a migração — o arquivo `.sql` permanece byte a byte igual.

Depois de reverter, reaplicar com o comando da seção "Como aplicar". A mesma sequência de
drop, para produção com dados, seria precedida de backup e feita em janela — o ADR-0007
trata backup/hospedagem, que ainda não existe.
