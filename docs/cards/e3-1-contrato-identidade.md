# E3.1 — Contrato OpenAPI v0.4: identidade, sessão e perfil

- **Perfil:** po · **Depende de:** E2.11 (gate da Etapa 2) · **Status:** pronto para execução
- **Etapa 3 do DRS (§18):** identidade — cadastro, login/logout e edição de perfil (RF008–RF011)

## Contexto

A Etapa 2 entregou a descoberta pública no contrato **v0.3**. A Etapa 3 abre identidade e sessão, e
o **ADR-0003** já fixa: e-mail + senha com bcrypt 12; verificação de e-mail **obrigatória para
publicar** (navegar e editar perfil não exigem); sessão em cookie `httpOnly`+`Secure`+`SameSite=Lax`
mantida no servidor, sem JWT; CSRF nas escritas; tokens de verificação/recuperação de uso único,
armazenados hasheados; `EmailSender` no domínio com outbox local de desenvolvimento.

Contrato primeiro (AGENTS.md regra 1): backend e frontend da Etapa 3 implementam contra ele.

## Escopo

1. Endpoints no `contracts/openapi.yaml`, seguindo a convenção **já existente** no arquivo
   (releia-o antes de escrever — não invente estilo novo para rota, nome de campo ou envelope):
   cadastro de conta, login, logout, estado da sessão corrente, editar perfil próprio, reenvio e
   confirmação de verificação de e-mail, solicitação e confirmação de recuperação de acesso.
2. Vocabulário de erro do v0.2.1 estendido: **401** não autenticado, **403** sem permissão,
   **409** conflito de unicidade (e-mail/slug), **422** validação de campo, **429** reenvio em
   cooldown. Nenhum erro revela se existe conta com determinado e-mail, exceto no cadastro.
3. Declarar no contrato **onde a sessão é persistida** (tabela no banco ou memória do servidor) e
   os tempos de expiração (inatividade 12 h, absoluta 7 dias — ADR-0003 (e)). Esta é a decisão que
   o ADR-0003 deixou explicitamente para a Etapa 3.
4. Nenhuma resposta pública expõe e-mail, hash, token ou identificador interno (RNF013).
5. Registrar no contrato que a publicação exige conta em estado `ATIVO` (verificação concluída) e
   que navegar/pesquisar/editar perfil não exigem.

## Fora de escopo

Implementar endpoints, telas, migração. Decidir provedor de e-mail (pendente com ADR-0006/0007).
Endpoints de publicação (Etapa 4).

## Critérios de aceite

1. `make contract:check` com **0 erros e 0 warnings**, saída colada.
2. Cada endpoint de identidade com request, resposta de sucesso, respostas de erro e exemplos.
3. Schemas de perfil público sem campo privado (teste de ausência, não de presença).
4. `version: 0.4.0` e nota de compatibilidade (v0.3 permanece íntegra para os consumidores da
   Etapa 2).

## Evidência esperada

Saída do `contract:check`, diff do contrato, e a lista endpoint → requisito.

## Rastreabilidade

RF008–RF011, RN009, RNF009, RNF012, RNF013, CA004, CA007; ADR-0003; DRS §18, §20.3 itens 2 e 3.
