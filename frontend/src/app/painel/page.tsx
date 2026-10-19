import type { Metadata } from 'next';
import Link from 'next/link';
import { PreviaDoPerfil } from '@/components/painel/PreviaDoPerfil';
import { ReenvioDeVerificacao } from '@/components/painel/ReenvioDeVerificacao';
import { Badge } from '@/components/ui/Badge';
import { PageState } from '@/components/ui/PageState';
import { rotaAutor } from '@/lib/rotas';
import { lerSessaoNoServidor } from '@/lib/sessao/servidor';

/**
 * `/painel` — área do usuário (RF009/RF011, DRS §15.2, PAGE_BLUEPRINTS.md §9).
 *
 * O que a tela tem de fazer, na letra do §15.2 e do card E3.7: **resumir as ações principais**
 * e dizer **o estado da conta** — verificação de e-mail pendente ou concluída — junto do que
 * ainda não está liberado por causa disso, com o caminho para reenviar o e-mail (ADR-0003 (h)).
 *
 * Renderizada no SERVIDOR com a sessão desta requisição (`lerSessaoNoServidor`, que reenvia o
 * cookie `HttpOnly` à API). A escolha não é de desempenho: sem ela, o HTML entregue teria um
 * "carregando" (ou um formulário vazio) no lugar do conteúdo, e quem não executa JavaScript —
 * ou o rastreador de um link compartilhado — não veria a própria conta. Como a leitura usa
 * `cookies()`, a rota é dinâmica por construção: nenhuma página privada pode ser servida do
 * cache para outra pessoa.
 *
 * Sobre o que a área do usuário aponta, e isso é decisão registrada: ela leva às telas que
 * EXISTEM — a edição do perfil público e a publicação de material (`/painel/publicar`, entregue
 * no E4.6). A listagem dos materiais próprios com editar/retirar/excluir é de etapa seguinte do
 * produto (§15.2 a lista; o MVP a entrega depois) e o §21.5 proíbe deixar "pronto" o que não
 * existe — um cartão vazio prometendo tela futura é pior que a ausência. Então não há bloco nem
 * link para ela: há a frase que diz que ela ainda não existe. O cartão de publicação avisa que
 * publicar exige e-mail confirmado quando a conta está `PENDENTE_VERIFICACAO` (ADR-0003 (b)),
 * para esta área não mandar a pessoa a uma tela que a bloquearia sem aviso.
 *
 * Dado privado (RNF013): o que esta tela exibe é escrito campo por campo, e o perfil que ela
 * recebe também já vem copiado campo a campo de `SessaoAtual`/`PerfilPublico` por
 * `lib/sessao/servidor.ts` — ali **não existe** e-mail de login, hash de senha, token nem
 * identificador de conta. A cópia na leitura não é preciosismo: o perfil atravessa a fronteira
 * servidor→cliente (é prop de componente de cliente) e o Next serializa essas props DENTRO do
 * HTML entregue. Declarar um tipo estreito não apaga um campo que veio no objeto; medição feita
 * no card mostrou e-mail, id de conta, hash e token no documento quando o objeto era repassado
 * inteiro. A evidência serve esses campos a mais na resposta da API e confere que nenhum deles
 * aparece no HTML de nenhuma das duas telas.
 */
export const metadata: Metadata = {
  title: 'Área do usuário',
  description:
    'Estado da sua conta SciPath: verificação de e-mail, o que a conta já pode fazer e os caminhos para editar o perfil público.',
  // Página privada não entra em índice (RNF024, CA011): o conteúdo depende da sessão de quem
  // pede, e indexá-lo publicaria (ou deixaria rastreável) um endereço que só serve à conta.
  robots: { index: false, follow: false },
};

export default async function PainelPage() {
  const leitura = await lerSessaoNoServidor();

  if (leitura.tipo === 'falha') {
    return (
      <div className="sc-page">
        <PageState
          kind="network-error"
          title="Não foi possível abrir a sua área agora"
          message="O servidor não respondeu à consulta de sessão, então não sabemos de quem é esta conta — e mostrar uma área com dados que não foram confirmados seria pior que não mostrar. Tente de novo; se continuar, tente em alguns minutos."
          action={
            <Link className="sc-btn sc-btn--ghost sc-btn--sm" href="/">
              Voltar ao início
            </Link>
          }
        />
      </div>
    );
  }

  if (leitura.tipo === 'anonimo') {
    // §15.3: sem sessão, o estado é "sessão necessária" — 401 com o destino preservado. O login
    // devolve a pessoa a ESTA tela (`?proximo=`), validado no servidor contra open redirect.
    return (
      <div className="sc-page">
        <PageState
          kind="session-expired"
          title="Entre para abrir a sua área"
          message="Esta área mostra o estado da sua conta e o que ela já pode fazer, então depende de quem está pedindo. Entre com e-mail e senha: você volta para cá depois do login. Se a sua sessão estava aberta, ela expirou por inatividade — a área é a mesma."
          loginDestination="/painel"
          details={
            <p className="sc-helper">
              Ainda não tem conta?{' '}
              <Link href="/cadastro">Criar conta</Link> leva menos de um minuto e libera navegar e
              editar o perfil na hora.
            </p>
          }
        />
      </div>
    );
  }

  const { estadoConta, perfil } = leitura.sessao;
  const contaAtiva = estadoConta === 'ATIVO';
  const nome = perfil.nomePublico;

  return (
    <div className="sc-page">
      <header className="sc-sec__head">
        <p className="sc-sec__num">
          <i>Conta</i> área do usuário
        </p>
        <h1 className="sc-h1">{nome}</h1>
        <p className="sc-sec__lead">
          Esta é a sua área: o estado da conta, o que ela já pode fazer na plataforma e os
          caminhos para editar o seu perfil público e publicar um material. A listagem dos seus
          próprios materiais — com editar, retirar e excluir — ainda não existe nesta versão, e
          por isso não há um caminho para ela aqui.
        </p>
      </header>

      <section className="mt-8" aria-labelledby="estado-da-conta">
        <h2 className="sc-subhead" id="estado-da-conta">
          Estado da conta
        </h2>

        {contaAtiva ? (
          <div className="sc-notice sc-notice--ok sc-notice--pagina" data-estado-conta="ATIVO">
            <Badge tone="green">e-mail confirmado</Badge>
            <h3 className="sc-notice__title">A sua conta está ativa</h3>
            <p className="sc-notice__msg">
              O e-mail cadastrado foi confirmado, e é isso que a plataforma verifica — nada além
              disso. A conta não tem selo de instituição, de titulação nem de revisão científica:
              esses conceitos não existem no SciPath (RN010, RN011).
            </p>
            <p className="sc-notice__msg">
              O seu perfil público está no endereço <span className="sc-mono">/u/{perfil.slug}</span>,
              com o que você declarou nele — o endereço responde a qualquer visitante, sem sessão.
            </p>
            <div className="sc-notice__actions">
              <Link className="sc-btn sc-btn--primary sc-btn--sm" href={rotaAutor(perfil.slug)}>
                Abrir o meu perfil público
              </Link>
            </div>
          </div>
        ) : (
          <div
            className="sc-notice sc-notice--atencao sc-notice--pagina"
            data-estado-conta="PENDENTE_VERIFICACAO"
          >
            <Badge tone="yellow">e-mail não confirmado</Badge>
            <h3 className="sc-notice__title">Falta confirmar o seu e-mail</h3>
            <p className="sc-notice__msg">
              A conta existe e <strong>não está bloqueada</strong>: você entra, navega, busca, abre
              materiais e edita o seu perfil agora mesmo. O que ainda não está liberado é{' '}
              <strong>publicar material</strong> — a publicação é a única operação que exige
              e-mail confirmado (ADR-0003 (b)).
            </p>
            <p className="sc-notice__msg">
              A confirmação chega por um link enviado ao endereço da conta. O endereço não é
              exibido nesta tela nem em nenhuma outra: ele não existe em nenhuma resposta do
              contrato que o frontend consome (RNF013). Se o link não chegou, peça outro abaixo —
              o contrato aceita um pedido a cada 60 segundos.
            </p>
            <ReenvioDeVerificacao />
          </div>
        )}
      </section>

      <section className="mt-10" aria-labelledby="acoes-principais">
        <h2 className="sc-subhead" id="acoes-principais">
          O que você pode fazer agora
        </h2>
        <ul className="sc-body mt-4 grid list-none gap-4 p-0 sm:grid-cols-2">
          <li className="sc-card sc-card--pad" data-acao="publicar">
            <h3 className="sc-card__title">Publicar material</h3>
            {contaAtiva ? (
              <p className="sc-card__copy">
                Envie um PDF com título, descrição, categoria, tipo e licença. A autoria é a sua
                conta e a licença é escolha sua — as duas ficam visíveis na página pública do
                material, e os dados são gravados junto do arquivo.
              </p>
            ) : (
              <p className="sc-card__copy">
                Publicar exige <strong>e-mail confirmado</strong> (ADR-0003 (b)) — é a única
                operação que exige. Com a conta pendente, a tela de publicação explica o motivo em
                vez de abrir o formulário; o caminho para reenviar o e-mail está no estado da
                conta, acima.
              </p>
            )}
            <Link
              className={`sc-btn sc-btn--sm ${contaAtiva ? 'sc-btn--primary' : 'sc-btn--ghost'}`}
              href="/painel/publicar"
            >
              Publicar material
            </Link>
          </li>
          <li className="sc-card sc-card--pad" data-acao="editar-perfil">
            <h3 className="sc-card__title">Editar o perfil público</h3>
            <p className="sc-card__copy">
              Nome público, identificador do endereço, biografia, instituição declarada, áreas de
              interesse e links — com prévia de como o perfil aparece para quem o abre.
            </p>
            <Link className="sc-btn sc-btn--ghost sc-btn--sm" href="/painel/perfil">
              Editar perfil
            </Link>
          </li>
          <li className="sc-card sc-card--pad" data-acao="explorar">
            <h3 className="sc-card__title">Explorar materiais publicados</h3>
            <p className="sc-card__copy">
              Buscar por palavra-chave, filtrar por categoria, tipo e ordem, e abrir o material com
              autoria e licença visíveis. Não precisa de verificação para isso.
            </p>
            <Link className="sc-btn sc-btn--ghost sc-btn--sm" href="/explorar">
              Explorar materiais
            </Link>
          </li>
        </ul>
        <p className="sc-helper sc-measure mt-4">
          A listagem dos seus próprios materiais — com editar, retirar e excluir — ainda não existe
          nesta versão da plataforma; por isso ela ainda não aparece como card nesta lista. Quando
          for entregue, passa a ter um.
        </p>
      </section>

      <section className="mt-10" aria-labelledby="dados-da-conta">
        <h2 className="sc-subhead" id="dados-da-conta">
          O que aparece nesta área
        </h2>
        <p className="sc-body sc-measure">
          Só o que é público no seu perfil e o estado da conta. E-mail de login, senha, hash,
          tokens e identificador interno de conta não são exibidos aqui — nem poderiam: eles não
          existem nas respostas que esta tela consome (RNF013). A autoridade sobre o que você pode
          fazer é o servidor, não esta tela: esconder um link nunca protegeu nada (RNF010).
        </p>
      </section>

      <div className="mt-10">
        <PreviaDoPerfil
          perfil={{
            slug: perfil.slug,
            nomePublico: perfil.nomePublico,
            biografia: perfil.biografia,
            afiliacaoDeclarada: perfil.afiliacaoDeclarada,
            areasInteresse: perfil.areasInteresse,
            linksExternos: perfil.linksExternos,
          }}
          publicamenteVisivel={contaAtiva}
        />
      </div>
    </div>
  );
}
