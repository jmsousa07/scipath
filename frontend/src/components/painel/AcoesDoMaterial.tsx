'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useRef, useState } from 'react';
import { Button } from '@/components/ui/Button';
import { FormState } from '@/components/ui/FormState';
import { entrarHref } from '@/components/ui/PageState';
import { Recarregar } from '@/components/ui/Recarregar';
import type { EstadoDeMaterial } from '@/lib/api/tipos';
import {
  AJUDA_DA_ACAO,
  CONFIRMACAO_DE_EXCLUSAO,
  DESFECHO_EM_ANDAMENTO,
  ROTULO_DA_ACAO,
  acoesDoEstado,
  desfechoDeEstadoMudado,
  desfechoDeMaterialExcluido,
  estadoDeDestino,
  type AcaoDeMaterial,
} from '@/lib/materiais/acoes';
import { rotaEditarMaterial, rotaPreservada } from '@/lib/rotas';
import type { Desfecho } from '@/lib/sessao/erros';
import { esquecerSessao } from '@/lib/sessao/estado';
import { excluirMaterialProprio, mudarEstadoMaterialProprio } from '@/lib/sessao/navegador';

/**
 * Ações do dono sobre UM material da lista (RF015, RF017; PAGE_BLUEPRINTS.md §12).
 *
 * O que este componente é: os botões do material, a confirmação da exclusão e o resumo do
 * resultado. O que ele NÃO é: controle de autorização. A derivação "que ações este estado
 * oferece" é apresentação (`lib/materiais/acoes.ts`), e quem recusa é o servidor — esconder
 * o botão nunca protegeu nada (RNF010, PAGE_BLUEPRINTS §12).
 *
 * Como uma ação muda a tela, em três passos:
 *   1. a escrita sai pelo proxy da MESMA origem, com o `X-CSRF-Token` da cookie
 *      `XSRF-TOKEN` (`lib/sessao/navegador.ts`) — nada de `fetch` solto aqui;
 *   2. o desfecho vai para o resumo, com o estado novo que o SERVIDOR devolveu (RF017:
 *      "o usuário recebe confirmação");
 *   3. `router.refresh()` reexecuta a leitura da página no servidor, e é assim que o
 *      estado novo aparece NA LISTA — sem recarregar o documento e sem o cliente manter
 *      uma cópia própria do acervo, que divergiria do servidor (RNF010).
 *
 * Por que este componente continua montado depois da exclusão (em vez de sumir junto com
 * os botões): é o que preserva o resumo de sucesso. A exclusão leva o material a
 * `EXCLUIDO`, estado em que não há ação nenhuma, e se o resumo morasse junto dos botões ele
 * desapareceria no mesmo instante em que a pessoa mais precisa lê-lo.
 *
 * Os estados que este componente produz, do §15.3: `envio em andamento` (botão desabilitado
 * + `aria-busy`, e a trava por `ref` que fecha a janela entre o clique e o re-render),
 * `tentativa duplicada` (`DESFECHO_EM_ANDAMENTO`), `sucesso`, `falha recuperável` (com o
 * `detail` do contrato) e `sessão expirada` (401 → o cabeçalho esquece a sessão e o login
 * preserva `/painel/materiais`).
 *
 * A confirmação da exclusão é um `<dialog>` nativo: `showModal()` prende o foco, o `Esc`
 * fecha e o leitor de tela anuncia o nome acessível do diálogo. Sem JavaScript o diálogo
 * não abre e a exclusão não acontece — o mesmo vale para TODA escrita desta tela, que é o
 * desenho do produto (a escrita passa pelo cliente, ADR-0003 (e)); o que não depende de
 * JavaScript é o conteúdo e o estado da lista, que chegam prontos no HTML.
 */
export function AcoesDoMaterial({
  slug,
  titulo,
  estado,
  contaVerificada,
}: {
  slug: string;
  titulo: string;
  estado: EstadoDeMaterial;
  contaVerificada: boolean;
}) {
  const router = useRouter();
  const [desfecho, setDesfecho] = useState<Desfecho | null>(null);
  const [pendente, setPendente] = useState<AcaoDeMaterial | null>(null);
  /** Trava de envio único — o mesmo desenho do `useEnvioUnico`, por linha em vez de por form. */
  const emAndamento = useRef(false);
  const dialogo = useRef<HTMLDialogElement>(null);

  const acoes = acoesDoEstado(estado);

  function ocupar(): boolean {
    if (emAndamento.current) return false;
    emAndamento.current = true;
    return true;
  }

  function liberar(): void {
    emAndamento.current = false;
    setPendente(null);
  }

  async function mudarEstado(acao: AcaoDeMaterial): Promise<void> {
    const destino = estadoDeDestino(acao);
    if (destino === null) return;
    if (!ocupar()) {
      setDesfecho(DESFECHO_EM_ANDAMENTO);
      return;
    }
    setPendente(acao);
    setDesfecho(null);

    const resultado = await mudarEstadoMaterialProprio(slug, destino, { contaVerificada });

    if (!resultado.ok) {
      // 401 numa escrita com identidade: a sessão acabou. O cabeçalho para de mostrar uma
      // sessão que o servidor já não reconhece (RNF010 — a interface reflete, não protege).
      if (resultado.desfecho.sessaoEncerrada) esquecerSessao();
      setDesfecho(resultado.desfecho);
      liberar();
      return;
    }

    setDesfecho(desfechoDeEstadoMudado(resultado.dados));
    liberar();
    router.refresh();
  }

  async function excluir(): Promise<void> {
    dialogo.current?.close();
    if (!ocupar()) {
      setDesfecho(DESFECHO_EM_ANDAMENTO);
      return;
    }
    setPendente('excluir');
    setDesfecho(null);

    const resultado = await excluirMaterialProprio(slug);

    if (!resultado.ok) {
      if (resultado.desfecho.sessaoEncerrada) esquecerSessao();
      setDesfecho(resultado.desfecho);
      liberar();
      return;
    }

    // 204 não traz corpo: o título vem da lista que a tela já tinha, e é ele que identifica
    // QUAL material foi excluído — "material excluído" sem nome não é confirmação (RF017).
    setDesfecho(desfechoDeMaterialExcluido(titulo));
    liberar();
    router.refresh();
  }

  // Só o estado terminal chega aqui sem ação. Sem resumo (nada aconteceu nesta tela), o
  // componente não renderiza nada — é o que a bateria de HTML afirma para `EXCLUIDO`.
  if (acoes.length === 0 && desfecho === null) return null;

  const idDoResumo = `material-resumo-${slug}`;
  const idDoDialogo = `material-exclusao-${slug}`;

  return (
    <div className="mt-4" data-acoes={slug}>
      {acoes.length > 0 ? (
        <ul className="flex list-none flex-wrap items-center gap-2 p-0">
          {acoes.map((acao) => (
            <li key={acao}>
              {acao === 'editar' ? (
                <Link
                  className="sc-btn sc-btn--ghost sc-btn--sm"
                  href={rotaEditarMaterial(slug)}
                  data-acao="editar"
                  data-material={slug}
                >
                  {ROTULO_DA_ACAO.editar}
                  <span className="sc-visually-hidden"> o material {titulo}</span>
                </Link>
              ) : acao === 'excluir' ? (
                <Button
                  variant="ghost"
                  size="sm"
                  data-acao="excluir"
                  data-material={slug}
                  loading={pendente === 'excluir'}
                  onClick={() => dialogo.current?.showModal()}
                >
                  {ROTULO_DA_ACAO.excluir}
                  <span className="sc-visually-hidden"> o material {titulo}</span>
                </Button>
              ) : (
                <>
                  <Button
                    variant="ghost"
                    size="sm"
                    data-acao={acao}
                    data-material={slug}
                    aria-describedby={`material-ajuda-${slug}-${acao}`}
                    loading={pendente === acao}
                    onClick={() => void mudarEstado(acao)}
                  >
                    {ROTULO_DA_ACAO[acao]}
                    <span className="sc-visually-hidden"> — o material {titulo}</span>
                  </Button>
                  {/* O que a ação FAZ, dito a quem usa leitor de tela — o rótulo do botão só
                      tem o verbo (RNF004: a mensagem diz o que acontece). Visualmente esta
                      frase não aparece: o botão fica ao lado do próprio estado, que já a
                      explica na linha do tempo da página. */}
                  <span className="sc-visually-hidden" id={`material-ajuda-${slug}-${acao}`}>
                    {AJUDA_DA_ACAO[acao]}
                  </span>
                </>
              )}
            </li>
          ))}
        </ul>
      ) : null}

      {desfecho ? (
        <div className="mt-4">
          <FormState
            id={idDoResumo}
            kind={desfecho.estado}
            tituloNivel={4}
            title={desfecho.titulo}
            message={desfecho.mensagem}
            action={
              <>
                {desfecho.sessaoEncerrada ? (
                  <Link
                    className="sc-btn sc-btn--primary sc-btn--sm"
                    href={rotaPreservada(entrarHref('/painel/materiais'))}
                  >
                    Entrar novamente
                  </Link>
                ) : null}
                {desfecho.republicacaoBloqueada ? (
                  <Link className="sc-btn sc-btn--ghost sc-btn--sm" href="/painel">
                    Ver o estado da conta e reenviar a confirmação
                  </Link>
                ) : null}
                {desfecho.estado !== 'success' && !desfecho.sessaoEncerrada ? <Recarregar /> : null}
              </>
            }
          />
        </div>
      ) : null}

      {/* Confirmação da exclusão (RF017, ADR-0011 §7): diálogo explícito, e só na exclusão.
          Retirar e repor são reversíveis e NÃO pedem confirmação — pedir confirmação para
          tudo ensina a pessoa a clicar em "sim" sem ler, que é o oposto do objetivo. */}
      {acoes.includes('excluir') ? (
        <dialog
          ref={dialogo}
          className="sc-card sc-card--pad"
          aria-labelledby={idDoDialogo}
          data-dialogo="exclusao"
          data-material={slug}
        >
          <h4 className="sc-notice__title" id={idDoDialogo}>
            {CONFIRMACAO_DE_EXCLUSAO.titulo}
          </h4>
          <p className="sc-notice__msg">
            Material: <strong>{titulo}</strong>
          </p>
          <p className="sc-notice__msg">{CONFIRMACAO_DE_EXCLUSAO.irreversivel}</p>
          <p className="sc-notice__msg">{CONFIRMACAO_DE_EXCLUSAO.retencao}</p>
          <p className="sc-helper">{CONFIRMACAO_DE_EXCLUSAO.alternativa}</p>
          <div className="sc-notice__actions mt-4">
            <Button
              variant="primary"
              size="sm"
              data-acao="confirmar-exclusao"
              data-material={slug}
              loading={pendente === 'excluir'}
              onClick={() => void excluir()}
            >
              Excluir definitivamente
              <span className="sc-visually-hidden"> o material {titulo}</span>
            </Button>
            <Button
              variant="ghost"
              size="sm"
              data-acao="cancelar-exclusao"
              data-material={slug}
              onClick={() => dialogo.current?.close()}
            >
              Cancelar
            </Button>
          </div>
        </dialog>
      ) : null}
    </div>
  );
}
