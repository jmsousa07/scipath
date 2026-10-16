# E1.4 — Erros, validação e configuração por perfil

- **Perfil:** backend · **Depende de:** E1.2 · **Status:** pronto para execução

## Objetivo

Fechar os ajustes 1, 2, 3 e 8 do ADR-0004: o backend responde erro no padrão do contrato, valida
entrada de forma explícita e **não sobe com schema desatualizado em produção**.

## Escopo

1. Corrigir a descrição do projeto no `pom.xml` (hoje repete a narrativa invalidada pelo ADR-0002).
2. Adicionar `spring-boot-starter-validation` — dependência nova, justificada por RNF011.
3. **Tratamento de erro único** (`@ControllerAdvice` → `ProblemDetail` com `errors[]`), cobrindo
   validação de entrada (400), não encontrado (404), não autorizado (401/403) e erro interno
   (500), sempre com mensagem acionável e sem vazar detalhe interno (RNF004, RNF012).
4. **Flyway fail-fast em produção:** a estratégia tolerante que hoje engole falha de migração fica
   restrita aos perfis `dev`/`test`; no perfil `prod` a aplicação **falha ao subir** se a migração
   falhar.
5. Ajuste 8: registrar que CORS e segurança pertencem à Etapa 3 (nota no ADR-0004, sem código).

## Fora de escopo

Autenticação, sessão, CORS, endpoints de domínio.

## Critérios de aceite

1. Payload inválido responde **400** no formato `ProblemDetail` com `errors[]` apontando o campo —
   teste automatizado com a saída colada (RNF011).
2. Erro inesperado responde 500 **sem** stack trace, SQL ou caminho de arquivo na resposta
   (RNF012), com o detalhe apenas no log.
3. Com o perfil `prod` e uma migração quebrada (cenário de teste), a aplicação não sobe.
4. `make test` verde e o `dependency:tree` mostrando a dependência nova isolada.

## Evidência esperada

Log dos testes de erro (400 e 500), log do cenário de migração quebrada em `prod`, diff do
`pom.xml` com justificativa.

## Rastreabilidade

RNF004, RNF011, RNF012, ADR-0004 (ajustes 1, 2, 3, 8), DRS §14.3.
