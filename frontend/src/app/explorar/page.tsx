import type { Metadata } from 'next';
import Link from 'next/link';
import type { ReactNode } from 'react';
import { BuscaEFiltros } from './BuscaEFiltros';
import { ListagemDeMateriais } from '@/components/dominio/ListagemDeMateriais';
import { PageState } from '@/components/ui/PageState';
import { Recarregar } from '@/components/ui/Recarregar';
import { listarCategorias, listarMateriais, listarTiposMaterial, type Falha } from '@/lib/api/client';
import {
  ORDENS,
  ORDEM_PADRAO,
  consultaDaApi,
  lerParametros,
  temFiltrosAtivos,
  urlDaPagina as urlDaPaginaDe,
  urlSemFiltro,
  type ParametroInvalido,
  type ParametrosExplorar,
} from '@/lib/explorar/consulta';
import { rotaExplorar } from '@/lib/rotas';
import type { ItemTaxonomia } from '@/lib/api/tipos';

/**
 * `/explorar` — a tela onde o visitante encontra material (RF002, RF003, RF004).
 *
 * Renderizada no SERVIDOR, com a URL como única fonte da verdade: os parâmetros
 * (`q`, `categoria`, `tipo`, `ordem`, `page`) são lidos, validados e viram a consulta
 * da API. Consequências deliberadas:
 *
 *   - colar a URL com filtros reproduz o mesmo resultado, e o HTML entregue já contém a
 *     listagem (sem depender de JavaScript — RNF023–RNF025);
 *   - duas URLs diferentes descrevendo a mesma lista não existem: a forma canônica omite
 *     o que é padrão (`lib/explorar/consulta.ts`);
 *   - `canonical` é sempre `/explorar`: a listagem filtrada é uma VISTA do mesmo acervo,
 *     não uma página própria (o contrato pede `canonical` estável para esta rota).
 *
 * Sobre os sete estados (DRS §15.3): `sucesso`, `vazio` (as duas variantes: nada publicado e
 * nada que corresponda à busca) e `não encontrado` (endereço que não forma listagem) são
 * renderizados aqui; `erro de rede/servidor` traz a ação de tentar de novo. `carregando`
 * NÃO tem tela intermediária nesta rota — medido, não esquecido: o `loading.tsx` do App
 * Router entrega o HTML com a tela de carregamento visível e a listagem de verdade dentro
 * de um bloco `hidden` que só o JavaScript revela, de modo que quem não executa JS veria
 * "Carregando o conteúdo" para sempre. O contrato pede o contrário para esta rota (RNF023,
 * DRS §12.3: conteúdo real no HTML). A indicação de busca em andamento vive no
 * `role="status"` do controle de busca. `não autorizado` (403) e `sessão expirada` (401)
 * não podem ocorrer numa rota pública — o contrato diz que os dois pertencem a operações
 * com identidade, que começam na Etapa 3 —, mas o mapeamento existe: se a API responder
 * 401/403, a tela mostra o estado certo em vez de "falha de conexão" mentirosa.
 *
 * Nenhum dado fictício: sem acervo, a tela DIZ que não há material publicado.
 */

export const metadata: Metadata = {
  title: 'Explorar materiais',
  description:
    'Lista pública de materiais de estudo e divulgação científica: busque por título ou descrição, filtre por categoria e tipo de material, com autoria e licença visíveis em cada resultado.',
  alternates: { canonical: '/explorar' },
};

type Props = {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
};

export default async function ExplorarPage({ searchParams }: Props) {
  const brutos = await searchParams;
  const leitura = lerParametros(brutos);

  if (!leitura.ok) {
    return (
      <Moldura>
        <FiltroRecusado invalidos={leitura.invalidos} />
      </Moldura>
    );
  }

  const params = leitura.params;

  // Uma ida ao servidor para a listagem e a taxonomia do filtro (RF004, RF018): a tela é
  // o conjunto listagem + filtros, então as três respostas são aguardadas juntas.
  const [materiais, categorias, tipos] = await Promise.all([
    listarMateriais(consultaDaApi(params)),
    listarCategorias(),
    listarTiposMaterial(),
  ]);

  if (!materiais.ok) {
    return (
      <Moldura>
        <EstadoDaFalha falha={materiais.falha} oQue="a listagem de materiais" />
      </Moldura>
    );
  }
  if (!categorias.ok) {
    return (
      <Moldura>
        <EstadoDaFalha falha={categorias.falha} oQue="as categorias do filtro" />
      </Moldura>
    );
  }
  if (!tipos.ok) {
    return (
      <Moldura>
        <EstadoDaFalha falha={tipos.falha} oQue="os tipos de material do filtro" />
      </Moldura>
    );
  }

  return (
    <Moldura>
      {/* Quem marca o estado no HTML é a `ListagemDeMateriais`: `data-state="success"`
          no bloco da lista quando ela sai, `data-state="empty"` quando não há resultado.
          Envolver tudo em `kind="success"` faria a página vazia sair com DOIS
          marcadores (success por fora, empty por dentro) e o `grep` de QA não teria
          resposta única (DRS §15.3). */}
      <h2 className="sc-subhead">Buscar e filtrar</h2>

      <BuscaEFiltros
        categorias={categorias.dados.items}
        tipos={tipos.dados.items}
        params={{ q: params.q, categoria: params.categoria, tipo: params.tipo, ordem: params.ordem }}
      />

      <FiltrosAplicados
        params={params}
        categorias={categorias.dados.items}
        tipos={tipos.dados.items}
      />

      <ListagemDeMateriais
        pagina={materiais.dados}
        urlDaPagina={(pagina) => urlDaPaginaDe(params, pagina)}
        rotulo="Materiais encontrados"
        vazio={<VazioDaBusca params={params} categorias={categorias.dados.items} />}
      />
    </Moldura>
  );
}

/** Casca da página: cabeçalho com a identidade da tela, igual em todos os estados. */
function Moldura({ children }: { children: ReactNode }) {
  return (
    <div className="sc-page">
      <div className="sc-sec__head">
        <p className="sc-sec__num">
          <i>Descoberta</i> materiais públicos
        </p>
        <h1 className="sc-h1">Explorar materiais</h1>
        <p className="sc-sec__lead">
          Materiais publicados por estudantes, professores e pesquisadores. Busque por título ou
          descrição, filtre por categoria e tipo, e ordene o resultado — cada item mostra autoria e
          licença, e a ordem é a do contrato (publicação mais recente, ou título).
        </p>
      </div>
      {children}
    </div>
  );
}

/**
 * Estado para cada resposta que não veio. O 400 do contrato é erro de PARÂMETRO, não de
 * conexão: mostrar "falha de conexão" nesse caso mandaria a pessoa conferir a internet
 * por um defeito no endereço. Os demais status mantêm o estado próprio do DRS §15.3.
 */
function EstadoDaFalha({ falha, oQue }: { falha: Falha; oQue: string }) {
  if (falha.motivo === 'http') {
    if (falha.status === 400) {
      const invalidos: ParametroInvalido[] = (falha.problem?.errors ?? []).map((erro) => ({
        campo: erro.campo,
        valor: 'recusado pela API',
        esperado: erro.mensagem,
      }));
      return <FiltroRecusado invalidos={invalidos} />;
    }
    if (falha.status === 401) return <PageState kind="session-expired" />;
    if (falha.status === 403) return <PageState kind="unauthorized" />;
    if (falha.status === 404) return <PageState kind="not-found" />;
  }

  return (
    <PageState
      kind="network-error"
      title="Não foi possível carregar os materiais"
      message={`Não deu para carregar ${oQue}: a resposta não chegou ao servidor ou o servidor falhou ao responder. A lista não foi exibida justamente para não mostrar um resultado que não é o real. Tente de novo — se continuar, tente outra vez em alguns minutos.`}
      action={<Recarregar />}
    />
  );
}

/**
 * Endereço que não forma listagem (parâmetro fora do formato do contrato).
 *
 * Escolha registrada: o estado `não encontrado` é o que significa "este endereço não
 * corresponde a nada" no DRS §15.3 — e é isso que aconteceu. Recusar em vez de ignorar o
 * filtro inválido é a mesma razão do teto de `q` no ADR-0008: descartar em silêncio
 * mostraria um resultado diferente do que o endereço pediu sem a pessoa saber.
 */
function FiltroRecusado({ invalidos }: { invalidos: ParametroInvalido[] }) {
  return (
    <PageState
      kind="not-found"
      title="Este endereço não forma uma listagem"
      message="Um filtro da URL está fora do formato que a busca aceita. Confira o que foi recusado abaixo, ajuste o endereço ou limpe os filtros para ver todos os materiais publicados."
      details={
        <ul className="sc-body list-disc space-y-1 pl-5">
          {invalidos.map((invalido) => (
            <li key={invalido.campo}>
              <span className="sc-mono">{invalido.campo}</span>: {invalido.valor} — esperado:{' '}
              {invalido.esperado}
            </li>
          ))}
        </ul>
      }
      action={
        <Link className="sc-btn sc-btn--ghost sc-btn--sm" href={rotaExplorar('/explorar')}>
          Ver todos os materiais
        </Link>
      }
    />
  );
}

/**
 * Filtros aplicados: visíveis, removíveis UM A UM, com "limpar tudo".
 *
 * Cada chip é um LINK para a mesma listagem sem aquele filtro — então remover filtro
 * funciona sem JavaScript, entra na ordem de tabulação e a URL resultante continua
 * compartilhável.
 */
function FiltrosAplicados({
  params,
  categorias,
  tipos,
}: {
  params: ParametrosExplorar;
  categorias: ItemTaxonomia[];
  tipos: ItemTaxonomia[];
}) {
  const chips: { campo: 'q' | 'categoria' | 'tipo' | 'ordem'; rotulo: string; texto: string }[] = [];

  if (params.q !== '') chips.push({ campo: 'q', rotulo: 'Busca', texto: params.q });
  if (params.categoria !== '') {
    chips.push({
      campo: 'categoria',
      rotulo: 'Categoria',
      texto: categorias.find((item) => item.slug === params.categoria)?.nome ?? params.categoria,
    });
  }
  if (params.tipo !== '') {
    chips.push({
      campo: 'tipo',
      rotulo: 'Tipo',
      texto: tipos.find((item) => item.slug === params.tipo)?.nome ?? params.tipo,
    });
  }
  if (params.ordem !== ORDEM_PADRAO) {
    chips.push({
      campo: 'ordem',
      rotulo: 'Ordem',
      texto: ORDENS.find((ordem) => ordem.valor === params.ordem)?.rotulo ?? params.ordem,
    });
  }

  if (chips.length === 0) return null;

  return (
    <div className="sc-filtros-ativos">
      <span className="sc-helper">Filtros aplicados:</span>

      {chips.map((chip) => (
        <Link
          key={chip.campo}
          className="sc-filtro-ativo"
          href={rotaExplorar(urlSemFiltro(params, chip.campo))}
        >
          {chip.rotulo}: {chip.texto}
          <span className="sc-filtro-ativo__x" aria-hidden="true">
            ×
          </span>
          <span className="sc-visually-hidden"> — remover este filtro</span>
        </Link>
      ))}

      <Link className="sc-filtro-ativo" href={rotaExplorar('/explorar')}>
        Limpar tudo
        <span className="sc-filtro-ativo__x" aria-hidden="true">
          ×
        </span>
        <span className="sc-visually-hidden"> — remover todos os filtros</span>
      </Link>
    </div>
  );
}

/**
 * Estado `vazio` — nunca tela branca (DRS §15.3, PAGE_BLUEPRINTS).
 *
 * Dois casos, com mensagens diferentes porque são situações diferentes:
 *
 *   - COM filtro: nada corresponde à busca. As sugestões são reais — "limpar os filtros"
 *     e as categorias que a taxonomia devolveu (não uma lista fixa no código);
 *   - SEM filtro: o acervo público está vazio. Dizer "limpe os filtros" aí seria mentira,
 *     e inventar material para preencher a tela é o risco §20.1.
 */
function VazioDaBusca({
  params,
  categorias,
}: {
  params: ParametrosExplorar;
  categorias: ItemTaxonomia[];
}) {
  const comFiltro = temFiltrosAtivos(params);

  if (!comFiltro) {
    return (
      <PageState
        kind="empty"
        title="Ainda não há material publicado"
        message="O acervo público está vazio neste momento: nenhum material foi publicado ainda. Assim que houver publicação, ela aparece nesta lista."
        action={
          <Link className="sc-btn sc-btn--ghost sc-btn--sm" href="/">
            Voltar ao início
          </Link>
        }
      />
    );
  }

  return (
    <PageState
      kind="empty"
      details={
        <div>
          <p className="sc-helper">Em vez de refazer a busca, você pode:</p>
          <ul className="sc-body list-disc space-y-1 pl-5">
            <li>
              <Link href={rotaExplorar('/explorar')}>limpar a busca e os filtros</Link> e ver todos
              os materiais publicados;
            </li>
            <li>
              {categorias.length > 0 ? (
                <>
                  explorar por categoria:{' '}
                  {categorias.map((categoria, indice) => (
                    <span key={categoria.slug}>
                      {indice > 0 ? ' · ' : null}
                      <Link href={rotaExplorar(`/explorar?categoria=${categoria.slug}`)}>
                        {categoria.nome}
                      </Link>
                    </span>
                  ))}
                </>
              ) : (
                <>explorar outras categorias quando a lista de categorias estiver disponível;</>
              )}
            </li>
          </ul>
        </div>
      }
      action={
        <Link className="sc-btn sc-btn--ghost sc-btn--sm" href={rotaExplorar('/explorar')}>
          Limpar busca e filtros
        </Link>
      }
    />
  );
}
