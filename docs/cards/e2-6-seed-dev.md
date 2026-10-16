# E2.6 — Seed de desenvolvimento para verificação

- **Perfil:** backend · **Depende de:** E1.8, E1.3 · **Status:** pronto para execução

## Objetivo

Ter o que listar, filtrar, buscar e paginar nas verificações da Etapa 2 — sem que dado de exemplo
posa como conteúdo real (risco nomeado no DRS §20.1).

## Escopo

1. `infra/seed-dev.sql` + alvo `make db:seed:dev`: cria autores de exemplo (slug prefixado
   `exemplo-`), 6 a 8 materiais distribuídos entre eles, cobrindo categorias e tipos diferentes,
   licenças distintas e **um material RETIRADO** (para verificar o 404) e **um EXCLUIDO**.
2. **Só em banco local de desenvolvimento.** Nunca no `scipath_test` (que precisa de estado limpo
   para os testes de integração) e nunca em produção.
3. Script idempotente e reversível por `make db:reset`.
4. Nenhuma alteração de schema: os campos existentes bastam (o prefixo `exemplo-` no slug é o
   marcador).

## Fora de escopo

Criar rota pública ou tela de seed; gerar dado em produção.

## Critérios de aceite

1. Rodar duas vezes seguidas não duplica nem falha.
2. `make test` continua verde depois do seed (o banco de teste segue limpo).
3. Documentado no `README` **como dado de desenvolvimento**, com o aviso de não usar em
   demonstração ao usuário final.

## Evidência esperada

Saída das duas execuções do seed, consulta `SELECT count(*)` por tabela, log do `make test`.

## Rastreabilidade

DRS §20.1, §17, ADR-0007; apoia os critérios de E2.2–E2.5 e E2.11.
