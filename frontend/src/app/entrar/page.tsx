import type { Metadata } from 'next';
import Link from 'next/link';
import { EstadosDaConsultaDeSessao } from '@/components/sessao/EstadosDaConsultaDeSessao';
import { FormularioEntrar } from '@/components/sessao/FormularioEntrar';
import { PageState } from '@/components/ui/PageState';
import { caminhoInterno } from '@/lib/sessao/destino';

/**
 * `/entrar` — login (RF009, CA004; PAGE_BLUEPRINTS.md §6).
 *
 * Dois parâmetros de URL, os dois lidos no SERVIDOR:
 *   - `proximo`  destino pedido antes do 401, preservado pelo desvio da §15.3. Ele é
 *                validado por `caminhoInterno` (só caminho interno: `?proximo=` livre
 *                viraria redirecionador aberto) e vai como prop para o formulário;
 *   - `motivo=sessao-expirada`  o aviso de sessão expirada sai no HTML entregue, antes de
 *                o JavaScript existir.
 *
 * A rota é renderizada por requisição por causa desses parâmetros — e isso é o certo
 * aqui: o formulário, os rótulos e o aviso de sessão expirada saem prontos no HTML (o
 * critério do card pede o formulário verificável no HTML entregue, sem depender de
 * JavaScript), e o estado da SESSÃO continua sendo perguntado depois da hidratação — em
 * `EstadosDaConsultaDeSessao` (carregando / servidor fora do ar) e em `JaAutenticado`, que
 * toma o lugar do formulário quando a resposta diz que a sessão já está aberta.
 *
 * Nenhuma credencial em URL, em log ou em mensagem (RNF009): o envio é POST no corpo, pelo
 * proxy `/api` da mesma origem, e a recusa mostra a mensagem genérica que o contrato
 * permite (RNF013 — o login não revela se o e-mail existe).
 */
export const metadata: Metadata = {
  title: 'Entrar',
  description:
    'Entre na sua conta SciPath com e-mail e senha para publicar materiais e editar o seu perfil público.',
  alternates: { canonical: '/entrar' },
};

type Props = {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
};

export default async function EntrarPage({ searchParams }: Props) {
  const brutos = await searchParams;
  const proximo = caminhoInterno(brutos.proximo);
  const sessaoExpirada = brutos.motivo === 'sessao-expirada';

  return (
    <div className="sc-page">
      <div className="sc-sec__head">
        <p className="sc-sec__num">
          <i>Identidade</i> sessão
        </p>
        <h1 className="sc-h1">Entrar</h1>
        <p className="sc-sec__lead">
          A conta que assina os materiais que você publica. Navegar, buscar e abrir materiais não
          precisa de conta; publicar e editar o seu perfil, sim.
        </p>
      </div>

      <EstadosDaConsultaDeSessao />

      {sessaoExpirada ? (
        <PageState
          kind="session-expired"
          title="Sua sessão expirou"
          message="Por segurança, o acesso anterior foi encerrado. Entre novamente para continuar — o destino que você estava abrindo continua guardado e volta a abrir depois do login."
          details={
            proximo ? (
              <p className="sc-helper">
                Destino guardado: <span className="sc-mono">{proximo}</span>
              </p>
            ) : null
          }
          action={
            <Link className="sc-btn sc-btn--ghost sc-btn--sm" href="/">
              Voltar ao início
            </Link>
          }
        />
      ) : null}

      <FormularioEntrar {...(proximo ? { proximo } : {})} />

      <p className="sc-helper mt-6">
        Esqueceu a senha? A recuperação de acesso é um requisito importante ainda não entregue —
        enquanto ela não existe, não há caminho de redefinição nesta tela.{' '}
        <Link href="/termos">Veja os termos de uso</Link> para saber como os seus dados são
        tratados.
      </p>
    </div>
  );
}
