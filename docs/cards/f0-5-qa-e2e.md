# F0.5 — QA: e2e do caminho feliz da fatia 0 com evidência

## Contexto

O time só pode dizer "a fatia 0 está de pé" se houver uma verificação automática que
suba a stack e percorra o caminho do usuário. Este card também estabelece o harness que
todos os cards seguintes vão usar — por isso ele vem antes de qualquer feature nova.

## Entrada

- `contracts/openapi.yaml`, `docs/cards/f0-3-*.md` e `docs/cards/f0-4-*.md` (critérios que você vai verificar).
- `Makefile` (alvos `up`, `api:dev`, `web:dev`, `e2e`).
- A stack precisa subir nativa: Postgres local + API + web.

## Saída

`e2e/` com Playwright configurado e um spec do caminho feliz:
publicar um material pela API → ele aparece na lista do front → clicar abre a leitura.
Mais um runner que sobe a stack, roda o spec e derruba tudo, deixando o log em `docs/qa/`.

## Critérios de aceite

1. `make e2e` sobe a stack, roda o spec e passa, num repositório limpo (sem passo manual).
2. O spec falha de verdade quando o comportamento muda: prove desligando um trecho da API
   (ou apontando para porta errada) e mostrando o vermelho na evidência.
3. Verificações negativas incluídas: `POST /materiais` sem payload válido devolve 400 com
   `errors[]`; `GET` com `tipo` inválido devolve 400.
4. O log de execução fica em `docs/qa/f0-5-e2e-<AAAA-MM-DD>.log`, com o veredito por
   critério dos cards F0.3 e F0.4 (passou / falhou / não testado).
5. Ao final, `docs/qa/f0-5-veredito.md` diz explicitamente o que **não** foi testado.

## Fora de escopo

Testes de carga, testes de segurança, cobertura de todas as telas, CI remota.

## Evidência exigida

O próprio log de execução (critério 4) + o arquivo de veredito (critério 5) +
`docs/qa/f0-5-falha-proposital.log` (critério 2).
