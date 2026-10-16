import type { ButtonHTMLAttributes, ReactNode } from 'react';

/**
 * Botão do sistema — envolve `.sc-btn` do contrato visual (extraído do DS2 em
 * `design-system.css`). Não redefine aparência: escolhe a variante.
 *
 * Acessibilidade: o foco visível vem de `base.css` (`:focus-visible`); o alvo
 * mínimo de 44 px vem de `layout.css`. Nada disso é opcional (RNF006, RNF008).
 */

export type ButtonVariant = 'primary' | 'blue' | 'ghost' | 'text';
export type ButtonSize = 'sm' | 'md' | 'lg';

const VARIANTE: Record<ButtonVariant, string> = {
  primary: 'sc-btn--primary',
  blue: 'sc-btn--blue',
  ghost: 'sc-btn--ghost',
  text: 'sc-btn--text',
};

const TAMANHO: Record<ButtonSize, string> = {
  sm: 'sc-btn--sm',
  md: '',
  lg: 'sc-btn--lg',
};

export type ButtonProps = Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'children'> & {
  variant?: ButtonVariant;
  size?: ButtonSize;
  /**
   * Operação em andamento: o botão fica `disabled` e anuncia `aria-busy`, o que
   * impede o envio duplo (DRS §15.3, "envio em andamento"). O rótulo permanece —
   * quem usa leitor de tela ouve "Indisponível" pelo próprio `disabled`.
   */
  loading?: boolean;
  children: ReactNode;
};

export function Button({
  variant = 'primary',
  size = 'md',
  loading = false,
  disabled = false,
  className,
  children,
  type = 'button',
  ...rest
}: ButtonProps) {
  const desabilitado = disabled || loading;
  const classes = [
    'sc-btn',
    VARIANTE[variant],
    TAMANHO[size],
    desabilitado ? 'sc-btn--dis' : '',
    className,
  ]
    .filter(Boolean)
    .join(' ');

  return (
    <button
      {...rest}
      type={type}
      className={classes}
      disabled={desabilitado}
      aria-busy={loading || undefined}
    >
      {loading ? <i className="sc-spin" aria-hidden="true" /> : null}
      {children}
    </button>
  );
}

/**
 * Botão de envio de formulário: é o `Button` com `type="submit"` e a regra de
 * envio único explícita na API. `pending` deve ser `true` do primeiro clique até
 * a resposta do servidor — enquanto isso o botão fica desabilitado.
 */
export function SubmitButton({ pending, children, ...rest }: Omit<ButtonProps, 'type' | 'loading'> & { pending: boolean }) {
  return (
    <Button {...rest} type="submit" loading={pending}>
      {children}
    </Button>
  );
}
