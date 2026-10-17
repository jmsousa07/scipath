import type { Metadata } from 'next';
import Link from 'next/link';
import type { Route } from 'next';
import { SearchForm } from '@/components/SearchForm';
import { CardMaterial } from '@/components/dominio/CardMaterial';
import { PageState, entrarHref } from '@/components/ui/PageState';
import { Recarregar } from '@/components/ui/Recarregar';
import { listarCategorias, listarMateriais, type Falha, type Resultado } from '@/lib/api/client';
import { rotaExplorar } from '@/lib/rotas';
import type { ItemTaxonomia, MaterialResumo, Pagina } from '@/lib/api/tipos';

/**
 * `/` — página inicial (RF001): explicar o produto em segundos e dar o caminho para
 * explorar ou publicar (PAGE_BLUEPRINTS.md §1, DRS §15).
 *
 * Renderizada no SERVIDOR, com o conteúdo no HTML entregue (RNF023–RNF025, DRS §12.3):
 * proposta de valor, busca em destaque, categorias e os materiais recentes saem no
 * documento, sem depender de JavaScript.
 *
 * O que esta página NÃO faz, de propósito (DRS §12.4):
 *   - não inventa destaque, ranking, contagem de acessos ou "popularidade". A única
 *     ordem que existe é a do contrato (`GET /api/materiais` sem `ordem` = publicação
 *     mais recente, desempate estável por id) e o único número exibido é o `total` que
 *     a própria API devolveu;
 *   - não simula acervo (§20.1): com o acervo vazio ela DIZ que está vazio e convida a
 *     publicar — é o estado real do produto enquanto não houver publicação.
 *
 * Os sete estados de página do DRS §15.3, um por um:
 *   - `sucesso`  o bloco de recentes sai marcado com `data-state="success"`;
 *   - `vazio`    nenhum material publicado → `data-state="empty"` com convite a
 *                publicar, mantendo a explicação do produto na tela (a página não
 *                pode parecer quebrada);
 *   - `erro de rede/servidor`  a API não respondeu ou falhou → `data-state="network-error"`
 *                com a ação de tentar de novo (`Recarregar`, `router.refresh()`), e o
 *                resto da página (proposta de valor, busca, convite) continua de pé;
 *   - `não encontrado` / `não autorizado` / `sessão expirada`  mapeados a partir de
 *                404/403/401 da API, como em `/explorar` (RNF003: estado equivalente,
 *                comportamento equivalente). Numa rota pública sem identidade eles não
 *                deveriam ocorrer; o mapeamento existe para não mentir "falha de
 *                conexão" se ocorrerem;
 *   - `carregando`  NÃO tem tela intermediária nesta rota, pela mesma razão medida no
 *                card E2.8: o `loading.tsx` do App Router entrega o HTML com o aviso de
 *                carregamento visível e o conteúdo de verdade dentro de um bloco
 *                `hidden` que só o JavaScript revela — quem não executa JS veria
 *                "Carregando o conteúdo" para sempre, contra RNF023/§12.3.
 *
 * Diferença registrada em relação a `/explorar`: lá um 400 vira `não encontrado`, porque
 * o 400 vem de um parâmetro que a PESSOA escreveu na URL; aqui a requisição não tem
 * parâmetro de visitante (só o `size` que este código escolhe), então 400 e 5xx caem em
 * `erro de rede/servidor` — é falha do servidor, e dizer "endereço não encontrado" seria
 * mentira sobre a causa.
 *
 * Pendência registrada no card: o convite a publicar aponta para
 * `/entrar?proximo=/painel/publicar`, e a rota `/entrar` chega com a área do usuário
 * (Etapa 3). Até lá o clique leva a 404 — consequência da dependência entre etapas, não
 * um defeito desta página (mesmo precedente do card E1.6 com `/explorar`).
 */

export const metadata: Metadata = {
  // `absolute` porque a página inicial está no MESMO segmento de rota do layout raiz, e o
  // `title.template` do layout só se aplica a segmentos filhos — medido no HTML entregue
  // (`/termos` sai "Termos de uso · SciPath"; `/` saía sem o sufixo). Declarar o título
  // inteiro aqui evita depender de herança que não acontece nesta rota (RNF024).
  title: { absolute: 'SciPath — descobrir materiais científicos' },
  description:
    'Plataforma pública onde estudantes, professores e pesquisadores publicam notas de aula, slides, artigos e guias de estudo — encontrados por busca e filtros explícitos, com autoria e licença visíveis em cada material.',
  alternates: { canonical: '/' },
};

/** Quantos materiais recentes a página inicial mostra. O resto vive em `/explorar`. */
const RECENTES_NA_HOME = 6;

/**
 * A rota `/entrar` pertence à Etapa 3 e ainda não existe no projeto; o `typedRoutes` do
 * `next.config.ts` só compila `href` de rota existente, então o destino montado por
 * `entrarHref()` (que já preserva o caminho pedido) passa por cast — o mesmo mecanismo
 * que `PageState` usa para o próprio botão de sessão expirada.
 */
const rotaDeDestino = (url: string) => url as Route;

export default async function HomePage() {
  // Uma ida ao servidor para as duas listas que a página mostra (RF001). `Promise.all`
  // porque são independentes: a página não deve esperar uma para começar a outra.
  const [materiais, categorias] = await Promise.all([
    listarMateriais({ size: RECENTES_NA_HOME }),
    listarCategorias(),
  ]);

  return (
    <div className="sc-page">
      <PropostaDeValor />
      <BuscaEmDestaque />
      <AreaDeDados materiais={materiais} categorias={categorias} />
      <ConviteAPublicar />
    </div>
  );
}

/** Proposta de valor: material · contexto · pessoa (PAGE_BLUEPRINTS.md §1). */
function PropostaDeValor() {
  return (
    <div className="sc-sec__head">
      <p className="sc-sec__num">
        <i>SciPath</i> plataforma pública de divulgação científica
      </p>
      <h1 className="sc-h1">Descobrir, compreender e compartilhar conhecimento científico</h1>
      <p className="sc-sec__lead">
        O SciPath reúne <strong>material</strong> — notas de aula, slides, artigos e guias de
        estudo —, o <strong>contexto</strong> de cada um (tipo, categoria e a licença explicada em
        linguagem simples) e a <strong>pessoa</strong> que publicou. A busca é por texto e por
        filtros explícitos, e a ordem da lista é sempre dita: publicação mais recente.
      </p>
    </div>
  );
}

/**
 * Busca em destaque (RF001): o mesmo formulário do cabeçalho, com `id` próprio para não
 * duplicar o alvo do rótulo na mesma página. Envia para `/explorar?q=`, que é a URL
 * compartilhável da busca.
 */
function BuscaEmDestaque() {
  return (
    <section className="sc-card sc-card--pad mt-6" aria-labelledby="busca-destaque-titulo">
      <h2 className="sc-subhead mt-0" id="busca-destaque-titulo">
        Buscar material
      </h2>
      <p className="sc-body sc-measure">
        Procure por título ou descrição. O resultado abre na listagem pública, onde dá para filtrar
        por categoria e por tipo de material.
      </p>
      <div className="mt-4">
        <SearchForm id="busca-destaque" />
      </div>
      <p className="sc-helper mt-3">
        Cada resultado mostra autoria e licença. <Link href="/explorar">Ver todos os materiais</Link>
        .
      </p>
    </section>
  );
}

/**
 * Área que depende da API. As duas listas vêm na mesma resposta ao visitante e o estado é
 * UM só: se qualquer uma falhar, a página mostra um único bloco de falha no lugar das
 * duas — dois marcadores de estado na mesma página deixariam a pergunta "de que estado é
 * esta página?" sem resposta única (DRS §15.3, medido no card E2.8).
 */
function AreaDeDados({
  materiais,
  categorias,
}: {
  materiais: Resultado<Pagina<MaterialResumo>>;
  categorias: Resultado<Pagina<ItemTaxonomia>>;
}) {
  if (!materiais.ok) return <FalhaDaHome falha={materiais.falha} oQue="os materiais recentes" />;
  if (!categorias.ok) return <FalhaDaHome falha={categorias.falha} oQue="as categorias" />;

  return (
    <>
      <Recentes pagina={materiais.dados} />
      <Categorias itens={categorias.dados.items} />
    </>
  );
}

/**
 * Estado `erro de rede/servidor` (e os três estados que o contrato distingue por status).
 * A mensagem diz o que aconteceu e o que fazer (RNF004) e o resto da página continua
 * visível: a falha é do acervo, não do produto.
 */
function FalhaDaHome({ falha, oQue }: { falha: Falha; oQue: string }) {
  if (falha.motivo === 'http') {
    if (falha.status === 401) return <PageState kind="session-expired" />;
    if (falha.status === 403) return <PageState kind="unauthorized" />;
    if (falha.status === 404) return <PageState kind="not-found" />;
  }

  return (
    <PageState
      kind="network-error"
      title="Não foi possível carregar os materiais"
      message={`Não deu para carregar ${oQue}: a resposta não chegou ao servidor ou o servidor falhou ao responder. Nada foi exibido no lugar do acervo justamente para não mostrar um resultado que não é o real. Tente de novo — se continuar, tente outra vez em alguns minutos.`}
      action={<Recarregar />}
    />
  );
}

/**
 * Materiais recentes. A ordem é a padrão do contrato (publicação mais recente, com
 * desempate estável por id) e está escrita na tela, porque ordem sem regra explícita é
 * "destaque" inventado (DRS §12.2, §12.4).
 */
function Recentes({ pagina }: { pagina: Pagina<MaterialResumo> }) {
  const { items, total } = pagina;

  if (items.length === 0) {
    return (
      <section aria-labelledby="recentes-titulo">
        <h2 className="sc-subhead" id="recentes-titulo">
          Materiais recentes
        </h2>
        <PageState
          kind="empty"
          title="Ainda não há material publicado"
          message="O acervo público está vazio: nenhum material foi publicado até agora. Esta lista se enche sozinha conforme as publicações acontecem — e a ordem continua sendo a da publicação mais recente."
          details={
            <p className="sc-body">
              O produto já está no ar: dá para buscar na listagem pública, e quem publica são
              estudantes, professores e pesquisadores com conta de e-mail verificado.
            </p>
          }
          action={
            <>
              <Link
                className="sc-btn sc-btn--primary sc-btn--sm"
                href={rotaDeDestino(entrarHref('/painel/publicar'))}
              >
                Publicar um material
              </Link>
              <Link className="sc-btn sc-btn--ghost sc-btn--sm" href="/explorar">
                Ver a listagem pública
              </Link>
            </>
          }
        />
      </section>
    );
  }

  return (
    <section aria-labelledby="recentes-titulo" data-state="success">
      <h2 className="sc-subhead" id="recentes-titulo">
        Materiais recentes
      </h2>
      <p className="sc-helper">
        {contagem(items.length, total)} · ordem do contrato: publicação mais recente, com desempate
        estável.
      </p>

      <ul className="sc-listagem">
        {items.map((material) => (
          <li key={material.slug}>
            <CardMaterial material={material} />
          </li>
        ))}
      </ul>

      <p className="mt-4">
        <Link className="sc-btn sc-btn--ghost sc-btn--sm" href="/explorar">
          Ver todos os materiais publicados
        </Link>
      </p>
    </section>
  );
}

/**
 * Texto da contagem — sempre a partir do envelope da API (`total` = quantos materiais
 * atendem à consulta, `items.length` = quantos couberam neste recorte). Nenhum número
 * calculado "por fora" do que o servidor devolveu.
 */
function contagem(mostrados: number, total: number): string {
  if (total === 1) return '1 material publicado';
  if (total <= mostrados) return `${total} materiais publicados`;
  return `Mostrando os ${mostrados} mais recentes de ${total} materiais publicados`;
}

/**
 * Categorias da taxonomia (RF001). Cada categoria é um LINK para a listagem filtrada —
 * funciona por teclado e sem JavaScript, e a URL resultante é compartilhável. O chip usa
 * `.sc-filtro-ativo` (criado no card E2.8): é um link com cara de chip, o mesmo alvo de
 * toque de 2.75rem e nenhuma classe nova por tela (DESIGN_SYSTEM.md §0.2).
 */
function Categorias({ itens }: { itens: ItemTaxonomia[] }) {
  return (
    <section aria-labelledby="categorias-titulo">
      <h2 className="sc-subhead" id="categorias-titulo">
        Explorar por categoria
      </h2>

      {itens.length === 0 ? (
        <p className="sc-body sc-measure">
          A lista de categorias não está disponível agora. Enquanto ela não volta, a busca e a
          listagem pública continuam funcionando.
        </p>
      ) : (
        <>
          <p className="sc-helper">Escolha uma categoria para ver só os materiais dela.</p>
          <ul className="m-0 flex list-none flex-wrap gap-2 p-0">
            {itens.map((categoria) => (
              <li key={categoria.slug}>
                <Link
                  className="sc-filtro-ativo"
                  href={rotaExplorar(`/explorar?categoria=${categoria.slug}`)}
                >
                  {categoria.nome}
                </Link>
              </li>
            ))}
          </ul>
        </>
      )}
    </section>
  );
}

/**
 * Convite a publicar — sempre visível, e é o que impede o acervo vazio de parecer página
 * quebrada (PAGE_BLUEPRINTS.md §1, "estado especial"). Diz quem publica e o que a
 * publicação exige hoje (conta com e-mail verificado, ADR-0003), sem prometer o que ainda
 * não existe.
 */
function ConviteAPublicar() {
  return (
    <section className="sc-card sc-card--pad mt-8" aria-labelledby="publicar-titulo">
      <h2 className="sc-subhead mt-0" id="publicar-titulo">
        Publicar um material
      </h2>
      <p className="sc-body sc-measure">
        Quem publica aqui são estudantes, professores e pesquisadores. Cada material publicado mostra
        autoria, categoria, tipo e a licença escolhida pelo autor — em linguagem simples, para
        ninguém confundir &ldquo;está público&rdquo; com &ldquo;qualquer um pode reutilizar&rdquo;.
      </p>
      <p className="sc-body sc-measure mt-3">
        Publicar exige conta com e-mail verificado. A entrada e o cadastro chegam junto com a área do
        usuário; o endereço pedido fica preservado para você voltar a ele depois de entrar.
      </p>
      <p className="mt-4 flex flex-wrap items-center gap-3">
        <Link
          className="sc-btn sc-btn--primary sc-btn--sm"
          href={rotaDeDestino(entrarHref('/painel/publicar'))}
        >
          Publicar material
        </Link>
        <Link className="sc-btn sc-btn--ghost sc-btn--sm" href="/explorar">
          Explorar o que já está publicado
        </Link>
      </p>
    </section>
  );
}
