import type { Metadata } from 'next';
import Link from 'next/link';
import { cache } from 'react';
import { EstadoDaFalha } from '@/components/dominio/EstadoDaFalha';
import { ListagemDeMateriais } from '@/components/dominio/ListagemDeMateriais';
import { Chip } from '@/components/ui/Chip';
import { PageState } from '@/components/ui/PageState';
import { obterAutor, type Falha } from '@/lib/api/client';
import type { AutorPublico } from '@/lib/api/tipos';
import { lerPagina, slugValido, urlComPagina } from '@/lib/pagina';

/**
 * `/u/[slug]` — perfil público do autor (RF006, PAGE_BLUEPRINTS.md §4).
 *
 * Renderizada no SERVIDOR: identidade, `canonical` e a lista de materiais saem no HTML
 * entregue, sem depender de JavaScript (RNF023–RNF025). O que a página exibe é o que o
 * contrato publica em `AutorPublico` — nem mais (nada de campo inventado) nem menos.
 *
 * O que esta página NÃO faz, de propósito:
 *
 *   - **não exibe dado privado algum** (RNF013): e-mail de login, id de conta, hash de senha
 *     e token não existem no schema nem em `lib/api/tipos.ts`, e a tela escreve campo por
 *     campo em vez de despejar o objeto — o que não está no tipo não tem como vazar para o
 *     HTML. A evidência do card prova isso servindo esses campos A MAIS na resposta e
 *     conferindo que nenhum deles aparece no HTML;
 *   - **não apresenta afiliação como vínculo atestado** (RN011, CA012, §13.1). O contrato
 *     chama o campo de `afiliacaoDeclarada` e diz, no próprio schema, que é declaração do
 *     autor; a página diz a mesma coisa em português, com o rótulo "Afiliação declarada" e a
 *     autoria da frase atribuída a quem a escreveu. Não existe emblema, nota nem distintivo de
 *     instituição — nada disso existe no dado;
 *   - **não replica a identidade em cada item da lista**: o card de material já mostra
 *     autoria, categoria, tipo, licença e data (E2.8), e a lista é o MESMO componente da
 *     listagem pública (o card E2.10 pede "não duplicar tela").
 *
 * Um detalhe de identificação que vale registrar: a página é a MESMA rota para autor com e
 * sem material publicado. Autor sem publicação responde 200 com lista vazia (E2.5) — ausência
 * de material não é perfil inexistente —, e é o que o estado `vazio` explica na tela, com o
 * convite para explorar o que já está publicado.
 *
 * Os sete estados do DRS §15.3 nesta rota: `sucesso` (o bloco da lista marcado pela própria
 * `ListagemDeMateriais`), `vazio` (autor sem publicação, e também a página além da última —
 * os dois dizem coisas diferentes e por isso têm textos diferentes), `não encontrado` (perfil
 * de conta não ativa, perfil inexistente, slug fora do formato e `page` fora do formato, com o
 * MESMO texto: a API responde 404 indistinguível e a tela não desfaz essa decisão, RN002/CA008),
 * `erro de rede/servidor` com "tentar de novo", e 401/403 mapeados — não deveriam ocorrer numa
 * rota pública, mas dizer "falha de conexão" se ocorrerem seria mentira sobre a causa.
 * `carregando` NÃO tem tela intermediária, pela mesma razão medida nos cards E2.7–E2.9: com
 * `loading.tsx` o conteúdo real sai dentro de um bloco `hidden` que só o JavaScript revela, e
 * quem não executa JS (e o rastreador que só lê HTML) veria "Carregando" para sempre — contra
 * RNF023/§12.3.
 *
 * STATUS das duas respostas (card E2.12, que fechou a troca antes registrada como pendência):
 * o perfil que NÃO é público — e o que nunca existiu, o endereço fora do formato e o `page`
 * fora do formato — responde **404 de verdade**, com o estado do DRS §15.3 no HTML entregue e
 * `noindex` (RNF023, RNF024). Quem decide o status é o proxy (`src/proxy.ts`), fora
 * da renderização, porque o `notFound()` do App Router entrega 404 com o corpo vazio (medido
 * no card E2.9 e reconfirmado no E2.12); a página continua decidindo o que MOSTRAR. Falha de
 * rede, 5xx e 401/403 seguem em 200, com o estado que corresponde à causa: 404 ali afirmaria
 * que o endereço não corresponde a nada, e o que aconteceu foi outra coisa.
 */

type Props = {
  params: Promise<{ slug: string }>;
  searchParams: Promise<Record<string, string | string[] | undefined>>;
};

/**
 * Leitura da rota: o endereço é recusado antes de qualquer chamada, e a identidade vem da
 * API só quando o endereço pode corresponder a um perfil.
 */
type Leitura =
  | { tipo: 'endereco-recusado'; campo: string; valor: string; esperado: string }
  | { tipo: 'falha'; falha: Falha }
  | { tipo: 'autor'; autor: AutorPublico };

function primeiro(valor: string | string[] | undefined): string | undefined {
  if (Array.isArray(valor)) return valor[0];
  return valor;
}

/**
 * Uma leitura por render: `generateMetadata` e a página precisam do MESMO resultado.
 * `cache` do React memoiza por requisição — com os mesmos argumentos, o `curl` de uma visita
 * gera UMA requisição à API, e não duas (a evidência conta as linhas no log do fixture).
 *
 * Os argumentos são os brutos da URL (e não o já convertido) porque a validação é parte da
 * leitura: assim a chave do memo é exatamente o que a pessoa pediu.
 */
const lerPerfil = cache(async (slug: string, pageBruto: string | undefined): Promise<Leitura> => {
  if (!slugValido(slug)) {
    return {
      tipo: 'endereco-recusado',
      campo: 'slug',
      valor: slug,
      esperado: 'slug no formato nome-em-minusculas (ex.: maria-silva)',
    };
  }

  const pagina = lerPagina(pageBruto);
  if (!pagina.ok) {
    return { tipo: 'endereco-recusado', campo: 'page', valor: pagina.valor, esperado: pagina.esperado };
  }

  const resultado = await obterAutor(slug, pagina.pagina - 1);
  if (!resultado.ok) return { tipo: 'falha', falha: resultado.falha };
  return { tipo: 'autor', autor: resultado.dados };
});

export async function generateMetadata({ params, searchParams }: Props): Promise<Metadata> {
  const { slug } = await params;
  const brutos = await searchParams;
  const leitura = await lerPerfil(slug, primeiro(brutos.page));

  if (leitura.tipo !== 'autor') {
    // Sem identidade não há título nem descrição verdadeiros para publicar, e o endereço fica
    // fora de índice em TODOS os casos de falha: um endereço que hoje não serve um perfil
    // público (conta não ativa, perfil inexistente, falha de servidor) não deve virar
    // resultado de busca, e sem `canonical` a página não declara uma URL para algo que não
    // está lá (RNF024).
    return { title: 'Perfil de autor', robots: { index: false, follow: false } };
  }

  const autor = leitura.autor;

  return {
    // `title` = nome público (o `template` do layout raiz acrescenta " · SciPath").
    title: autor.nomePublico,
    // `description` a partir da biografia, como o contrato manda (RNF024). Sem biografia não
    // se inventa uma: a frase descreve o que a PÁGINA oferece, sem afirmar nada sobre a
    // pessoa que ela não tenha dito.
    description:
      autor.biografia ??
      `Perfil público de ${autor.nomePublico} no SciPath: materiais publicados, áreas de interesse informadas pela própria pessoa e links externos que ela declarou.`,
    // O `canonical` sai do `slug` DA RESPOSTA, e não do que veio na URL: assim ele é estável e
    // não depende de `page` nem de parâmetro extra na barra de endereços (RNF025).
    alternates: { canonical: `/u/${autor.slug}` },
  };
}

export default async function PerfilPage({ params, searchParams }: Props) {
  const { slug } = await params;
  const brutos = await searchParams;
  const leitura = await lerPerfil(slug, primeiro(brutos.page));

  if (leitura.tipo === 'falha') {
    return (
      <EstadoDaFalha
        falha={leitura.falha}
        oQue="este perfil"
        tituloNaoEncontrado="Não encontramos este perfil"
        mensagemNaoEncontrado="Este endereço não corresponde a nenhum perfil público: o link pode estar incorreto, ou a conta não está ativa nesta plataforma. Perfil que não é público e perfil que nunca existiu respondem igual, de propósito — a plataforma não conta quem saiu do ar."
        endereco={`/u/${slug}`}
      />
    );
  }

  if (leitura.tipo === 'endereco-recusado') {
    return (
      <div className="sc-page">
        <PageState
          kind="not-found"
          title="Este endereço não abre um perfil público"
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

  const autor = leitura.autor;

  return (
    <div className="sc-page">
      <Identidade autor={autor} />

      <section aria-labelledby="materiais-do-autor" className="mt-10">
        <h2 className="sc-subhead" id="materiais-do-autor">
          Materiais publicados
        </h2>
        <ListagemDeMateriais
          pagina={autor.materiais}
          urlDaPagina={(pagina) => urlComPagina(`/u/${autor.slug}`, pagina)}
          rotulo={`Materiais publicados por ${autor.nomePublico}`}
          vazio={<VazioDoAutor autor={autor} />}
        />
      </section>
    </div>
  );
}

/**
 * Identidade pública (RF006): nome, bio, áreas de interesse, links e a afiliação DECLARADA.
 *
 * Cada bloco informado pela pessoa é precedido do rótulo do que ele é, porque o que a página
 * afirma sobre alguém tem de ser atribuído: "áreas de interesse informadas", "links
 * declarados", "afiliação declarada". A ausência de um bloco é dita como ausência — nunca
 * preenchida com texto genérico que passaria por informação.
 */
function Identidade({ autor }: { autor: AutorPublico }) {
  const { nomePublico, biografia, afiliacaoDeclarada, areasInteresse, linksExternos, slug } = autor;

  return (
    <header className="sc-sec__head">
      <p className="sc-sec__num">
        <i>Pessoa</i> perfil público
      </p>
      <h1 className="sc-h1">{nomePublico}</h1>

      {biografia ? (
        <p className="sc-sec__lead">{biografia}</p>
      ) : (
        <p className="sc-helper">
          Esta pessoa ainda não escreveu uma biografia no perfil. O que está abaixo é o que ela
          informou ao criar o perfil.
        </p>
      )}

      <dl className="mt-8 grid gap-5 sm:grid-cols-2">
        <div className="sm:col-span-2">
          <dt className="sc-caption">Afiliação declarada</dt>
          <dd className="sc-body m-0">
            {afiliacaoDeclarada ? (
              <>
                <span className="sc-mono">Declarado por {nomePublico}:</span> {afiliacaoDeclarada}
              </>
            ) : (
              <>Esta pessoa não informou afiliação no perfil.</>
            )}
          </dd>
        </div>

        <div>
          <dt className="sc-caption">Áreas de interesse</dt>
          <dd className="sc-body m-0">
            {areasInteresse.length > 0 ? (
              <ul className="m-0 flex list-none flex-wrap gap-2 p-0">
                {areasInteresse.map((area) => (
                  <li key={area}>
                    <Chip dot={false}>{area}</Chip>
                  </li>
                ))}
              </ul>
            ) : (
              <>Esta pessoa não informou áreas de interesse.</>
            )}
          </dd>
        </div>

        <div>
          <dt className="sc-caption">Links externos</dt>
          <dd className="sc-body m-0">
            {linksExternos.length > 0 ? (
              <ul className="m-0 list-none space-y-1 p-0">
                {linksExternos.map((link) => (
                  <li key={link}>
                    {ehEnderecoWeb(link) ? (
                      <a href={link} target="_blank" rel="noopener noreferrer nofollow">
                        {link}
                      </a>
                    ) : (
                      // Texto livre que não é endereço web: exibido COMO TEXTO. Transformar em
                      // link exigiria adivinhar o protocolo (e `mailto:`/`javascript:` seriam
                      // consequências dessa adivinhação).
                      <span>{link}</span>
                    )}
                  </li>
                ))}
              </ul>
            ) : (
              <>Esta pessoa não informou links externos.</>
            )}
          </dd>
        </div>

        <div className="sm:col-span-2">
          <dt className="sc-caption">Endereço estável</dt>
          <dd className="sc-body m-0">
            <span className="sc-mono">/u/{slug}</span>
          </dd>
        </div>
      </dl>

      <p className="sc-helper sc-measure mt-6">
        Tudo o que aparece acima é o que a própria pessoa declarou no perfil: a plataforma não
        atesta conteúdo, vínculo institucional nem titulação. A afiliação é uma declaração de
        quem a escreveu, e é assim que ela aparece aqui.
      </p>
    </header>
  );
}

/** Só `http` e `https` viram link — o resto é texto, sem adivinhação de protocolo. */
function ehEnderecoWeb(valor: string): boolean {
  return /^https?:\/\/\S+$/i.test(valor.trim());
}

/**
 * Estado `vazio` do perfil: a pessoa existe e não publicou nada. Não é erro dela nem da
 * página — a mensagem diz isso e oferece o próximo passo real, que é o acervo publicado
 * (DRS §15.3, §12.3: nunca tela branca).
 */
function VazioDoAutor({ autor }: { autor: AutorPublico }) {
  return (
    <PageState
      kind="empty"
      title="Ainda não há material publicado por esta pessoa"
      message={`${autor.nomePublico} tem perfil público na plataforma e ainda não publicou material. Quando a primeira publicação acontecer, ela aparece nesta lista — a ordem é a do contrato: publicação mais recente.`}
      action={
        <>
          <Link className="sc-btn sc-btn--primary sc-btn--sm" href="/explorar">
            Explorar o que já está publicado
          </Link>
          <Link className="sc-btn sc-btn--ghost sc-btn--sm" href="/">
            Voltar ao início
          </Link>
        </>
      }
    />
  );
}
