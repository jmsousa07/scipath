# E4.5 — Backend: coletor de arquivos órfãos (idempotente e observável)

- **Perfil:** backend · **Depende de:** E4.4 · **Status:** pronto para execução

## Contexto

O RN008 admite que a transação de publicação falhe: o arquivo já movido para o storage fica sem
material vivo. Sem coleta, isso vira lixo permanente e oculto. O RN012 exige que operações de
limpeza sejam **seguras e observáveis**. O ADR-0005 §3 fixa o coletor diário, idempotente, que
**nunca** apaga arquivo referenciado por material vivo e **registra a contagem** do que removeu.

Escopo aqui: os **órfãos de publicação**. A purga de material `EXCLUIDO` (7 dias) e a anonimização
de metadados (90 dias) são da Etapa 5 (RF017) — card próprio.

## Escopo

1. Rotina de coleta que encontra `file_asset` sem material vivo associado e mais antigo que a
   janela de segurança, e remove o arquivo do storage e o registro, **na ordem que não deixa
   referência pendente**.
2. **Idempotente**: rodar duas vezes seguidas não muda o resultado da segunda (teste explícito).
3. **Nunca apaga arquivo referenciado por material vivo** — inclusive material `RETIRADO`
   (retirado continua vivo: republicável) e `EXCLUIDO` dentro da janela de retenção.
4. **Observável**: registra contagem de examinados, removidos e falhas, com identificador de
   execução; a saída é inspecionável sem abrir o banco (§RN012, RNF de observabilidade).
5. **Testado no caso de falha no meio**: interrupção durante a remoção não deixa o sistema em
   estado pior — a execução seguinte termina o serviço.
6. Agendamento **declarado** (não silencioso): a forma de disparo (agendador da aplicação ou
   comando no `Makefile`) fica registrada na documentação, com o comando de execução manual para
   verificação.

## Fora de escopo

Purga de `EXCLUIDO`/anonimização de metadados (Etapa 5). Interface de administração.

## Critérios de aceite

1. Suíte cobrindo: órfão removido; arquivo de material vivo **intacto** (público, retirado e
   excluído dentro da janela); segunda execução sem efeito; interrupção no meio e retomada.
2. A saída de uma execução real (contagem + o que removeu) colada na evidência
   `docs/qa/e4-5-<data>.log`.
3. `./mvnw -o test` verde (heap limitado); lint e contrato verdes.

## Rastreabilidade

RN008, RN012; ADR-0005 §2 (órfãos) e §3; DRS §8.3.2, §13.2.
