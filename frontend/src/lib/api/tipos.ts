/**
 * Tipos da superfície pública da API — recorte LIDO pela descoberta pública, campo a
 * campo conforme `contracts/openapi.yaml` (v0.3.0).
 *
 * Por que escritos à mão, e não gerados com `openapi-typescript`: gerar tipos exige
 * adicionar uma dependência de desenvolvimento, e mexer em dependência é do card que
 * mexe nelas (E1.10 é o dono; AGENTS.md — `npm install` fora desse card é proibido).
 * Consequência registrada: NADA aqui é verificado contra o contrato pelo compilador.
 * Se um schema mudar lá, é este arquivo que precisa acompanhar; quando a geração de
 * tipos entrar no projeto, este arquivo vira o artefato gerado (vira card).
 *
 * Só entra aqui o que a tela consome. Campo do contrato que a tela não usa não é
 * copiado "por completude": publicar no código um campo que ninguém exibe é a mesma
 * fronteira contra ficção (§21.1) aplicada do lado do cliente.
 */

/** Envelope padrão de lista paginada (RF002, RNF021). `page` começa em 0. */
export type Pagina<T> = {
  items: T[];
  page: number;
  size: number;
  total: number;
};

/** Identidade pública do autor na listagem (RF002, RF007). Sem dado de conta. */
export type AutorResumo = {
  slug: string;
  nomePublico: string;
};

/** Categoria anexada ao material — `slug` é a chave estável aceita pelo filtro. */
export type CategoriaRef = {
  slug: string;
  nome: string;
};

/** Tipo de material anexado ao material — `slug` é a chave estável do filtro. */
export type TipoMaterialRef = {
  slug: string;
  nome: string;
};

/**
 * Licença explícita (RN006). `descricaoCurta` é obrigatória no contrato justamente
 * porque é ela que impede a leitura de que "estar público" significa domínio público
 * (§13.2): a tela nunca exibe só o nome da licença.
 */
export type LicencaRef = {
  slug: string;
  nome: string;
  descricaoCurta: string;
};

/** Item da descoberta pública (RF002). Todo item devolvido está PUBLICADO. */
export type MaterialResumo = {
  slug: string;
  titulo: string;
  descricao: string;
  autor: AutorResumo;
  categoria: CategoriaRef;
  tipo: TipoMaterialRef;
  licenca: LicencaRef;
  /** Instante da publicação, em UTC com `Z` (chave da ordenação `recentes`). */
  publicadoEm: string;
  /** Ausente quando coincide com `publicadoEm`. */
  atualizadoEm?: string;
};

/**
 * Licença no DETALHE do material: o mesmo `LicencaRef` da listagem, mais o
 * `identificador` público (ex.: SPDX) e a `urlReferencia` do texto da licença
 * (`LicencaRef` no contrato já os declara opcionais; a listagem não os consome).
 *
 * Os dois são AUSENTES quando a licença não tem identificador nem texto público
 * (caso de "Todos os direitos reservados") — ausência é informação, e a tela não
 * inventa um valor para preencher (RN006, §13.2).
 */
export type LicencaDetalhe = LicencaRef & {
  identificador?: string;
  urlReferencia?: string;
};

/**
 * Material público completo (RF005) — `GET /api/materiais/{slug}`.
 *
 * `atualizadoEm` é obrigatório aqui (o contrato o lista em `required`), ao contrário
 * do item de listagem, que o omite quando coincide com a publicação.
 *
 * O ACESSO AO ARQUIVO não está neste schema: a versão v0.3.0 do contrato não serve
 * arquivo, e publicar um campo de download sem implementação seria fronteira contra
 * ficção (DRS §21.1). A página do material, por isso, não pode oferecer botão de
 * acesso — ela diz que não há arquivo disponível (ver `app/materiais/[slug]/page.tsx`).
 * Quando o RF014 entrar, o campo nasce no contrato primeiro.
 */
export type MaterialDetalhe = {
  slug: string;
  titulo: string;
  descricao: string;
  autor: AutorResumo;
  categoria: CategoriaRef;
  tipo: TipoMaterialRef;
  licenca: LicencaDetalhe;
  /** Nesta superfície é sempre `PUBLICADO`: retirado/excluído responde 404. */
  estado: 'PUBLICADO';
  publicadoEm: string;
  atualizadoEm: string;
};

/**
 * Item de referência da taxonomia (ADR-0006), recortado ao que o filtro exibe.
 * O schema completo (`id`, `descricao`, `ativo`) está no contrato; `descricao` só é copiada
 * na variante abaixo, porque só a página de categoria (card E2.10) a exibe.
 */
export type ItemTaxonomia = {
  slug: string;
  nome: string;
};

/**
 * Categoria de referência como a PÁGINA `/categoria/[slug]` (RF007) precisa dela: `nome` e
 * `descricao`. É o `descricao` do contrato — texto curto escrito para descoberta e SEO
 * (§12) —, nunca um resumo redigido na tela.
 */
export type CategoriaDaPagina = ItemTaxonomia & {
  descricao: string;
};

/**
 * Identidade pública do autor e a página dos materiais publicados dele (`AutorPublico` do
 * contrato v0.3.0, RF006).
 *
 * O que NÃO existe aqui, e não é omissão: e-mail de login, id de conta, hash de senha e
 * token não estão no schema e não são copiados para cá (RNF013) — campo que a tela não
 * exibe não entra no tipo, pela mesma fronteira contra ficção registrada no topo do
 * arquivo. `biografia` e `afiliacaoDeclarada` são opcionais porque o contrato os declara
 * ausentes quando não informados (RN004).
 */
export type AutorPublico = {
  slug: string;
  nomePublico: string;
  biografia?: string;
  /** Declaração do próprio autor — nunca vínculo atestado pela plataforma (RN011). */
  afiliacaoDeclarada?: string;
  /** Áreas de interesse declaradas; lista vazia quando o autor não informou. */
  areasInteresse: string[];
  /** Links externos declarados (texto livre); lista vazia quando não informados. */
  linksExternos: string[];
  /** Página dos materiais publicados do autor, no envelope padrão (RN021). */
  materiais: Pagina<MaterialResumo>;
};

/** Critério de ordenação aceito pelo contrato no parâmetro `ordem` (§12.2). */
export type OrdemMateriais = 'recentes' | 'titulo';

/** Erro de campo do `ProblemDetail` (RFC 7807) — presente só em erro de validação. */
export type ErroDeCampo = {
  campo: string;
  mensagem: string;
};

/** Formato único de erro da API (`application/problem+json`). */
export type ProblemDetail = {
  type: string;
  title: string;
  status: number;
  detail?: string;
  instance?: string;
  errors?: ErroDeCampo[];
};

/* ---------------------------------------------------------------------------
   Identidade e sessão — recorte da tag `identidade` do contrato v0.4.0 (RF008,
   RF009) que as telas de cadastro, login e logout leem. Pela mesma fronteira
   contra ficção do topo deste arquivo, só entra o campo que a tela usa; e-mail
   de login, hash, token e id de conta não existem no schema e não são copiados
   para cá (RNF013).
   --------------------------------------------------------------------------- */

/**
 * Estado da conta (§8.2.1, ADR-0003 (b)). `SUSPENSO` NÃO vem em `SessaoAtual`: conta
 * suspensa não mantém sessão — o login a recusa com 403. Ele aparece aqui só porque o
 * 403 do login precisa ser explicado na tela.
 */
export type EstadoConta = 'PENDENTE_VERIFICACAO' | 'ATIVO';

/** Perfil público como o contrato o devolve na sessão (mesmo `PerfilPublico` de `/u/[slug]`). */
export type PerfilDaSessao = {
  slug: string;
  nomePublico: string;
  biografia?: string;
  afiliacaoDeclarada?: string;
  areasInteresse: string[];
  linksExternos: string[];
};

/**
 * `GET /api/sessoes/atual` — estado da sessão corrente (RF009). Sem sessão a resposta é
 * 200 com `autenticado: false` (não é erro, é o estado normal do visitante).
 */
export type SessaoAtual = {
  autenticado: boolean;
  estadoConta?: EstadoConta;
  perfil?: PerfilDaSessao;
};

/** `ReenvioVerificacao` — aceite do reenvio, com os dois instantes do cooldown (ADR-0003 (c)). */
export type ReenvioVerificacao = {
  reenviadoEm: string;
  /** Instante a partir do qual um novo reenvio é aceito (60 s). Alimenta a contagem da tela. */
  podeReenviarEm: string;
};

/** `CadastroConta` — dados mínimos do cadastro (RF008). O slug inicial nasce no servidor. */
export type CadastroConta = {
  email: string;
  senha: string;
  nomePublico: string;
};

/** `Credenciais` — login (RF009). */
export type Credenciais = {
  email: string;
  senha: string;
};

/* ---------------------------------------------------------------------------
   Edição do próprio perfil — recorte da tag `identidade` do contrato v0.4.0
   (RF011) que a tela de edição lê e escreve. Mesma fronteira contra ficção do
   topo deste arquivo: entra o campo que a tela usa, e nada além.
   --------------------------------------------------------------------------- */

/**
 * `PerfilPublico` — identidade pública da conta, devolvida por `PUT /perfil/atual`.
 *
 * É o MESMO schema que a sessão entrega (`PerfilDaSessao`): o contrato descreve os campos
 * públicos em um só lugar (`PerfilPublico`), e a sessão, a resposta da edição e o perfil
 * público do autor (`AutorPublico`) compõem dele. Por isso o alias — dois nomes para o
 * mesmo recorte seria duas chances de divergirem.
 */
export type PerfilPublico = PerfilDaSessao;

/**
 * `EdicaoPerfil` — corpo de `PUT /perfil/atual` (RF011).
 *
 * Sem campo de identificador de conta ou de perfil: a propriedade vem da sessão
 * (ADR-0003 (j), CA007) — é o que impede editar o perfil de outra conta manipulando a
 * requisição. `slug` é validado no formato e na unicidade (RN009): fora do padrão → 422;
 * já em uso por outra conta → 409, com o campo culpado em `errors[]`.
 *
 * `afiliacaoDeclarada` é DECLARAÇÃO de quem escreve, nunca vínculo verificado: o contrato
 * não tem — e esta tela não inventa — selo, medalha ou marca de instituição (RN011, CA012).
 *
 * Opcionais: a edição SUBSTITUI o conjunto, então a tela envia os quatro em toda
 * requisição; `''` e `[]` valem como "não informado" e é assim que o campo volta vazio.
 */
export type EdicaoPerfil = {
  nomePublico: string;
  slug: string;
  biografia?: string;
  afiliacaoDeclarada?: string;
  areasInteresse?: string[];
  linksExternos?: string[];
};

/* ---------------------------------------------------------------------------
   Publicação de material — recorte da tag `publicacao` do contrato v0.5.0
   (RF012, RF018, RF019) que o formulário de `/painel/publicar` lê e escreve.
   Mesma fronteira contra ficção do topo deste arquivo: entra o campo que a tela
   usa — e, no caso da licença, a `descricaoCurta`, porque é ela que explica a
   escolha em linguagem simples (RF018, RN006). O campo de AUTOR não existe aqui
   de propósito: a autoria vem da sessão, nunca do corpo enviado (ADR-0003 (j)).
   --------------------------------------------------------------------------- */

/**
 * `PublicacaoMaterial` — o que o formulário envia em `POST /materiais`
 * (`multipart/form-data`, os metadados e o arquivo na MESMA requisição: é o que
 * torna a ordem do RN008 implementável no servidor).
 *
 * `categoriaSlug`, `tipoSlug` e `licencaSlug` são os `slug` estáveis da taxonomia
 * (ADR-0006, RF018) — os mesmos valores que `/categorias`, `/tipos-material` e
 * `/licencas` devolvem e que os filtros de descoberta aceitam. Nenhum deles tem
 * valor padrão: quem escolhe é o autor, e `''` é "ainda não escolheu" — não é uma
 * licença atribuída em silêncio.
 *
 * `declaracaoDireito` é obrigatório e precisa ser `true` (RF012): é a confirmação
 * de que o autor possui o direito de compartilhar o material. Ausente ou `false`
 * o servidor recusa com 422 no campo.
 */
export type PublicacaoMaterial = {
  titulo: string;
  descricao: string;
  categoriaSlug: string;
  tipoSlug: string;
  licencaSlug: string;
  declaracaoDireito: boolean;
  arquivo: File;
};

/**
 * `MaterialPublicado` — resposta **201** de `POST /materiais` (RF012, RF019).
 *
 * O `slug` é gerado no SERVIDOR (único e estável, RNF025) e é a chave da URL
 * pública: o cliente navega para `urlPublica`, que o contrato publica no mesmo
 * campo em vez de obrigar a tela a montar o endereço por conta própria.
 */
export type MaterialPublicado = {
  slug: string;
  /** A publicação nasce PUBLICADO (RF019) — o enum do contrato tem um único valor. */
  estado: 'PUBLICADO';
  /** Caminho da página pública do material (ex.: `/materiais/…`). */
  urlPublica: string;
};

/* ---------------------------------------------------------------------------
   Gerenciamento do material próprio — recorte da tag `gerenciamento` do
   contrato v0.6.0 (RF015–RF017, ADR-0011) que a superfície `/painel/materiais`
   lê e escreve. Mesma fronteira contra ficção do topo deste arquivo: entra o
   campo que a tela usa. E-mail de login, hash, token, identificador de conta,
   vínculo de perfil e chave de armazenamento não existem neste schema e não são
   copiados para cá (RNF013, RNF014).
   --------------------------------------------------------------------------- */

/**
 * Estado de publicação do material (`MaterialProprio.estado`, ADR-0011 §2).
 *
 * Os TRÊS valores aparecem na visão do dono, e é deliberado: `RETIRADO` é reversível
 * pelo próprio dono (o `PATCH` o repõe no público) e `EXCLUIDO` é **terminal** no MVP —
 * não há restauração (ADR-0011 §6). As ações do dono valem em `PUBLICADO` e `RETIRADO`;
 * em `EXCLUIDO`, nenhuma.
 */
export type EstadoDeMaterial = 'PUBLICADO' | 'RETIRADO' | 'EXCLUIDO';

/**
 * Estado de DESTINO aceito por `PATCH /perfil/atual/materiais/{slug}`
 * (`TransicaoDeMaterial`): `EXCLUIDO` não entra por valor de estado — a exclusão é o
 * `DELETE`, ato próprio e terminal (RF019, ADR-0011 §5).
 */
export type EstadoDeDestino = 'PUBLICADO' | 'RETIRADO';

/**
 * Metadados do arquivo na visão do dono (`ArquivoMaterial` do contrato).
 *
 * `disponivel` é `false` quando o binário já foi purgado pela rotina de retenção
 * (7 dias depois da exclusão, ADR-0005 §3): o METADADO permanece, o arquivo não. É
 * informação que a tela mostra em vez de deixar a pessoa concluir sozinha que o
 * botão de download sumiu por defeito (RF017).
 */
export type ArquivoDoMaterial = {
  nomeOriginal: string;
  tamanhoBytes: number;
  disponivel: boolean;
};

/**
 * Material do próprio dono, na visão de gerenciamento (RF015) — o MESMO schema no item
 * da lista e no detalhe (ADR-0011 §2), e por isso um tipo só aqui.
 *
 * **Sem `autor`**: a autoria é sempre o perfil da sessão (RN001); devolver o dono como
 * autor de si mesmo seria um campo com um valor só. É também o que impede reusar
 * `MaterialResumo`/`MaterialDetalhe` — os dois carregam `autor` e declaram `estado`
 * apenas como `PUBLICADO`.
 */
export type MaterialProprio = {
  slug: string;
  titulo: string;
  descricao: string;
  estado: EstadoDeMaterial;
  categoria: CategoriaRef;
  tipo: TipoMaterialRef;
  licenca: LicencaRef;
  arquivo: ArquivoDoMaterial;
  /** Instante da publicação; **não muda** por edição nem por republicação (ADR-0011 §4). */
  publicadoEm: string;
  /** Instante da última escrita que alterou o material — é a ordem da lista do dono. */
  atualizadoEm: string;
};

/**
 * `PUT /perfil/atual/materiais/{slug}` — o conjunto editável inteiro (RF016, ADR-0011 §4).
 *
 * Os cinco campos são obrigatórios porque a tela os apresenta todos preenchidos: a edição
 * SUBSTITUI o conjunto. Fora do schema, e por isso imutável por esta rota: `slug` (é a URL
 * canônica, RNF025), autoria/propriedade (vem da sessão, RN001), `estado` (é o `PATCH`), o
 * arquivo (trocar o binário é outra operação, sem requisito no MVP) e `declaracaoDireito`
 * (pertence ao ato de publicar, RF012).
 */
export type EdicaoDeMaterial = {
  titulo: string;
  descricao: string;
  categoriaSlug: string;
  tipoSlug: string;
  licencaSlug: string;
};

/** `PATCH /perfil/atual/materiais/{slug}` — um campo, um ato (RF017, RF019). */
export type TransicaoDeMaterial = {
  estado: EstadoDeDestino;
};
