/**
 * "Este endereço resolve um recurso PÚBLICO?" — a pergunta que o proxy precisa responder
 * ANTES de o corpo começar a sair, para poder entregar **404 de verdade** (card E2.12).
 *
 * Por que existe: a página de `/materiais/[slug]`, `/u/[slug]` e `/categoria/[slug]` é
 * renderizada no servidor e o estado `não encontrado` precisa sair no HTML entregue
 * (RNF023, §12.3). Medido no card E2.9 e reconfirmado no E2.12: `notFound()` do App
 * Router NÃO serve para isso — em rota dinâmica o App Router responde 404 com o corpo
 * vazio e manda o estado dentro do pacote de JavaScript, o que deixaria quem não executa
 * JS com uma página em branco. Sobra decidir o status fora da renderização: é o proxy
 * (`src/proxy.ts`), que roda antes dela. A página continua decidindo o que MOSTRAR; este
 * módulo responde só a pergunta binária que o proxy precisa — e responde com a MESMA
 * fonte de verdade que a tela (a API pública, via o cliente único de `lib/api/client.ts`).
 *
 * TRÊS RESPOSTAS, e a diferença entre elas é o que evita mentir:
 *
 *   - `publico`        a API entregou o recurso: a página responde 200;
 *   - `nao-encontrado` a API respondeu 404 (inexistente, retirado, excluído ou conta não
 *                      ativa) ou 400 (endereço fora do formato), ou o recurso não está
 *                      entre as categorias ativas — os casos que a tela mostra como
 *                      `não encontrado` e que agora saem com HTTP 404;
 *   - `indefinido`     NÃO houve resposta (rede fora, tempo esgotado, 5xx) ou a resposta
 *                      foi 401/403. Aí não existe "não encontrado" nenhum: a tela mostra
 *                      `erro de rede/servidor`, `sessão expirada` ou `não autorizado` — e
 *                      o status continua **200**, porque 404 afirmaria que o endereço não
 *                      corresponde a nada, e o que aconteceu foi outra coisa.
 *
 * O que este módulo NÃO faz: decidir o contrato. Ele não muda o que a API responde (o 404
 * de recurso continua sendo da API) e não inventa estado novo — só reconhece, do lado de
 * fora da renderização, os mesmos casos que a tela já reconhece por dentro.
 *
 * Custo declarado: uma leitura a mais por visita nas três rotas (a decisão do status e a
 * renderização da página pedem o recurso cada uma). A leitura é a MESMA consulta por
 * `slug` que a tela faz — nenhum endpoint novo, nenhum dado a mais — e a alternativa seria
 * uma segunda forma de perguntar "existe?", que é exatamente o que este módulo evita.
 * A leitura é sempre `no-store` (é o cliente que decide isso): material retirado não pode
 * continuar sendo servido, e um veredito em cache devolveria 200 para um endereço que já
 * saiu do ar (§12.3, CA008).
 */
import { listarCategorias, obterAutor, obterMaterial, type Falha } from './api/client';
import { lerPagina, slugValido } from './pagina';

/** Rotas com um recurso público identificado por `slug` — as três do card E2.12. */
export type RotaComRecurso = 'materiais' | 'u' | 'categoria';

/** O que o proxy faz com o pedido, depois de perguntar à API. */
export type VereditoDoRecurso = 'publico' | 'nao-encontrado' | 'indefinido';

/**
 * A falha da API é "não encontrado"? Só o 404 (recurso fora do público) e o 400 (endereço
 * fora do formato do contrato) — os dois que a tela `EstadoDaFalha` mapeia para o estado
 * `não encontrado` (DRS §15.3). Os outros status são outra coisa (401/403 = identidade,
 * 5xx = servidor) e não podem virar 404 sem mentir sobre a causa.
 */
function ehNaoEncontrado(falha: Falha): boolean {
  return falha.motivo === 'http' && (falha.status === 404 || falha.status === 400);
}

/**
 * O endereço pode, em princípio, corresponder a um recurso? Slug fora do formato e `page`
 * fora do formato não chegam a ser consultados: a tela os recusa antes (`lib/pagina.ts`) e
 * os mostra como `não encontrado`, então o status tem de acompanhar.
 *
 * `/materiais/[slug]` NÃO entra aqui: aquela rota não valida o slug localmente — ela
 * pergunta à API, que responde 400 para endereço fora do formato, e a tela mostra o MESMO
 * estado. Fazer a validação nos dois lados criaria duas regras para a mesma pergunta.
 */
function enderecoPlausivel(slug: string, pageBruto: string | undefined): boolean {
  if (!slugValido(slug)) return false;
  return lerPagina(pageBruto).ok;
}

/**
 * O veredito de uma rota: `slug` é o segmento que veio na URL e `pageBruto` o valor CRU do
 * parâmetro `page` (a validação é parte da leitura, e é a mesma função da tela).
 */
export async function vereditoDoRecurso(
  rota: RotaComRecurso,
  slug: string,
  pageBruto: string | undefined,
): Promise<VereditoDoRecurso> {
  if (rota === 'materiais') {
    const resultado = await obterMaterial(slug);
    if (resultado.ok) return 'publico';
    return ehNaoEncontrado(resultado.falha) ? 'nao-encontrado' : 'indefinido';
  }

  if (!enderecoPlausivel(slug, pageBruto)) return 'nao-encontrado';

  if (rota === 'u') {
    const pagina = lerPagina(pageBruto);
    // `enderecoPlausivel` já recusou o que não é página válida; o `if` existe para o
    // compilador (e para não converter `undefined` por acidente).
    if (!pagina.ok) return 'nao-encontrado';

    const resultado = await obterAutor(slug, pagina.pagina - 1);
    if (resultado.ok) return 'publico';
    return ehNaoEncontrado(resultado.falha) ? 'nao-encontrado' : 'indefinido';
  }

  // `/categoria/[slug]`: o contrato não tem `GET /api/categorias/{slug}` — a pergunta é
  // feita contra a mesma lista de referência que a página consulta (ADR-0006).
  const resultado = await listarCategorias();
  if (!resultado.ok) return 'indefinido';
  return resultado.dados.items.some((item) => item.slug === slug) ? 'publico' : 'nao-encontrado';
}
