'use client';

import { Button } from '@/components/ui/Button';
import { PageState } from '@/components/ui/PageState';
import { verificarSessao } from '@/lib/sessao/estado';
import { useSessao } from './useSessao';

/**
 * Os estados de PÁGINA do §15.3 que a consulta de sessão das telas de acesso produz
 * antes de o formulário entrar em ação.
 *
 * Por que a tela de acesso "depende de dados": ela depende do estado da sessão — quem já
 * está autenticado não deve receber um formulário de login. É o servidor que diz quem
 * está autenticado (`GET /api/sessoes/atual`, 200 com `autenticado: false` para o
 * visitante, nunca 401). Dessa dependência saem estes estados reais:
 *
 *   - `carregando`            a pergunta foi feita e ainda não voltou;
 *   - `erro de rede/servidor` a pergunta não teve resposta — o formulário continua na tela
 *                             (dá para tentar entrar mesmo assim) e há "Conferir de novo";
 *   - `sucesso`               NÃO é renderizado aqui: quando a resposta diz que há sessão,
 *                             quem mostra o bloco é `JaAutenticado` (o formulário sai de
 *                             cena e o bloco de sessão entra) — um dono por estado, sem
 *                             dois blocos dizendo a mesma coisa;
 *   - `sessão expirada`       também não: vem da URL (`?motivo=sessao-expirada`, o desvio
 *                             que veio de um 401) e a PÁGINA a renderiza no servidor, para
 *                             o aviso sair pronto no HTML entregue (`app/entrar/page.tsx`);
 *   - `não autorizado`        nenhum GET deste fluxo pode produzi-lo (o 403 exige escrita
 *                             com CSRF); o mapeamento dele existe no formulário, onde a
 *                             escrita acontece (`lib/sessao/erros.ts`);
 *   - `não encontrado`        idem: não há recurso no caminho desta consulta;
 *   - `vazio`                 não se aplica: não há lista nem busca nesta tela.
 */
export function EstadosDaConsultaDeSessao() {
  const sessao = useSessao();

  if (sessao.situacao === 'verificando') {
    return (
      <PageState
        kind="loading"
        title="Conferindo se você já está autenticado"
        message="Perguntamos ao servidor se existe uma sessão aberta neste navegador. Se existir, esta tela mostra de quem é a sessão em vez de pedir login de novo."
      />
    );
  }

  if (sessao.situacao === 'indisponivel') {
    return (
      <PageState
        kind="network-error"
        title="Não foi possível conferir a sua sessão"
        message="O servidor não respondeu à consulta de sessão. Você pode tentar entrar pelo formulário abaixo — se o problema continuar, tente de novo em alguns instantes."
        action={
          <Button variant="ghost" size="sm" onClick={() => void verificarSessao(true)}>
            Conferir de novo
          </Button>
        }
      />
    );
  }

  return null;
}
