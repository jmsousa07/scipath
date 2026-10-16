# infra/ — ambiente local do SciPath

Scripts de ambiente. Nada aqui é produção: a máquina local não tem Docker (ADR-0007), o
PostgreSQL 16 é nativo em `127.0.0.1:5432`.

| Script | Para que serve |
|---|---|
| `db-setup.sh` | cria a role e o banco de **desenvolvimento** (`scipath`) e escreve o `.env` com uma senha gerada (nunca impressa). |
| `db-test-setup.sh` | prepara o banco de **teste** (`scipath_test`) para a suíte de backend, **sem senha versionada**. Ver abaixo. |
| `seed-dev.sql` | insere o **dado de exemplo** (autores e materiais fictícios) no banco de **desenvolvimento**, para as verificações da Etapa 2 (E2.2–E2.5, E2.11). **Não é conteúdo do produto** — ver abaixo. |

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