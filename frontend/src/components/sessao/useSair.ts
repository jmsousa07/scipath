'use client';

import { useRouter } from 'next/navigation';
import { useState } from 'react';
import { esquecerSessao } from '@/lib/sessao/estado';
import { encerrarSessao } from '@/lib/sessao/navegador';

/**
 * Ação de logout (RF009, CA004), num lugar só — cabeçalho e tela de acesso usam a mesma.
 *
 * Ordem deliberada: primeiro o SERVIDOR (`DELETE /api/sessoes/atual`, que invalida a
 * sessão de verdade e expira a cookie), depois a interface. Se o pedido falhar, a sessão
 * continua mostrada e a falha é dita — anunciar "você saiu" sem o servidor ter encerrado
 * seria mentira sobre segurança (RNF010 é o outro lado disso: a tela não protege dado).
 *
 * O 401 aqui NÃO é erro: o contrato é idempotente ("sem sessão válida também responde
 * 204"), porque sair não deve depender de a sessão ainda estar viva. Um `sessaoEncerrada`
 * na falha significa exatamente isso — a sessão já não valia.
 *
 * Depois de sair, a navegação vai para a página inicial e `router.refresh()` refaz a
 * renderização de servidor: as telas que mostram dado de sessão voltam ao estado anônimo.
 */
export type Sair = {
  sair: () => void;
  saindo: boolean;
  falha: string | null;
};

export function useSair(): Sair {
  const router = useRouter();
  const [saindo, setSaindo] = useState(false);
  const [falha, setFalha] = useState<string | null>(null);

  async function sair() {
    if (saindo) return;
    setSaindo(true);
    setFalha(null);

    const resultado = await encerrarSessao();
    const encerrada = resultado.ok || resultado.desfecho.sessaoEncerrada === true;
    setSaindo(false);

    if (!encerrada) {
      setFalha(resultado.ok ? null : resultado.desfecho.mensagem);
      return;
    }

    esquecerSessao();
    router.replace('/');
    router.refresh();
  }

  return { sair: () => void sair(), saindo, falha };
}
