import type { ReactNode } from 'react';

/**
 * Tabela de dados com cabeçalho.
 *
 * Estrutura: `<table>` com `<caption>` (o nome da tabela existe para quem usa
 * leitor de tela, mesmo quando a página já tem título), `<thead>` com `<th scope="col">`
 * e `<tbody>` com as linhas. Sem `thead`/`scope` o leitor de tela lê célula solta e
 * perde o significado da coluna (RNF006).
 *
 * O visual é `.sc-table`, primitivo de projeto em `layout.css` (ver o comentário
 * lá: o DS2 define `.sc-table` para o próprio documento, com `min-width:660px`,
 * incompatível com 375 px — RNF002).
 *
 * Estado vazio: quando `rows` vem vazio, a tabela continua de pé, com cabeçalho e
 * uma linha explicando — em vez de sumir e deixar a página sem referência.
 */

export type Column<R> = {
  /** chave da coluna; casa com a propriedade da linha quando não há `render`. */
  key: string;
  label: string;
  render?: (row: R) => ReactNode;
};

export type DataTableProps<R> = {
  caption: string;
  columns: Column<R>[];
  rows: R[];
  rowKey: (row: R) => string;
  emptyLabel?: string;
  emptyHint?: string;
};

export function DataTable<R extends Record<string, ReactNode>>({
  caption,
  columns,
  rows,
  rowKey,
  emptyLabel = 'Nenhum registro para exibir.',
  emptyHint,
}: DataTableProps<R>) {
  return (
    <div className="sc-table-wrap">
      <table className="sc-table">
        <caption className="sc-visually-hidden">{caption}</caption>
        <thead>
          <tr>
            {columns.map((coluna) => (
              <th key={coluna.key} scope="col">
                {coluna.label}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.length === 0 ? (
            <tr>
              <td colSpan={columns.length}>
                <span className="sc-body-sm">{emptyLabel}</span>
                {emptyHint ? (
                  <>
                    {' '}
                    <span className="sc-caption">{emptyHint}</span>
                  </>
                ) : null}
              </td>
            </tr>
          ) : (
            rows.map((linha) => (
              <tr key={rowKey(linha)}>
                {columns.map((coluna) => (
                  <td key={coluna.key}>{coluna.render ? coluna.render(linha) : linha[coluna.key]}</td>
                ))}
              </tr>
            ))
          )}
        </tbody>
      </table>
    </div>
  );
}
