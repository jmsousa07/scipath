import type { NextRequest } from 'next/server';

/**
 * Proxy `/api/*` do Next para a API do SciPath — a MESMA ORIGEM que o ADR-0003 (e) pede.
 *
 * Por que existe, em uma frase: o cookie de sessão é `HttpOnly`, `SameSite=Lax` e viaja
 * sozinho quando o navegador fala com a origem em que está — se o navegador chamasse a
 * API em outro host/porta, o cookie (ou a configuração para aceitá-lo) viraria o desenho
 * do sistema. Aqui o navegador só conhece `localhost:3000`; este arquivo encaminha.
 *
 * O que ele faz questão de NÃO mudar:
 *   - `Cookie` do pedido vai como veio e `Set-Cookie` da resposta volta TODOS (a leitura
 *     `getSetCookie()` preserva os dois: `SCIPATH_SESSAO` e `XSRF-TOKEN`);
 *   - `X-CSRF-Token` do navegador é encaminhado — a checagem é do servidor, o proxy não
 *     valida nada e não "conserta" nada;
 *   - nada de corpo de erro inventado para resposta da API: o `ProblemDetail` atravessa
 *     intacto, porque é ele que a tela usa para decidir o estado (DRS §15.3).
 *
 * `API_BASE_URL` é lido em TEMPO DE EXECUÇÃO (não no build, como aconteceria com um
 * `rewrite` do `next.config.ts`): é a mesma razão declarada em `lib/api/client.ts` — a
 * mesma imagem pode apontar para outro backend sem rebuild. Em produção, um proxy
 * reverso faz o mesmo trabalho (é o que o ADR-0003 (e) descreve para produção).
 *
 * Quando a API não responde, a resposta é nossa e diz isso (502, com corpo JSON
 * próprio): o contrato não cobriria "API inalcançável" com uma classe dele, e atribuir
 * ao servidor um erro que não veio dele seria mentir para a tela.
 */
const BASE_PADRAO = 'http://localhost:8080/api';

/** Teto de tempo do encaminhamento: a API que aceita a conexão e não responde não prende o pedido. */
const LIMITE_MS = 8000;

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

function baseDaApi(): string {
  const bruto = process.env.API_BASE_URL ?? process.env.NEXT_PUBLIC_API_BASE_URL ?? BASE_PADRAO;
  return bruto.replace(/\/+$/, '');
}

async function encaminhar(
  pedido: NextRequest,
  contexto: { params: Promise<{ caminho: string[] }> },
): Promise<Response> {
  const { caminho } = await contexto.params;
  const sufixo = caminho.map((parte) => encodeURIComponent(parte)).join('/');
  const destino = `${baseDaApi()}/${sufixo}${pedido.nextUrl.search}`;

  const cabecalhos = new Headers(pedido.headers);
  // Cabeçalhos que descrevem a conexão do navegador com ESTE servidor, não com a API.
  cabecalhos.delete('host');
  cabecalhos.delete('connection');
  cabecalhos.delete('content-length');
  cabecalhos.delete('accept-encoding');
  // `expect: 100-continue` é um pedido do CLIENTE ao PRÓXIMO salto ("me diga se posso mandar o
  // corpo"); quem tem de responder isso é este servidor, e o `fetch` do Node RECUSA a requisição
  // quando ele segue adiante (medido: `fetch failed` → 502 na publicação). O caso aparece em
  // corpo grande — o `curl` o acrescenta acima de 1 KB e a publicação é a primeira escrita do
  // produto que manda arquivo (E4.6). Sem esta linha, todo upload feito por um cliente que
  // anuncia `Expect` falha com "API inalcançável", que é uma mentira sobre a causa.
  cabecalhos.delete('expect');

  const temCorpo = pedido.method !== 'GET' && pedido.method !== 'HEAD';

  try {
    const resposta = await fetch(destino, {
      method: pedido.method,
      headers: cabecalhos,
      body: temCorpo ? await pedido.arrayBuffer() : undefined,
      redirect: 'manual',
      cache: 'no-store',
      signal: AbortSignal.timeout(LIMITE_MS),
    });

    const saida = new Headers(resposta.headers);
    // `content-encoding` fica para trás: o `fetch` já descomprimiu o corpo.
    saida.delete('content-encoding');
    saida.delete('content-length');
    saida.delete('set-cookie');

    const devolvida = new Response(resposta.body, { status: resposta.status, headers: saida });
    for (const cookie of resposta.headers.getSetCookie()) {
      devolvida.headers.append('set-cookie', cookie);
    }
    return devolvida;
  } catch {
    return Response.json(
      {
        title: 'API inalcançável',
        status: 502,
        detail:
          'O servidor da aplicação não respondeu no tempo esperado. O pedido não foi aplicado; tente de novo em instantes.',
      },
      { status: 502 },
    );
  }
}

export const GET = encaminhar;
export const POST = encaminhar;
export const PUT = encaminhar;
export const PATCH = encaminhar;
export const DELETE = encaminhar;
export const HEAD = encaminhar;
