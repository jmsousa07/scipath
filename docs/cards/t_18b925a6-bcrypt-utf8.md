# t_18b925a6 — Limitar senha por bytes UTF-8 para BCrypt

Contexto: o backend usa BCrypt (12), que limita a entrada a 72 bytes UTF-8. Os DTOs atuais limitam `String` a 72 caracteres; entradas multibyte podem passar pela validação e provocar exceção genérica 500 no cadastro ou na recuperação. Corrigir este caso sem mudar algoritmo/custo de hash.

Entrada: `backend/src/main/java/com/scipath/conta/CadastroContaRequisicao.java`, `backend/src/main/java/com/scipath/token/RecuperacaoConfirmacaoRequisicao.java`, `backend/src/main/java/com/scipath/error/ApiExceptionHandler.java`, testes `CadastroContaIntegrationTest` e `RecuperacaoDeSenhaIntegrationTest`, `contracts/openapi.yaml` e `frontend/src/lib/sessao/validacao.ts`.

Saída: validação reutilizável no servidor que limita o tamanho UTF-8 a 72 bytes para senha de cadastro e nova senha de recuperação; mensagens 422 em `errors[]` apontam o campo certo. Atualizar a validação do frontend e as descrições OpenAPI para explicitar o limite em bytes, preservando limites de caracteres já publicados quando aplicável.

## Critérios de aceite

1. Testes RED/GREEN cobrem senha ASCII no limite e senha multibyte em 72 e >72 bytes, no cadastro e na recuperação.
2. >72 bytes retorna 422 em `senha`/`novaSenha`; a recuperação não consome o token no erro. Nenhuma dessas entradas provoca 500.
3. Cadastro/recuperação válidos continuam usando BCrypt custo 12; login e senhas existentes não mudam.
4. Validação do frontend e contrato explicam o mesmo limite; não registrar senha/token em log.
5. Rodar `MAVEN_OPTS=-Xmx384m make test`, `make lint`, `make contract:check`, `npm run verify`; guardar saída em `docs/qa/<id>-<data>.log`.

## Fora de escopo

Migrações, alteração de BCrypt/custo, reset global de contas, publicação/push.
