# E5.2 — Backend: listar e ler os materiais próprios

- **Perfil:** backend · **Depende de:** E5.8 (contrato v0.6 no `main`) · **Status:** pronto para execução

## Contexto

RF015 (E): o usuário autenticado consulta **os materiais que publicou**, com estado de publicação e
ações disponíveis. Dois dos três critérios são de backend: a lista é **limitada aos materiais da
sessão** e ela **não substitui a autorização** (esconder na tela não protege nada — RNF010, RN013).
Habilita a tela `/painel/materiais` (E5.6) e a leitura que pré-preenche a edição (E5.7).

## Entrada

`contracts/openapi.yaml` v0.6 (tag `gerenciamento`), `docs/adr/ADR-0011-...md` §1–§3,
`backend/src/main/java/com/scipath/material/` (`MateriaisController`, `MateriaisService`,
`MateriaisRepositorio`, `DetalheDoMaterial`), `EstadoPublicacao`, `Pagina`.

## Escopo

1. **`GET /api/perfil/atual/materiais`** — lista paginada dos materiais do perfil da sessão
   (envelope `Pagina`: `items`, `page`, `size`, `total`), teto de `size` = 100 recusado com 400
   (nunca truncado).
2. Ordenação **`atualizadoEm` DESC com desempate por `id` ASC**, documentada na resposta; o
   desempate é o que garante que paginar não repita nem pule item (RNF021). Nenhum filtro nem
   ordenação alternativa no MVP.
3. **Os três estados aparecem** — `PUBLICADO`, `RETIRADO` e `EXCLUIDO` — porque é a visão do dono
   (RF015 + PAGE_BLUEPRINTS §12); o filtro por estado do público (RN002) **não** se aplica aqui.
4. **`GET /api/perfil/atual/materiais/{slug}`** — o material do dono em qualquer dos três estados
   (a edição de um `RETIRADO` precisa dele: o detalhe público responde 404).
5. **Material de outra conta ou inexistente → 404 com o mesmo corpo** do 404 público, sem revelar
   existência (ADR-0011 §3). Slug fora do formato → 400 (o endereço está malformado, não é o
   recurso que falta).
6. `arquivo.disponivel = false` quando o binário já foi purgado (`file_asset.estado_processamento =
   'PURGADO'`) — a tela não oferece link que responderia 404.
7. Nenhum campo privado na resposta (RNF013): sem e-mail, sem identificador de conta, sem
   `profile_id`, sem chave de storage. Sem `autor` no corpo: é sempre o dono.
8. Controller e serviço próprios (`MateriaisPropriosController` / `MateriaisPropriosService`, sob
   `/api`): `MateriaisController` é do público e não deve ser reescrito por esta etapa. Leitura não
   exige `X-CSRF-Token`.

## Fora de escopo

Editar, retirar, republicar, excluir (E5.3, E5.4). Frontend (E5.6, E5.7). Filtro por estado.

## Critérios de aceite

1. Testes de integração: lista paginada do dono com os três estados; **A não vê material de B**
   (e a resposta de A para o slug de B é 404 idêntico ao de slug inexistente, byte a byte no corpo);
   vazio é 200 com `items: []`; `page`/`size` fora da faixa → 400; `size` acima de 100 → 400 (não
   truncar); paginação estável (duas páginas não repetem nem omitem item), inclusive com vários
   materiais no mesmo `atualizadoEm`.
2. `401` sem sessão nas duas rotas.
3. Teste de resposta sem dado privado (RNF013) e sem `autor`.
4. `./mvnw -o test` verde com heap limitado (`MAVEN_OPTS=-Xmx384m`), saída colada em
   `docs/qa/e5-2-<data>.log`, commitada. `make contract:check` e `make lint` verdes.

## Rastreabilidade

RF015, RN001, RN002, RN013, RNF010, RNF013, RNF021; DRS §7.4, §8.3.4, §15.2, §17.1–§17.2; ADR-0011.
