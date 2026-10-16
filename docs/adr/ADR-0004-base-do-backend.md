# ADR-0004 — Base do backend: aproveitar o esqueleto entregue, com ajustes obrigatórios

- Data: 2026-10-16
- Status: **aceita** (decisão tomada a pedido do dono: "analise o esqueleto do back e decida se
  está de acordo com o DRS / se dá para reutilizá-lo")
- Decisores: João Mateus (dono, por delegação explícita da análise) · orquestração
- Resolve: DRS §20.3 item 1 (arquitetura base) — em conjunto com o ADR-0001
- Relaciona: DRS §14.1, §14.2, §14.3, §14.5, §17, §21.1, §21.3

## Contexto

Antes da chegada do DRS, o card F0.1 foi executado pelo perfil `backend` e entregou um esqueleto
Spring Boot no branch `feat/f0-1-backend` (commit `4f524ab`). Com a baseline do DRS (ADR-0002)
definida, era preciso decidir: reaproveitar esse código como base da Etapa 1 ou descartá-lo e
reescrever. O DRS orienta a decisão: §21.1 diz que código existente é **evidência do estado do
repositório**, não prova de que uma decisão anterior foi correta; §14.1 é neutro quanto a
continuar ou não uma tentativa anterior. Logo, a análise foi feita por mérito, contra o DRS.

## O que foi encontrado (evidência)

11 arquivos, 350 linhas, pacote `com.scipath`:

```
pom.xml                                   Java 21, Spring Boot 3.5.16, wrapper Maven
src/main/java/com/scipath/SciPathApplication.java
src/main/java/com/scipath/config/FlywayConfig.java
src/main/java/com/scipath/health/{HealthController,HealthService,HealthResponse}.java
src/main/resources/application.yml        sem segredo; tudo por variável de ambiente
src/main/resources/db/migration/README.md (nenhuma migração ainda — era o F0.3)
src/test/java/com/scipath/{SciPathApplicationTests,health/HealthControllerTest,health/HealthServiceTest}.java
```

**Conforme com o DRS (verificado no código):**

| Item | Evidência |
|---|---|
| Stack sem dependência desnecessária (§14.2) | só `web`, `jdbc`, `flyway-core`, `flyway-database-postgresql`, `postgresql`, `starter-test` |
| Sem H2 para falsear SQL real (regra do projeto + §17.2) | nenhuma dependência H2 no `pom.xml` |
| Configuração sem segredos; nada commitado (RNF012) | `application.yml` usa `${SPRING_DATASOURCE_*}` sem valor padrão — se o `.env` faltar, `/health` responde `database=DOWN` em vez de inventar destino |
| DTO separado da regra (§14.3 — "não retornar entidades") | `HealthResponse` é record próprio; `HealthService` não é exposto |
| Migrações versionadas (RNF028) | Flyway configurado com `flyway-database-postgresql` |
| Testes proporcionais (§17.1/§17.2) | teste de serviço + teste do controller com `MockMvc` standalone |
| Legibilidade e "porquê" documentados (§21.4) | comentários explicam a decisão, não o óbvio |

**Desvios e ajustes obrigatórios** (viram critério de aceite de cards da Etapa 1):

1. `pom.xml` descreve o projeto como "conhecimento estruturado e interconectado" — narrativa
   **invalidada pelo ADR-0002**. Corrigir para a definição do DRS (§2.1).
2. Falta `spring-boot-starter-validation` e o **tratamento de erro padronizado** (RFC 7807 com
   `errors[]`), exigido pelo §14.3. Entra na Etapa 1.
3. **Flyway tolerante engole falha de migração e a API sobe.** Razoável em desenvolvimento
   (foi o que atendeu o critério 4 do F0.1), **inaceitável em produção**: serviria com schema
   desatualizado silenciosamente. Restringir a estratégia tolerante a `dev`/`test` por perfil;
   em produção a subida falha rápido.
4. Não existe teste de integração contra o PostgreSQL real (o caso `database=DOWN` foi
   verificado à mão). §17.2 pede integração com banco real — adicionar contra `scipath_test`.
5. Observabilidade mínima (RNF029) ainda é só o `/health` próprio. Decidir na Etapa 1 entre
   Actuator (com endpoints restritos) ou log estruturado + o `/health` do contrato.
6. Estrutura de pacotes por domínio (`account`, `profile`, `material`, `file`, `taxonomy`)
   quando o domínio chegar; `health` permanece como está.
7. Sem CORS/segurança configurados — esperado: é a Etapa 3 (ADR-0003).

## Decisão

**Aproveitar o esqueleto como base da Etapa 1**, aplicando os ajustes 1–7 como critérios de
aceite dos cards correspondentes. Não descartar e não reescrever.

Justificativa: nada nele contraria o DRS — os desvios são de **completude**, não de direção.
Reescrever do zero descartaria configuração externalizada correta, Flyway instalado e testes
verdes, sem ganho mensurável (§14.2: não adicionar custo sem requisito).

## Ratificação da stack (ADR-0001)

A instrução do dono de avaliar a **reutilização deste esqueleto Java/Spring** implica ratificar
a stack do ADR-0001 (Java 21 + Spring Boot 3 + PostgreSQL + monorepo) — não houve pedido de troca
de stack. Registrado como ratificação implícita e explícita nesta data; se a intenção era outra,
este ADR deve ser corrigido antes de qualquer implementação de produto.

## Consequências

- **Positivas:** a Etapa 1 começa com build, config e testes funcionando; o trabalho do worker
  F0.1 é preservado; os ajustes são pequenos e verificáveis.
- **Negativas:** carrega a dívida dos itens 2–5, que precisam ser fechados **antes** das
  funcionalidades de produto, não depois.
- **Risco aceito:** manter código gerado por agente exige que cada ajuste seja verificado com
  evidência (log de teste), não por leitura (§21.3: "nunca inventar que testes passaram").

## Alternativas descartadas

- **Descartar e reescrever do zero em Java.** Perde código correto e não endereça nenhum
  requisito; serviria só para "limpar" — o que o §21.3 não pede.
- **Descartar e migrar para Node/TS.** Troca de stack sem requisito que a justifique (§14.2) e
  contra a ratificação acima.
- **Aproveitar sem ajustes.** Deixa descrição de produto inválida no `pom.xml`, erros fora do
  padrão do contrato e migração tolerante em produção — todos violariam o DRS em algum §.

## Rastreabilidade

- **Requisitos afetados:** §14.1–§14.5, §17.1/§17.2, RNF012, RNF028, RNF029, §21.1, §21.3.
- **Impactos:** `backend/` (herdado do branch `feat/f0-1-backend`); cards da Etapa 1; ADR-0001
  (ratificado).
