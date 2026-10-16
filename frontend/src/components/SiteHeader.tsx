import Link from 'next/link';
import { BrandMark } from '@/components/BrandMark';
import { HeaderNav } from '@/components/HeaderNav';
import { HeaderScrollState } from '@/components/HeaderScrollState';
import { SearchForm } from '@/components/SearchForm';

const HEADER_ID = 'sc-header';

/**
 * Cabeçalho global, conforme `.sc-header` do DS2: fixo, translúcido e com a busca
 * à direita. O DS2 lista seções do próprio design system; aqui a navegação é a do
 * produto (e só aponta para rotas que existem — nada de link morto).
 */
export function SiteHeader() {
  return (
    <header className="sc-header" id={HEADER_ID}>
      <HeaderScrollState targetId={HEADER_ID} />

      <Link className="sc-header__brand" href="/" aria-label="SciPath — página inicial">
        <BrandMark />
        <span className="sc-header__word">
          Sci<i>Path</i>
        </span>
      </Link>

      <HeaderNav />

      <span className="sc-header__spacer" />

      <div className="sc-header__search">
        <SearchForm />
      </div>
    </header>
  );
}