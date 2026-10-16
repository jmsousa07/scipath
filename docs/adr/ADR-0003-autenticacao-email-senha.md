# ADR-0003 — Autenticação por e-mail + senha, com verificação obrigatória para publicar

- Data: 2026-10-16
- Status: **aceita** — decisão do dono registrada nesta data (as alíneas marcadas _"proposta
  técnica"_ aguardam ratificação e podem ser revisadas)
- Decisores: João Mateus (dono) · registrado pela orquestração
- Resolve: DRS §20.3 itens 2 e 3
- Requisitos relacionados: RF008–RF011, RN001, RN009, RN011, RNF009, RNF012, RNF013, RNF017,
  CA004, CA005, CA007, CA012

## Contexto

O DRS deixa explicitamente em aberto: (a) autenticação por senha e e-mail, provedor externo ou
outro mecanismo; (b) verificação de e-mail obrigatória antes de publicar ou apenas recomendada;
(c) configuração de e-mail. O §3.2 já fixa que o MVP **não** exige e-mail institucional verificado
e que a arquitetura de identidade deve permitir verificação institucional ou ORCID no futuro sem
depender delas no fluxo básico. O §20.2 fixa que publicação exige conta autenticada.

Restrições do ambiente: não há servidor de e-mail nesta máquina e não há Docker (nada de
mail-catcher em contêiner).

## Decisão do dono (2026-10-16)

1. **Autenticação: e-mail + senha.** Sem provedor de identidade externo no MVP.
2. **Verificação de e-mail obrigatória para publicar.** Navegar, pesquisar e editar o próprio
   perfil continuam liberados sem verificação.

## Decisões técnicas decorrentes _(propostas técnicas — revisáveis)_

- **(a) Hashing de senha:** bcrypt com custo 12, via `spring-security-crypto` (já disponível no
  Spring Boot, sem dependência nova — §14.2 pede não adicionar biblioteca sem necessidade
  demonstrada). Migrar para Argon2id só com justificativa registrada. Nunca registrar senha,
  hash ou token em log (RNF009).
- **(b) Estados da conta:** `PENDENTE_VERIFICACAO` (estado inicial), `ATIVO` (após confirmação),
  `SUSPENSO` (reservado para abuso). **Login funciona em `PENDENTE_VERIFICACAO`**; apenas a
  publicação exige `ATIVO`.
- **(c) Token de verificação:** 32 bytes aleatórios URL-safe, armazenado **hasheado** (SHA-256),
  uso único, validade de 24 h, reenvio com cooldown de 60 s. A confirmação consome o token e
  registra `email_verificado_em`.
- **(d) Recuperação de acesso (RF010, prioridade I):** mesmo padrão — token de uso único
  hasheado, validade curta (30 min), resposta que **não revela** se o e-mail existe (evita
  enumeração de contas).
- **(e) Sessão:** cookie `httpOnly` + `Secure` + `SameSite=Lax`, sessão mantida no servidor
  (revogável), com expiração explícita (proposta: inatividade 12 h, absoluta 7 dias). Sem JWT no
  MVP. Frontend e API **na mesma origem** — em desenvolvimento via proxy/rewrite de `/api` pelo
  Next; em produção via proxy reverso — para não depender de cookie cross-origin. Escritas
  protegidas por token CSRF. Onde a sessão é persistida (memória ou tabela) fica para a Etapa 3.
- **(f) Envio de e-mail:** interface `EmailSender` no domínio (mesmo padrão do `StorageService`),
  com implementação local de desenvolvimento que grava a mensagem em um **outbox local**
  (tabela ou arquivo) acessível por comando de diagnóstico; em produção, provedor SMTP definido
  no ADR-0006 com credenciais em `.env` (RNF012). **Nenhum token é registrado em log**, nem em
  desenvolvimento — o link sai apenas pelo outbox.
- **(g) Falha de envio (RNF017):** a conta permanece `PENDENTE_VERIFICACAO`, a publicação segue
  bloqueada e a interface oferece reenvio com mensagem acionável; o erro é registrado sem token.
- **(h) Transparência na interface (§13.1, RNF004):** ao tentar publicar sem verificação, a tela
  explica o que falta e permite reenviar — não é muro silencioso.
- **(i) Identidade futura:** o modelo reserva espaço para verificação institucional/ORCID, mas
  nada é apresentado como verificado sem processo real (RN011, CA012).
- **(j) Propriedade (RN001):** `profile_id` do material vem do contexto autenticado, nunca do
  payload.

## Consequências

**Positivas**
- Fluxo de identidade simples, sem dependência externa; alinhado a RNF009/RNF012/RNF013.
- Verificação obrigatória reduz abuso no upload, que é um risco nomeado no §20.1.
- Separação limpa entre "pode navegar" e "pode publicar", que é o que o §20.2 descreve.

**Negativas / custos aceitos**
- Exige infraestrutura de envio de e-mail (ainda que local em desenvolvimento) e estado de
  token no banco.
- Adiciona fricção ao primeiro uso: o usuário precisa confirmar o e-mail antes de publicar.
  Mitigação: a navegação pública não é bloqueada e o aviso de verificação é persistente.
- Um canal de e-mail mal configurado bloqueia publicação — por isso o diagnóstico
  (`make doctor`) e o reenvio entram como critério de aceite da Etapa 3.

## Alternativas descartadas

- **Provedor externo / OAuth (Google, ORCID) agora.** Adia o essencial, exige credenciais e
  consentimento, e o DRS trata verificação institucional/ORCID como evolução — não como base.
- **Verificação apenas recomendada.** O dono decidiu obrigatória; publicar sem verificar
  agrava abuso e enfraquece a resposta a denúncia (§13.2).
- **JWT em cookie.** Revogação e expiração mais difíceis de administrar, sem ganho mensurável
  no MVP (§14.2: não adicionar complexidade sem requisito).
- **Login sem senha (magic link).** Torna o e-mail um ponto único de falha para autenticar e
  criar conta.

## Rastreabilidade

- **Requisitos afetados:** RF008–RF011, RN001, RN009, RN011, RNF009, RNF012, RNF013, RNF017,
  CA004, CA005, CA007, CA012.
- **Impactos:** `docs/01-arquitetura.md` (§2 modelo de conta, §5 páginas, §8 decisões);
  Etapa 3 do roadmap (§18); endpoints de autenticação em `contracts/openapi.yaml`; cards da
  Etapa 3.
- **Pendente:** ratificação das alíneas (a)–(f); provedor de e-mail real definido no ADR-0006
  (hospedagem e backup).
