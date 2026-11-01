# E5.5 — Backend: rotina de retenção — expurgo do binário de material excluído

- **Perfil:** backend · **Depende de:** E5.4 · **Status:** pronto para execução

## Contexto

O ADR-0005 §3 (b), ratificado pelo dono, fixa: exclusão lógica imediata, **binário purgado depois de
7 dias**, metadados retidos, rotina **diária, idempotente e observável** (RN012: "operações de
limpeza devem ser seguras e observáveis"). O card E4.5 registrou explicitamente que essa parte é da
Etapa 5 — é este card. `file_asset.estado_processamento` já tem o valor `PURGADO` esperando por ela.

## Entrada

`docs/adr/ADR-0005-...md` §3, `docs/adr/ADR-0011-...md` §6 e §9, `docs/01-arquitetura.md` §4.1,
`backend/src/main/java/com/scipath/material/` (coletor de órfãos do E4.5 — o modelo de rotina a
seguir, **não** o código a copiar), `Makefile` (alvo `coleta:orfaos`), V1 (`file_asset`,
`material.excluido_em` da migração do E5.4).

## Escopo

1. Rotina que encontra **materiais em `EXCLUIDO` com `excluido_em` anterior a agora − 7 dias** e
   remove o binário do storage, marcando o `file_asset` como **`PURGADO`**. Metadados (material e
   `file_asset`) **permanecem**.
2. **Nunca toca material vivo**: `PUBLICADO`, `RETIRADO` e `EXCLUIDO` dentro dos 7 dias ficam
   intactos — inclusive no arquivo. Órfão de publicação não é assunto desta rotina (é do coletor do
   E4.5): as duas não podem disputar o mesmo arquivo.
3. **Idempotente**: a segunda execução examina zero candidatos e remove zero; remover um binário já
   ausente **não** é falha (é o estado desejado) — é o que torna a retomada após interrupção segura.
4. **Observável**: cada execução tem identificador próprio e escreve, com o prefixo
   `[expurgo-retencao]`, uma linha por arquivo purgado e um resumo
   `execucao=<id> examinados=<n> purgados=<n> falhas=<n> janela=7d` — dá para auditar sem abrir o
   banco.
5. **Janela e disparo em configuração** (não em constante): `scipath.expurgo.janela` (padrão `7d`) e
   `scipath.expurgo.agendamento` (cron de 6 campos, padrão diário, **conferido na subida**: expressão
   inválida falha ao subir, não deixa rotina declarada e morta). No perfil de teste o agendamento é
   desligado, como no coletor de órfãos.
6. **Execução manual declarada**: alvo `make expurgo:retencao`, que roda **uma** passada sem abrir
   porta (mesmo desenho do `coleta:orfaos`).
7. Documente a rotina em `docs/01-arquitetura.md` §4.2 (a ser criada) e ajuste a última frase do
   §4.1, que hoje só aponta a Etapa 5.

## Fora de escopo

**Anonimização dos metadados após 90 dias** — pendência aberta e registrada (ADR-0011 §9,
`docs/01-arquitetura.md` §8): falta decidir quais campos e se o vínculo com a conta é removido; é
política de privacidade (RNF015) e não se inventa aqui. Nada desta rotina apaga metadado.
Frontend (E5.6). Coletor de órfãos (E4.5) — não reescrever.

## Critérios de aceite

1. Testes: material `EXCLUIDO` há 8 dias → binário removido e `file_asset` `PURGADO`; `EXCLUIDO` há
   1 dia, `PUBLICADO` e `RETIRADO` → **intactos**; segunda execução → zero candidatos; falha no meio
   (storage indisponível para um arquivo) → os demais seguem, falha contada, execução seguinte
   termina o serviço.
2. Saída real de uma execução colada em `docs/qa/e5-5-<data>.log` (contagem + prefixo
   `[expurgo-retencao]`), commitada.
3. `./mvnw -o test` verde com heap limitado; `make lint` e `make contract:check` verdes.

## Rastreabilidade

RF017, RN012; ADR-0005 §3, ADR-0011 §6, §9; DRS §9.0.12, §13.2, §17.5; RNF015 (parte aberta).
