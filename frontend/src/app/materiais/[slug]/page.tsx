import type { Metadata } from 'next';
import Link from 'next/link';
import { cache } from 'react';
import { PageState } from '@/components/ui/PageState';
import { Recarregar } from '@/components/ui/Recarregar';
import { obterMaterial, type Falha } from '@/lib/api/client';
import type { MaterialDetalhe } from '@/lib/api/tipos';
import { dataCurta } from '@/lib/format';
import { rotaAutor, rotaExplorar } from '@/lib/rotas';

/**
 * `/materiais/[slug]` — página pública de um material (RF005, PAGE_BLUEPRINTS.md §3).
 *
 * Renderizada no SERVIDOR: os metadados, a autoria, a licença e as datas saem no HTML
 * entregue, sem depender de JavaScript (RNF023, §12.3). O que a página mostra é o que o
 * contrato publica em `MaterialDetalhe` — nem mais (nada de campo inventado) nem menos
 * (título, descrição, tipo, categoria, autor com link, datas, licença explicada).
 *
 * O que NÃO existe nesta página, de propósito:
 *
 *   - **marca de revisão, certificação, curadoria ou verificação** (RN010, RN011,
 *     CA012). O dado não tem isso, então a tela também não tem: a página DIZ, em uma
 *     linha, que o conteúdo é o que quem publicou declarou (§13.1);
 *   - **botão de acesso ao arquivo**. O contrato v0.3.0 não serve arquivo — o
 *     `MaterialDetalhe` não tem campo de arquivo (ver o comentário no schema e em
 *     `lib/api/tipos.ts`). A página, então, informa que não há arquivo disponível, em
 *     vez de exibir um botão que não leva a nada. Quando o RF014 entrar, o campo nasce
 *     no contrato primeiro e a ação aparece aqui, condicionada à existência dele;
 *   - **dado estruturado** (JSON-LD). Só entraria se fosse correto por inteiro (§12.3);
 *     hoje não é decisão deste card.
 *
 * Material fora do público — inexistente, RETIRADO ou EXCLUIDO — e slug fora do formato
 * caem no estado `não encontrado` do DRS §15.3, com o MESMO texto para os quatro casos:
 * a API responde 404 indistinguível para material retirado, excluído e inexistente
 * (RN002, CA008), e a página que os diferenciasse desfaria essa decisão e revelaria o
 * que saiu do ar.
 *
 * Duas medições deste card explicam COMO o estado chega:
 *
 *   1. `notFound()` **não** serve aqui. Medido com `curl`: numa rota dinâmica, o App
 *      Router entrega a resposta 404 com o corpo do estado apenas dentro do pacote de
 *      JavaScript — o HTML traz a casca e o `<main>` vazio. Quem não executa JS (e o
 *      rastreador que só lê HTML, RNF023/§12.3) veria uma página em branco. O mesmo vale
 *      chamando `notFound()` de `generateMetadata`, antes de o corpo começar a sair;
 *   2. por isso o estado é renderizado pela PRÓPRIA página, no HTML entregue, e o
 *      `generateMetadata` marca o endereço como `noindex, nofollow` (RNF024): um material
 *      que saiu do ar não pode virar resultado de busca. Rota que não casa com endereço
 *      nenhum continua tendo o 404 de verdade, com a página de `not-found.tsx` da raiz
 *      (medido: sai renderizada, com o estado no HTML).
 *
 * Troca registrada: assim, a página de um material que não é público responde **200**
 * (com o estado e sem canonical), e quem responde 404 é a API. Fazer a PÁGINA responder
 * 404 exigiria saber a resposta antes de o corpo começar a sair (middleware ou route
 * handler) ou uma opção de Next que bufferiza a resposta para rastreadores — decisão com
 * efeito em SEO e operação, que não cabe neste card e fica anotada para um card próprio.
 *
 * Os sete estados do DRS §15.3 nesta rota: `sucesso` (conteúdo), `não encontrado`
 * (retirado, inexistente ou slug malformado), `erro de rede/servidor` (com "tentar de
 * novo") e `não autorizado` / `sessão expirada` mapeados caso a API responda 401/403 —
 * não deveriam ocorrer numa rota pública, mas mostrar "falha de conexão" nesse caso
 * seria mentira sobre a causa. `vazio` e `carregando` não têm estado próprio aqui: um
 * material é um registro (ou existe, ou não existe — não há lista vazia), e `loading`
 * não tem tela intermediária pela mesma razão medida nos cards E2.7/E2.8 — com
 * `loading.tsx` o conteúdo real sai dentro de um bloco `hidden` que só o JavaScript
 * revela, e quem não executa JS veria "Carregando" para sempre (RNF023, §12.3).
 *
 * Pendência registrada: o link de autoria aponta para `/u/[slug]`, rota do card E2.10.
 * Até ela existir, o clique leva a 404 — mesmo precedente do card E1.6 com `/explorar`
 * e do E2.7 com `/entrar`. O link de categoria já leva a endereço que existe
 * (`/explorar?categoria=`), que é a navegação material → categoria do RF007.
 */

type Props = { params: Promise<{ slug: string }> };

/**
 * Uma leitura por render: `generateMetadata` e a página precisam do MESMO material.
 * `cache` do React memoiza por requisição, então o `curl` de uma visita gera UMA
 * requisição à API — e não duas (a evidência conta as linhas no log do fixture).
 */
const materialDoSlug = cache((slug: string) => obterMaterial(slug));

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { slug } = await params;
  const resultado = await materialDoSlug(slug);

  if (!resultado.ok) {
    // Sem material não há título nem descrição verdadeiros para publicar, e o endereço
    // fica fora de índice em TODOS os casos de falha: um endereço que hoje não serve
    // material público (retirado, inexistente ou falha de servidor) não deve virar
    // resultado de busca, e sem `canonical` a página não declara uma URL para algo que
    // não está lá (RNF024).
    return {
      title: 'Material',
      robots: { index: false, follow: false },
    };
  }

  const material = resultado.dados;

  return {
    // `title` = título do material (o `template` do layout raiz acrescenta " · SciPath")
    // e `description` = descrição — os dois campos que o contrato garante para isso
    // (RNF024). O `canonical` sai do `slug` DA RESPOSTA, e não do que veio na URL:
    // assim ele é estável e não depende de estado do navegador nem de parâmetro extra
    // na barra de endereços (RNF025).
    title: material.titulo,
    description: material.descricao,
    alternates: { canonical: `/materiais/${material.slug}` },
  };
}

export default async function MaterialPage({ params }: Props) {
  const { slug } = await params;
  const resultado = await materialDoSlug(slug);

  if (!resultado.ok) return <EstadoDaFalha falha={resultado.falha} slug={slug} />;

  return (
    <PageState kind="success">
      <article className="sc-page">
        <div className="sc-sec__head">
          <p className="sc-sec__num">
            <i>Material</i> {resultado.dados.tipo.nome}
          </p>
          <h1 className="sc-h1">{resultado.dados.titulo}</h1>
          <p className="sc-sec__lead">{resultado.dados.descricao}</p>
        </div>

        <Ficha material={resultado.dados} />
        <Licenca material={resultado.dados} />
        <Arquivo />
        <Navegacao material={resultado.dados} />
      </article>
    </PageState>
  );
}

/**
 * Ficha do material: os metadados obrigatórios do RF005, cada um com o rótulo à vista.
 *
 * `<dl>` e não tabela: são pares nome/valor de UM registro — `dt`/`dd` é o que descreve
 * a relação para quem usa leitor de tela ("Tipo de material: Nota de aula"), enquanto
 * uma tabela de uma linha só serviria para dados tabulares.
 *
 * O endereço estável aparece escrito (o `slug` é o identificador público do recurso,
 * RN009/RNF025): é a URL que a pessoa pode copiar, e é a mesma que o `canonical` declara.
 */
function Ficha({ material }: { material: MaterialDetalhe }) {
  const { tipo, categoria, autor, publicadoEm, atualizadoEm, slug } = material;

  return (
    <>
      <dl className="mt-8 grid gap-5 sm:grid-cols-2">
        <div>
          <dt className="sc-caption">Tipo de material</dt>
          <dd className="sc-body m-0">{tipo.nome}</dd>
        </div>

        <div>
          <dt className="sc-caption">Categoria</dt>
          <dd className="sc-body m-0">
            <Link href={rotaExplorar(`/explorar?categoria=${categoria.slug}`)}>{categoria.nome}</Link>
          </dd>
        </div>

        <div>
          <dt className="sc-caption">Autoria</dt>
          <dd className="sc-body m-0">
            <Link href={rotaAutor(autor.slug)}>{autor.nomePublico}</Link>
          </dd>
        </div>

        <div>
          <dt className="sc-caption">Publicado em</dt>
          <dd className="sc-body m-0">
            <time dateTime={publicadoEm}>{dataCurta(publicadoEm)}</time>
          </dd>
        </div>

        <div>
          <dt className="sc-caption">Última atualização</dt>
          <dd className="sc-body m-0">
            <time dateTime={atualizadoEm}>{dataCurta(atualizadoEm)}</time>
          </dd>
        </div>

        <div>
          <dt className="sc-caption">Endereço estável</dt>
          <dd className="sc-body m-0">
            <span className="sc-mono">/materiais/{slug}</span>
          </dd>
        </div>
      </dl>

      <p className="sc-helper sc-measure mt-6">
        Tudo nesta página é o que quem publicou declarou — título, descrição, categoria, tipo e
        licença. A plataforma não atesta o conteúdo e não endossa o material: a autoria e a licença
        são informação do autor, e é assim que elas aparecem aqui.
      </p>
    </>
  );
}

/**
 * Licença explicada (RN006, §13.2).
 *
 * `descricaoCurta` vem do contrato e é o texto da própria licença em linguagem simples —
 * é ela que impede a leitura errada de que "estar público" significa "qualquer um pode
 * reutilizar". `identificador` e `urlReferencia` só aparecem quando existem: o caso
 * "Todos os direitos reservados" não tem nem identificador nem texto público, e a
 * ausência é dita como ausência em vez de virar linha vazia.
 */
function Licenca({ material }: { material: MaterialDetalhe }) {
  const { licenca } = material;

  return (
    <section className="sc-card sc-card--pad mt-8" aria-labelledby="licenca-titulo">
      <h2 className="sc-subhead mt-0" id="licenca-titulo">
        Licença: {licenca.nome}
      </h2>

      <p className="sc-body sc-measure">{licenca.descricaoCurta}</p>

      {licenca.identificador ? (
        <p className="sc-helper mt-3">
          Identificador da licença: <span className="sc-mono">{licenca.identificador}</span>
        </p>
      ) : (
        <p className="sc-helper mt-3">
          Esta licença não tem identificador público — as condições são as descritas acima.
        </p>
      )}

      {licenca.urlReferencia ? (
        <p className="sc-helper">
          <a href={licenca.urlReferencia} rel="license noopener noreferrer" target="_blank">
            Ler os termos completos da licença
          </a>{' '}
          (abre em outro site)
        </p>
      ) : null}

      <p className="sc-helper sc-measure">
        A licença foi escolhida por quem publicou e vale para quem quer reutilizar o material:
        estar público não quer dizer domínio público.
      </p>
    </section>
  );
}

/**
 * Acesso ao arquivo.
 *
 * Estado real de hoje, dito com todas as letras: não há arquivo para acessar. O contrato
 * público (v0.3.0) não publica nome, formato nem endereço de arquivo — a Etapa 2 é a
 * descoberta, e o envio de arquivos entra depois (RF014). A alternativa seria um botão
 * morto ou um campo inventado; as duas seriam pior do que a frase abaixo, porque
 * prometeriam algo que o produto não faz (§20.1, §21.1).
 *
 * Quando o arquivo existir, o que decide se a ação aparece é o DADO (há arquivo público
 * no payload), nunca uma condição de tela — e material fora do público nem chega aqui:
 * ele responde 404 (CA008, RN002).
 */
function Arquivo() {
  return (
    <section className="sc-card sc-card--pad mt-6" aria-labelledby="arquivo-titulo">
      <h2 className="sc-subhead mt-0" id="arquivo-titulo">
        Acesso ao arquivo
      </h2>
      <p className="sc-body sc-measure">
        Este material ainda não tem arquivo disponível para acesso. A plataforma hoje publica a
        informação do material — título, descrição, autoria, categoria, tipo, datas e licença — e
        ainda não serve arquivos para leitura ou download.
      </p>
      <p className="sc-helper sc-measure">
        O que está descrito acima é o que existe publicado. Se o arquivo for retirado pela pessoa
        que publicou, ele deixa de aparecer aqui: material retirado sai do ar junto com este
        endereço.
      </p>
    </section>
  );
}

/**
 * Caminhos para continuar a navegação (RF007, CA001): de volta à listagem e para os
 * outros materiais da mesma categoria. Os dois destinos existem hoje.
 */
function Navegacao({ material }: { material: MaterialDetalhe }) {
  return (
    <nav className="mt-8 flex flex-wrap items-center gap-3" aria-label="Continuar a navegação">
      <Link className="sc-btn sc-btn--ghost sc-btn--sm" href="/explorar">
        Ver todos os materiais publicados
      </Link>
      <Link
        className="sc-btn sc-btn--ghost sc-btn--sm"
        href={rotaExplorar(`/explorar?categoria=${material.categoria.slug}`)}
      >
        Mais em {material.categoria.nome}
      </Link>
    </nav>
  );
}

/**
 * Estado para a resposta que não trouxe material.
 *
 * `não encontrado` (404 e o 400 de slug malformado) sai com a MESMA redação dos quatro
 * casos de propósito: retirado, excluído, inexistente e endereço malformado não se
 * distinguem, e a página não desfaz a decisão da API (RN002, CA008). O endereço pedido
 * aparece na tela porque foi ele que a pessoa digitou — mostrar o que se tentou abrir é
 * o que permite corrigir o link.
 *
 * Os outros casos têm estado próprio: 401 e 403 como o DRS §15.3 manda (não deveriam
 * ocorrer numa rota pública; o mapeamento existe para não mentir sobre a causa), e o
 * resto (rede, 5xx, resposta fora do contrato) em `erro de rede/servidor`, com a ação
 * de tentar de novo.
 */
function EstadoDaFalha({ falha, slug }: { falha: Falha; slug: string }) {
  if (falha.motivo === 'http') {
    if (falha.status === 401) return <PageState kind="session-expired" />;
    if (falha.status === 403) return <PageState kind="unauthorized" />;
    if (falha.status === 404 || falha.status === 400) {
      return (
        <div className="sc-page">
          <PageState
            kind="not-found"
            title="Não encontramos este material"
            message="Este endereço não corresponde a nenhum material público: o link pode estar incorreto, ou o material foi retirado por quem publicou. Material retirado e material que nunca existiu respondem igual, de propósito — a plataforma não revela o que saiu do ar."
            details={
              <p className="sc-body">
                Endereço pedido: <span className="sc-mono">/materiais/{slug}</span>
              </p>
            }
          />
        </div>
      );
    }
  }

  return (
    <div className="sc-page">
      <PageState
        kind="network-error"
        title="Não foi possível carregar este material"
        message="A resposta da API não chegou ou o servidor falhou ao responder. O material não foi exibido justamente para não mostrar informação que não é a publicada. Tente de novo — se continuar, tente outra vez em alguns minutos."
        action={<Recarregar />}
      />
      <p className="mt-6">
        <Link className="sc-btn sc-btn--ghost sc-btn--sm" href="/explorar">
          Ver todos os materiais publicados
        </Link>
      </p>
    </div>
  );
}
