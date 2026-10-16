import type { ReactNode } from 'react';

/**
 * Badge `.sc-badge` do contrato visual (DS2). Sinal curto e textual ao lado de um
 * título, estado ou metadado. Nunca é o único portador da informação (RNF007):
 * o texto acompanha o tom.
 */

export type BadgeTone = 'neutro' | 'blue' | 'green' | 'red' | 'yellow' | 'purple' | 'dark';

const TOM: Record<BadgeTone, string> = {
  neutro: '',
  blue: 'sc-badge--blue',
  green: 'sc-badge--green',
  red: 'sc-badge--red',
  yellow: 'sc-badge--yellow',
  purple: 'sc-badge--purple',
  dark: 'sc-badge--dark',
};

export type BadgeProps = {
  tone?: BadgeTone;
  /** Ponto pulsante de "acontecendo agora". O ponto é decorativo: `aria-hidden`. */
  pulse?: boolean;
  className?: string;
  children: ReactNode;
};

export function Badge({ tone = 'neutro', pulse = false, className, children }: BadgeProps) {
  const classes = ['sc-badge', TOM[tone], pulse ? 'sc-badge--pulse' : '', className]
    .filter(Boolean)
    .join(' ');

  return (
    <span className={classes}>
      {pulse ? <i aria-hidden="true" /> : null}
      {children}
    </span>
  );
}
