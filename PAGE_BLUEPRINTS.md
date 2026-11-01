# PAGE_BLUEPRINTS.md — páginas, rotas e estados obrigatórios

> Derivado do **DRS §15** (responsabilidade funcional das páginas) e do contrato visual em
> `DESIGN_SYSTEM.md`. Estilo detalhado pertence ao design system; **aqui** ficam
> responsabilidade, dados, rota, estados e critérios de aceite.
>
> Regra do §15.3: **toda página que depende de dados** trata sete estados; **todo formulário**
> trata seis. Eles são **componentes reutilizáveis** — não implementações por tela.
> Dado mockado nunca aparece numa tela como se fosse real.

## Estados (contratos compartilhados)

**Página** (`<PageState>`): `carregando` · `sucesso` · `vazio` · `erro de rede/servidor` ·
`não encontrado` (404) · `não autorizado` (403) · `sessão expirada` (401 → redireciona ao login
preservando o destino).

**Formulário** (`<FormState>`): `campo inválido` (mensagem associada ao controle) · `envio em
andamento` (botão desabilitado, sem envio duplo) · `falha recuperável` (mensagem acionável, dados
preservados — `RNF004`) · `sucesso` · `tentativa duplicada` · `arquivo acima do limite ou formato
inválido`.

## Rotas (fixadas na Etapa 2; a rota de edição foi corrigida no card E5.6)

Decisão de arquitetura do §14.3: a rota é definida **uma vez** e não se reinventa por tela.
Slugs estáveis e canônicos (`RNF025`).

| Página | Rota | Pública |
|---|---|---|
| Início | `/` | sim |
| Explorar materiais | `/explorar` | sim |
| Detalhe do material | `/materiais/[slug]` | sim |
| Perfil público | `/u/[slug]` | sim |
| Categoria | `/categoria/[slug]` | sim |
| Login | `/entrar` | sim |
| Cadastro | `/cadastro` | sim |
| Termos / privacidade | `/termos`, `/privacidade` | sim |
| Área do usuário | `/painel` | não |
| Editar perfil | `/painel/perfil` | não |
| Publicar material | `/painel/publicar` | não |
| Materiais próprios | `/painel/materiais` | não |
| Editar material | `/painel/materiais/[slug]/editar` | não |

**A rota de edição é por `[slug]`, não por `[id]`** (correção do card E5.6): o `slug` é o
identificador canônico e estável do material (`RNF025`) e é o que a lista do dono já tem em
mãos. Um `id` interno obrigaria o contrato a publicá-lo — e ele não o publica, de propósito.

Rotas privadas **não** são indexadas (§12.3). Rotas públicas entregam conteúdo real no HTML.

## Decisão: 200 vs 404 nas rotas dinâmicas (card E2.12)

**Decisão tomada**, não ressalva: as três rotas públicas que resolvem **um recurso por `slug`**
respondem **404 de verdade** quando o endereço não corresponde a recurso público — com o estado
`não encontrado` do §15.3 no HTML entregue (RNF023, §12.3) e `noindex` (RNF024).

| Endereço | Resposta | Corpo |
|---|---|---|
| `/materiais/[slug]` — material retirado, excluído ou inexistente; slug fora do formato | **404** | estado `não encontrado`, com o endereço pedido |
| `/u/[slug]` — perfil não público (conta não ativa), perfil inexistente; slug ou `page` fora do formato | **404** | estado `não encontrado`, com o endereço pedido |
| `/categoria/[slug]` — categoria fora das ativas (inexistente ou desativada); slug ou `page` fora do formato | **404** | estado `não encontrado` |
| `/materiais/[slug]`, `/u/[slug]`, `/categoria/[slug]` — recurso público | **200** | conteúdo no HTML entregue |
| `/u/[slug]`, `/categoria/[slug]` — existe e **não tem material** | **200** | estado `vazio` explicativo (ausência de material não é endereço errado) |
| qualquer das três — API fora do ar, 5xx, 401/403 | **200** | estado que corresponde à causa (`erro de rede/servidor`, `sessão expirada`, `não autorizado`) — nunca `não encontrado` |
| `/explorar?categoria=<desconhecida>` | **200** | listagem vazia: filtro sem resultado é consulta, não endereço errado |
| endereço que não casa com rota nenhuma | **404** | `not-found.tsx` da raiz (`app/not-found.tsx`) |

**Como o status é decidido:** pelo proxy (`frontend/src/proxy.ts`), fora da renderização, antes de o
corpo começar a sair. O `notFound()` do App Router **não** serve: medido em rota dinâmica (cards
E2.9 e E2.12), ele responde 404 com o corpo vazio — o estado viaja dentro do pacote de JavaScript e
quem não executa JS veria página branca, contra RNF023. O proxy pergunta à API se o endereço resolve
recurso público (`frontend/src/lib/recurso-publico.ts`, a mesma leitura que a tela faz) e, quando
não resolve, reescreve a mesma requisição com `status: 404`; a página então renderiza o estado, como
sempre renderizou. Custo declarado: uma leitura a mais por visita nessas três rotas.

**A meta `robots` que sai no 404:** o Next injeta `<meta name="robots" content="noindex"/>` em toda
resposta com status > 400 (`app-render: NonIndex`) e essa meta **substitui** a do `generateMetadata`
— uma meta `robots` por página. Consequência: no 404 o `noindex` é do framework e o `nofollow` que a
página declara não chega ao visitante. Não muda o que o card exige (segue fora do índice, CA011), e o
`noindex, nofollow` declarado pela página continua valendo nas respostas **200** de erro (API fora do
ar, 5xx), que não são 404. Fica registrado, não corrigido: forçar o `nofollow` exigiria mexer no
status ou na árvore de metadata, fora do recorte do card.

**O que não mudou:** a API. O 404 de recurso continua sendo dela, indistinguível entre inexistente,
retirado e excluído (RN002, CA008) — e a página que os diferenciasse desfaria essa decisão. O
contrato segue v0.3. As rotas privadas (`/painel*`) não entram nesta decisão: seguem com `noindex` e
o estado na tela (fora do escopo do E2.12). Esta decisão **fecha a ressalva 1** de
`docs/qa/aceite-etapa-2.md` — o laudo lá registrado vale para o commit que ele verificou, e a
ressalva era esta troca (200 + `noindex`) que aqui deixa de existir.

## Páginas públicas

### 1. Início — `/`
- **Responsabilidade (§15):** explicar o produto e orientar para explorar ou publicar.
- **Conteúdo:** proposta de valor (material · contexto · pessoa), busca em destaque, categorias,
  materiais recentes. "Em destaque" só existe com **regra explícita** de seleção ou ordenação
  (§12.4) — proibido inventar popularidade, ranking ou avaliação.
- **Dados:** contagem/recentes de materiais, lista de categorias.
- **Aceite:** `CA001` (um visitante encontra caminho para explorar), `CA011` (indexável).
- **Estado especial:** com acervo vazio, a página **não** pode parecer quebrada — mostra convite
  a publicar e a explicação do produto.

### 2. Explorar materiais — `/explorar`
- **Responsabilidade:** listar, buscar, filtrar e paginar materiais públicos.
- **Dados:** `GET /api/materiais` (paginado, filtro por categoria/tipo, busca lexical).
- **Ordenação padrão documentada:** publicação/atualização mais recente, com desempate estável
  (§12.2). Sem "relevância" simulada.
- **Estados:** os sete; `vazio` = "nenhum material encontrado" **com** sugestão (limpar filtro,
  ver categorias), nunca tela branca. Paginação preserva filtros na URL (compartilhável).
- **Aceite:** `CA001`, `RNF021` (paginação), `RNF002` (mobile/desktop).

### 3. Detalhe do material — `/materiais/[slug]`
- **Responsabilidade:** exibir metadados, autoria, licença e acesso ao arquivo (`CA002`).
- **Conteúdo:** título, descrição, tipo, categoria, licença **explicada** (o que permite / não
  permite — `RN006`), autor com link para o perfil, data, ação de acesso ao arquivo.
- **Transparência (`CA012`):** nenhuma marca de revisão, certificação ou verificação institucional.
- **Acesso ao arquivo:** botão "Acessar/baixar" quando público; se retirado, a página informa
  (`CA008`) e não serve o arquivo. Erro de storage → mensagem acionável, não 500 cru.
- **SEO:** título, descrição, URL canônica; dados estruturados apenas se **corretos** (§12.3).

### 4. Perfil público — `/u/[slug]`
- **Responsabilidade:** identidade pública + materiais do autor (`CA003`).
- **Conteúdo:** nome público, bio, áreas de interesse, links externos; **afiliação declarada**
  apresentada como declaração, nunca como verificação (`RN011`, `CA012`). **Nunca** exibir e-mail,
  credencial ou dado privado (`RNF013`).
- **Estados:** os sete; perfil sem material → vazio explicativo.

### 5. Categoria — `/categoria/[slug]`
- **Responsabilidade:** contexto da categoria + materiais associados.
- **Conteúdo:** nome e descrição da categoria (do contrato), a lista dos materiais dela e o
  endereço estável. Usa o **mesmo componente de listagem** de `/explorar` — não é uma segunda
  tela (card E2.10).
- **Estados:** os sete; categoria sem material → vazio explicativo, com as categorias que a
  taxonomia devolveu como sugestão. Categoria fora das ativas (ou inexistente) → `não
  encontrado`: a rota não inventa uma categoria a partir de um filtro sem resultado (o filtro
  em `/explorar` continua respondendo 200 vazio para slug desconhecido — são perguntas
  diferentes).
- **Relação com `/explorar?categoria=…`:** as duas existem e não se substituem — esta dá o
  contexto da categoria; aquela é a listagem filtrável (busca, tipo, ordem, paginação) do mesmo
  recorte. O card de material aponta para as duas: o nome da categoria vai para cá, o chip de
  filtro para a listagem.
- **Taxonomia (`ADR-0006`, ratificado com um nível):** sem subcategorias; não existe hierarquia
  profunda para exibir.

### 6. Login — `/entrar` e 7. Cadastro — `/cadastro`
- **Responsabilidade:** autenticar / criar conta (`CA004`).
- **Cadastro:** e-mail + senha (ADR-0003), com confirmação de senha e aviso de que
  **publicar exige verificar o e-mail**. Após criar conta, a sessão já existe e a pessoa é
  orientada a completar o perfil — **sem bloquear a navegação pública** (§7.2 do DRS).
- **Formulário:** os seis estados; erro de credencial é genérico (não revela se o e-mail existe).
- **Aceite:** `CA004`; nunca expor credencial em mensagem, URL ou log (`RNF009`).

### 8. Termos / privacidade — `/termos`, `/privacidade`
- **Responsabilidade:** explicar regras e tratamento de dados antes do lançamento (§13.3).
- **Aceite:** `CA012`; são **dependência de lançamento** (ADR-0007), não tela decorativa.

## Páginas autenticadas

### 9. Área do usuário — `/painel`
- Resumo das ações principais + acesso aos materiais próprios.
- **Aviso persistente** quando o e-mail não estiver verificado, com reenvio das instruções
  (ADR-0003 (h)): publicar exige verificação; navegar não.

### 10. Editar perfil — `/painel/perfil`
- Nome público, slug (único, validado no servidor — `RN009`), bio, afiliação declarada, áreas de
  interesse, links. Pré-visualização de como o perfil público aparece.

### 11. Publicar material — `/painel/publicar`
- **Responsabilidade:** validar metadados, licença e arquivo (§7.3).
- **Campos obrigatórios:** título, descrição, categoria, tipo, licença, arquivo PDF (`RN004`).
- **Confirmação de direitos (`RN007`):** declaração explícita de que o usuário tem direito de
  compartilhar. Não é checkbox decorativo.
- **Arquivo:** validação de tipo e tamanho no cliente **e** no servidor (limite do `ADR-0005`:
  30 MB, PDF, validado por conteúdo); erro específico ("acima de X MB", "formato não aceito").
- **Estados do formulário:** os seis; em falha, **não** criar registro incompleto nem arquivo
  órfão (`RN008`) e preservar os campos preenchidos (`RNF004`).
- **Bloqueio por verificação:** se a conta não estiver verificada, a página explica o motivo e
  oferece reenvio — sem erro seco.
- **Sucesso:** confirmação + link para a página pública (`CA005`).

### 12. Materiais próprios — `/painel/materiais`
- Lista os materiais do usuário com estado (`PUBLICADO`, `RETIRADO`, `EXCLUIDO`) e **as ações de
  cada estado** (`CA008`, `RF015`, `RF017`): `PUBLICADO` → editar, retirar, excluir; `RETIRADO` →
  editar, repor no público, excluir; `EXCLUIDO` → **nenhuma** (estado terminal, `ADR-0011` §2).
  A derivação da ação a partir do estado é de apresentação; quem julga é o servidor.
- Paginada (`atualizadoEm` decrescente, `id` crescente — a ordem do contrato), com título, tipo,
  categoria, licença explicada, estado e as duas datas; link para a página pública **só** quando
  `PUBLICADO` (em `RETIRADO`/`EXCLUIDO` ela responde 404 — link que leva a 404 é promessa falsa).
- **Vazio orienta o primeiro passo** (`RF015`): sem material, a tela leva a publicar — ou, com a
  conta pendente de verificação, ao estado da conta, porque publicar exige e-mail confirmado.
- **Excluir pede confirmação explícita** (`RF017`, `ADR-0011` §7): o diálogo diz que **não há como
  desfazer** e que o arquivo deixa de estar disponível em 7 dias. Retirar e repor são reversíveis
  e não pedem diálogo. A confirmação é ato da interface — um campo `confirmacao` no corpo não
  confirmaria nada, já que o cliente sempre pode enviá-lo.
- **Autorização no servidor** (`RN003`, `RNF010`): esconder o botão não é controle. Material
  inexistente, de outra conta ou já `EXCLUIDO` respondem o **mesmo 404** indistinguível
  (`ADR-0011` §3).
- **Entregue no card E5.6**, com as leituras no servidor (`GET /perfil/atual/materiais` e
  `…/{slug}`, cookie de sessão reenviada) e as escritas pelo cliente com `X-CSRF-Token`. A rota
  existia neste documento desde a Etapa 1 e **não existia** no produto: a área do usuário (`§9`)
  dizia que a listagem "ainda não existe nesta versão". Isso deixou de ser verdade no E5.6 — o
  card da listagem entrou no §9 e a frase saiu.

### 13. Editar material — `/painel/materiais/[slug]/editar`
- Atualiza metadados do material **próprio**; propriedade verificada no servidor (`CA007`).
- Publicação só pode ser retirada/republicada pelo dono; alteração de `profile_id` no payload é
  ignorada e recusada (`RN001`).
- O identificador é o `slug` — é a chave canônica (`RNF025`) e é o que a lista do §12 entrega.
  Fora do formulário, e por isso imutáveis por esta rota: `slug`, autoria, `estado`, o arquivo e
  `declaracaoDireito` (ADR-0011 §4). O endereço da edição é montado por `rotaEditarMaterial`
  (`lib/rotas.ts`).

## Acessibilidade e SEO (transversal, entra no aceite de toda página)

- Navegação completa por teclado, foco visível, `nav/main/section/article` semânticos, hierarquia
  de títulos, rótulos associados, erro associado ao campo (`RNF006`); alvo WCAG 2.2 AA (`RNF008`).
- Público: `<title>` e descrição adequados, URL canônica estável, conteúdo no HTML entregue,
  `robots.txt` + sitemap coerentes; privado: `noindex` (`RNF023`, `RNF024`, `RNF025`).
- Verificação obrigatória em **375 px e 1440 px**, e com `prefers-reduced-motion` ativo.
