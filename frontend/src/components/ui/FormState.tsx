import type { ReactNode } from 'react';
import { Badge, type BadgeTone } from './Badge';
import { MENSAGENS_FORMULARIO, mensagemArquivo, type FormStateKind, type NoticeTone } from './mensagens';

/**
 * `<FormState>` — os seis estados de formulário do DRS §15.3.
 *
 * Divisão de responsabilidade (é o que evita duplicação em cada formulário):
 *   - `invalid-field`  resumo de erros ACIMA dos campos, com âncora para o primeiro
 *                      campo com problema (`fieldId`). A mensagem de cada campo fica
 *                      no próprio campo — `TextField`/`TextAreaField` a associam ao
 *                      controle por `aria-describedby` (RNF006);
 *   - `submitting`     envio em andamento. O envio único é garantido pelo botão
 *                      (`SubmitButton pending` → desabilitado + `aria-busy`);
 *   - `recoverable-failure` falha que se recupera: a mensagem diz que NADA foi
 *                      perdido — e a tela precisa manter os valores preenchidos
 *                      (RNF004);
 *   - `success`        envio concluído, com o próximo passo;
 *   - `duplicate-attempt` o envio já está em processamento; repetir não cria outro;
 *   - `invalid-file`   arquivo acima do limite ou em formato inválido, com o motivo
 *                      específico via `mensagemArquivo()`.
 *
 * O resumo de erros nasce com `tabIndex={-1}` e `id`: o formulário deve MANTER o
 * foco nele depois do envio recusado, senão quem navega por teclado volta ao topo
 * do documento sem saber o que aconteceu.
 */

const BADGE_DO_TOM: Record<NoticeTone, BadgeTone> = {
  info: 'blue',
  ok: 'green',
  atencao: 'yellow',
  erro: 'red',
};

export type FormStateProps = {
  kind: FormStateKind;
  title?: string;
  message?: string;
  /** ação da tela (ex.: botão "Tentar novamente" que reenvia). */
  action?: ReactNode;
  /** id do primeiro campo com problema — vira âncora "Ir para o campo". */
  fieldId?: string;
  /** motivo do `invalid-file`: limite de tamanho ou formato não aceito. */
  fileMotivo?: 'tamanho' | 'formato';
  /** limite configurável exibido ao usuário (ADR-0005), ex.: '30 MB'. */
  fileLimite?: string;
  /** tamanho observado, quando conhecido, ex.: '42 MB'. */
  fileTamanho?: string;
  /** id do resumo; a tela usa `aria-describedby` no `<form>` apontando para ele. */
  id?: string;
  tituloNivel?: 2 | 3 | 4;
};

export function FormState({
  kind,
  title,
  message,
  action,
  fieldId,
  fileMotivo = 'formato',
  fileLimite,
  fileTamanho,
  id,
  tituloNivel = 3,
}: FormStateProps) {
  const mensagem = MENSAGENS_FORMULARIO[kind];
  const texto =
    message ??
    (kind === 'invalid-file'
      ? `${mensagem.mensagem} ${mensagemArquivo({ motivo: fileMotivo, limite: fileLimite, tamanho: fileTamanho })}`
      : mensagem.mensagem);

  const Titulo = `h${tituloNivel}` as 'h2' | 'h3' | 'h4';
  const boaNoticia = mensagem.tom === 'ok' || kind === 'submitting';

  return (
    <div
      id={id}
      tabIndex={-1}
      className={`sc-notice sc-notice--${mensagem.tom}`}
      data-state={kind}
      role={boaNoticia ? 'status' : 'alert'}
    >
      <Badge tone={BADGE_DO_TOM[mensagem.tom]}>{mensagem.rotulo}</Badge>
      <Titulo className="sc-notice__title">{title ?? mensagem.titulo}</Titulo>
      <p className="sc-notice__msg">{texto}</p>
      {action || fieldId ? (
        <div className="sc-notice__actions">
          {action}
          {fieldId ? (
            <a className="sc-btn sc-btn--ghost sc-btn--sm" href={`#${fieldId}`}>
              Ir para o campo
              <span className="sc-visually-hidden"> com problema</span>
            </a>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}
