# infra/ — ambiente local do SciPath

Scripts de ambiente. Nada aqui é produção: a máquina local não tem Docker (ADR-0007), o
PostgreSQL 16 é nativo em `127.0.0.1:5432`.

| Script | Para que serve |
|---|---|
| `db-setup.sh` | cria a role e o banco de **desenvolvimento** (`scipath`) e escreve o `.env` com uma senha gerada (nunca impressa). |
| `db-test-setup.sh` | prepara o banco de **teste** (`scipath_test`) para a suíte de backend, **sem senha versionada**. Ver abaixo. |

## Banco de teste e autenticação da suíte (card E1.5)

A suíte de backend (`make test`) precisa rodar reproduzível de um shell limpo, sem depender
do `.env` de quem executa (ADR-0004, ajustes 4 e 5; RNF012, §17.2, §17.5). Para isso a
autenticação do banco de teste **não usa senha nenhuma**:

- **role `scipath_test`**, com `LOGIN` e **sem senha** (`PASSWORD NULL`);
- **banco `scipath_test`**, dono a role `scipath_test`;
- **regra `trust` no `pg_hba`**, restrita ao banco `scipath_test`, à role `scipath_test` e
  ao loopback (`127.0.0.1/32` e `::1/128`) — inserida **antes** da regra genérica
  `scram-sha-256`, porque no `pg_hba` a primeira regra que casa é a que vale.

Escolha registrada no card E1.5 (opção (i)): nenhum segredo é versionado, e o `.env` deixa
de ser pré-requisito para rodar os testes. A opção (ii) — `.env.test` com senha local —
ficou descartada por exigir um segredo no repositório para o mesmo resultado.

O datasource da suíte mora em `backend/src/test/resources/application-test.yml` (perfil
`test`, ativado pelo surefire). O alvo `make test` **não** carrega o `.env` de propósito:
uma variável `SPRING_DATASOURCE_*` exportada no ambiente venceria o YAML e faria o teste
rodar contra o banco de desenvolvimento `scipath` — o alvo limpa essas variáveis antes de
chamar o Maven.

### Uso

```bash
infra/db-setup.sh          # uma vez: banco de desenvolvimento + .env
infra/db-test-setup.sh     # uma vez (ou sempre que quiser o banco de teste zerado)
make test                  # roda a suíte contra scipath_test, sem precisar do .env
```

`db-test-setup.sh` é **idempotente** e **recria** `scipath_test` do zero (é banco de teste;
recriá-lo é o que permite à suíte provar que as migrações são aplicadas a partir de schema
vazio). Ao rodar, ele guarda a primeira cópia do `pg_hba` em `pg_hba.conf.scipath.orig`.

Reversão da preparação: apagar as duas linhas entre os marcadores `# BEGIN scipath-test` e
`# END scipath-test` no `pg_hba`, recarregar (`SELECT pg_reload_conf();`) e
`DROP DATABASE scipath_test; DROP ROLE scipath_test;` como superusuário.

A role de desenvolvimento continua com acesso ao banco de teste: os scripts de evidência
dos cards E1.2/E1.3 usam `SPRING_DATASOURCE_URL_TEST` do `.env` e não podem quebrar.

## Quem é o dono do banco/esquema de teste — e por quê (card E1.12)

| Objeto | Dono | Por quê |
|---|---|---|
| banco `scipath_test` | role `scipath_test` | é assim que `db-test-setup.sh` o cria (`CREATE DATABASE ... OWNER`). |
| esquema `public` | `pg_database_owner` = a role dona do banco (`scipath_test`) | no PostgreSQL 15+ o esquema `public` pertence ao dono do banco, não ao superusuário. |
| tabelas, sequências, histórico do Flyway | **role de teste (`scipath_test`) ou role de dev (`scipath`) — tanto faz** | há duas formas de migrar este banco e cada uma usa uma role; a posse é deliberadamente irrelevante (abaixo). |

**As duas formas de migrar.** A suíte (`make test`) sobe o contexto Spring com a role de
teste (`application-test.yml`) e o Flyway da aplicação cria as tabelas — dono
`scipath_test`. Já `make db:migrate:test` e os scripts de evidência dos cards E1.2/E1.3/E1.2a
migram pelo plugin do Maven usando `SPRING_DATASOURCE_USERNAME` do `.env`, isto é a role de
dev `scipath` — dono `scipath`.

**Por que não se escolhe um dono só.** `DROP`/`ALTER` de tabela são privilégios do dono e não
se concedem; os scripts de evidência do E1.2 derrubam e recriam as tabelas do `scipath_test`
como role de dev, e a suíte precisa do banco utilizável como role de teste. Em vez de proibir
uma das duas formas (o que quebraria a outra), `db-test-setup.sh` fecha o acesso nos **dois
sentidos** com `ALTER DEFAULT PRIVILEGES` — objetos criados pela role de teste são concedidos
à role de dev (desde o E1.5) e objetos criados pela role de dev são concedidos à role de teste
(desde o E1.12). Resultado: migrar por qualquer uma das roles deixa o banco utilizável pela
outra, e o achado do E1.8 (`permission denied for table category` depois de
`make db:migrate:test`) não se reproduz.

Consequência prática: quem quer o banco de teste num estado conhecido roda
`infra/db-test-setup.sh`; depois disso `make test` e `make db:migrate:test` são
intercambiáveis na ordem, sem deixar o banco meio-migrado "inutilizável" para a suíte.
