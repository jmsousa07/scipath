# E5.6 — Frontend: página "Materiais próprios" e o cliente da superfície de gerenciamento

- **Perfil:** frontend · **Depende de:** E5.8 (contrato v0.6 no `main`) · **Status:** pronto para execução

## Contexto

A rota `/painel/materiais` está em `PAGE_BLUEPRINTS.md` §12 desde a Etapa 1 e **não existe**: a área
do usuário (`/painel`) diz literalmente que a listagem "ainda não existe nesta versão". A partir
deste card, existe — com estado de publicação e as ações de editar, retirar, republicar e excluir
(RF015, RF017; CA008).

## Entrada

`PAGE_BLUEPRINTS.md` §9, §12 e a tabela de rotas; `DESIGN_SYSTEM.md`; contrato v0.6 (tag
`gerenciamento`); `docs/adr/ADR-0011-...md` §1–§3, §7; `frontend/src/lib/api/client.ts` e `tipos.ts`
(nasceram na Etapa 2), `frontend/src/app/painel/page.tsx`,
`frontend/src/components/ui/PageState.tsx`, `frontend/src/lib/sessao/`.

## Escopo

1. **Cliente da API** — funções e tipos para **as cinco rotas** de `/perfil/atual/materiais`
   (listar, ler, editar, mudar estado, excluir), com o helper de escrita que já existe
   (`X-CSRF-Token` da cookie `XSRF-TOKEN`, ver `lib/sessao/navegador.ts`). Este card é o **dono
   único** desses arquivos nesta etapa: as cinco funções nascem juntas, para o card E5.7 não
   precisar reabri-los.
2. **Página `/painel/materiais`** — lista paginada dos materiais do dono, com título, tipo,
   categoria, estado (`PUBLICADO`, `RETIRADO`, `EXCLUIDO`) e datas; link para a página pública quando
   `PUBLICADO`; **ações por estado**: `PUBLICADO` → editar, retirar, excluir; `RETIRADO` → editar,
   republicar, excluir; `EXCLUIDO` → **nenhuma ação** (estado terminal, ADR-0011 §2). A derivação da
   ação a partir do estado é de apresentação — o servidor é a autoridade (RNF010).
3. **Confirmação na exclusão** (RF017: "exige confirmação quando houver risco de perda de dados"):
   diálogo explícito dizendo que **não há como desfazer** e que o arquivo deixa de estar disponível
   em 7 dias. Retirar e republicar são reversíveis: não pedem diálogo.
4. **Sete estados de página** (§15.3): carregando, sucesso, **vazio** (com o primeiro passo de
   publicação: RF015 "o estado vazio orienta o primeiro passo"), erro de rede/servidor, não
   encontrado, não autorizado, sessão expirada (401 → login preservando `/painel/materiais`).
5. **Sucesso das ações** comunica o resultado (RF017: "o usuário recebe confirmação"), com o estado
   novo visível na lista sem recarregar a página inteira.
6. **Área do usuário** (`/painel`): o card e o link para `/painel/materiais` entram; **saem** as
   frases que dizem que a listagem não existe (são duas hoje) — prometer tela inexistente ou negar
   tela existente são o mesmo defeito.
7. **`PAGE_BLUEPRINTS.md`**: corrija a rota de edição na tabela e no §13 (`[id]` → `[slug]`, é o
   slug que o projeto usa como identificador canônico: RNF025) e ajuste o §12 ao que foi entregue.
8. **Privacidade e SEO**: página privada com `noindex`; nenhum dado privado no HTML entregue
   (RNF013) — inclua a checagem de que e-mail/hash/token/`contaId` não aparecem no documento.

## Fora de escopo

A tela de edição (E5.7). Backend. Filtro/ordenação por estado (não existem no contrato do MVP).

## Critérios de aceite

1. `npm run build`, `npm run lint` e `npm run typecheck` verdes, saída colada.
2. Bateria do HTML entregue (nos moldes das telas anteriores): **sem material fictício** (AGENTS
   regra 9) e, com a API simulada, os quatro estados da lista presentes — vazio orientando o
   primeiro passo, `PUBLICADO` com as três ações, `RETIRADO` com republicar, `EXCLUIDO` sem ação.
3. Navegador real, 375 px e 1440 px, uma jornada por teclado (foco visível, ações alcançáveis) e
   **um** screenshot por viewport, versionado em `docs/qa/`.
4. Evidência `docs/qa/e5-6-<data>.log` commitada com: saída dos três comandos, a bateria e as
   medições do navegador.

## Rastreabilidade

RF015, RF017; RN013, RNF003, RNF006–RNF008, RNF010, RNF013, RNF024; CA008, CA009; DRS §15.2, §15.3,
§17.3; ADR-0011 §2–§3, §7.
