'use client';

import Link from 'next/link';
import { useEffect, useRef, useState } from 'react';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { useSessao } from './useSessao';
import {
  desfechoDeReenvioAceito,
  reenviarVerificacaoEmail,
  segundosDeEspera,
} from '@/lib/sessao/navegador';
import { DESFECHO_DUPLICADO, type Desfecho } from '@/lib/sessao/erros';

/**
 * Aviso PERSISTENTE de verificação de e-mail pendente (ADR-0003 (h), DRS §13.1).
 *
 * Por que existe, em duas frases: publicar exige conta `ATIVO` (ADR-0003 (b)), e uma
 * conta recém-criada nasce `PENDENTE_VERIFICACAO`. Esconder essa pendência — ou só
 * descobri-la na hora de publicar — seria o "muro silencioso" que o ADR proíbe. Então o
 * aviso acompanha o usuário em todas as páginas, diz o que falta, diz o que NÃO falta
 * (navegar, buscar e editar o perfil seguem liberados) e oferece o reenvio ali mesmo.
 *
 * Nada de dado fictício: o estado mostrado é o `estadoConta` que o servidor devolveu em
 * `GET /api/sessoes/atual`, e ele só aparece quando a sessão está autenticada.
 *
 * Os estados do §15.3, no reenvio:
 *   - `submitting`            botão desabilitado + `aria-busy` (sem envio duplo);
 *   - `duplicate-attempt`     clique que escapa antes do re-render, travado por `ref`;
 *   - `success`              202 do contrato: `reenviadoEm`/`podeReenviarEm` governam a
 *                            contagem exibida — o tempo vem do servidor, não de um
 *                            relógio inventado no cliente;
 *   - `recoverable-failure`   429 (`Retry-After` → contagem), 500 e falha de rede, com o
 *                            botão disponível de novo quando faz sentido.
 */
export function AvisoDeVerificacao() {
  const sessao = useSessao();
  const [desfecho, setDesfecho] = useState<Desfecho | null>(null);
  const [enviando, setEnviando] = useState(false);
  const [aguardarS, setAguardarS] = useState(0);
  const emAndamento = useRef(false);

  // Contagem do cooldown: um tique por segundo enquanto houver espera a mostrar. O valor
  // inicial vem da resposta do servidor (202 `podeReenviarEm` ou 429 `Retry-After`).
  useEffect(() => {
    if (aguardarS <= 0) return;
    const relogio = setTimeout(() => setAguardarS((restante) => restante - 1), 1000);
    return () => clearTimeout(relogio);
  }, [aguardarS]);

  if (sessao.situacao !== 'autenticado' || sessao.estadoConta !== 'PENDENTE_VERIFICACAO') {
    return null;
  }

  async function reenviar() {
    if (emAndamento.current) {
      setDesfecho(DESFECHO_DUPLICADO);
      return;
    }
    emAndamento.current = true;
    setEnviando(true);
    setDesfecho(null);

    const resultado = await reenviarVerificacaoEmail();
    emAndamento.current = false;
    setEnviando(false);

    if (resultado.ok) {
      const agora = Date.now();
      setAguardarS(segundosDeEspera(resultado.dados, agora));
      setDesfecho(desfechoDeReenvioAceito(resultado.dados, agora));
      return;
    }

    setDesfecho(resultado.desfecho);
    setAguardarS(resultado.desfecho.aguardarS ?? 0);
  }

  const estado = enviando ? 'submitting' : (desfecho?.estado ?? 'atencao');
  const bloqueado = enviando || aguardarS > 0;

  return (
    <section
      className="sc-notice sc-notice--atencao sc-notice--pagina"
      data-state={estado}
      data-aviso="verificacao-de-email"
      aria-labelledby="aviso-verificacao-titulo"
    >
      <Badge tone="yellow">e-mail não confirmado</Badge>
      <h2 className="sc-notice__title" id="aviso-verificacao-titulo">
        Falta confirmar o seu e-mail para publicar
      </h2>
      <p className="sc-notice__msg">
        Sua conta existe e você pode navegar, buscar e editar o seu perfil agora mesmo. O que ainda
        não está liberado é <strong>publicar material</strong>: a publicação exige o e-mail
        confirmado, e a confirmação chega por um link enviado ao endereço cadastrado.
      </p>

      {desfecho ? (
        <p className={desfecho.estado === 'success' ? 'sc-notice__msg' : 'sc-field__msg'} role="status">
          <strong>{desfecho.titulo}.</strong> {desfecho.mensagem}
        </p>
      ) : (
        <p className="sc-helper">
          Confira a caixa de entrada (e o lixo eletrônico) do e-mail cadastrado. O link vale 24 h e
          só pode ser usado uma vez.
        </p>
      )}

      <div className="sc-notice__actions">
        <Button variant="primary" size="sm" loading={enviando} disabled={bloqueado} onClick={reenviar}>
          {aguardarS > 0 ? `Reenviar em ${aguardarS} s` : 'Reenviar e-mail de confirmação'}
        </Button>
        <Link className="sc-btn sc-btn--ghost sc-btn--sm" href="/explorar">
          Explorar materiais enquanto isso
        </Link>
      </div>
    </section>
  );
}
