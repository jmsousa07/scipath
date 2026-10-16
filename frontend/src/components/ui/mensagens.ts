/**
 * Catálogo de mensagens dos estados obrigatórios — DRS §15.3.
 *
 * Por que existe: RNF004 exige que toda mensagem de erro, falha ou validação diga
 * o que aconteceu E o que fazer a seguir. Se cada tela escrevesse a sua, o produto
 * teria treze redações diferentes para o mesmo estado (RNF003). Aqui é o único
 * lugar onde esse texto é escrito; a tela pode sobrescrever o título e a mensagem
 * por prop, mas parte deste padrão.
 *
 * Nenhuma mensagem é só "erro": todas nomeiam o ocorrido e a próxima ação.
 * Os rótulos (`drs`) são o nome do estado no DRS §15.3 — usados na demonstração
 * `/_estados` para deixar explícito qual estado está sendo exibido.
 */

/** Os sete estados de página do DRS §15.3. */
export type PageStateKind =
  | 'loading'
  | 'success'
  | 'empty'
  | 'network-error'
  | 'not-found'
  | 'unauthorized'
  | 'session-expired';

/** Os seis estados de formulário do DRS §15.3. */
export type FormStateKind =
  | 'invalid-field'
  | 'submitting'
  | 'recoverable-failure'
  | 'success'
  | 'duplicate-attempt'
  | 'invalid-file';

/** Tom visual do primitivo `.sc-notice` (a cor reforça; o texto é que informa). */
export type NoticeTone = 'info' | 'ok' | 'atencao' | 'erro';

export type MensagemDeEstado = {
  /** Nome do estado em PAGE_BLUEPRINTS.md / DRS §15.3 (português). */
  drs: string;
  /** Rótulo curto, exibido no chip ao lado do título. */
  rotulo: string;
  titulo: string;
  mensagem: string;
  tom: NoticeTone;
  /** Ação padrão. `null` quando a ação depende da tela (ex.: recarregar os dados). */
  acao: { rotulo: string; href: string } | null;
};

export const MENSAGENS_PAGINA: Record<PageStateKind, MensagemDeEstado> = {
  loading: {
    drs: 'carregando',
    rotulo: 'carregando',
    titulo: 'Carregando o conteúdo',
    mensagem:
      'Buscando os dados desta página. Costuma levar poucos segundos — se demorar, você pode continuar navegando pelo menu.',
    tom: 'info',
    acao: null,
  },
  success: {
    drs: 'sucesso',
    rotulo: 'carregado',
    titulo: 'Conteúdo carregado',
    mensagem: 'Os dados desta página chegaram e estão sendo exibidos abaixo.',
    tom: 'ok',
    acao: null,
  },
  empty: {
    drs: 'vazio',
    rotulo: 'nada encontrado',
    titulo: 'Nenhum material encontrado',
    mensagem:
      'Não há material publicado que corresponda a estes critérios. Limpe os filtros aplicados ou explore outra categoria para ver mais resultados.',
    tom: 'info',
    acao: { rotulo: 'Ver todos os materiais', href: '/explorar' },
  },
  'network-error': {
    drs: 'erro de rede/servidor',
    rotulo: 'falha de conexão',
    titulo: 'Não foi possível carregar esta página',
    mensagem:
      'A conexão com o servidor falhou ou demorou mais que o esperado. Confira sua internet e tente de novo; se continuar, tente novamente em alguns minutos.',
    tom: 'erro',
    acao: null,
  },
  'not-found': {
    drs: 'não encontrado',
    rotulo: 'não encontrado',
    titulo: 'Página ou material não encontrado',
    mensagem:
      'O endereço pode estar incorreto, ou o material foi retirado pelo autor. Volte à busca e escolha outro material.',
    tom: 'erro',
    acao: { rotulo: 'Ir para a busca', href: '/explorar' },
  },
  unauthorized: {
    drs: 'não autorizado',
    rotulo: 'acesso negado',
    titulo: 'Esta área não é da sua conta',
    mensagem:
      'Você não tem permissão para ver ou editar este conteúdo: ele pertence a outra pessoa. Entre com a conta correta ou volte para o início.',
    tom: 'erro',
    acao: { rotulo: 'Entrar com outra conta', href: '/entrar' },
  },
  'session-expired': {
    drs: 'sessão expirada',
    rotulo: 'sessão expirada',
    titulo: 'Sua sessão expirou',
    mensagem:
      'Por segurança, seu acesso foi encerrado. Entre novamente para continuar de onde parou — o endereço que você pediu é preservado e você volta para ele.',
    tom: 'atencao',
    acao: { rotulo: 'Entrar novamente', href: '/entrar' },
  },
};

export const MENSAGENS_FORMULARIO: Record<FormStateKind, MensagemDeEstado> = {
  'invalid-field': {
    drs: 'campo inválido',
    rotulo: 'corrija os campos',
    titulo: 'Confira os campos destacados',
    mensagem:
      'Um ou mais campos precisam de correção antes de enviar. A mensagem de cada campo diz o que ajustar; nada do que você já preencheu foi apagado.',
    tom: 'erro',
    acao: null,
  },
  submitting: {
    drs: 'envio em andamento',
    rotulo: 'enviando',
    titulo: 'Enviando…',
    mensagem:
      'Aguarde a resposta do servidor. O botão de envio fica desabilitado até a resposta para que o formulário não seja enviado duas vezes.',
    tom: 'info',
    acao: null,
  },
  'recoverable-failure': {
    drs: 'falha recuperável',
    rotulo: 'falhou',
    titulo: 'Não foi possível enviar agora',
    mensagem:
      'O servidor recusou o envio ou está fora do ar. Todos os campos continuam preenchidos com o que você digitou: revise o que for indicado e envie novamente.',
    tom: 'erro',
    acao: null,
  },
  success: {
    drs: 'sucesso',
    rotulo: 'enviado',
    titulo: 'Envio concluído',
    mensagem:
      'Registramos o seu envio. A partir daqui você pode abrir a página pública do material ou voltar aos seus materiais.',
    tom: 'ok',
    acao: null,
  },
  'duplicate-attempt': {
    drs: 'tentativa duplicada',
    rotulo: 'envio repetido',
    titulo: 'Este envio já está em andamento',
    mensagem:
      'Recebemos uma tentativa igual há instantes e ela ainda está sendo processada. Espere a resposta antes de enviar de novo: repetir não cria um segundo registro.',
    tom: 'atencao',
    acao: null,
  },
  'invalid-file': {
    drs: 'arquivo acima do limite ou em formato inválido',
    rotulo: 'arquivo não aceito',
    titulo: 'O arquivo não foi aceito',
    mensagem: 'O arquivo enviado não atende às regras de publicação.',
    tom: 'erro',
    acao: null,
  },
};

/**
 * Mensagem do estado `invalid-file`, com o motivo específico (PAGE_BLUEPRINTS.md,
 * "Publicar material": erro específico — "acima de X MB", "formato não aceito").
 *
 * @param motivo   'tamanho' recusa por limite; 'formato' recusa por tipo de arquivo
 * @param limite   limite configurável em texto, ex.: '30 MB' (ADR-0005)
 * @param tamanho  tamanho observado do arquivo, ex.: '42 MB' (opcional)
 */
export function mensagemArquivo({
  motivo,
  limite,
  tamanho,
}: {
  motivo: 'tamanho' | 'formato';
  limite?: string;
  tamanho?: string;
}): string {
  if (motivo === 'formato') {
    return 'O arquivo não está em PDF. Converta para .pdf e envie de novo — os outros formatos não são servidos pela plataforma.';
  }
  const limiteTexto = limite ?? 'o limite configurado';
  const tamanhoTexto = tamanho ? `O arquivo tem ${tamanho} e ` : '';
  return `${tamanhoTexto}o limite é ${limiteTexto}. Reduza o PDF (exporte com menos imagens ou divida o documento em partes) e envie novamente.`;
}
