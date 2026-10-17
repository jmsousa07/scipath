# E3.4 — Backend: tokens de uso único — verificação de e-mail e recuperação de acesso

- **Perfil:** backend · **Depende de:** E3.2 · **Status:** pronto para execução

## Contexto

O ADR-0003 (c), (d), (f), (g), (h) fixa o mecanismo: token de 32 bytes aleatórios URL-safe,
armazenado **hasheado** (SHA-256), uso único, validade de 24 h para verificação de e-mail e 30 min
para recuperação; reenvio com cooldown de 60 s; resposta de recuperação que **não revela** se o
e-mail existe; `EmailSender` no domínio com implementação local que grava em **outbox** de
desenvolvimento (nunca em log). A verificação é obrigatória para **publicar** — publicar é Etapa 4,
mas o token e o estado da conta nascem aqui.

## Escopo

1. **Nada de migração:** a tabela `verification_token` já existe no `V1__modelo_mvp.sql`, com
   `token_hash` (UNIQUE), `tipo` (`CONFIRMACAO_EMAIL` | `RECUPERACAO_SENHA`), `expira_em` e
   `consumido_em` — o uso único e as validades do ADR-0003 já estão no banco. Este card escreve a
   camada de aplicação. Se faltar coluna, **pare e registre bloqueio** (coluna nova é card de
   migração aditiva).
2. Interface `EmailSender` no domínio (mesmo padrão do `StorageService`) com implementação local de
   desenvolvimento que grava a mensagem em outbox acessível por comando de diagnóstico; nenhum
   token em log, nem no outbox em texto claro além do próprio link.
3. Geração, envio, reenvio com cooldown (**429** no contrato) e confirmação da verificação de
   e-mail: confirmar consome o token (uso único), registra `email_verificado_em` e leva a conta a
   `ATIVO`. Token inválido/expirado/reusado → erro do contrato, sem revelar detalhe do token.
4. Recuperação de acesso (RF010): solicitação responde **igual** para e-mail existente e
   inexistente; confirmação com token válido troca a senha (bcrypt 12) e invalida as sessões
   abertas daquela conta.
5. Falha de envio (RNF017): conta permanece `PENDENTE_VERIFICACAO`, nada é marcado como enviado, o
   erro é registrado **sem token** e a interface tem como oferecer reenvio.
6. Comando de diagnóstico do canal de e-mail (ex.: alvo no `Makefile` ou script em `infra/`) que
   mostra o último item do outbox — é o que torna o fluxo verificável sem servidor de e-mail.

## Fora de escopo

Provedor SMTP real (pendente com ADR-0006/0007), publicação (Etapa 4), telas (E3.6/E3.7).

## Critérios de aceite

1. Testes de integração colados: fluxo completo de verificação (cria conta → token no outbox →
   confirma → conta `ATIVO`).
2. Teste: token reusado e token expirado → recusa com o erro do contrato.
3. Teste: reenvio dentro do cooldown → 429.
4. Teste: recuperação com e-mail inexistente e com existente devolvem **o mesmo** status e corpo.
5. Teste: senha trocada pela recuperação invalida a sessão anterior (cookie antigo → 401).
6. Grep na evidência provando que nenhum token/hash aparece nos logs de execução.
7. Teste: falha simulada do `EmailSender` deixa a conta em `PENDENTE_VERIFICACAO` e não marca envio.

## Evidência esperada

Saída dos testes, o conteúdo do outbox de desenvolvimento (com token mascarado) e o grep de segredo.
Renomeie o arquivo de evidência com o id do card (regra P1).

## Rastreabilidade

RF010, RNF009, RNF012, RNF013, RNF017, CA004; ADR-0003 (c), (d), (f), (g), (h); DRS §8.2.3, §13.1.
