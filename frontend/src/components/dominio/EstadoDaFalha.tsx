import Link from 'next/link';
import { PageState } from '@/components/ui/PageState';
import { Recarregar } from '@/components/ui/Recarregar';
import type { Falha } from '@/lib/api/client';

/**
 * Resposta que não trouxe o recurso → o estado do DRS §15.3 que corresponde à CAUSA.
 *
 * Por que é um componente do domínio, e não código repetido em cada página: `/u/[slug]`
 * (RF006), `/categoria/[slug]` (RF007) e as telas que vierem depois fazem a MESMA pergunta
 * — "a API não respondeu o que eu pedi; o que eu digo ao visitante?" — e a resposta precisa
 * ser a mesma (RNF003). O card E2.9 escreveu este mapeamento dentro da página dele; quando
 * esta rota foi escrita, ele passou a ter dois consumidores, e o segundo consumidor é
 * exatamente o gatilho do DESIGN_SYSTEM.md §0.2 para promover o código ao sistema.
 *
 * Mapeamento, um por um:
 *
 *   - **404 → `não encontrado`.** É o "este endereço não corresponde a nada público". A
 *     redação é a mesma para perfil não ativo, perfil inexistente e slug fora do formato, de
 *     propósito: a API responde o mesmo 404 para os dois primeiros (RN002, CA008) e uma tela
 *     que os distinguisse desfaria essa decisão, contando que aquele perfil existe mas não é
 *     público;
 *   - **400 → `erro de rede/servidor`, e não `não encontrado`.** Este é o ponto onde estas
 *     rotas diferem de `/explorar`: lá um 400 vem de um filtro que a PESSOA escreveu na URL e
 *     é justo dizer "o endereço não forma uma listagem"; aqui o `slug` e o `page` são
 *     validados ANTES de a requisição sair (`lib/pagina.ts`), então um 400 da API significa
 *     que a plataforma mandou uma requisição que a própria API recusa — defeito do lado de
 *     cá. Chamar isso de "endereço não encontrado" culparia a pessoa por um defeito nosso;
 *     fica em `erro de rede/servidor`, que é o estado para "o servidor não atendeu ao
 *     pedido";
 *   - **401 → `sessão expirada`, 403 → `não autorizado`.** Numa rota pública de descoberta
 *     eles não deveriam ocorrer — o contrato diz que os dois pertencem a operações com
 *     identidade, que começam na Etapa 3. O mapeamento existe para não mentir: dizer "falha
 *     de conexão" quando o servidor respondeu 403 manda a pessoa conferir a internet por um
 *     defeito que não é dela;
 *   - **o resto → `erro de rede/servidor`**, com a ação de tentar de novo. Aqui entra o
 *     que não houve resposta, a resposta fora do contrato e o 5xx.
 *
 * `data-state` no HTML: o estado `não encontrado` sai com o marcador do `PageState`, e é o
 * ÚNICO marcador da página nesse caso (por isso ele não aparece dentro de um bloco
 * `success`). O STATUS 404 que acompanha esse estado é decidido fora da renderização, pelo
 * proxy (`src/proxy.ts`, card E2.12) — este componente decide o que a tela MOSTRA, e as duas
 * decisões saem da mesma pergunta feita à API (`lib/recurso-publico.ts`).
 */
export type EstadoDaFalhaProps = {
  falha: Falha;
  /** o que a tela tentou carregar, na voz da página: "os materiais deste autor"… */
  oQue: string;
  /** título do estado `não encontrado` — específico de cada tela. */
  tituloNaoEncontrado: string;
  /** por que este endereço não corresponde a nada, na voz da tela. */
  mensagemNaoEncontrado: string;
  /** endereço que a pessoa pediu, para ela poder corrigir o link. */
  endereco: string;
};

export function EstadoDaFalha({
  falha,
  oQue,
  tituloNaoEncontrado,
  mensagemNaoEncontrado,
  endereco,
}: EstadoDaFalhaProps) {
  if (falha.motivo === 'http') {
    if (falha.status === 401) return <Moldura><PageState kind="session-expired" /></Moldura>;
    if (falha.status === 403) return <Moldura><PageState kind="unauthorized" /></Moldura>;
    if (falha.status === 404) {
      return (
        <Moldura>
          <PageState
            kind="not-found"
            title={tituloNaoEncontrado}
            message={mensagemNaoEncontrado}
            details={
              <p className="sc-body">
                Endereço pedido: <span className="sc-mono">{endereco}</span>
              </p>
            }
          />
        </Moldura>
      );
    }
  }

  return (
    <Moldura>
      <PageState
        kind="network-error"
        title={`Não foi possível carregar ${oQue}`}
        message={`A resposta da API não chegou ou o servidor falhou ao responder. Nada foi exibido no lugar ${oQue} justamente para não mostrar informação que não é a real. Tente de novo — se continuar, tente outra vez em alguns minutos.`}
        action={<Recarregar />}
      />
      <p className="mt-6">
        <Link className="sc-btn sc-btn--ghost sc-btn--sm" href="/explorar">
          Ver todos os materiais publicados
        </Link>
      </p>
    </Moldura>
  );
}

/** Casca da página (`sc-page`) para o bloco de estado ficar com a mesma largura da tela. */
function Moldura({ children }: { children: React.ReactNode }) {
  return <div className="sc-page">{children}</div>;
}
