'use client';

import Link from 'next/link';
import { useState } from 'react';
import { SubmitButton } from '@/components/ui/Button';
import { SelectField, TextAreaField, TextField } from '@/components/ui/Field';
import { FormState } from '@/components/ui/FormState';
import { entrarHref } from '@/components/ui/PageState';
import { Recarregar } from '@/components/ui/Recarregar';
import { useEnvioUnico } from '@/components/sessao/useEnvioUnico';
import type { CategoriaDaPagina, ItemTaxonomia, LicencaRef, MaterialProprio } from '@/lib/api/tipos';
import {
  MOTIVO_SEM_LINK_PUBLICO,
  camposNaoEditaveis,
  desfechoDeMaterialEditado,
  opcoesDaTaxonomia,
} from '@/lib/materiais/edicao';
import { desfechoDeCamposInvalidos, type Desfecho } from '@/lib/sessao/erros';
import { esquecerSessao } from '@/lib/sessao/estado';
import { editarMaterialProprio } from '@/lib/sessao/navegador';
import { formatarBytes, validarEdicaoDeMaterial, type ErrosDeCampo } from '@/lib/sessao/validacao';
import { rotaMaterial, rotaPreservada } from '@/lib/rotas';

/**
 * Formulário de edição dos metadados de um material PRÓPRIO (RF016, RF018;
 * `PAGE_BLUEPRINTS.md` §13) — `PUT /api/perfil/atual/materiais/{slug}` pelo proxy `/api` da
 * mesma origem, com o `X-CSRF-Token` que mora em `lib/sessao/navegador.ts` (ADR-0003 (e)).
 *
 * O que este componente NÃO decide, de propósito:
 *   - **a propriedade nem o estado do material**: quem julga se a edição é permitida é o
 *     SERVIDOR (RNF010, CA007). A tela não é controle de autorização; ela oferece o caminho;
 *   - **as três listas**: categoria, tipo e licença chegam por prop, lidas no SERVIDOR pela
 *     página a partir de `/categorias`, `/tipos-material` e `/licencas`. Nenhuma lista é
 *     escrita aqui — é o que o RF018 exige e o que a bateria de HTML confere;
 *   - **o que não é editável**: o endereço público (`slug`) e o arquivo ficam VISÍVEIS e
 *     explicados (`lib/materiais/edicao.ts`), não escondidos. O contrato não tem campo para
 *     eles nesta rota (`EdicaoDeMaterial`), e o `PUT` que tentasse mudá-los receberia 200
 *     ignorando o campo — a tela não abre a porta para a pessoa descobrir isso no fim.
 *
 * Os seis estados de formulário do §15.3, e o que provoca cada um AQUI:
 *   - `campo inválido`      validação local (título/descrição em branco ou acima do teto,
 *                           escolha de taxonomia vazia) e o 422 do SERVIDOR — a mensagem cai no
 *                           campo que o contrato aponta em `errors[]`, não num resumo genérico;
 *   - `envio em andamento`  `pending` do botão: clique/Enter desabilitado + `aria-busy`;
 *   - `falha recuperável`   falha de rede, 5xx, 400 e o 403 de CSRF. Nada do que estava
 *                           preenchido é apagado (RNF004);
 *   - `sucesso`             200 do contrato: a confirmação diz o que mudou, o que NÃO mudou e
 *                           leva à página pública, que é onde a alteração se comprova (RF016);
 *   - `tentativa duplicada` clique/Enter repetido antes da resposta, travado por `ref` no
 *                           `useEnvioUnico` — repetir não manda o segundo `PUT`;
 *   - `arquivo…inválido`    **não existe nesta tela**, e isso é dito na tela: não há envio de
 *                           arquivo na edição de metadados, então não há limite de tamanho nem
 *                           formato a recusar. O arquivo continua o da publicação.
 *
 * `sessão expirada` (§15.3, estado de página) também aparece aqui: o 401 do `PUT` significa
 * que a sessão caiu entre carregar a página e salvar. A tela diz isso, limpa o estado de
 * sessão do cabeçalho (`esquecerSessao`) e oferece o login com o destino preservado — que é
 * esta mesma página de edição.
 */
export function FormularioEdicao({
  material,
  categorias,
  tipos,
  licencas,
  contaVerificada,
}: {
  material: MaterialProprio;
  categorias: CategoriaDaPagina[];
  tipos: ItemTaxonomia[];
  licencas: LicencaRef[];
  contaVerificada: boolean;
}) {
  const [titulo, setTitulo] = useState(material.titulo);
  const [descricao, setDescricao] = useState(material.descricao);
  const [categoriaSlug, setCategoriaSlug] = useState(material.categoria.slug);
  const [tipoSlug, setTipoSlug] = useState(material.tipo.slug);
  const [licencaSlug, setLicencaSlug] = useState(material.licenca.slug);
  const [errosLocais, setErrosLocais] = useState<ErrosDeCampo>({});
  const [errosDoServidor, setErrosDoServidor] = useState<ErrosDeCampo>({});
  /** O material como o SERVIDOR o devolveu no 200 — não o que a tela achou que enviou. */
  const [editado, setEditado] = useState<MaterialProprio | null>(null);

  const envio = useEnvioUnico(enviar);

  /** O destino preservado no login é ESTA página: quem perdeu a sessão volta para a edição. */
  const destino = `/painel/materiais/${material.slug}/editar`;
  const estaveis = camposNaoEditaveis(material, {
    tamanhoDoArquivo: formatarBytes(material.arquivo.tamanhoBytes),
  });
  const opcoesCategoria = opcoesDaTaxonomia(categorias, material.categoria);
  const opcoesTipo = opcoesDaTaxonomia(tipos, material.tipo);
  const opcoesLicenca = opcoesDaTaxonomia(licencas, material.licenca);

  async function enviar(): Promise<Desfecho> {
    const locais = validarEdicaoDeMaterial({ titulo, descricao, categoriaSlug, tipoSlug, licencaSlug });
    if (Object.keys(locais).length > 0) {
      setErrosLocais(locais);
      setErrosDoServidor({});
      return desfechoDeCamposInvalidos(locais);
    }
    setErrosLocais({});

    const resultado = await editarMaterialProprio(
      material.slug,
      {
        titulo: titulo.trim(),
        descricao: descricao.trim(),
        categoriaSlug,
        tipoSlug,
        licencaSlug,
      },
      { contaVerificada },
    );

    if (!resultado.ok) {
      setErrosDoServidor(resultado.desfecho.campos);
      // 401 numa escrita com identidade: a sessão acabou. O cabeçalho para de mostrar uma
      // sessão que o servidor já não reconhece (RNF010 — a interface reflete, não protege).
      if (resultado.desfecho.sessaoEncerrada) esquecerSessao();
      return resultado.desfecho;
    }

    setErrosDoServidor({});
    setEditado(resultado.dados);
    return desfechoDeMaterialEditado(resultado.dados);
  }

  const erroDe = (campo: string) => errosLocais[campo] ?? errosDoServidor[campo];
  const desfecho = envio.desfecho;
  const sucesso = desfecho?.estado === 'success' && editado !== null;
  const duplicado = envio.estado === 'duplicate-attempt';

  return (
    <section
      className="sc-card sc-card--pad mt-8"
      aria-labelledby="edicao-titulo"
      data-material={material.slug}
      data-estado-material={material.estado}
    >
      <h2 className="sc-subhead mt-0" id="edicao-titulo">
        Dados do material
      </h2>
      <p className="sc-body sc-measure">
        Os cinco campos abaixo são os que descrevem o material, e todos vêm preenchidos com o que
        está publicado agora. A autoria não é um campo: ela é a conta dona do material, e é o
        servidor que a aplica — por isso não existe campo de autor nesta tela.
      </p>

      {sucesso ? (
        <div className="mt-4">
          <FormState
            id="edicao-resumo"
            kind="success"
            title={desfecho?.titulo}
            message={desfecho?.mensagem}
            action={
              <>
                <Link
                  className="sc-btn sc-btn--primary sc-btn--sm"
                  href={rotaMaterial(material.slug)}
                  data-acao="abrir-pagina-publica"
                  data-material={material.slug}
                >
                  Abrir a página pública
                </Link>
                <Link className="sc-btn sc-btn--ghost sc-btn--sm" href="/painel/materiais">
                  Voltar aos materiais próprios
                </Link>
              </>
            }
          />
          <p className="sc-helper mt-3" data-material-editado={material.slug}>
            Estado de publicação: <strong>{editado.estado}</strong> — a edição não muda o estado do
            material (retirar e repor são outras ações, na lista dos seus materiais).
          </p>
        </div>
      ) : null}

      <form
        className="space-y-4 mt-4"
        method="post"
        noValidate
        onSubmit={envio.submeter}
        aria-busy={envio.pendente}
        aria-describedby={envio.estado !== 'inicial' && !sucesso ? 'edicao-resumo' : undefined}
      >
        {envio.estado !== 'inicial' && !sucesso ? (
          <FormState
            id="edicao-resumo"
            kind={envio.pendente ? 'submitting' : (desfecho?.estado ?? 'recoverable-failure')}
            title={duplicado ? 'Esta alteração já está sendo enviada' : desfecho?.titulo}
            message={
              duplicado
                ? 'Recebemos esta tentativa há instantes e ela ainda está sendo processada. Espere a resposta antes de enviar de novo: repetir não grava uma segunda alteração.'
                : desfecho?.mensagem
            }
            fieldId={desfecho?.campoFoco}
            action={
              <>
                {desfecho?.sessaoEncerrada ? (
                  <Link
                    className="sc-btn sc-btn--primary sc-btn--sm"
                    href={rotaPreservada(entrarHref(destino))}
                  >
                    Entrar novamente
                  </Link>
                ) : null}
                {desfecho && desfecho.estado !== 'success' && !desfecho.sessaoEncerrada ? (
                  <Recarregar />
                ) : null}
              </>
            }
          />
        ) : null}

        <TextField
          id="edicao-titulo-material"
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
          id="edicao-descricao"
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
          id="edicao-categoria"
          name="categoriaSlug"
          label="Categoria"
          required
          value={categoriaSlug}
          data-escolha-atual={material.categoria.slug}
          onChange={(evento) => setCategoriaSlug(evento.target.value)}
          error={erroDe('categoriaSlug')}
          hint="As categorias são as da plataforma — não há categoria digitada à mão, para que o mesmo assunto não apareça com nomes diferentes em telas diferentes."
        >
          {opcoesCategoria.map((categoria) => (
            <option key={categoria.slug} value={categoria.slug}>
              {categoria.nome}
            </option>
          ))}
        </SelectField>

        <SelectField
          id="edicao-tipo"
          name="tipoSlug"
          label="Tipo de material"
          required
          value={tipoSlug}
          data-escolha-atual={material.tipo.slug}
          onChange={(evento) => setTipoSlug(evento.target.value)}
          error={erroDe('tipoSlug')}
          hint="Nota de aula, slides, artigo, guia de estudo… A lista é a da plataforma."
        >
          {opcoesTipo.map((tipo) => (
            <option key={tipo.slug} value={tipo.slug}>
              {tipo.nome}
            </option>
          ))}
        </SelectField>

        <SelectField
          id="edicao-licenca"
          name="licencaSlug"
          label="Licença de reutilização"
          required
          value={licencaSlug}
          data-escolha-atual={material.licenca.slug}
          onChange={(evento) => setLicencaSlug(evento.target.value)}
          error={erroDe('licencaSlug')}
          hint="A licença diz o que outras pessoas podem fazer com o material. Trocar a licença aqui vale para quem abrir o material depois desta alteração."
        >
          {opcoesLicenca.map((licenca) => (
            <option key={licenca.slug} value={licenca.slug}>
              {licenca.nome}
            </option>
          ))}
        </SelectField>

        {/*
          RF018/RN006: as licenças são explicadas com o `descricaoCurta` que a API devolveu —
          nenhum resumo é redigido na tela. A lista inteira vai no documento entregue (não
          depende de hidratação): sem JavaScript, a pessoa lê o que cada licença permite antes
          de trocar.
        */}
        <section
          className="sc-notice sc-notice--info"
          aria-labelledby="edicao-licencas"
          data-bloco="licencas-explicadas"
        >
          <h3 className="sc-notice__title" id="edicao-licencas">
            O que cada licença permite
          </h3>
          <ul className="sc-body sc-measure">
            {licencas.map((licenca) => (
              <li key={licenca.slug} data-licenca={licenca.slug}>
                <strong>{licenca.nome}</strong> — {licenca.descricaoCurta}
              </li>
            ))}
          </ul>
        </section>

        {/*
          O que NÃO é editável aqui, dito em vez de escondido (§13): o endereço público e o
          arquivo. Nenhum dos dois é campo de `EdicaoDeMaterial`, e a tela explica o motivo —
          quem procurou o campo de arquivo encontra a resposta, não a ausência.
        */}
        <section
          className="sc-notice sc-notice--atencao"
          aria-labelledby="edicao-nao-editaveis"
          data-bloco="campos-nao-editaveis"
        >
          <h3 className="sc-notice__title" id="edicao-nao-editaveis">
            O que esta tela não edita
          </h3>
          <dl className="sc-body sc-measure">
            {estaveis.map((campo) => (
              <div key={campo.id} data-campo-estavel={campo.id}>
                <dt>
                  <strong>{campo.rotulo}</strong>
                </dt>
                <dd>
                  <p className="sc-mono" data-valor-estavel={campo.id}>
                    {campo.valor}
                  </p>
                  <p>{campo.explicacao}</p>
                  <p>{campo.porque}</p>
                  {campo.linkPublico ? (
                    <p>
                      <Link
                        href={rotaMaterial(material.slug)}
                        data-link-publico={material.slug}
                      >
                        Abrir a página pública
                      </Link>
                    </p>
                  ) : (
                    <p data-sem-link-publico={material.slug}>{MOTIVO_SEM_LINK_PUBLICO}</p>
                  )}
                </dd>
              </div>
            ))}
          </dl>
          <p className="sc-notice__msg">
            Retirar do público, repor e excluir também não estão aqui: são ações da{' '}
            <Link href="/painel/materiais">lista dos seus materiais</Link>, cada uma com a sua
            consequência explicada.
          </p>
        </section>

        <SubmitButton pending={envio.pendente} variant="primary">
          Salvar alterações
        </SubmitButton>
      </form>
    </section>
  );
}
