# Migrações Flyway do SciPath

Migrações versionadas (`V<n>__<slug>.sql`), **aditivas e imutáveis** (RNF028). Uma migração
já aplicada nunca é editada: correção entra como `V2__...`, `V3__...`. O `slug` é curto e
descritivo; o número é a ordem.

- `V1__modelo_mvp.sql` — modelo de dados do MVP (card E1.2): conta, identidade, taxonomia
  de referência, arquivo e material.
- `V2__seed_taxonomia.sql` — seed da taxonomia (card E1.3). Ainda não existe.

## Como aplicar

```bash
make db:migrate        # banco de dev   (SPRING_DATASOURCE_URL)
make db:migrate:test   # banco de teste (SPRING_DATASOURCE_URL_TEST)
```

Os dois alvos do `Makefile` carregam o `.env` da raiz e passam os `-Dflyway.*`; falha de
migração sai com exit != 0. O comando manual abaixo faz o mesmo, para quem precisa de
controle fino (outro banco, outro `.env`):

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

## Reversão (rollback)

O DoD chama "reversível" o que tem **procedimento existente e exercitado**, não promessa. A
`V1` não usa `DROP`/`ALTER`: é uma criação de tabelas limpa, então a reversão é o drop das
tabelas na ordem inversa das dependências (filhas antes das mães). Isto **destrói dados** —
vale para banco de desenvolvimento/teste, nunca para produção com dados reais.

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
