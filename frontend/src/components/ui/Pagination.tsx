import Link from 'next/link';
import type { Route } from 'next';

/**
 * Paginação (RNF021) — links de verdade, não botões com JavaScript: a página lista
 * tem de funcionar e ser compartilhável sem JS, e a URL precisa preservar os
 * filtros (`buildHref` recebe a página e devolve a URL completa).
 *
 * Acessibilidade: `<nav aria-label>`, página atual marcada com `aria-current="page"`
 * (anunciada, e não só pintada — RNF007), navegação por teclado com alvos de 44 px,
 * "Anterior"/"Próxima" nomeados, e nos limites o item vira um `span` com
 * `aria-disabled` em vez de sumir (o controle não muda de lugar sob o cursor).
 */

export type PaginationProps = {
  /** página atual, 1-based */
  page: number;
  totalPages: number;
  /** URL da página pedida, PRESERVANDO busca e filtros atuais. */
  buildHref: (page: number) => string;
  label?: string;
};

type Item = number | 'gap';

function janelaDePaginas(page: number, total: number): Item[] {
  if (total <= 7) return Array.from({ length: total }, (_, i) => i + 1);

  const visiveis = [1, total, page - 1, page, page + 1]
    .filter((p) => p >= 1 && p <= total)
    .sort((a, b) => a - b);

  const itens: Item[] = [];
  let anterior = 0;
  for (const p of visiveis) {
    if (anterior !== 0 && p - anterior > 1) itens.push('gap');
    itens.push(p);
    anterior = p;
  }
  return itens;
}

export function Pagination({ page, totalPages, buildHref, label = 'Paginação dos resultados' }: PaginationProps) {
  if (totalPages <= 1) return null;

  const atual = Math.min(Math.max(page, 1), totalPages);
  const itens = janelaDePaginas(atual, totalPages);
  // A URL vem de `buildHref` (string montada com os filtros atuais), não de um
  // literal de rota — daí o `as Route` do roteador tipado (`typedRoutes`).
  const url = (pagina: number) => buildHref(pagina) as Route;

  return (
    <nav className="sc-pagination" aria-label={label}>
      {atual > 1 ? (
        <Link className="sc-pagination__item" href={url(atual - 1)} rel="prev">
          Anterior<span className="sc-visually-hidden"> — página {atual - 1} de {totalPages}</span>
        </Link>
      ) : (
        <span className="sc-pagination__item sc-pagination__item--dis" aria-disabled="true">
          Anterior<span className="sc-visually-hidden"> — você está na primeira página</span>
        </span>
      )}

      {itens.map((item, indice) =>
        item === 'gap' ? (
          <span key={`gap-${indice}`} className="sc-pagination__ellipsis" aria-hidden="true">
            …
          </span>
        ) : item === atual ? (
          <Link key={item} className="sc-pagination__item" href={url(item)} aria-current="page">
            {item}
            <span className="sc-visually-hidden"> (página atual)</span>
          </Link>
        ) : (
          <Link key={item} className="sc-pagination__item" href={url(item)}>
            {item}
          </Link>
        ),
      )}

      {atual < totalPages ? (
        <Link className="sc-pagination__item" href={url(atual + 1)} rel="next">
          Próxima<span className="sc-visually-hidden"> — página {atual + 1} de {totalPages}</span>
        </Link>
      ) : (
        <span className="sc-pagination__item sc-pagination__item--dis" aria-disabled="true">
          Próxima<span className="sc-visually-hidden"> — você está na última página</span>
        </span>
      )}

      <span className="sc-pagination__info">
        Página {atual} de {totalPages}
      </span>
    </nav>
  );
}
