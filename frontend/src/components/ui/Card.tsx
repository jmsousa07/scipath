import type { ElementType, ReactNode } from 'react';

/**
 * Card `.sc-card` do contrato visual (DS2): superfície com borda, raio e sombra,
 * com espaço opcional para etiqueta, título e resumo.
 *
 * `as` existe para semântica (RNF006): card de material é `article`, bloco de
 * ajuda é `section`, item de lista é `li`. O nome do componente não deve decidir
 * a estrutura do documento.
 */

export type CardProps = {
  as?: ElementType;
  eyebrow?: ReactNode;
  title?: ReactNode;
  copy?: ReactNode;
  /** `.sc-card--pad`: mais respiro interno, para card de destaque. */
  pad?: boolean;
  /** `.sc-card--hoverable`: só quando o card inteiro é clicável. */
  hoverable?: boolean;
  className?: string;
  children?: ReactNode;
};

export function Card({
  as: Tag = 'div',
  eyebrow,
  title,
  copy,
  pad = false,
  hoverable = false,
  className,
  children,
}: CardProps) {
  const classes = ['sc-card', pad ? 'sc-card--pad' : '', hoverable ? 'sc-card--hoverable' : '', className]
    .filter(Boolean)
    .join(' ');

  return (
    <Tag className={classes}>
      {eyebrow ? <p className="sc-card__eyebrow">{eyebrow}</p> : null}
      {title ? <h3 className="sc-card__title">{title}</h3> : null}
      {copy ? <p className="sc-card__copy">{copy}</p> : null}
      {children}
    </Tag>
  );
}
