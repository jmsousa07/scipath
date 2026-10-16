import type { ReactNode } from 'react';

/**
 * Chip `.sc-chip` do contrato visual (DS2) — etiqueta solta, com ponto colorido
 * opcional, para tipo de material, categoria ou licença. Como o badge, o chip diz
 * o que é por TEXTO; o ponto é reforço visual (RNF007).
 */

export type ChipTone = 'neutro' | 'green' | 'red' | 'yellow';

const TOM: Record<ChipTone, string> = {
  neutro: '',
  green: 'sc-chip--green',
  red: 'sc-chip--red',
  yellow: 'sc-chip--yellow',
};

export type ChipProps = {
  tone?: ChipTone;
  /** Ponto colorido à esquerda. Decorativo por definição (`aria-hidden`). */
  dot?: boolean;
  className?: string;
  children: ReactNode;
};

export function Chip({ tone = 'neutro', dot = true, className, children }: ChipProps) {
  const classes = ['sc-chip', TOM[tone], className].filter(Boolean).join(' ');

  return (
    <span className={classes}>
      {dot ? <i className="sc-chip__dot" aria-hidden="true" /> : null}
      {children}
    </span>
  );
}
