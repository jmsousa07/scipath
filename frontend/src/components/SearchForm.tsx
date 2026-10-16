/**
 * Busca do cabeçalho — formulário GET, funciona sem JavaScript.
 *
 * Destino: `/explorar` é a rota canônica da descoberta (`PAGE_BLUEPRINTS.md`), que
 * chega na Etapa 2. Até lá o envio leva a 404 — consequência registrada no card E1.6,
 * não um defeito de layout.
 *
 * Acessibilidade (RNF006/RNF007): o rótulo existe de verdade (só visualmente oculto),
 * o ícone é decorativo e o estado de foco vem do token de foco em `base.css`.
 */
export function SearchForm() {
  return (
    <form className="sc-search" role="search" action="/explorar" method="get">
      <svg className="sc-search__icon" viewBox="0 0 24 24" fill="none" aria-hidden="true" focusable="false">
        <circle cx="11" cy="11" r="6.5" stroke="currentColor" strokeWidth="1.6" />
        <path d="M16 16l4.5 4.5" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" />
      </svg>
      <label className="sc-visually-hidden" htmlFor="busca">
        Buscar material por título, descrição ou autoria
      </label>
      <input
        id="busca"
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