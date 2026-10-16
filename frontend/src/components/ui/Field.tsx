import type { InputHTMLAttributes, ReactNode, TextareaHTMLAttributes } from 'react';

/**
 * Campos de formulário sobre o contrato visual (`.sc-field` do DS2).
 *
 * Garantias da primitiva (RNF006 — acessibilidade entra no aceite, não é polimento):
 *   1. `<label for>` sempre associado ao controle (`htmlFor={id}`);
 *   2. a mensagem de erro é associada ao CONTROLE por `aria-describedby` + `aria-invalid`,
 *      não apenas colocada perto dele;
 *   3. o obrigatório é dito em texto (não só um asterisco): o `*` é decorativo e um
 *      texto só para leitor de tela diz "(campo obrigatório)";
 *   4. o erro é anunciado (`role="alert"`) quando aparece;
 *   5. o foco visível do controle vem de `layout.css` (o DS2 zera o `outline` do input
 *      dentro da caixa e a camada de componentes venceria a de fundação).
 *
 * `id` é obrigatório e explícito de propósito: o HTML tem de ser estável e único no
 * documento — nada de id gerado no cliente, o formulário precisa funcionar sem JS.
 */

type CampoComum = {
  /** id do controle; também define os ids de ajuda (`-ajuda`) e de erro (`-erro`). */
  id: string;
  label: ReactNode;
  name?: string;
  hint?: string;
  error?: string;
  required?: boolean;
  className?: string;
};

type RenderControle = (aria: { id: string; describedBy?: string }) => ReactNode;

function descritoPor(id: string, hint?: string, error?: string) {
  return [hint ? `${id}-ajuda` : null, error ? `${id}-erro` : null].filter(Boolean).join(' ') || undefined;
}

function Campo({
  id,
  label,
  hint,
  error,
  required = false,
  className,
  multiline = false,
  children: render,
}: Pick<CampoComum, 'id' | 'label' | 'hint' | 'error' | 'required' | 'className'> & {
  multiline?: boolean;
  children: RenderControle;
}) {
  const classes = [
    'sc-field',
    multiline ? 'sc-field--multiline' : '',
    error ? 'sc-field--error' : '',
    className,
  ]
    .filter(Boolean)
    .join(' ');

  return (
    <div className={classes}>
      <label className="sc-field__label" htmlFor={id}>
        {label}
        {required ? (
          <>
            <span aria-hidden="true"> *</span>
            <span className="sc-visually-hidden"> (campo obrigatório)</span>
          </>
        ) : null}
      </label>
      <div className="sc-field__box">{render({ id, describedBy: descritoPor(id, hint, error) })}</div>
      {hint ? (
        <p className="sc-field__msg" id={`${id}-ajuda`}>
          {hint}
        </p>
      ) : null}
      {error ? (
        <p className="sc-field__msg" id={`${id}-erro`} role="alert">
          {error}
        </p>
      ) : null}
    </div>
  );
}

export type TextFieldProps = CampoComum & Omit<InputHTMLAttributes<HTMLInputElement>, 'id' | 'name' | 'className'>;

/** Campo de texto de uma linha (`input`). */
export function TextField({
  id,
  label,
  name,
  hint,
  error,
  required = false,
  className,
  type = 'text',
  ...rest
}: TextFieldProps) {
  return (
    <Campo id={id} label={label} hint={hint} error={error} required={required} className={className}>
      {({ describedBy }) => (
        <input
          {...rest}
          id={id}
          name={name ?? id}
          type={type}
          required={required}
          aria-invalid={error ? true : undefined}
          aria-describedby={describedBy}
        />
      )}
    </Campo>
  );
}

export type TextAreaFieldProps = CampoComum &
  Omit<TextareaHTMLAttributes<HTMLTextAreaElement>, 'id' | 'name' | 'className'>;

/** Campo de texto de várias linhas (`textarea`) — descrição, resumo, bio. */
export function TextAreaField({
  id,
  label,
  name,
  hint,
  error,
  required = false,
  className,
  rows = 4,
  ...rest
}: TextAreaFieldProps) {
  return (
    <Campo id={id} label={label} hint={hint} error={error} required={required} className={className} multiline>
      {({ describedBy }) => (
        <textarea
          {...rest}
          id={id}
          name={name ?? id}
          rows={rows}
          required={required}
          aria-invalid={error ? true : undefined}
          aria-describedby={describedBy}
        />
      )}
    </Campo>
  );
}

export type FileFieldProps = CampoComum &
  Omit<InputHTMLAttributes<HTMLInputElement>, 'id' | 'name' | 'className' | 'type'>;

/**
 * Campo de arquivo (PDF — ADR-0005). O estado "arquivo acima do limite ou em formato
 * inválido" (DRS §15.3) chega por `error`; os textos acionáveis saem de
 * `mensagemArquivo()`, que diz o limite e o que fazer — nunca "arquivo inválido".
 *
 * O limite é passado em texto (`limite`) porque o valor configurável mora no backend
 * (ADR-0005): a tela exibe o limite, não o inventa.
 */
export function FileField({
  id,
  label,
  name,
  hint,
  error,
  required = false,
  className,
  accept = 'application/pdf',
  ...rest
}: FileFieldProps) {
  return (
    <Campo id={id} label={label} hint={hint} error={error} required={required} className={className}>
      {({ describedBy }) => (
        <input
          {...rest}
          id={id}
          name={name ?? id}
          type="file"
          accept={accept}
          required={required}
          aria-invalid={error ? true : undefined}
          aria-describedby={describedBy}
        />
      )}
    </Campo>
  );
}
