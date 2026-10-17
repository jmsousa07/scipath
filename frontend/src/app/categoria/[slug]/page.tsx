import type { Metadata } from 'next';
import Link from 'next/link';
import { cache } from 'react';
import { EstadoDaFalha } from '@/components/dominio/EstadoDaFalha';
import { ListagemDeMateriais } from '@/components/dominio/ListagemDeMateriais';
import { PageState } from '@/components/ui/PageState';
import { listarCategorias, listarMateriais, type Falha } from '@/lib/api/client';
import type { CategoriaDaPagina, MaterialResumo, Pagina } from '@/lib/api/tipos';
import { lerPagina, slugValido, urlComPagina } from '@/lib/pagina';
import { rotaCategoria, rotaExplorar } from '@/lib/rotas';

/**
 * `/categoria/[slug]` — contexto da categoria e os materiais dela (RF007,
 * PAGE_BLUEPRINTS.md §5; a taxonomia de um nível só é a do ADR-0006).
 *
 * Renderizada no SERVIDOR: nome, descrição, `canonical` e a lista saem no HTML entregue,
 * sem depender de JavaScript (RNF023–RNF025).
 *
 * COMO ESTA PÁGINA DESCOBRE A CATEGORIA — decisão registrada, porque o contrato não tem
 * `GET /api/categorias/{slug}`:
 *
 *   1. o nome e a descrição vêm de `GET /api/categorias?size=100` (a taxonomia de referência
 *      do ADR-0006: 13 categorias, teto por requisição do contrato), procurando o `slug`
 *      pedido entre os itens ativos;
 *   2. os materiais vêm de `GET /api/materiais?categoria={slug}`, com o MESMO `slug` que a
 *      listagem pública aceita como filtro (é o que o E2.5 provou do lado do backend, e é o
 *      que garante que as duas telas nunca divirjam: RN013);
 *   3. as duas leituras são independentes e vão juntas (`Promise.all`).
 *
 * Consequência que precisa estar escrita: **categoria que não existe entre as ativas não tem
 * página**. `GET /api/materiais?categoria=slug-inexistente` responde 200 com lista vazia (o
 * contrato recusa 404 ali, de propósito: filtro sem resultado não é endereço errado), mas uma
 * PÁGINA que se anuncia como "a categoria X" e não tem X para mostrar seria a plataforma
 * inventando uma categoria a partir de um filtro vazio. Então o endereço de uma categoria
 * desconhecida — inexistente ou desativada, a listagem só devolve ativa — cai no estado
 * `não encontrado`, e a tela oferece as categorias que EXISTEM na resposta, em vez de uma
 * lista fixa no código.
 *
 * Os sete estados do DRS §15.3 nesta rota: `sucesso`, `vazio` (categoria sem material
 * publicado — o acervo pode estar começando), `não encontrado` (categoria desconhecida, slug
 * fora do formato e `page` fora do formato), `erro de rede/servidor` com "tentar de novo",
 * 401/403 mapeados como nas outras rotas públicas, e `carregando` **sem** tela intermediária
 * pela razão medida nos cards E2.7–E2.9 (com `loading.tsx` o conteúdo real sai em bloco
 * `hidden` e quem não executa JS veria "Carregando" para sempre — contra RNF023/§12.3).
 */

type Props = {
  params: Promise<{ slug: string }>;
  searchParams: Promise<Record<string, string | string[] | undefined>>;
};

type Leitura =
  | { tipo: 'endereco-recusado'; campo: string; valor: string; esperado: string }
  | { tipo: 'falha'; falha: Falha }
  | { tipo: 'sem-categoria' }
  | { tipo: 'categoria'; categoria: CategoriaDaPagina; outras: CategoriaDaPagina[]; materiais: Pagina<MaterialResumo> };

function primeiro(valor: string | string[] | undefined): string | undefined {
  if (Array.isArray(valor)) return valor[0];
  return valor;
}

/**
 * Uma leitura por render, memoizada por requisição (`cache` do React): `generateMetadata` e a
 * página usam o mesmo resultado, então uma visita gera UMA consulta de taxonomia e UMA de
 * materiais — e não quatro (a evidência conta as linhas no log do fixture).
 */
const lerCategoria = cache(async (slug: string, pageBruto: string | undefined): Promise<Leitura> => {
  if (!slugValido(slug)) {
    return {
      tipo: 'endereco-recusado',
      campo: 'slug',
      valor: slug,
      esperado: 'slug no formato nome-em-minusculas (ex.: computacao-e-tecnologia)',
    };
  }

  const pagina = lerPagina(pageBruto);
  if (!pagina.ok) {
    return { tipo: 'endereco-recusado', campo: 'page', valor: pagina.valor, esperado: pagina.esperado };
  }

  const [categorias, materiais] = await Promise.all([
    listarCategorias(),
    listarMateriais({ categoria: slug, page: pagina.pagina - 1 }),
  ]);

  if (!categorias.ok) return { tipo: 'falha', falha: categorias.falha };

  const categoria = categorias.dados.items.find((item) => item.slug === slug);
  if (categoria === undefined) return { tipo: 'sem-categoria' };

  if (!materiais.ok) return { tipo: 'falha', falha: materiais.falha };

  return {
    tipo: 'categoria',
    categoria,
    outras: categorias.dados.items.filter((item) => item.slug !== slug),
    materiais: materiais.dados,
  };
});

export async function generateMetadata({ params, searchParams }: Props): Promise<Metadata> {
  const { slug } = await params;
  const brutos = await searchParams;
  const leitura = await lerCategoria(slug, primeiro(brutos.page));

  if (leitura.tipo !== 'categoria') {
    // Sem categoria não há nome nem descrição verdadeiros para publicar. Endereço que hoje não
    // serve conteúdo público fica fora de índice e sem `canonical` (RNF024).
    return { title: 'Categoria', robots: { index: false, follow: false } };
  }

  const { categoria } = leitura;

  return {
    title: categoria.nome,
    // `description` = `descricao` do contrato — texto escrito para descoberta e SEO (§12),
    // nunca um resumo redigido aqui.
    description: categoria.descricao,
    // Do `slug` da resposta: estável, e independente de `page` ou de parâmetro extra na
    // barra de endereços (RNF025).
    alternates: { canonical: `/categoria/${categoria.slug}` },
  };
}

export default async function CategoriaPage({ params, searchParams }: Props) {
  const { slug } = await params;
  const brutos = await searchParams;
  const leitura = await lerCategoria(slug, primeiro(brutos.page));

  if (leitura.tipo === 'falha') {
    return (
      <EstadoDaFalha
        falha={leitura.falha}
        oQue="esta categoria"
        tituloNaoEncontrado="Não encontramos esta categoria"
        mensagemNaoEncontrado="Este endereço não corresponde a nenhuma categoria da plataforma. As categorias são uma lista de referência (um nível, sem subcategorias) e só as ativas têm página — o link pode estar incorreto, ou a categoria foi desativada."
        endereco={`/categoria/${slug}`}
      />
    );
  }

  if (leitura.tipo === 'endereco-recusado') {
    return (
      <div className="sc-page">
        <PageState
          kind="not-found"
          title="Este endereço não abre uma categoria"
          message="Parte do endereço está fora do formato que a plataforma aceita. Confira o que foi recusado abaixo e ajuste o link — ou vá para a busca."
          details={
            <ul className="sc-body list-disc space-y-1 pl-5">
              <li>
                <span className="sc-mono">{leitura.campo}</span>: {leitura.valor} — esperado:{' '}
                {leitura.esperado}
              </li>
            </ul>
          }
          action={
            <Link className="sc-btn sc-btn--ghost sc-btn--sm" href="/explorar">
              Ver todos os materiais publicados
            </Link>
          }
        />
      </div>
    );
  }

  if (leitura.tipo === 'sem-categoria') {
    return (
      <div className="sc-page">
        <PageState
          kind="not-found"
          title="Não encontramos esta categoria"
          message="Este endereço não corresponde a nenhuma categoria ativa da plataforma. A lista de categorias é de referência e só as ativas aparecem — confira abaixo as que existem hoje, ou veja todos os materiais publicados."
          details={
            <p className="sc-body">
              Categoria pedida: <span className="sc-mono">{slug}</span>
            </p>
          }
          action={
            <Link className="sc-btn sc-btn--ghost sc-btn--sm" href="/explorar">
              Ver todos os materiais publicados
            </Link>
          }
        />
      </div>
    );
  }

  const { categoria, outras, materiais } = leitura;

  return (
    <div className="sc-page">
      <div className="sc-sec__head">
        <p className="sc-sec__num">
          <i>Categoria</i> taxonomia da plataforma
        </p>
        <h1 className="sc-h1">{categoria.nome}</h1>
        <p className="sc-sec__lead">{categoria.descricao}</p>
        <p className="sc-helper">
          Endereço estável: <span className="sc-mono">/categoria/{categoria.slug}</span>
        </p>
      </div>

      <section aria-labelledby="materiais-da-categoria">
        <h2 className="sc-subhead" id="materiais-da-categoria">
          Materiais nesta categoria
        </h2>
        <ListagemDeMateriais
          pagina={materiais}
          urlDaPagina={(pagina) => urlComPagina(`/categoria/${categoria.slug}`, pagina)}
          rotulo={`Materiais da categoria ${categoria.nome}`}
          vazio={<VazioDaCategoria categoria={categoria} outras={outras} />}
        />
      </section>
    </div>
  );
}

/**
 * Estado `vazio` da categoria: a categoria existe e ainda não tem material publicado.
 *
 * As sugestões são as categorias que a PRÓPRIA resposta devolveu (o que existe hoje), não uma
 * lista fixa no código — e o link da busca filtrada continua ali para quem prefere filtrar em
 * `/explorar`. Nada de material de exemplo: acervo vazio é dito, não preenchido (§20.1).
 */
function VazioDaCategoria({
  categoria,
  outras,
}: {
  categoria: CategoriaDaPagina;
  outras: CategoriaDaPagina[];
}) {
  return (
    <PageState
      kind="empty"
      title="Ainda não há material publicado nesta categoria"
      message={`Nenhum material publicado em ${categoria.nome} até agora. A categoria existe na taxonomia da plataforma; o que falta é a primeira publicação nela — esta lista se enche sozinha quando acontecer.`}
      details={
        <div>
          <p className="sc-helper">Enquanto isso, você pode:</p>
          <ul className="sc-body list-disc space-y-1 pl-5">
            <li>
              <Link href="/explorar">ver todos os materiais publicados</Link>;
            </li>
            <li>
              {outras.length > 0 ? (
                <>
                  explorar outra categoria:{' '}
                  {outras.map((outra, indice) => (
                    <span key={outra.slug}>
                      {indice > 0 ? ' · ' : null}
                      <Link href={rotaCategoria(outra.slug)}>{outra.nome}</Link>
                    </span>
                  ))}
                </>
              ) : (
                <>
                  <Link href={rotaExplorar(`/explorar?categoria=${categoria.slug}`)}>
                    filtrar a listagem por esta categoria
                  </Link>
                  , que hoje também não tem resultado.
                </>
              )}
            </li>
          </ul>
        </div>
      }
      action={
        <Link className="sc-btn sc-btn--primary sc-btn--sm" href="/explorar">
          Explorar os materiais publicados
        </Link>
      }
    />
  );
}
