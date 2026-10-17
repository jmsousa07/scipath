# P4 — Harness E1.8 reprova por defeito do instrumento: reversão desatualizada pós-V4 e padrão de segredo com falso positivo

**Perfil:** qa · **Depende de:** E3.8 (achado da verificação independente da Etapa 3) · **Status:** pronto para execução

Achado da verificação E3.8 (card `t_76729dda`), medido em `docs/qa/e3-identidade-2026-10-17.log` seção 1.1 e no log bruto `docs/qa/e3-8-harness-2026-10-17.log`. Dois critérios do harness (`infra/qa-harness.sh`) reprovam por defeito do **instrumento** — o produto está correto nos dois casos.

**(a) E1.2 reversão exercitada e reversível → `restantes=8 remigrate=0`, exit=1.**

O passo "4.2 Drop na ordem inversa das dependências" (linhas ~196-206 do log bruto) derruba uma **lista fixa** de tabelas do V1, anterior à migração `V4__sessao.sql` da Etapa 3. Como `sessao` tem FK para `account` (`sessao_account_id_fkey`), o `DROP TABLE account` falha com "cannot drop table account because other objects depend on it"; o `psql -c` roda tudo numa transação única, então o erro aborta o lote inteiro e nenhuma tabela cai.

Reprodução: `bash infra/qa-harness.sh` e ler o passo 4.2.

Correção esperada: derivar a ordem do catálogo (consultar `information_schema`/`pg_constraint` e derrubar em ordem topológica) em vez de manter lista fixa — assim a próxima migração com FK não quebra o critério de novo. Depois, o harness deve voltar a `9 ATENDE`.

**(b) E1.5 nenhum segredo no repositório → `senha=0 padroes=10`.**

O critério real (valor do `.env` em arquivo versionado) passa: 0 arquivos. As 10 linhas que o padrão de segredo marca são todas legítimas:

- senha fictícia de script de verificação (`frontend/scripts/check-erros-sessao.mjs`, 3);
- token fictício de duplo de teste (`docs/qa/e2-8-fixture-api.mjs`, 3);
- nome de variável em código de teste (`backend/src/test/.../ApoioDeTestesDeToken.java`, `FalhaDeEnvioDeEmailIntegrationTest.java`, 3);
- uma linha de **texto** da própria evidência (`docs/qa/e3-8-evidencia.sh`, 1 — a string de grep que procura `#token=`).

Nenhuma em código de produção (`backend/src/main` e `frontend/src` dão 0).

Correção esperada: o padrão precisa separar segredo real de duplo de teste (excluir `**/src/test/**`, `**/scripts/check-*.mjs`, `docs/qa/**` do escopo, ou exigir **forma** de segredo, não só `chave=valor`), senão o critério reprova por artefato e ensina o time a ignorá-lo.

## Critério de pronto

`bash infra/qa-harness.sh` termina com **0 NÃO ATENDE**, sem afrouxar nenhum critério: o critério de segredo continua pegando segredo real (um valor do `.env` colado num arquivo versionado deve continuar reprovando). Log bruto do harness anexado como evidência.

## Rastreabilidade

E1.2, E1.5, E1.8; `infra/qa-harness.sh`; seção 1.1 de `docs/qa/e3-identidade-2026-10-17.log`.
