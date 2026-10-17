'use client';

import { useRef, useState, type FormEvent } from 'react';
import { DESFECHO_DUPLICADO, type Desfecho, type EstadoDoFormulario } from '@/lib/sessao/erros';

/**
 * Envio ÚNICO de formulário, com os estados do §15.3 que dependem de comportamento:
 * `submitting` (envio em andamento), `duplicate-attempt` (tentativa repetida) e o
 * resultado do envio — `invalid-field`, `recoverable-failure`, `success`.
 *
 * A trava é dupla, de propósito: `pendente` desabilita o botão (`<SubmitButton>`), e o
 * `ref` `emAndamento` fecha a janela entre o clique e o re-render que desabilita o botão
 * — o mesmo desenho exercitado no `/_estados` (E1.7). Sem o `ref`, um clique rápido (ou
 * um Enter repetido) criaria duas contas com o mesmo formulário.
 *
 * `executar` devolve o `Desfecho` já traduzido (`lib/sessao/erros.ts` e
 * `lib/sessao/navegador.ts`): aqui não se decide sobre status HTTP.
 */
export type EnvioUnico = {
  /** `inicial` = nada foi enviado ainda; os demais são os estados do DRS §15.3. */
  estado: EstadoDoFormulario | 'inicial';
  desfecho: Desfecho | null;
  pendente: boolean;
  submeter: (evento: FormEvent<HTMLFormElement>) => void;
};

export function useEnvioUnico(executar: () => Promise<Desfecho>): EnvioUnico {
  const [desfecho, setDesfecho] = useState<Desfecho | null>(null);
  const [pendente, setPendente] = useState(false);
  const emAndamento = useRef(false);

  function submeter(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault();

    if (emAndamento.current) {
      setDesfecho(DESFECHO_DUPLICADO);
      return;
    }

    emAndamento.current = true;
    setPendente(true);
    setDesfecho(null);

    void executar()
      .then((resultado) => setDesfecho(resultado))
      .finally(() => {
        emAndamento.current = false;
        setPendente(false);
      });
  }

  return {
    estado: pendente ? 'submitting' : (desfecho?.estado ?? 'inicial'),
    desfecho,
    pendente,
    submeter,
  };
}
