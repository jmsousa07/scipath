'use client';

import Link from 'next/link';
import { useState } from 'react';
import { SubmitButton } from '@/components/ui/Button';
import { TextAreaField, TextField } from '@/components/ui/Field';
import { FormState } from '@/components/ui/FormState';
import { entrarHref } from '@/components/ui/PageState';
import type { EdicaoPerfil, EstadoConta, PerfilPublico } from '@/lib/api/tipos';
import { desfechoDeCamposInvalidos, type Desfecho } from '@/lib/sessao/erros';
import { esquecerSessao } from '@/lib/sessao/estado';
import { desfechoDePerfilSalvo, editarPerfil } from '@/lib/sessao/navegador';
import {
  listaLinhaPorLinha,
  textoDeLista,
  validarEdicaoPerfil,
  type ErrosDeCampo,
} from '@/lib/sessao/validacao';
import { rotaAutor, rotaPreservada } from '@/lib/rotas';
import { useEnvioUnico } from '@/components/sessao/useEnvioUnico';
import { PreviaDoPerfil } from './PreviaDoPerfil';

/**
 * Formulário de edição do perfil próprio (RF011, CA009; PAGE_BLUEPRINTS.md §10) —
 * `PUT /api/perfil/atual` pelo proxy `/api` da mesma origem (ADR-0003 (e)).
 *
 * Os seis estados de formulário do §15.3, e o que provoca cada um aqui:
 *   - `campo inválido`         validação local (formato/tamanho do slug, limites do contrato) e
 *                              o 422/409 do SERVIDOR — a mensagem cai no campo que o contrato
 *                              aponta em `errors[]`, não num resumo genérico;
 *   - `envio em andamento`     `pending` do botão: clique/Enter desabilitado + `aria-busy`;
 *   - `falha recuperável`      falha de rede, 5xx, 400/415 e o 403 (CSRF/conta suspensa). Nada
 *                              do que foi digitado é apagado (RNF004);
 *   - `sucesso`                200 do contrato: a alteração está salva;
 *   - `tentativa duplicada`    Enter/clique repetido antes da resposta, travado por `ref` no
 *                              `useEnvioUnico` — repetir não manda uma segunda edição;
 *   - `arquivo inválido`       não se aplica: este formulário não tem campo de arquivo.
 *
 * `sessão expirada` (§15.3, estado de página) também aparece AQUI, e de propósito: o 401 de
 * `PUT /perfil/atual` significa que a sessão caiu entre carregar a página e salvar (inatividade
 * de 12 h, logout em outro lugar, recuperação de senha). A tela então diz isso, limpa o estado
 * de sessão do cabeçalho (`esquecerSessao`) e oferece o login **com o destino preservado** —
 * quem entrar volta direto para esta tela.
 *
 * O que este formulário NÃO faz: não manda identificador de conta/perfil (a propriedade vem da
 * sessão, ADR-0003 (j)) e não apresenta instituição como verificada — o campo se chama
 * "Afiliação declarada" e o rótulo diz de quem é a frase (RN011).
 *
 * As listas (áreas de interesse e links) são um item por linha num `textarea`: cabe no HTML
 * entregue, funciona sem JavaScript e não inventa um widget de etiquetas que o contrato visual
 * não tem.
 */
export function FormularioPerfil({
  perfilInicial,
  estadoConta,
}: {
  perfilInicial: PerfilPublico;
  estadoConta: EstadoConta;
}) {
  const [nomePublico, setNomePublico] = useState(perfilInicial.nomePublico);
  const [slug, setSlug] = useState(perfilInicial.slug);
  const [biografia, setBiografia] = useState(perfilInicial.biografia ?? '');
  const [afiliacaoDeclarada, setAfiliacaoDeclarada] = useState(perfilInicial.afiliacaoDeclarada ?? '');
  const [areasTexto, setAreasTexto] = useState(textoDeLista(perfilInicial.areasInteresse));
  const [linksTexto, setLinksTexto] = useState(textoDeLista(perfilInicial.linksExternos));
  const [errosLocais, setErrosLocais] = useState<ErrosDeCampo>({});
  const [errosDoServidor, setErrosDoServidor] = useState<ErrosDeCampo>({});
  /** Perfil como o servidor o devolveu no último 200 — é o que a prévia mostra depois de salvar. */
  const [salvoEm, setSalvoEm] = useState<string | null>(null);

  const envio = useEnvioUnico(enviar);

  const jaVerificada = estadoConta === 'ATIVO';
  const areas = listaLinhaPorLinha(areasTexto);
  const links = listaLinhaPorLinha(linksTexto);

  /** O que está na tela AGORA — a prévia mostra isto, inclusive antes de salvar. */
  const previa: PerfilPublico = {
    nomePublico,
    slug,
    biografia,
    afiliacaoDeclarada,
    areasInteresse: areas,
    linksExternos: links,
  };

  async function enviar(): Promise<Desfecho> {
    const locais = validarEdicaoPerfil({
      nomePublico,
      slug,
      biografia,
      afiliacaoDeclarada,
      areasInteresse: areas,
      linksExternos: links,
    });
    if (Object.keys(locais).length > 0) {
      setErrosLocais(locais);
      setErrosDoServidor({});
      return desfechoDeCamposInvalidos(locais);
    }
    setErrosLocais({});

    // A edição SUBSTITUI o conjunto de campos: os quatro opcionais vão sempre, vazios quando
    // não informados (`''`/`[]` valem como "não informado" no contrato). Mandar só o que mudou
    // deixaria ambíguo o campo que a pessoa apagou.
    const dados: EdicaoPerfil = {
      nomePublico: nomePublico.trim(),
      slug: slug.trim(),
      biografia: biografia.trim(),
      afiliacaoDeclarada: afiliacaoDeclarada.trim(),
      areasInteresse: areas,
      linksExternos: links,
    };

    const resultado = await editarPerfil(dados);
    if (!resultado.ok) {
      setErrosDoServidor(resultado.desfecho.campos);
      // 401 numa escrita com identidade: a sessão acabou. O cabeçalho para de mostrar uma
      // sessão que o servidor já não reconhece (RNF010 — a interface reflete, não protege).
      if (resultado.desfecho.sessaoEncerrada) esquecerSessao();
      return resultado.desfecho;
    }

    const perfil = resultado.dados;
    setErrosDoServidor({});
    // A resposta é a autoridade sobre o que ficou salvo (o servidor normaliza espaço, slug,
    // etc.): os campos e a prévia passam a mostrar o que ele devolveu.
    setNomePublico(perfil.nomePublico);
    setSlug(perfil.slug);
    setBiografia(perfil.biografia ?? '');
    setAfiliacaoDeclarada(perfil.afiliacaoDeclarada ?? '');
    setAreasTexto(textoDeLista(perfil.areasInteresse));
    setLinksTexto(textoDeLista(perfil.linksExternos));
    setSalvoEm(perfil.slug);
    return desfechoDePerfilSalvo(perfil, jaVerificada);
  }

  const erroDe = (campo: string) => errosLocais[campo] ?? errosDoServidor[campo];
  const desfecho = envio.desfecho;
  const sucesso = desfecho?.estado === 'success';
  const duplicado = envio.estado === 'duplicate-attempt';

  return (
    <>
      <section className="sc-card sc-card--pad mt-8" aria-labelledby="edicao-titulo">
        <h2 className="sc-subhead mt-0" id="edicao-titulo">
          Campos públicos do perfil
        </h2>
        <p className="sc-body sc-measure">
          Estes são os campos que qualquer visitante lê no seu perfil. Os que estão marcados como
          opcionais podem ficar vazios: o perfil continua válido sem eles, e a página pública diz que
          a informação não foi dada em vez de inventar conteúdo.
        </p>

        {sucesso ? (
          <div className="mt-4">
            <FormState
              id="perfil-resumo"
              kind="success"
              title={desfecho?.titulo}
              message={desfecho?.mensagem}
              action={
                <>
                  {jaVerificada && salvoEm ? (
                    <Link className="sc-btn sc-btn--primary sc-btn--sm" href={rotaAutor(salvoEm)}>
                      Abrir o meu perfil público
                    </Link>
                  ) : null}
                  <Link className="sc-btn sc-btn--ghost sc-btn--sm" href="/painel">
                    Voltar à área do usuário
                  </Link>
                </>
              }
            />
          </div>
        ) : null}

        <form
          className="space-y-4 mt-4"
          method="post"
          noValidate
          onSubmit={envio.submeter}
          aria-busy={envio.pendente}
          aria-describedby={envio.estado !== 'inicial' && !sucesso ? 'perfil-resumo' : undefined}
        >
          {envio.estado !== 'inicial' && !sucesso ? (
            <FormState
              id="perfil-resumo"
              kind={envio.estado === 'submitting' ? 'submitting' : envio.estado}
              title={duplicado ? 'Esta alteração já está sendo salva' : desfecho?.titulo}
              message={
                duplicado
                  ? 'Recebemos este envio há instantes e ele ainda está sendo processado. Espere a resposta antes de enviar de novo: repetir não salva uma segunda versão do perfil.'
                  : desfecho?.mensagem
              }
              fieldId={desfecho?.campoFoco}
              action={
                // Sessão encerrada (401): o caminho de volta é entrar de novo, e o destino
                // pedido é preservado pelo login (§15.3) — a pessoa volta para esta tela.
                desfecho?.sessaoEncerrada ? (
                  <Link
                    className="sc-btn sc-btn--primary sc-btn--sm"
                    href={rotaPreservada(entrarHref('/painel/perfil'))}
                  >
                    Entrar novamente
                  </Link>
                ) : null
              }
            />
          ) : null}

          <TextField
            id="perfil-nome"
            name="nomePublico"
            label="Nome público"
            autoComplete="name"
            maxLength={120}
            required
            value={nomePublico}
            onChange={(evento) => setNomePublico(evento.target.value)}
            error={erroDe('nomePublico')}
            hint="É o nome que assina os seus materiais e o seu perfil. Pode ser o seu nome ou um apelido estável."
          />

          <TextField
            id="perfil-slug"
            name="slug"
            label="Identificador público (slug)"
            inputMode="url"
            maxLength={120}
            required
            value={slug}
            onChange={(evento) => setSlug(evento.target.value)}
            error={erroDe('slug')}
            hint="Forma o endereço do seu perfil: /u/o-seu-slug. Só letras minúsculas, números e hífen simples entre as palavras. Precisa ser único na plataforma — se já estiver em uso, o servidor recusa e o campo diz isso."
          />

          <TextAreaField
            id="perfil-biografia"
            name="biografia"
            label="Biografia curta"
            rows={4}
            maxLength={600}
            value={biografia}
            onChange={(evento) => setBiografia(evento.target.value)}
            error={erroDe('biografia')}
            hint="Até 600 caracteres. Opcional: sem biografia, o perfil continua válido e a página pública diz que ela não foi escrita."
          />

          <TextAreaField
            id="perfil-afiliacao"
            name="afiliacaoDeclarada"
            label="Instituição / afiliação declarada (opcional)"
            rows={2}
            maxLength={300}
            value={afiliacaoDeclarada}
            onChange={(evento) => setAfiliacaoDeclarada(evento.target.value)}
            error={erroDe('afiliacaoDeclarada')}
            hint="Autodeclaração: o que você escrever aparece no perfil como declaração sua, com o seu nome atribuído à frase. A plataforma não verifica vínculo institucional e não exibe selo de instituição (RN011)."
          />

          <TextAreaField
            id="perfil-areas"
            name="areasInteresse"
            label="Áreas de interesse (opcional)"
            rows={3}
            value={areasTexto}
            onChange={(evento) => setAreasTexto(evento.target.value)}
            error={erroDe('areasInteresse')}
            hint="Uma por linha, até 20 — por exemplo: algoritmos. Elas aparecem como etiquetas no seu perfil."
          />

          <TextAreaField
            id="perfil-links"
            name="linksExternos"
            label="Site pessoal ou perfil acadêmico (opcional)"
            rows={3}
            value={linksTexto}
            onChange={(evento) => setLinksTexto(evento.target.value)}
            error={erroDe('linksExternos')}
            hint="Um endereço por linha, até 20. Endereços que começam com http:// ou https:// viram link; o resto é exibido como texto, sem verificação (RN011)."
          />

          <SubmitButton pending={envio.pendente} variant="primary">
            Salvar perfil
          </SubmitButton>
        </form>
      </section>

      <div className="mt-8">
        <PreviaDoPerfil perfil={previa} publicamenteVisivel={jaVerificada} />
      </div>
    </>
  );
}
