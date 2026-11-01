# ADR-0011 — Gerenciamento do material próprio (Etapa 5): superfície, estados e retenção

- Data: 2026-11-01
- Status: **aceita** — decisões de especificação do card F4 (`t_a8e75a19`), delegadas ao perfil `po`
  pelo dono (João Mateus); a pendência do §9 continua sendo decisão dele
- Relaciona: DRS §7.4, §15.2, §16 (CA007, CA008), §17.4, §18 Etapa 5; RF015–RF017, RF019; RN001,
  RN002, RN012, RN013; RNF004, RNF010, RNF013, RNF021, RNF025; ADR-0002, ADR-0003 (b), ADR-0005 §3;
  `docs/01-arquitetura.md` §2, §4.1, §5, §8

## Contexto

A aceitação do MVP (`docs/qa/p5-aceitacao-mvp.md`, card `t_f498b3aa`) mediu o **efeito** da retirada
— material em `RETIRADO` sai da listagem, da busca e do perfil do autor, e detalhe e arquivo passam
a responder 404 indistinguível — e declarou o que **não** existe: o **ato** (`PUT`/`PATCH`/`DELETE`
em `/materiais/{slug}` respondem 405). Sem operação de edição também não há como tentar o acesso
cruzado que CA007 exige. A Etapa 5 (DRS §18) é essa lacuna, e o DRS §16 só aceita o MVP com os itens
essenciais demonstrados.

RF015–RF017 fixam o comportamento, mas deixam aberto o que uma fronteira precisa decidir: **onde**
moram as rotas, **qual** a visão do dono, **como** o servidor recusa, e **o que** a exclusão faz com
o binário. É o que este ADR fecha, com o DRS como fonte #1.

## Decisões

### 1. A superfície é `/perfil/atual/materiais`, não `/materiais/proprios`

O material é do perfil da sessão (RN001) e já existe `/perfil/atual` na v0.4. O literal
`/materiais/proprios` colidiria com `GET /materiais/{slug}`: um material cujo slug fosse `proprios`
ficaria inalcançável publicamente — a rota privada capturaria o endereço dele. Preferir o caminho
que não cria ambiguidade é mais barato que confiar na precedência de padrão do framework.

Cinco rotas (todas com sessão; escrita exige `X-CSRF-Token`):

| Rota | Ato | Requisito |
|---|---|---|
| `GET /perfil/atual/materiais` | listar os próprios | RF015 |
| `GET /perfil/atual/materiais/{slug}` | ler um próprio (qualquer estado) | RF015 |
| `PUT /perfil/atual/materiais/{slug}` | editar metadados | RF016 |
| `PATCH /perfil/atual/materiais/{slug}` | retirar / republicar | RF017, RF019 |
| `DELETE /perfil/atual/materiais/{slug}` | excluir (lógica) | RF017 |

### 2. A visão do dono inclui os três estados; as ações só nos estados vivos

A lista do dono traz `PUBLICADO`, `RETIRADO` e `EXCLUIDO` — RF015 diz "incluindo estado de
publicação" e `PAGE_BLUEPRINTS.md` §12 nomeia os três. `RF017` ("deixa de aparecer em listagens e
páginas públicas") é sobre o **público**; esconder do dono o que ele acabou de excluir é que seria
desonesto. A leitura de um material próprio vale nos três estados; as **ações** valem em
`PUBLICADO` e `RETIRADO` — em `EXCLUIDO` não há ação nenhuma (estado terminal).

### 3. "Não é seu" e "não existe" respondem a mesma coisa: 404 indistinguível

Nas rotas de ação, material de outra conta responde **404 com o mesmo corpo** do 404 público
(RN002): um 403 confirmaria a existência de material `RETIRADO`/`EXCLUIDO` alheio e transformaria o
código de status em oráculo de enumeração — exatamente o que CA008 evita no caminho público.
`EXCLUIDO` (terminal) responde 404 nas rotas de ação pelo mesmo motivo, o que deixa uma assimetria
declarada e deliberada: a leitura mostra o histórico do dono, a ação exige material vivo. Custo
aceito: quem tenta editar com a conta errada lê "não encontrado"; o ganho é uma regra só, sem
matriz de estados × atos.

### 4. Editar é metadado, e o que não se edita aqui é decisão

Campos editáveis (os mesmos limites da publicação): `titulo`, `descricao`, `categoriaSlug`,
`tipoSlug`, `licencaSlug`. Fora do schema, e por isso imutáveis por esta rota:

- **`slug`** — é a URL canônica pública (RNF025, RN009); trocá-lo quebra link e indexação;
- **autoria/propriedade** — vem da sessão, nunca do corpo (RN001);
- **`estado`** — é o `PATCH` (ato distinto, com recusa própria);
- **arquivo** — trocar o binário mexe em validação, armazenamento, órfãos e retenção: é outra
  operação, sem requisito no MVP. Quem precisa de outro arquivo publica um novo e retira o antigo;
- **`declaracaoDireito`** — pertence ao ato de publicar (RF012) e não é re-perguntada: a autoria do
  registro não muda.

`atualizadoEm` sobe a cada escrita; `publicadoEm` **nunca** muda no MVP — inclusive na
republicação. Se republicar reescrevesse a data, retirar e republicar viraria manobra para subir ao
topo da listagem pública (que ordena por `publicado_em`), e ordenação manipulável é o que o §12.2
não admite.

### 5. O estado muda por `PATCH`, com a máquina de estados como fonte única

`PATCH` recebe `{"estado": "RETIRADO" | "PUBLICADO"}` — um campo, um ato. A validação é
`EstadoPublicacao.podeTransicionarPara`, a única fonte do estado inicial e das transições (o
próprio enum já registra isso: "para que a Etapa 5 não invente uma segunda regra"). Consequências
declaradas:

- `EXCLUIDO` no corpo → **422** `validacao_campo` (valor fora do enum aceito), com mensagem que
  aponta o `DELETE`: o cliente não consegue excluir por um campo de estado (RF019: estado inválido
  não entra por requisição arbitrária);
- pedir o estado em que o material **já está** → **200 idempotente, sem escrita** (é o que o
  javadoc do enum prescreve: quem precisa ignorar repetição compara os estados antes de gravar);
- `RETIRADO → PUBLICADO` com conta `PENDENTE_VERIFICACAO` → **403** com mensagem acionável:
  repor material no público é publicar (ADR-0003 (b)). Editar, retirar e excluir **não** exigem
  verificação — não colocam nada novo no público.

### 6. Exclusão é lógica e terminal no MVP; `excluido_em` é coluna nova

A decisão do dono no ADR-0005 §3 (b) é exclusão lógica imediata, metadados retidos e binário purgado
depois de 7 dias. O V1 e o `EstadoPublicacao` já fixaram `EXCLUIDO` como **terminal** ("não há saída
de `EXCLUIDO` neste MVP") — e é o que o produto tem: não existe caminho de restauração. A frase
"reversível na janela" do ADR-0005 §3 pertence à coluna de *prós* da opção (b) avaliada, e ficou
superada pela decisão ratificada + V1; **este ADR registra a divergência e o que vale**: no MVP,
excluir não volta.

O relógio da retenção precisa de uma data explícita: o `DELETE` grava **`excluido_em`** (migração
aditiva nova). Derivar a janela de `atualizado_em` funcionaria por acidente (estado terminal ⇒
nenhuma escrita posterior), e acidente não é invariante: qualquer manutenção futura que toque a
linha zeraria o prazo sem erro nenhum.

### 7. A confirmação é ato de interface, não parâmetro da API

RF017 pede confirmação "quando houver risco de perda de dados". Um campo `confirmacao=true` no
corpo não confirma nada — o cliente sempre pode enviá-lo. O `DELETE` é o ato; a confirmação é o
diálogo explícito da tela, que diz que a exclusão **não volta** e que o arquivo será removido em 7
dias. Retirar e republicar são reversíveis e não exigem diálogo.

## Consequências

- Positivas: fronteira pequena e uniforme (uma regra de recusa, um enum de estado, um alvo de
  exclusão); o EFEITO de CA008 já medido pela aceitação passa a ter ATO; CA007 ganha as operações
  que faltavam para o acesso cruzado ser tentado; nenhuma mudança fora do DRS §18.
- Negativas: 404 para material alheio é menos explicativo que 403 (custo aceito em §3); a lista do
  dono cresce sem filtro nem ordenação alternativas no MVP (a tela não precisa; filtro vira card se
  o uso pedir); `material.profile_id` continua obrigatório, então o ADR-0005 §3 (90 dias) fica
  parcialmente aberto — §9.
- Nada aqui antecipa trilhas, coleções ou relações entre materiais (ADR-0002).

## §9 — Pendência aberta (decisão do dono, não do agente)

**Anonimização dos metadados após 90 dias** (ADR-0005 §3, RF017, RNF015). O que está ratificado é o
prazo; falta definir **o que** anonimizar: quais campos (`titulo`, `descricao`,
`file_asset.nome_original_seguro`) e, principalmente, se o vínculo com a conta (`material.profile_id`)
é removido — isso é política de privacidade (LGPD, RNF015) e não se inventa por conta própria. Até a
decisão: a rotina de retenção purga o binário aos 7 dias e **não** toca metadados. Registrado em
`docs/01-arquitetura.md` §8 e no card E5.5 (fora de escopo). O QA declara o item como NÃO
VERIFICADO no aceite da etapa.

## Alternativas descartadas

- **Rotas de ação como sub-recursos** (`POST …/retirada`, `POST …/republicacao`): mais superfície
  para o mesmo ato, e o cliente continua podendo pedir repetição — a máquina de estados resolve
  igual. Ficou o `PATCH` de um campo, cujos valores já são públicos no contrato (`estado`).
- **`DELETE` físico imediato**: descartado no ADR-0005 §3 (a) — engano do usuário vira perda
  definitiva e a resposta a denúncia perde o rastro.
- **Expor `acoes[]` na resposta da lista**: duplicaria no contrato um mapa que a tela deriva de três
  estados; o servidor é a autoridade de qualquer forma (RNF010).
- **Reusar `MaterialResumo`/`MaterialDetalhe` para a visão do dono**: os dois declaram `estado` só
  como `PUBLICADO` e carregam `autor` — o dono não é "autor de si mesmo" e precisa dos três estados.

## Rastreabilidade

RF015, RF016, RF017, RF019; RN001, RN002, RN012, RN013; RNF004, RNF010, RNF013, RNF021, RNF025;
CA007, CA008; DRS §7.4, §8.3.4–§8.3.8, §15.2, §17.1–§17.4, §18; ADR-0003 (b), ADR-0005 §3.
Cards da etapa: E5.1–E5.10 (`docs/cards/e5-*.md`).
