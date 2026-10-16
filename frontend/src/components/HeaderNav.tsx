'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';

/**
 * Navegação do cabeçalho. Cliente só por causa de `usePathname`: o DS2 marca a
 * seção atual com `[aria-current="true"]` (sublinhado), e isso não pode depender
 * de cor sozinha — o estado é anunciado pelo atributo, não pelo pixel.
 * As rotas privadas e de descoberta da Etapa 2 entram nesta lista quando existirem.
 */
const LINKS = [{ href: '/', label: 'Início' }] as const;

export function HeaderNav() {
  const pathname = usePathname();

  return (
    <nav className="sc-header__nav" aria-label="Navegação principal">
      {LINKS.map((link) => (
        <Link
          key={link.href}
          className="sc-header__link"
          href={link.href}
          aria-current={pathname === link.href ? 'true' : undefined}
        >
          {link.label}
        </Link>
      ))}
    </nav>
  );
}