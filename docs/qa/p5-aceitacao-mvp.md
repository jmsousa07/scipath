# Aceitação do MVP — os 12 critérios globais do DRS §16 (CA001–CA012)

- **Card:** `t_f498b3aa` (P5) · **perfil:** qa · **data (UTC):** 2026-11-01
- **Repositório:** `$HOME/Documents/scipath` · **branch do card:** `wt/t_f498b3aa`
- **HEAD medido:** `bc371c0` (`merge(wt/t_d0b855b8): E1.5-f1 — uma dona para os objetos de scipath_test`) · **main:** `bc371c0` — o worktree está **no mesmo commit** do `main`
- **Comando (a saída é a evidência, versionada):**
  `bash docs/qa/p5-aceitacao.sh 2>&1 | tee docs/qa/p5-aceitacao-mvp-2026-11-01.log`
- **Log bruto:** `docs/qa/p5-aceitacao-mvp-2026-11-01.log` (instrumento: `docs/qa/p5-aceitacao.sh`)
- **Rastreabilidade:** DRS §16 (CA001–CA012), §17.5, §21.3; `docs/00-visao.md` §9; AGENTS.md (definição de pronto)

## 1. Veredito

| CA | Critério (DRS §16) | Veredito |
|---|---|---|
| CA001 | Descoberta pública | **ATENDE** |
| CA002 | Contexto do material | **ATENDE** |
| CA003 | Identidade pública | **ATENDE** |
| CA004 | Cadastro e sessão | **ATENDE** |
| CA005 | Publicação completa | **ATENDE** |
| CA006 | Validação de arquivo | **ATENDE** |
| CA007 | Autorização | **ATENDE** (a metade "editar/excluir material de outro" é da Etapa 5 — ver §2.7) |
| CA008 | Retirada de publicação | **ATENDE (efeito) · NÃO VERIFICADO (ato)** — ver §2.8 |
| CA009 | Responsividade e acessibilidade | **ATENDE** |
| CA010 | Qualidade de entrega | **ATENDE** |
| CA011 | SEO básico | **ATENDE** |
| CA012 | Transparência | **ATENDE** |

`resumo: 12 critérios avaliados · 12 ATENDE · 1 com parte NÃO VERIFICADO · 0 NÃO ATENDE`
(o log traz a frase na seção "VEREDITO POR CRITÉRIO DE ACEITE", com a base de cada um).

Nada foi omitido: as partes não medidas estão na §2 e na §4, cada uma com a razão. Os quatro
defeitos reais encontrados viraram card (§3) — **este card não corrigiu nada** (AGENTS.md regra 5:
quem implementa não verifica).

## 2. Critério por critério — critério, comando, saída, evidência

### 2.1 CA001 — Descoberta pública
**Critério.** *"Um visitante sem conta consegue explorar materiais públicos, pesquisar por
palavra-chave, aplicar filtros e abrir a página de um resultado."*
**Comando.** `curl -s "$API/materiais?q=<termo único>"` e as variantes de filtro/paginação — §7 do log.
**Saída (recorte).** listagem 200 sem cookie nenhum; filtro por `categoriaSlug` e `tipoSlug`
encontram o material; conjunto vazio devolve `"items":[]` com 200; busca pelo termo único, pela
forma **acentuada** (`verificação`) e pela **sem acento** (`verificacao`) encontram o mesmo slug;
abrir o resultado responde 200; `page=0&size=1` e `page=1&size=1` não repetem item.
**Evidência.** `docs/qa/p5-aceitacao-mvp-2026-11-01.log` §7 (requisições cruas em `$PROBE/b-7.*`).

### 2.2 CA002 — Contexto do material
**Critério.** *"A página de material mostra os metadados obrigatórios, autor, categoria, licença e
ação de acesso ao arquivo."*
**Comando.** `curl -s "$API/materiais/<slug>"` — §8 do log.
**Saída (recorte).** o detalhe traz título, descrição, autor (`slug`+`nomePublico`), categoria,
tipo, licença com `identificador` (`CC-BY-4.0`), `urlReferencia` (creativecommons.org) e
`descricaoCurta`, o bloco `arquivo` (`nomeOriginal`, `tamanhoBytes`, `disponivel:true`), `estado` e
`publicadoEm`; `tamanhoBytes` bate com o binário e a ação de acesso
(`GET /materiais/{slug}/arquivo`) responde 200; nenhum e-mail, hash, `chaveInterna`, caminho de
storage ou identificador de conta no corpo (RNF013).
**Evidência.** mesmo log, §8.

### 2.3 CA003 — Identidade pública
**Critério.** *"O visitante consegue abrir o perfil do autor a partir do material e consultar
outros materiais públicos desse autor."*
**Comando.** `curl -s "$API/materiais/<slug>"` → `slug` do autor; `curl -s "$API/autores/<slug>"` — §9.
**Saída (recorte).** o material entrega o `slug` do autor; `GET /autores/{slug}` responde 200,
traz `nomePublico` e lista os **dois** materiais públicos da autora, **sem** listar o material do
outro autor; perfil inexistente → 404; nenhum dado privado no perfil.
**Evidência.** mesmo log, §9.

### 2.4 CA004 — Cadastro e sessão
**Critério.** *"Uma pessoa consegue criar conta, autenticar-se e encerrar a sessão sem expor
credenciais."*
**Comando.** `POST /api/contas`, confirmação pelo outbox (`#token`), `POST /api/sessoes`,
`DELETE /api/sessoes` — §3 do log.
**Saída (recorte).** três contas criadas (201) nascem `PENDENTE_VERIFICACAO`; e-mail repetido → 409;
confirmação pelo outbox → 204 e conta `ATIVO`; a senha guardada é `$2a$12$…` (60 caracteres) e
**não** aparece em claro em resposta alguma; logout → 204 e o **mesmo cookie reapresentado não
autentica mais** (`autenticado:false`, invalidação no servidor); login 200; senha errada e e-mail
inexistente devolvem **401 de corpo idêntico** (sem oráculo de existência); nenhuma resposta de
sessão carrega e-mail, `senha`, hash, `contaId` ou `accountId`.
**Evidência.** mesmo log, §3. **Achado:** o contrato publica `DELETE /sessoes/atual` e a aplicação
responde 405 ali — o logout medido usou `DELETE /api/sessoes`, a rota que a aplicação tem (§3.4 do
log). Vira card F1.
**Não provado.** entrega real de e-mail (canal = outbox de desenvolvimento, ADR-0003 (f)).

### 2.5 CA005 — Publicação completa *(um dos dois "órfãos" da rastreabilidade)*
**Critério.** *"Um usuário autenticado consegue publicar um PDF válido com os metadados
obrigatórios e recebe uma URL pública funcional."*
**Comando.** `curl -X POST "$API/materiais"` (multipart, sessão da conta ATIVO) — §4 do log.
**Saída (recorte).** 201 com `slug`, `"estado":"PUBLICADO"` e `urlPublica:"/materiais/…"`; no banco,
`material` + `file_asset` com `estado_processamento=VALIDADO`, `nome_original_seguro` preservando o
espaço interno e chave interna em UUID; o binário está no armazenamento e a área de recebimento
ficou vazia; `GET /materiais/{slug}` 200 e `GET /materiais/{slug}/arquivo` 200 com o arquivo
**byte a byte igual** ao enviado (`cmp`), `application/pdf` e `Content-Disposition` com o nome
sanitizado; conta `PENDENTE_VERIFICACAO` → 403 com "Confirme seu e-mail para publicar material.";
sem `declaracaoDireito` → 422; categoria inexistente → 422; nenhuma dessas tentativas criou material.
**Evidência.** mesmo log, §4.

### 2.6 CA006 — Validação de arquivo *(o segundo "órfão")*
**Critério.** *"Arquivos não permitidos, corrompidos ou acima do limite configurado são recusados
sem criar material publicamente acessível."*
**Comando.** cinco publicações negativas — §5 do log.
**Saída (recorte).** `.pdf` sem assinatura → 415; PNG → 415; PDF válido declarado `text/plain` →
415; nome sem extensão `.pdf` → 415; PDF de 32 MB → **413** com `O arquivo excede o limite de 30
MB.`; nome `../../etc/passwd.pdf` → 201 com **`passwd.pdf`** guardado (nenhum arquivo fora do
armazenamento; chave interna em UUID; o download sugere o nome limpo). Nenhuma recusa criou
`material`, `file_asset` ou binário; a área de recebimento ficou vazia (RN012).
**Evidência.** mesmo log, §5.

### 2.7 CA007 — Autorização
**Critério.** *"Um usuário não consegue editar ou excluir materiais de outro usuário, mesmo
manipulando a requisição fora da interface."*
**Comando.** `POST /api/materiais` sem sessão / sem CSRF / com campos de autor forjados;
`PUT|PATCH|DELETE /api/materiais/{slug}`; `PUT /api/perfil/atual` — §6 do log.
**Saída (recorte).** sem sessão → **401**; com sessão e sem `X-CSRF-Token` → **403**; conta
`PENDENTE_VERIFICACAO` → 403; o corpo com `autorSlug`/`contaId`/`email` de **outra** pessoa é
ignorado e o autor gravado é **o da sessão** (medido no banco: B publica declarando A, o autor é B);
`PUT`/`PATCH`/`DELETE` em `/materiais/{slug}` → **405** (a operação não existe no contrato v0.5.0);
`PUT /perfil/atual` é sempre o perfil da sessão (401 sem sessão) e editar o próprio perfil não toca
o de A.
**Evidência.** mesmo log, §6.
**NÃO VERIFICADO (declarado).** a metade do critério que fala em *editar ou excluir material de
outro usuário* não é executável nesta entrega: as duas operações **não existem** (Etapa 5). O que
existe — propriedade decidida pela sessão, autoria não negociável no corpo, 401/403 — está medido.
Card F4 especifica a Etapa 5.

### 2.8 CA008 — Retirada de publicação
**Critério.** *"Ao retirar um material, ele deixa de aparecer na busca e a URL de arquivo deixa de
permitir acesso público, conforme política definida."*
**Comando.** estado aplicado no banco (`update material set estado_publicacao=…`) e a mesma sonda
pública quatro vezes — §10 do log.
**Saída (recorte).**

| estado | listagem | busca | detalhe | arquivo | perfil do autor |
|---|---|---|---|---|---|
| `PUBLICADO` | presente | aparece | 200 | 200 | presente |
| `RETIRADO` | **ausente** | **ausente** | **404** | **404** | ausente |
| `EXCLUIDO` | ausente | ausente | 404 | 404 | ausente |
| republicado | presente | aparece | 200 | 200 | presente |

O 404 do retirado é **idêntico** ao 404 de um slug que nunca existiu (sem revelar existência, RN002);
o binário permanece no armazenamento (quem apaga é o coletor de órfãos, não a retirada).
**Evidência.** mesmo log, §10.
**Veredito: ATENDE (efeito) · NÃO VERIFICADO (ato).** O **ato de retirar** pelo dono não existe no
MVP: `DELETE`/`PATCH`/`PUT /api/materiais/{slug}` → 405 (§10.5). Não foi declarado "ATENDE" porque
metade do critério — "ao retirar um material" — não é executável nesta entrega. É a Etapa 5 (DRS
§18) e está fora das dependências E1–E4 deste card; virou o card F4.

### 2.9 CA009 — Responsividade e acessibilidade
**Critério.** *"As jornadas essenciais funcionam em desktop e celular e podem ser executadas por
teclado, com foco visível e mensagens associadas aos campos."*
**Comando.** bateria estática (`npm run`) — §10b do log; navegador —
`CHROMIUM_BIN=… BASE_URL=$WEB ROTAS_JSON=… node docs/qa/e2-11-navegador.mjs` (§13.2),
`node docs/qa/e2-14-documento.mjs` (§13.3), `node docs/qa/p5-teclado.mjs` (§13.4).
**Saída (recorte).** `check:layout` 4/4 regras em **375 e 1440 px**, `check:states` **13/13**
estados, `check:tokens`/`check:style`/`typecheck`/`lint`/`build` exit 0; navegador real nas
**quatro rotas da jornada** (`/`, `/explorar`, `/materiais/{slug}`, `/u/{slug}`) em 375 e 1440:
sem rolagem horizontal, foco visível nas 14 paradas de `Tab`, contraste AA no `<main>`, movimento
reduzido — **30 ATENDE · 0 NAO ATENDE**; contraste do **documento inteiro** (cabeçalho e rodapé
incluídos) **0 amostra abaixo de AA** em 8 combinações; jornada de busca **executada só com
teclado** (`Tab` → digitar → `Enter`) nas duas larguras, com o material encontrado no resultado.
**Evidência.** `docs/qa/p5-aceitacao-mvp-2026-11-01.log` §10b e §13; **prints**
`docs/qa/p5-jornada-375.png` e `docs/qa/p5-jornada-1440.png`; instrumento de teclado
`docs/qa/p5-teclado.mjs`.
**Ressalva declarada.** o alvo **agregado** `npm run verify` não chega ao fim em Node 22 neste
ambiente (achado da §10b, card F2): os passos que sustentam o critério foram medidos **um a um**.
**Não provado.** leitor de tela real (NVDA/VoiceOver): mede-se a marcação que ele consome
(`label[for]`, `aria-describedby`, `aria-invalid`).

### 2.10 CA010 — Qualidade de entrega
**Critério.** *"Build e testes passam; migrações são reproduzíveis; configuração de ambiente está
documentada; não há segredos commitados; erros críticos não são silenciosamente ignorados."*
**Comando.** `make contract:check`, `make db:migrate:test` (duas vezes), `make test`, `make lint`,
`git grep` do valor da senha, `grep` das variáveis em `docs/`, `AGENTS.md`, `infra/`, `Makefile`,
`application.yml`; §2.3 — §1 do log.
**Saída (recorte).** `make contract:check` exit 0 **e o mesmo Spectral REPROVA um contrato quebrado**
(falsificação do gate, §1.1a); migrações aplicadas **do zero** (`1,2,3,4`) e a reaplicação é
idempotente (`Schema "public" is up to date`); `make test` **exit 0** — `Tests run: 228, Failures: 0,
Errors: 0, Skipped: 0`, `BUILD SUCCESS`; `make lint` exit 0; **0** arquivos versionados com o valor
da senha do `.env` e **0** padrões de segredo em código de produto; **9/9** variáveis de ambiente
documentadas; as respostas de erro são `problem+json` (400 de corpo ilegível, 404 de recurso
inexistente, 415 de tipo não suportado) — nada é devolvido como 200 silencioso.
**Evidência.** mesmo log, §1 e §2.3.
**Achado de ambiente (registrado, com card F3):** neste cluster, `make db:migrate:test` (plugin do
Maven) deixa as tabelas com dono `scipath` e `show role` pela role de desenvolvimento devolve
`none` — a provisão do passo 4 de `infra/db-test-setup.sh` (card E1.5-f1) não está aplicada, e
`make db:migrate:test && make test` resulta em 131 erros `permission denied for table material`. A
aceitação mediu `make test` no estado que a suíte declara (schema vazio; ela mesma migra), e o
achado está aberto com a reprodução. O script de provisão exige `sudo`, que nesta máquina pede senha.

### 2.11 CA011 — SEO básico
**Critério.** *"Páginas públicas entregam conteúdo principal indexável, metadados relevantes e URLs
estáveis; páginas privadas não são indexadas."*
**Comando.** `curl` no **build de produção** servido (`next start` do artefato standalone) contra a
API real — §11 do log; sem executar JavaScript.
**Saída (recorte).** `/`, `/explorar`, `/materiais/{slug}` e `/u/{slug}` respondem 200 e o HTML
entregue contém o conteúdo principal (o link do material publicado aparece na listagem; o detalhe
traz título e descrição); **as quatro** rotas têm `<title>`, `meta description` e `rel="canonical"`;
o canonical do detalhe aponta para o slug estável; **dois pedidos da mesma URL devolvem sha256
idêntico**; `/painel`, `/painel/publicar` e `/painel/perfil` (com sessão, 200) são
`noindex, nofollow`.
**Evidência.** mesmo log, §11.
**Medido e declarado:** `/robots.txt` e `/sitemap.xml` não existem (`404`) — CA011 não os exige;
ficam registrados para não virar implícito.

### 2.12 CA012 — Transparência
**Critério.** *"A plataforma não afirma que materiais ou afiliações foram verificados, revisados por
pares ou certificados quando isso não ocorreu."*
**Comando.** `grep` das alegações no HTML das quatro páginas públicas, no contrato e no detalhe;
`curl` em `/termos` e `/privacidade` — §12 do log.
**Saída (recorte).** **0** ocorrências de "revisado por pares", "certificado", "selo", "verificação
institucional", "curadoria" nas quatro páginas; o contrato não tem campo de selo/certificado e
`afiliacaoDeclarada` é declaração do autor (RN011); a licença **e o que ela permite**
(`descricaoCurta`, `CC BY`) aparecem na página do material; `/termos` e `/privacidade` → 200.
**Evidência.** mesmo log, §12.

## 3. Defeitos encontrados — todos viraram card (com reprodução)

| Card | Defeito | Onde |
|---|---|---|
| **F1** `t_3b46f764` | Logout: o contrato publica `DELETE /api/sessoes/atual` e a aplicação responde **405** ali; o mapeamento real é `DELETE /api/sessoes` (`SessoesController`) | §3.4 |
| **F2** `t_37566841` | `npm run verify` não roda até o fim em Node 22 (`ERR_UNKNOWN_FILE_EXTENSION` em `check:erros-sessao`/`perfil-erros`/`publicar-erros`); a correção existe (`1b4cd8a`, branch `wt/t_8e24d2d3`) e **não está integrada** | §10b |
| **F3** `t_254b7c3d` | `infra/db-test-setup.sh` §4 não aplicado nesta máquina: `make db:migrate:test && make test` → 131 erros `permission denied for table material`. Exige `sudo` (ação humana) — card nasce **bloqueado** | §1.3 |
| **F4** `t_a8e75a19` | Etapa 5 (Gerenciamento) não existe: listar/editar/retirar próprios materiais — é o que deixa CA007 e CA008 parcialmente **NÃO VERIFICADOS** | §2.7, §2.8 |

Nenhum foi corrigido aqui: o QA mede e reporta (AGENTS.md regra 5, "o QA não corrige").

## 4. O que esta aceitação NÃO provou (declarado, nunca omitido)

1. **CA008 — o ato de retirar** (endpoint/tela do dono): não existe no MVP (§2.8).
2. **CA007 — editar/excluir material de outro usuário**: não existe operação para tentar (§2.7).
3. **CA004 — entrega real de e-mail**: o canal exercitado é o outbox de desenvolvimento (ADR-0003 (f)).
4. **CA009 — leitor de tela real** (NVDA/VoiceOver/TalkBack): mede-se a marcação que ele consome.
5. **CA009 — outros motores de navegador** (Firefox/Safari): a passada é do Chromium headless desta máquina.
6. **Limite de upload ≠ 30 MB**: medido o limite corrente do ADR-0005, não o caminho de configuração trocada.
7. **MinIO/S3**: o armazenamento é disco local atrás do `StorageService` (ADR-0005); outro provedor não foi exercitado.
8. **Hospedagem, backup com restauração testada, termos/privacidade definitivos e canal de denúncia**: ADR-0007 — fora do MVP desta etapa. **O sistema não é declarado pronto para produção.**
9. **Conferência visual humana**: os dois PNGs são do instrumento (375 e 1440 px); ninguém olhou a tela e aprovou o desenho.
10. **Ordem de tabulação completa do produto** e zoom de 200%: medidos 14 paradas por página, não a página inteira.

## 5. Ambiente da prova

- Banco: **`scipath_test`** — o banco de TESTE do projeto (nunca o de desenvolvimento `scipath`); as
  jornadas rodam contra a API do produto (jar, perfil `dev`) apontada para ele, com armazenamento,
  área de recebimento e outbox **dedicados** (fora do repositório, removidos no fim).
- A API é o **jar do produto** (`./mvnw package`), não um duplo de teste; o frontend é o **build de
  produção** servido (`output: 'standalone'`), com `API_BASE_URL` apontando para a mesma API.
- Ao fim: nenhum processo vivo, portas 8080/3100 livres, `scipath_test` devolvido vazio (o estado
  que a suíte espera) e o banco de desenvolvimento intacto — tudo conferido na §14 do log.
- Sem Docker (ADR-0007); PostgreSQL 16 nativo em `127.0.0.1:5432`.

## 6. Reprodução

```bash
cd $HOME/Documents/scipath/.worktrees/t_f498b3aa      # ou o worktree do card
bash docs/qa/p5-aceitacao.sh 2>&1 | tee docs/qa/p5-aceitacao-mvp-$(date -u +%F).log
```

O script recria o banco de teste do zero, empacota e sobe a API e o build do frontend, executa as
jornadas, roda a passada de navegador e limpa tudo no fim. A saída **é** a evidência: cada aferição
tem o valor esperado, o obtido e a origem.
