# ADR-0010 — Ordenação de texto é explícita (`COLLATE "C"`), não herdada da collation do banco

- Data: 2026-10-31
- Status: **aceita** — decisão de implementação do card E1.13; ratificação do dono pendente
  (ver "Rastreabilidade")
- Decisores: implementação (perfil `backend`) · revisão do orquestrador
- Origem: card **E1.13**, consequência do critério de reprodutibilidade do card **E1.5**
  ("reproduzível por outra pessoa, num clone limpo") e da mesma família do achado **E4.8-f1**
  (o locale do processo decidia o resultado da suíte)
- Relaciona: RN009, RNF021, DRS §12.2, §17.5, §21.5; contrato v0.5 (`Ordenação estável por
  nome`, `OrdemMateriais`); ADR-0008 (que já recusa `lower()` por depender da collation);
  ADR-0006 (taxonomia de referência)

## Contexto

O gate `make test` reprovava em toda máquina cujo locale não fosse `C.UTF-8`. Medido em
2026-10-31, neste clone (`$HOME/Documents/scipath`, `LANG=en_US.UTF-8`):

```
Tests run: 227, Failures: 1, Errors: 0
TaxonomiaControllerIntegrationTest.ordenacaoPorNomeEEstavelEntreChamadasEPaginas:187
[nomes de /api/licencas em ordem] group is not sorted because element 0:
  "CC0 1.0 (domínio público)" is not less or equal than element 1: "CC BY 4.0"
```

O `ORDER BY nome` de `TaxonomiaRepositorio` não declarava collation, então a ordem saía do
**`datcollate` do banco**, herdado do `template1` no `CREATE DATABASE` — e `infra/db-setup.sh`
e `infra/db-test-setup.sh` não fixam locale:

| Máquina | `LANG` | `datcollate` de `scipath_test` | resultado |
|---|---|---|---|
| VPS original | `C.UTF-8` | `C.UTF-8` | verde |
| clone novo (laptop) | `en_US.UTF-8` | `en_US.UTF-8` | **1 falha** |

Fato 1 — a diferença é o *tratamento de espaço e pontuação*, não o acento. Medido no banco
`en_US.UTF-8` deste clone, sobre as 7 licenças do seed:

```
sem COLLATE (collation do banco, en_US.UTF-8)      com COLLATE "C"
  CC0 1.0 (domínio público)                          CC BY 4.0
  CC BY 4.0                                          CC BY-NC 4.0
  ...                                                CC BY-NC-SA 4.0
  Todos os direitos reservados                       CC BY-ND 4.0
                                                     CC BY-SA 4.0
                                                     CC0 1.0 (domínio público)
                                                     Todos os direitos reservados
```

Sob `en_US.UTF-8` o espaço é "ignorado" no primeiro nível de comparação, e `CC0` sobe na
frente de `CC BY`; sob `C` a comparação é byte a byte e `' '` (0x20) < `'0'` (0x30). Sob
`C.UTF-8` a ordem **já era** byte a byte, então a suíte passava — por acidente do locale da
máquina, não por decisão do código.

Fato 2 — a suíte (e o contrato, implicitamente) assume ordem de ponto de código: o teste usa
`assertThat(nomes).isSorted()` do AssertJ, que compara `String` por ponto de código, e o
próprio teste documentava a premissa como fato do ambiente ("A base é C.UTF-8").

Fato 3 — o padrão do produto tem duas implementações de ordenação por texto, ambas herdando a
collation do banco: `ORDER BY nome, slug` (taxonomia, os três endpoints de referência) e
`ORDER BY m.titulo ASC, m.id DESC` (listagem de materiais com `ordem=titulo`). Só a segunda é
chave de conteúdo publicado por terceiro — ou seja, o defeito também decidia a ordem que o
visitante vê.

## Decisão

**Toda chave de ordenação de texto declara `COLLATE "C"` na própria consulta; a collation do
banco deixa de influenciar a ordem publicada.** No código:

```java
// TaxonomiaRepositorio.ORDEM — os três endpoints de referência (RF018)
" ORDER BY nome COLLATE \"C\", slug COLLATE \"C\""

// MateriaisRepositorio.ordenacaoDe — ordem=titulo (RN013)
" ORDER BY m.titulo COLLATE \"C\" ASC, m.id DESC\n"
```

1. **`"C"`, e não `"C.UTF-8"`.** `C` compara bytes; numa base UTF-8 a ordem de bytes é a ordem
   de pontos de código, que é exatamente o que a asserção da suíte e a leitura "A–Z" do
   contrato significam. `C.UTF-8` exigiria que a *collation existisse* no servidor, o que
   volta a depender do ambiente que se quer eliminar.
2. **A collation é declarada na consulta, não na coluna.** Uma migração
   (`ALTER TABLE … ALTER COLUMN nome TYPE text COLLATE "C"`) reescreve a tabela, altera a
   semântica de **toda** comparação e `LIKE` da coluna — inclusive a de código que não ordena
   — e amarra o schema ao comportamento; e o critério 4 do card proíbe mexer em migração para
   alterar dado existente. A cláusula na consulta é local, reversível e visível junto do
   `ORDER BY` que ela governa.
3. **Collation explícita também no desempate.** `slug` (e qualquer chave secundária de texto)
   leva `COLLATE "C"`: o desempate existe para decidir a página quando os nomes empatam, e
   com collation herdada ele reintroduziria a dependência do ambiente justo no caso em que ele
   é a única coisa que decide.
4. **Chave não textual não recebe `COLLATE`.** `publicado_em` (`timestamptz`) e `id` (`uuid`)
   têm ordem própria e já são byte a byte: `RECENTES` fica como está, e é por isso que o
   índice `material_estado_data_idx` continua servindo a consulta padrão sem passo de
   ordenação. `ColetaDeOrfaosRepositorio` (`ORDER BY f.enviado_em, f.id`) também não muda.
5. **O ambiente continua livre.** O projeto **não** passa a impor `C.UTF-8` no `CREATE
   DATABASE` nem no locale da máquina: seria consertar o ambiente e deixar o código dependente
   dele — a recorrência voltaria em cada clone novo, que é o defeito que este ADR fecha. O
   banco pode nascer no locale que o `template1` da máquina tiver; a ordem publicada não muda
   por isso.
6. **Nenhuma migração nova.** O card tem escopo de código; a decisão não altera schema, dado
   nem contrato em sentido estrito (ver abaixo).

## Consequências

- **Positivas:** `make test` passa a ser verde com o banco em `en_US.UTF-8`, `C.UTF-8` ou
  qualquer outro locale — medido nos dois (evidência do E1.13); a ordem publicada pelos
  endpoints de referência e por `ordem=titulo` passa a ser uma propriedade do código, não da
  máquina; a promessa "ordenado por nome" do contrato deixa de ser ambígua.
- **Negativas e limitações, registradas:**
  - **A ordem deixa de ser "alfabética humana".** `C` ordena `'Z' < 'a'` (maiúscula antes de
    minúscula) e coloca acentuadas depois de `z`. Hoje não se vê: nomes de taxonomia são
    capitalizados e de conteúdo tipicamente também, e a lista de referência é fixa. Se algum dia
    o produto quiser ordem "de dicionário" (que ignora caixa e acento), isso é uma **nova**
    decisão, com grupo de collation explícito — e não um efeito colateral de qual servidor rodou
    a migração.
  - **Um `ORDER BY` novo pode esquecer o `COLLATE`.** A cláusula é por consulta, então não há
    um único ponto que a garanta. Mitigação: o teste de integração da taxonomia usa
    `isSorted()` por ponto de código e reprova em qualquer locale que não seja byte a byte
    (é literalmente o que aconteceu aqui, e foi assim que o defeito apareceu); um teste de
    ordem para `ordem=titulo` com títulos que espaçam/pontuam deve seguir a mesma forma.
  - **A ordenação perde o uso de índice com collation `C` em coluna de outra collation.** Isso
    não é hipótese com os índices que existem hoje — é o que acontece. As três listas de
    referência têm índice sobre a chave de ordenação: `category_listagem_idx`,
    `material_type_listagem_idx` e `license_listagem_idx`, todos `(ativo, nome)`, na migração
    `V1__modelo_mvp.sql` (linhas 82-84). Eles servem a consulta **sem** a cláusula
    (`ORDER BY nome, slug` → `Limit` → `Incremental Sort` → `Index Scan using … (Index Cond:
    ativo = true)`) e deixam de servir a **ordem** com ela (`ORDER BY nome COLLATE "C", slug
    COLLATE "C"` → `Limit` → `Sort` → `Seq Scan … (Filter: ativo)`); o filtro por `ativo`
    continua sendo servido nos dois casos. Medido em 2026-10-31 (tabela **temporária** de 20.001
    linhas, índice `(ativo, nome)`, colunas `text` na collation do banco `en_US.UTF-8`,
    `ANALYZE`) — reproduzível pelo SQL abaixo, que não cria objeto nenhum no schema:

    ```sql
    CREATE TEMP TABLE t_nome (ativo boolean NOT NULL, nome text NOT NULL, slug text NOT NULL);
    INSERT INTO t_nome
    SELECT (g % 20 <> 0), 'nome ' || lpad(g::text, 5, '0'), 'slug-' || lpad(g::text, 5, '0')
    FROM generate_series(1, 20001) g;
    CREATE INDEX t_nome_idx ON t_nome (ativo, nome);
    ANALYZE t_nome;
    EXPLAIN SELECT slug FROM t_nome WHERE ativo ORDER BY nome, slug LIMIT 100;
    EXPLAIN SELECT slug FROM t_nome WHERE ativo
            ORDER BY nome COLLATE "C", slug COLLATE "C" LIMIT 100;
    ```

    Para reproduzir, cole o bloco acima em `psql -U scipath -d scipath` — o banco precisa estar
    na collation que se quer medir (aqui, `en_US.UTF-8`). O script de evidência
    `docs/qa/e1-13-f2-evidencia.sh` extrai **este** bloco do próprio ADR e o executa, de modo que
    o ADR e a medição registrada não podem divergir.

    Hoje o efeito é **imaterial**: as tabelas de referência têm 13/6/7 linhas e o cliente lê a
    lista inteira (`size` padrão 100 ≥ total), então `Seq Scan` + `Sort` é mais barato que
    qualquer índice. **Este ADR não cria índice nenhum**; se algum dia o volume pedir, é decisão
    nova, com medição. Para `titulo` de `material` a metade antiga da afirmação vale: não há
    índice sobre ele (o único de conteúdo é o GIN de busca, V3:59). E vale para o futuro, em
    `nome` ou em `titulo`: **um índice sobre coluna de texto tem de nascer com a mesma collation
    da cláusula**, senão o planejador deixa de reconhecê-lo (mesmo acoplamento já registrado no
    ADR-0008 para a expressão da busca).
- **Contrato:** as descrições de ordenação em `contracts/openapi.yaml` ganham a palavra
  "ponto de código" — é **esclarecimento**, não mudança incompatível (o contrato nunca
  prometeu ordem alfabética sensível a acento; prometia "ordenada por nome"), então **não**
  sobe versão. A fronteira de campos e parâmetros não muda.

## Alternativas descartadas

- **Recriar os bancos com `LC_COLLATE 'C.UTF-8'` e fixar o locale em `infra/db-*.sh`.** É a
  correção do ambiente, não a do código: desbloqueia a máquina de hoje e deixa o defeito
  inteiro para o próximo clone — o oposto do critério de reprodutibilidade do E1.5. Custo
  também real: exige `DROP DATABASE` nos scripts de todo mundo que já tem banco.
- **Trocar a asserção da suíte para comparar com a collation do banco.** Aceitaria o defeito
  como norma: a ordem que o visitante vê passaria a mudar de máquina e o contrato ficaria
  impossível de verificar de forma portável. Além disso, o `ORDER BY` também afeta
  `ordem=titulo`, que o teste da taxonomia nem toca.
- **`ALTER TABLE … COLLATE "C"` na migração.** Ver Decisão §2: reescreve a tabela, muda a
  semântica de todas as comparações da coluna e viola o critério 4 do card.
- **`COLLATE "und-x-icu"` / grupo de collation para ordem de dicionário.** Dependeria de ICU
  compilado no servidor (decisão de infraestrutura do ADR-0007) e muda a ordem **publicada**
  em relação à suíte atual — decisão de produto que ninguém pediu.
- **Fixar `COLLATE "C"` só na taxonomia (o mínimo do card).** Deixaria `ordem=titulo` —
  ordenação de conteúdo publicado por terceiro, a que o visitante usa — dependendo da máquina.
  O defeito é um só; corrigido pela metade, volta pelo outro endpoint.

## Rastreabilidade

- **Requisitos afetados:** RN009, RNF021 (ordem estável e paginação sem repetição), DRS §12.2
  (ordenação da listagem), §17.5 (evidência reproduzível), §21.5 (decisão registrada).
- **Impactos:** `TaxonomiaRepositorio.ORDEM`; `MateriaisRepositorio.ordenacaoDe`;
  comentário do teste `TaxonomiaControllerIntegrationTest`; descrições de ordenação em
  `contracts/openapi.yaml`; nota em `infra/README.md`; evidência em `docs/qa/`.
- **Pendente:** ratificação do dono (ADR aceito pela implementação, como o ADR-0008 registrou
  o seu desvio). Um teste de ordem para `ordem=titulo` com títulos que exerçam espaço e
  pontuação é recomendável — se virar card, nasce no mesmo lugar do teste da taxonomia.
