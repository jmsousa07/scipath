# infra/ — ambiente local do SciPath

Scripts de ambiente. Nada aqui é produção: a máquina local não tem Docker (ADR-0007), o
PostgreSQL 16 é nativo em `127.0.0.1:5432`.

| Script | Para que serve |
|---|---|
| `db-setup.sh` | cria a role e o banco de **desenvolvimento** (`scipath`) e escreve o `.env` na raiz do repositório **principal** com uma senha gerada (nunca impressa). Ver abaixo. |
| `db-test-setup.sh` | prepara o banco de **teste** (`scipath_test`) para a suíte de backend, **sem senha versionada**. Ver abaixo. |
| `seed-dev.sql` | insere o **dado de exemplo** (autores e materiais fictícios) no banco de **desenvolvimento**, para as verificações da Etapa 2 (E2.2–E2.5, E2.11). **Não é conteúdo do produto** — ver abaixo. |

## Onde o `.env` é gravado (card E2.6-f2)

`db-setup.sh` escreve o `.env` na raiz do **repositório principal** — o mesmo cálculo do
`Makefile` (`git rev-parse --path-format=absolute --git-common-dir` + `/..`), não a raiz de
onde ele foi chamado. Como cada card roda num git worktree, gravar ali (comportamento antigo)
rotacionava a senha da role e deixava o `.env` da raiz principal estale: um worktree passa a
usar credenciais diferentes sem aviso, e a falha só aparece no card seguinte. Fora de um
repositório git, o script cai para a própria raiz.

O `Makefile` procura o `.env` de onde o `make` roda **antes** do da raiz principal
(`ENV_FILE`). Se existir um `.env` de worktree deixado pelo uso antigo, o script **avisa**,
sem apagar nada em silêncio — para usar o `.env` da raiz principal, remova o do worktree.

## Seed de desenvolvimento (card E2.6)

```bash
make db:seed:dev   # insere o dado de exemplo no banco de desenvolvimento (scipath)
```

`infra/seed-dev.sql` cria **3 autores de exemplo e 8 materiais de exemplo** (6 `PUBLICADO`,
1 `RETIRADO`, 1 `EXCLUIDO`) para que listagem, filtros, busca, paginação e o 404 do detalhe
tenham o que mostrar nas verificações da Etapa 2. Cobre os 6 tipos de material, 6 categorias
e as 7 licenças, com `publicado_em` espalhado (a listagem pública ordena por data, DRS §12.2).

**É dado de desenvolvimento, não conteúdo do SciPath.** Não use em demonstração ao usuário
final: nada ali é publicação de alguém. O que impede que passe por material real:

- todo `slug` e todo e-mail é prefixado por `exemplo-` (o domínio `@exemplo.invalid` é
  reservado pelo RFC 2606 — não existe e não recebe correio) — **é o marcador**, sem coluna
  nova nem flag no schema (card E2.6, escopo 4);
- `nome_publico` e `titulo` começam com "Exemplo", para que print de tela também não se
  confunda com conteúdo de verdade;
- `afiliacao_declarada` diz em texto que não há vínculo real — o campo é declaração do autor
  e nunca pode ser lido como vínculo verificado (RN011, DRS §13.1);
- as `file_asset` são **só metadado**: `chave_interna` com prefixo zerado e `checksum` de
  `e` repetido são inconfundíveis, e o binário **não existe no storage** — o seed habilita
  listagem/filtro/busca, não download de arquivo de exemplo;
- as contas **não autenticam**: `senha_hash` é um bcrypt de formato válido com pré-imagem
  desconhecida e descartada, então nenhuma senha (nem a "óbvia") entra como um autor de
  exemplo (ADR-0003).

Onde roda: **só no banco local de desenvolvimento** (`scipath`, loopback). O alvo recusa o
banco de teste antes de abrir conexão (e recusa host que não seja loopback); o próprio
`.sql` recusa de novo — `psql -f infra/seed-dev.sql` na mão não escapa da guarda. Nunca em
`scipath_test` (a suíte de integração precisa de estado limpo) e nunca em produção
(ADR-0007: não há ambiente remoto).

**Idempotente:** rodar `make db:seed:dev` duas, três vezes seguidas não duplica nem falha —
os UUIDs vêm do banco e as referências são por chave natural (`e-mail`, `slug`,
`chave_interna`), com `ON CONFLICT DO NOTHING`; a seed semeia, não sobrescreve (como a V2).

**Reversível por `make db:reset`** (destrutivo, pede confirmação): o banco é recriado do
zero e as migrações reaplicadas, então o dado de exemplo desaparece sem resíduo. Não existe
alvo de "limpar só o seed" — apagar linha de exemplo pela metade seria uma segunda forma de
mexer nos dados, sem requisito.

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
make db:seed:dev           # opcional: dado de exemplo no banco de desenvolvimento
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
| tabelas, sequências, histórico do Flyway | **role de teste (`scipath_test`) ou role de dev (`scipath`) — tanto faz** | há duas formas de migrar este banco e cada uma usa uma role; a posse é deliberadamente irrelevante (abaixo, e §E1.5-f1 para o caso do DDL). |

**As duas formas de migrar.** A suíte (`make test`) sobe o contexto Spring com a role de
teste (`application-test.yml`) e o Flyway da aplicação cria as tabelas — dono
`scipath_test`. Já `make db:migrate:test` e os scripts de evidência dos cards E1.2/E1.3/E1.2a
migram pelo plugin do Maven usando `SPRING_DATASOURCE_USERNAME` do `.env`, isto é a role de
dev `scipath` — dono `scipath`.

**Por que não se escolhe um dono só.** `DROP`/`ALTER` de tabela são privilégios do dono e não
se concedem; os scripts de evidência do E1.2 derrubam e recriam as tabelas do `scipath_test`
como role de dev, e a suíte precisa do banco utilizável como role de teste. Em vez de proibir
uma das duas formas (o que quebraria a outra), `db-test-setup.sh` faz o banco de teste ter
**uma dona só** — a role de teste — e faz a role de dev **atuar como ela neste banco**:

| Camada | O que garante | Desde |
|---|---|---|
| `ALTER DEFAULT PRIVILEGES` (dois sentidos) | **DML** em objeto criado pela outra role: SELECT/INSERT/UPDATE/DELETE/TRUNCATE + USAGE em sequência | E1.5 (teste → dev) e E1.12 (dev → teste) |
| `GRANT` da role de teste à role de dev | a role de dev é **membro** da dona (pré-requisito do `SET role` abaixo) | E1.5-f1 |
| `ALTER ROLE … IN DATABASE … SET role` | **DDL e DML**: toda sessão da role de dev **no banco de teste** atua como a role de teste, então o que ela cria já nasce com a dona certa e ela pode derrubar/alterar qualquer objeto de lá | E1.5-f1 |

Resultado: migrar por qualquer uma das roles deixa o banco utilizável pela outra, e os dois
achados do harness não se reproduzem — nem o `permission denied for table category` do E1.8
(DML), nem o `must be owner of relation material` do E1.5-f1 (DDL).

Consequência prática: quem quer o banco de teste num estado conhecido roda
`infra/db-test-setup.sh`; depois disso `make test` e `make db:migrate:test` são
intercambiáveis na ordem, sem deixar o banco meio-migrado "inutilizável" para a suíte.

**O harness não declara mais esse achado de memória (card E5.10-f1).** `infra/qa-harness.sh`
§4.4.1 mede, em cada execução, o dono da tabela `category` e — **tabela a tabela** — quais
objetos de `public` ficaram fora do alcance da role da suíte (correlação por objeto no
catálogo, nunca um nome agregado: um estado **misto**, em que parte das tabelas tem a role da
suíte e parte não, é declarado, não suprimido). §4.4.2 tem **três** desfechos, e a decisão mora
numa função única (`estado_achado_e18`, extraída pela evidência e exercitada com entradas
sintéticas):

| Desfecho | Quando | O que o harness faz |
|---|---|---|
| `REPRODUZIDO` | a medição prova a condição (objeto fora do dono da suíte **e** suíte sem acesso a ele) | registra o ACHADO do E1.8 |
| `NAO_REPRODUZIDO` | a medição **foi feita** e a condição não ocorreu | imprime o resultado observado, sem achado |
| `MEDIÇÃO INDISPONÍVEL` | alguma consulta falhou ou voltou vazia | imprime a observação crua do psql, não declara achado e **não** afirma causa |

Medição ausente não é prova de ausência: é indisponibilidade, e o instrumento a nomeia como tal.
Um instrumento que afirma o que não mediu é defeito do instrumento, não achado do produto.

## O dono do objeto decide DDL: uma role só dona o banco de teste (card E1.5-f1)

O `ALTER DEFAULT PRIVILEGES` do E1.12 fechou o acesso DML, e com isso o achado do E1.8 parou de
acontecer. Não bastou: a Etapa 4 acrescentou testes de integração que fazem **DDL** no banco de
teste para injetar falha de gravação (RN008) — `RollbackDaPublicacaoIntegrationTest` e
`ColetaDeOrfaosIntegrationTest` criam uma FUNÇÃO e um GATILHO sobre `material` e `file_asset`.
Criar e derrubar gatilho, `DROP TABLE` e `ALTER TABLE` checam a **POSSE** do objeto no
PostgreSQL (`pg_class_ownercheck` → `has_privs_of_role`), e posse **não se concede por GRANT**.

Estado reproduzido (2026-10-31, nesta máquina): com o banco migrado por `make db:migrate:test`
(dono `scipath`), `make test` termina em `BUILD FAILURE` com `Tests run: 228, Failures: 2,
Errors: 11`, todos derivados de `ERROR: must be owner of relation material` /
`... of relation file_asset`. No sentido inverso, o harness (`infra/qa-harness.sh` §4.2) derruba
as tabelas como role de dev e elas pertencem à role de teste: sai
`NAO ATENDE [restantes=9 remigrate=0]`.

**O caminho da associação mútua não existe.** A saída "cada role é membro da outra" é recusada
pelo próprio PostgreSQL: o ciclo em `pg_auth_members` é rejeitado com
`ERROR: role "scipath" is a member of role "scipath_test"` — medido em laboratório com
PostgreSQL 16.13 descartável (`docs/qa/e1-5-f1-mecanismo-*.log`), depois de a primeira direção
ser aceita. Só há **um** sentido possível de associação, e portanto só uma role pode ser
"estranha" à posse dos objetos.

**Decisão (registrada): uma role só dona o que existe em `scipath_test` — a role de teste.** A
role de dev continua **autenticando** com a senha do `.env` (é o que preserva a evidência do
E1.2b: senha fora do argv do `mvnw`), mas toda sessão dela **neste banco** atua como a role de
teste, em duas linhas do passo 4 de `db-test-setup.sh`:

```sql
GRANT scipath_test TO scipath;                              -- membro da dona (pré-requisito)
ALTER ROLE scipath IN DATABASE scipath_test SET role TO scipath_test;  -- SET ROLE automático, só neste banco
```

Efeito, verificado em laboratório: a sessão da role de dev no banco de teste reporta
`session_user=scipath` e `current_user=scipath_test`; uma tabela criada por ela ali **nasce com
dona `scipath_test`**; e ela derruba tabela criada pela role de teste (é a dona), enquanto a
suíte — que conecta como `scipath_test` — segue dona e capaz. O banco de desenvolvimento
(`scipath`) **não** é afetado: o `SET` é por banco (`pg_db_role_setting`), e é isso que a
contraprova do laboratório mostra (`session_user=scipath current_user=scipath` lá).

**Por que isto e não outra coisa.**

- *Alternativa descartada: um sentido de associação só.* Dar a uma role os poderes da outra
  resolve um lado e deixa o outro exatamente como está: com `scipath_test` membro de `scipath`,
  o harness §4.2 passa a funcionar e a suíte continua morrendo em `must be owner of relation
  material` quando as tabelas vierem da role de dev (critério 1 do card).
- *Alternativa descartada: proibir uma das formas de migrar* (revogar `CREATE` no schema, para
  o banco de teste só aceitar a role de teste). Transforma a armadilha silenciosa num erro
  visível, mas quebra os scripts de evidência do E1.2/E1.3 e o próprio `make db:migrate:test`,
  que o card exige que continuem funcionando.
- *`REASSIGN OWNED` no alvo `db:migrate:test`* normalizaria só o caminho que passa pelo alvo: os
  scripts de evidência que falam Flyway direto com a role de dev (`docs/qa/e1-2-evidencia.sh`,
  `e1-3-evidencia.sh`) continuariam envenenando o banco. O `SET role` por banco é o mesmo
  conserto no lugar onde ele vale para qualquer caminho — a própria conexão.

**Custo aceito.** A role de dev pode `SET ROLE scipath_test` (é o mecanismo) e, no banco de
teste, os comandos dela aparecem como `current_user = scipath_test` — quem depurar pela role de
dev precisa saber disso. Em ambiente local não se abre nada que já não estivesse aberto: a role
de teste não tem senha e entra por `trust` de loopback (E1.5). Não há essa preparação em
produção (sem hospedagem, ADR-0007) e nada disso é credencial versionada (RNF012).

**Como verificar depois de mudar.** `infra/db-test-setup.sh` é pré-requisito (é ele que aplica as
duas linhas) e exige superusuário — `sudo -u postgres`, que este script já usa. Sem ele, a role
de dev segue dona dos próprios objetos e a suíte volta a falhar com
`must be owner of relation material` sempre que as tabelas tiverem sido criadas por ela.
`docs/qa/e1-5-f1-evidencia.sh` roda as duas ordens e o harness a partir daí.

## Collation do banco não decide a ordem publicada (card E1.13, ADR-0010)

Os dois bancos nascem com a collation do `template1` da máquina: `db-setup.sh` e
`db-test-setup.sh` **não** declaram `LC_COLLATE`. Isso é **deliberado**, não pendência — a
`datcollate` do banco é livre porque a ordem das listagens é fixada **no SQL**, não herdada:
as consultas que ordenam texto usam `COLLATE "C"` (ordem de ponto de código), na taxonomia
(`ORDER BY nome COLLATE "C", slug COLLATE "C"`) e na listagem `ordem=titulo`
(`ORDER BY m.titulo COLLATE "C" ASC, m.id DESC`).

Medido em 2026-10-31 num clone com `LANG=en_US.UTF-8` (`datcollate` `en_US.UTF-8`): sem a
cláusula, `make test` reprovava em
`TaxonomiaControllerIntegrationTest.ordenacaoPorNomeEEstavelEntreChamadasEPaginas` — sob
`en_US.UTF-8` a collation ignora espaço e pontuação, e `"CC0 1.0 (…)"` subia na frente de
`"CC BY 4.0"`. O mesmo clone numa máquina `C.UTF-8` passava: a suíte dependia do locale do
servidor, não do código. Com a cláusula, `make test` é verde nas duas collations (evidência
em `docs/qa/e1-13-evidencia.sh`).

Não é preciso recriar nada para "consertar" o locale. Quem **quiser** um banco `C.UTF-8`
(por exemplo para reproduzir a VPS ao pé da letra) cria com collation explícita:

```bash
sudo -u postgres psql -c "CREATE DATABASE scipath_test TEMPLATE template0 \
  LC_COLLATE 'C.UTF-8' LC_CTYPE 'C.UTF-8' OWNER scipath_test;"
```

e replica os `GRANT` de `db-test-setup.sh` (a partir do passo 4). Não é exigência do produto:
o resultado das listagens publicadas é o mesmo nos dois casos — é justamente o que o
ADR-0010 fixou. Um `ORDER BY` novo sobre coluna de texto deve declarar a mesma collation,
senão a dependência volta em silêncio (o `ORDER BY` de data/uuid não precisa: `timestamptz` e
`uuid` já comparam byte a byte).

