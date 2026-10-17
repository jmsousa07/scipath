import Link from 'next/link';
import { CardMaterial } from '@/components/dominio/CardMaterial';
import { PageState } from '@/components/ui/PageState';
import { Pagination } from '@/components/ui/Pagination';
import { totalDePaginas } from '@/lib/explorar/consulta';
import { rotaExplorar } from '@/lib/rotas';
import type { MaterialResumo, Pagina } from '@/lib/api/tipos';

/**
 * Listagem de materiais públicos — o componente de lista do produto.
 *
 * Existe separado da página por decisão de reuso: o E2.10 entrega `/categoria/[slug]` e
 * `/u/[slug]` "com o mesmo componente de listagem do E2.8 (não duplicar tela)". Por isso
 * aqui não há NADA específico de `/explorar`: os filtros, a busca e as sugestões do
 * estado vazio vêm de fora, por prop.
 *
 * Responsabilidades:
 *   - estado `vazio` da listagem, nunca tela branca (DRS §15.3). Dois casos diferentes,
 *     e a diferença importa:
 *       · `total === 0`  → nada corresponde à consulta: quem hospeda a listagem decide o
 *         texto e as sugestões (limpar filtro, ver categorias);
 *       · `total > 0` e página sem item → a consulta TEM resultado, a página pedida é que
 *         passou do fim. Dizer "nenhum material encontrado" aí seria mentira; a ação é ir
 *         para a última página, que existe e é calculada do envelope (nunca de meta
 *         inventada);
 *   - paginação com alvo de 44 px, `aria-current` e URL que preserva os filtros
 *     (`urlDaPagina`, RNF021);
 *   - contagem anunciada por texto, não por cor (RNF007).
 */
export type ListagemDeMateriaisProps = {
  /** envelope do contrato (`GET /api/materiais`): `items`, `page`, `size`, `total` */
  pagina: Pagina<MaterialResumo>;
  /** URL que hospeda a listagem para uma página — preserva os filtros da tela */
  urlDaPagina: (pagina: number) => string;
  /** o que mostrar quando a consulta não tem resultado nenhum */
  vazio: React.ReactNode;
  /** nome do bloco para leitor de tela (ex.: "Materiais encontrados") */
  rotulo: string;
};

function contagem(total: number): string {
  return total === 1 ? '1 material encontrado' : `${total} materiais encontrados`;
}

export function ListagemDeMateriais({ pagina, urlDaPagina, vazio, rotulo }: ListagemDeMateriaisProps) {
  const { items, total, size } = pagina;
  const totalPaginas = totalDePaginas(total, size);

  if (items.length === 0) {
    if (total === 0) return <>{vazio}</>;
    return (
      <PageState
        kind="empty"
        tituloNivel={3}
        title="Esta página não tem resultados"
        message={`A consulta tem ${total === 1 ? '1 material' : `${total} materiais`}, mas não há item nesta página: a lista termina na página ${totalPaginas}.`}
        action={
          <Link className="sc-btn sc-btn--ghost sc-btn--sm" href={rotaExplorar(urlDaPagina(totalPaginas))}>
            Ir para a última página
          </Link>
        }
      />
    );
  }

  return (
    // `data-state="success"` no próprio bloco da lista (DRS §15.3): assim a rota tem
    // UM marcador de estado no HTML — este, quando a lista sai. Em página sem
    // resultado quem marca é o estado `empty`, que vem de fora: `grep 'data-state'`
    // na resposta precisa devolver uma resposta só.
    <section aria-label={rotulo} data-state="success">
      <p className="sc-helper" role="status">
        {contagem(total)} · página {pagina.page + 1} de {totalPaginas}
      </p>

      <ul className="sc-listagem">
        {items.map((material) => (
          <li key={material.slug}>
            <CardMaterial material={material} />
          </li>
        ))}
      </ul>

      <Pagination page={pagina.page + 1} totalPages={totalPaginas} buildHref={urlDaPagina} />
    </section>
  );
}
