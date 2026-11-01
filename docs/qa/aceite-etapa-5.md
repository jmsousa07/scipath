# Aceite da Etapa 5 — gerenciamento do material próprio

- **Card:** `t_77a27f71` (E5.10) · **perfil:** qa · **data (UTC):** 2026-11-01
- **Árvore medida:** branch `wt/t_77a27f71`, base `72405c8` (integração E5.9 no `main`); nenhum arquivo de produto foi alterado.
- **Contrato:** OpenAPI `0.6.0` · **rastreabilidade:** RF015–RF017, RF019; CA007–CA010; DRS §16, §17.1–§17.5, §21.3–§21.4; ADR-0011.
- **Log bruto principal:** `docs/qa/e5-10-evidencia-bruta-2026-11-01.log` (`make test`). Todos os comandos abaixo têm log bruto próprio em `docs/qa/e5-10-*-2026-11-01.log`.
- **Reprodução:** `bash docs/qa/e5-10-evidencia.sh` (executa os gates, `npm run verify`, Chromium e `infra/qa-harness.sh`; o harness recria `scipath_test`).

## Veredito

**RF015, RF016, RF017 e RF019: ATENDEM** na API, no banco de teste e na interface medida. **CA007 e CA008 agora atendem por inteiro**: a edição/retirada/exclusão cruzada foi tentada contra material de B, e o ato e o efeito da retirada foram medidos, fechando as partes que o P5 havia deixado não verificadas. Nenhum defeito de produto foi encontrado nesta rodada.

O gate de produto executou verde: `make test` (267 testes, 0 falhas/erros), `make lint` e `make contract:check` (todos exit 0). O `infra/qa-harness.sh` terminou com exit 0, mas seu subcritério de recriar `scipath_test` **não foi executado com sucesso**: `infra/db-test-setup.sh` exit 1 porque `sudo` exige autenticação em terminal. A suíte do próprio harness, mesmo assim, passou com 267/267. O setup é limitação do ambiente, não falha do produto. O harness também emitiu um ACHADO de permissão incondicional que contradiz sua própria execução e a leitura pós-harness; isso é defeito do instrumento, aberto no card `t_4d06c84f`.

## Veredito por critério de aceite

| Critério | Veredito | Razão |
|---|---|---|
| 1. RF015 — listar/ler só os próprios, três estados, vazio, paginação, privacidade | **ATENDE** | Testes de integração com A/B, PostgreSQL e HTTP; evidência §1. |
| 2. RF016 — edição, público reflete, imutáveis, 422, payload forjado | **ATENDE** | `EdicaoDeMaterialProprioIntegrationTest`, banco e leitura pública; evidência §2. |
| 3. RF017/RF019 — retirada, republicação, exclusão, cinco superfícies, retenção | **ATENDE** | Testes de estado e expurgo; evidência §3. |
| 4. DRS §17.4 — negativos individuais, status e ausência de efeito | **ATENDE** | Evidência §4; linha de B comparada antes/depois. |
| 5. CA007 e CA008 — adendo ao P5 | **ATENDE** | Ambas as partes antes não verificadas foram exercitadas; §5. |
| 6. Interface — páginas/formulário, exclusão, teclado/foco, 375/1440, reduced-motion | **ATENDE no escopo medido** | Chromium real + fixtures de teste; humanos/leitor de tela/outros motores ficam não provados (§6 e §8). |
| 7. §17.5 — `make test`, `make lint`, contrato e harness | **ATENDE para os três gates de produto; NÃO ATENDE para o provisionamento do harness nesta máquina** | `make test`, `make lint`, `make contract:check` exit 0. O passo `db-test-setup` do harness não autentica sem terminal/sudo; detalhado em §7. |
| 8. Limites e não provado | **DECLARADO** | Itens não verificados listados em §8; o MVP não é declarado pronto para produção. |

## 1. RF015 — listar e ler os próprios: ATENDE

**Comando:** `make test` · log bruto principal, classe `MateriaisPropriosIntegrationTest` (`Tests run: 11, Failures: 0, Errors: 0`). A classe usa `TestRestTemplate` contra o contexto Spring e PostgreSQL `scipath_test`; não usa mock de repositório.

- A lista da sessão A contém somente os materiais de A; a de B contém somente o material de B. São exercitados `PUBLICADO`, `RETIRADO` e `EXCLUIDO` na mesma visão do dono.
- O dono sem materiais recebe 200 e `items: []`; a paginação por páginas de 2 é igual à lista completa, sem repetir ou omitir item, inclusive com oito itens empatados em `atualizadoEm`.
- A leitura própria responde 200 nos três estados; a leitura de material alheio responde o mesmo 404 do inexistente e do 404 público, normalizando apenas `instance`.
- O corpo é exatamente o schema `MaterialProprio`, sem autor, identificador de conta, e-mail, hash, token, chave ou caminho interno. O log registra a varredura de dados privados/infra sem ocorrência.
- `page`/`size` inválidos recebem 400. O estado vazio é resposta 200 com envelope paginado.

**Saída relevante:** `docs/qa/e5-10-evidencia-bruta-2026-11-01.log` — linhas do bloco `[E5.2]` e resumo da classe, em torno das linhas 968–1018.

## 2. RF016 — editar: ATENDE

**Comando:** `make test` · mesma evidência bruta, classe `EdicaoDeMaterialProprioIntegrationTest` (`12/12`, 0 falhas/erros).

- Edição válida persiste os cinco campos; o `GET /api/materiais/{slug}` público devolve os metadados editados. `slug`, propriedade, estado, arquivo e `publicadoEm` permanecem; `atualizadoEm` avança.
- Categoria, tipo ou licença inexistente/inativa recebem 422 no campo correspondente; campos obrigatórios ausentes recebem 400. Conta `PENDENTE_VERIFICACAO` pode editar.
- Payload com `autorSlug`, `contaId`, `perfilId`, `slug` e `estado` é aceito sem trocar a propriedade nem os campos imutáveis; a comparação no banco confirma proprietário e data de publicação preservados.
- A tenta editar material de B: 404 e linha de B inalterada. Material `EXCLUIDO`: 404.

**Saída relevante:** no log principal, linhas 911–967: 422 de taxonomia, edição válida, leitura pública refletida, valores imutáveis no banco e recusas de acesso cruzado.

## 3. RF017/RF019 — retirada, republicação, exclusão e retenção: ATENDE

**Comando:** `make test` · classe `GerenciamentoDeEstadoDeMaterialProprioIntegrationTest` (`9/9`, 0 falhas/erros) e `ExpurgoDeRetencaoIntegrationTest` (`3/3`, 0 falhas/erros), ambas no log bruto principal.

- `PATCH` retira e `PATCH` republica; a suíte mede listagem, busca, detalhe, arquivo e perfil público do autor. Retirado/excluído desaparecem das superfícies; republicado volta ao público.
- Repetir o estado atual responde 200 sem escrita. `EXCLUIDO` enviado por `PATCH` responde 422; `DELETE` responde 204 e é lógico, com `excluido_em`/metadados preservados e o `file_asset` ainda VALIDADO antes da retenção.
- A tenta retirar e excluir o material de B: 404 indistinguível do inexistente e linha de B idêntica antes/depois. Escrita sem sessão = 401; com sessão, sem CSRF = 403.
- **Retenção executada de verdade** pelo serviço em banco e storage temporário: registro EXCLUIDO com 8 dias e binário é removido; relatório `examinados=2 purgados=2 falhas=0 janela=7d` (inclui registro sem binário), metadados permanecem; a segunda passada dá `examinados=0 purgados=0 falhas=0`. EXCLUIDO recente, PUBLICADO e RETIRADO são preservados. Um segundo teste força falha de storage, mede `2 purgados / 1 falha` e confirma retomada na passada seguinte.

**Saída relevante:** log principal, linhas 494–512 (relatórios de expurgo) e resumo de testes de RF017/RF019 na linha 857. A rotina usa dados de teste com datas relativas de 8 dias; não se aguardaram 7 dias de relógio real.

## 4. Negativos do DRS §17.4: ATENDEM

Os casos foram exercitados individualmente nos testes de integração acima:

| Tentativa | Medição | Evidência |
|---|---|---|
| Listar/ler rota privada sem sessão | 401 | `MateriaisPropriosIntegrationTest`, `[E5.2]` |
| Editar/retirar/excluir sem CSRF (com sessão) | 403 | `EdicaoDeMaterialProprioIntegrationTest` e `GerenciamentoDeEstadoDeMaterialProprioIntegrationTest` |
| A editar material de B | 404; banco de B inalterado | `[E5.3] PUT (A) no material de B -> 404` e `material de B no banco, antes e depois: inalterado` |
| A retirar/excluir material de B | 404 indistinguível do inexistente; linha de B inalterada | `recusaAcessoCruzadoNasDuasRotas` |
| Consulta de material retirado/excluído no público | 404 igual ao inexistente; o dono ainda lê o próprio | testes de leitura e das cinco superfícies públicas |
| `autorSlug`/`contaId`/`perfilId` adulterados | ignorados; propriedade conferida no banco | edição do material próprio, RF016 |
| Campos privados em payload público/próprio | schemas/varredura sem ocorrência de e-mail, hash, token, `contaId`, chave/caminho | testes E5.2/E5.3 e log |

## 5. CA007 e CA008 — adendo aos vereditos do P5

- **CA007 — ATENDE.** O P5 já havia medido autenticação/CSRF e autoria vinculada à sessão. Agora as três ações relevantes foram exercitadas contra material de B: editar, retirar e excluir; todas recusam sem efeito no banco de B.
- **CA008 — ATENDE.** O P5 havia medido o efeito público, mas não o ato do dono. Esta rodada executou o `PATCH` de retirada e o `DELETE` lógico, mediu as cinco superfícies, a republicação, a idempotência e o expurgo posterior.
- O laudo histórico `docs/qa/p5-aceitacao-mvp.md` não foi editado. Este é o adendo de veredito da Etapa 5.

## 6. Interface — ATENDE no escopo medido

**Comandos:** `cd frontend && npm run verify` e `node docs/qa/e5-10-navegador.mjs`. O segundo usa o build standalone de produção, Chromium real e fixtures de contrato **só para testes**; não usa dados de usuário nem altera código do produto.

- `npm run verify` exit 0: build, lint/typecheck, bateria `check:materiais-erros` 75/75, `check:editar-erros` 44/44, HTML de edição 13/13 e `check:states` com os 7 estados de página + 6 de formulário (13/13).
- No HTML entregue da lista SSR, foram confirmados: sessão expirada, sucesso, vazio, erro de servidor, não autorizado, endereço inválido e página além do fim. `carregando` é transitório e não é HTML entregue nessa rota SSR; a demonstração de estados do frontend o cobre, mas não foi alegado como documento observado.
- No navegador, em **375 px e 1440 px**: lista com três estados, sem overflow horizontal (`scrollWidth=375` e `1425`, respectivamente), sem canários de material fictício/campos privados; `prefers-reduced-motion: reduce` ativo; Tab alcança link de edição e campo de título com foco visível; cinco controles do formulário têm rótulos; o botão Salvar é alcançado por Tab e envia via Enter.
- 422 recebido pelo formulário (HTTP 422) fica em `edicao-categoria`, com `aria-invalid` e `aria-describedby` apontando para a mensagem existente.
- O `<dialog>` de exclusão abre com título acessível, texto “Não há como desfazer”, efeito público, retenção de 7 dias e alternativa reversível. Esc fecha sem enviar DELETE.
- **Screenshots:** `docs/qa/e5-10-painel-375.png` e `docs/qa/e5-10-painel-1440.png`. Foram gerados a partir de fixture de teste; não constituem conteúdo de produção nem aprovação visual humana.

**Saída bruta:** `docs/qa/e5-10-frontend-verify-2026-11-01.log` e `docs/qa/e5-10-browser-2026-11-01.log`.

## 7. Gates do §17.5 e instrumento

| Comando | Resultado observado | Log |
|---|---|---|
| `make test` | exit 0; 267 testes, 0 falhas/erros | `e5-10-evidencia-bruta-2026-11-01.log` |
| `cd frontend && npm run build` | exit 0; rotas `/painel/materiais` e `/painel/materiais/[slug]/editar` compiladas | `e5-10-build-2026-11-01.log` |
| `make lint` | exit 0; eslint + typecheck | `e5-10-lint-2026-11-01.log` |
| `make contract:check` | exit 0; Spectral sem erro | `e5-10-contract-2026-11-01.log` |
| `cd frontend && npm run verify` | exit 0; 13 estados + baterias | `e5-10-frontend-verify-2026-11-01.log` |
| `bash infra/qa-harness.sh` | exit 0, mas `infra/db-test-setup.sh` e `db-test-setup-2` saem 1 (`sudo: A terminal is required to authenticate`); a tabela E1.5 de “recriar banco do zero” aparece NÃO ATENDE. O teste em shell limpo dentro do harness passa 267/267. | `e5-10-infra-harness-2026-11-01.log` |

**Separação produto × instrumento/ambiente.** A recusa do `sudo` impede o passo de provisionamento do harness nesta sessão e não é defeito do produto. Pós-harness, a releitura independente confirmou `owner=scipath_test`, `show role=scipath_test`, membership `t`, banco `0/0/0`, portas livres e nenhum processo residual (`e5-10-db-recheck-2026-11-01.log`). O `infra/qa-harness.sh` também registra incondicionalmente um ACHADO afirmando owner `scipath` e falha de permissão, apesar de seu próprio `make test` verde e da leitura pós-harness. Esse defeito de instrumento virou **`t_4d06c84f`**; o ambiente F3 já existente (**`t_254b7c3d`**, bloqueado) recebeu comentário com os dados novos para reavaliação. Não foi criado card de defeito de produto.

## 8. O que esta verificação NÃO prova

1. Anonimização dos metadados após 90 dias: decisão continua pendente do dono, ADR-0011 §9 (**NÃO VERIFICADO**).
2. Leitor de tela real (NVDA/VoiceOver/TalkBack), outro motor de navegador (Firefox/Safari) e aprovação visual humana. Chromium e as medições automatizadas não substituem essas verificações.
3. MinIO/S3; a implementação testada usa storage local.
4. Entrega real de e-mail (os testes exercitam o outbox de desenvolvimento).
5. Integração ponta a ponta do Next servido com o jar Spring servido no mesmo navegador: testes do backend exercitam MVC/HTTP e PostgreSQL real no contexto de teste; a passada visual do Next usa fixtures de API compatíveis com OpenAPI.
6. Hospedagem, backup com restauração testada, termos/privacidade definitivos e canal de denúncia (ADR-0007). **O SciPath não é declarado pronto para produção.**

## Cards relacionados

- **Defeito de produto:** nenhum encontrado nesta rodada.
- **Instrumento:** `t_4d06c84f` — achado incondicional obsoleto no `infra/qa-harness.sh`.
- **Ambiente já reportado:** `t_254b7c3d` (F3, bloqueado por sudo) — comentário adicionado com a medição pós-harness; QA não alterou nem fechou esse card.
