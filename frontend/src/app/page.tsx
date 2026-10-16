import type { Metadata } from 'next';
import Link from 'next/link';

/**
 * Página inicial de fundação (Etapa 1).
 *
 * Ela NÃO é a página de descoberta — essa é da Etapa 2 e depende de dados. Aqui o
 * objetivo é provar o layout global sem inventar acervo: nenhuma contagem, categoria
 * ou material fictício aparece (DRS §20.1, PAGE_BLUEPRINTS.md).
 */
export const metadata: Metadata = {
  alternates: { canonical: '/' },
};

export default function HomePage() {
  return (
    <div className="sc-page">
      <div className="sc-sec__head">
        <p className="sc-sec__num">
          <i>Etapa 1</i> base do produto
        </p>
        <h1 className="sc-h1">Descobrir, compreender e compartilhar conhecimento científico</h1>
        <p className="sc-sec__lead">
          O SciPath reúne materiais produzidos por estudantes, professores e pesquisadores — notas de
          aula, slides, artigos e guias de estudo — e os torna encontráveis por busca e filtros
          explícitos, com autoria e licença sempre visíveis.
        </p>
      </div>

      <div className="sc-measure mt-4 space-y-4">
        <h2 className="sc-subhead">O que está publicado hoje</h2>
        <p className="sc-body">
          Esta é a base do produto, não a descoberta. O que existe agora: o contrato visual aplicado
          por tokens, as fontes servidas do próprio domínio (sem CDN), o cabeçalho com busca e o
          rodapé responsivos, e as duas páginas de fundação exigidas para o lançamento.
        </p>
        <ul className="sc-body list-disc space-y-2 pl-5">
          <li>Tokens e escala tipográfica extraídos do design system do projeto.</li>
          <li>Fontes locais com <span className="sc-mono">font-display: swap</span>.</li>
          <li>
            <Link href="/termos">Termos de uso</Link> e <Link href="/privacidade">Política de privacidade</Link>{' '}
            publicados — dependências de lançamento (ADR-0007).
          </li>
        </ul>

        <h2 className="sc-subhead">O que ainda não existe</h2>
        <p className="sc-body">
          A busca, a listagem, os filtros, o perfil público e a publicação de materiais. Até lá, esta
          página não simula acervo: nenhum número, nome ou material é exibido como se fosse real
          (<span className="sc-mono">DRS §20.1</span>). O envio da busca do cabeçalho aponta para a
          rota canônica da descoberta, <span className="sc-mono">/explorar</span>, que passa a
          responder quando a Etapa 2 chegar.
        </p>
      </div>
    </div>
  );
}