/**
 * Falha do contrato → estado do formulário (DRS §15.3) — a tradução, num lugar só.
 *
 * Por que existe: o contrato (v0.4.0, tag `identidade`) recusa por CÓDIGO
 * (`urn:scipath:erro:<slug>`), e o cliente que decide pelo texto da mensagem quebra
 * quando o texto muda. Aqui o código decide, e a decisão vira o estado do §15.3 que a
 * tela exibe — com a mensagem já escrita para o usuário (RNF004: o que aconteceu e o
 * que fazer), nunca a string crua do servidor jogada na tela.
 *
 * Módulo PURO de propósito: sem `import` de runtime, sem `fetch`, sem DOM. É o que
 * permite `node scripts/check-erros-sessao.mjs` exercitar cada classe de erro com os
 * payloads REAIS do contrato e provar o mapeamento sem navegador (AGENTS.md — a prova
 * do card de implementação não é o navegador).
 *
 * Tabela de decisão (dois níveis: status/código e operação):
 *
 * | Resposta | Operação | Estado (§15.3) | Onde a mensagem aparece |
 * |---|---|---|---|
 * | sem resposta (rede/tempo) | qualquer | `recoverable-failure` | resumo do formulário |
 * | 2xx sem JSON | qualquer | `recoverable-failure` | resumo |
 * | 400 `validacao` | qualquer | `recoverable-failure` | resumo |
 * | 401 `nao_autenticado` | abrir sessão | `invalid-field` | resumo, sem apontar campo |
 * | 401 `nao_autenticado` | escrita com identidade | `recoverable-failure` + `sessaoEncerrada` | resumo |
 * | 403 `acesso_negado` | qualquer | `recoverable-failure` + `acessoNegado` | resumo (`detail` do contrato) |
 * | 404 `nao_encontrado` | qualquer | `recoverable-failure` | resumo |
 * | 409 `conflito_de_unicidade` | qualquer | `invalid-field` | CAMPO em conflito (`errors[]`) |
 * | 415 `tipo_nao_suportado` | qualquer | `recoverable-failure` | resumo |
 * | 422 `validacao_campo` | qualquer | `invalid-field` | um por campo (`errors[]`) |
 * | 429 `reenvio_em_cooldown` | reenvio | `recoverable-failure` + `aguardarS` | resumo |
 * | 500 `interno` | qualquer | `recoverable-failure` | resumo |
 *
 * O mapa de campos é o do controle no formulário: `email`, `senha`, `nomePublico` —
 * os mesmos nomes do contrato (`CadastroConta`, `Credenciais`), então a mensagem cai no
 * campo certo sem tabela de tradução. Campo que o formulário não tem aparece no resumo
 * (ver `foraDosCampos`): mensagem de campo desconhecido não pode sumir em silêncio.
 */
import type { ProblemDetail } from '../api/tipos';

/**
 * Operações de identidade que passam por aqui — decidem o sentido do mesmo status.
 * `editar-perfil` entrou com o card E3.7: `PUT /perfil/atual` é a segunda escrita que
 * exige identidade (a primeira é o cadastro), e o 401 dela significa "a sessão acabou",
 * não "credencial errada".
 */
export type OperacaoDeSessao =
  | 'abrir-sessao'
  | 'criar-conta'
  | 'editar-perfil'
  | 'encerrar-sessao'
  | 'reenviar-verificacao';

/**
 * Falha na granularidade que o cliente distingue, espelhando `lib/api/client.ts` (a
 * versão de servidor do mesmo desenho): `sem-resposta` é conexão/tempo; `corpo-invalido`
 * é resposta 2xx que não é o JSON do contrato; `http` é resposta com status de erro.
 */
export type FalhaDeEnvio =
  | { motivo: 'sem-resposta' }
  | { motivo: 'corpo-invalido' }
  | { motivo: 'http'; status: number; problem: ProblemDetail | null; retryAfterS?: number };

/** Estado do §15.3 que o formulário pode exibir. */
export type EstadoDoFormulario =
  | 'invalid-field'
  | 'submitting'
  | 'recoverable-failure'
  | 'success'
  | 'duplicate-attempt';

export type Desfecho = {
  /** Estado do formulário a exibir (o `kind` de `<FormState>`). */
  estado: EstadoDoFormulario;
  /** Classe do contrato (`urn:scipath:erro:<slug>`) ou `falha-de-rede`/`corpo-invalido`. */
  classe: string;
  titulo: string;
  mensagem: string;
  /** Campo do formulário → mensagem associada ao controle (RNF006: `aria-describedby`). */
  campos: Record<string, string>;
  /** Primeiro campo culpado — vira a âncora "Ir para o campo" do resumo. */
  campoFoco?: string;
  /** Mensagens de campo que o formulário não tem; entram no resumo, não somem. */
  foraDosCampos: string[];
  /** 429: segundos até poder reenviar (do `Retry-After`, nunca inventado). */
  aguardarS?: number;
  /** 401 numa escrita com identidade: a sessão acabou e o caminho pedido se preserva. */
  sessaoEncerrada?: boolean;
  /** 403: a plataforma recusou a operação (CSRF ausente ou conta suspensa). */
  acessoNegado?: boolean;
};

/** Classe estável do `type` do contrato, sem o prefixo. */
function classeDoContrato(problem: ProblemDetail | null): string {
  return problem?.type.replace('urn:scipath:erro:', '') ?? 'desconhecido';
}

/** `errors[]` do contrato (RFC 7807) → mapa campo → mensagem. */
function camposDoProblem(problem: ProblemDetail | null): { campos: Record<string, string>; fora: string[] } {
  const campos: Record<string, string> = {};
  const fora: string[] = [];
  for (const erro of problem?.errors ?? []) {
    if (CAMPO_CONHECIDO.has(erro.campo)) campos[erro.campo] = erro.mensagem;
    else fora.push(`${erro.campo}: ${erro.mensagem}`);
  }
  return { campos, fora };
}

/** Campos que existem como controle nos formulários de identidade (E3.6 e E3.7). */
const CAMPO_CONHECIDO = new Set([
  'email',
  'senha',
  'nomePublico',
  // E3.7 — os campos do formulário de edição do perfil (schema `EdicaoPerfil`). Sem eles,
  // um 422/409 vindo do servidor cairia no resumo em vez do campo que a pessoa precisa
  // corrigir: o contrato aponta o campo (`errors[]`) e a tela tem de saber onde pousá-lo.
  'slug',
  'biografia',
  'afiliacaoDeclarada',
  'areasInteresse',
  'linksExternos',
]);

function primeiroCampo(campos: Record<string, string>): string | undefined {
  return Object.keys(campos)[0];
}

/** Resumo que abre a lista de campos fora do formulário (nunca descarta a mensagem). */
function comForaDoFormulario(mensagem: string, fora: string[]): string {
  if (fora.length === 0) return mensagem;
  return `${mensagem} O servidor também apontou: ${fora.join('; ')}.`;
}

/**
 * 409 do contrato — "um dado já pertence a outra conta/perfil".
 *
 * A mensagem muda com a OPERAÇÃO porque o mesmo status significa coisas diferentes para
 * quem está lendo: no cadastro, a conta não nasceu; na edição do perfil (E3.7), a conta
 * existe e o que não foi salvo foi a alteração. O contrato manda o campo culpado em
 * `errors[]` (o backend do E3.5 o envia: `campo: slug`); quando ele não vem, o campo padrão
 * é o da operação — nunca um campo que a tela não tem.
 */
function conflito(
  mensagemDoServidor: string | null,
  campos: Record<string, string>,
  fora: string[],
  operacao: OperacaoDeSessao,
): Desfecho {
  const daEdicao = operacao === 'editar-perfil';
  const campoPadrao = daEdicao ? 'slug' : 'email';
  const mensagemCampo =
    mensagemDoServidor ||
    (daEdicao
      ? 'Este identificador público já pertence a outro perfil. Escolha outro — o endereço /u/{slug} precisa ser único.'
      : 'Este e-mail já tem conta no SciPath. Entre com ele ou cadastre outro endereço de e-mail.');
  const alvo = primeiroCampo(campos) ?? campoPadrao;
  return {
    estado: 'invalid-field',
    classe: 'conflito_de_unicidade',
    titulo: 'Este dado já está em uso',
    mensagem: comForaDoFormulario(
      daEdicao
        ? 'A alteração não foi salva porque um dos dados enviados já pertence a outro perfil. Corrija o campo indicado e envie de novo — nada do que você digitou foi apagado.'
        : 'A conta não foi criada porque um dos dados enviados já pertence a outra conta. Corrija o campo indicado e envie de novo — nada do que você digitou foi apagado.',
      fora,
    ),
    campos: { ...campos, [alvo]: campos[alvo] ?? mensagemCampo },
    campoFoco: alvo,
    foraDosCampos: fora,
  };
}

/**
 * Traduz a falha de um envio de sessão no estado que o formulário mostra.
 *
 * @param falha     resposta classificada (rede, corpo ou status + ProblemDetail)
 * @param operacao  qual operação de identidade foi recusada
 */
export function mapearFalhaDeEnvio(falha: FalhaDeEnvio, operacao: OperacaoDeSessao): Desfecho {
  if (falha.motivo === 'sem-resposta') {
    return {
      estado: 'recoverable-failure',
      classe: 'falha-de-rede',
      titulo: 'Não foi possível enviar agora',
      mensagem:
        'A conexão com o servidor falhou ou demorou mais que o esperado. Confira sua internet e envie de novo: tudo o que você digitou continua preenchido.',
      campos: {},
      foraDosCampos: [],
    };
  }

  if (falha.motivo === 'corpo-invalido') {
    return {
      estado: 'recoverable-failure',
      classe: 'corpo-invalido',
      titulo: 'A resposta do servidor não foi entendida',
      mensagem:
        'O servidor respondeu, mas a resposta não veio no formato do contrato. Nada foi concluído: tente de novo em instantes; se persistir, avise a equipe.',
      campos: {},
      foraDosCampos: [],
    };
  }

  const { status, problem } = falha;
  const classe = classeDoContrato(problem);
  const { campos, fora } = camposDoProblem(problem);
  const detalheDoServidor = problem?.detail ?? null;

  switch (status) {
    case 400:
      return {
        estado: 'recoverable-failure',
        classe: 'validacao',
        titulo: 'O envio não pôde ser processado',
        mensagem: comForaDoFormulario(
          'O servidor não conseguiu ler o envio como ele chegou. Envie de novo; se acontecer sempre, avise a equipe.',
          fora,
        ),
        campos,
        campoFoco: primeiroCampo(campos),
        foraDosCampos: fora,
      };

    case 401:
      // Abrir sessão: o contrato devolve o MESMO corpo para e-mail inexistente e senha
      // errada (RNF013), e o `detail` não diz qual campo falhou. Por isso a mensagem não
      // aponta campo nenhum — apontar um seria inventar o que o servidor não disse.
      if (operacao === 'abrir-sessao') {
        return {
          estado: 'invalid-field',
          classe: 'nao_autenticado',
          titulo: 'E-mail ou senha não conferem',
          mensagem:
            'A combinação de e-mail e senha não foi aceita. Confira os dois campos e tente de novo. Por segurança, a plataforma não diz qual dos dois está errado.',
          campos: {},
          foraDosCampos: [],
        };
      }
      return {
        estado: 'recoverable-failure',
        classe: 'nao_autenticado',
        titulo: 'Sua sessão não está mais válida',
        mensagem:
          'A sessão expirou ou foi encerrada em outro lugar, então esta operação não foi concluída. Entre novamente — o endereço que você pediu é preservado e você volta para ele.',
        campos: {},
        foraDosCampos: [],
        sessaoEncerrada: true,
      };

    case 403:
      return {
        estado: 'recoverable-failure',
        classe: 'acesso_negado',
        titulo: 'A plataforma recusou este envio',
        mensagem: detalheDoServidor
          ? `${detalheDoServidor} Se esta página ficou muito tempo aberta, recarregue-a e tente de novo.`
          : 'A operação exigiu um token de segurança que não confere com a sua sessão, ou a sua conta não tem permissão para ela. Recarregue a página e tente de novo.',
        campos: {},
        foraDosCampos: [],
        acessoNegado: true,
      };

    case 404:
      return {
        estado: 'recoverable-failure',
        classe: 'nao_encontrado',
        titulo: 'O endereço desta operação não existe',
        mensagem:
          'O servidor não reconheceu o endereço da operação. Nada foi concluído; recarregue a página e tente de novo.',
        campos: {},
        foraDosCampos: [],
      };

    case 409:
      return conflito(detalheDoServidor, campos, fora, operacao);

    case 415:
      return {
        estado: 'recoverable-failure',
        classe: 'tipo_nao_suportado',
        titulo: 'O envio chegou em um formato não aceito',
        mensagem:
          'O servidor recusou o formato do envio. Nada foi concluído; recarregue a página e tente de novo.',
        campos: {},
        foraDosCampos: [],
      };

    case 422:
      return {
        estado: 'invalid-field',
        classe: 'validacao_campo',
        titulo: 'Confira os campos destacados',
        mensagem: comForaDoFormulario(
          'Um ou mais campos não passaram na validação do servidor. A mensagem de cada campo diz o que ajustar; nada do que você digitou foi apagado.',
          fora,
        ),
        campos,
        campoFoco: primeiroCampo(campos),
        foraDosCampos: fora,
      };

    case 429: {
      const segundos = falha.retryAfterS;
      return {
        estado: 'recoverable-failure',
        classe: 'reenvio_em_cooldown',
        titulo: 'Aguarde para pedir outro e-mail',
        mensagem: segundos
          ? `O último pedido de confirmação foi feito há pouco. O próximo pode ser enviado em ${segundos} s.`
          : 'O último pedido de confirmação foi feito há pouco. O contrato prevê 60 s entre um pedido e o seguinte; tente de novo quando o botão liberar.',
        campos: {},
        foraDosCampos: [],
        aguardarS: segundos,
      };
    }

    default:
      // 5xx — inclui o 502 do proxy quando a API nem respondeu. A mensagem não promete
      // causa: diz que o servidor falhou e que nada foi aplicado (RNF004).
      if (status >= 500) {
        return {
          estado: 'recoverable-failure',
          classe,
          titulo: 'O servidor não conseguiu concluir',
          mensagem:
            'O servidor falhou ao processar o envio e nada foi aplicado. Tente de novo em instantes; se continuar, avise a equipe. O que você digitou continua preenchido.',
          campos: {},
          foraDosCampos: [],
        };
      }
      return {
        estado: 'recoverable-failure',
        classe,
        titulo: 'Não foi possível concluir agora',
        mensagem:
          'O servidor respondeu de um jeito que este formulário não sabe tratar, e a operação não foi concluída. Tente de novo em instantes; se continuar, avise a equipe.',
        campos: {},
        foraDosCampos: [],
      };
  }
}

/**
 * `invalid-field` produzido pela validação do PRÓPRIO formulário (antes de qualquer
 * requisição): os mesmos limites que o contrato publica. O servidor valida de novo — esta
 * função existe para o erro chegar mais rápido, não para substituir a autoridade dele.
 */
export function desfechoDeCamposInvalidos(campos: Record<string, string>): Desfecho {
  const nomes = Object.keys(campos);
  return {
    estado: 'invalid-field',
    classe: 'validacao-local',
    titulo: 'Confira os campos destacados',
    mensagem:
      'Falta corrigir o que está indicado abaixo antes de enviar. Nada do que você digitou foi apagado.',
    campos,
    campoFoco: nomes[0],
    foraDosCampos: [],
  };
}

/** `duplicate-attempt`: o envio já está em andamento; repetir não cria o segundo. */
export const DESFECHO_DUPLICADO: Desfecho = {
  estado: 'duplicate-attempt',
  classe: 'envio-duplicado',
  titulo: 'Este envio já está em andamento',
  mensagem:
    'Recebemos esta tentativa há instantes e ela ainda está sendo processada. Espere a resposta antes de enviar de novo: repetir não cria uma segunda conta nem abre uma segunda sessão.',
  campos: {},
  foraDosCampos: [],
};
