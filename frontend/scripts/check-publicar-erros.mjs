#!/usr/bin/env node
/**
 * card E4.6, critério 3 (parte executável): as falhas de `POST /materiais` — e a validação
 * local dos campos do RF012 — viram o estado do §15.3 certo, exercitadas com os PAYLOADS REAIS
 * do contrato (v0.5.0, `ErroArquivoMuitoGrande`, `ErroTipoNaoSuportado`, `ErroValidacaoCampo`,
 * `ErroAcessoNegado`).
 *
 * Por que um script Node e não o navegador: a prova do card de implementação é `next build` +
 * lint + typecheck + HTML entregue (AGENTS.md — navegador é ferramenta do card de QA). O
 * mapeamento é lógica pura de propósito: este script importa `src/lib/sessao/erros.ts` e
 * `src/lib/sessao/validacao.ts` DIRETO e alimenta cada caso.
 *
 * Os tipos são removidos por `scripts/ts-loader.mjs`, registrado por `scripts/ts-registrar.mjs`
 * (o type stripping nativo do Node não serve — ver card E1.6-f1).
 *
 * O que este script NÃO prova: que o servidor responde aquilo (isso é do backend e do card de
 * QA) e que a tela PINTA o estado (isso é do navegador, no card de QA). Prova que, dado o que o
 * contrato publica, a decisão do cliente é a certa — e que o 415 da publicação NÃO cai na
 * mensagem genérica de "formato não aceito do pedido", nem o 403 de conta não confirmada cai num
 * "acesso negado" cru.
 *
 * Uso: node --import ./scripts/ts-registrar.mjs scripts/check-publicar-erros.mjs
 *      (é o que `npm run check:publicar-erros` faz)
 */
import { mapearFalhaDeEnvio, desfechoDeArquivoInvalido } from '../src/lib/sessao/erros.ts';
import {
  avaliarArquivo,
  formatarBytes,
  limiteDeArquivoEmBytes,
  LIMITE_PADRAO_DE_ARQUIVO_BYTES,
  validarPublicacao,
} from '../src/lib/sessao/validacao.ts';

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

/** Corpo REAL do contrato para arquivo que não é PDF válido (ErroTipoNaoSuportado/arquivoNaoPdf). */
const ARQUIVO_NAO_PDF = problema('tipo_nao_suportado', 415, {
  title: 'Tipo de mídia não suportado',
  detail: 'O arquivo enviado não é um PDF válido.',
  instance: '/api/materiais',
});

/** Corpo REAL do contrato para arquivo acima do limite (ErroArquivoMuitoGrande/arquivoGrande). */
const ARQUIVO_GRANDE = problema('arquivo_muito_grande', 413, {
  title: 'Arquivo muito grande',
  detail: 'O arquivo excede o limite de 30 MB.',
  instance: '/api/materiais',
});

/** Corpo REAL do contrato para conta sem e-mail confirmado (ErroAcessoNegado/emailNaoVerificado). */
const EMAIL_NAO_VERIFICADO = problema('acesso_negado', 403, {
  title: 'Acesso negado',
  detail: 'Confirme seu e-mail para publicar material.',
  instance: '/api/materiais',
});

/** Corpo REAL do contrato para CSRF ausente (ErroAcessoNegado/csrfAusente). */
const CSRF_AUSENTE = problema('acesso_negado', 403, {
  title: 'Acesso negado',
  detail: "Falta o cabeçalho 'X-CSRF-Token' ou ele não corresponde à sessão corrente.",
  instance: '/api/perfil/atual',
});

const LIMITE = LIMITE_PADRAO_DE_ARQUIVO_BYTES;
const LIMITE_TEXTO = formatarBytes(LIMITE);

console.log('== check:publicar-erros — mapeamento de falha e validação local (contrato v0.5.0) ==\n');

/* --------------------------------------------------------- validação de cliente */

console.log('1. validação LOCAL do formulário (RF012) — os limites do schema PublicacaoMaterial');
{
  const vazios = validarPublicacao(
    {
      titulo: '',
      descricao: '',
      categoriaSlug: '',
      tipoSlug: '',
      licencaSlug: '',
      declaracaoDireito: false,
      arquivo: null,
    },
    { limiteBytes: LIMITE },
  );
  verificar(
    'formulário vazio aponta os SETE campos obrigatórios',
    Object.keys(vazios).sort().join(',') ===
      'arquivo,categoriaSlug,declaracaoDireito,descricao,licencaSlug,tipoSlug,titulo',
    JSON.stringify(Object.keys(vazios)),
  );
  verificar(
    'a licença ausente é explicada como escolha explícita (não há padrão)',
    /automaticamente/.test(vazios.licencaSlug ?? ''),
    vazios.licencaSlug,
  );
  verificar(
    'a declaração de direito ausente é recusada antes do envio (RN007)',
    /direito de compartilhar/.test(vazios.declaracaoDireito ?? ''),
    vazios.declaracaoDireito,
  );

  const tituloLongo = validarPublicacao(
    {
      titulo: 'x'.repeat(201),
      descricao: 'descrição',
      categoriaSlug: 'computacao-e-tecnologia',
      tipoSlug: 'nota-de-aula',
      licencaSlug: 'cc-by-4.0',
      declaracaoDireito: true,
      arquivo: { nome: 'nota.pdf', tamanhoBytes: 1024 },
    },
    { limiteBytes: LIMITE },
  );
  verificar(
    'título acima de 200 caracteres é recusado com o limite dito em texto',
    /200/.test(tituloLongo.titulo ?? '') && tituloLongo.descricao === undefined,
    JSON.stringify(tituloLongo),
  );

  const naoPdf = validarPublicacao(
    {
      titulo: 'Título',
      descricao: 'Descrição',
      categoriaSlug: 'computacao-e-tecnologia',
      tipoSlug: 'nota-de-aula',
      licencaSlug: 'cc-by-4.0',
      declaracaoDireito: true,
      arquivo: { nome: 'nota.docx', tamanhoBytes: 1024 },
    },
    { limiteBytes: LIMITE },
  );
  verificar(
    'arquivo sem extensão .pdf é recusado localmente, nomeando o arquivo',
    /nota\.docx/.test(naoPdf.arquivo ?? '') && /conteúdo/.test(naoPdf.arquivo ?? ''),
    naoPdf.arquivo,
  );

  const grande = validarPublicacao(
    {
      titulo: 'Título',
      descricao: 'Descrição',
      categoriaSlug: 'computacao-e-tecnologia',
      tipoSlug: 'nota-de-aula',
      licencaSlug: 'cc-by-4.0',
      declaracaoDireito: true,
      arquivo: { nome: 'nota.pdf', tamanhoBytes: LIMITE + 1 },
    },
    { limiteBytes: LIMITE },
  );
  verificar(
    `arquivo acima do limite é recusado citando o limite (${LIMITE_TEXTO})`,
    new RegExp(LIMITE_TEXTO).test(grande.arquivo ?? ''),
    grande.arquivo,
  );

  const valido = validarPublicacao(
    {
      titulo: 'Introdução a algoritmos',
      descricao: 'Nota de aula.',
      categoriaSlug: 'computacao-e-tecnologia',
      tipoSlug: 'nota-de-aula',
      licencaSlug: 'cc-by-4.0',
      declaracaoDireito: true,
      arquivo: { nome: 'nota.pdf', tamanhoBytes: 1024 },
    },
    { limiteBytes: LIMITE },
  );
  verificar('publicação completa não produz erro local', Object.keys(valido).length === 0, JSON.stringify(valido));
}

console.log('\n2. avaliação do arquivo (extensão/tamanho — o CONTEÚDO é do servidor)');
{
  verificar('nada escolhido → ausente', avaliarArquivo(null, LIMITE) === 'ausente');
  verificar('nome vazio → ausente', avaliarArquivo({ nome: '   ', tamanhoBytes: 10 }, LIMITE) === 'ausente');
  verificar('PDF em maiúsculas é aceito', avaliarArquivo({ nome: 'NOTA.PDF', tamanhoBytes: 10 }, LIMITE) === 'ok');
  verificar('não-PDF → formato', avaliarArquivo({ nome: 'nota.txt', tamanhoBytes: 10 }, LIMITE) === 'formato');
  verificar('PDF exatamente no limite é aceito', avaliarArquivo({ nome: 'nota.pdf', tamanhoBytes: LIMITE }, LIMITE) === 'ok');
  verificar('um byte acima → tamanho', avaliarArquivo({ nome: 'nota.pdf', tamanhoBytes: LIMITE + 1 }, LIMITE) === 'tamanho');
}

console.log('\n3. o limite é o CONFIGURADO (ADR-0005 §1), com o padrão de 30 MB');
{
  verificar('variável ausente → padrão do ADR', limiteDeArquivoEmBytes(undefined) === 30 * 1024 * 1024);
  verificar("valor '1048576' é respeitado", limiteDeArquivoEmBytes('1048576') === 1048576);
  verificar("valor inválido 'abc' cai no padrão", limiteDeArquivoEmBytes('abc') === 30 * 1024 * 1024);
  verificar("valor '0' cai no padrão (não é um limite)", limiteDeArquivoEmBytes('0') === 30 * 1024 * 1024);
  verificar("valor negativo cai no padrão", limiteDeArquivoEmBytes('-5') === 30 * 1024 * 1024);
  verificar('30 MB formata como "30 MB"', formatarBytes(30 * 1024 * 1024) === '30 MB', formatarBytes(30 * 1024 * 1024));
}

console.log('\n4. falhas de POST /materiais → estado do §15.3 (corpos do contrato v0.5.0)');
{
  const naoPdf = mapearFalhaDeEnvio(http(415, ARQUIVO_NAO_PDF), 'publicar-material', {
    contaVerificada: true,
    limiteArquivo: LIMITE_TEXTO,
  });
  verificar('415 → arquivo inválido', naoPdf.estado === 'invalid-file', naoPdf.estado);
  verificar('415 → motivo "formato"', naoPdf.arquivoMotivo === 'formato', String(naoPdf.arquivoMotivo));
  verificar(
    '415 → a mensagem é a do SERVIDOR ("não é um PDF válido")',
    (naoPdf.mensagem ?? '').includes('não é um PDF válido'),
    naoPdf.mensagem,
  );

  const grande = mapearFalhaDeEnvio(http(413, ARQUIVO_GRANDE), 'publicar-material', {
    contaVerificada: true,
    limiteArquivo: LIMITE_TEXTO,
  });
  verificar('413 → arquivo inválido', grande.estado === 'invalid-file', grande.estado);
  verificar('413 → motivo "tamanho"', grande.arquivoMotivo === 'tamanho', String(grande.arquivoMotivo));
  verificar(
    '413 → a mensagem diz o limite CORRENTE do servidor ("excede o limite de 30 MB")',
    (grande.mensagem ?? '').includes('excede o limite de 30 MB'),
    grande.mensagem,
  );

  // Sem `detail` (servidor que só manda o status), a tela compõe com o limite que exibe.
  const grandeSemDetail = mapearFalhaDeEnvio(http(413, null), 'publicar-material', {
    contaVerificada: true,
    limiteArquivo: LIMITE_TEXTO,
  });
  verificar(
    '413 sem detail → a mensagem usa o limite que a tela exibe, e diz o que fazer',
    (grandeSemDetail.mensagem ?? '').includes(LIMITE_TEXTO) && /Reduza o PDF/.test(grandeSemDetail.mensagem ?? ''),
    grandeSemDetail.mensagem,
  );

  const bloqueada = mapearFalhaDeEnvio(http(403, EMAIL_NAO_VERIFICADO), 'publicar-material', {
    contaVerificada: false,
  });
  verificar('403 com conta NÃO confirmada → publicação bloqueada', bloqueada.publicacaoBloqueada === true);
  verificar(
    '403 de verificação → título "Confirme seu e-mail para publicar", não "acesso negado"',
    bloqueada.titulo === 'Confirme seu e-mail para publicar',
    bloqueada.titulo,
  );
  verificar(
    '403 de verificação → a mensagem do servidor é preservada',
    (bloqueada.mensagem ?? '').includes('Confirme seu e-mail para publicar material.'),
    bloqueada.mensagem,
  );
  verificar('403 de verificação → NÃO marca acesso negado genérico', bloqueada.acessoNegado !== true);

  const csrf = mapearFalhaDeEnvio(http(403, CSRF_AUSENTE), 'publicar-material', { contaVerificada: true });
  verificar('403 de CSRF na conta ATIVA → acesso negado genérico', csrf.acessoNegado === true);
  verificar('403 de CSRF não vira "confirme o e-mail"', csrf.publicacaoBloqueada !== true, csrf.titulo);
  verificar(
    '403 de CSRF → a mensagem traz o detail do contrato',
    (csrf.mensagem ?? '').includes("Falta o cabeçalho 'X-CSRF-Token'"),
    csrf.mensagem,
  );

  const semContexto = mapearFalhaDeEnvio(http(403, EMAIL_NAO_VERIFICADO), 'publicar-material');
  verificar(
    'sem saber o estado da conta, o 403 continua "acesso negado" (o cliente não inventa causa)',
    semContexto.acessoNegado === true && semContexto.publicacaoBloqueada !== true,
    semContexto.titulo,
  );

  const sessao = mapearFalhaDeEnvio(
    http(401, problema('nao_autenticado', 401, { detail: 'Sessão ausente ou inválida.' })),
    'publicar-material',
  );
  verificar('401 → sessão encerrada, com o caminho preservado sinalizado', sessao.sessaoEncerrada === true);

  const taxonomiaInexistente = mapearFalhaDeEnvio(
    http(
      422,
      problema('validacao_campo', 422, {
        detail: 'Um ou mais campos são inválidos.',
        errors: [{ campo: 'licencaSlug', mensagem: 'licença inexistente ou inativa' }],
      }),
    ),
    'publicar-material',
  );
  verificar('422 → campo inválido', taxonomiaInexistente.estado === 'invalid-field', taxonomiaInexistente.estado);
  verificar(
    '422 de categoria/tipo/licença cai no CAMPO apontado em errors[] (RF018)',
    taxonomiaInexistente.campos.licencaSlug === 'licença inexistente ou inativa',
    JSON.stringify(taxonomiaInexistente.campos),
  );

  const semResposta = mapearFalhaDeEnvio({ motivo: 'sem-resposta' }, 'publicar-material');
  verificar(
    'sem resposta → falha recuperável que diz que nada se perdeu',
    semResposta.estado === 'recoverable-failure' && /continua preenchido|preenchido/.test(semResposta.mensagem ?? ''),
    semResposta.mensagem,
  );

  const interno = mapearFalhaDeEnvio(
    http(500, problema('interno', 500, { detail: 'Falha interna.' })),
    'publicar-material',
  );
  verificar('500 → falha recuperável que promete que nada foi aplicado', /nada foi aplicado/.test(interno.mensagem ?? ''));

  // Regressão: o 415 das OUTRAS escritas continua caindo na mensagem genérica de formato do
  // pedido — o desvio introduzido para a publicação não pode vazar para a edição de perfil.
  const outro415 = mapearFalhaDeEnvio(http(415, ARQUIVO_NAO_PDF), 'editar-perfil');
  verificar(
    '415 fora da publicação continua "formato do envio" (sem regressão)',
    outro415.estado === 'recoverable-failure' && outro415.classe === 'tipo_nao_suportado',
    `${outro415.estado}/${outro415.classe}`,
  );

  // E o 403 das outras escritas continua acesso negado.
  const outro403 = mapearFalhaDeEnvio(http(403, CSRF_AUSENTE), 'editar-perfil');
  verificar('403 fora da publicação continua acesso negado (sem regressão)', outro403.acessoNegado === true);

  const todas = [naoPdf, grande, grandeSemDetail, bloqueada, csrf, sessao, taxonomiaInexistente, semResposta, interno];
  verificar(
    'nenhuma mensagem é "erro inesperado" (RNF004: toda falha diz o que aconteceu)',
    todas.every((d) => !/erro inesperado/i.test(d.mensagem ?? '')),
  );
  verificar(
    'nenhuma mensagem é vazia',
    todas.every((d) => (d.mensagem ?? '').trim().length > 20),
    todas.map((d) => (d.mensagem ?? '').slice(0, 20)).join(' | '),
  );
}

console.log('\n5. desfecho de arquivo inválido montado pela tela (validação local)');
{
  const formato = desfechoDeArquivoInvalido('formato', {});
  verificar('formato → estado invalid-file e classe do contrato', formato.estado === 'invalid-file' && formato.classe === 'tipo_nao_suportado');
  verificar('formato → mensagem diz o que fazer', /Converta para \.pdf/.test(formato.mensagem ?? ''), formato.mensagem);

  const tamanho = desfechoDeArquivoInvalido('tamanho', { limite: LIMITE_TEXTO, tamanho: '42 MB' });
  verificar('tamanho → estado invalid-file e classe do contrato', tamanho.estado === 'invalid-file' && tamanho.classe === 'arquivo_muito_grande');
  verificar(
    'tamanho → a mensagem traz o tamanho observado E o limite',
    /42 MB/.test(tamanho.mensagem ?? '') && new RegExp(LIMITE_TEXTO).test(tamanho.mensagem ?? ''),
    tamanho.mensagem,
  );
  verificar(
    'tamanho → o motivo e o limite viajam no desfecho (o <FormState> os usa)',
    tamanho.arquivoMotivo === 'tamanho' && tamanho.arquivoLimite === LIMITE_TEXTO,
  );
}

console.log(`\n== ${total - falhas}/${total} verificações PASS · ${falhas} FALHA(s) ==`);
if (falhas > 0) {
  console.log('FAIL');
  process.exit(1);
}
console.log('PASS — o mapeamento de erro e a validação local da publicação estão corretos.');
