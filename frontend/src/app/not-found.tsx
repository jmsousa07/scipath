import Link from 'next/link';
import { PageState } from '@/components/ui/PageState';

/**
 * Estado `não encontrado` do DRS §15.3 para endereço que não casa com rota nenhuma.
 *
 * Por que esta página existe: o App Router entrega o `not-found.tsx` da raiz quando a URL
 * não corresponde a nenhuma rota do projeto. Sem ele, o Next usa a página 404 padrão — em
 * inglês, fora do contrato visual — e o estado `não encontrado` do produto não existiria.
 * Medido na evidência do card: este caso sai com HTTP 404 e com o estado RENDERIZADO no
 * HTML entregue (a 404 de rota inexistente é servida pronta, sem depender de JavaScript).
 *
 * Redação genérica de propósito: aqui chega qualquer endereço errado, não só endereço de
 * material. O caso "material retirado ou inexistente" tem redação própria e é renderizado
 * pela PRÓPRIA página `/materiais/[slug]` (`EstadoDaFalha`, em `app/materiais/[slug]/`),
 * com o endereço pedido à vista — porque numa rota dinâmica o `notFound()` do App Router
 * entrega o estado apenas no pacote de JavaScript (medido no card E2.9: o HTML sai com o
 * `<main>` vazio). O status 404 daquelas rotas é decidido fora da renderização, pelo proxy
 * (`src/proxy.ts`, card E2.12); esta página é o 404 do endereço que não casa com rota
 * nenhuma. As duas decisões estão registradas no docstring de `/materiais/[slug]` e em
 * `PAGE_BLUEPRINTS.md`.
 *
 * O status 404 é o que mantém endereço inexistente fora do índice de busca (RNF024,
 * §12.3). A ação padrão do estado (`MENSAGENS_PAGINA['not-found']`) já leva à busca; o
 * link abaixo devolve o caminho para a página inicial, para quem chegou por link quebrado.
 */
export default function NaoEncontrado() {
  return (
    <div className="sc-page">
      <PageState
        kind="not-found"
        title="Não encontramos nada neste endereço"
        message="O endereço pedido não corresponde a nenhuma página pública: o link pode estar incorreto ou o endereço pode ter mudado. Conteúdo retirado por quem publicou também sai do ar, e responde igual a um endereço que nunca existiu — de propósito, a plataforma não revela o que foi retirado."
      />
      <p className="mt-6">
        <Link className="sc-btn sc-btn--ghost sc-btn--sm" href="/">
          Voltar ao início
        </Link>
      </p>
    </div>
  );
}
