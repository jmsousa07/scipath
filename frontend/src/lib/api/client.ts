/**
 * Cliente único da API (fronteira: `contracts/openapi.yaml` v0.6.0).
 *
 * Por que existe: a regra do projeto é um cliente tipado e não `fetch` espalhado pela
 * tela. Aqui moram a base da URL, o limite de tempo, a leitura do `ProblemDetail` e a
 * distinção entre "o servidor respondeu errado" e "não houve resposta" — a tela decide
 * o estado a exibir, o cliente só relata.
 *
 * Duas superfícies, um módulo: a DESCOBERTA PÚBLICA (listagem, detalhe, taxonomia,
 * autor) e a LEITURA DO DONO (`/perfil/atual/materiais`, card E5.6). As duas são
 * leituras de SERVIDOR e é por isso que cabem aqui; a segunda recebe o cabeçalho
 * `Cookie` da requisição já montado (ver `requisitar`), sem este módulo conhecer o Next.
 * As ESCRITAS não moram aqui, e não é esquecimento: elas exigem o `X-CSRF-Token` da
 * cookie `XSRF-TOKEN`, que só existe no NAVEGADOR (`lib/sessao/navegador.ts`).
 *
 * ESTE MÓDULO É DE SERVIDOR. Nenhum componente de cliente (`'use client'`) pode
 * importá-lo: a base lê `process.env`, que no bundle do navegador não existe. A
 * separação não é estética — sem ela, a URL do backend vazaria para o cliente e o
 * visitante passaria a falar direto com a API, o que não é o desenho do produto
 * (a busca é renderizada no servidor, RNF023–RNF025).
 */
import type {
  AutorPublico,
  CategoriaDaPagina,
  ItemTaxonomia,
  LicencaRef,
  MaterialDetalhe,
  MaterialProprio,
  MaterialResumo,
  OrdemMateriais,
  Pagina,
  ProblemDetail,
} from './tipos';

/**
 * Base da API. `API_BASE_URL` (servidor, lido em tempo de execução) tem precedência
 * sobre `NEXT_PUBLIC_API_BASE_URL` (inlinado no build) para que a mesma imagem possa
 * apontar para outro backend sem rebuild; o padrão é o backend de desenvolvimento.
 */
const BASE_PADRAO = 'http://localhost:8080/api';

export function baseDaApi(): string {
  const bruto = process.env.API_BASE_URL ?? process.env.NEXT_PUBLIC_API_BASE_URL ?? BASE_PADRAO;
  return bruto.replace(/\/+$/, '');
}

/**
 * Limite de tempo da requisição. Sem ele, um backend que aceita a conexão e não
 * responde deixa o visitante preso no estado "carregando" para sempre — em vez de ver
 * a falha de rede com a ação de tentar de novo (RNF004). O valor é folgado de propósito:
 * é teto de segurança, não meta de desempenho (RNF020 mede, não promete).
 */
const LIMITE_MS = 8000;

/**
 * Motivo da falha, na granularidade que a tela precisa distinguir:
 *   - `rede`     não houve resposta (servidor fora, DNS, tempo esgotado);
 *   - `resposta` houve resposta 2xx, mas o corpo não é o JSON do contrato;
 *   - `http`     houve resposta com status de erro; `problem` traz o `ProblemDetail`
 *                quando o corpo veio no formato do contrato (`application/problem+json`).
 */
export type Falha =
  | { motivo: 'rede' }
  | { motivo: 'resposta' }
  | { motivo: 'http'; status: number; problem: ProblemDetail | null };

export type Resultado<T> = { ok: true; dados: T } | { ok: false; falha: Falha };

/**
 * `fetch` do Next aceita `next.revalidate` além do `RequestInit` padrão; o tipo do
 * DOM não conhece essa extensão, então ela é declarada aqui em vez de espalhar `as`.
 */
type IniciarRequisicao = RequestInit & { next?: { revalidate?: number } };

function lerProblem(texto: string, tipoConteudo: string | null): ProblemDetail | null {
  if (!tipoConteudo?.includes('problem+json')) return null;
  try {
    const bruto: unknown = JSON.parse(texto);
    if (typeof bruto !== 'object' || bruto === null) return null;
    const problem = bruto as Partial<ProblemDetail>;
    if (typeof problem.type !== 'string' || typeof problem.title !== 'string') return null;
    if (typeof problem.status !== 'number') return null;
    return problem as ProblemDetail;
  } catch {
    return null;
  }
}

async function requisitar<T>(
  caminho: string,
  init: IniciarRequisicao,
  /**
   * Cabeçalho `Cookie` da requisição que a página está atendendo. Só as leituras da
   * superfície do DONO o usam (`/perfil/atual/materiais`): sem sessão a resposta é 401, e a
   * sessão do produto é a cookie `HttpOnly` (ADR-0003 (e)) — quem a tem em mãos é a página,
   * que a recebe de `lib/sessao/servidor.ts`. Este módulo não importa nada do Next: ele
   * RECEBE o cabeçalho pronto, e por isso continua sendo um cliente de API e nada mais.
   */
  cabecalhoDeCookie?: string,
): Promise<Resultado<T>> {
  const url = `${baseDaApi()}${caminho}`;
  const cabecalhos = new Headers(init.headers);
  if (cabecalhoDeCookie) cabecalhos.set('cookie', cabecalhoDeCookie);

  let resposta: Response;
  try {
    resposta = await fetch(url, { ...init, headers: cabecalhos, signal: AbortSignal.timeout(LIMITE_MS) });
  } catch {
    return { ok: false, falha: { motivo: 'rede' } };
  }

  const texto = await resposta.text();

  if (!resposta.ok) {
    return {
      ok: false,
      falha: {
        motivo: 'http',
        status: resposta.status,
        problem: lerProblem(texto, resposta.headers.get('content-type')),
      },
    };
  }

  try {
    return { ok: true, dados: JSON.parse(texto) as T };
  } catch {
    return { ok: false, falha: { motivo: 'resposta' } };
  }
}

/**
 * Consulta da listagem pública, nos mesmos nomes do contrato. `page` é 0-based aqui
 * porque é assim que a API conta; a URL de `/explorar` é 1-based (ver
 * `lib/explorar/consulta.ts`) e a conversão acontece na fronteira, num lugar só.
 *
 * `size` é opcional e quem decide é a TELA: `/explorar` não o envia (usa o padrão do
 * contrato, 20, e pagina) e a página inicial envia `6`, porque ela mostra um recorte
 * ("os mais recentes") com o link para a listagem completa. O envelope da resposta
 * devolve o `size` aplicado e o `total` da consulta inteira — nenhum número é
 * inventado no cliente (DRS §12.4).
 */
export type ConsultaMateriais = {
  q?: string;
  categoria?: string;
  tipo?: string;
  ordem?: OrdemMateriais;
  page?: number;
  size?: number;
};

function comParametros(pares: Record<string, string | number | undefined>): string {
  const busca = new URLSearchParams();
  for (const [chave, valor] of Object.entries(pares)) {
    if (valor === undefined || valor === '') continue;
    busca.set(chave, String(valor));
  }
  const texto = busca.toString();
  return texto === '' ? '' : `?${texto}`;
}

/**
 * `GET /api/materiais` — listagem paginada (RF002). `/explorar` não envia `size` (usa o
 * padrão do contrato, 20, e pagina); a página inicial envia 6 para mostrar um recorte e
 * linkar a listagem completa. O envelope da resposta devolve o `size` aplicado, então
 * nenhum número de itens por página é inventado no cliente.
 *
 * `cache: 'no-store'`: a listagem é a superfície onde material novo tem de aparecer;
 * cache de resposta aqui significaria mostrar acervo que já mudou (§12.2).
 */
export function listarMateriais(consulta: ConsultaMateriais): Promise<Resultado<Pagina<MaterialResumo>>> {
  return requisitar<Pagina<MaterialResumo>>(`/materiais${comParametros(consulta)}`, {
    cache: 'no-store',
  });
}

/**
 * `GET /api/materiais/{slug}` — detalhe público do material (RF005).
 *
 * `cache: 'no-store'` aqui não é preferência de desempenho, é requisito: material
 * retirado não pode continuar sendo servido como se estivesse ativo (§12.3, CA008).
 * A leitura do detalhe é por recurso, então o custo de não ter cache é uma requisição
 * por visita — o mesmo desenho da listagem.
 *
 * O `slug` vai codificado no caminho: ele vem da URL da página, e um valor com
 * caractere especial não pode virar consulta a outro endereço.
 */
export function obterMaterial(slug: string): Promise<Resultado<MaterialDetalhe>> {
  return requisitar<MaterialDetalhe>(`/materiais/${encodeURIComponent(slug)}`, {
    cache: 'no-store',
  });
}

/**
 * Taxonomia ativa para o filtro (RF004, RF018). Duas decisões:
 *
 * 1. `size=100` explícito: o teto por requisição do contrato. Com o padrão (20), a
 *    21ª categoria ativa sumiria do filtro em silêncio — filtro que esconde opção é
 *    pior que filtro ausente.
 * 2. `revalidate: 3600`: categoria e tipo são dado de REFERÊNCIA, alterado só por
 *    migração Flyway (ADR-0006) — não mudam enquanto um visitante navega. O cache
 *    também é o que deixa a taxonomia sobreviver a uma instabilidade momentânea do
 *    endpoint: falha de revalidação serve o valor anterior em vez de derrubar a tela.
 */
const REVALIDACAO_TAXONOMIA_S = 3600;

/**
 * Categorias da taxonomia. O tipo de retorno é o da categoria COMPLETA (`nome` +
 * `descricao`), e não o `ItemTaxonomia` enxuto do filtro: `/categoria/[slug]` (RF007) é
 * uma PÁGINA cuja razão de existir é dar contexto, e o contexto é o `descricao` que o
 * contrato publica. Exibir um resumo escrito na tela seria inventar o que o dado já diz.
 */
export function listarCategorias(): Promise<Resultado<Pagina<CategoriaDaPagina>>> {
  return requisitar<Pagina<CategoriaDaPagina>>(`/categorias${comParametros({ size: 100 })}`, {
    next: { revalidate: REVALIDACAO_TAXONOMIA_S },
  });
}

export function listarTiposMaterial(): Promise<Resultado<Pagina<ItemTaxonomia>>> {
  return requisitar<Pagina<ItemTaxonomia>>(`/tipos-material${comParametros({ size: 100 })}`, {
    next: { revalidate: REVALIDACAO_TAXONOMIA_S },
  });
}

/**
 * `GET /api/licencas` — licenças de reutilização ativas (RF018, RN006), para o autor
 * escolher na publicação.
 *
 * Duas decisões, as mesmas das vizinhas — e uma a mais:
 *
 * 1. `size=100` explícito e `revalidate: 3600` pelas mesmas razões de categoria e tipo
 *    (dado de referência alterado só por migração Flyway, ADR-0006);
 * 2. o tipo de retorno é `LicencaRef` (com `descricaoCurta`), não o `ItemTaxonomia` do
 *    filtro: a tela de publicação precisa explicar em linguagem simples o que a licença
 *    permite, e essa explicação É o `descricaoCurta` do contrato — escrevê-la na tela
 *    seria redigir de novo o que o dado já diz (RF018, RN006).
 */
export function listarLicencas(): Promise<Resultado<Pagina<LicencaRef>>> {
  return requisitar<Pagina<LicencaRef>>(`/licencas${comParametros({ size: 100 })}`, {
    next: { revalidate: REVALIDACAO_TAXONOMIA_S },
  });
}

/**
 * `GET /api/autores/{slug}` — identidade pública do autor e a página dos materiais dele
 * (RF006, autor do contrato v0.3.0).
 *
 * `cache: 'no-store'` pela mesma razão da listagem: material recém-publicado tem de
 * aparecer no perfil do autor, e resposta de cache aqui mostraria acervo que já mudou.
 *
 * `page` é 0-based (contrato) e a PRIMEIRA página não é enviada: o default do contrato é 0,
 * então mandá-la só criaria uma segunda URL pedindo exatamente a mesma coisa (§12.2).
 * O backend já recusa `page` negativo e `size` acima do teto; a tela não replica a
 * validação do contrato — ela lê e valida o que a PESSOA escreveu na barra de endereços
 * (`lib/pagina.ts`).
 */
export function obterAutor(slug: string, page: number): Promise<Resultado<AutorPublico>> {
  const consulta = page > 0 ? { page } : {};
  return requisitar<AutorPublico>(`/autores/${encodeURIComponent(slug)}${comParametros(consulta)}`, {
    cache: 'no-store',
  });
}

/* ---------------------------------------------------------------------------
   Leitura do DONO — `/perfil/atual/materiais` (tag `gerenciamento`, v0.6.0).
   Recebem o cabeçalho `Cookie` da requisição: sem sessão o contrato responde 401,
   e quem tem a cookie `HttpOnly` é a página que está renderizando.
   --------------------------------------------------------------------------- */

/**
 * `GET /api/perfil/atual/materiais` — a visão do DONO (RF015, DRS §7.4).
 *
 * O que esta leitura tem de diferente das públicas, e por quê:
 *
 *   - **os três estados vêm na resposta** (`PUBLICADO`, `RETIRADO` e `EXCLUIDO`): o RF017
 *     tira o material das listagens PÚBLICAS; esconder do dono o que ele acabou de excluir
 *     seria desonesto (ADR-0011 §2). Quem a tela esconde — ou não oferece — é a AÇÃO, e
 *     derivá-la do estado é apresentação: a autoridade continua no servidor (RNF010);
 *   - **a propriedade vem da sessão, nunca do caminho** (RN001, CA007): não há parâmetro
 *     que peça material de outra pessoa, e a lista de A nunca contém material de B;
 *   - **`page` é 0-based** (contrato, `NumeroPagina`) e a PRIMEIRA página não é enviada:
 *     o default do contrato é 0, e mandá-la criaria uma segunda URL pedindo a mesma coisa.
 *     `size` não é enviado — o padrão do contrato (20) é o da tela, e o envelope devolve o
 *     `size` aplicado, então nenhum número de itens por página é inventado no cliente;
 *   - **conjunto vazio é 200 com `items: []`**, nunca erro (RNF004): é o estado normal de
 *     quem ainda não publicou, e é dele que a tela tira o primeiro passo (RF015).
 */
export function listarMateriaisProprios(
  cabecalhoDeCookie: string,
  pagina: number,
): Promise<Resultado<Pagina<MaterialProprio>>> {
  const consulta = pagina > 0 ? `?page=${pagina}` : '';
  return requisitar<Pagina<MaterialProprio>>(
    `/perfil/atual/materiais${consulta}`,
    { cache: 'no-store' },
    cabecalhoDeCookie,
  );
}

/**
 * `GET /api/perfil/atual/materiais/{slug}` — lê um material PRÓPRIO em qualquer estado
 * (RF015). É a leitura que pré-preenche a edição (card E5.7) e a única que enxerga o
 * material `RETIRADO`, para o qual o detalhe público responde 404 (RN002).
 *
 * O `slug` vai codificado no caminho: ele vem da URL da página ou do envelope da lista, e
 * um valor com caractere especial não pode virar consulta a outro endereço.
 *
 * O que ela NÃO distingue, e é decisão do contrato (ADR-0011 §3): material inexistente,
 * de outra conta ou já `EXCLUIDO` respondem o MESMO 404 — a leitura do dono não vira
 * oráculo de existência de material alheio.
 */
export function obterMaterialProprio(
  cabecalhoDeCookie: string,
  slug: string,
): Promise<Resultado<MaterialProprio>> {
  return requisitar<MaterialProprio>(
    `/perfil/atual/materiais/${encodeURIComponent(slug)}`,
    { cache: 'no-store' },
    cabecalhoDeCookie,
  );
}
