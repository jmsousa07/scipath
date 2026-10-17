'use client';

import { useRouter } from 'next/navigation';
import { useTransition } from 'react';
import { Button } from '@/components/ui/Button';

/**
 * Ação "tentar de novo" do estado `erro de rede/servidor` (RNF004: toda falha diz o que
 * aconteceu E o que fazer).
 *
 * Por que é um componente de cliente: recarregar os dados de uma página renderizada no
 * SERVIDOR é `router.refresh()` — reexecuta a busca no servidor e entrega o HTML novo,
 * sem recarregar o documento inteiro e sem perder a URL (a lista é a mesma URL pedida).
 * O botão reusa `<Button>` com `loading`, que já desabilita durante a operação e evita
 * a segunda tentativa antes de a primeira terminar.
 */
export function Recarregar() {
  const router = useRouter();
  const [pendente, iniciar] = useTransition();

  return (
    <Button
      variant="primary"
      size="sm"
      loading={pendente}
      onClick={() => iniciar(() => router.refresh())}
    >
      Tentar de novo
    </Button>
  );
}
