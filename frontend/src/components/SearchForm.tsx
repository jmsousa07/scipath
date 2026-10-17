/**
 * Busca do produto — formulário GET, funciona sem JavaScript.
 *
 * Destino: `/explorar` é a rota canônica da descoberta (`PAGE_BLUEPRINTS.md`); o envio
 * leva a `/explorar?q=<termo>`, que é a URL compartilhável da busca.
 *
 * Usado em dois lugares: o cabeçalho (todas as páginas) e a busca em destaque da
 * página inicial (card E2.7). Por isso o `id` é parâmetro: dois formulários iguais na
 * mesma página com o mesmo `id` repetiriam o alvo do rótulo (`<label for>`) e o
 * documento teria id duplicado — o que quebra a associação rótulo/controle (RNF006).
 * O comportamento é o MESMO nos dois: nenhuma variante de tela (DESIGN_SYSTEM.md §0.2).
 *
 * Acessibilidade (RNF006/RNF007): o rótulo existe de verdade (só visualmente oculto),
 * o ícone é decorativo e o estado de foco vem do token de foco em `base.css`.
 */
export function SearchForm({ id = 'busca' }: { id?: string }) {
  return (
    <form className="sc-search" role="search" action="/explorar" method="get">
      <svg className="sc-search__icon" viewBox="0 0 24 24" fill="none" aria-hidden="true" focusable="false">
        <circle cx="11" cy="11" r="6.5" stroke="currentColor" strokeWidth="1.6" />
        <path d="M16 16l4.5 4.5" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
      </svg>
      <label className="sc-visually-hidden" htmlFor={id}>
        Buscar material por título, descrição ou autoria
      </label>
      <input
        id={id}
        name="q"
        type="search"
        placeholder="Buscar material"
        autoComplete="off"
        maxLength={120}
      />
      <button className="sc-btn sc-btn--ghost sc-btn--sm" type="submit">
        Buscar
      </button>
    </form>
  );
}