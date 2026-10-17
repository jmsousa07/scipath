'use client';

/**
 * Cliente de sessão do NAVEGADOR — cadastro, login, logout e reenvio de verificação.
 *
 * Por que é um módulo separado de `lib/api/client.ts`: aquele é de SERVIDOR (lê
 * `process.env`, fala direto com o backend) e nenhum componente de cliente pode
 * importá-lo. Este fala com a MESMA ORIGEM, pelo proxy `/api` do Next
 * (`src/app/api/[...caminho]/route.ts`), que encaminha para a API — é o desenho do
 * ADR-0003 (e): cookie de sessão `HttpOnly` viajando sozinho, sem `Authorization`, sem
 * token em `localStorage` e sem sessão acessível ao JavaScript. Aqui não existe uma
 * linha sequer que guarde credencial em memória além do corpo da requisição.
 *
 * CSRF (contrato v0.4.0): toda escrita exige `X-CSRF-Token`, e o servidor emite o token
 * na cookie `XSRF-TOKEN` — legível pelo JavaScript, porque é o cliente que precisa
 * devolvê-lo no cabeçalho (a cookie de sessão é que é `HttpOnly`). A primeira leitura do
 * app (`GET /sessoes/atual`) é o que traz essa cookie; por isso `garantirTokenCsrf()`
 * faz essa leitura quando ainda não há token — sem ela, o cadastro do primeiro acesso
 * levaria 403 sem motivo visível para o usuário.
 *
 * Efeito colateral honesto: nenhuma resposta daqui é cacheada (`cache: 'no-store'`) e
 * todas usam `credentials: 'same-origin'`, senão o cookie de sessão não viajaria.
 */
import type {
  CadastroConta,
  Credenciais,
  EdicaoPerfil,
  PerfilPublico,
  ProblemDetail,
  ReenvioVerificacao,
  SessaoAtual,
} from '../api/tipos';
import { mapearFalhaDeEnvio, type Desfecho, type OperacaoDeSessao } from './erros';

/** Mesma origem: o proxy do Next encaminha para a API (ADR-0003 (e)). */
const PROXY = '/api';

/** Teto de tempo da requisição — sem ele, o envio fica "em andamento" para sempre. */
const LIMITE_MS = 8000;

/** Cookie de CSRF do contrato (legível pelo cliente) e cabeçalho que a devolve. */
const COOKIE_CSRF = 'XSRF-TOKEN';
const CABECALHO_CSRF = 'X-CSRF-Token';

export type ResultadoDeEnvio<T> = { ok: true; dados: T } | { ok: false; desfecho: Desfecho };

function tokenCsrf(): string | null {
  const par = document.cookie
    .split('; ')
    .find((cookie) => cookie.startsWith(`${COOKIE_CSRF}=`));
  return par ? decodeURIComponent(par.slice(COOKIE_CSRF.length + 1)) : null;
}

/**
 * Devolve o token de CSRF, pedindo pela primeira leitura do app quando ele ainda não
 * existe (o `GET /sessoes/atual` é quem emite a cookie). Devolve `null` quando nem assim
 * o servidor respondeu — a escrita segue e recebe do servidor o 403 explicado, em vez de
 * o cliente inventar um erro próprio.
 */
export async function garantirTokenCsrf(): Promise<string | null> {
  const existente = tokenCsrf();
  if (existente) return existente;

  try {
    await fetch(`${PROXY}/sessoes/atual`, {
      credentials: 'same-origin',
      cache: 'no-store',
      headers: { accept: 'application/json' },
      signal: AbortSignal.timeout(LIMITE_MS),
    });
  } catch {
    return null;
  }
  return tokenCsrf();
}

function lerProblem(texto: string, tipoConteudo: string | null): ProblemDetail | null {
  if (!tipoConteudo?.includes('problem+json')) return null;
  try {
    const bruto: unknown = JSON.parse(texto);
    if (typeof bruto !== 'object' || bruto === null) return null;
    const problem = bruto as Partial<ProblemDetail>;
    if (typeof problem.type !== 'string' || typeof problem.title !== 'string') return null;
    if (typeof problem.status !== 'number') return null;
    return problem as ProblemDetail;
  } catch {
    return null;
  }
}

/** `Retry-After` em segundos (contrato do 429): usado só quando o servidor o mandou. */
function retryAfter(resposta: Response): number | undefined {
  const bruto = resposta.headers.get('retry-after');
  if (!bruto) return undefined;
  const segundos = Number.parseInt(bruto, 10);
  return Number.isFinite(segundos) && segundos >= 0 ? segundos : undefined;
}

/**
 * Executa uma operação de identidade pelo proxy, com o CSRF do contrato.
 *
 * Devolve sempre `ResultadoDeEnvio`: sucesso com os dados do contrato, ou a falha já
 * TRADUZIDA em estado de formulário (`mapearFalhaDeEnvio`) — a tela não decide sobre
 * status HTTP, só exibe o estado (RNF003: estado equivalente, comportamento equivalente).
 */
async function executar<T>(
  operacao: OperacaoDeSessao,
  caminho: string,
  metodo: 'GET' | 'POST' | 'PUT' | 'DELETE',
  corpo?: unknown,
): Promise<ResultadoDeEnvio<T>> {
  const cabecalhos: Record<string, string> = { accept: 'application/json' };
  if (corpo !== undefined) cabecalhos['content-type'] = 'application/json';
  if (metodo !== 'GET') {
    const token = await garantirTokenCsrf();
    if (token) cabecalhos[CABECALHO_CSRF] = token;
  }

  let resposta: Response;
  try {
    resposta = await fetch(`${PROXY}${caminho}`, {
      method: metodo,
      credentials: 'same-origin',
      cache: 'no-store',
      headers: cabecalhos,
      body: corpo === undefined ? undefined : JSON.stringify(corpo),
      signal: AbortSignal.timeout(LIMITE_MS),
    });
  } catch {
    return { ok: false, desfecho: mapearFalhaDeEnvio({ motivo: 'sem-resposta' }, operacao) };
  }

  const texto = await resposta.text();

  if (!resposta.ok) {
    return {
      ok: false,
      desfecho: mapearFalhaDeEnvio(
        {
          motivo: 'http',
          status: resposta.status,
          problem: lerProblem(texto, resposta.headers.get('content-type')),
          retryAfterS: retryAfter(resposta),
        },
        operacao,
      ),
    };
  }

  // 204 (logout) não tem corpo — e não ter corpo é a resposta certa, não um erro.
  if (resposta.status === 204 || texto === '') {
    return { ok: true, dados: undefined as T };
  }

  try {
    return { ok: true, dados: JSON.parse(texto) as T };
  } catch {
    return { ok: false, desfecho: mapearFalhaDeEnvio({ motivo: 'corpo-invalido' }, operacao) };
  }
}

/** `GET /api/sessoes/atual` — quem está autenticado (200 nos dois casos; 401 não existe aqui). */
export function obterSessaoAtual(): Promise<ResultadoDeEnvio<SessaoAtual>> {
  return executar<SessaoAtual>('encerrar-sessao', '/sessoes/atual', 'GET');
}

/** `POST /api/contas` — cria a conta e JÁ abre a sessão (RF008, DRS §7.2). */
export function criarConta(dados: CadastroConta): Promise<ResultadoDeEnvio<SessaoAtual>> {
  return executar<SessaoAtual>('criar-conta', '/contas', 'POST', dados);
}

/** `POST /api/sessoes` — autentica e abre a sessão (RF009). */
export function abrirSessao(credenciais: Credenciais): Promise<ResultadoDeEnvio<SessaoAtual>> {
  return executar<SessaoAtual>('abrir-sessao', '/sessoes', 'POST', credenciais);
}

/** `DELETE /api/sessoes/atual` — encerra a sessão no servidor (RF009). Idempotente: 204. */
export function encerrarSessao(): Promise<ResultadoDeEnvio<void>> {
  return executar<void>('encerrar-sessao', '/sessoes/atual', 'DELETE');
}

/** `POST /api/verificacao-email/reenvios` — reenvia o link para o e-mail da própria conta. */
export function reenviarVerificacaoEmail(): Promise<ResultadoDeEnvio<ReenvioVerificacao>> {
  return executar<ReenvioVerificacao>(
    'reenviar-verificacao',
    '/verificacao-email/reenvios',
    'POST',
  );
}

/**
 * `PUT /api/perfil/atual` — salva os campos públicos do PRÓPRIO perfil (RF011, card E3.7).
 *
 * Três coisas que este envio não faz, e são o desenho do contrato:
 *   - **não manda identificador de quem edita**: a propriedade vem da sessão (ADR-0003 (j),
 *     CA007) — o corpo tem só os campos públicos, então não existe requisição que altere o
 *     perfil de outra conta;
 *   - **não decide sobre status**: 401 (sessão encerrada), 403 (CSRF/conta suspensa), 409
 *     (slug de outro perfil), 422 (campo inválido) e 5xx chegam traduzidos por
 *     `mapearFalhaDeEnvio` — a tela só exibe o estado;
 *   - **não guarda resposta**: `cache: 'no-store'` em `executar`, porque a resposta é o
 *     perfil JÁ alterado, e servir de cache mostraria o valor antigo como se fosse o novo.
 */
export function editarPerfil(dados: EdicaoPerfil): Promise<ResultadoDeEnvio<PerfilPublico>> {
  return executar<PerfilPublico>('editar-perfil', '/perfil/atual', 'PUT', dados);
}

/**
 * Desfecho de sucesso da EDIÇÃO DO PERFIL (RF011).
 *
 * O texto depende do estado da conta de propósito: `PUT /perfil/atual` grava a alteração em
 * qualquer conta autenticada — inclusive `PENDENTE_VERIFICACAO` (editar o perfil não exige
 * e-mail confirmado, ADR-0003 (b)) —, mas o perfil PÚBLICO (`/u/{slug}`) só responde para
 * conta `ATIVO`. Prometer "veja em /u/seu-slug" para quem ainda não confirmou o e-mail seria
 * mandar a pessoa para um 404.
 */
export function desfechoDePerfilSalvo(perfil: PerfilPublico, jaVerificada: boolean): Desfecho {
  return {
    estado: 'success',
    classe: 'perfil-salvo',
    titulo: 'Perfil salvo',
    mensagem: jaVerificada
      ? `O seu perfil público foi atualizado: o endereço /u/${perfil.slug} já mostra o que está acima. A alteração vale para o perfil que qualquer visitante abre.`
      : `A alteração foi salva e vale para o seu perfil. O endereço /u/${perfil.slug} passa a responder a visitantes quando a confirmação do e-mail for concluída — enquanto a conta está pendente, o perfil aparece só aqui.`,
    campos: {},
    foraDosCampos: [],
  };
}

/**
 * Desfecho de sucesso do CADASTRO: nada de "conta criada com sucesso" e tela vazia — a
 * mensagem diz o que já existe (sessão aberta) e o que falta (confirmar o e-mail para
 * publicar), que é o caminho honesto do ADR-0003 (h) e do §7.2 item 5.
 */
export function desfechoDeContaCriada(nomePublico: string): Desfecho {
  return {
    estado: 'success',
    classe: 'conta-criada',
    titulo: `Conta criada — bem-vindo(a), ${nomePublico}`,
    mensagem:
      'Sua conta existe e a sessão já está aberta: você pode navegar, buscar e explorar agora. Falta um passo para publicar: confirme o e-mail pelo link que enviamos. Enquanto a confirmação não acontecer, a publicação fica bloqueada — o aviso no alto do site permite reenviar o link quando precisar.',
    campos: {},
    foraDosCampos: [],
  };
}

/** Desfecho de sucesso do LOGIN (RF009). */
export function desfechoDeSessaoAberta(nomePublico: string, jaVerificada: boolean): Desfecho {
  return {
    estado: 'success',
    classe: 'sessao-aberta',
    titulo: `Você entrou como ${nomePublico}`,
    mensagem: jaVerificada
      ? 'A sessão está aberta neste navegador. Você pode publicar material, editar o seu perfil e encerrar a sessão quando quiser.'
      : 'A sessão está aberta neste navegador. Falta confirmar o e-mail para publicar material; navegar, buscar e editar o perfil já estão liberados.',
    campos: {},
    foraDosCampos: [],
  };
}

/** Desfecho de sucesso do REENVIO, com a contagem do cooldown que o servidor devolveu. */
export function desfechoDeReenvioAceito(reenvio: ReenvioVerificacao, agoraMs: number): Desfecho {
  return {
    estado: 'success',
    classe: 'reenvio-aceito',
    titulo: 'Enviamos o link de confirmação',
    mensagem: `O e-mail de confirmação foi enviado para o endereço da sua conta. Ele vale por 24 h e só pode ser usado uma vez.${
      segundosDeEspera(reenvio, agoraMs) > 0
        ? ` Se não chegar, espere ${segundosDeEspera(reenvio, agoraMs)} s e peça outro.`
        : ''
    }`,
    campos: {},
    foraDosCampos: [],
  };
}

/** Segundos de espera calculados do instante que o servidor devolveu (`podeReenviarEm`). */
export function segundosDeEspera(reenvio: ReenvioVerificacao, agoraMs: number): number {
  const alvo = Date.parse(reenvio.podeReenviarEm);
  if (Number.isNaN(alvo)) return 0;
  return Math.max(0, Math.ceil((alvo - agoraMs) / 1000));
}
