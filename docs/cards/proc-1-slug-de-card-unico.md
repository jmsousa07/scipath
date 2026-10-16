# P1 — Slug de card único: o nome da evidência não pode colidir

## Contexto

Dois cards diferentes nasceram com o **mesmo id/slug** `E2.6-f2`:

- `t_9669646d` (backend) — *`make web:dev` mascara falha do frontend*
- `t_78e02609` (qa) — *`db-setup.sh` grava o `.env` na raiz de onde roda*

Cada um escreveu sua evidência no **mesmo caminho** `docs/qa/e2-6-f2-evidencia.sh`. Na
integração das duas branches em `main`, o git parou em conflito `add/add` nesse arquivo:
não é conflito de conteúdo — são **dois documentos distintos com o mesmo nome**, e o git não
tem como escolher. Resolvido à mão pelo orquestrador, renomeando para
`e2-6-f2-webdev-evidencia.sh` e `e2-6-f2-db-setup-evidencia.sh`.

## Por que isso importa

O AGENTS.md diz que a evidência é versionada e que cada card tem um id. Se dois cards
compartilham slug, a evidência de um **sobrescreve** a do outro em qualquer integração, e a
prova de um dos dois desaparece sem ninguém notar (o merge "passa" quando os conteúdos são
idênticos; quando diferem, alguém resolve no impulso e perde uma das duas).

## Pedido

1. Registrar no `AGENTS.md`, na seção de regras de trabalho, que o **id do card é único e
   imutável** e que o **nome do arquivo de evidência inclui o id do card**.
2. Quando o assunto tiver duas frentes, dar ids distintos (`E2.6-f2a` e `E2.6-f2b`, ou dois
   ids de assunto) — nunca reaproveitar o mesmo id para dois cards.
3. Não é preciso renomear nada já integrado: os dois arquivos já estão com nomes distintos em
   `main` (merge `56391f7`). O pedido é a **regra**, para não repetir.

## Critério de pronto

`AGENTS.md` com a regra escrita e o motivo (colisão de evidência em integração), sem tocar
código. Evidência: o diff do `AGENTS.md` anexado ao card.
