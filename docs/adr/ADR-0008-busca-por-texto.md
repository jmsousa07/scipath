# ADR-0008 — Busca por texto: escopo, dobra de acento e índice

- Data: 2026-10-16
- Status: **aceita** (decisão técnica; o comportamento de borda está fixado em teste)
- Decisores: implementação (perfil `backend`) · revisão do orquestrador
- Resolve: §20.2 ("o MVP prioriza busca lexical simples") e §12.1 (escopo e previsibilidade da busca)
- Relaciona: RF003, RN013, RNF020, DRS §12.1, §12.2, §20.2, §21.5; contrato v0.3
  (`components/parameters/FiltroBusca`); ADR-0002 (o que está fora do MVP); ADR-0006 (taxonomia
  de referência)

## Contexto

O contrato v0.3 já declara o parâmetro `q` (`GET /api/materiais`) com promessas explícitas:
busca lexical sobre **título e descrição**, **resistente a acento e caixa** ("matemática" e
"matematica" casam o mesmo conjunto), **termo ausente ou vazio não é erro**, **caracteres
especiais tratados como texto, nunca como operador**, **sem ordenação nem promessa de
relevância**, teto de 200 caracteres. O card E2.3 pede a implementação e o §21.5 proíbe
"escolher uma arquitetura definitiva sem registrar os motivos e os trade-offs" — daí este ADR.

Três fatos medidos nesta máquina (PostgreSQL 16.15) delimitam a decisão. Sem eles a escolha
seria opinião:

```
to_tsvector('portuguese', 'matemática') -> 'matemát'
to_tsvector('portuguese', 'matematica') -> 'matemat'
to_tsvector('portuguese', 'ordenação')  -> 'orden'
to_tsvector('portuguese', 'ordenacao')  -> 'ordenaca'
```

1. A configuração `portuguese` de fábrica **não dobra acento** — e o acento ainda contamina o
   radical produzido pelo stem. Implementar sobre ela quebraria a promessa central do contrato
   e a RN013 (busca consistente).
2. As funções que a busca usa são **imutáveis** (`to_tsvector(regconfig, text)`, `translate`,
   `plainto_tsquery(regconfig, text)`, todos `provolatile = 'i'`), então o predicado pode ser
   indexado como índice de expressão — não é preciso coluna derivada nem trigger.
3. `CREATE EXTENSION unaccent` **falha** para a role de desenvolvimento no banco de teste:

```
ERROR: permission denied to create extension "unaccent"
HINT:  Must have CREATE privilege on current database to create this extension.
```

`infra/db-test-setup.sh` dá à role de dev `CONNECT`/`TEMPORARY` no `scipath_test`, e o dono do
banco é `scipath_test`. Migrar pela role de dev é um caminho documentado
(`make db:migrate:test`, README das migrações) e o card E1.12 o tornou equivalente ao caminho
da suíte. Uma migração que exigisse a extensão quebraria um dos dois.

## Decisão

**Full-text do PostgreSQL sobre título e descrição, com a configuração `portuguese` nomeada
explicitamente, dobra de acento própria e índice GIN de expressão** — migração
`V3__busca_texto_material.sql`, predicado em `MateriaisRepositorio`.

1. **Predicado:** `to_tsvector('portuguese', <dobra(titulo || ' ' || descricao)>) @@
   plainto_tsquery('portuguese', <dobra(:q)>)`.
2. **`plainto_tsquery`, nunca `to_tsquery`.** O primeiro trata a entrada como texto; o segundo
   como expressão, em que `&`, `|`, `!`, `:` e parênteses são operadores. O contrato e o §12.1
   exigem que caracteres especiais sejam texto: `q=zzz | matematica` é uma conjunção de três
   palavras (nenhum resultado), não uma disjunção. É também a barreira que impede um `q` de
   terceiro de virar sintaxe de busca.
3. **Dobra de acento por `translate()` explícito**, na mesma expressão do índice, sem chamar
   `lower()`: o mapa cobre as duas caixas (maiúscula e minúscula) e o que sobra é ASCII, que o
   analisador do `tsvector` rebaixa de forma determinística. `lower()` depende da collation do
   banco (num banco `LC_CTYPE=C` ele não dobra "Á") — e o resultado da busca não pode depender
   de configuração da máquina.
4. **Índice GIN de expressão** sobre `titulo || ' ' || descricao`, chamado `material_busca_idx`.
   Índice e consulta têm de ser a mesma expressão: é o que faz o planejador reconhecer o índice
   (provado por `EXPLAIN` na evidência do card). Uma coluna gerada evitaria essa duplicação ao
   custo de `material` carregar um dado que só a busca usa, mais um passo de escrita em toda
   publicação — volume de escrita a mais sem requisito.
5. **Escopo: título e descrição.** Nome público do autor e categoria ficam de fora: o §12.1 os
   admite "se for compatível com a implementação escolhida", e o contrato não os promete.
   Publicar escopo maior que o prometido é exatamente o que o §21.5 chama de afirmação sem
   lastro.
6. **Sem ordenação por relevância.** O resultado sai na ordenação pedida por `ordem` — a mesma
   ordenação total e determinística do E2.2. Não existe `ts_rank` na consulta: o §12.2 proíbe
   simular relevância sem algoritmo correspondente.
7. **`q` vazio é ausência de busca**, não erro nem 400: a listagem padrão volta inteira.
8. **`q` acima de 200 caracteres é 400**, na forma única de erro do contrato. Cortar em silêncio
   mudaria o resultado sem o cliente saber — mesma decisão já tomada para `size` (RNF021).

## Consequências

- **Positivas:** a promessa de acento e caixa passa a ser verdadeira e verificável por teste;
  a busca não depende de extensão, de collation nem de `default_text_search_config`; nenhum
  requisito de infraestrutura novo (o `postgresql-contrib` não passa a ser obrigatório);
  o índice é criado pela migração, dentro do repositório (RNF028).
- **Negativas e limitações, registradas:**
  - **Radical, não sinônimo.** O stem casa variações de forma ("ordenação"/"ordenacao"), não
    significados. "algoritmo" não acha "método". Sinônimos estão fora do MVP (§20.2).
  - **Termo só de palavras de ligação devolve lista vazia.** `de`, `a`, `para` são stopwords da
    configuração: o `tsquery` sai vazio e `@@` com consulta vazia é falso. Não há exceção —
    um termo sem lexema não é termo de busca, e o estado "nenhum resultado" é o que o contrato
    já define para não-encontrado. Está fixado em teste para não virar surpresa.
  - **Sem destaque de trecho (highlight) e sem prefixo.** `matem` não acha "matemática"; a
    busca é por palavra. Prefixo exigiria `to_tsquery` com `:*` (e portanto entrada interpretada
    como expressão) ou trigrama — os dois descartados abaixo.
  - **O mapa de acento é do português.** Título em outro idioma com acento fora do mapa (por
    exemplo `ő`, `ß`) não é dobrado. O §20.2 fixa o idioma inicial em português brasileiro.
  - **A duplicação da expressão entre migração e consulta é um acoplamento.** Se um lado mudar
    sem o outro, o índice deixa de ser usado — o planejador cai em varredura sequencial sem
    erro, e por isso o `EXPLAIN` é evidência obrigatória, não formalidade.
  - **Manter o índice custa escrita.** Cada publicação/atualização recalcula o vetor de duas
    colunas. Custo aceito: a leitura é o caminho quente do produto, a escrita de material é
    rara.

## Alternativas descartadas

- **`portuguese` de fábrica, sem dobra de acento.** Descartada por medição: quebra a promessa do
  contrato e a RN013 (ver Contexto, fato 1).
- **Extensão `unaccent` + configuração própria (`COPY = portuguese` com `unaccent, portuguese_stem`).**
  Funciona (medido: `to_tsvector('portuguese', translate(...))` equivalente a `unaccent`,
  e `unaccent` é extensão *trusted* no PostgreSQL 16), mas: (a) `CREATE EXTENSION` exige CREATE
  no banco — quebra `make db:migrate:test` e o invariante do E1.12; (b) torna o
  `postgresql-contrib` dependência de implantação, decisão que é do ADR-0007, não deste card;
  (c) o mapa de acentos passa a ser arquivo do sistema (`unaccent.rules`), fora do repositório —
  o resultado da busca mudaria com o pacote do sistema operacional, sem mudança no código.
- **`ILIKE '%termo%'`.** Só é honesto com índice de trigrama; sem ele, varredura sequencial em
  toda busca (contraria RNF020). Além disso não tem noção de palavra (casa dentro de palavra),
  não radicaliza e **também precisaria da mesma dobra de acento** — ou seja, herdaria o problema
  em vez de resolvê-lo.
- **`pg_trgm` + similaridade de trigrama.** É outra extensão (mesmos problemas de (a) e (b)), e
  a similaridade casa por proximidade de caracteres, não por palavra: o §12.1 pede busca
  "lexical, simples e previsível", não casamento difuso que devolve resultado inesperado.
- **`to_tsquery` com a entrada do usuário.** Permitiria operadores (`&`, `|`, `:*`) e violaria
  a promessa de tratar caracteres especiais como texto (§12.1), além de abrir a busca para
  sintaxe controlada por terceiro.
- **Coluna gerada `tsvector` em `material`.** Evita repetir a expressão na consulta, mas
  adiciona coluna e custo de escrita a uma tabela cujo caminho crítico é a leitura. Fica
  registrada como opção se algum dia a expressão duplicada causar erro real.
- **Índice parcial (`WHERE estado_publicacao = 'PUBLICADO'`).** Reduz o índice sem mudar
  semântica, mas depende de o predicado ser reconhecido em toda consulta futura. Ganho pequeno,
  risco silencioso: se valer, entra como migração aditiva com evidência própria.
- **Índice separado por título e por descrição.** Duas buscas a combinar em `OR` — perde a
  coesão do documento (um termo no título e outro na descrição deixariam de ser encontrados
  juntos) e não reduz nada.
- **Elasticsearch/serviço externo de busca.** Fora do escopo do MVP (§20.2), sem requisito de
  infraestrutura distribuída (§12.1) e desproporcional a 954 MB de RAM (ADR-0007, AGENTS.md).
- **Relevância, sinônimos e busca semântica.** Explicitamente fora do MVP (§20.2, §21.5) — e o
  contrato não publica o que o produto não calcula.

## Rastreabilidade

- **Requisitos afetados:** RF003, RN013, RNF020, §12.1, §12.2, §20.2, §21.5; RNF004 (vazio = 200).
- **Impactos:** migração `V3__busca_texto_material.sql`; `MateriaisController` (parâmetro `q` com
  teto), `MateriaisService` (termo vazio é ausência de busca) e `MateriaisRepositorio` (predicado
  e dobra); testes de integração da busca; `MateriaisBuscaDobraTest` (teste de unidade que
  impede consulta e índice de divergirem sem ninguém perceber); `docs/qa/` (evidência com
  `EXPLAIN`); contrato v0.3 (o `q` já estava declarado pelo card E2.1 — este ADR fixa o
  comportamento).
- **Pendente:** nada que bloqueie. Um índice parcial e a coluna gerada ficam como opções
  registradas, para qualquer card que apresente a medição que as justifique.
