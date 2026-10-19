import type { Metadata } from 'next';
import Link from 'next/link';
import { FormularioPublicacao } from '@/components/painel/FormularioPublicacao';
import { ReenvioDeVerificacao } from '@/components/painel/ReenvioDeVerificacao';
import { PageState } from '@/components/ui/PageState';
import { Badge } from '@/components/ui/Badge';
import { listarCategorias, listarLicencas, listarTiposMaterial } from '@/lib/api/client';
import { lerSessaoNoServidor } from '@/lib/sessao/servidor';
import {
  formatarBytes,
  limiteDeArquivoEmBytes,
  VARIAVEL_DO_LIMITE_DE_ARQUIVO,
} from '@/lib/sessao/validacao';

/**
 * `/painel/publicar` — publicar material (RF012, §8.3; PAGE_BLUEPRINTS.md §11).
 *
 * O que esta página tem de resolver ANTES de mostrar o formulário, e por quê:
 *
 *   1. **sessão** — publicar é uma escrita com identidade; a página lê a sessão desta
 *      requisição no SERVIDOR (`lerSessaoNoServidor`, que reenvia a cookie `HttpOnly` à API).
 *      Sem sessão, o estado é `sessão expirada` com o destino preservado (`?proximo=`), como
 *      em `/painel` e `/painel/perfil`;
 *   2. **estado da conta** — publicar exige conta `ATIVO` (ADR-0003 (b)). Conta
 *      `PENDENTE_VERIFICACAO` NÃO recebe o formulário: recebe a explicação do motivo e o
 *      caminho de reenvio (o reenvio existe desde o E3.4). É o §11 na letra: "se a conta não
 *      estiver verificada, a página explica o motivo e oferece reenvio — sem erro seco". A
 *      verificação poderia cair entre a renderização e o envio; para esse caso o formulário
 *      trata o 403 com o mesmo caminho (`publicacaoBloqueada`);
 *   3. **as listas da taxonomia** — categoria, tipo e licença vêm da API (`/categorias`,
 *      `/tipos-material`, `/licencas`), lidas aqui no SERVIDOR e entregues prontas no HTML.
 *      Três razões, todas de requisito: RF018 proíbe listas divergentes entre telas (a fonte
 *      é uma só); RNF023 exige o conteúdo no documento ENTREGUE (a lista aparece sem
 *      depender de hidratação); e uma API fora do ar precisa virar estado de erro explícito,
 *      não um `<select>` vazio se passando por lista completa.
 *
 * Ordem de tratamento: sessão → estado da conta → taxonomia. Nenhuma tela é montada com dado
 * pela metade, e nenhum material de exemplo entra no lugar (§20.1, AGENTS.md regra 9).
 *
 * SEO/privacidade: página privada, `noindex, nofollow` (RNF024, CA011) — o conteúdo depende de
 * quem pede e indexá-lo publicaria uma tela que só serve à conta. Nada de e-mail de login,
 * senha, hash ou token é renderizado: esses campos não existem nos schemas que a tela consome
 * (RNF013).
 */
export const metadata: Metadata = {
  title: 'Publicar material',
  description:
    'Publique um material no SciPath: título, descrição, categoria, tipo, licença e o arquivo PDF, com a confirmação do direito de compartilhar.',
  // Área privada fora de índice (RNF024, CA011).
  robots: { index: false, follow: false },
};

export default async function PublicarMaterialPage() {
  const leitura = await lerSessaoNoServidor();

  if (leitura.tipo === 'falha') {
    return (
      <div className="sc-page">
        <PageState
          kind="network-error"
          title="Não foi possível abrir a página de publicação agora"
          message="O servidor não respondeu à consulta de sessão, então não sabemos de quem é a conta que publicaria — e mostrar um formulário que não pode ser enviado seria pior que não mostrar. Tente de novo em instantes."
          action={
            <Link className="sc-btn sc-btn--ghost sc-btn--sm" href="/painel">
              Voltar à área do usuário
            </Link>
          }
        />
      </div>
    );
  }

  if (leitura.tipo === 'anonimo') {
    return (
      <div className="sc-page">
        <PageState
          kind="session-expired"
          title="Entre para publicar um material"
          message="A autoria de um material é a conta que o publica, e ela vem da sessão — não existe campo de autor no formulário. Entre com e-mail e senha: depois do login você volta direto para cá, com o formulário pronto."
          loginDestination="/painel/publicar"
          details={
            <p className="sc-helper">
              Se você não tem conta, <Link href="/cadastro">criar conta</Link> já abre a sessão.
              Publicar, porém, exige o e-mail confirmado — o cadastro explica esse passo.
            </p>
          }
        />
      </div>
    );
  }

  const { estadoConta } = leitura.sessao;

  // §11: conta sem e-mail confirmado NÃO recebe o formulário — recebe o motivo e o caminho de
  // reenvio. Mostrar o formulário para quem não pode enviar produziria um erro no fim do
  // caminho em vez de uma explicação no começo dele.
  if (estadoConta !== 'ATIVO') {
    return (
      <div className="sc-page">
        <header className="sc-sec__head">
          <p className="sc-sec__num">
            <i>Conta</i> publicar material
          </p>
          <h1 className="sc-h1">Publicar material</h1>
        </header>

        <section
          className="sc-notice sc-notice--atencao sc-notice--pagina"
          aria-labelledby="verificacao-pendente"
          data-aviso="publicacao-bloqueada-por-verificacao"
          data-estado-conta={estadoConta}
        >
          <Badge tone="yellow">e-mail não confirmado</Badge>
          <h2 className="sc-notice__title" id="verificacao-pendente">
            Confirme o seu e-mail para publicar
          </h2>
          <p className="sc-notice__msg">
            Publicar é a <strong>única</strong> operação do SciPath que exige e-mail confirmado
            (ADR-0003). O formulário não foi aberto porque o servidor recusaria o envio — e
            preencher um formulário que termina em recusa não é um caminho, é uma armadilha.
          </p>
          <p className="sc-notice__msg">
            Navegar, buscar, abrir materiais e editar o perfil continuam liberados agora mesmo. A
            confirmação chega por um link enviado ao endereço da conta; esse endereço não é
            exibido nesta tela nem em nenhuma outra (RNF013). Se o link não chegou, peça outro
            abaixo — o contrato aceita um pedido a cada 60 segundos.
          </p>
          <ReenvioDeVerificacao />
        </section>

        <p className="mt-8">
          <Link className="sc-btn sc-btn--ghost sc-btn--sm" href="/painel">
            Voltar à área do usuário
          </Link>
        </p>
      </div>
    );
  }

  // As três listas de referência, lidas no SERVIDOR. Qualquer uma fora do ar já é motivo para
  // não abrir o formulário: um `<select>` sem opção não é uma lista vazia legítima — é uma
  // falha de leitura, e dizer isso é o estado de erro de rede (RNF004).
  const [categorias, tipos, licencas] = await Promise.all([
    listarCategorias(),
    listarTiposMaterial(),
    listarLicencas(),
  ]);

  if (!categorias.ok || !tipos.ok || !licencas.ok) {
    return (
      <div className="sc-page">
        <PageState
          kind="network-error"
          title="Não foi possível carregar as listas de categoria, tipo e licença"
          message="As opções de categoria, tipo de material e licença vêm do servidor, e a consulta não respondeu — abrir o formulário sem elas faria você escolher no escuro e ser recusado no envio. Nada foi perdido: tente de novo em instantes."
          action={
            <Link className="sc-btn sc-btn--ghost sc-btn--sm" href="/painel">
              Voltar à área do usuário
            </Link>
          }
        />
      </div>
    );
  }

  // O limite de upload é o MESMO configurado no backend (`SCIPATH_UPLOAD_MAX_BYTES`, ADR-0005
  // §1): a tela exibe o valor corrente em vez de inventar um número próprio. Ausente ou
  // inválido, vale o padrão do ADR — que é o que o servidor aplica no mesmo caso.
  const limiteBytes = limiteDeArquivoEmBytes(process.env[VARIAVEL_DO_LIMITE_DE_ARQUIVO]);

  return (
    <div className="sc-page">
      <header className="sc-sec__head">
        <p className="sc-sec__num">
          <i>Conta</i> publicar material
        </p>
        <h1 className="sc-h1">Publicar material</h1>
        <p className="sc-sec__lead">
          Um material é um arquivo PDF com os dados que o descrevem: título, descrição, categoria,
          tipo e licença. Os dados e o arquivo são gravados numa única operação — se algo for
          recusado, nada fica pela metade e nada do que você escreveu é apagado.
        </p>
      </header>

      <section
        className="sc-notice sc-notice--info sc-notice--pagina"
        aria-labelledby="autor-licenca"
        data-aviso="autoria-e-licenca"
      >
        <h2 className="sc-notice__title" id="autor-licenca">
          Autoria e licença são explícitas
        </h2>
        <p className="sc-notice__msg">
          A <strong>autoria</strong> é a conta que publica: vem da sessão, não de um campo do
          formulário, e é ela que assina a página pública do material.
        </p>
        <p className="sc-notice__msg">
          A <strong>licença</strong> é escolha sua, sem valor padrão: é ela que diz o que outras
          pessoas podem fazer com o material. Publicar não significa abrir mão dos direitos, e a
          plataforma não atribui licença em nome de ninguém.
        </p>
        <p className="sc-notice__msg">
          O limite de tamanho do arquivo nesta instalação é <strong>{formatarBytes(limiteBytes)}</strong>,
          e o formato aceito é PDF — conferido pelo conteúdo do arquivo, não apenas pela extensão.
        </p>
      </section>

      <FormularioPublicacao
        categorias={categorias.dados.items}
        tipos={tipos.dados.items}
        licencas={licencas.dados.items}
        limiteBytes={limiteBytes}
        contaVerificada
      />
    </div>
  );
}
