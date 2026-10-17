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
