/**
 * Rotas públicas do produto usadas pela descoberta.
 *
 * Por que este módulo existe: `next.config.ts` liga `typedRoutes`, então um `href` só
 * compila se a rota existir no projeto. As funções daqui montam endereços de rotas que
 * podem não existir nesta branch ainda — o card E2.9 entregou `/materiais/[slug]`, e
 * `/u/[slug]` continua pendente do card E2.10:
 *
 *   - `/materiais/[slug]` → EXISTE desde o card E2.9 (`app/materiais/[slug]/page.tsx`);
 *     o cast segue aqui por uniformidade com a função vizinha, e sai na limpeza que
 *     olhar as duas juntas (card próprio — o `routeMaterial` do card E2.8 é o mesmo
 *     ponto de chamada, não um segundo caminho);
 *   - `/u/[slug]`         → card E2.10.
 *
 * O cast é o mesmo mecanismo já usado no E1.7 (`PageState`/`mensagens.ts` apontando para
 * `/entrar` e `/explorar` antes de existirem), com o motivo escrito aqui e a pendência
 * registrada no card. A alternativa — não linkar — deixaria o visitante sem caminho do
 * resultado até o material, que é o propósito da listagem (RF002, CA001) e do próprio
 * detalhe (RF007).
 *
 * Quando as rotas existirem, o cast continua válido e pode ser removido numa limpeza
 * (card próprio): o compilador passa a verificar o literal sozinho.
 */
import type { Route } from 'next';

/** `/materiais/[slug]` — detalhe público do material (rota entregue no card E2.9). */
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

/**
 * `/painel/materiais/[slug]/editar` — edição dos metadados de um material próprio
 * (RF016, PAGE_BLUEPRINTS.md §13).
 *
 * O identificador é o **slug**, não um `id`: o slug é a chave canônica e estável do
 * material (RNF025) e é o que a lista do dono já tem em mãos — usar um `id` interno
 * obrigaria o contrato a publicá-lo, e ele não publica. A correção da tabela de rotas do
 * `PAGE_BLUEPRINTS.md`, que dizia `[id]`, é do card E5.6.
 *
 * Com `as Route` porque a página ainda NÃO existe nesta branch — ela é o card E5.7 — e o
 * `typedRoutes` só conhece as rotas escritas no projeto. É o MESMO mecanismo (e o mesmo
 * motivo registrado) de `rotaAutor` e `rotaCategoria` quando as páginas delas não
 * existiam: sem o cast, o link não compila; sem o link, o dono lê uma lista de materiais
 * que não leva a lugar nenhum. Ao entregar a página, o cast pode sair (card próprio).
 */
export function rotaEditarMaterial(slug: string): Route {
  return `/painel/materiais/${slug}/editar` as Route;
}

/**
 * Destino interno preservado pelo parâmetro `proximo` (DRS §15.3: 401 → login com o
 * destino preservado). O valor vem da barra de endereços, então é montado em tempo de
 * execução e o `typedRoutes` não o verifica: daí o cast. Quem ACEITA esse valor é
 * `caminhoInterno` (`lib/sessao/destino.ts`), aplicado no SERVIDOR — só passa caminho
 * interno (`/…`); um `?proximo=` apontando para outro domínio transformaria o login em
 * redirecionador aberto, e um valor recusado nem chega ao formulário.
 */
export function rotaPreservada(url: string): Route {
  return url as Route;
}
