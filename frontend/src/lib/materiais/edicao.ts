/**
 * Edição dos metadados de um material próprio (RF016, PAGE_BLUEPRINTS.md §13) — módulo PURO:
 * sem `fetch`, sem `process.env`, sem API do Next e sem nenhum `import` de RUNTIME.
 *
 * Por que ele existe separado do componente: o que o §13 promete é verificável — o que NÃO
 * pode ser editado (e por quê), quem pode ser editado e o que a confirmação de sucesso tem de
 * dizer (o critério que amarra o RF016: *"a página pública reflete a alteração após sucesso"*).
 * Em módulo puro isso é exercitado por bateria sem navegador
 * (`scripts/check-editar-erros.mjs`); dentro de um `'use client'`, só o navegador veria os
 * textos.
 *
 * O que este módulo NÃO decide: se a edição é aceita. A propriedade (RN001, CA007), o estado
 * vivo do material (ADR-0011 §2 e §6) e a validade da taxonomia (RF018) são julgados pelo
 * SERVIDOR; esconder o formulário nunca protegeu nada (RNF010).
 */
import type { EstadoDeMaterial, MaterialProprio } from '@/lib/api/tipos';
import type { Desfecho } from '@/lib/sessao/erros';

/**
 * Os cinco campos editáveis do contrato (`EdicaoDeMaterial`, v0.6.0): título, descrição,
 * categoria, tipo e licença. A lista mora aqui, e não espalhada pelo componente, porque é
 * ela que a bateria de HTML confere no documento entregue.
 */
export const CAMPOS_EDITAVEIS = [
  'titulo',
  'descricao',
  'categoriaSlug',
  'tipoSlug',
  'licencaSlug',
] as const;

/**
 * A edição só faz sentido nos estados em que o dono age (`PUBLICADO` e `RETIRADO`,
 * ADR-0011 §2). `EXCLUIDO` é terminal: o `PUT` do contrato responde o **mesmo 404** de
 * material inexistente ou alheio (ADR-0011 §3 e §6), e a lista do dono também não oferece
 * a ação de editar nesse estado.
 *
 * A decisão é de APRESENTAÇÃO: quem recusa é o servidor. A função existe para a tela não
 * oferecer um formulário que o servidor vai recusar no fim do caminho — a armadilha que o
 * §11 do `PAGE_BLUEPRINTS.md` proíbe na publicação.
 */
export function edicaoPermitida(estado: EstadoDeMaterial): boolean {
  return estado !== 'EXCLUIDO';
}

/** Um campo que a pessoa vê mas NÃO edita aqui, com a explicação de por quê (§13). */
export type CampoEstavel = {
  /** `endereco` = o `slug` (URL canônica), `arquivo` = o PDF da publicação. */
  id: 'endereco' | 'arquivo';
  rotulo: string;
  /** O valor atual, como o dono o reconhece. */
  valor: string;
  /** O que o valor significa para quem publicou. */
  explicacao: string;
  /** Por que ele não é editável NESTA tela (a razão, não um "não pode"). */
  porque: string;
  /**
   * Endereço da página pública, **só** quando ela responde: em `RETIRADO` ela responde 404,
   * e link que leva a 404 é promessa falsa (`PAGE_BLUEPRINTS.md` §12).
   */
  linkPublico?: string;
};

/**
 * Por que um material retirado não recebe o link da página pública — dito, em vez de deixar
 * a pessoa concluir que o link sumiu por defeito.
 */
export const MOTIVO_SEM_LINK_PUBLICO =
  'Enquanto o material está retirado, a página pública dele responde "não encontrado": o endereço continua sendo dele, e é o mesmo depois de repor no público — por isso o link não aparece agora.';

/**
 * Os campos que ficam **visíveis e explicados** em vez de escondidos (§13): o endereço
 * público (estável, RNF025) e o arquivo da publicação (trocar o binário não é deste fluxo).
 *
 * `tamanhoDoArquivo` chega pronto (em texto) porque quem formata bytes é `lib/sessao/validacao.ts`
 * — este módulo não importa nada de runtime, e é isso que o deixa ser exercitado no Node.
 */
export function camposNaoEditaveis(
  material: MaterialProprio,
  opcoes: { tamanhoDoArquivo: string },
): readonly CampoEstavel[] {
  const retirado = material.estado === 'RETIRADO';

  const endereco: CampoEstavel = {
    id: 'endereco',
    rotulo: 'Endereço público (não muda)',
    valor: `/materiais/${material.slug}`,
    explicacao:
      'O endereço é a chave estável do material (RNF025): ele não muda por edição, nem quando o material é retirado e reposto — quem guardou o link continua chegando ao mesmo material.',
    porque:
      'Esta tela edita os cinco dados que descrevem o material (título, descrição, categoria, tipo e licença); o endereço não é um deles.',
  };
  if (!retirado) endereco.linkPublico = `/materiais/${material.slug}`;

  const arquivo: CampoEstavel = {
    id: 'arquivo',
    rotulo: 'Arquivo da publicação (não muda)',
    valor: `${material.arquivo.nomeOriginal} · ${opcoes.tamanhoDoArquivo}`,
    explicacao:
      'O arquivo é o PDF que foi enviado quando o material foi publicado, e continua sendo o mesmo depois de salvar os dados.',
    porque:
      'Trocar o arquivo de um material publicado é outra operação — ela mexe em validação de conteúdo, armazenamento, arquivos órfãos e retenção, e não existe nesta versão. É também por isso que o estado "arquivo acima do limite ou em formato inválido" não aparece aqui: não há envio de arquivo para ele descrever.' +
      (material.arquivo.disponivel
        ? ''
        : ' O binário deste material já foi removido do armazenamento pela retenção de 7 dias (ADR-0005 §3); os dados que o descrevem permanecem.'),
  };

  return [endereco, arquivo];
}

/**
 * As opções de um `<select>` da taxonomia, com a escolha ATUAL do material preservada.
 *
 * O caso comum é trivial: o `slug` que a leitura devolveu está na lista que `/categorias`,
 * `/tipos-material` ou `/licencas` devolvem, e a lista passa como veio. O caso de borda é o
 * que justifica a função: se a escolha atual **saiu da lista ativa** (taxonomia desativada por
 * migração), um `<select>` controlado sem a opção correspondente fica em branco e enviaria
 * string vazia — a pessoa receberia "escolha a categoria" apontando um campo que ela não
 * esvaziou. Aqui a escolha atual entra na lista como mais uma opção, marcada como tal, e com o
 * nome que o SERVIDOR devolveu no próprio material (nenhum nome é inventado na tela — RF018).
 * Manter essa opção selecionada e salvar recebe 422 no campo, com a mensagem do servidor: a
 * tela não esconde o problema, ela dá o caminho para resolvê-lo.
 */
export function opcoesDaTaxonomia<T extends { slug: string; nome: string }>(
  itens: readonly T[],
  atual: { slug: string; nome: string },
): readonly { slug: string; nome: string }[] {
  if (itens.some((item) => item.slug === atual.slug)) return itens;
  return [...itens, { slug: atual.slug, nome: `${atual.nome} (atual — fora da lista ativa)` }];
}

/**
 * Estado `sucesso` da edição (§15.3), montado do `MaterialProprio` que o **servidor**
 * devolveu no 200 — não do que a tela achava ter enviado.
 *
 * O critério do card é explícito: *"a página pública reflete a alteração após sucesso"*. Por
 * isso a mensagem diz onde conferir (o endereço público) e diz o que NÃO mudou (endereço,
 * arquivo e a data de publicação original — ADR-0011 §4) — quem editou precisa saber que a
 * alteração é dos metadados, não do material inteiro.
 */
export function desfechoDeMaterialEditado(material: MaterialProprio): Desfecho {
  return {
    estado: 'success',
    classe: 'material-editado',
    titulo: 'Alterações salvas',
    mensagem: `Os dados de "${material.titulo}" foram atualizados no servidor: a página pública /materiais/${material.slug} já mostra o que está nesta tela — é ali que a alteração se comprova. O endereço público, o arquivo e a data da primeira publicação continuam os mesmos; o que mudou foram os dados que descrevem o material.`,
    campos: {},
    foraDosCampos: [],
  };
}
