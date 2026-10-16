# ADR-0006 — Taxonomia inicial: tipos de material, categorias e licenças

- Data: 2026-10-16
- Status: **proposta registrada** — aguardando ratificação do dono quanto às **listas** (a
  estrutura de gestão por seed/migração está aceita como decisão técnica)
- Decisores: João Mateus (dono) · orquestração
- Resolve: DRS §20.3 itens 4 (licenças iniciais), 5 (categorias iniciais) e 8 (administração)
- Relaciona: DRS §11.1.5, §11.1.6, §11.1.7, §6.3, RN004, RN005, RN006, CA002

## Contexto

O DRS pede taxonomia **controlada** (não texto livre), licença **explícita** apresentada como
condição de reuso — jamais como decoração, nem sugerindo que "estar público" significa "domínio
público" — e deixa em aberto se haverá painel administrativo. O §6.3 responde a última:
"Não é necessário construir um painel administrativo completo se os dados iniciais puderem ser
gerenciados por seed/migração controlada."

## Decisão de gestão (técnica)

- Tipos, categorias e licenças são **dados de referência** carregados por migração versionada de
  seed (`V2__seed_taxonomia.sql`), com `slug` estável como chave de referência.
- **Não há painel administrativo no MVP.** Alteração da taxonomia é nova migração — auditável
  e reproduzível (RNF028). Se a operação exigir edição em tempo real, isso vira requisito novo
  e ADR próprio (não "melhoria óbvia").
- `material` referencia por chave estrangeira: categoria inválida ou livre é **recusada** (RN005),
  o que também é critério de aceite de teste de integração.
- O modelo admite hierarquia simples (`category.parent_id`), mas o MVP usa **um nível** —
  profundidade além disso não tem requisito (§11.1.5).

## Listas iniciais (proposta — a ratificar)

**Tipos de material** (`material_type`): `nota-de-aula`, `slides`, `artigo`, `apresentacao`,
`guia-de-estudo`, `outro`. Fonte: §11.1.6, que cita exatamente esses.

**Categorias** (`category`) — 13 áreas iniciais, alinhadas às grandes áreas do conhecimento:

```
computacao-e-tecnologia     matematica-e-estatistica   fisica-e-astronomia
quimica                     ciencias-biologicas        saude
engenharias                 ciencias-agrarias          ciencias-humanas
ciencias-sociais-aplicadas  linguistica-e-letras       artes
multidisciplinar
```

Cada uma com nome de exibição, `slug` e descrição curta (ajuda a descoberta e o SEO — §12).

**Licenças** (`license`) — 7 opções, cada uma com nome, identificador, URL de referência e
descrição curta em linguagem simples:

```
CC BY 4.0            CC BY-SA 4.0         CC BY-NC 4.0
CC BY-NC-SA 4.0      CC BY-ND 4.0         CC0 1.0 (domínio público)
Todos os direitos reservados (permitido ler e baixar; vedada reutilização)
```

Regra de interface (RN006, §13.2): a licença aparece com explicação curta do que permite, e a
opção "todos os direitos reservados" deixa claro que o material continua protegido — o contrário
do equívoco que o DRS nomeia.

## Consequências

- **Positivas:** taxonomia estável, sem texto livre poluindo busca e filtros; alteração
  auditável por migração; zero código de administração no MVP.
- **Negativas:** mudar uma categoria em produção não é operação de tela — exige migração (aceito
  conscientemente; é o caminho que o §6.3 autoriza).
- **Ponto de atenção:** as listas são a primeira coisa que um usuário real percebe errada. O DRS
  manda revisá-las antes do lançamento — isso entra na Etapa 6.

## Alternativas descartadas

- **Categoria como texto livre.** Viola RN005 e quebra filtro/paginação consistente (§12).
- **Painel administrativo no MVP.** Complexidade sem requisito (§14.2); §6.3 permite seed.
- **Só "domínio público" e "todos os direitos reservados".** Reduz opções legítimas de
  compartilhamento acadêmico, que é o uso central do produto.
- **Hierarquia profunda de categorias.** Sem requisito e encarece busca e navegação.

## Rastreabilidade

- **Requisitos afetados:** §11.1.5–7, §6.3, RN004, RN005, RN006, RNF028, CA002.
- **Impactos:** migração de seed; `contracts/openapi.yaml` (endpoints de taxonomia para popular
  os formulários do frontend); formulário de publicação (Etapa 4); filtros (Etapa 2).
- **Pendente:** ratificação das três listas. Sem ratificação, a Etapa 2 (filtros) e a Etapa 4
  (publicação) não podem ser especificadas em card.
