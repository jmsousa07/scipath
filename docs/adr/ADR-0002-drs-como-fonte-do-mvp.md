# ADR-0002 — DRS v1.0 como fonte primeira e reconciliação do escopo do MVP

- Data: 2026-10-16
- Status: **aceita** (autorizada explicitamente pelo dono nesta data)
- Decisores: João Mateus (dono) · registrado com apoio do gerente/orquestração
- Substitui parcialmente: `docs/01-arquitetura.md` (versão anterior, escrita antes do DRS)

## Contexto

`docs/00-visao.md` e `docs/01-arquitetura.md` foram escritos a partir da conversa de
concepção do projeto, antes de existir requisito formal. Propunham como núcleo do produto:
conceito, relação entre conceitos, trilha de aprendizagem, pré-requisito, grafo de
conhecimento, progresso e curadoria.

Em 16/10/2026 entrou o **DRS v1.0** (`assets/raw_files/drs_scipath_v1.0.pdf`), que define outro
MVP: um catálogo público de materiais com identidade de autor, categorias, tipos, licenças,
upload controlado e gestão pelo próprio autor. Nele, revisão por pares/selo científico está na
lista **explícita de fora do MVP** (§5.2), e §21.5 proíbe o agente de ampliar escopo por
iniciativa própria. §21.1 estabelece a hierarquia de fontes (DRS > arquitetura aprovada >
design > tarefas) e afirma que código existente é evidência de estado, **não** prova de que uma
decisão anterior foi correta.

Consequência: os dois documentos de visão/arquitetura eram, na prática, uma proposta de
produto divergente do requisito — e segui-los levaria o time a violar o DRS.

## Decisão

1. O **DRS v1.0 é a fonte #1** de requisitos, regras e critérios de aceite do MVP (§21.1).
2. `docs/00-visao.md` e `docs/01-arquitetura.md` foram **reescritos** para a baseline do DRS:
   domínio com Account, Profile, Material/Publication, FileAsset, Category, MaterialType e
   License; escopo restrito ao §5.1; fora de escopo do §5.2 respeitado; estados de interface,
   diretrizes de arquitetura, busca/SEO e testes conforme §10–§17.
3. **Trilhas, grafo de conhecimento, relações entre materiais, coleções, progresso e
   curadoria por pares ficam registrados como oportunidade pós-MVP** — não serão
   implementados, nem parcialmente, nem "de forma preparatória", sem novo requisito aprovado.
4. Os **cards F0.1–F0.5 do quadro perdem validade** como especificação: foram escritos sobre o
   modelo antigo. O quadro será reescrito com cards rastreáveis a RF/RN/CA do DRS, na ordem do
   roadmap do §18 (Etapa 0 em diante).
5. O **ADR-0001** (monorepo, contrato primeiro, Java 21 + Spring Boot 3 + PostgreSQL) passa a
   ser **proposta registrada aguardando ratificação explícita do dono**, que é decisão da
   Etapa 0 (§20.3 item 1). O DRS exige que a escolha de stack seja justificada e registrada
   antes da implementação de funcionalidades; ele também é neutro quanto a qual stack usar.

## Consequências

**Positivas**
- Escopo protegido contra expansão por agentes; o produto passa a ter uma jornada principal
  clara e verificável (descobrir → entender → acessar → publicar → gerenciar).
- Critério de aceite passa a ser objetivo: os 12 CA globais do §16.
- Menos superfície para o time de agentes errar: um domínio pequeno e bem definido.

**Negativas / custos aceitos**
- O discurso de produto muda: "conhecimento interconectado" **não** é o diferencial do MVP;
  o diferencial inicial é contexto + autoria + pessoa (§3.4). Isso precisa se refletir na
  página inicial e nos textos.
- O `assets/design_system_2.html` foi construído antes do DRS e traz o lettering "Conhecimento
  científico, interligado". Revisão visual vira card próprio (Etapa 0/1), não ajuste silencioso.
- Perda do trabalho de especificação dos cards F0.1–F0.5 (o código entregue do F0.1, porém,
  permanece aproveitável como base da Etapa 1).

## Alternativas descartadas

- **Manter o `01-arquitetura.md` anterior.** Rejeitada: propõe entidades fora do escopo do MVP
  e faria o time violar §5.2 e §21.5.
- **Manter as duas visões (produto "interconectado" e DRS) como camadas.** Rejeitada: duas
  fontes de produto geram divergência de implementação — exatamente o que §21.1 previne.
- **Implementar trilhas/grafo "em paralelo, sem atrapalhar o MVP".** Rejeitada: sem requisito
  aprovado e contra §21.6 ("o menor sistema completo que satisfaça os requisitos essenciais",
  não a maior demonstração possível).
- **Ignorar o DRS e continuar o plano anterior.** Rejeitada: o DRS é a baseline aprovada pelo
  dono; código e documentos antigos são evidência de estado, não autorização.

## Rastreabilidade da mudança (§22 exige)

- **Requisitos afetados:** §5.2 (fora do MVP), §11 (modelo de domínio), §21.5 (proibições),
  §3.4/§3.5 (proposta de valor e direção visual), §16 (critérios de aceite globais).
- **Impactos:** `docs/00-visao.md`, `docs/01-arquitetura.md` (reescritos); cards F0.1–F0.5
  (invalidados, a reescrever); `assets/design_system_2.html` (revisão pendente de tagline);
  `AGENTS.md` (precisa incorporar §17.5 DoD e §21.4 formato de relatório).
- **Critérios de aceite revisados:** adotados os CA001–CA012 do DRS §16 como critérios globais
  do projeto.
