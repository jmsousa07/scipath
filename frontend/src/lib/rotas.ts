/**
 * Rotas públicas do produto usadas pela descoberta.
 *
 * Por que este módulo existe: `next.config.ts` liga `typedRoutes`, então um `href` só
 * compila se a rota existir no projeto — o que é bom, e é justamente o que trava esta
 * tela hoje. A listagem precisa apontar para rotas que ainda NÃO existem nesta branch:
 *
 *   - `/materiais/[slug]` → card E2.9;
 *   - `/u/[slug]`         → card E2.10.
 *
 * A saída é o mesmo mecanismo já usado no E1.7 (`PageState`/`mensagens.ts` apontando
 * para `/entrar` e `/explorar` antes de existirem): o cast para `Route`, com o motivo
 * escrito aqui e a pendência registrada no card. A alternativa — não linkar — deixaria
 * o visitante sem caminho do resultado até o material, que é o propósito da listagem
 * (RF002, CA001).
 *
 * Quando as rotas existirem, o cast continua válido e pode ser removido numa limpeza
 * (card próprio): o compilador passa a verificar o literal sozinho.
 */
import type { Route } from 'next';

/** `/materiais/[slug]` — detalhe público do material (dono da rota: card E2.9). */
export function rotaMaterial(slug: string): Route {
  return `/materiais/${slug}` as Route;
}

/** `/u/[slug]` — perfil público do autor (dono da rota: card E2.10). */
export function rotaAutor(slug: string): Route {
  return `/u/${slug}` as Route;
}

/**
 * `/categoria/[slug]` — página da categoria (RF007; dono da rota: card E2.10).
 *
 * Com `as Route`, como as duas acima, e o motivo foi MEDIDO neste card: mesmo com a rota
 * existindo no projeto, o `Route` do `typedRoutes` recusa um template literal montado em tempo
 * de execução (`/categoria/${slug}` não é atribuível a `Route`) — o gerador de tipos só conhece
 * os literais que aparecem escritos no código. `tsc --noEmit` sozinho não acusa, porque os tipos
 * de rota são gerados no build; foi o `next build` que reprovou. O cast continua sendo o
 * mecanismo do projeto (E1.7, E2.7, E2.9) e some quando houver geração de tipos a partir do
 * contrato de rotas — card próprio.
 */
export function rotaCategoria(slug: string): Route {
  return `/categoria/${slug}` as Route;
}

/** `/explorar` com ou sem filtros — rota desta tela; o cast cobre a query montada. */
export function rotaExplorar(url: string): Route {
  return url as Route;
}
