# P5 — Setup portátil: `db-setup.sh` assume Linux com `sudo -u postgres`, e o repositório não tem remoto

- **Perfil:** gerente · **Status:** registrado, **não despachar** enquanto a fila estiver pausada
  (este card nasceu com `--triage` de propósito)

## Contexto

Achado ao levar o projeto para uma segunda máquina (a pessoal do dono, 2026-10-19). O guia de
portabilidade está escrito em `docs/rodar-em-outra-maquina.md`; o que **falta corrigir** ficou
registrado aqui.

## Achados medidos

1. **Bit de execução — corrigido.** `infra/db-setup.sh` estava `100644` (`-rw-rw-r--`) enquanto
   `db-test-setup.sh` estava `100755`: rodar `./infra/db-setup.sh` numa máquina nova dá
   `Permission denied`. Corrigido em `c63b512` (o modo é versionado no Git, então a cópia nova já
   vem certa).
2. **`sudo -u postgres` — não portátil.** O script cria role e bancos via `sudo -u postgres psql`.
   Isso funciona em Linux/WSL com sudo, mas **não no macOS**: o Homebrew roda o cluster como o
   usuário corrente e `sudo -u postgres` responde `unknown user: postgres`. O guia traz o
   equivalente manual; o script não sabe se virar.
3. **Sem remoto Git.** `git remote -v` é vazio: o projeto existe só na VPS. Não há clone, não há
   backup fora dela, e levar para outra máquina exige `rsync`/`git bundle` (documentado). É
   decisão do dono para onde publicar.

## Pedido

1. `db-setup.sh` (e `db-test-setup.sh`) devem detectar o superusuário disponível — tentar
   `sudo -u postgres`, cair para o usuário corrente quando o cluster for local do usuário — e
   dizer na saída qual caminho usou (nunca imprimir senha).
2. Um alvo `make doctor` que valide portabilidade: versão de JDK/Node, presença de `psql`, se o
   cluster responde, e se o mecanismo de superusuário funciona nesta máquina (hoje ele só confere
   ferramentas e arquivos).
3. Decisão de remoto (dono): publicar em GitHub e passar a usar `git clone`/`push` como caminho
   normal, aposentando o `rsync` manual. Enquanto não houver decisão, o guia mantém `rsync` e
   `bundle`.

## Critério de pronto

Uma máquina limpa (Linux ou macOS) sai do zero até `make api:dev` + `make web:dev` seguindo
**somente** `docs/rodar-em-outra-maquina.md`, sem passo manual fora dele, com o log da execução
colado como evidência.

## Rastreabilidade

ADR-0007 (sem Docker, ambiente local), ADR-0004 (ajustes de ambiente), `infra/README.md`;
`docs/rodar-em-outra-maquina.md`; commit `c63b512`.
