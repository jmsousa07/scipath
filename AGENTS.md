# SciPath — regras do projeto

Plataforma pública de divulgação científica: estudantes, professores e pesquisadores
publicam materiais (notas de aula, slides, artigos, explicações) e o conhecimento fica
**estruturado e interconectado** — com conceitos, pré-requisitos, trilhas de aprendizagem
e um grafo navegável. O diferencial do produto é a organização, não o volume.

Este arquivo vale para **todo agente** que rodar neste repositório (gerente, po, backend,
frontend, qa). Ele vence hábitos genéricos: leia antes de agir.

## Stack e decisão

- Backend: **Java 21 + Spring Boot 3 (Maven)** + PostgreSQL 16. Escolhido pela stack do João
  e pela meta profissional (vaga ObIT-NE); custo: mais cerimônia que um monolito Node.
- Frontend: **Next.js (App Router) + React + TypeScript + Tailwind**.
- Arquivos: disco local atrás de uma interface `StorageService` (S3/MinIO depois, sem reescrever domínio).
- **Sem Docker nesta máquina.** PostgreSQL 16 roda nativo em `127.0.0.1:5432`.
  Credenciais em `.env` (nunca imprimir, nunca colar em card, nunca no chat).

## Mapa do repositório

```
AGENTS.md                     este arquivo (regras compartilhadas)
docs/00-visao.md              objetivo, escopo, fora de escopo, público
docs/01-arquitetura.md        módulos, modelo de dados, decisões técnicas
docs/adr/ADR-XXXX-*.md        decisão tomada, com contexto e consequência
docs/cards/<id>-<slug>.md     spec de cada tarefa (escrita pelo PO)
docs/qa/<card>-<data>.log     evidência de verificação (escrita pelo QA)
contracts/openapi.yaml        A FRONTEIRA entre backend e frontend
contracts/db/migrations/      migrações SQL versionadas (Flyway)
backend/ frontend/ e2e/       os três projetos
assets/design_system_2.html   contrato VISUAL do produto (tipografia, cor, componentes)
infra/                        scripts de ambiente (banco, setup)
Makefile                      atalhos: up, db:migrate, api:dev, web:dev, test, lint, e2e
```

## Regras de trabalho (não negociáveis)

1. **Contrato primeiro.** Mudança de API começa em `contracts/openapi.yaml`; mudança de dados
   começa em migração nova. Nunca edite migração já aplicada.
2. **Nada acontece sem card.** Card no kanban com assignee, `--workspace worktree:<repo>` e
   `--branch` própria. Dois cards nunca escrevem no mesmo arquivo.
3. **Um card = um assunto**, ≤ ~400 palavras de corpo, autossuficiente (o executor não vê o histórico de conversa).
4. **Evidência ou não aconteceu.** Log de teste, print, diff de contrato — em arquivo, anexado ao card.
5. **Quem implementa não verifica.** O QA é outro perfil.
6. **Sem mudança não pedida.** Refatoração, "melhoria" e limpeza oportunista são proibidas:
   se vale a pena, vira card.
7. **Português no domínio, inglês na infra.** Entidade `Material`, campo `materialType`,
   contrato de API em inglês.
8. **Nomes canônicos do domínio** (use sempre): material, conceito, trilha, pré-requisito,
   grafo de conhecimento, curadoria, progresso, área.

## Definição de pronto (gate)

`make test` + `make lint` + `make contract:check` verdes · migração nova e reversível ·
contrato atualizado se a fronteira mudou · evidência anexada ao card · documentação tocada
quando o comportamento mudou. Sem isso, o card volta.

## Higiene de contexto (o projeto não mora no chat)

- Decisão tomada em conversa → `docs/adr/ADR-XXXX-*.md`.
- Convenção combinada → este arquivo.
- Fronteira de API → `contracts/openapi.yaml`.
- Evidência → arquivo em `docs/qa/`.
- **Se só existe no chat, vai ser comprimido e perdido.** Escreva no repositório.

## Ambiente e comandos

```bash
make doctor        # confere JDK, Maven, Node, banco
make up            # sobe banco + API + web (o que já existir)
make db:migrate    # Flyway
make api:dev       # API em :8080
make web:dev       # Next em :3000
make test          # testes do backend
make lint          # lint + typecheck
make e2e           # Playwright
make contract:check# valida openapi.yaml
```

## Contexto do dono

João Mateus de Sousa, Aracati/CE, Ciência da Computação no IFCE Aracati. Aprende
implementando antes de usar biblioteca — em dúvida técnica, explique a intuição e o
trade-off antes de despejar solução pronta. Comunicação direta, sem elogio genérico,
separando fato de hipótese.
