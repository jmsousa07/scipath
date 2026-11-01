/**
 * Ação do dono como função do estado do material — módulo PURO, sem `fetch`, sem
 * `process.env` e sem API do Next.
 *
 * Por que existe separado: a derivação "que ações este material oferece?" é
 * APRESENTAÇÃO (o servidor é a autoridade — RNF010; esconder o botão nunca protegeu
 * nada), e ela precisa ser a MESMA em toda superfície que mostre o material do dono.
 * Além disso, um módulo puro pode ser exercitado por bateria sem navegador
 * (`scripts/check-materiais-erros.mjs`), que é como o card prova as quatro
 * combinações estado × ações sem subir a API.
 *
 * A tabela vem do ADR-0011 §2 e §6, e não de conveniência:
 *
 * | Estado      | Ações                              | Por quê                                                    |
 * |-------------|------------------------------------|------------------------------------------------------------|
 * | `PUBLICADO` | editar, retirar, excluir           | está no público; retirar tira, republicar não se aplica     |
 * | `RETIRADO`  | editar, republicar, excluir        | a retirada é reversível pelo dono (o `PATCH` repõe)         |
 * | `EXCLUIDO`  | **nenhuma**                        | estado TERMINAL: `EXCLUIDO` não sai por caminho nenhum       |
 *
 * O que este módulo NÃO decide: se a ação vai ser aceita. Quem recusa é o servidor, por
 * RN002 (404 indistinguível), ADR-0003 (b) (republicar exige e-mail confirmado) e pela
 * máquina de estados do domínio. A tela oferece o caminho; o servidor julga.
 */
import type { EstadoDeDestino, EstadoDeMaterial, MaterialProprio } from '@/lib/api/tipos';
import type { Desfecho } from '@/lib/sessao/erros';

/** Ato do dono sobre um material próprio. `editar` é navegação; os outros três, escrita. */
export type AcaoDeMaterial = 'editar' | 'retirar' | 'republicar' | 'excluir';

/**
 * Ações válidas por estado (ADR-0011 §2). `EXCLUIDO` devolve lista VAZIA — e lista vazia
 * é a resposta, não a ausência de resposta: é o que a tela usa para não renderizar ação
 * nenhuma, e é o que a bateria afirma.
 *
 * `readonly` de propósito: quem consome não muta a tabela compartilhada.
 */
const ACOES_POR_ESTADO: Record<EstadoDeMaterial, readonly AcaoDeMaterial[]> = {
  PUBLICADO: ['editar', 'retirar', 'excluir'],
  RETIRADO: ['editar', 'republicar', 'excluir'],
  EXCLUIDO: [],
};

export function acoesDoEstado(estado: EstadoDeMaterial): readonly AcaoDeMaterial[] {
  return ACOES_POR_ESTADO[estado];
}

/** Rótulo visível de cada ação. Nenhum deles é "ok"/"confirmar": o verbo diz o ato. */
export const ROTULO_DA_ACAO: Record<AcaoDeMaterial, string> = {
  editar: 'Editar',
  retirar: 'Retirar do público',
  republicar: 'Repor no público',
  excluir: 'Excluir',
};

/** Ajuda curta por ação — o que ela faz, dito antes do clique (RNF004). */
export const AJUDA_DA_ACAO: Record<AcaoDeMaterial, string> = {
  editar: 'Título, descrição, categoria, tipo e licença. O endereço público e o arquivo não mudam.',
  retirar: 'Sai da busca, do perfil do autor e do acesso ao arquivo. Você pode repor depois.',
  republicar: 'Volta à busca e ao acesso ao arquivo. Exige e-mail confirmado.',
  excluir: 'Sai de tudo e não volta: a exclusão é terminal nesta versão.',
};

/**
 * Estado de destino do `PATCH` para uma ação de estado (RF017, RF019). `null` quando a
 * ação não muda estado (`editar`, `excluir`): são outros atos, com outras rotas — e
 * devolver `null` em vez de `undefined` obriga quem chama a tratar o caso.
 *
 * Note o que NÃO existe aqui: `excluir` → `EXCLUIDO`. O contrato recusa `EXCLUIDO` no
 * corpo do `PATCH` com 422, apontando o `DELETE` (ADR-0011 §5); exclusão por valor de
 * estado não é um caminho que esta tela possa abrir.
 */
export function estadoDeDestino(acao: AcaoDeMaterial): EstadoDeDestino | null {
  if (acao === 'retirar') return 'RETIRADO';
  if (acao === 'republicar') return 'PUBLICADO';
  return null;
}

/**
 * Como cada estado aparece para o dono: rótulo curto (badge) e explicação.
 *
 * A explicação de `EXCLUIDO` é o que substitui o botão: em vez de uma linha muda, a tela
 * DIZ que não há restauração e que o arquivo sai do armazenamento em 7 dias (ADR-0011 §6,
 * ADR-0005 §3). Estado terminal sem explicação vira a pergunta "e agora?".
 */
export const ESTADO_DO_MATERIAL: Record<
  EstadoDeMaterial,
  { rotulo: string; descricao: string }
> = {
  PUBLICADO: {
    rotulo: 'Publicado',
    descricao:
      'No ar: aparece na busca, na página da categoria, no seu perfil público e na página do material.',
  },
  RETIRADO: {
    rotulo: 'Retirado',
    descricao:
      'Fora do público: não aparece na busca, no seu perfil nem na página do material, e o arquivo não é servido. A retirada é reversível — você pode repor o material no público.',
  },
  EXCLUIDO: {
    rotulo: 'Excluído',
    descricao:
      'Estado terminal: não há como desfazer nem restaurar nesta versão. O registro dos dados permanece com você nesta lista e o arquivo deixa de estar disponível em 7 dias.',
  },
};

/**
 * As duas informações que a confirmação da exclusão tem de dar (RF017, ADR-0011 §7).
 *
 * Por que o texto mora aqui e não no componente: a confirmação é o requisito, e o
 * requisito tem de ser verificável. Em módulo puro ele é exercitado pela bateria, que
 * afirma as duas frases; dentro de um `'use client'`, só o navegador as veria.
 *
 * E por que a confirmação é DIÁLOGO da tela e não um campo do corpo: um
 * `confirmacao=true` não confirma nada — o cliente sempre pode enviá-lo (ADR-0011 §7).
 */
export const CONFIRMACAO_DE_EXCLUSAO = {
  titulo: 'Excluir este material?',
  /** Não desfazer é fato do produto, não aviso genérico: `EXCLUIDO` é terminal (ADR-0011 §6). */
  irreversivel:
    'Não há como desfazer. A exclusão é definitiva nesta versão da plataforma: o material sai da sua lista de publicados, sai da busca e do seu perfil público, e não existe restauração — nem você, nem a equipe, conseguem trazê-lo de volta.',
  /** O que acontece com o arquivo, com o prazo que a retenção usa (ADR-0005 §3). */
  retencao:
    'O arquivo deixa de estar disponível imediatamente e é removido do armazenamento em 7 dias. Os dados que descrevem o material (título, descrição, categoria, tipo e licença) continuam visíveis nesta lista, com o estado Excluído.',
  /** A alternativa honesta: quem só quer tirar do público tem um caminho reversível. */
  alternativa:
    'Se a intenção é apenas tirar o material do público sem perder nada, use "Retirar do público": é reversível e não pede confirmação.',
} as const;

/**
 * Segunda tentativa enquanto a primeira ainda está no ar (§15.3, `tentativa duplicada`).
 *
 * Por que existe um desfecho para isto: cada linha da lista tem os seus próprios botões, e
 * a trava é por linha — o mesmo desenho do `useEnvioUnico` (que trava o formulário inteiro,
 * porque lá há um só envio). Sem o `ref`, um clique rápido repetido mandaria dois `PATCH` e
 * dois `DELETE` para o mesmo material; o segundo receberia 404 (o material já não está vivo
 * para a ação), e a tela contaria ao usuário uma falha que ele mesmo provocou.
 *
 * O texto é próprio desta superfície — repetir aqui NÃO cria "uma segunda conta", como diz o
 * `DESFECHO_DUPLICADO` dos formulários de identidade.
 */
export const DESFECHO_EM_ANDAMENTO: Desfecho = {
  estado: 'duplicate-attempt',
  classe: 'acao-em-andamento',
  titulo: 'Esta ação já está em andamento',
  mensagem:
    'O pedido anterior ainda está sendo processado pelo servidor. Espere a resposta antes de repetir: cada ação é enviada uma vez só, e o resultado aparece na lista com o estado novo.',
  campos: {},
  foraDosCampos: [],
};

/* ---------------------------------------------------------------------------
   Desfechos de sucesso das ações (RF017: "o usuário recebe confirmação").
   Nenhum deles é "operação realizada com sucesso": a confirmação diz o ESTADO
   NOVO, que é o que a lista passa a mostrar.

   Por que estes dois moram AQUI e não em `lib/sessao/navegador.ts`, junto dos
   outros desfechos: eles não dependem de nada do navegador (nem `fetch`, nem
   cookie de CSRF, nem `document`) — são texto montado a partir do que o servidor
   devolveu. Morando aqui, a bateria os exercita sem navegador
   (`scripts/check-materiais-erros.mjs`), que é o que o card pede.
   --------------------------------------------------------------------------- */

/**
 * Sucesso da retirada ou da republicação (`PATCH`).
 *
 * O texto é o do estado que o SERVIDOR devolveu — não o que a tela pediu. Isso importa: o
 * `PATCH` é idempotente (pedir o estado em que o material já está responde 200 sem escrita,
 * ADR-0011 §5), então o desfecho de um pedido repetido tem de ser lido do estado real.
 */
export function desfechoDeEstadoMudado(material: MaterialProprio): Desfecho {
  const retirado = material.estado === 'RETIRADO';
  return {
    estado: 'success',
    classe: retirado ? 'material-retirado' : 'material-republicado',
    titulo: retirado ? 'Material retirado do público' : 'Material reposto no público',
    mensagem: retirado
      ? `"${material.titulo}" saiu da busca, da página da categoria, do seu perfil público e do acesso ao arquivo — a página pública dele responde "não encontrado" enquanto durar a retirada. Você continua vendo o material aqui, com o estado Retirado, e pode repor no público quando quiser.`
      : `"${material.titulo}" voltou ao público: aparece na busca, na página da categoria, no seu perfil e na página do material. A data de publicação original é preservada (${material.publicadoEm}) — repor não empurra o material para o topo da listagem.`,
    campos: {},
    foraDosCampos: [],
  };
}

/**
 * Sucesso da exclusão (`DELETE`, 204 sem corpo).
 *
 * O título do material é passado pela tela porque a resposta 204 não traz corpo nenhum: a
 * tela já tinha o nome na lista, e usá-lo é mais honesto que um "o material foi excluído" sem
 * dizer qual. O que o texto NÃO pode deixar de dizer é o que aconteceu com o arquivo e que não
 * há volta (RF017, ADR-0005 §3, ADR-0011 §6).
 */
export function desfechoDeMaterialExcluido(titulo: string): Desfecho {
  return {
    estado: 'success',
    classe: 'material-excluido',
    titulo: 'Material excluído',
    mensagem: `"${titulo}" saiu de todas as superfícies públicas no mesmo instante e agora aparece aqui com o estado Excluído. A exclusão não tem volta nesta versão. O arquivo deixa de estar disponível imediatamente e é removido do armazenamento em 7 dias; os dados que descrevem o material continuam nesta lista.`,
    campos: {},
    foraDosCampos: [],
  };
}
