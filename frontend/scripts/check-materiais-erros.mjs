#!/usr/bin/env node
/**
 * card E5.6, critério 2 (parte executável, sem navegador): a tabela estado × ações, o texto da
 * confirmação da exclusão e o mapeamento das falhas das TRÊS escritas de
 * `/perfil/atual/materiais` (contrato v0.6.0, tag `gerenciamento`).
 *
 * Por que um script Node e não o navegador: a prova do card de implementação é `next build` +
 * lint + typecheck + HTML entregue (AGENTS.md — navegador é ferramenta do card de QA). O que
 * este script exercita é lógica PURA — `src/lib/materiais/acoes.ts` (ações por estado, rótulos,
 * confirmação, desfechos de sucesso) e `src/lib/sessao/erros.ts` (falha → estado do §15.3) —
 * importados DIRETO e alimentados caso a caso.
 *
 * Os tipos são removidos por `scripts/ts-loader.mjs`, registrado por `scripts/ts-registrar.mjs`
 * (o type stripping nativo do Node não serve nesta máquina — ver card E1.6-f1).
 *
 * O que este script NÃO prova: que o servidor responde assim (isso é do backend e do card de QA)
 * e que a tela PINTA o estado (isso é do navegador, no card de QA). Prova que, dado o que o
 * contrato publica, a decisão do cliente é a certa — e que a exclusão pede confirmação dizendo
 * o que o RF017 exige: que não há como desfazer e que o arquivo sai em 7 dias.
 *
 * Uso: node --import ./scripts/ts-registrar.mjs scripts/check-materiais-erros.mjs
 *      (é o que `npm run check:materiais-erros` faz)
 */
import {
  AJUDA_DA_ACAO,
  CONFIRMACAO_DE_EXCLUSAO,
  DESFECHO_EM_ANDAMENTO,
  ESTADO_DO_MATERIAL,
  ROTULO_DA_ACAO,
  acoesDoEstado,
  desfechoDeEstadoMudado,
  desfechoDeMaterialExcluido,
  estadoDeDestino,
} from '../src/lib/materiais/acoes.ts';
import { mapearFalhaDeEnvio } from '../src/lib/sessao/erros.ts';

let falhas = 0;
let total = 0;

function verificar(titulo, condicao, detalhe) {
  total += 1;
  if (condicao) {
    console.log(`PASS  ${titulo}`);
    return;
  }
  falhas += 1;
  console.log(`FALHA ${titulo}${detalhe === undefined ? '' : `  — ${detalhe}`}`);
}

/** `ProblemDetail` do contrato (RFC 7807) — `type` é o vocabulário fechado. */
const problema = (type, status, extra = {}) => ({
  type: `urn:scipath:erro:${type}`,
  title: type,
  status,
  ...extra,
});

const http = (status, problem) => ({ motivo: 'http', status, problem });

/** Corpo REAL do contrato para material inexistente/alheio/excluído numa rota de ação. */
const NAO_ENCONTRADO = problema('nao_encontrado', 404, {
  title: 'Não encontrado',
  detail: 'O material não foi encontrado.',
  instance: '/api/perfil/atual/materiais/resumo-de-grafos',
});

/** Corpo REAL do contrato para escrita sem CSRF (sessão presente). */
const CSRF_AUSENTE = problema('acesso_negado', 403, {
  title: 'Acesso negado',
  detail: "Falta o cabeçalho 'X-CSRF-Token' ou ele não corresponde à sessão corrente.",
});

/** Corpo REAL do contrato para repor no público com conta PENDENTE_VERIFICACAO. */
const REPUBLICACAO_BLOQUEADA = problema('acesso_negado', 403, {
  title: 'Acesso negado',
  detail: 'Confirme seu e-mail para republicar o material.',
  instance: '/api/perfil/atual/materiais/resumo-de-grafos',
});

/** Corpo REAL do contrato para `EXCLUIDO` no corpo do `PATCH` (ADR-0011 §5). */
const EXCLUSAO_POR_ESTADO = problema('validacao_campo', 422, {
  title: 'Campo inválido',
  detail: 'Um ou mais campos do corpo não foram aceitos.',
  errors: [
    {
      campo: 'estado',
      mensagem: 'a exclusão é a operação DELETE; este campo aceita PUBLICADO ou RETIRADO',
    },
  ],
});

/** Corpo REAL do contrato para taxonomia inexistente no `PUT` (RF018). */
const CATEGORIA_INEXISTENTE = problema('validacao_campo', 422, {
  title: 'Campo inválido',
  detail: 'Um ou mais campos são inválidos.',
  errors: [{ campo: 'categoriaSlug', mensagem: 'categoria inexistente ou inativa' }],
});

/** Material do dono como o contrato o devolve (recorte do exemplo `comMateriais`). */
const material = (estado) => ({
  slug: 'resumo-de-grafos',
  titulo: 'Resumo de grafos',
  descricao: 'Resumo de percurso em largura e em profundidade, com exercícios resolvidos.',
  estado,
  categoria: { slug: 'computacao-e-tecnologia', nome: 'Computação e Tecnologia' },
  tipo: { slug: 'slides', nome: 'Slides' },
  licenca: {
    slug: 'todos-os-direitos-reservados',
    nome: 'Todos os direitos reservados',
    descricaoCurta: 'O material continua protegido.',
  },
  arquivo: { nomeOriginal: 'resumo-de-grafos.pdf', tamanhoBytes: 204800, disponivel: true },
  publicadoEm: '2026-09-02T12:00:00Z',
  atualizadoEm: '2026-10-10T18:20:00Z',
});

console.log('== check:materiais-erros — ações por estado, confirmação e falhas (contrato v0.6.0) ==\n');

/* ------------------------------------------------- 1. estado × ações (ADR-0011 §2, §6) */

console.log('1. a tabela estado × ações é a do ADR-0011 §2 — e `EXCLUIDO` não tem ação nenhuma');
{
  const publicadas = acoesDoEstado('PUBLICADO');
  verificar(
    'PUBLICADO oferece editar, retirar e excluir — nesta ordem',
    publicadas.join(',') === 'editar,retirar,excluir',
    publicadas.join(','),
  );

  const retiradas = acoesDoEstado('RETIRADO');
  verificar(
    'RETIRADO oferece editar, REPOR e excluir — e não oferece retirar',
    retiradas.join(',') === 'editar,republicar,excluir',
    retiradas.join(','),
  );
  verificar('RETIRADO não oferece `retirar` (já está retirado)', !retiradas.includes('retirar'));

  const excluidas = acoesDoEstado('EXCLUIDO');
  verificar(
    'EXCLUIDO devolve lista VAZIA (estado terminal: nenhuma ação)',
    Array.isArray(excluidas) && excluidas.length === 0,
    JSON.stringify(excluidas),
  );

  verificar(
    'todo estado de `MaterialProprio` tem entrada na tabela (nenhum `undefined`)',
    ['PUBLICADO', 'RETIRADO', 'EXCLUIDO'].every((estado) => Array.isArray(acoesDoEstado(estado))),
  );
  verificar(
    'nenhuma ação se repete na mesma lista (o botão não aparece duas vezes)',
    ['PUBLICADO', 'RETIRADO', 'EXCLUIDO'].every(
      (estado) => new Set(acoesDoEstado(estado)).size === acoesDoEstado(estado).length,
    ),
  );
}

console.log('\n2. a ação de estado vira o corpo do PATCH — e excluir NÃO vira estado');
{
  verificar('retirar → RETIRADO', estadoDeDestino('retirar') === 'RETIRADO');
  verificar('republicar → PUBLICADO', estadoDeDestino('republicar') === 'PUBLICADO');
  verificar(
    'excluir → NULL, nunca `EXCLUIDO` (o contrato recusa esse valor no PATCH)',
    estadoDeDestino('excluir') === null,
    String(estadoDeDestino('excluir')),
  );
  verificar('editar → NULL (é navegação, não transição de estado)', estadoDeDestino('editar') === null);
  verificar(
    'o destino de uma ação de estado nunca é `EXCLUIDO`',
    ['retirar', 'republicar', 'editar', 'excluir'].every(
      (acao) => estadoDeDestino(acao) !== 'EXCLUIDO',
    ),
  );
}

console.log('\n3. rótulos e ajuda: nenhum estado nem ação fica sem texto (RNF007, RNF004)');
{
  const acoes = ['editar', 'retirar', 'republicar', 'excluir'];
  for (const acao of acoes) {
    verificar(
      `a ação "${acao}" tem rótulo e ajuda com o que ela faz`,
      (ROTULO_DA_ACAO[acao] ?? '').length > 2 && (AJUDA_DA_ACAO[acao] ?? '').length > 30,
      `${ROTULO_DA_ACAO[acao]} / ${(AJUDA_DA_ACAO[acao] ?? '').slice(0, 30)}`,
    );
  }

  for (const estado of ['PUBLICADO', 'RETIRADO', 'EXCLUIDO']) {
    const entrada = ESTADO_DO_MATERIAL[estado] ?? {};
    verificar(
      `o estado ${estado} tem rótulo curto e explicação (≥ 60 caracteres)`,
      (entrada.rotulo ?? '').length > 3 && (entrada.descricao ?? '').length >= 60,
      `${entrada.rotulo} / ${(entrada.descricao ?? '').length} caracteres`,
    );
  }

  verificar(
    'a explicação de RETIRADO diz que a retirada é REVERSÍVEL (é o que autoriza "repor")',
    /reversível/i.test(ESTADO_DO_MATERIAL.RETIRADO.descricao),
    ESTADO_DO_MATERIAL.RETIRADO.descricao,
  );
  verificar(
    'a explicação de EXCLUIDO diz que é terminal e que não há restauração',
    /terminal/i.test(ESTADO_DO_MATERIAL.EXCLUIDO.descricao) &&
      /restaurar|restauração/i.test(ESTADO_DO_MATERIAL.EXCLUIDO.descricao),
    ESTADO_DO_MATERIAL.EXCLUIDO.descricao,
  );
}

console.log('\n4. a confirmação da exclusão diz o que o RF017 exige (ADR-0011 §7)');
{
  verificar(
    'diz que NÃO HÁ COMO DESFAZER, em letra, não por cor',
    /não há como desfazer/i.test(CONFIRMACAO_DE_EXCLUSAO.irreversivel),
    CONFIRMACAO_DE_EXCLUSAO.irreversivel.slice(0, 80),
  );
  verificar(
    'diz que o material sai de todas as superfícies públicas',
    /sai da sua lista|sai da busca|sai do seu perfil/i.test(CONFIRMACAO_DE_EXCLUSAO.irreversivel),
  );
  verificar(
    'diz que o arquivo deixa de estar disponível em 7 dias (ADR-0005 §3)',
    /7 dias/.test(CONFIRMACAO_DE_EXCLUSAO.retencao),
    CONFIRMACAO_DE_EXCLUSAO.retencao.slice(0, 80),
  );
  verificar(
    'diz o que CONTINUA visível (os metadados, na lista do dono)',
    /continuam visíveis/i.test(CONFIRMACAO_DE_EXCLUSAO.retencao),
  );
  verificar(
    'oferece a alternativa reversível em vez de só dizer não',
    /Retirar do público/.test(CONFIRMACAO_DE_EXCLUSAO.alternativa) &&
      /reversível/i.test(CONFIRMACAO_DE_EXCLUSAO.alternativa),
  );
  verificar(
    'o título do diálogo nomeia o ato e pergunta',
    /\?$/.test(CONFIRMACAO_DE_EXCLUSAO.titulo) && /excluir/i.test(CONFIRMACAO_DE_EXCLUSAO.titulo),
    CONFIRMACAO_DE_EXCLUSAO.titulo,
  );
  verificar(
    'nenhuma das frases da confirmação promete restauração',
    !/restaurar é possível|você pode restaurar|desfazer depois|reverter depois/i.test(
      Object.values(CONFIRMACAO_DE_EXCLUSAO).join(' '),
    ),
  );
}

console.log('\n5. desfechos de sucesso das ações (RF017: "o usuário recebe confirmação")');
{
  const retirado = desfechoDeEstadoMudado(material('RETIRADO'));
  verificar('retirada → estado de formulário `success`', retirado.estado === 'success', retirado.estado);
  verificar(
    'retirada → o título diz que saiu do público e a classe é própria',
    retirado.titulo === 'Material retirado do público' && retirado.classe === 'material-retirado',
    `${retirado.titulo} / ${retirado.classe}`,
  );
  verificar(
    'retirada → a mensagem nomeia o material e diz que a página pública responde "não encontrado"',
    retirado.mensagem.includes('Resumo de grafos') && /não encontrado/i.test(retirado.mensagem),
  );
  verificar(
    'retirada → a mensagem diz que repor ainda é possível (a retirada é reversível)',
    /repor no público quando quiser/i.test(retirado.mensagem),
  );

  const reposto = desfechoDeEstadoMudado(material('PUBLICADO'));
  verificar(
    'reposição → o título fala em repor e a classe é a de republicação',
    reposto.titulo === 'Material reposto no público' && reposto.classe === 'material-republicado',
    `${reposto.titulo} / ${reposto.classe}`,
  );
  verificar(
    'reposição → diz que `publicadoEm` NÃO muda (repor não é manobra de ordenação, ADR-0011 §4)',
    reposto.mensagem.includes('2026-09-02T12:00:00Z') &&
      /data de publicação original é preservada/i.test(reposto.mensagem),
  );
  verificar(
    'os dois desfechos de estado são DIFERENTES (o texto vem do estado do servidor, não do pedido)',
    retirado.mensagem !== reposto.mensagem && retirado.titulo !== reposto.titulo,
  );

  const excluido = desfechoDeMaterialExcluido('Resumo de grafos');
  verificar('exclusão → `success` e classe própria', excluido.estado === 'success' && excluido.classe === 'material-excluido');
  verificar(
    'exclusão → a mensagem nomeia o material excluído (o 204 não traz corpo: o nome vem da lista)',
    excluido.mensagem.includes('Resumo de grafos'),
  );
  verificar(
    'exclusão → repete as duas informações críticas: não tem volta e 7 dias',
    /não tem volta/i.test(excluido.mensagem) && /7 dias/.test(excluido.mensagem),
  );
  verificar(
    'exclusão → diz o estado novo (`Excluído`) que a lista passa a mostrar',
    /estado Excluído/i.test(excluido.mensagem),
  );

  verificar(
    'tentativa duplicada tem desfecho próprio (não é o "cria uma segunda conta" do cadastro)',
    DESFECHO_EM_ANDAMENTO.estado === 'duplicate-attempt' &&
      /cada ação é enviada uma vez só/i.test(DESFECHO_EM_ANDAMENTO.mensagem),
  );
  verificar(
    'nenhum desfecho de sucesso é vazio ou genérico',
    [retirado, reposto, excluido].every((desfecho) => desfecho.mensagem.trim().length > 80),
  );
}

console.log('\n6. falhas das três escritas → estado do §15.3 (corpos do contrato v0.6.0)');
{
  // 404 — o mais importante do card: material inexistente, de outra conta e já excluído
  // respondem o MESMO corpo (ADR-0011 §3), e a tela não pode desfazer essa decisão.
  for (const operacao of ['editar-material', 'mudar-estado-material', 'excluir-material']) {
    const falha = mapearFalhaDeEnvio(http(404, NAO_ENCONTRADO), operacao);
    verificar(
      `${operacao}: 404 → "não está disponível para esta operação", não "não é seu"`,
      falha.titulo === 'Este material não está disponível para esta operação' &&
        falha.classe === 'nao_encontrado',
      `${falha.titulo} / ${falha.classe}`,
    );
    verificar(
      `${operacao}: 404 → a mensagem explica que os três casos são indistinguíveis`,
      /inexistente, de outra conta ou já excluído/i.test(falha.mensagem) &&
        /não distingue os três casos/i.test(falha.mensagem),
      falha.mensagem.slice(0, 90),
    );
    verificar(
      `${operacao}: 404 → a mensagem do servidor é preservada`,
      falha.mensagem.includes('O material não foi encontrado.'),
    );
  }

  // O 404 das OUTRAS operações continua com o texto antigo — sem regressão.
  const outro404 = mapearFalhaDeEnvio(http(404, NAO_ENCONTRADO), 'editar-perfil');
  verificar(
    '404 fora do gerenciamento continua o texto genérico (sem regressão)',
    outro404.titulo === 'O endereço desta operação não existe',
    outro404.titulo,
  );

  // 401 — a sessão caiu entre carregar a página e agir.
  for (const operacao of ['editar-material', 'mudar-estado-material', 'excluir-material']) {
    const falha = mapearFalhaDeEnvio(
      http(401, problema('nao_autenticado', 401, { detail: 'Sessão ausente ou inválida.' })),
      operacao,
    );
    verificar(
      `${operacao}: 401 → sessão encerrada, com o destino preservado sinalizado`,
      falha.sessaoEncerrada === true && falha.estado === 'recoverable-failure',
      `${falha.estado} / sessaoEncerrada=${String(falha.sessaoEncerrada)}`,
    );
  }

  // 403 de CSRF — o acesso negado comum, que NÃO é "confirme o e-mail".
  for (const operacao of ['editar-material', 'mudar-estado-material', 'excluir-material']) {
    const falha = mapearFalhaDeEnvio(http(403, CSRF_AUSENTE), operacao, { contaVerificada: true });
    verificar(
      `${operacao}: 403 de CSRF → acesso negado, sem virar "confirme o e-mail"`,
      falha.acessoNegado === true &&
        falha.republicacaoBloqueada !== true &&
        falha.publicacaoBloqueada !== true,
      falha.titulo,
    );
    verificar(
      `${operacao}: 403 de CSRF → traz o detail do contrato`,
      falha.mensagem.includes("Falta o cabeçalho 'X-CSRF-Token'"),
    );
  }

  // 403 da REPOSIÇÃO — mesma causa da publicação bloqueada, com outro verbo (ADR-0011 §5).
  const reposicaoBloqueada = mapearFalhaDeEnvio(http(403, REPUBLICACAO_BLOQUEADA), 'mudar-estado-material', {
    contaVerificada: false,
  });
  verificar(
    'repor no público com conta pendente → `republicacaoBloqueada`, não acesso negado genérico',
    reposicaoBloqueada.republicacaoBloqueada === true && reposicaoBloqueada.acessoNegado !== true,
    reposicaoBloqueada.titulo,
  );
  verificar(
    'o título fala em REPOR, não em publicar (a pessoa clicou em repor)',
    reposicaoBloqueada.titulo === 'Confirme seu e-mail para repor no público',
    reposicaoBloqueada.titulo,
  );
  verificar(
    'a mensagem do servidor é preservada e diz que NADA mudou',
    reposicaoBloqueada.mensagem.includes('Confirme seu e-mail para republicar o material.') &&
      /Nada mudou/i.test(reposicaoBloqueada.mensagem),
  );

  // Sem saber o estado da conta, o cliente NÃO inventa a causa.
  const semContexto = mapearFalhaDeEnvio(http(403, REPUBLICACAO_BLOQUEADA), 'mudar-estado-material');
  verificar(
    'sem o estado da conta, o 403 continua acesso negado (o cliente não inventa causa)',
    semContexto.acessoNegado === true && semContexto.republicacaoBloqueada !== true,
    semContexto.titulo,
  );

  // Excluir NUNCA é bloqueado por verificação — o 403 dele não vira "confirme o e-mail".
  const excluirNegado = mapearFalhaDeEnvio(http(403, REPUBLICACAO_BLOQUEADA), 'excluir-material', {
    contaVerificada: false,
  });
  verificar(
    'excluir com conta pendente NÃO vira bloqueio de verificação (excluir não exige e-mail confirmado)',
    excluirNegado.republicacaoBloqueada !== true && excluirNegado.acessoNegado === true,
  );
  const editarNegado = mapearFalhaDeEnvio(http(403, REPUBLICACAO_BLOQUEADA), 'editar-material', {
    contaVerificada: false,
  });
  verificar(
    'editar com conta pendente NÃO vira bloqueio de verificação (editar não exige e-mail confirmado)',
    editarNegado.republicacaoBloqueada !== true,
  );

  // A publicação original continua com o comportamento dela — sem regressão.
  const publicar = mapearFalhaDeEnvio(
    http(403, problema('acesso_negado', 403, { detail: 'Confirme seu e-mail para publicar material.' })),
    'publicar-material',
    { contaVerificada: false },
  );
  verificar(
    'o 403 de publicar-material continua `publicacaoBloqueada` (sem regressão do E4.6)',
    publicar.publicacaoBloqueada === true && publicar.republicacaoBloqueada !== true,
    publicar.titulo,
  );

  // 422 — o `PATCH` com `EXCLUIDO` no corpo (situação que o contrato recusa, ADR-0011 §5).
  const excluirPorEstado = mapearFalhaDeEnvio(http(422, EXCLUSAO_POR_ESTADO), 'mudar-estado-material');
  verificar(
    '422 do PATCH → `invalid-field`, com o resumo de campos',
    excluirPorEstado.estado === 'invalid-field' && excluirPorEstado.classe === 'validacao_campo',
    `${excluirPorEstado.estado} / ${excluirPorEstado.classe}`,
  );
  verificar(
    'o campo `estado` NÃO vira campo de formulário (não há controle com esse nome aqui): a mensagem entra no resumo',
    Object.keys(excluirPorEstado.campos).length === 0 &&
      excluirPorEstado.foraDosCampos.some((texto) => texto.startsWith('estado:')),
    JSON.stringify(excluirPorEstado.foraDosCampos),
  );
  verificar(
    'a mensagem do servidor sobre a exclusão chega ao usuário (não é descartada)',
    excluirPorEstado.mensagem.includes('a exclusão é a operação DELETE'),
  );

  // 422 da edição — o campo culpado cai no CAMPO (é o formulário do E5.7 que o exibe).
  const taxonomia = mapearFalhaDeEnvio(http(422, CATEGORIA_INEXISTENTE), 'editar-material');
  verificar(
    '422 do PUT → a mensagem cai no campo apontado em errors[] (categoriaSlug)',
    taxonomia.campos.categoriaSlug === 'categoria inexistente ou inativa' &&
      taxonomia.campoFoco === 'categoriaSlug',
    JSON.stringify(taxonomia.campos),
  );

  // 400 e 5xx — o que não é validação de campo.
  const quatrocentos = mapearFalhaDeEnvio(
    http(400, problema('validacao', 400, { detail: 'Campo obrigatório ausente: titulo.' })),
    'editar-material',
  );
  verificar(
    '400 → falha recuperável que diz o que fazer (RNF004)',
    quatrocentos.estado === 'recoverable-failure' && /Envie de novo/i.test(quatrocentos.mensagem),
    quatrocentos.mensagem.slice(0, 60),
  );

  const interno = mapearFalhaDeEnvio(
    http(500, problema('interno', 500, { detail: 'Falha interna.' })),
    'excluir-material',
  );
  verificar(
    '500 → falha recuperável que promete que nada foi aplicado',
    interno.estado === 'recoverable-failure' && /nada foi aplicado/i.test(interno.mensagem),
  );

  const semResposta = mapearFalhaDeEnvio({ motivo: 'sem-resposta' }, 'mudar-estado-material');
  verificar(
    'sem resposta → falha de rede, sem concluir nada',
    semResposta.classe === 'falha-de-rede' && semResposta.estado === 'recoverable-failure',
    semResposta.classe,
  );

  const corpoInvalido = mapearFalhaDeEnvio({ motivo: 'corpo-invalido' }, 'editar-material');
  verificar(
    'resposta 2xx fora do contrato → falha recuperável (não vira sucesso)',
    corpoInvalido.estado === 'recoverable-failure' && corpoInvalido.classe === 'corpo-invalido',
    corpoInvalido.classe,
  );

  const todas = [
    reposicaoBloqueada,
    semContexto,
    excluirPorEstado,
    taxonomia,
    quatrocentos,
    interno,
    semResposta,
    corpoInvalido,
  ];
  verificar(
    'nenhuma mensagem é "erro inesperado" nem vazia (RNF004)',
    todas.every((desfecho) => !/erro inesperado/i.test(desfecho.mensagem) && desfecho.mensagem.trim().length > 40),
    todas.map((desfecho) => desfecho.mensagem.slice(0, 16)).join(' | '),
  );
}

console.log(`\n== ${total - falhas}/${total} verificações PASS · ${falhas} FALHA(s) ==`);
if (falhas > 0) {
  console.log('FAIL');
  process.exit(1);
}
console.log('PASS — as ações por estado, a confirmação da exclusão e o mapeamento de falha estão corretos.');
