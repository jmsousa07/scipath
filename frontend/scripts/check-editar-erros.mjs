#!/usr/bin/env node
/**
 * card E5.7, critério 2 (parte executável, sem navegador): as regras da EDIÇÃO de metadados —
 * validação local (`EdicaoDeMaterial`, contrato v0.6.0), os campos que a tela NÃO edita, a
 * lista de opções com a escolha atual, o desfecho de sucesso e o mapeamento das falhas do
 * `PUT /perfil/atual/materiais/{slug}`.
 *
 * Por que um script Node e não o navegador: a prova do card de implementação é `next build` +
 * lint + typecheck + HTML entregue (AGENTS.md — navegador é ferramenta do card de QA, e
 * `next dev`/Chromium são proibidos em card de implementação). O que este script exercita é
 * lógica PURA — `src/lib/materiais/edicao.ts`, `src/lib/sessao/validacao.ts` e
 * `src/lib/sessao/erros.ts` — importados DIRETO e alimentados caso a caso.
 *
 * Os tipos são removidos por `scripts/ts-loader.mjs`, registrado por `scripts/ts-registrar.mjs`
 * (o type stripping nativo do Node não serve nesta máquina — card E1.6-f1).
 *
 * O que este script NÃO prova: que o servidor responde assim (isso é do backend e do card de
 * QA) e que a tela PINTA o estado (isso é o navegador, no card de QA). Prova que, dado o que o
 * contrato publica, a decisão do cliente é a certa.
 *
 * Uso: node --import ./scripts/ts-registrar.mjs scripts/check-editar-erros.mjs
 *      (é o que `npm run check:editar-erros` faz)
 */
import {
  MOTIVO_SEM_LINK_PUBLICO,
  camposNaoEditaveis,
  desfechoDeMaterialEditado,
  edicaoPermitida,
  opcoesDaTaxonomia,
} from '../src/lib/materiais/edicao.ts';
import { desfechoDeCamposInvalidos, mapearFalhaDeEnvio } from '../src/lib/sessao/erros.ts';
import { validarEdicaoDeMaterial } from '../src/lib/sessao/validacao.ts';

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

/** Corpo REAL do contrato para escrita sem CSRF (sessão presente). */
const CSRF_AUSENTE = problema('acesso_negado', 403, {
  title: 'Acesso negado',
  detail: "Falta o cabeçalho 'X-CSRF-Token' ou ele não corresponde à sessão corrente.",
});

/** Corpo REAL do contrato para taxa de campo inválido no `PUT` (RF018). */
const LICENCA_INEXISTENTE = problema('validacao_campo', 422, {
  title: 'Campo inválido',
  detail: 'Um ou mais campos são inválidos.',
  errors: [{ campo: 'licencaSlug', mensagem: 'licença inexistente ou inativa' }],
});

/** Corpo REAL do contrato para material inexistente/alheio/excluído numa rota de ação. */
const NAO_ENCONTRADO = problema('nao_encontrado', 404, {
  title: 'Não encontrado',
  detail: 'O material não foi encontrado.',
  instance: '/api/perfil/atual/materiais/resumo-de-grafos',
});

/** Material do dono como o contrato o devolve (recorte do exemplo `comMateriais`). */
const material = (estado, extras = {}) => ({
  slug: 'resumo-de-grafos',
  titulo: 'Resumo de grafos',
  descricao: 'Resumo de percurso em largura e em profundidade, com exercícios resolvidos.',
  estado,
  categoria: { slug: 'computacao-e-tecnologia', nome: 'Computação e Tecnologia' },
  tipo: { slug: 'slides', nome: 'Slides' },
  licenca: {
    slug: 'todos-os-direitos-reservados',
    nome: 'Todos os direitos reservados',
    descricaoCurta: 'O material continua protegido. Ler e baixar é permitido; reutilizar não é.',
  },
  arquivo: { nomeOriginal: 'resumo-de-grafos.pdf', tamanhoBytes: 204800, disponivel: true },
  publicadoEm: '2026-09-02T12:00:00Z',
  atualizadoEm: '2026-10-10T18:20:00Z',
  ...extras,
});

console.log('== check:editar-erros — edição de metadados (contrato v0.6.0, RF016) ==\n');

/* ------------------------------------------- 1. quem pode ser editado (ADR-0011 §2 e §6) */

console.log('1. estados em que a edição é oferecida (ADR-0011 §2, §6)');
{
  verificar('PUBLICADO: editável (é o estado do material no ar)', edicaoPermitida('PUBLICADO'));
  verificar('RETIRADO: editável (a retirada é reversível e não tira os dados)', edicaoPermitida('RETIRADO'));
  verificar(
    'EXCLUIDO: NÃO editável (estado terminal — o PUT responde o mesmo 404)',
    edicaoPermitida('EXCLUIDO') === false,
  );
}

/* --------------------------------------- 2. validação local do corpo (EdicaoDeMaterial) */

console.log('\n2. validação local dos cinco campos (limites do contrato: título 1–200, descrição 1–2000)');
{
  const entrada = {
    titulo: 'Resumo de grafos (revisado)',
    descricao: 'Percurso em largura e em profundidade, com exercícios resolvidos.',
    categoriaSlug: 'computacao-e-tecnologia',
    tipoSlug: 'slides',
    licencaSlug: 'todos-os-direitos-reservados',
  };
  const valido = validarEdicaoDeMaterial(entrada);
  verificar('os cinco campos preenchidos e dentro do teto → nenhum erro local', Object.keys(valido).length === 0, JSON.stringify(valido));

  const vazios = validarEdicaoDeMaterial({ titulo: '', descricao: '', categoriaSlug: '', tipoSlug: '', licencaSlug: '' });
  verificar(
    'campos em branco → erro POR CAMPO (título, descrição, categoria, tipo e licença)',
    ['titulo', 'descricao', 'categoriaSlug', 'tipoSlug', 'licencaSlug'].every((campo) => typeof vazios[campo] === 'string'),
    Object.keys(vazios).join(','),
  );
  verificar(
    'nenhuma mensagem local é só "campo inválido" (RNF004: diz o que fazer)',
    Object.values(vazios).every((mensagem) => mensagem.length > 40),
  );

  const espaco = validarEdicaoDeMaterial({ ...entrada, titulo: '   ', descricao: '\n\t ' });
  verificar(
    'só espaço em branco NÃO passa por título nem por descrição (trim antes de decidir)',
    typeof espaco.titulo === 'string' && typeof espaco.descricao === 'string',
    JSON.stringify(Object.keys(espaco)),
  );

  const tituloGrande = validarEdicaoDeMaterial({ ...entrada, titulo: 'x'.repeat(201) });
  verificar(
    'título com 201 caracteres → erro no campo título, e a mensagem diz o teto',
    typeof tituloGrande.titulo === 'string' && tituloGrande.titulo.includes('200'),
    tituloGrande.titulo,
  );
  const tituloNoTeto = validarEdicaoDeMaterial({ ...entrada, titulo: 'x'.repeat(200) });
  verificar('título com exatamente 200 caracteres → aceito (o teto do contrato é inclusivo)', tituloNoTeto.titulo === undefined);

  const descricaoGrande = validarEdicaoDeMaterial({ ...entrada, descricao: 'y'.repeat(2001) });
  verificar(
    'descrição com 2001 caracteres → erro no campo descrição, com o teto na mensagem',
    typeof descricaoGrande.descricao === 'string' && descricaoGrande.descricao.includes('2000'),
    descricaoGrande.descricao,
  );

  const desfechoDosLocais = desfechoDeCamposInvalidos(vazios);
  verificar(
    'os erros locais viram `invalid-field` com o primeiro campo culpado para a âncora do resumo',
    desfechoDosLocais.estado === 'invalid-field' && desfechoDosLocais.campoFoco === 'titulo',
    `${desfechoDosLocais.estado}/${desfechoDosLocais.campoFoco}`,
  );
}

/* ------------------------------ 3. o que a tela NÃO edita, e é dito em vez de escondido */

console.log('\n3. campos não editáveis, visíveis e explicados (PAGE_BLUEPRINTS.md §13, RNF025)');
{
  const publicados = camposNaoEditaveis(material('PUBLICADO'), { tamanhoDoArquivo: '200 KB' });
  verificar('são dois: o endereço público e o arquivo', publicados.length === 2, String(publicados.length));
  verificar('os ids são estáveis e nomeados (`endereco`, `arquivo`)', publicados.map((campo) => campo.id).join(',') === 'endereco,arquivo');

  const endereco = publicados.find((campo) => campo.id === 'endereco');
  verificar(
    'o endereço mostrado é o do material, com o slug que NÃO muda (RNF025)',
    endereco.valor === '/materiais/resumo-de-grafos',
    endereco.valor,
  );
  verificar(
    'a explicação do endereço diz que ele é estável e não muda por edição',
    /chave estável/i.test(endereco.explicacao) && /não muda por edição/i.test(endereco.explicacao),
  );
  verificar(
    'e diz POR QUE ele não é editável aqui (o motivo, não um "não pode")',
    /não é um deles/i.test(endereco.porque),
  );
  verificar(
    'material PUBLICADO → a rota de edição oferece o link da página pública',
    endereco.linkPublico === '/materiais/resumo-de-grafos',
    String(endereco.linkPublico),
  );

  const arquivo = publicados.find((campo) => campo.id === 'arquivo');
  verificar(
    'o arquivo mostrado é o da publicação, com o nome original e o tamanho',
    arquivo.valor === 'resumo-de-grafos.pdf · 200 KB',
    arquivo.valor,
  );
  verificar(
    'a explicação do arquivo diz que trocar o binário é outra operação (não é deste fluxo)',
    /outra operação/i.test(arquivo.porque),
  );
  verificar(
    'e diz por que o estado "arquivo inválido" do §15.3 não aparece nesta tela',
    /não há envio de arquivo/i.test(arquivo.porque),
  );

  const retirados = camposNaoEditaveis(material('RETIRADO'), { tamanhoDoArquivo: '200 KB' });
  const enderecoRetirado = retirados.find((campo) => campo.id === 'endereco');
  verificar(
    'material RETIRADO → SEM link da página pública (ela responde 404 enquanto durar a retirada)',
    enderecoRetirado.linkPublico === undefined,
    String(enderecoRetirado.linkPublico),
  );
  verificar(
    'e a ausência do link é EXPLICADA, não um vazio silencioso',
    /responde "não encontrado"/.test(MOTIVO_SEM_LINK_PUBLICO) && /repor no público/.test(MOTIVO_SEM_LINK_PUBLICO),
  );

  const purgado = camposNaoEditaveis(
    material('EXCLUIDO', { arquivo: { nomeOriginal: 'resumo-de-grafos.pdf', tamanhoBytes: 204800, disponivel: false } }),
    { tamanhoDoArquivo: '200 KB' },
  );
  const arquivoPurgado = purgado.find((campo) => campo.id === 'arquivo');
  verificar(
    'arquivo já purgado pela retenção → o campo DIZ isso, em vez de a pessoa concluir que sumiu por defeito',
    /retenção de 7 dias/.test(arquivoPurgado.porque),
  );
}

/* ------------------------------------------------ 4. as listas da API com a escolha atual */

console.log('\n4. opções do `<select>`: as da API, com a escolha atual preservada (RF018)');
{
  const daApi = [
    { slug: 'computacao-e-tecnologia', nome: 'Computação e Tecnologia' },
    { slug: 'matematica-e-estatistica', nome: 'Matemática e Estatística' },
  ];
  const atual = { slug: 'matematica-e-estatistica', nome: 'Matemática e Estatística' };
  const comAtual = opcoesDaTaxonomia(daApi, atual);
  verificar(
    'escolha atual presente na lista → a lista é a da API, na ordem da API, sem item a mais',
    comAtual.length === 2 && comAtual[0].slug === daApi[0].slug && comAtual[1].slug === daApi[1].slug,
    comAtual.map((item) => item.slug).join(','),
  );

  const foraDaLista = { slug: 'fisica', nome: 'Física' };
  const comFora = opcoesDaTaxonomia(daApi, foraDaLista);
  verificar(
    'escolha atual FORA da lista ativa → ela entra como opção, marcada (o select não fica em branco)',
    comFora.length === 3 && comFora[2].slug === 'fisica' && /atual — fora da lista ativa/.test(comFora[2].nome),
    comFora.map((item) => `${item.slug}:${item.nome}`).join(' | '),
  );
  verificar(
    'o nome usado é o que o SERVIDOR devolveu no material — nenhum nome é inventado na tela',
    comFora[2].nome.startsWith('Física'),
    comFora[2].nome,
  );
  verificar(
    'e as opções da API continuam todas ali, na ordem',
    comFora.slice(0, 2).map((item) => item.slug).join(',') === 'computacao-e-tecnologia,matematica-e-estatistica',
  );
}

/* ---------------------------------------------------- 5. desfecho de sucesso (RF016) */

console.log('\n5. sucesso: a confirmação diz o que mudou, o que não mudou e onde comprovar (RF016)');
{
  const desfecho = desfechoDeMaterialEditado(material('PUBLICADO'));
  verificar('o estado é `success` (§15.3)', desfecho.estado === 'success', desfecho.estado);
  verificar('o título é o do material que o servidor devolveu', desfecho.mensagem.includes('Resumo de grafos'));
  verificar(
    'a mensagem aponta a página pública com o SLUG — é onde a alteração se comprova',
    desfecho.mensagem.includes('/materiais/resumo-de-grafos'),
  );
  verificar(
    'e diz que o endereço, o arquivo e a data de publicação NÃO mudaram (ADR-0011 §4)',
    /endereço público, o arquivo e a data da primeira publicação continuam os mesmos/.test(desfecho.mensagem),
  );
  verificar('nenhuma mensagem de campo entra no sucesso', Object.keys(desfecho.campos).length === 0);
}

/* --------------------------------- 6. falhas do PUT pelo mapeamento do §15.3 (RNF004) */

console.log('\n6. falhas de `PUT /perfil/atual/materiais/{slug}` → estado do §15.3');
{
  const taxonomia = mapearFalhaDeEnvio(http(422, LICENCA_INEXISTENTE), 'editar-material', { contaVerificada: true });
  verificar(
    '422 de taxonomia inexistente/inativa → `invalid-field` com a mensagem NO CAMPO `licencaSlug`',
    taxonomia.estado === 'invalid-field' && typeof taxonomia.campos.licencaSlug === 'string',
    JSON.stringify(taxonomia.campos),
  );
  verificar(
    'o campo culpado vira a âncora "Ir para o campo" (RNF006: erro associado ao controle)',
    taxonomia.campoFoco === 'licencaSlug',
    String(taxonomia.campoFoco),
  );
  verificar(
    'a mensagem do servidor é preservada no campo, sem resumo genérico por cima',
    taxonomia.campos.licencaSlug === 'licença inexistente ou inativa',
    taxonomia.campos.licencaSlug,
  );

  const naoEncontrado = mapearFalhaDeEnvio(http(404, NAO_ENCONTRADO), 'editar-material', { contaVerificada: true });
  verificar(
    '404 (inexistente = alheio = excluído) → `recoverable-failure`, nunca "não encontrado" de página (§15.3 de formulário)',
    naoEncontrado.estado === 'recoverable-failure',
    naoEncontrado.estado,
  );
  verificar(
    'e a mensagem NÃO diz de quem é o material: repete a decisão do ADR-0011 §3 (o mesmo corpo para os três casos)',
    /MESMA resposta/.test(naoEncontrado.mensagem) && /ADR-0011/.test(naoEncontrado.mensagem),
  );
  verificar(
    'a mensagem tranqüiliza sobre o que NÃO aconteceu (nada foi alterado)',
    /Nada foi alterado/.test(naoEncontrado.mensagem),
  );

  const csrf = mapearFalhaDeEnvio(http(403, CSRF_AUSENTE), 'editar-material', { contaVerificada: true });
  verificar(
    '403 de CSRF → `recoverable-failure` com `acessoNegado` e o `detail` do contrato',
    csrf.estado === 'recoverable-failure' && csrf.acessoNegado === true && csrf.mensagem.includes('X-CSRF-Token'),
    csrf.mensagem,
  );
  verificar(
    'e NÃO é confundido com a publicação bloqueada por e-mail não confirmado (editar não exige verificação)',
    csrf.publicacaoBloqueada === undefined && csrf.republicacaoBloqueada === undefined,
  );

  const expirada = mapearFalhaDeEnvio(http(401, problema('nao_autenticado', 401)), 'editar-material', {
    contaVerificada: true,
  });
  verificar(
    '401 → `sessaoEncerrada`, e a tela oferece o login com o destino preservado',
    expirada.sessaoEncerrada === true,
  );

  const rede = mapearFalhaDeEnvio({ motivo: 'sem-resposta' }, 'editar-material');
  verificar(
    'sem resposta → `recoverable-failure` dizendo que nada foi apagado (RNF004)',
    rede.estado === 'recoverable-failure' && /continua preenchido/.test(rede.mensagem),
  );

  const servidor = mapearFalhaDeEnvio(http(500, problema('interno', 500)), 'editar-material');
  verificar(
    '5xx → `recoverable-failure` sem prometer causa, e o que estava preenchido continua lá',
    servidor.estado === 'recoverable-failure' && /nada foi aplicado/.test(servidor.mensagem),
  );

  const corpoInvalido = mapearFalhaDeEnvio({ motivo: 'corpo-invalido' }, 'editar-material');
  verificar('2xx sem o JSON do contrato → `recoverable-failure`', corpoInvalido.estado === 'recoverable-failure');
}

console.log(`\n== ${total - falhas}/${total} verificações PASS · ${falhas} FALHA(s) ==`);
if (falhas > 0) {
  console.log('FAIL');
  process.exit(1);
}
console.log('PASS — a edição de metadados (RF016) decide certo para cada entrada do contrato.');
