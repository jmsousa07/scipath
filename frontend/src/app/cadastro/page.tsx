import type { Metadata } from 'next';
import Link from 'next/link';
import { EstadosDaConsultaDeSessao } from '@/components/sessao/EstadosDaConsultaDeSessao';
import { FormularioCadastro } from '@/components/sessao/FormularioCadastro';

/**
 * `/cadastro` — criação de conta (RF008, CA004; PAGE_BLUEPRINTS.md §7).
 *
 * O aviso de que **publicar exige verificar o e-mail** aparece no HTML entregue, antes do
 * formulário, e não só depois do envio: a pessoa precisa saber o que a conta permite
 * antes de criar a senha. A tela também diz o que NÃO exige verificação (navegar, buscar,
 * editar o perfil), porque o ADR-0003 (b) separa as duas coisas e esconder a diferença
 * seria o "muro silencioso" que o ADR-0003 (h) proíbe.
 *
 * Rota pública e estática: o formulário e os campos principais saem no HTML, sem depender
 * de JavaScript (`?proximo=` seria inútil aqui — quem chega ao cadastro não veio de um
 * 401). O estado da sessão é perguntado depois da hidratação, como em `/entrar`.
 */
export const metadata: Metadata = {
  title: 'Criar conta',
  description:
    'Crie a sua conta SciPath com e-mail e senha para publicar materiais de estudo e divulgação científica com autoria e licença visíveis.',
  alternates: { canonical: '/cadastro' },
};

export default function CadastroPage() {
  return (
    <div className="sc-page">
      <div className="sc-sec__head">
        <p className="sc-sec__num">
          <i>Identidade</i> nova conta
        </p>
        <h1 className="sc-h1">Criar conta</h1>
        <p className="sc-sec__lead">
          Pedimos o mínimo: nome público, e-mail e senha. A conta nasce pronta para navegar e
          editar o perfil; para publicar material, falta um passo — a confirmação do e-mail.
        </p>
      </div>

      <section className="sc-notice sc-notice--info sc-notice--pagina" aria-labelledby="cadastro-aviso">
        <h2 className="sc-notice__title" id="cadastro-aviso">
          O que a conta libera na hora — e o que exige confirmar o e-mail
        </h2>
        <p className="sc-notice__msg">
          <strong>Na hora:</strong> navegar, buscar, abrir materiais, entrar na sessão e editar o seu
          perfil público. <strong>Depois de confirmar o e-mail:</strong> publicar material. O link de
          confirmação é enviado para o endereço que você cadastrar e vale 24 h; se ele não chegar, o
          site mantém um aviso com o reenvio em todas as páginas — a pendência não fica escondida.
        </p>
      </section>

      <EstadosDaConsultaDeSessao />

      <FormularioCadastro />

      <p className="sc-helper mt-6">
        Ao criar a conta você declara que os dados são seus e concorda com os{' '}
        <Link href="/termos">termos de uso</Link> e com a{' '}
        <Link href="/privacidade">política de privacidade</Link>.
      </p>
    </div>
  );
}
