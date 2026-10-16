import Link from 'next/link';
import { BrandMark } from '@/components/BrandMark';

/**
 * Rodapé global conforme `.sc-footer` do DS2 (fundo escuro, quatro blocos, linha de
 * créditos). O DS2 usa as colunas para seções do próprio documento de design; aqui
 * elas trazem **só o que existe**: as páginas publicadas e o estado real do projeto.
 * Coluna sem link é coluna com texto — inventar link de tela que não existe seria
 * pior do que ter uma coluna mais pobre.
 */
export function SiteFooter() {
  return (
    <footer className="sc-footer">
      <div className="sc-footer__inner">
        <div>
          <div className="sc-footer__brand">
            <BrandMark strokeToken="var(--sc-surface)" />
            SciPath
          </div>
          <p className="sc-footer__copy">
            Plataforma pública de divulgação científica: estudantes, professores e pesquisadores
            publicam materiais — notas de aula, slides, artigos e guias de estudo — e visitantes os
            encontram por busca e filtros explícitos, com autoria e licença visíveis.
          </p>
        </div>

        <div>
          <div className="sc-footer__title">Navegação</div>
          <Link className="sc-footer__link" href="/">
            Início
          </Link>
        </div>

        <div>
          <div className="sc-footer__title">Documentos legais</div>
          <Link className="sc-footer__link" href="/termos">
            Termos de uso
          </Link>
          <Link className="sc-footer__link" href="/privacidade">
            Política de privacidade
          </Link>
        </div>

        <div>
          <div className="sc-footer__title">Estado do projeto</div>
          <p className="sc-footer__copy mt-0">
            Etapa 1 — base do produto. A descoberta de materiais chega na Etapa 2; nenhuma tela
            publicada exibe dado fictício (<span className="sc-mono">DRS §20.1</span>).
          </p>
        </div>
      </div>

      <div className="sc-footer__bottom">
        <span>© 2026 SciPath · plataforma pública de divulgação científica</span>
        <span>MVP em desenvolvimento — não é lançamento público (ADR-0007)</span>
      </div>
    </footer>
  );
}