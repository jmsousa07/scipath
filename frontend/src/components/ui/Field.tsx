import type {
  InputHTMLAttributes,
  ReactNode,
  SelectHTMLAttributes,
  TextareaHTMLAttributes,
} from 'react';

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

export type SelectFieldProps = CampoComum &
  Omit<SelectHTMLAttributes<HTMLSelectElement>, 'id' | 'name' | 'className' | 'children'> & {
    /** As `<option>` do controle — vêm dos dados, nunca de lista escrita na tela (RF018). */
    children: ReactNode;
  };

/**
 * Campo de seleção (`select`) sobre `.sc-field` do DS2 — o primitivo que a publicação
 * (card E4.6) precisou para categoria, tipo de material e licença.
 *
 * Por que virou primitivo agora, e não antes: o filtro de `/explorar` (card E2.8) foi o
 * primeiro lugar do produto com seleção e trouxe um `CampoSelect` LOCAL, com a nota de que
 * "quando um segundo lugar precisar de seleção, vira primitivo — card próprio". Este é o
 * segundo lugar. A consolidação do filtro para cá é card próprio: mudar a tela de descoberta
 * dentro do card de publicação seria mudança não pedida (AGENTS.md regra 6).
 *
 * Garantias as mesmas do `Campo`: `<label for>` associado, mensagem de erro ligada ao
 * controle por `aria-describedby`, `aria-invalid` quando há erro, obrigatório dito em texto.
 * O reset de aparência do `select` vive em `layout.css` (§11) — aqui não há estilo solto.
 *
 * `defaultValue`/`value` chegam pelo `rest`; quem usa escolhe entre controlado e não
 * controlado, como em qualquer `select` do React.
 */
export function SelectField({
  id,
  label,
  name,
  hint,
  error,
  required = false,
  className,
  children,
  ...rest
}: SelectFieldProps) {
  return (
    <Campo id={id} label={label} hint={hint} error={error} required={required} className={className}>
      {({ describedBy }) => (
        <select
          {...rest}
          id={id}
          name={name ?? id}
          required={required}
          aria-invalid={error ? true : undefined}
          aria-describedby={describedBy}
        >
          {children}
        </select>
      )}
    </Campo>
  );
}

export type CheckboxFieldProps = Omit<
  InputHTMLAttributes<HTMLInputElement>,
  'id' | 'name' | 'className' | 'type'
> & {
  id: string;
  name?: string;
  label: ReactNode;
  hint?: string;
  error?: string;
  required?: boolean;
  className?: string;
};

/**
 * Caixa de marcação única — usada pela declaração de direito de compartilhar da publicação
 * (RF012, RN007). Virou primitivo porque o §11 exige que essa confirmação seja "visível e
 * explícita, não uma checkbox decorativa": o controle precisa de rótulo associado, de
 * mensagem de erro ligada a ele e de alvo clicável — três coisas que uma `<label>` crua não
 * garante sozinha.
 *
 * O rótulo vem DEPOIS do controle e envolve o texto inteiro: quem navega por teclado tabula
 * até a caixa e ouve o texto do rótulo; quem usa o mouse acerta o alvo clicável sem mirar um
 * quadrado de poucos pixels. A aparência (alinhamento, tamanho do alvo, `accent-color`) vive
 * em `layout.css` — nenhum valor de estilo solto aqui.
 */
export function CheckboxField({
  id,
  name,
  label,
  hint,
  error,
  required = false,
  className,
  ...rest
}: CheckboxFieldProps) {
  const classes = ['sc-field', 'sc-field--check', error ? 'sc-field--error' : '', className]
    .filter(Boolean)
    .join(' ');

  return (
    <div className={classes}>
      <div className="sc-check">
        <input
          {...rest}
          id={id}
          name={name ?? id}
          type="checkbox"
          required={required}
          aria-invalid={error ? true : undefined}
          aria-describedby={descritoPor(id, hint, error)}
        />
        <label className="sc-field__label" htmlFor={id}>
          {label}
          {required ? (
            <>
              <span aria-hidden="true"> *</span>
              <span className="sc-visually-hidden"> (campo obrigatório)</span>
            </>
          ) : null}
        </label>
      </div>
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
