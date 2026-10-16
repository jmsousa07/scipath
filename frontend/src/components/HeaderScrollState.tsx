'use client';

import { useEffect } from 'react';

/**
 * Aplica `.is-scrolled` no cabeçalho quando a página sai do topo — é o estado que o
 * DS2 desenha (`border-bottom` + sombra). Melhoria progressiva: sem JS o cabeçalho
 * continua legível e navegável, só não ganha a borda.
 */
export function HeaderScrollState({ targetId }: { targetId: string }) {
  useEffect(() => {
    const header = document.getElementById(targetId);
    if (!header) return;

    const update = () => {
      header.classList.toggle('is-scrolled', window.scrollY > 4);
    };

    update();
    window.addEventListener('scroll', update, { passive: true });
    return () => window.removeEventListener('scroll', update);
  }, [targetId]);

  return null;
}