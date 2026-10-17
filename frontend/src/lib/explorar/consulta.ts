/**
 * Estado de `/explorar` na URL — leitura, validação e montagem (RF002, RF003, RF004).
 *
 * Módulo PURO, sem `fetch`, sem `process.env`, sem API do Next: é ele que faz a URL ser
 * a única fonte da verdade da tela. Dois motivos para ele existir separado:
 *
 *   1. o mesmo mapeamento serve à tela (servidor), aos controles (cliente) e à
 *      evidência do card — se cada um montasse a URL por conta própria, o "mesmo
 *      endereço, mesma lista" deixaria de ser consequência e passaria a ser sorte;
 *   2. validar o que chega antes de chamar a API é o que transforma parâmetro
 *      malformado em mensagem compreensível, em vez de repassar 400 do backend.
 *
 * Convenções registradas (decisões deste card, não do contrato):
 *
 *   - **Página 1-based na URL.** Para quem compartilha o endereço, "page=2" é a segunda
 *     página. A API conta em 0-based (contrato, `NumeroPagina`); a conversão acontece
 *     UMA vez, em `consultaDaApi()`.
 *   - **Forma canônica**: valor padrão não entra na URL (`ordem=recentes` e `page=1` são
 *     omitidos). Assim existe UMA URL por listagem — duas URLs diferentes descrevendo a
 *     mesma lista seriam justamente o que o critério de aceite 2 quer excluir.
 *   - **Valor presente e vazio é tratado como ausente** (`?categoria=` equivale a nenhum
 *     filtro de categoria). A API responde 400 a `categoria=` vazio (decisão do E2.2);
 *     aqui o formulário GET sem JavaScript envia todo campo vazio ao ser submetido, e
 *     recusar a própria submissão do formulário quebraria a tela em vez de limpar o
 *     filtro. Nada é enviado à API nesse caso — a fronteira continua respeitada.
 *   - **Parâmetro repetido** (`?categoria=a&categoria=b`): vale o primeiro valor. Um
 *     filtro só; escolher o primeiro é determinístico e não inventa combinação.
 */
import type { OrdemMateriais } from '@/lib/api/tipos';

/** Critérios de ordenação do contrato (§12.2), com o rótulo de exibição. */
export const ORDENS: readonly { valor: OrdemMateriais; rotulo: string }[] = [
  { valor: 'recentes', rotulo: 'Publicação mais recente' },
  { valor: 'titulo', rotulo: 'Título (A–Z)' },
];

export const ORDEM_PADRAO: OrdemMateriais = 'recentes';

/** Teto de `q` no contrato (`FiltroBusca`, ADR-0008): acima disso a API responde 400. */
export const LIMITE_BUSCA = 200;

/** Formato de slug do contrato (`FiltroCategoria`, `FiltroTipo`, `slug` de recurso). */
const FORMATO_SLUG = /^[a-z0-9]+(-[a-z0-9]+)*$/;

export type ParametrosExplorar = {
  /** termo de busca já sem espaço nas pontas; vazio = sem busca */
  q: string;
  /** slug da categoria; vazio = sem filtro */
  categoria: string;
  /** slug do tipo de material; vazio = sem filtro */
  tipo: string;
  ordem: OrdemMateriais;
  /** página 1-based, como aparece na URL */
  pagina: number;
};

/** Parâmetro recusado, com o que a tela precisa dizer para a pessoa corrigir. */
export type ParametroInvalido = {
  campo: string;
  valor: string;
  esperado: string;
};

export type LeituraParametros =
  | { ok: true; params: ParametrosExplorar }
  | { ok: false; invalidos: ParametroInvalido[] };

type Brutos = Record<string, string | string[] | undefined>;

function primeiro(valor: string | string[] | undefined): string {
  if (Array.isArray(valor)) return valor[0] ?? '';
  return valor ?? '';
}

/**
 * Lê e valida os parâmetros da URL. Nada de "consertar em silêncio": valor inválido é
 * devolvido como inválido (mesma razão pela qual o ADR-0008 recusa `q` acima do teto
 * em vez de cortar — cortar mudaria o resultado sem o cliente saber).
 */
export function lerParametros(brutos: Brutos): LeituraParametros {
  const invalidos: ParametroInvalido[] = [];

  const qBruto = primeiro(brutos.q);
  if (qBruto.length > LIMITE_BUSCA) {
    invalidos.push({
      campo: 'q',
      valor: `${qBruto.length} caracteres`,
      esperado: `termo de busca com até ${LIMITE_BUSCA} caracteres`,
    });
  }

  const filtros: { campo: 'categoria' | 'tipo'; valor: string }[] = [
    { campo: 'categoria', valor: primeiro(brutos.categoria) },
    { campo: 'tipo', valor: primeiro(brutos.tipo) },
  ];
  const slugs: Record<string, string> = { categoria: '', tipo: '' };
  for (const filtro of filtros) {
    if (filtro.valor === '') continue;
    if (!FORMATO_SLUG.test(filtro.valor)) {
      invalidos.push({
        campo: filtro.campo,
        valor: filtro.valor,
        esperado: 'slug no formato nome-em-minusculas (ex.: computacao-e-tecnologia)',
      });
      continue;
    }
    slugs[filtro.campo] = filtro.valor;
  }

  const ordemBruto = primeiro(brutos.ordem);
  const ordemValida = ORDENS.find((opcao) => opcao.valor === ordemBruto)?.valor;
  if (ordemBruto !== '' && ordemValida === undefined) {
    invalidos.push({
      campo: 'ordem',
      valor: ordemBruto,
      esperado: `um destes valores: ${ORDENS.map((opcao) => opcao.valor).join(', ')}`,
    });
  }

  const paginaBruto = primeiro(brutos.page);
  let pagina = 1;
  if (paginaBruto !== '') {
    if (!/^[1-9]\d*$/.test(paginaBruto)) {
      invalidos.push({
        campo: 'page',
        valor: paginaBruto,
        esperado: 'número inteiro de página, começando em 1',
      });
    } else {
      pagina = Number(paginaBruto);
    }
  }

  if (invalidos.length > 0) return { ok: false, invalidos };

  return {
    ok: true,
    params: {
      q: qBruto.trim(),
      categoria: slugs.categoria ?? '',
      tipo: slugs.tipo ?? '',
      ordem: ordemValida ?? ORDEM_PADRAO,
      pagina,
    },
  };
}

/**
 * Consulta para a API, nos nomes e na contagem do contrato. Padrão é omitido (a API
 * aplica o mesmo default) e `size` não aparece: o padrão do contrato (20) é o que a
 * listagem usa, e o envelope devolve o `size` aplicado.
 */
export function consultaDaApi(params: ParametrosExplorar): {
  q?: string;
  categoria?: string;
  tipo?: string;
  ordem?: OrdemMateriais;
  page: number;
} {
  return {
    q: params.q === '' ? undefined : params.q,
    categoria: params.categoria === '' ? undefined : params.categoria,
    tipo: params.tipo === '' ? undefined : params.tipo,
    ordem: params.ordem === ORDEM_PADRAO ? undefined : params.ordem,
    page: params.pagina - 1,
  };
}

/** URL canônica de `/explorar` para um estado. É a mesma função usada em toda a tela. */
export function urlExplorar(params: ParametrosExplorar): string {
  const busca = new URLSearchParams();
  if (params.q !== '') busca.set('q', params.q);
  if (params.categoria !== '') busca.set('categoria', params.categoria);
  if (params.tipo !== '') busca.set('tipo', params.tipo);
  if (params.ordem !== ORDEM_PADRAO) busca.set('ordem', params.ordem);
  if (params.pagina > 1) busca.set('page', String(params.pagina));

  const texto = busca.toString();
  return texto === '' ? '/explorar' : `/explorar?${texto}`;
}

/**
 * URL com UM filtro removido (`?`) ou limpo, voltando à primeira página: mudar o
 * conjunto de resultados estando na página 5 levaria a uma página que talvez não exista
 * mais — e a URL compartilhada apontaria para "vazio" sem motivo.
 */
export function urlSemFiltro(
  params: ParametrosExplorar,
  campo: 'q' | 'categoria' | 'tipo' | 'ordem',
): string {
  const sem: ParametrosExplorar = {
    ...params,
    q: campo === 'q' ? '' : params.q,
    categoria: campo === 'categoria' ? '' : params.categoria,
    tipo: campo === 'tipo' ? '' : params.tipo,
    ordem: campo === 'ordem' ? ORDEM_PADRAO : params.ordem,
    pagina: 1,
  };
  return urlExplorar(sem);
}

/** URL da página pedida, preservando busca e filtros (RNF021). */
export function urlDaPagina(params: ParametrosExplorar, pagina: number): string {
  return urlExplorar({ ...params, pagina });
}

/** Há algo além do estado padrão? Decide se faz sentido oferecer "limpar tudo". */
export function temFiltrosAtivos(params: ParametrosExplorar): boolean {
  return (
    params.q !== '' ||
    params.categoria !== '' ||
    params.tipo !== '' ||
    params.ordem !== ORDEM_PADRAO ||
    params.pagina > 1
  );
}

/** Número de páginas do envelope do contrato: `total` itens, `size` por página. */
export function totalDePaginas(total: number, size: number): number {
  if (size <= 0) return 1;
  return Math.max(1, Math.ceil(total / size));
}
