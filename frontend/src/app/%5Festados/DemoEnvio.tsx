'use client';

import { useEffect, useRef, useState } from 'react';
import { SubmitButton } from '@/components/ui/Button';
import { TextField } from '@/components/ui/Field';
import { FormState } from '@/components/ui/FormState';

/**
 * Demonstração INTERATIVA de `envio em andamento` e `falha recuperável`.
 *
 * Por que só isto é cliente: os outros onze estados são estruturas que aparecem
 * prontas no HTML entregue pelo servidor (não precisam de JS para existir). O que
 * precisa de comportamento é a promessa de "sem envio duplo" e de "dados
 * preservados" — e é aqui que ela é exercitada de verdade, no clique.
 *
 * NÃO existe servidor nesta página: o resultado é simulado no cliente e o rótulo
 * diz isso. Nenhum dado real de plataforma é criado (§20.1).
 */

type Status = 'idle' | 'submitting' | 'success' | 'recoverable-failure';

export function DemoEnvio() {
  const [status, setStatus] = useState<Status>('idle');
  const [titulo, setTitulo] = useState('Guia de estudo — exemplo digitado à mão');
  const [simularFalha, setSimularFalha] = useState(false);

  const emAndamento = useRef(false);
  const relogio = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(
    () => () => {
      if (relogio.current) clearTimeout(relogio.current);
    },
    [],
  );

  function enviar(evento: React.FormEvent<HTMLFormElement>) {
    evento.preventDefault();
    // Trava de reentrada: mesmo que o clique escape antes do re-render que
    // desabilita o botão, o envio não acontece duas vezes.
    if (emAndamento.current) return;
    emAndamento.current = true;
    setStatus('submitting');

    relogio.current = setTimeout(() => {
      emAndamento.current = false;
      setStatus(simularFalha ? 'recoverable-failure' : 'success');
    }, 1200);
  }

  return (
    <div data-state="demo-envio" className="space-y-4">
      <p className="sc-caption">
        Demonstração interativa — sem servidor: o resultado é simulado no navegador. Serve para
        exercitar o <strong>botão desabilitado durante o envio</strong> (sem envio duplo) e a
        preservação do texto digitado quando o envio falha.
      </p>

      <form className="space-y-4" onSubmit={enviar} aria-busy={status === 'submitting'}>
        {status === 'submitting' ? (
          <FormState kind="submitting" id="resumo-envio" />
        ) : null}
        {status === 'success' ? <FormState kind="success" id="resumo-envio" /> : null}
        {status === 'recoverable-failure' ? (
          <FormState
            kind="recoverable-failure"
            id="resumo-envio"
            message="O servidor simulado recusou o envio. Repare que o título digitado CONTINUA no campo abaixo — nada foi apagado (RNF004)."
            action={
              <SubmitButton pending={false} variant="ghost" size="sm">
                Tentar novamente
              </SubmitButton>
            }
          />
        ) : null}

        <TextField
          id="titulo-demo-envio"
          label="Título (exemplo de campo de demonstração)"
          value={titulo}
          onChange={(evento) => setTitulo(evento.target.value)}
          hint="Digite, envie e provoque a falha: o que você escreveu não se perde."
        />

        <label className="sc-field__label" htmlFor="simular-falha">
          <input
            id="simular-falha"
            type="checkbox"
            checked={simularFalha}
            onChange={(evento) => setSimularFalha(evento.target.checked)}
          />{' '}
          Simular falha do servidor (para ver o estado &quot;falha recuperável&quot;)
        </label>

        <div className="flex flex-wrap items-center gap-3">
          <SubmitButton pending={status === 'submitting'}>Enviar (simulação)</SubmitButton>
          <span className="sc-caption">
            {status === 'submitting'
              ? 'Enviando: o botão acima está desabilitado e anuncia "ocupado".'
              : 'O botão fica desabilitado do clique até a resposta.'}
          </span>
        </div>
      </form>
    </div>
  );
}
