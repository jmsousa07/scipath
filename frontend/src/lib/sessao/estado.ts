'use client';

/**
 * Estado da sessão compartilhado pela interface — uma fonte só para o cabeçalho, o aviso
 * de verificação e as páginas de acesso.
 *
 * Por que um store, e não um `fetch` em cada componente: o card pede "estado de sessão
 * consistente em todo o site", e consistência com duas buscas independentes não existe —
 * o cabeçalho diria "saiu" enquanto o aviso de verificação ainda mostraria a conta
 * anterior. Aqui a leitura é uma só (`GET /api/sessoes/atual`), o resultado é o mesmo para
 * todo mundo e quem escreve (login, cadastro, logout) atualiza o mesmo lugar.
 *
 * Onde o estado NÃO vive: em `localStorage`, em cookie legível ou em qualquer "token" em
 * JavaScript. A informação guardada é só a que o servidor devolveu nesta aba (nome
 * público e estado da conta) e a autoridade continua sendo o servidor: a tela reflete a
 * sessão, não a protege (RNF010, RF009).
 *
 * O que este módulo NÃO faz: decidir o que o visitante pode ver. Nenhum dado privado é
 * renderizado a partir dele — material retirado, perfil de outra conta e publicação
 * continuam sendo recusados pelo servidor.
 */
import type { EstadoConta, PerfilDaSessao, SessaoAtual } from '../api/tipos';
import { obterSessaoAtual } from './navegador';

/** `verificando` = ainda não sabemos; `indisponivel` = perguntamos e o servidor não respondeu. */
export type SituacaoDaSessao = 'verificando' | 'anonimo' | 'autenticado' | 'indisponivel';

export type EstadoDaSessao = {
  situacao: SituacaoDaSessao;
  perfil?: PerfilDaSessao;
  estadoConta?: EstadoConta;
};

/**
 * Estado que o SERVIDOR entrega no HTML: sem sessão conhecida, a marcação é a do
 * visitante (Entrar · Criar conta). É o que garante que a página não depende de
 * JavaScript para oferecer o caminho de entrada — e é o mesmo valor durante a hidratação,
 * então não há divergência entre HTML e primeira renderização no cliente.
 */
export const ESTADO_ANONIMO: EstadoDaSessao = { situacao: 'anonimo' };

/**
 * Estado inicial do cliente depois da hidratação: ainda não perguntamos ao servidor.
 * É `verificando` de propósito — dizer "anonimo" antes de perguntar seria afirmar o que
 * não se sabe, e é justamente o estado "carregando" do §15.3.
 */
let atual: EstadoDaSessao = { situacao: 'verificando' };

const ouvintes = new Set<() => void>();
let emVoo: Promise<EstadoDaSessao> | null = null;

function publicar(estado: EstadoDaSessao): void {
  atual = estado;
  for (const ouvinte of ouvintes) ouvinte();
}

/** Snapshot para `useSyncExternalStore` (o valor é substituído, nunca mutado). */
export function lerSessao(): EstadoDaSessao {
  return atual;
}

/** Snapshot do servidor e da hidratação: sempre o mesmo objeto, sempre o anônimo. */
export function lerSessaoNoServidor(): EstadoDaSessao {
  return ESTADO_ANONIMO;
}

export function assinarSessao(ouvinte: () => void): () => void {
  ouvintes.add(ouvinte);
  return () => {
    ouvintes.delete(ouvinte);
  };
}

/**
 * Pergunta ao servidor quem está autenticado. Chamadas simultâneas compartilham a mesma
 * requisição (`emVoo`) — abrir três telas não gera três `GET /sessoes/atual`.
 *
 * `forcar` existe para a ação explícita "tentar de novo" depois de falha: aí a pergunta
 * é nova de verdade e não pode ser respondida pelo resultado anterior.
 */
export function verificarSessao(forcar = false): Promise<EstadoDaSessao> {
  if (emVoo && !forcar) return emVoo;

  const promessa = obterSessaoAtual()
    .then((resultado) => {
      if (!resultado.ok) {
        publicar({ situacao: 'indisponivel' });
      } else if (!resultado.dados.autenticado) {
        publicar({ situacao: 'anonimo' });
      } else {
        publicar({
          situacao: 'autenticado',
          perfil: resultado.dados.perfil,
          estadoConta: resultado.dados.estadoConta,
        });
      }
      return atual;
    })
    .finally(() => {
      emVoo = null;
    });

  emVoo = promessa;
  return promessa;
}

/**
 * Aplica o estado que uma operação bem-sucedida devolveu (login, cadastro) — sem uma
 * segunda ida ao servidor, que poderia devolver dado mais novo e contradizer a tela.
 */
export function aplicarSessao(sessao: SessaoAtual): void {
  if (!sessao.autenticado) {
    publicar({ situacao: 'anonimo' });
    return;
  }
  publicar({
    situacao: 'autenticado',
    perfil: sessao.perfil,
    estadoConta: sessao.estadoConta,
  });
}

/** Logout: o servidor já invalidou a sessão; aqui a interface deixa de mostrá-la. */
export function esquecerSessao(): void {
  publicar({ situacao: 'anonimo' });
}
