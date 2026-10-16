import Link from 'next/link';
import type { Route } from 'next';
import type { ReactNode } from 'react';
import { Badge, type BadgeTone } from './Badge';
import { MENSAGENS_PAGINA, type NoticeTone, type PageStateKind } from './mensagens';

/**
 * `<PageState>` — os sete estados de página do DRS §15.3.
 *
 * Por que existe: sem ele, cada tela inventa o seu "carregando", o seu "vazio" e o
 * seu "deu erro" — o resultado é treze telas com treze comportamentos (RNF003).
 *
 * Contrato:
 *   - `loading`          conteúdo remoto em busca; anuncia em região `status`;
 *   - `success`          o caminho feliz. É explícito: `children` é o conteúdo da
 *                        página, e o bloco sai marcado com `data-state="success"`;
 *   - `empty`            não há resultado, MAS sempre com próximo passo na mensagem;
 *   - `network-error`    falha de conexão/servidor; `alert`, ação injetada pela tela
 *                        (recarregar depende do cliente, não desta primitiva);
 *   - `not-found`        404 — endereço errado ou conteúdo retirado;
 *   - `unauthorized`     403 — a área é de outra conta;
 *   - `session-expired`  401 — e, por contrato, o destino é PRESERVADO no login
 *                        (`loginDestination` → `/entrar?proximo=…`).
 *
 * Nenhum estado aqui é enfeite de cor: cada um traz chip com o nome do estado,
 * título e mensagem acionável (RNF004, RNF007).
 */

const BADGE_DO_TOM: Record<NoticeTone, BadgeTone> = {
  info: 'blue',
  ok: 'green',
  atencao: 'yellow',
  erro: 'red',
};

/**
 * URL de entrada preservando o destino (DRS §15.3: 401 → login preservando o
 * destino). O parâmetro se chama `proximo` e carrega o caminho pedido.
 */
export function entrarHref(destino?: string) {
  return destino ? `/entrar?proximo=${encodeURIComponent(destino)}` : '/entrar';
}

/**
 * As URLs dos estados vêm de dados (catálogo de mensagens / destino pedido), não
 * de literal no código — por isso o `as Route` do roteador tipado (`typedRoutes`
 * em `next.config.ts`). O destino `/_estados` e as rotas do produto passam a ser
 * verificados pelo compilador quando forem literais.
 */
const href = (url: string) => url as Route;

export type PageStateProps = {
  kind: PageStateKind;
  /** conteúdo da página — usado apenas em `kind="success"`. */
  children?: ReactNode;
  title?: string;
  message?: string;
  /** substitui a ação padrão do estado (ex.: botão que recarrega os dados). */
  action?: ReactNode;
  /** contexto extra do caso concreto (id do material, filtros aplicados…). */
  details?: ReactNode;
  /** só em `session-expired`: o caminho pedido, preservado para depois do login. */
  loginDestination?: string;
  /** nível do título do bloco; 2 na página, 3 dentro de uma seção. */
  tituloNivel?: 2 | 3;
};

function acaoPadrao(kind: PageStateKind, loginDestination?: string) {
  const mensagem = MENSAGENS_PAGINA[kind];
  if (kind === 'session-expired') {
    return (
      <Link className="sc-btn sc-btn--primary sc-btn--sm" href={href(entrarHref(loginDestination))}>
        {mensagem.acao?.rotulo ?? 'Entrar novamente'}
      </Link>
    );
  }
  if (!mensagem.acao) return null;
  return (
    <Link className="sc-btn sc-btn--ghost sc-btn--sm" href={href(mensagem.acao.href)}>
      {mensagem.acao.rotulo}
    </Link>
  );
}

export function PageState({
  kind,
  children,
  title,
  message,
  action,
  details,
  loginDestination,
  tituloNivel = 2,
}: PageStateProps) {
  // Caminho feliz explícito: a página entrega os dados; nada é anunciado.
  if (kind === 'success') {
    return <div data-state="success">{children}</div>;
  }

  const mensagem = MENSAGENS_PAGINA[kind];
  const Titulo = (tituloNivel === 3 ? 'h3' : 'h2') as 'h2' | 'h3';
  const papel = mensagem.tom === 'erro' || kind === 'session-expired' ? 'alert' : 'status';
  const acao = action ?? acaoPadrao(kind, loginDestination);

  return (
    <div
      className={`sc-notice sc-notice--pagina sc-notice--${mensagem.tom}`}
      data-state={kind}
      role={papel}
    >
      <Badge tone={BADGE_DO_TOM[mensagem.tom]}>{mensagem.rotulo}</Badge>
      <Titulo className="sc-notice__title">{title ?? mensagem.titulo}</Titulo>
      <p className="sc-notice__msg">{message ?? mensagem.mensagem}</p>
      {details}
      {acao ? <div className="sc-notice__actions">{acao}</div> : null}
    </div>
  );
}
