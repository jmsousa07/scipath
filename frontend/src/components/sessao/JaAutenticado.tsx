'use client';

import Link from 'next/link';
import { Button } from '@/components/ui/Button';
import { PageState } from '@/components/ui/PageState';
import { rotaPreservada } from '@/lib/rotas';
import { useSair } from './useSair';
import { useSessao } from './useSessao';

/**
 * Estado de SUCESSO da consulta de sessão: quem já está autenticado abrindo a tela de
 * acesso (DRS §15.3, estado de página `sucesso`).
 *
 * Por que existe, e onde ele vive: quem já tem sessão aberta não deve receber um
 * formulário de login como se não tivesse. Este bloco — e não o formulário — é o que a
 * tela mostra nesse caso: de quem é a sessão, o caminho de saída e o caminho de volta ao
 * destino que a pessoa queria abrir (`?proximo=`, preservado pelo desvio da §15.3).
 *
 * Uma decisão explícita aqui: **o nome público não linka** `/u/{slug}`. Aquela rota só
 * responde para conta verificada (ADR-0003 (b)) e mandar uma conta `PENDENTE_VERIFICACAO`
 * para um 404 seria pior que não linkar. O que falta para publicar aparece no aviso
 * persistente do site (`AvisoDeVerificacao`), que tem o reenvio.
 *
 * Isto não é controle de acesso: quem recusa operação sem sessão é o servidor (RNF010).
 * Esconder o formulário de quem já entrou é cortesia de interface, não proteção.
 */
export function JaAutenticado({ proximo }: { proximo?: string }) {
  const sessao = useSessao();
  const { sair, saindo, falha } = useSair();

  const pendente = sessao.estadoConta === 'PENDENTE_VERIFICACAO';

  return (
    <PageState kind="success">
      <h2 className="sc-subhead mt-0">Você já está autenticado</h2>
      <p className="sc-body sc-measure">
        A sessão aberta neste navegador é de <strong>{sessao.perfil?.nomePublico ?? ''}</strong>
        {pendente
          ? ' — com o e-mail ainda não confirmado, o que libera navegar e editar o perfil, mas não publicar.'
          : '.'}{' '}
        Para entrar com outra conta, encerre esta sessão primeiro.
      </p>
      <div className="sc-notice__actions">
        {proximo ? (
          <Link className="sc-btn sc-btn--primary sc-btn--sm" href={rotaPreservada(proximo)}>
            Continuar de onde parei
          </Link>
        ) : (
          <Link className="sc-btn sc-btn--primary sc-btn--sm" href="/explorar">
            Explorar materiais
          </Link>
        )}
        <Button variant="ghost" size="sm" loading={saindo} onClick={sair}>
          Sair desta conta
        </Button>
      </div>
      {falha ? (
        <p className="sc-field__msg" role="alert">
          {falha}
        </p>
      ) : null}
    </PageState>
  );
}
