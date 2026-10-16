import type { Metadata } from 'next';

/**
 * Termos de uso — dependência de lançamento (ADR-0007, DRS §13.3), não tela decorativa.
 *
 * Conteúdo honesto: só afirma o que já está decidido em ADR/DRS e marca explicitamente
 * o que ainda não está. Nada de cláusula jurídica inventada por agente.
 */
export const metadata: Metadata = {
  title: 'Termos de uso',
  description:
    'Regras de uso da plataforma SciPath: o que é publicado, quem pode publicar, licença, retirada de material e canal de denúncia.',
  alternates: { canonical: '/termos' },
};

export default function TermosPage() {
  return (
    <article className="sc-page">
      <div className="sc-sec__head">
        <p className="sc-sec__num">
          <i>Documento</i> dependência de lançamento
        </p>
        <h1 className="sc-h1">Termos de uso</h1>
        <p className="sc-sec__lead">
          Regras de uso da plataforma: o que pode ser publicado, quem pode publicar, como a licença
          aparece e como um material é retirado.
        </p>
      </div>

      <div className="sc-measure space-y-4">
        <p className="sc-body">
          Este documento está <strong>em elaboração</strong>. O SciPath não vai ao ar antes de os
          termos estarem concluídos: eles são item de lançamento, listados no ADR-0007 e no DRS §13.3.
          O que está escrito abaixo é o que as decisões registradas já permitem afirmar; o que falta
          está na última seção, nomeado.
        </p>

        <h2 className="sc-subhead">Uso da plataforma</h2>
        <p className="sc-body">
          O SciPath é uma plataforma pública de divulgação científica. Visitantes navegam sem conta.
          Publicar e manter materiais exige conta com e-mail e senha, e a publicação exige e-mail
          verificado (ADR-0003) — navegar e editar o perfil não exigem.
        </p>

        <h2 className="sc-subhead">Material publicado</h2>
        <ul className="sc-body list-disc space-y-2 pl-5">
          <li>Quem publica declara ter o direito de compartilhar o arquivo.</li>
          <li>
            A licença escolhida no momento da publicação fica visível na página do material, com o que
            ela permite e não permite.
          </li>
          <li>
            O material precisa de título, descrição, categoria, tipo, licença e arquivo PDF válido (DRS
            §7.3).
          </li>
          <li>
            A plataforma <strong>não</strong> certifica, revisa nem endossa o conteúdo: não há selo de
            verificação, revisão por pares ou aprovação institucional (CA012).
          </li>
        </ul>

        <h2 className="sc-subhead">Retirada e remoção</h2>
        <p className="sc-body">
          Quem publicou pode retirar o material: um material retirado deixa de ser listado, buscado e
          servido. Conteúdo irregular é retirado enquanto a contestação é avaliada (ADR-0007).
        </p>

        <h2 className="sc-subhead">Denúncia e contato</h2>
        <p className="sc-body">
          O canal de denúncia de direitos autorais e o endereço operacional de contato ainda{' '}
          <strong>não existem</strong> — é pendência registrada (ADR-0007, item 5). Enquanto não
          houver endereço publicado, não há canal de denúncia: isso bloqueia o lançamento, por decisão
          explícita.
        </p>

        <h2 className="sc-subhead">O que falta neste documento</h2>
        <ul className="sc-body list-disc space-y-2 pl-5">
          <li>Texto jurídico completo e revisão humana antes do lançamento.</li>
          <li>Endereço operacional de contato e canal de denúncia.</li>
          <li>Procedimento de remoção por violação de direitos autorais, com prazos.</li>
          <li>Política de exclusão e retenção de material e conta.</li>
        </ul>
      </div>
    </article>
  );
}