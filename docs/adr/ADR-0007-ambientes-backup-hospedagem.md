# ADR-0007 — Ambientes, backup, hospedagem e canal de denúncia

- Data: 2026-10-16
- Status: **aceita** quanto ao escopo do MVP (local, sem lançamento público antes da Etapa 6);
  **proposta registrada** quanto aos itens operacionais marcados como proposta
- Decisores: João Mateus (dono) · orquestração
- Resolve: DRS §20.3 itens 9 (denúncia e contato) e 10 (hospedagem e backups)
- Relaciona: DRS §14.5, §13.2, §13.3, RNF019, RNF030, §21.5

## Contexto

O §14.5 exige documentar ambientes, variáveis, banco, storage, migrações, inicialização com dados
de referência e procedimento de implantação/rollback — e afirma que **compilar não é estar pronto
para produção**. O §13.3 lista dependências de lançamento que não são telas, mas são obrigatórias:
termos de uso, política de privacidade, canal de denúncia de direitos autorais, procedimento de
remoção de conteúdo irregular, contato operacional e rotina de backup/recuperação.

Hoje existe **um** ambiente: esta máquina (PostgreSQL 16 local, sem Docker).

## Decisão

1. **Escopo de ambiente do MVP: desenvolvimento local + testes automatizados + build de
   produção.** Documentados em `README`/`Makefile` e no `docs/01-arquitetura.md` §7.
2. **Não haverá lançamento público antes da Etapa 6 (Hardening)** e antes de existirem os itens
   do §13.3. O agente não declara o sistema pronto para produção em nenhuma circunstância
   (§14.5, §21.5).
3. **Backup (RNF019) — proposta:** `pg_dump` diário do banco + cópia do diretório de arquivos,
   retenção de 7 dias, script versionado em `infra/`, e **teste de restauração documentado** —
   porque o RNF019 exige recuperação, não apenas o arquivo de backup. Sem o teste de restauração,
   o item não está cumprido.
4. **Hospedagem — proposta (decidir só quando houver tráfego):** uma única VM Linux rodando API
   + PostgreSQL + proxy reverso, escolhida por simplicidade operacional (§14.2 proíbe adicionar
   infraestrutura distribuída sem requisito mensurável). Nada de contêiner orquestrado no MVP.
   O provedor e o custo ficam para quando a Etapa 6 chegar.
5. **Denúncia de conteúdo (item 9) — proposta:** um endereço de e-mail operacional declarado
   publicamente + tabela `content_report` (material, motivo, contato do denunciante, data,
   situação) alimentada por formulário simples. Sem workflow de moderação em múltiplos níveis —
   explicitamente fora do MVP (§5.2). A operação responde com o procedimento do §13.3: retirar o
   material enquanto a contestação é avaliada.
6. **Variáveis de ambiente:** um único `.env` documentado (`.env.example`), segredos nunca
   versionados (RNF012), `make doctor` diagnostica o ambiente.

## Consequências

- **Positivas:** o projeto não se compromete com custo de hospedagem nem com infraestrutura
  antes de a jornada principal funcionar; os requisitos operacionais ficam nomeados, não
  esquecidos.
- **Negativas:** "MVP funcionando" ≠ "produto no ar". Isso precisa estar claro em qualquer
  comunicação — inclusive nas minhas respostas e nos relatórios de agente (§21.5 prevê
  exatamente esse erro de comunicação).
- **Risco aceito:** sem backup automático até a Etapa 6, um dano local é perda real. Mitigação
  mínima imediata: o repositório é versionado e as migrações são reproduzíveis — o banco pode ser
  reconstruído do zero; o que se perde são dados de uso, que ainda não existem.

## Alternativas descartadas

- **Subir para nuvem agora.** Custo e configuração sem demanda; contraria §14.2 e §21.6.
- **Kubernetes / contêineres.** Sem requisito mensurável; a máquina não tem Docker.
- **Backup só quando "houver dados".** É exatamente quando não há dados que se testa a
  restauração com custo baixo; deixar para depois significa descobrir o problema no pior momento.
- **Sistema de denúncia com workflow e painel.** Fora do MVP (§5.2).

## Rastreabilidade

- **Requisitos afetados:** §14.5, §13.2, §13.3, RNF012, RNF019, RNF030, §21.5.
- **Impactos:** `infra/` (scripts de backup e restauração), `.env.example`, `Makefile`
  (`make backup`, `make restore:test`), documentação de implantação; Etapa 6.
- **Pendente:** ratificação dos itens 3–5 (backup, hospedagem, denúncia) e escolha do provedor de
  e-mail (dependência do ADR-0003 (f)).
