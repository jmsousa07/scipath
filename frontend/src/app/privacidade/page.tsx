import type { Metadata } from 'next';

/**
 * Política de privacidade — dependência de lançamento (ADR-0007, DRS §13.3).
 *
 * Descreve apenas o que a modelagem já define (docs/01-arquitetura.md §2) e o que
 * ainda precisa ser decidido — sem prometer retenção, exclusão ou prazos que ninguém
 * decidiu.
 */
export const metadata: Metadata = {
  title: 'Política de privacidade',
  description:
    'Quais dados a plataforma SciPath trata, o que aparece publicamente, o que nunca é exposto e o que ainda falta decidir.',
  alternates: { canonical: '/privacidade' },
};

export default function PrivacidadePage() {
  return (
    <article className="sc-page">
      <div className="sc-sec__head">
        <p className="sc-sec__num">
          <i>Documento</i> dependência de lançamento
        </p>
        <h1 className="sc-h1">Política de privacidade</h1>
        <p className="sc-sec__lead">
          Quais dados a plataforma trata, o que aparece publicamente e o que nunca é exposto.
        </p>
      </div>

      <div className="sc-measure space-y-4">
        <p className="sc-body">
          Este documento está <strong>em elaboração</strong> e é dependência de lançamento (ADR-0007).
          O que segue reflete a modelagem já definida em <span className="sc-mono">docs/01-arquitetura.md</span>;
          o que ainda não foi decidido aparece nomeado no fim.
        </p>

        <h2 className="sc-subhead">Dados que a plataforma trata</h2>
        <ul className="sc-body list-disc space-y-2 pl-5">
          <li>
            <strong>Conta:</strong> e-mail (único, normalizado) e senha. A senha é guardada apenas como
            hash <span className="sc-mono">bcrypt</span> — nunca em texto legível.
          </li>
          <li>
            <strong>Perfil:</strong> nome público, identificador público, biografia, afiliação
            declarada, áreas de interesse e links externos.
          </li>
          <li>
            <strong>Material:</strong> metadados (título, descrição, categoria, tipo, licença), estado
            de publicação e datas.
          </li>
          <li>
            <strong>Arquivo:</strong> nome original saneado, tipo detectado, tamanho, checksum e chave
            interna gerada pelo sistema.
          </li>
        </ul>

        <h2 className="sc-subhead">O que aparece publicamente</h2>
        <p className="sc-body">
          Nome público, biografia, áreas de interesse, links externos, afiliação declarada e os
          materiais publicados com sua licença. A afiliação é exibida como <strong>declaração</strong> da
          pessoa, nunca como verificação da plataforma (RN011, CA012).
        </p>
        <p className="sc-body">
          E-mail, credenciais, tokens e a chave interna do arquivo <strong>nunca</strong> aparecem em
          resposta pública — a resposta pública é montada a partir de um recorte próprio, não da
          entidade de persistência (RNF013).
        </p>

        <h2 className="sc-subhead">Verificação de e-mail</h2>
        <p className="sc-body">
          Verificar o e-mail é exigência para <strong>publicar</strong>. Navegar, criar conta e editar o
          perfil não exigem verificação (ADR-0003). O token de verificação é de uso único, guardado
          como hash, com validade e nunca registrado em log.
        </p>

        <h2 className="sc-subhead">Backup e recuperação</h2>
        <p className="sc-body">
          Está proposta uma rotina de cópia diária do banco e do diretório de arquivos, com retenção de
          7 dias e teste de restauração documentado (ADR-0007, item 3). Enquanto o teste de restauração
          não for executado, a rotina não conta como cumprida — hoje ela ainda não existe.
        </p>

        <h2 className="sc-subhead">O que falta neste documento</h2>
        <ul className="sc-body list-disc space-y-2 pl-5">
          <li>Prazo de retenção de dados de conta e de material excluído.</li>
          <li>Procedimento de exclusão a pedido e o que sobrevive à exclusão (metadados de denúncia, por exemplo).</li>
          <li>Base legal e encarregado de dados, quando houver lançamento público.</li>
          <li>Rotina de backup e restauração efetivamente executada e testada.</li>
        </ul>
      </div>
    </article>
  );
}