# E4.10 — Painel: apontar para `/painel/publicar` (a tela agora existe)

- **Perfil:** frontend · **Depende de:** E4.6 · **Status:** pronto para execução
- **Card no quadro (recriado):** substitui `t_c83f7952`, que nasceu com `workspace: scratch` por
  defeito do criador. Renumerado de "E4.7 — Painel" para **E4.10**: já existe um card E4.7 (a
  integração da etapa, `t_6f69d6e4`), e dois cards com o mesmo número confundem a fila.

## Contexto

O E4.6 entregou `/painel/publicar` e, de propósito, **não tocou em `/painel`** (fora de escopo
declarado no card: "Mexer no cabeçalho, no painel (§9)"). O resultado é que `/painel` passa a
conter uma afirmação **falsa** — `frontend/src/app/painel/page.tsx`, seção "O que você pode fazer
agora":

> "Nesta versão da plataforma não existem telas de publicação nem de listagem dos seus materiais;
> por isso esta área aponta apenas para o que já está no ar."

A tela de publicação existe desde o E4.6. A listagem de materiais próprios continua não existindo
(Etapa 5) — a frase precisa separar as duas coisas em vez de negar as duas.

## Escopo

1. Substituir o parágrafo do cabeçalho que diz que não há tela de publicação pelo que é verdade
   hoje: há `/painel/publicar`; a listagem/edição/retirada dos próprios materiais (Etapa 5) ainda
   não existe e isso continua dito.
2. Adicionar o cartão de ação "Publicar material" na lista de "O que você pode fazer agora",
   apontando para `/painel/publicar`. O cartão deve dizer o que a publicação exige (e-mail
   confirmado — ADR-0003 (b)) quando `estadoConta === 'PENDENTE_VERIFICACAO'`, para a área do
   usuário não mandar a pessoa a uma tela que vai bloquear sem aviso.
3. Nada além disso: não mexer no cabeçalho do site, no formulário de perfil nem em
   `/painel/publicar`.

## Critérios de aceite

1. `npm run build` + `npm run lint` + `npm run typecheck` verdes, saída colada.
2. HTML entregue de `/painel` (conta `ATIVO`) contém o link para `/painel/publicar`; e não contém
   mais a frase que nega a existência da tela de publicação. Estender
   `frontend/scripts/check-perfil.mjs` (ou evidência equivalente) com a asserção.
3. Com conta `PENDENTE_VERIFICACAO`, o cartão diz que publicar exige e-mail confirmado e mantém o
   caminho de reenvio que já está na tela.
4. Evidência versionada em `docs/qa/<card>-<data>.log`.

## Rastreabilidade

ADR-0003 (b); `PAGE_BLUEPRINTS.md` §9 e §11; AGENTS.md regras 6, 9 e 10.
