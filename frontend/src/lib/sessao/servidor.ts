/**
 * Leitura da sessão NO SERVIDOR — quem o Next é quando renderiza uma página privada.
 *
 * Por que este módulo existe (e por que ele NÃO substitui `lib/sessao/estado.ts`):
 * `/painel` e `/painel/perfil` mostram dado que só faz sentido para quem está autenticado —
 * nome público, slug, bio, afiliação, e o estado da conta. Renderizar isso DEPOIS da
 * hidratação deixaria o HTML entregue com um formulário vazio (ou com "carregando") no lugar
 * do conteúdo, e quem não executa JavaScript veria uma tela vazia justamente na área da
 * própria conta. Então a página pergunta ao servidor, na requisição, com o cookie que o
 * navegador já mandou.
 *
 * Divisão de trabalho entre os dois:
 *   - ESTE módulo responde "esta requisição tem sessão, e de quem é?" para a PÁGINA decidir
 *     o que entregar no HTML (conteúdo, sessão expirada, falha de rede);
 *   - `estado.ts` continua sendo a fonte única do CABEÇALHO e do aviso de verificação (E3.6),
 *     que são client-side e vivem em qualquer página. Um não contradiz o outro: os dois leem
 *     a MESMA resposta (`GET /api/sessoes/atual`), e o servidor é a autoridade nos dois.
 *
 * Como o cookie viaja: o mesmo cookie `SCIPATH_SESSAO` (`HttpOnly`) que o navegador mandou
 * para cá é reenviado à API no cabeçalho `Cookie`. É o desenho do ADR-0003 (e) visto do
 * servidor — a mesma origem para o navegador (pelo proxy `/api`) e uma chamada de servidor
 * para servidor aqui. Nada é guardado: nenhum valor de cookie é copiado para a resposta, e o
 * HTML entregue não contém sessão, token nem e-mail de login (RNF013).
 *
 * `cookies()` também é o que torna estas rotas DINÂMICAS. Isso não é efeito colateral: uma
 * página privada renderizada no build (ou cacheada como estática) seria a mesma página para
 * todo mundo — e servir o perfil de uma conta a outra pessoa é exatamente o defeito que este
 * módulo impede.
 */
import { cookies } from 'next/headers';
import { baseDaApi } from '@/lib/api/client';
import type { EstadoConta, PerfilDaSessao } from '@/lib/api/tipos';

/**
 * Nome da cookie de sessão, como o contrato o publica no topo de `contracts/openapi.yaml` e
 * o ADR-0003 (e) o descreve (`HttpOnly`, `Secure`, `SameSite=Lax`, `Path=/`). O valor é
 * opaco: este módulo o reenvia e nunca o lê.
 */
const COOKIE_SESSAO = 'SCIPATH_SESSAO';

/** Teto de tempo da consulta — sem ele, uma API que aceita e não responde prenderia a renderização. */
const LIMITE_MS = 8000;

/**
 * A sessão desta requisição, no recorte que as páginas privadas consomem.
 *
 * Não é o `SessaoAtual` inteiro de propósito: aqui não cabe "autenticado: false" (quem não tem
 * sessão é o outro caso da união) nem perfil ausente. Este módulo é quem garante a forma — se a
 * API responder algo que não confirma as duas coisas, o resultado é `anonimo`, e a página não
 * precisa testar de novo o que já foi testado.
 */
export type SessaoDaRequisicao = {
  /** `PENDENTE_VERIFICACAO` ou `ATIVO` (ADR-0003 (b)); `SUSPENSO` não mantém sessão. */
  estadoConta: EstadoConta;
  perfil: PerfilDaSessao;
};

/**
 * O que a página precisa saber — três casos, e cada um tem uma tela diferente:
 *   - `anonimo`     não há sessão nesta requisição (§15.3: 401 → entrar, com o destino
 *                   preservado). Inclui o cookie expirado: o contrato responde 200 com
 *                   `autenticado: false` (não 401), e "não há sessão válida" é o mesmo
 *                   fato para quem está olhando a tela;
 *   - `autenticado` há sessão válida, com o perfil público e o estado da conta;
 *   - `falha`       a API não respondeu ou respondeu fora do contrato — a página mostra o
 *                   estado de erro de rede com "tentar de novo", nunca o conteúdo de uma
 *                   sessão que não foi confirmada (RNF004).
 */
export type LeituraDaSessao =
  | { tipo: 'anonimo' }
  | { tipo: 'autenticado'; sessao: SessaoDaRequisicao }
  | { tipo: 'falha' };

/**
 * Lê o estado da sessão desta requisição.
 *
 * Devolve `anonimo` sem chamar a API quando a cookie de sessão não veio: não há o que
 * validar, e a resposta da página não pode depender de a API estar no ar para saber que
 * alguém não enviou credencial nenhuma. Quando ela veio, quem decide é a API — inclusive
 * para dizer que não vale mais (cookie expirado pelo prazo de inatividade ou revogado por
 * logout em outro lugar).
 */
export async function lerSessaoNoServidor(): Promise<LeituraDaSessao> {
  const jar = await cookies();
  if (!jar.get(COOKIE_SESSAO)) return { tipo: 'anonimo' };

  const cabecalhoDeCookie = jar
    .getAll()
    .map((cookie) => `${cookie.name}=${cookie.value}`)
    .join('; ');

  let resposta: Response;
  try {
    resposta = await fetch(`${baseDaApi()}/sessoes/atual`, {
      headers: { accept: 'application/json', cookie: cabecalhoDeCookie },
      cache: 'no-store',
      signal: AbortSignal.timeout(LIMITE_MS),
    });
  } catch {
    return { tipo: 'falha' };
  }

  // 401 não está nas respostas deste endpoint do contrato (ele responde 200 com
  // `autenticado:false`). Se algum dia responder, o significado é o mesmo: não há sessão.
  if (resposta.status === 401) return { tipo: 'anonimo' };
  if (!resposta.ok) return { tipo: 'falha' };

  let corpo: { autenticado?: boolean; estadoConta?: EstadoConta; perfil?: PerfilDaSessao };
  try {
    corpo = (await resposta.json()) as typeof corpo;
  } catch {
    return { tipo: 'falha' };
  }

  // Sem perfil não há identidade para mostrar, e sem estado da conta não há o que a área do
  // usuário tem de dizer: as duas coisas vêm do contrato, e a falta de qualquer uma é
  // "não há sessão utilizável" — nunca um conteúdo pela metade.
  if (corpo.autenticado !== true || !corpo.perfil || !corpo.estadoConta) return { tipo: 'anonimo' };

  // O perfil é COPIADO CAMPO A CAMPO, e isso não é preciosismo: o objeto que veio da API é
  // repassado adiante (a página o entrega a componentes de cliente), e o Next serializa esses
  // dados dentro do HTML (`self.__next_f`). Repassar o objeto inteiro levaria para o documento
  // qualquer campo EXTRA que o servidor tivesse mandado — e-mail, id de conta, hash, token —
  // mesmo que o TIPO não os declare (RNF013). Foi medido: com o repasse direto, esses campos
  // apareciam no HTML entregue. Copiando os seis campos públicos que o contrato publica, o
  // documento só pode conter o que a tela sabe exibir.
  return {
    tipo: 'autenticado',
    sessao: {
      estadoConta: corpo.estadoConta,
      perfil: {
        slug: corpo.perfil.slug,
        nomePublico: corpo.perfil.nomePublico,
        biografia: corpo.perfil.biografia,
        afiliacaoDeclarada: corpo.perfil.afiliacaoDeclarada,
        areasInteresse: corpo.perfil.areasInteresse,
        linksExternos: corpo.perfil.linksExternos,
      },
    },
  };
}
