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
 *
 * Onde mora, e por quê: nasceu em `src/app/explorar/` (card E2.8) e foi PROMOVIDO para a
 * camada de componentes no card E2.7, quando a página inicial virou o segundo consumidor
 * do mesmo estado — `DESIGN_SYSTEM.md` §0.2 ("tela nova usa componente existente ou
 * promove um ao sistema") e RNF003 (estado equivalente tem o mesmo comportamento em toda
 * página). Nada do comportamento mudou; só o endereço do arquivo, para que a home não
 * importe código de dentro da pasta de outra rota.
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
