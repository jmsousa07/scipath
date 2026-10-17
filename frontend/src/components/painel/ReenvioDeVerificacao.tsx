'use client';

import { useEffect, useState } from 'react';
import { SubmitButton } from '@/components/ui/Button';
import { FormState } from '@/components/ui/FormState';
import { DESFECHO_DUPLICADO, type Desfecho } from '@/lib/sessao/erros';
import {
  desfechoDeReenvioAceito,
  reenviarVerificacaoEmail,
  segundosDeEspera,
} from '@/lib/sessao/navegador';
import { useEnvioUnico } from '@/components/sessao/useEnvioUnico';

/**
 * Reenvio do e-mail de confirmação na ÁREA DO USUÁRIO (ADR-0003 (c), (h); RF008/RF011).
 *
 * Por que existe além do aviso persistente do layout (E3.6): o §15.2 dá à área do usuário a
 * tarefa de resumir o estado da conta, e o estado que muda o que a pessoa pode fazer é a
 * verificação de e-mail. Quem abre esta tela está justamente procurando o que falta — e a
 * resposta certa inclui o BOTÃO, não uma instrução para procurar o aviso em outra parte da
 * página.
 *
 * Diferença para o aviso do layout: aquele é a lembrete que acompanha a navegação em todas as
 * páginas; este vive na tela de estado da conta. Os dois falam com o MESMO endpoint e obedecem
 * à mesma regra de cooldown de 60 s do contrato — se um reenvio acontecer em qualquer um dos
 * dois, o outro recebe o 429 com `Retry-After` e mostra a espera. Quem manda é o servidor.
 *
 * Estados do §15.3 exercitados aqui: `envio em andamento` (botão desabilitado + `aria-busy`),
 * `tentativa duplicada` (trava por `ref` no `useEnvioUnico` — clique/Enter repetido não vira um
 * segundo envio), `sucesso` (202, com a contagem calculada do instante que o SERVIDOR devolveu em
 * `podeReenviarEm`) e `falha recuperável` (rede, 5xx e o 429 do cooldown, com o tempo do
 * `Retry-After`). Nenhum tempo é inventado no cliente: ou vem do servidor, ou não é mostrado.
 *
 * Ele é renderizado pela PÁGINA só quando a conta está `PENDENTE_VERIFICACAO` — o estado vem da
 * leitura de sessão do servidor, não de uma suposição do cliente.
 */
export function ReenvioDeVerificacao() {
  const [desfecho, setDesfecho] = useState<Desfecho | null>(null);
  const [aguardarS, setAguardarS] = useState(0);

  const envio = useEnvioUnico(reenviar);

  // Um tique por segundo enquanto houver espera; o valor inicial vem da resposta do servidor.
  useEffect(() => {
    if (aguardarS <= 0) return;
    const relogio = setTimeout(() => setAguardarS((restante) => restante - 1), 1000);
    return () => clearTimeout(relogio);
  }, [aguardarS]);

  async function reenviar(): Promise<Desfecho> {
    const resultado = await reenviarVerificacaoEmail();
    if (resultado.ok) {
      const agora = Date.now();
      setAguardarS(segundosDeEspera(resultado.dados, agora));
      const aceito = desfechoDeReenvioAceito(resultado.dados, agora);
      setDesfecho(aceito);
      return aceito;
    }
    setAguardarS(resultado.desfecho.aguardarS ?? 0);
    setDesfecho(resultado.desfecho);
    return resultado.desfecho;
  }

  const duplicado = envio.estado === 'duplicate-attempt';
  const desfechoExibido = duplicado ? DESFECHO_DUPLICADO : desfecho;

  /**
   * O `DESFECHO_DUPLICADO` compartilhado (E3.6) fala de conta e sessão — verdade no cadastro e
   * no login, texto errado para um reenvio de e-mail. Aqui o estado é o mesmo (`tentativa
   * duplicada`, com o mesmo `data-state`), e a frase é a desta operação: repetir não manda um
   * segundo e-mail nem reinicia o cooldown.
   */
  const tituloDuplicado = duplicado ? 'Este reenvio já está em andamento' : desfechoExibido?.titulo;
  const mensagemDuplicada = duplicado
    ? 'Recebemos este pedido há instantes e ele ainda está sendo processado. Espere a resposta antes de pedir de novo: repetir não envia um segundo e-mail e ainda consome o intervalo de 60 segundos do contrato.'
    : desfechoExibido?.mensagem;

  return (
    <div data-aviso="reenvio-de-verificacao">
      <form onSubmit={envio.submeter} aria-busy={envio.pendente} noValidate>
        <SubmitButton pending={envio.pendente} variant="primary" disabled={aguardarS > 0}>
          {aguardarS > 0 ? `Reenviar em ${aguardarS} s` : 'Reenviar e-mail de confirmação'}
        </SubmitButton>
      </form>

      {envio.estado !== 'inicial' && desfechoExibido ? (
        <div className="mt-4">
          <FormState
            id="reenvio-resumo"
            kind={envio.pendente ? 'submitting' : desfechoExibido.estado}
            title={tituloDuplicado}
            message={mensagemDuplicada}
          />
        </div>
      ) : (
        <p className="sc-helper mt-3">
          O link vale 24 h e só pode ser usado uma vez. Se não chegar, confira o lixo eletrônico do
          e-mail cadastrado — o endereço da conta não é exibido aqui, nem em nenhuma tela da
          plataforma.
        </p>
      )}
    </div>
  );
}
