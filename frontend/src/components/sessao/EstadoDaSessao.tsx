'use client';

import Link from 'next/link';
import { Button } from '@/components/ui/Button';
import { entrarHref } from '@/components/ui/PageState';
import { verificarSessao } from '@/lib/sessao/estado';
import { rotaPreservada } from '@/lib/rotas';
import { useSair } from './useSair';
import { useSessao } from './useSessao';

/**
 * Estado da sessão no cabeçalho (RF009, CA004 — "o cabeçalho reflete a sessão").
 *
 * O que ele NÃO é: um controle de acesso. Esconder ou mostrar um botão nunca protegeu
 * nada — quem recusa operação sem sessão é o servidor (RNF010 e o critério de RF009:
 * "não depende apenas de esconder botões para proteger dados"). Aqui o cabeçalho apenas
 * diz o que o servidor reportou.
 *
 * Quatro situações, e o que cada uma mostra:
 *   - `verificando`     "Verificando sessão…" — já hidratamos e ainda não perguntamos;
 *   - `anonimo`         Entrar · Criar conta. É o MESMO que o HTML entregue traz (o store
 *                       devolve o estado anônimo no servidor), então quem não executa
 *                       JavaScript vê um caminho de entrada válido;
 *   - `autenticado`     nome público + Sair. O nome NÃO linka o perfil público: `/u/{slug}`
 *                       responde só para conta verificada (ADR-0003 (b)), e mandar uma
 *                       conta pendente para um 404 seria pior que não linkar;
 *   - `indisponivel`    o servidor não respondeu: os caminhos de entrada ficam de pé, o
 *                       cabeçalho diz que não conseguiu verificar e oferece "Verificar de
 *                       novo" (RNF004) — nunca mostrando como anônimo quem não é.
 *
 * O logout mora em `useSair` porque a tela de acesso usa a mesma ação: duplicar a ordem
 * (servidor primeiro, interface depois) seria duplicar a chance de mentir sobre ela.
 */
export function EstadoDaSessao() {
  const sessao = useSessao();
  const { sair, saindo, falha } = useSair();

  if (sessao.situacao === 'verificando') {
    return (
      <p className="sc-caption" data-state="loading" role="status">
        Verificando sessão…
      </p>
    );
  }

  if (sessao.situacao === 'autenticado') {
    return (
      <div className="flex items-center gap-3" data-state="success">
        <span className="sc-caption">
          Sessão de <strong>{sessao.perfil?.nomePublico ?? ''}</strong>
        </span>
        {/*
          Caminho para a área do usuário (E3.7). O nome NÃO linka o perfil público — `/u/{slug}`
          responde só para conta verificada (ADR-0003 (b)), e mandar uma conta pendente para um
          404 seria pior que não linkar (a decisão é a mesma documentada no topo deste arquivo).
          Já `/painel` responde para qualquer conta autenticada: é a tela que diz o estado da
          conta e leva à edição do perfil.
        */}
        <Link className="sc-header__link" href="/painel">
          Área do usuário
        </Link>
        <Button variant="ghost" size="sm" loading={saindo} onClick={sair}>
          Sair
        </Button>
        {falha ? (
          <span className="sc-caption sc-field__msg" role="alert">
            {falha}
          </span>
        ) : null}
      </div>
    );
  }

  return (
    <div
      className="flex items-center gap-3"
      data-state={sessao.situacao === 'indisponivel' ? 'network-error' : 'anonimo'}
    >
      <Link className="sc-header__link" href={rotaPreservada(entrarHref())}>
        Entrar
      </Link>
      <Link className="sc-btn sc-btn--ghost sc-btn--sm" href="/cadastro">
        Criar conta
      </Link>
      {sessao.situacao === 'indisponivel' ? (
        <>
          <span className="sc-caption" role="status">
            Não foi possível verificar a sua sessão.
          </span>
          <Button variant="text" size="sm" onClick={() => void verificarSessao(true)}>
            Verificar de novo
          </Button>
        </>
      ) : null}
    </div>
  );
}
