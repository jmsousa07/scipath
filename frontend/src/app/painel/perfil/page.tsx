import type { Metadata } from 'next';
import Link from 'next/link';
import { FormularioPerfil } from '@/components/painel/FormularioPerfil';
import { PageState } from '@/components/ui/PageState';
import { rotaAutor } from '@/lib/rotas';
import { lerSessaoNoServidor } from '@/lib/sessao/servidor';

/**
 * `/painel/perfil` — editar o próprio perfil (RF011, DRS §8.2.4 e §15.2;
 * PAGE_BLUEPRINTS.md §10).
 *
 * Os campos são os do RF011, um a um: nome público, slug, biografia curta,
 * instituição/afiliação declarada (opcional), áreas de interesse (opcional) e site pessoal ou
 * perfil acadêmico (opcional). Os opcionais podem ficar vazios sem quebrar nada — o contrato
 * trata `''`/`[]` como "não informado" e o perfil continua válido (RF011, critério 3).
 *
 * Servidor primeiro: a página lê a sessão desta requisição e entrega no HTML o formulário
 * PREENCHIDO com o perfil atual (`lerSessaoNoServidor`) mais a prévia de como o perfil público
 * aparece. Não é detalhe de desempenho — é o que faz esta tela funcionar sem JavaScript e o que
 * impede uma página privada de ser servida do cache (a leitura usa `cookies()`, então a rota é
 * dinâmica por construção, e o perfil de uma conta nunca é a resposta entregue a outra).
 *
 * Os três desfechos da leitura da sessão, e o estado do §15.3 de cada um:
 *   - sem sessão (`anonimo`)      → `sessão expirada` com o destino preservado: o login devolve a
 *                                   pessoa a ESTA tela (`?proximo=/painel/perfil`, validado no
 *                                   servidor contra open redirect);
 *   - sessão válida               → `sucesso`: o conteúdo da página;
 *   - API inalcançável (`falha`)  → `erro de rede/servidor` com o caminho de volta — e nada de
 *                                   formulário vazio se passando por perfil real.
 *
 * `não autorizado` (403, §15.3) não nasce de um GET desta tela: `GET /sessoes/atual` não tem 403
 * no contrato. Ele aparece onde a escrita pode produzi-lo — `PUT /perfil/atual` sem
 * `X-CSRF-Token` ou com a conta suspensa —, e o formulário o mostra no resumo com o `detail` do
 * contrato (`lib/sessao/erros.ts`). Inventar um 403 de página aqui seria fabricar um estado.
 *
 * RN011/CA012, o ponto que o card pede explícito na tela: a instituição informada é
 * **autodeclaração**. O aviso antes do formulário diz isso, o rótulo do campo diz isso, o valor
 * digitado é atribuído a quem o escreveu ("Declarado por …") e não existe — em lugar nenhum
 * desta tela — selo, medalha, "verificado" ou nota de instituição.
 *
 * Dado privado (RNF013): o formulário manda só os campos públicos, sem identificador de conta ou
 * de perfil (a propriedade vem da sessão — ADR-0003 (j), CA007), e nada de e-mail de login,
 * senha, hash ou token é renderizado — esses campos não existem nos schemas que a tela consome.
 */
export const metadata: Metadata = {
  title: 'Editar perfil',
  description:
    'Edite os campos públicos do seu perfil SciPath: nome público, identificador do endereço, biografia, instituição declarada, áreas de interesse e links.',
  // Área privada fora de índice (RNF024, CA011) — o conteúdo é o perfil de quem está autenticado.
  robots: { index: false, follow: false },
};

export default async function EditarPerfilPage() {
  const leitura = await lerSessaoNoServidor();

  if (leitura.tipo === 'falha') {
    return (
      <div className="sc-page">
        <PageState
          kind="network-error"
          title="Não foi possível carregar o seu perfil agora"
          message="O servidor não respondeu à consulta de sessão, então não sabemos de quem é este perfil — e abrir um formulário de edição sem os valores atuais levaria você a sobrescrever dados que não foram lidos. Tente de novo em instantes."
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
          title="Entre para editar o seu perfil"
          message="O perfil que esta tela edita é o da conta autenticada — não existe campo de identificador no formulário, porque a propriedade vem da sessão. Entre com e-mail e senha: depois do login você volta direto para cá, com os seus dados carregados."
          loginDestination="/painel/perfil"
          details={
            <p className="sc-helper">
              Se você não tem conta, <Link href="/cadastro">criar conta</Link> já abre a sessão e
              libera esta tela na hora.
            </p>
          }
        />
      </div>
    );
  }

  const { estadoConta, perfil } = leitura.sessao;
  const contaAtiva = estadoConta === 'ATIVO';

  return (
    <div className="sc-page">
      <header className="sc-sec__head">
        <p className="sc-sec__num">
          <i>Conta</i> editar perfil
        </p>
        <h1 className="sc-h1">Editar perfil</h1>
        <p className="sc-sec__lead">
          Aqui você altera o que qualquer visitante lê no seu perfil público: como você é chamado,
          o endereço do perfil, a sua biografia e o que você escolher declarar sobre a sua atuação.
          Salvar substitui o conjunto dos campos públicos de uma vez.
        </p>
      </header>

      <section
        className="sc-notice sc-notice--info sc-notice--pagina"
        aria-labelledby="aviso-autodeclaracao"
        data-aviso="afiliacao-autodeclarada"
      >
        <h2 className="sc-notice__title" id="aviso-autodeclaracao">
          Instituição e afiliação são autodeclaradas
        </h2>
        <p className="sc-notice__msg">
          O campo de instituição é uma <strong>declaração sua</strong>: o que você escrever aparece
          no perfil com o seu nome atribuído à frase (<span className="sc-mono">Declarado por …</span>).
          A plataforma <strong>não verifica vínculo institucional, titulação nem identidade
          acadêmica</strong>, e não existe selo, medalha ou marca de &ldquo;verificado&rdquo; em
          nenhuma tela do SciPath (RN011, CA012). O mesmo vale para os links: são texto declarado,
          sem checagem no MVP.
        </p>
        <p className="sc-notice__msg">
          O identificador do endereço (o <em>slug</em>) é o único campo com regra de plataforma: ele
          precisa ser único — se já pertencer a outro perfil, o servidor recusa a gravação e o
          campo mostra o motivo (RN009).
        </p>
      </section>

      <FormularioPerfil perfilInicial={perfil} estadoConta={estadoConta} />

      <p className="sc-helper sc-measure mt-8">
        {contaAtiva ? (
          <>
            A conta está com o e-mail confirmado, então o seu perfil público em{' '}
            <Link href={rotaAutor(perfil.slug)}>/u/{perfil.slug}</Link> já mostra o resultado desta
            edição para qualquer visitante.
          </>
        ) : (
          <>
            A conta ainda está com o e-mail <strong>não confirmado</strong>: a edição do perfil
            funciona normalmente (não exige verificação), mas o endereço público{' '}
            <span className="sc-mono">/u/{perfil.slug}</span> só passa a responder a visitantes
            quando a confirmação for concluída. O caminho para reenviar o link está na{' '}
            <Link href="/painel">área do usuário</Link>.
          </>
        )}
      </p>

      <p className="sc-helper sc-measure mt-3">
        Esta página não mostra e-mail de login, senha, hash nem token: esses dados não existem nas
        respostas do contrato que a tela consome (RNF013). A autoridade sobre o que pode ser salvo é
        o servidor — a tela apenas informa o que ele respondeu (RNF010).
      </p>

      <p className="mt-6">
        <Link className="sc-btn sc-btn--ghost sc-btn--sm" href="/painel">
          Voltar à área do usuário
        </Link>
      </p>
    </div>
  );
}
