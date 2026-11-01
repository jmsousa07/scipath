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
  EdicaoDeMaterial,
  EdicaoPerfil,
  EstadoDeDestino,
  MaterialProprio,
  MaterialPublicado,
  PerfilPublico,
  ProblemDetail,
  PublicacaoMaterial,
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
  metodo: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE',
  corpo?: unknown,
  opcoes: {
    multipart?: boolean;
    limiteMs?: number;
    /**
     * O que o status sozinho não diz e a tradução precisa saber — ver o terceiro parâmetro de
     * `mapearFalhaDeEnvio`. A publicação o usa: o 403 dela é "confirme o e-mail" quando a conta
     * é a `PENDENTE_VERIFICACAO` que a tela leu no servidor (ADR-0003 (b)).
     */
    contexto?: { contaVerificada?: boolean; limiteArquivo?: string };
  } = {},
): Promise<ResultadoDeEnvio<T>> {
  const multipart = opcoes.multipart === true && corpo !== undefined;
  const cabecalhos: Record<string, string> = { accept: 'application/json' };
  // `multipart/form-data` NÃO recebe `content-type` escrito à mão: o cabeçalho precisa carregar o
  // `boundary` que o próprio navegador gerou ao montar o `FormData`, e escrevê-lo aqui (sem o
  // boundary) faria o servidor não conseguir ler o corpo. É a diferença que a publicação (E4.6)
  // trouxe: os metadados e o arquivo viajam na mesma requisição (RF012, RN008).
  if (corpo !== undefined && !multipart) cabecalhos['content-type'] = 'application/json';
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
      body: corpo === undefined ? undefined : multipart ? (corpo as FormData) : JSON.stringify(corpo),
      signal: AbortSignal.timeout(opcoes.limiteMs ?? LIMITE_MS),
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
        opcoes.contexto,
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
 * `POST /api/materiais` — publica um material da conta autenticada (RF012, card E4.6).
 *
 * Por que este envio não é igual aos outros três, em uma frase: os METADADOS e o ARQUIVO
 * viajam na MESMA requisição (`multipart/form-data`), porque é isso que torna a ordem do
 * RN008 implementável no servidor — ele recebe, valida o PDF por conteúdo, move para o
 * storage e só então grava material e arquivo numa transação (ADR-0005 §2). Mandar o
 * arquivo num segundo pedido deixaria a janela em que existiria material sem arquivo ou
 * arquivo sem material.
 *
 * O que este envio NÃO manda, e é o desenho do contrato: **não há campo de autor**. A
 * autoria vem da sessão (`ADR-0003 (j)`) — não existe corpo capaz de publicar em nome de
 * outra conta. `categoriaSlug`, `tipoSlug` e `licencaSlug` são os `slug` da taxonomia
 * (RF018), e nenhum deles tem valor padrão: a licença, em especial, é escolha explícita do
 * autor (RN006) — o servidor não atribui uma em silêncio.
 *
 * O teto de tempo é maior que o das outras escritas (120 s) e isso é requisito, não
 * folga: a requisição carrega até o limite do ADR-0005 (30 MB por padrão) e é justamente
 * numa máquina pequena que um upload legítimo demoraria mais que os 8 s das escritas de
 * identidade. Um teto curto aqui abortaria um envio válido e diria ao usuário que a rede
 * falhou.
 */
const LIMITE_PUBLICACAO_MS = 120_000;

export function publicarMaterial(
  dados: PublicacaoMaterial,
  contexto: { contaVerificada: boolean; limiteArquivo?: string },
): Promise<ResultadoDeEnvio<MaterialPublicado>> {
  const corpo = new FormData();
  corpo.set('titulo', dados.titulo);
  corpo.set('descricao', dados.descricao);
  corpo.set('categoriaSlug', dados.categoriaSlug);
  corpo.set('tipoSlug', dados.tipoSlug);
  corpo.set('licencaSlug', dados.licencaSlug);
  // O contrato tipa `declaracaoDireito` como boolean; em `multipart/form-data` todo campo é
  // texto, então o valor vai como `'true'`. O que o servidor recusa é ausente/`false` — e é
  // por isso que a tela nunca deixa o campo vazio: ela só envia o que a pessoa confirmou.
  corpo.set('declaracaoDireito', String(dados.declaracaoDireito));
  // Terceiro argumento com o nome do arquivo: sem ele o `FormData` usaria o padrão `blob` e o
  // nome original (sanitizado pelo servidor) viraria outro — o contrato publica o nome
  // original justamente para o download sugerir o arquivo certo (RF014).
  corpo.set('arquivo', dados.arquivo, dados.arquivo.name);

  return executar<MaterialPublicado>('publicar-material', '/materiais', 'POST', corpo, {
    multipart: true,
    limiteMs: LIMITE_PUBLICACAO_MS,
    contexto,
  });
}

/* ---------------------------------------------------------------------------
   Gerenciamento do material próprio — `PUT`/`PATCH`/`DELETE` de
   `/perfil/atual/materiais/{slug}` (tag `gerenciamento` do contrato v0.6.0,
   RF015–RF017). MORAM AQUI, e não em `lib/api/client.ts`, por uma razão só: as
   três são ESCRITAS, e escrita exige `X-CSRF-Token` — que só o navegador tem, na
   cookie `XSRF-TOKEN` legível (`garantirTokenCsrf` acima). As duas LEITURAS da
   mesma superfície ficam no cliente de servidor, onde a cookie de sessão está.
   --------------------------------------------------------------------------- */

/**
 * `PUT /api/perfil/atual/materiais/{slug}` — edita os metadados (RF016, ADR-0011 §4).
 *
 * Três coisas que este envio NÃO manda, e são o desenho do contrato:
 *   - **não manda `slug`**: a URL canônica pública não muda por edição (RNF025) — o
 *     `EdicaoDeMaterial` não tem o campo, então não existe requisição que troque o
 *     endereço do material;
 *   - **não manda autoria nem propriedade**: vêm da sessão (RN001, CA007). `autorSlug`,
 *     `contaId` e `perfilId` não existem no corpo — não há como editar material alheio
 *     nem publicar em nome de outra conta;
 *   - **não manda `estado`**: é o `PATCH`, ato distinto; e não manda `declaracaoDireito`,
 *     que pertence ao ato de publicar (RF012) e não é re-perguntada.
 *
 * O `slug` vai codificado no caminho pelo mesmo motivo da leitura: ele vem da URL e um
 * valor com caractere especial não pode virar consulta a outro endereço.
 */
export function editarMaterialProprio(
  slug: string,
  dados: EdicaoDeMaterial,
  contexto: { contaVerificada: boolean },
): Promise<ResultadoDeEnvio<MaterialProprio>> {
  return executar<MaterialProprio>(
    'editar-material',
    `/perfil/atual/materiais/${encodeURIComponent(slug)}`,
    'PUT',
    dados,
    { contexto },
  );
}

/**
 * `PATCH /api/perfil/atual/materiais/{slug}` — retira do público ou repõe (RF017, RF019).
 *
 * Um campo, um ato: `{"estado": "RETIRADO" | "PUBLICADO"}`. O corpo não aceita `EXCLUIDO`
 * (o contrato responde 422 apontando o `DELETE`, ADR-0011 §5), e por isso o tipo do
 * destino é `EstadoDeDestino` — a exclusão sai por `excluirMaterialProprio`, abaixo.
 *
 * O `contaVerificada` viaja como contexto porque o 403 desta rota pode ser duas coisas
 * diferentes: CSRF ausente (acesso negado comum) ou conta `PENDENTE_VERIFICACAO` pedindo
 * `PUBLICADO` — repor no público é publicar (ADR-0003 (b), ADR-0011 §5). Sem saber o
 * estado da conta, o cliente não inventa a causa: fica o acesso negado genérico.
 */
export function mudarEstadoMaterialProprio(
  slug: string,
  estado: EstadoDeDestino,
  contexto: { contaVerificada: boolean },
): Promise<ResultadoDeEnvio<MaterialProprio>> {
  return executar<MaterialProprio>(
    'mudar-estado-material',
    `/perfil/atual/materiais/${encodeURIComponent(slug)}`,
    'PATCH',
    { estado },
    { contexto },
  );
}

/**
 * `DELETE /api/perfil/atual/materiais/{slug}` — exclui (RF017). Exclusão LÓGICA: os
 * metadados permanecem e o binário é purgado 7 dias depois (ADR-0005 §3).
 *
 * **Não há campo de confirmação no corpo, e é deliberado** (ADR-0011 §7): um
 * `confirmacao=true` não confirmaria nada — o cliente sempre pode enviá-lo. A confirmação
 * que o RF017 pede é o diálogo da tela (`CONFIRMACAO_DE_EXCLUSAO`), que diz que não há
 * como desfazer; ela acontece ANTES desta função ser chamada.
 *
 * Sucesso é **204 sem corpo**, e é por isso que o retorno é `void`: não há o que mostrar
 * do material depois da exclusão. O que a tela mostra é o estado novo (`EXCLUIDO`) na
 * lista, relida do servidor.
 */
export function excluirMaterialProprio(slug: string): Promise<ResultadoDeEnvio<void>> {
  return executar<void>(
    'excluir-material',
    `/perfil/atual/materiais/${encodeURIComponent(slug)}`,
    'DELETE',
  );
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
 * Desfecho de sucesso da PUBLICAÇÃO de um material (RF012, card E4.6).
 *
 * O que a mensagem tem de dizer, e nada além: que o material está no ar com o estado
 * `PUBLICADO` (RF019) e qual é o endereço público. Sem "enviado com sucesso" vazio: quem
 * publicou precisa saber que o arquivo foi gravado junto dos dados (numa transação — RN008,
 * ADR-0005 §2) e que a página já responde a qualquer visitante.
 */
export function desfechoDeMaterialPublicado(material: MaterialPublicado): Desfecho {
  return {
    estado: 'success',
    classe: 'material-publicado',
    titulo: 'Material publicado',
    mensagem: `O material e o arquivo foram gravados na mesma operação e ele já está no ar com o estado ${material.estado}: quem tiver o endereço abre a página sem precisar de conta. O endereço público é ${material.urlPublica}.`,
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

