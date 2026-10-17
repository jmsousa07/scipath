'use client';

import { useEffect, useSyncExternalStore } from 'react';
import {
  assinarSessao,
  lerSessao,
  lerSessaoNoServidor,
  verificarSessao,
  type EstadoDaSessao,
} from '@/lib/sessao/estado';

/**
 * Lê o estado da sessão e GARANTE que ele foi perguntado ao servidor uma vez.
 *
 * O `useEffect` roda em cada componente que usa o hook, mas a pergunta é deduplicada no
 * store (`verificarSessao`) — o cabeçalho, o aviso de verificação e a página de acesso
 * não geram três requisições. O efeito só existe para a primeira leitura; depois disso,
 * quem muda o estado (login, logout) publica direto para todos.
 */
export function useSessao(): EstadoDaSessao {
  const estado = useSyncExternalStore(assinarSessao, lerSessao, lerSessaoNoServidor);

  useEffect(() => {
    void verificarSessao();
  }, []);

  return estado;
}
