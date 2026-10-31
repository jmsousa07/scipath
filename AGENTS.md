# SciPath — regras do projeto

Plataforma pública de divulgação científica: estudantes, professores e pesquisadores
publicam **materiais** (notas de aula, slides, artigos, apresentações, guias de estudo)
e visitantes encontram esse material por busca e filtros explícitos, com **autoria e
licença visíveis**. O produto é a descoberta e a publicação confiável, não o volume.

> **Fora do MVP** (ADR-0002): trilhas de aprendizagem, grafo de conhecimento, relações
> entre materiais, coleções, conceitos/pré-requisitos, progresso e curadoria por pares.
> Não implementar, não modelar, não "deixar pronto". São oportunidade pós-MVP.

Este arquivo vale para **todo agente** que rodar neste repositório (gerente, po, backend,
frontend, qa). Ele vence hábitos genéricos: leia antes de agir.

## Hierarquia de fontes (DRS §21.1)

1. **DRS v1.0** — `assets/raw_files/drs_scipath_v1.0.pdf`: requisitos e regras do MVP. Em
   qualquer divergência, o DRS vence.
2. **ADRs aprovados** — `docs/adr/`: decisões técnicas.
3. **`DESIGN_SYSTEM.md` e `PAGE_BLUEPRINTS.md`** — apresentação e interação visual,
   derivados de `assets/design_system_2.html` (o contrato visual executável).
4. **Cards** — `docs/cards/`: recorte específico de implementação.

Código existente é **evidência do estado do repositório**, não prova de que uma decisão
anterior foi correta. Não preserve por inércia nem apague por reflexo: julgue pelo DRS.

## Stack e decisão

- Backend: **Java 21 + Spring Boot 3 (Maven)** + PostgreSQL 16 — ADR-0001 ratificado.
- Frontend: **Next.js (App Router) + React + TypeScript + Tailwind**.
- Arquivos: disco local atrás de `StorageService` (MinIO/S3 depois, sem tocar o domínio).
- Autenticação: **e-mail + senha (bcrypt 12), verificação de e-mail obrigatória para
  publicar** — ADR-0003. Navegar e editar perfil não exigem verificação.
- Upload: **PDF, 30 MB** (configurável), validado por conteúdo — ADR-0005.
- Taxonomia: 6 tipos, 13 categorias, 7 licenças, carregadas por migração de seed; **sem
  painel administrativo no MVP** — ADR-0006.
- **Sem Docker nesta máquina.** PostgreSQL 16 nativo em `127.0.0.1:5432`.
  Credenciais em `.env` (nunca imprimir, nunca colar em card, nunca no chat).
- Base do backend: esqueleto do commit `4f524ab` **aproveitado**, com os 8 ajustes
  obrigatórios listados no ADR-0004.

## Mapa do repositório

```
AGENTS.md                          este arquivo (regras compartilhadas)
docs/00-visao.md                   objetivo, escopo, fora de escopo, público
docs/01-arquitetura.md             módulos, modelo de dados, decisões técnicas
docs/adr/ADR-XXXX-*.md             decisão tomada, com contexto e consequência
docs/cards/<id>-<slug>.md          spec de cada tarefa (escrita pelo PO)
docs/qa/<card>-<data>.log          evidência de verificação (escrita pelo QA)
DESIGN_SYSTEM.md                   tokens, tipografia, componentes, movimento
PAGE_BLUEPRINTS.md                 rotas, responsabilidade das páginas, estados
contracts/openapi.yaml             A FRONTEIRA entre backend e frontend
backend/src/main/resources/db/migration/   migrações Flyway versionadas (V__)
backend/ frontend/ e2e/            os três projetos
assets/design_system_2.html        contrato VISUAL executável do produto
assets/raw_files/drs_scipath_v1.0.pdf      DRS v1.0 (fonte #1)
infra/                             scripts de ambiente (banco, backup)
Makefile                           atalhos: up, db:migrate, api:dev, web:dev, test, lint
```

## Regras de trabalho (não negociáveis)

1. **Contrato primeiro.** Mudança de API começa em `contracts/openapi.yaml`; mudança de
   dados começa em migração nova. Nunca edite migração já aplicada.
2. **Nada acontece sem card.** Card no kanban com assignee, worktree próprio e branch
   própria. Dois cards nunca escrevem no mesmo arquivo.
3. **Um card = um assunto**, ≤ ~400 palavras de corpo, autossuficiente (o executor não vê
   o histórico da conversa).
4. **Evidência ou não aconteceu.** Log de teste, print, diff de contrato — em arquivo,
   anexado ao card. Nunca declarar teste verde sem a saída colada.
5. **Quem implementa não verifica.** O QA é outro perfil.
6. **Sem mudança não pedida.** Refatoração, "melhoria" e limpeza oportunista são
   proibidas: se vale a pena, vira card.
7. **Português no domínio, inglês na infra.** Entidade `Material`, campo `materialType`,
   contrato de API em inglês.
8. **Nomes canônicos do domínio MVP** (use sempre): conta, perfil, material, arquivo,
   categoria, tipo de material, licença, estado de publicação, autor, busca, filtro.
   **Não** use: conceito, trilha, grafo, pré-requisito, coleção, progresso, curadoria.
9. **Nada de dado fictício em tela.** Mock só em teste automatizado; tela com dado falso
   é o risco "agente gerar telas bonitas com dados falsos" (DRS §20.1).
10. **Não declarar o sistema pronto para produção.** Sem hospedagem, backup testado,
    termos de uso, privacidade e canal de denúncia, o MVP não vai ao ar (ADR-0007).
11. **O id do card é único e imutável.** O id (`E2.6-f2`) identifica **um** card: dois cards
    nunca dividem o mesmo id, e o id não muda depois de criado. Assunto com duas frentes ganha
    ids distintos (`E2.6-f2a` e `E2.6-f2b`), nunca o mesmo id em dois cards. O nome do arquivo
    de evidência **contém o id do card** (`docs/qa/<card>-<data>.log`); id reaproveitado faz uma
    evidência sobrescrever a outra na integração e a prova de um dos cards desaparece sem
    ninguém notar — foi o conflito `add/add` de `docs/qa/e2-6-f2-evidencia.sh` (dois documentos
    distintos com o mesmo nome: `make web:dev` no backend e `db-setup.sh` no QA), resolvido
    renomeando para `e2-6-f2-webdev-evidencia.sh` e `e2-6-f2-db-setup-evidencia.sh`. Quando o
    assunto tiver duas frentes, o nome do arquivo carrega também a frente, não só o id.

## Workspace de cards

1. **Regra.** Todo card que toca arquivos do monorepo nasce com workspace de repositório —
   `worktree` com branch própria apontando para o checkout do repo —, nunca `scratch`.
   `scratch` serve só para investigação que **não** abre o repo.
2. **Motivo.** Sem o worktree o worker não enxerga `Makefile`, `infra/db-setup.sh` e os demais
   arquivos do repo. O erro não aparece na criação: aparece **um worker depois**, como run
   desperdiçada (RAM, tempo, um turno da fila serial) ou como card fechado por engano,
   declarando que não há defeito.
3. **Ação do gerente.** Card que nasceu com o workspace errado é corrigido **antes** de
   promover — o `promoted` de `t_748bc02e` (`E2.6-f3`) aconteceu com o workspace ainda vazio.
4. **Checagem de despacho.** Card com `workspace_kind=scratch` cujo corpo cita caminho de
   arquivo do repo (ex.: `Makefile`, `infra/...`) é erro de criação: corrija o workspace antes
   de despachar.

O caminho é o do checkout principal **da máquina que executa** (`$HOME/scipath` na VPS;
na cópia pessoal, o caminho do §1 de `docs/rodar-em-outra-maquina.md`). `kanban_create` não
oferece escolha de worktree: quem cria passa `--workspace` explicitamente, e o esquecimento
não dá erro na hora.

## Higiene de recursos (máquina única: 2 vCPU, 954 MB)

Medido em 2026-10-16: RAM 954 MB · 2 vCPU · swap 4 GB **já com ~700 MB em uso sem worker
nenhum** · baseline só do Hermes = ~500 MB · pico de **um** worker de backend
(`./mvnw test` no esqueleto) = **276 MB** · um passo de Next + Chromium custa **400–700 MB**.

1. **Um worker por vez.** `kanban.max_in_progress: 1` e `max_in_progress_per_profile: 1`.
   Não aumentar sem aumentar a RAM da máquina primeiro.
2. **Proibido em card de implementação:** `make api:dev` / `spring-boot:run` (custa um JVM
   inteiro e uma porta), `next dev`, navegador headless/Chromium, `npm install` fora do
   card que mexe em dependência, e rodar backend **e** frontend no mesmo card.
3. **Obrigatório no backend:** `./mvnw -o test` (offline, cache quente) com
   `MAVEN_OPTS=-Xmx384m` e heap limitado no surefire. A prova de funcionamento é o teste,
   não o servidor no ar.
4. **Obrigatório no frontend:** evidência = `next build` + `npm run lint` + typecheck.
   Screenshot é ferramenta do **card de QA**, rodando sozinho, e **um** por card.
5. **Ao terminar um card:** nenhum processo deixado vivo (portas 8080 e 3000 livres).
6. **Swap não é caminho para paralelismo.** Swap guarda página fria; worker é página
   quente — o resultado é thrashing, e a CPU de 2 núcleos é o outro gargalo. Só vale como
   rede de segurança do pico de um worker.

**Dimensionamento quando houver RAM: 2 GB → dois workers leves/médios; 4 GB → dois pesados
ou três leves.** Atenção: `kanban.min_free_memory_mb` está em 1536 e é **inalcançável** com
954 MB — ele trava o dispatcher em 1 worker novo por tick mesmo que `max_in_progress` suba.
Ao aumentar a RAM, baixe esse limiar junto, senão a configuração é no-op silencioso.

## Definição de pronto (gate do DRS §17.5)

`make test` + `make lint` + `make contract:check` verdes, **executados do jeito que a
evidência registra** (e reproduzíveis de shell limpo, sem depender de variável de ambiente
de quem rodou) · migração nova e reversível · contrato atualizado se a fronteira mudou ·
evidência anexada ao card · documentação tocada quando o comportamento mudou. Sem isso, o
card volta. Se um teste falhar, isso é evidência — nunca opinião, nunca silêncio.

## Higiene de contexto (o projeto não mora no chat)

- Decisão tomada em conversa → `docs/adr/ADR-XXXX-*.md`.
- Convenção combinada → este arquivo.
- Fronteira de API → `contracts/openapi.yaml`.
- Evidência → arquivo em `docs/qa/`, **versionado**: `docs/qa/*.log` é exceção explícita à regra
  `*.log` do `.gitignore`. Evidência que não está no repositório não é evidência (§17.5, §21.3);
  mantenha o log em texto e enxuto.
- **Se só existe no chat, vai ser comprimido e perdido.** Escreva no repositório.

## Ambiente e comandos

```bash
make doctor        # confere JDK, Maven, Node, banco
make up            # sobe banco + API + web (o que já existir)
make db:migrate    # Flyway
make api:dev       # API em :8080        (proibido como evidência de card)
make web:dev       # Next em :3000       (proibido como evidência de card)
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
