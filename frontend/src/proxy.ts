import { NextResponse, type NextRequest } from 'next/server';
import { vereditoDoRecurso, type RotaComRecurso } from '@/lib/recurso-publico';

/**
 * Proxy do Next — quem entrega o **404 de verdade** nas páginas dinâmicas (card E2.12).
 *
 * O PROBLEMA, medido (cards E2.9 e E2.12): `/materiais/[slug]`, `/u/[slug]` e
 * `/categoria/[slug]` renderizam o estado `não encontrado` do DRS §15.3 dentro da própria
 * página, no HTML entregue, porque é isso que RNF023/§12.3 exigem. Mas quem sabia responder
 * 404 era só a API: a página respondia **200** com o estado no corpo e `noindex, nofollow`.
 * O `notFound()` do App Router não resolve — em rota dinâmica ele responde 404 com o corpo
 * VAZIO e manda o estado dentro do pacote de JavaScript (medido: o texto só aparece no
 * `script` do RSC, nunca no documento; quem não executa JS veria página em branco).
 *
 * A SAÍDA, também medida: decidir o status ANTES de o corpo começar a sair — que é,
 * literalmente, onde este arquivo roda. O proxy pergunta à API se o endereço resolve um
 * recurso público (`lib/recurso-publico.ts`) e, quando não resolve, reescreve a MESMA
 * requisição para a MESMA rota com `status: 404`. A página então renderiza normalmente, com
 * o estado no HTML, e a resposta sai 404.
 *
 * O `noindex` continua — mas quem o entrega no 404 é o Next, não a página: em resposta com
 * status > 400 o framework injeta `<meta name="robots" content="noindex"/>` e essa meta
 * substitui a do `generateMetadata`. O `noindex, nofollow` declarado pela página segue sendo
 * o que sai nas respostas **200** de erro (API fora do ar), que não são 404. Medido na
 * bateria `docs/qa/e2-12-evidencia.sh`, seções 3.5 e 6.4 — está registrado em PAGE_BLUEPRINTS.
 *
 * Três detalhes que parecem detalhe e não são:
 *
 *   1. **O marcador na URL.** Um `rewrite` para a URL IDÊNTICA é descartado pelo Next: medido
 *      no card, a resposta volta 404 com o corpo vazio (só a casca e um `div hidden`), porque
 *      sem destino distinto não há re-renderização. Por isso o destino é a mesma rota com o
 *      parâmetro `__nao-publico=1`, que a página ignora (nenhuma tela lê esse parâmetro;
 *      `page` continua sendo o único parâmetro que elas leem). O endereço na barra de
 *      navegador NÃO muda — `rewrite` reescreve por dentro, não redireciona. Quem digitar o
 *      marcador à mão recebe a página com 200: o proxy não o interpreta como pedido de 404
 *      (o `searchParams.has` abaixo é o que impede que a própria reescrita volte por aqui);
 *   2. **`indefinido` não é 404.** Sem resposta da API (rede fora, 5xx) ou com 401/403, o
 *      status continua 200 e a página mostra o estado que corresponde à causa. Responder 404
 *      ali afirmaria que o endereço não corresponde a nada — mentira sobre outra coisa;
 *   3. **A API não muda.** Isto é decisão de APRESENTAÇÃO: o contrato v0.3 continua respondendo
 *      o que sempre respondeu, e é ele que decide se o recurso é público (RN002, CA008).
 *      O proxy só faz a mesma pergunta uma vez a mais, para poder escolher o status — custo
 *      declarado em `lib/recurso-publico.ts`.
 *
 * Convenção do Next 16: este arquivo se chama `proxy.ts` (`middleware.ts` está depreciado
 * nesta versão — o build avisa). O `matcher` cobre exatamente as três rotas do card; nenhuma
 * outra rota do produto passa por aqui.
 */
/** O parâmetro que marca a reescrita. Não é lido por tela nenhuma — ver o docstring acima. */
const MARCADOR = '__nao-publico';

export function proxy(requisicao: NextRequest) {
  const { pathname, searchParams } = requisicao.nextUrl;
  const segmentos = pathname.split('/');
  // `/materiais/<slug>`: ['', 'materiais', '<slug>']. Qualquer outra forma (segmento a mais,
  // barra no fim, raiz) não é uma das três rotas e segue sem passar pela consulta.
  const [, primeiraParte, slug, ...resto] = segmentos;
  const rota = ehRotaComRecurso(primeiraParte) ? primeiraParte : undefined;

  if (rota === undefined || !slug || resto.length > 0) return NextResponse.next();

  // Guarda de reentrância: o destino da reescrita é a mesma rota, então o marcador não pode
  // disparar uma segunda decisão (que reescreveria de novo, e de novo).
  if (searchParams.has(MARCADOR)) return NextResponse.next();

  return decidir(rota, slug, searchParams.get('page') ?? undefined, requisicao);
}

async function decidir(
  rota: RotaComRecurso,
  slug: string,
  pageBruto: string | undefined,
  requisicao: NextRequest,
): Promise<NextResponse> {
  const veredito = await vereditoDoRecurso(rota, slug, pageBruto);
  if (veredito !== 'nao-encontrado') return NextResponse.next();

  const destino = requisicao.nextUrl.clone();
  destino.searchParams.set(MARCADOR, '1');
  return NextResponse.rewrite(destino, { status: 404 });
}

function ehRotaComRecurso(valor: string | undefined): valor is RotaComRecurso {
  return valor === 'materiais' || valor === 'u' || valor === 'categoria';
}

export const config = {
  matcher: ['/materiais/:slug', '/u/:slug', '/categoria/:slug'],
};
