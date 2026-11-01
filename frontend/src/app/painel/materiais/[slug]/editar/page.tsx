import type { Metadata } from 'next';
import Link from 'next/link';
import type { ReactNode } from 'react';
import { FormularioEdicao } from '@/components/painel/FormularioEdicao';
import { Badge, type BadgeTone } from '@/components/ui/Badge';
import { PageState } from '@/components/ui/PageState';
import { Recarregar } from '@/components/ui/Recarregar';
import {
  listarCategorias,
  listarLicencas,
  listarTiposMaterial,
  obterMaterialProprio,
  type Falha,
} from '@/lib/api/client';
import type { MaterialProprio } from '@/lib/api/tipos';
import { ESTADO_DO_MATERIAL } from '@/lib/materiais/acoes';
import { edicaoPermitida } from '@/lib/materiais/edicao';
import { cabecalhoDeCookies, lerSessaoNoServidor } from '@/lib/sessao/servidor';

/**
 * `/painel/materiais/[slug]/editar` — edição dos metadados de um material próprio
 * (RF016, RF018; `PAGE_BLUEPRINTS.md` §13; DRS §15.2).
 *
 * Renderizada no SERVIDOR, com a sessão DESTA requisição (`lerSessaoNoServidor`) e com a
 * cookie `HttpOnly` reenviada à API (`cabecalhoDeCookies` → `obterMaterialProprio`). Três
 * razões, todas de requisito: sem isso o HTML entregue teria um "carregando" no lugar do
 * formulário (RNF023 — quem não executa JavaScript precisa poder ler o que está publicado
 * hoje); as três listas de taxonomia (RF018) precisam chegar prontas no documento; e uma
 * API fora do ar tem de virar estado de erro explícito, não um `<select>` vazio se passando
 * por lista completa. Como a leitura usa `cookies()`, a rota é dinâmica por construção:
 * nenhuma página privada pode ser servida do cache para outra pessoa.
 *
 * A ordem de tratamento é a mesma das outras telas privadas — sessão → material → taxonomia
 * — e por um motivo: cada passo estreita quem pode ver o quê, e nenhuma tela é montada com
 * dado pela metade.
 *
 * O que esta página NÃO decide: se a edição é permitida. A propriedade é do SERVIDOR
 * (RN001, RNF010, CA007); aqui só se evita oferecer um caminho que o servidor recusaria —
 * o `PUT` de um material alheio, inexistente ou `EXCLUIDO` responde o MESMO 404
 * (ADR-0011 §3).
 *
 * Os estados desta tela, e o que provoca cada um:
 *
 * | Estado | O que o provoca |
 * |---|---|
 * | `carregando` | não é documento entregue: sessão e material são lidos no servidor (RNF023) |
 * | `sucesso` | 200 com o material próprio e as três listas da taxonomia |
 * | `não encontrado` | 404 da API (inexistente, de outra conta ou excluído — o mesmo corpo, ADR-0011 §3) e o 400 de `slug` fora do formato |
 * | `não autorizado` | 403 da API na leitura do próprio material |
 * | `sessão expirada` | sem sessão na requisição, ou 401 entre a leitura da sessão e a do material — login preservando ESTA página de edição |
 * | `erro de rede/servidor` | a leitura não respondeu, respondeu fora do contrato, ou 5xx |
 * | — (estado do material) | `EXCLUIDO`: o material é terminal (ADR-0011 §6) e o `PUT` responde 404; a tela mostra por que não há o que editar, em vez de abrir um formulário que termina em recusa |
 *
 * Privacidade e SEO: página privada com `noindex, nofollow` (RNF024, CA011) e nenhum dado
 * privado no documento — o recorte consumido (`MaterialProprio`) não tem e-mail de login,
 * hash, token, identificador de conta nem chave de armazenamento (RNF013, RNF014).
 */

export const metadata: Metadata = {
  title: 'Editar material',
  description:
    'Atualize título, descrição, categoria, tipo e licença de um material seu. O endereço público e o arquivo continuam os mesmos.',
  // Área privada fora de índice (RNF024, CA011).
  robots: { index: false, follow: false },
};

type Props = { params: Promise<{ slug: string }> };

const TOM_DO_ESTADO: Record<string, BadgeTone> = {
  PUBLICADO: 'green',
  RETIRADO: 'yellow',
  EXCLUIDO: 'red',
};

export default async function EditarMaterialPage({ params }: Props) {
  const { slug } = await params;
  const destino = `/painel/materiais/${slug}/editar`;

  const leitura = await lerSessaoNoServidor();

  if (leitura.tipo === 'falha') {
    return (
      <Moldura>
        <PageState
          kind="network-error"
          title="Não foi possível abrir a edição agora"
          message="O servidor não respondeu à consulta de sessão, então não sabemos de quem é o material deste endereço — e abrir um formulário que não pode ser salvo seria pior que não abrir. Tente de novo em instantes."
          action={
            <Link className="sc-btn sc-btn--ghost sc-btn--sm" href="/painel/materiais">
              Voltar aos materiais próprios
            </Link>
          }
        />
      </Moldura>
    );
  }

  if (leitura.tipo === 'anonimo') {
    return (
      <Moldura>
        <PageState
          kind="session-expired"
          title="Entre para editar este material"
          message="Editar metadados é uma operação da conta dona do material, e quem diz qual é essa conta é a sessão — não o endereço. Entre com e-mail e senha: depois do login você volta direto para esta página de edição, com os dados já preenchidos."
          loginDestination={destino}
          details={
            <p className="sc-helper">
              Ainda não tem conta? <Link href="/cadastro">Criar conta</Link> leva menos de um
              minuto e já abre a sessão.
            </p>
          }
        />
      </Moldura>
    );
  }

  const contaAtiva = leitura.sessao.estadoConta === 'ATIVO';

  // A leitura do DONO: é ela que enxerga o material em qualquer estado, inclusive
  // `RETIRADO` — para o qual a página pública responde 404 (RN002, ADR-0011 §2).
  const resultado = await obterMaterialProprio(await cabecalhoDeCookies(), slug);

  if (!resultado.ok) {
    return (
      <Moldura>
        <EstadoDaFalha falha={resultado.falha} destino={destino} slug={slug} />
      </Moldura>
    );
  }

  // A resposta JSON pode conter campos extras, apesar do tipo OpenAPI conhecido. Como o
  // objeto atravessa a fronteira para o componente client, copie o allowlist do contrato;
  // serializar a resposta inteira poderia vazar email, token ou chave de armazenamento (RNF013/014).
  const lido = resultado.dados;
  const material: MaterialProprio = {
    slug: lido.slug,
    titulo: lido.titulo,
    descricao: lido.descricao,
    estado: lido.estado,
    categoria: { slug: lido.categoria.slug, nome: lido.categoria.nome },
    tipo: { slug: lido.tipo.slug, nome: lido.tipo.nome },
    licenca: {
      slug: lido.licenca.slug,
      nome: lido.licenca.nome,
      descricaoCurta: lido.licenca.descricaoCurta,
    },
    arquivo: {
      nomeOriginal: lido.arquivo.nomeOriginal,
      tamanhoBytes: lido.arquivo.tamanhoBytes,
      disponivel: lido.arquivo.disponivel,
    },
    publicadoEm: lido.publicadoEm,
    atualizadoEm: lido.atualizadoEm,
  };
  const estado = ESTADO_DO_MATERIAL[material.estado];

  // `EXCLUIDO` é terminal (ADR-0011 §6): o `PUT` responde 404 e não há caminho de volta.
  // A tela não abre o formulário — ela explica, no mesmo desenho que `/painel/publicar` usa
  // para a conta pendente de verificação: um aviso com o motivo, não uma recusa no fim do
  // preenchimento.
  if (!edicaoPermitida(material.estado)) {
    return (
      <Moldura>
        <section
          className="sc-notice sc-notice--atencao sc-notice--pagina mt-8"
          aria-labelledby="edicao-bloqueada"
          data-aviso="edicao-bloqueada-estado-terminal"
          data-estado-material={material.estado}
        >
          <Badge tone={TOM_DO_ESTADO[material.estado] ?? 'red'}>{estado.rotulo}</Badge>
          <h2 className="sc-notice__title" id="edicao-bloqueada">
            Este material não aceita edição: o estado é terminal
          </h2>
          <p className="sc-notice__msg">
            O material <strong data-material={material.slug}>{material.titulo}</strong> está no
            estado <strong>Excluído</strong>, e a exclusão não tem volta nesta versão da
            plataforma: o registro dos dados permanece com você na lista, mas o servidor não
            aceita mais nenhuma operação sobre ele — a edição de metadados responde a mesma coisa
            que um material inexistente, de propósito (ADR-0011 §3 e §6). O formulário não foi
            aberto para não terminar em recusa.
          </p>
          <p className="sc-notice__msg">
            O arquivo deixa de estar disponível imediatamente e é removido do armazenamento em 7
            dias (ADR-0005 §3). Para publicar outro material no lugar deste, use o caminho abaixo.
          </p>
          <div className="sc-notice__actions">
            <Link className="sc-btn sc-btn--primary sc-btn--sm" href="/painel/publicar">
              Publicar um material
            </Link>
            <Link className="sc-btn sc-btn--ghost sc-btn--sm" href="/painel/materiais">
              Voltar aos materiais próprios
            </Link>
          </div>
        </section>
      </Moldura>
    );
  }

  // As três listas de referência, lidas no SERVIDOR e entregues prontas no HTML (RF018,
  // RNF023). Qualquer uma fora do ar já é motivo para não abrir o formulário: um `<select>`
  // sem opção não é uma lista vazia legítima — é falha de leitura, e dizer isso é o estado
  // de erro de rede (RNF004).
  const [categorias, tipos, licencas] = await Promise.all([
    listarCategorias(),
    listarTiposMaterial(),
    listarLicencas(),
  ]);

  if (!categorias.ok || !tipos.ok || !licencas.ok) {
    return (
      <Moldura>
        <PageState
          kind="network-error"
          title="Não foi possível carregar as listas de categoria, tipo e licença"
          message="As opções de categoria, tipo de material e licença vêm do servidor, e a consulta não respondeu — abrir o formulário sem elas faria você trocar dados no escuro e ser recusado ao salvar. Nada do material foi alterado: tente de novo em instantes."
          action={<Recarregar />}
        />
      </Moldura>
    );
  }

  return (
    <Moldura>
      <p className="sc-helper" data-estado-material={material.estado}>
        Estado de publicação:{' '}
        <Badge tone={TOM_DO_ESTADO[material.estado] ?? 'blue'}>{estado.rotulo}</Badge>{' '}
        {estado.descricao}
      </p>

      <section
        className="sc-notice sc-notice--info sc-notice--pagina"
        aria-labelledby="edicao-o-que-muda"
        data-aviso="edicao-de-metadados"
      >
        <h2 className="sc-notice__title" id="edicao-o-que-muda">
          O que esta edição muda — e o que ela não muda
        </h2>
        <p className="sc-notice__msg">
          Esta tela grava os cinco dados que descrevem o material: título, descrição, categoria,
          tipo de material e licença. Ela <strong>não</strong> muda o endereço público, o arquivo,
          a autoria nem o estado de publicação — cada um deles tem o seu lugar explicado abaixo do
          formulário, em vez de um campo que a pessoa procura e não encontra.
        </p>
        <p className="sc-notice__msg">
          Ao salvar, a página pública do material passa a mostrar os dados novos: é ela que
          comprova a alteração (RF016).
        </p>
      </section>

      <FormularioEdicao
        material={material}
        categorias={categorias.dados.items}
        tipos={tipos.dados.items}
        licencas={licencas.dados.items}
        contaVerificada={contaAtiva}
      />
    </Moldura>
  );
}

/** Casca da página: cabeçalho com a identidade da tela, igual em todos os estados. */
function Moldura({ children }: { children: ReactNode }) {
  return (
    <div className="sc-page">
      <header className="sc-sec__head">
        <p className="sc-sec__num">
          <i>Conta</i> editar material
        </p>
        <h1 className="sc-h1">Editar material</h1>
        <p className="sc-sec__lead">
          Atualizar os dados de um material que já está publicado no seu nome. Alterar aqui não
          republica nem reenvia nada: o material continua o mesmo arquivo, no mesmo endereço, com
          o mesmo autor.
        </p>
      </header>
      {children}
      <p className="mt-8">
        <Link className="sc-btn sc-btn--ghost sc-btn--sm" href="/painel/materiais">
          Voltar aos materiais próprios
        </Link>
      </p>
    </div>
  );
}

/**
 * Leitura que não trouxe o material → o estado do §15.3 que corresponde à CAUSA.
 *
 * O 404 é o caso mais importante desta tela, e ele NÃO é detalhado de propósito: material
 * inexistente, de outra conta e já `EXCLUIDO` respondem o mesmo corpo (ADR-0011 §3), e uma
 * tela que os diferenciasse desfaria essa decisão — diria a quem sonda endereços se o
 * material de outra pessoa existe. O que a tela pode dizer é que a operação não encontrou
 * material seu para este endereço, e que a lista é a visão atualizada.
 *
 * O 400 entra no mesmo estado: `slug` fora do formato é um endereço que não forma material —
 * mesma família de `/painel/materiais?page=abc`, que também recusa em vez de corrigir em
 * silêncio. 401 e 403 não deveriam acontecer depois de a sessão ter sido confirmada, mas
 * acontecem (a sessão pode cair entre as duas leituras), e mentir "falha de conexão" quando
 * o servidor respondeu 403 manda a pessoa conferir a internet por um defeito que não é dela.
 */
function EstadoDaFalha({ falha, destino, slug }: { falha: Falha; destino: string; slug: string }) {
  if (falha.motivo === 'http') {
    if (falha.status === 401) {
      return (
        <PageState
          kind="session-expired"
          title="Sua sessão expirou"
          message="A sessão deixou de valer antes de o material ser lido, então os dados não foram carregados e nada foi alterado. Entre novamente: o endereço desta página é preservado e você volta para ela."
          loginDestination={destino}
        />
      );
    }
    if (falha.status === 403) {
      return (
        <PageState
          kind="unauthorized"
          title="A plataforma não liberou a edição deste material"
          message="O servidor respondeu que esta conta não pode editar o material deste endereço. Editar é operação da conta dona, então isso costuma significar sessão de outra conta ou permissão revogada — entre novamente com a conta correta."
          action={
            <Link className="sc-btn sc-btn--ghost sc-btn--sm" href="/painel/materiais">
              Ver os seus materiais
            </Link>
          }
        />
      );
    }
    if (falha.status === 404 || falha.status === 400) {
      return (
        <PageState
          kind="not-found"
          title="Este endereço não corresponde a um material seu"
          message="O servidor não encontrou um material seu para este endereço. Material inexistente, de outra conta ou já excluído respondem a MESMA resposta — a plataforma não distingue os três casos, para não confirmar a existência de material de outra pessoa (ADR-0011). Nada foi alterado."
          details={
            <p className="sc-body">
              Endereço pedido: <span className="sc-mono">/painel/materiais/{slug}/editar</span> — a
              lista dos seus materiais mostra os endereços que existem.
            </p>
          }
          action={
            <Link className="sc-btn sc-btn--primary sc-btn--sm" href="/painel/materiais">
              Ver os seus materiais
            </Link>
          }
        />
      );
    }
  }

  return (
    <PageState
      kind="network-error"
      title="Não foi possível carregar o material para edição"
      message="A resposta do servidor não chegou ou veio fora do formato do contrato. O formulário não foi exibido justamente para não abrir com dados que não são os reais — nada foi alterado. Tente de novo; se continuar, tente outra vez em alguns minutos."
      action={<Recarregar />}
    />
  );
}
