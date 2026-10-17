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

## Rotas (proposta — fixar antes da Etapa 2)

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
| Editar material | `/painel/materiais/[id]/editar` | não |

Rotas privadas **não** são indexadas (§12.3). Rotas públicas entregam conteúdo real no HTML.

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
- Lista os materiais do usuário com estado (`PUBLICADO`, `RETIRADO`, `EXCLUIDO`) e ações de editar,
  retirar e excluir (`CA008`).
- **Autorização no servidor** (`RN003`, `RNF010`): esconder o botão não é controle.

### 13. Editar material — `/painel/materiais/[id]/editar`
- Atualiza metadados do material **próprio**; propriedade verificada no servidor (`CA007`).
- Publicação só pode ser retirada/republicada pelo dono; alteração de `profile_id` no payload é
  ignorada e recusada (`RN001`).

## Acessibilidade e SEO (transversal, entra no aceite de toda página)

- Navegação completa por teclado, foco visível, `nav/main/section/article` semânticos, hierarquia
  de títulos, rótulos associados, erro associado ao campo (`RNF006`); alvo WCAG 2.2 AA (`RNF008`).
- Público: `<title>` e descrição adequados, URL canônica estável, conteúdo no HTML entregue,
  `robots.txt` + sitemap coerentes; privado: `noindex` (`RNF023`, `RNF024`, `RNF025`).
- Verificação obrigatória em **375 px e 1440 px**, e com `prefers-reduced-motion` ativo.
