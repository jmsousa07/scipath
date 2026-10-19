'use client';

import Link from 'next/link';
import { useState } from 'react';
import { SubmitButton } from '@/components/ui/Button';
import { CheckboxField, FileField, SelectField, TextAreaField, TextField } from '@/components/ui/Field';
import { FormState } from '@/components/ui/FormState';
import { entrarHref } from '@/components/ui/PageState';
import { Badge } from '@/components/ui/Badge';
import type { CategoriaDaPagina, ItemTaxonomia, LicencaRef, MaterialPublicado } from '@/lib/api/tipos';
import {
  desfechoDeArquivoInvalido,
  desfechoDeCamposInvalidos,
  type Desfecho,
} from '@/lib/sessao/erros';
import { esquecerSessao } from '@/lib/sessao/estado';
import { desfechoDeMaterialPublicado, publicarMaterial } from '@/lib/sessao/navegador';
import {
  avaliarArquivo,
  formatarBytes,
  validarPublicacao,
  type ErrosDeCampo,
} from '@/lib/sessao/validacao';
import { rotaMaterial, rotaPreservada } from '@/lib/rotas';
import { useEnvioUnico } from '@/components/sessao/useEnvioUnico';
import { ReenvioDeVerificacao } from './ReenvioDeVerificacao';

/**
 * Formulário de publicação de material (RF012, RF018, RN006, RN007; PAGE_BLUEPRINTS.md §11)
 * — `POST /api/materiais` pelo proxy `/api` da mesma origem (ADR-0003 (e)).
 *
 * O que este componente NÃO decide, de propósito:
 *   - **as listas de categoria, tipo e licença**: elas chegam por prop, vindas da API
 *     (`/categorias`, `/tipos-material`, `/licencas`, lidas no SERVIDOR pela página). Nenhuma
 *     lista é escrita aqui — é o que o RF018 exige ("não codificados de forma divergente em
 *     várias telas") e o que a bateria de HTML confere medindo o documento entregue;
 *   - **a licença**: nenhuma vem pré-selecionada. Quem escolhe é o autor (RN006) e a escolha
 *     é obrigatória — enviar sem escolher recebe 422 no campo `licencaSlug`;
 *   - **o limite de upload**: vem por prop, lido da configuração do ADR-0005 (`limiteBytes`).
 *     A validação local usa o mesmo número que a tela exibe; a autoridade continua sendo o
 *     servidor, e o 413 dele é mostrado com o `detail` dele.
 *
 * Os seis estados de formulário do §15.3, e o que provoca cada um aqui:
 *   - `campo inválido`         validação local (título/descrição, escolha de taxonomia, arquivo
 *                              ausente) e o 422 do SERVIDOR — a mensagem cai no campo que o
 *                              contrato aponta em `errors[]`, não num resumo genérico;
 *   - `envio em andamento`     `pending` do botão: clique/Enter desabilitado + `aria-busy`;
 *   - `falha recuperável`      falha de rede, 5xx, 400 e o 403 de CSRF. Nada do digitado é
 *                              apagado (RNF004);
 *   - `sucesso`                201 do contrato: o `slug`, o `estado` (`PUBLICADO`) e o link da
 *                              página pública (RF012, RF019);
 *   - `tentativa duplicada`    clique/Enter repetido antes da resposta, travado por `ref` no
 *                              `useEnvioUnico` — repetir não publica duas vezes;
 *   - `arquivo…inválido`       413 (acima do limite) e 415 (não é PDF) do servidor, e o arquivo
 *                              recusado pelas regras de cliente ANTES do envio — com o motivo
 *                              específico ("acima de X MB", "formato não aceito", RF013).
 *
 * Conta sem e-mail confirmado: a PÁGINA não entrega este formulário (ela explica o motivo e
 * oferece o reenvio, §11). Se a verificação cair entre a renderização e o envio, o 403 chega
 * com `publicacaoBloqueada` e o caminho de reenvio aparece no resumo — nunca um "acesso
 * negado" cru (ADR-0003 (b)).
 *
 * `sessão expirada` (§15.3, estado de página) também aparece aqui, e de propósito: o 401 de
 * `POST /materiais` significa que a sessão caiu entre carregar a página e publicar. A tela diz
 * isso, limpa o estado de sessão do cabeçalho (`esquecerSessao`) e oferece o login com o
 * destino preservado.
 */
export function FormularioPublicacao({
  categorias,
  tipos,
  licencas,
  limiteBytes,
  contaVerificada,
}: {
  categorias: CategoriaDaPagina[];
  tipos: ItemTaxonomia[];
  licencas: LicencaRef[];
  limiteBytes: number;
  contaVerificada: boolean;
}) {
  const [titulo, setTitulo] = useState('');
  const [descricao, setDescricao] = useState('');
  const [categoriaSlug, setCategoriaSlug] = useState('');
  const [tipoSlug, setTipoSlug] = useState('');
  const [licencaSlug, setLicencaSlug] = useState('');
  const [arquivo, setArquivo] = useState<File | null>(null);
  const [declaracaoDireito, setDeclaracaoDireito] = useState(false);
  const [errosLocais, setErrosLocais] = useState<ErrosDeCampo>({});
  const [errosDoServidor, setErrosDoServidor] = useState<ErrosDeCampo>({});
  /** O material como o SERVIDOR o devolveu no 201 — é a fonte do link da página pública. */
  const [publicado, setPublicado] = useState<MaterialPublicado | null>(null);

  const envio = useEnvioUnico(enviar);

  const limiteTexto = formatarBytes(limiteBytes);
  const escolhido = avaliarArquivo(
    arquivo ? { nome: arquivo.name, tamanhoBytes: arquivo.size } : null,
    limiteBytes,
  );
  const licencaEscolhida = licencas.find((licenca) => licenca.slug === licencaSlug);

  async function enviar(): Promise<Desfecho> {
    const locais = validarPublicacao(
      { titulo, descricao, categoriaSlug, tipoSlug, licencaSlug, declaracaoDireito, arquivo: arquivo ? { nome: arquivo.name, tamanhoBytes: arquivo.size } : null },
      { limiteBytes },
    );
    if (Object.keys(locais).length > 0) {
      setErrosLocais(locais);
      setErrosDoServidor({});
      // O arquivo tem estado próprio no §15.3 (`arquivo acima do limite ou em formato
      // inválido`), com o motivo específico — é ele que o resumo mostra quando o problema é
      // o arquivo; os demais campos vão para o resumo de campos inválidos.
      if (locais.arquivo) {
        return desfechoDeArquivoInvalido(escolhido === 'tamanho' ? 'tamanho' : 'formato', {
          limite: limiteTexto,
          tamanho: arquivo ? formatarBytes(arquivo.size) : undefined,
        });
      }
      return desfechoDeCamposInvalidos(locais);
    }
    setErrosLocais({});

    if (!arquivo) return desfechoDeCamposInvalidos({ arquivo: 'Escolha o arquivo PDF do material.' });

    const resultado = await publicarMaterial(
      {
        titulo: titulo.trim(),
        descricao: descricao.trim(),
        categoriaSlug,
        tipoSlug,
        licencaSlug,
        declaracaoDireito,
        arquivo,
      },
      { contaVerificada, limiteArquivo: limiteTexto },
    );

    if (!resultado.ok) {
      setErrosDoServidor(resultado.desfecho.campos);
      // 401 numa escrita com identidade: a sessão acabou. O cabeçalho para de mostrar uma
      // sessão que o servidor já não reconhece (RNF010 — a interface reflete, não protege).
      if (resultado.desfecho.sessaoEncerrada) esquecerSessao();
      return resultado.desfecho;
    }

    setErrosDoServidor({});
    setPublicado(resultado.dados);
    return desfechoDeMaterialPublicado(resultado.dados);
  }

  const erroDe = (campo: string) => errosLocais[campo] ?? errosDoServidor[campo];
  const desfecho = envio.desfecho;
  const sucesso = desfecho?.estado === 'success' && publicado !== null;
  const duplicado = envio.estado === 'duplicate-attempt';
  const bloqueado = desfecho?.publicacaoBloqueada === true;

  return (
    <>
      <section className="sc-card sc-card--pad mt-8" aria-labelledby="publicacao-titulo">
        <h2 className="sc-subhead mt-0" id="publicacao-titulo">
          Dados do material
        </h2>
        <p className="sc-body sc-measure">
          Todos os campos abaixo são obrigatórios, inclusive a confirmação de direitos no fim do
          formulário. A autoria não é um campo: ela é a conta que está publicando, e é o servidor
          que a aplica.
        </p>

        {sucesso ? (
          <div className="mt-4">
            <FormState
              id="publicacao-resumo"
              kind="success"
              title={desfecho?.titulo}
              message={desfecho?.mensagem}
              action={
                <>
                  <Link
                    className="sc-btn sc-btn--primary sc-btn--sm"
                    href={rotaMaterial(publicado.slug)}
                  >
                    Abrir a página pública
                  </Link>
                  <Link className="sc-btn sc-btn--ghost sc-btn--sm" href="/painel">
                    Voltar à área do usuário
                  </Link>
                </>
              }
            />
            <p className="sc-helper mt-3" data-material-publicado={publicado.slug}>
              <Badge tone="green">{publicado.estado}</Badge>{' '}
              O material está no estado <strong>{publicado.estado}</strong> — publicado e visível na
              descoberta desde agora.
            </p>
          </div>
        ) : null}

        <form
          className="space-y-4 mt-4"
          method="post"
          noValidate
          onSubmit={envio.submeter}
          aria-busy={envio.pendente}
          aria-describedby={envio.estado !== 'inicial' && !sucesso ? 'publicacao-resumo' : undefined}
        >
          {envio.estado !== 'inicial' && !sucesso ? (
            <FormState
              id="publicacao-resumo"
              kind={envio.pendente ? 'submitting' : (desfecho?.estado ?? 'recoverable-failure')}
              title={duplicado ? 'Esta publicação já está sendo enviada' : desfecho?.titulo}
              message={
                duplicado
                  ? 'Recebemos este envio há instantes e ele ainda está sendo processado. Espere a resposta antes de enviar de novo: repetir não publica um segundo material nem deixa arquivo duplicado.'
                  : desfecho?.mensagem
              }
              fieldId={desfecho?.campoFoco}
              fileMotivo={desfecho?.arquivoMotivo}
              fileLimite={desfecho?.arquivoLimite}
              fileTamanho={desfecho?.arquivoTamanho}
              action={
                <>
                  {desfecho?.sessaoEncerrada ? (
                    <Link
                      className="sc-btn sc-btn--primary sc-btn--sm"
                      href={rotaPreservada(entrarHref('/painel/publicar'))}
                    >
                      Entrar novamente
                    </Link>
                  ) : null}
                  {/* 403 de conta não verificada: o erro não é cru — ele traz o caminho de reenvio
                      que já existe desde o E3.4 (ADR-0003 (c), §11). */}
                  {bloqueado ? (
                    <Link className="sc-btn sc-btn--ghost sc-btn--sm" href="/painel">
                      Ver o estado da conta e reenviar a confirmação
                    </Link>
                  ) : null}
                </>
              }
            />
          ) : null}

          {bloqueado ? (
            <div data-aviso="reenvio-pela-publicacao">
              <ReenvioDeVerificacao />
            </div>
          ) : null}

          <TextField
            id="publicacao-titulo-material"
            name="titulo"
            label="Título do material"
            maxLength={200}
            required
            value={titulo}
            onChange={(evento) => setTitulo(evento.target.value)}
            error={erroDe('titulo')}
            hint="Uma frase curta que descreva o material. É este texto que aparece na busca e no topo da página pública."
          />

          <TextAreaField
            id="publicacao-descricao"
            name="descricao"
            label="Descrição"
            rows={6}
            maxLength={2000}
            required
            value={descricao}
            onChange={(evento) => setDescricao(evento.target.value)}
            error={erroDe('descricao')}
            hint="Explique o conteúdo, para quem serve e o que a pessoa aprende ao usar o material. É também a descrição que aparece nos resultados de busca."
          />

          <SelectField
            id="publicacao-categoria"
            name="categoriaSlug"
            label="Categoria"
            required
            value={categoriaSlug}
            onChange={(evento) => setCategoriaSlug(evento.target.value)}
            error={erroDe('categoriaSlug')}
            hint="As categorias são as da plataforma — não há categoria digitada à mão, para que o mesmo assunto não apareça com nomes diferentes em telas diferentes."
          >
            <option value="">Escolha a categoria…</option>
            {categorias.map((categoria) => (
              <option key={categoria.slug} value={categoria.slug}>
                {categoria.nome}
              </option>
            ))}
          </SelectField>

          <SelectField
            id="publicacao-tipo"
            name="tipoSlug"
            label="Tipo de material"
            required
            value={tipoSlug}
            onChange={(evento) => setTipoSlug(evento.target.value)}
            error={erroDe('tipoSlug')}
            hint="Nota de aula, slides, artigo, guia de estudo… A lista é a da plataforma."
          >
            <option value="">Escolha o tipo…</option>
            {tipos.map((tipo) => (
              <option key={tipo.slug} value={tipo.slug}>
                {tipo.nome}
              </option>
            ))}
          </SelectField>

          <SelectField
            id="publicacao-licenca"
            name="licencaSlug"
            label="Licença de reutilização"
            required
            value={licencaSlug}
            onChange={(evento) => setLicencaSlug(evento.target.value)}
            error={erroDe('licencaSlug')}
            hint="Nenhuma licença vem escolhida por padrão: é você quem diz o que pode ser feito com o material. O que cada uma permite está explicado logo abaixo."
          >
            <option value="">Escolha a licença…</option>
            {licencas.map((licenca) => (
              <option key={licenca.slug} value={licenca.slug}>
                {licenca.nome}
              </option>
            ))}
          </SelectField>

          {/*
            RF018/RN006: a licença é explicada em linguagem simples, e o texto é o
            `descricaoCurta` que a API devolveu — nenhum resumo é redigido aqui. A lista
            inteira vai no documento entregue (não depende de hidratação): sem JavaScript, a
            pessoa lê o que cada licença permite antes de escolher; com JavaScript, a escolha
            ganha destaque.
          */}
          <section
            className="sc-notice sc-notice--info"
            aria-labelledby="licencas-explicadas"
            data-bloco="licencas-explicadas"
          >
            <h3 className="sc-notice__title" id="licencas-explicadas">
              O que cada licença permite
            </h3>
            <p className="sc-notice__msg">
              Estar publicado no SciPath não significa domínio público: a licença é que diz o que
              outras pessoas podem fazer com o material (RN006).
            </p>
            <ul className="sc-body sc-measure">
              {licencas.map((licenca) => (
                <li key={licenca.slug} data-licenca={licenca.slug}>
                  <strong>{licenca.nome}</strong> — {licenca.descricaoCurta}
                </li>
              ))}
            </ul>
            {licencaEscolhida ? (
              <p className="sc-notice__msg" data-licenca-escolhida={licencaEscolhida.slug}>
                Licença escolhida: <strong>{licencaEscolhida.nome}</strong> —{' '}
                {licencaEscolhida.descricaoCurta}
              </p>
            ) : null}
          </section>

          <FileField
            id="publicacao-arquivo"
            name="arquivo"
            label="Arquivo do material (PDF)"
            required
            accept="application/pdf,.pdf"
            onChange={(evento) => setArquivo(evento.target.files?.[0] ?? null)}
            error={erroDe('arquivo')}
            hint={`Aceitamos apenas PDF, até ${limiteTexto}. O servidor confere o conteúdo do arquivo, não só a extensão: um arquivo renomeado para .pdf é recusado (ADR-0005).`}
          />

          <CheckboxField
            id="publicacao-declaracao"
            name="declaracaoDireito"
            label="Confirmo que tenho o direito de compartilhar este material"
            required
            checked={declaracaoDireito}
            onChange={(evento) => setDeclaracaoDireito(evento.target.checked)}
            error={erroDe('declaracaoDireito')}
            hint="Esta declaração é obrigatória (RN007). Declare apenas material de sua autoria, ou que você possa compartilhar: publicação sem essa confirmação é recusada pelo servidor."
          />

          <SubmitButton pending={envio.pendente} variant="primary">
            Publicar material
          </SubmitButton>
        </form>
      </section>
    </>
  );
}
