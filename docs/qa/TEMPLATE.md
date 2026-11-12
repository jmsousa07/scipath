# Template de evidência de QA — SciPath

> Toda verificação entrega **duas** peças: o log bruto, guardado **fora do repositório**
> em `SCIPATH_EVIDENCIA_DIR` (padrão `$HOME/.scipath/evidencia`, no formato
> `<card>-<data>.log`), e o **aceite** versionado em `docs/qa/*.md`, com veredito, comandos e
> números (ADR-0012). Evidência que só existe no chat é evidência perdida (DRS §17.5, §21.3).
>
> Este template é o **contrato do que conta como prova**. Ele existe porque a verificação
> do F0.1 nunca aconteceu por falta de um instrumento repetível — o mesmo motivo do
> `infra/qa-harness.sh`.

## 1. Cabeçalho — identifique a verificação antes da primeira linha de saída

O log abre com um bloco fixo. Sem ele, um `PASS` solto não diz **o que** passou, **onde**
nem **contra qual código**:

```
Card:        <id> (<docs/cards/<id>-<slug>.md>)
Perfil:      qa
Branch:      <branch>
Worktree:    <caminho absoluto>
HEAD:        <sha curto + assunto do commit verificado>
Data:        <AAAA-MM-DD> (UTC)
Ambiente:    SO, kernel, vCPU, RAM, versões de java/node/make, banco
Comando:     <o comando exato, colável, que reproduz o log>
```

**Regra do HEAD:** a evidência vale para o commit listado. Se o código mudar, o log perde
validade — não recicle log antigo sob um commit novo.

## 2. Comando exato e saída completa

- **Comando colável**, não descrito ("roda o build"). Quem lê precisa reproduzir sem
  adivinhar. Inclua as variáveis de ambiente que importam.
- **Saída completa**, não um resumo. Se for longa, ela pode ir em arquivo próprio
  (`docs/qa/<card>-<comando>.log`) e o resumo citar o arquivo — mas o resumo **não**
  substitui a saída.
- **Determinismo:** prefira comandos que não dependem do ambiente de quem rodou. A suíte
  de backend, por exemplo, é verificada de shell limpo:
  `env -i PATH=/usr/bin:/bin HOME=$HOME make test`. Se o comando depende de estado
  invisível, diga qual estado é esse.
- **Sem cor / sem binário:** limpe códigos ANSI (`| sed -r 's/\x1B\[[0-9;]*[A-Za-z]//g'`)
  — log com escape vira "binary file" para quem for revisar com `grep`.

## 3. Veredito por critério — nunca "tudo ok" sem lista

O log termina com uma tabela em que **cada critério dos cards cobertos** tem veredito
explícito e a saída que o sustenta:

| Veredito | Quando usar |
|---|---|
| **ATENDE** | a saída colada sustenta o critério, sem ressalva |
| **NÃO ATENDE** | a saída contradiz o critério — descreva de forma reproduzível (comando + saída + esperado) |
| **NÃO VERIFICADO** | não foi executado (falta de ferramenta, fora de escopo, dependência inexistente) — **liste o que ficou de fora e por quê** |

Um critério sem veredito é um critério não verificado. Omitir é pior do que reprovar:
reprovação vira card de correção, omissão vira defeito que ninguém vê.

## 4. Obrigações específicas do SciPath

- **Negativos contados junto com os positivos.** Não basta provar que o caminho feliz
  funciona: cole também a recusa (payload inválido → 400, migração quebrada → não sobe,
  categoria inexistente → FK recusa). É onde os defeitos moram.
- **Contra dados reais, nunca mock.** A contagem publicada é a do banco (§20.1); teste de
  integração fala com o PostgreSQL local.
- **Segredo nunca entra no log.** O `.env` é carregado com `set -a` só para `psql`/`flyway`;
  a senha não é ecoada. O log fecha com a conferência de que o valor da senha aparece 0
  vezes no repositório e no próprio log.
- **Higiene de recursos no fim.** Portas 8080/3000 livres, nenhum `java`/`surefire`/`next`
  deixado vivo (AGENTS.md, máquina de ~954 MB).
- **Reversibilidade.** O que o DoD chama "reversível" tem procedimento **exercitado**, não
  promessa: o log mostra a reversão rodando e a reaplicação em seguida (E1.2).

## 5. Seção "o que NÃO foi verificado"

Toda evidência de QA termina nomeando o que ficou de fora e por quê. Exemplos recorrentes
neste projeto: leitor de tela real (verifica-se a marcação que ele consome), jornadas de
ponta a ponta que dependem de etapas ainda não implementadas, backup restaurado e
hospedagem (ADR-0007). **Declarar o não verificado é parte do veredito — não é admissão de
falha.**

## 6. Instrumento

O harness que materializa este template é `infra/qa-harness.sh`:

```bash
bash infra/qa-harness.sh | tee docs/qa/e1-base-$(date -u +%Y-%m-%d).log
```

Ele roda do zero (recria o banco de teste, aplica e reverte migrações, roda a suíte em
shell limpo, valida o contrato, mede o frontend, faz a higiene) e imprime o veredito por
critério. A conferência **visual** (screenshot 375/1440, foco, contraste) é feita à parte,
com o navegador, e anexada ao log — o harness não a substitui.
