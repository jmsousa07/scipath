/**
 * Cliente único da API pública (fronteira: `contracts/openapi.yaml` v0.3.0).
 *
 * Por que existe: a regra do projeto é um cliente tipado e não `fetch` espalhado pela
 * tela. Aqui moram a base da URL, o limite de tempo, a leitura do `ProblemDetail` e a
 * distinção entre "o servidor respondeu errado" e "não houve resposta" — a tela decide
 * o estado a exibir, o cliente só relata.
 *
 * ESTE MÓDULO É DE SERVIDOR. Nenhum componente de cliente (`'use client'`) pode
 * importá-lo: a base lê `process.env`, que no bundle do navegador não existe. A
 * separação não é estética — sem ela, a URL do backend vazaria para o cliente e o
 * visitante passaria a falar direto com a API, o que não é o desenho do produto
 * (a busca é renderizada no servidor, RNF023–RNF025).
 */
import type {
  ItemTaxonomia,
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

async function requisitar<T>(caminho: string, init: IniciarRequisicao): Promise<Resultado<T>> {
  const url = `${baseDaApi()}${caminho}`;
  let resposta: Response;
  try {
    resposta = await fetch(url, { ...init, signal: AbortSignal.timeout(LIMITE_MS) });
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
 */
export type ConsultaMateriais = {
  q?: string;
  categoria?: string;
  tipo?: string;
  ordem?: OrdemMateriais;
  page?: number;
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
 * `GET /api/materiais` — listagem paginada (RF002). `size` NÃO é enviado: o padrão do
 * contrato (20) é o que a tela usa, e o envelope da resposta devolve o `size` aplicado
 * — nenhum número de itens por página é inventado aqui.
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

export function listarCategorias(): Promise<Resultado<Pagina<ItemTaxonomia>>> {
  return requisitar<Pagina<ItemTaxonomia>>(`/categorias${comParametros({ size: 100 })}`, {
    next: { revalidate: REVALIDACAO_TAXONOMIA_S },
  });
}

export function listarTiposMaterial(): Promise<Resultado<Pagina<ItemTaxonomia>>> {
  return requisitar<Pagina<ItemTaxonomia>>(`/tipos-material${comParametros({ size: 100 })}`, {
    next: { revalidate: REVALIDACAO_TAXONOMIA_S },
  });
}
