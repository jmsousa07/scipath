#!/usr/bin/env node
/**
 * card E5.6, critério 1 (parte executável): as DUAS leituras do dono, exercitadas contra uma
 * API de verdade (o duplo de teste `docs/qa/e5-6-stub-api.mjs`) — caminho, contagem de página,
 * cookie de sessão e a tradução de cada resposta de erro.
 *
 * Por que um script Node e não o navegador: a prova do card de implementação é `next build` +
 * lint + typecheck + HTML entregue (AGENTS.md — navegador é ferramenta do card de QA). E por
 * que vale a pena medi-lo contra HTTP e não só contra um objeto: o que se prova aqui é o
 * CAMINHO que a página percorre — `/perfil/atual/materiais` com o `page` 0-based, sem `size`, e
 * com a cookie de sessão no cabeçalho. Um teste de unidade com `fetch` substituído provaria a
 * intenção; este prova a requisição.
 *
 * O que este script NÃO prova: que o BACKEND responde assim (isso é teste do backend, cards
 * E5.2/E5.3/E5.4, e do card de QA) e que a lista RENDERIZA (isso é `check-materiais.mjs`, o HTML
 * entregue). Prova que o cliente de leitura do frontend pede o que o contrato diz e classifica a
 * resposta como o §15.3 exige.
 *
 * Os tipos são removidos por `scripts/ts-loader.mjs` (registrado por `scripts/ts-registrar.mjs`);
 * o módulo importado é `src/lib/api/client.ts`, que não tem `import` de runtime nenhum — é por
 * isso que ele roda fora do Next. As ESCRITAS não podem ser exercitadas aqui: elas vivem em
 * `src/lib/sessao/navegador.ts` e dependem do `document.cookie` do `XSRF-TOKEN`. O mapeamento
 * das falhas delas é exercitado por `check-materiais-erros.mjs`, e a forma das requisições
 * (`PUT`/`PATCH`/`DELETE` com `X-CSRF-Token`) é medida por `curl` na evidência do card.
 *
 * Uso:
 *   API_BASE_URL=http://127.0.0.1:8098/api node --import ./scripts/ts-registrar.mjs scripts/check-materiais-api.mjs
 *   node --import ./scripts/ts-registrar.mjs scripts/check-materiais-api.mjs --url http://127.0.0.1:8098/api
 */
import { createServer } from 'node:http';

// O cliente lê `API_BASE_URL` a cada requisição: apontar a base ANTES de chamar é o suficiente,
// e é assim que este script troca de duplo no meio da bateria sem reimportar o módulo.
const BASE = (() => {
  const indice = process.argv.indexOf('--url');
  const valor = indice >= 0 ? process.argv[indice + 1] : process.env.API_BASE_URL;
  if (!valor) {
    console.error('FALHA informe a base da API: --url http://127.0.0.1:8098/api  (ou API_BASE_URL)');
    process.exit(2);
  }
  return valor.replace(/\/+$/, '');
})();

process.env.API_BASE_URL = BASE;

const { listarMateriaisProprios, obterMaterialProprio, baseDaApi } = await import('../src/lib/api/client.ts');

const COOKIE_ATIVA = 'SCIPATH_SESSAO=ativa';
const COOKIE_VAZIA = 'SCIPATH_SESSAO=vazia';
const COOKIE_FALHA = 'SCIPATH_SESSAO=falha';
const COOKIE_NEGADA = 'SCIPATH_SESSAO=negada';

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

/** Os três materiais do duplo, um por estado — o mesmo conjunto do exemplo do contrato. */
const ESPERADOS = [
  { slug: 'introducao-a-algoritmos-de-ordenacao', estado: 'PUBLICADO' },
  { slug: 'resumo-de-grafos', estado: 'RETIRADO' },
  { slug: 'lista-de-exercicios-de-calculo', estado: 'EXCLUIDO' },
];

console.log(`== check:materiais-api — as leituras do dono contra ${baseDaApi()} ==\n`);

/* ------------------------------------------------------------------ sanidade da base */

console.log('0. a base que este script está medindo é a informada (não a padrão)');
verificar('`baseDaApi()` devolve a base informada', baseDaApi() === BASE, baseDaApi());
{
  const sonda = await fetch(`${BASE}/perfil/atual/materiais`, {
    headers: { cookie: COOKIE_ATIVA, accept: 'application/json' },
  });
  verificar('a API do duplo responde 200 na lista (pré-requisito da bateria)', sonda.status === 200, String(sonda.status));
}

/* ------------------------------------------------- 1. listar: caminho, página, sessão */

console.log('\n1. `listarMateriaisProprios` — a página dos três estados (RF015, ADR-0011 §2)');
{
  const resultado = await listarMateriaisProprios(COOKIE_ATIVA, 0);
  verificar('a leitura tem sucesso', resultado.ok === true, JSON.stringify(resultado).slice(0, 120));
  if (resultado.ok) {
    const pagina = resultado.dados;
    verificar('`total` vem do envelope (3 materiais)', pagina.total === 3, String(pagina.total));
    verificar('`size` vem do envelope (padrão do contrato: 20 — o cliente não o envia)', pagina.size === 20, String(pagina.size));
    verificar('a primeira página é a 0 (0-based no contrato)', pagina.page === 0, String(pagina.page));
    verificar('os três itens vieram', pagina.items.length === 3, String(pagina.items.length));
    verificar(
      'a lista traz os TRÊS estados — esconder do dono o que ele excluiu seria desonesto',
      ['PUBLICADO', 'RETIRADO', 'EXCLUIDO'].every((estado) =>
        pagina.items.some((item) => item.estado === estado),
      ),
      pagina.items.map((item) => item.estado).join(','),
    );
    verificar(
      'a ordem é a do contrato: `atualizadoEm` DESC (o que mexi por último, primeiro)',
      pagina.items.map((item) => item.atualizadoEm).every((valor, indice, todos) =>
        indice === 0 ? true : todos[indice - 1] >= valor,
      ),
      pagina.items.map((item) => item.atualizadoEm).join(' > '),
    );
    verificar(
      'cada item traz o que a tela exibe: tipo, categoria, licença EXPLICADA, arquivo e as duas datas',
      pagina.items.every(
        (item) =>
          item.tipo?.nome &&
          item.categoria?.nome &&
          item.licenca?.nome &&
          item.licenca?.descricaoCurta &&
          typeof item.arquivo?.disponivel === 'boolean' &&
          item.publicadoEm &&
          item.atualizadoEm,
      ),
    );
    verificar(
      'nenhum item carrega `autor` (a autoria é sempre a sessão — RN001)',
      pagina.items.every((item) => item.autor === undefined),
    );
    verificar(
      'o item EXCLUIDO mostra o arquivo como indisponível (retenção: metadado fica, binário sai)',
      pagina.items.find((item) => item.estado === 'EXCLUIDO')?.arquivo?.disponivel === false,
    );
    verificar(
      'os slugs são EXATAMENTE os do contrato (nada inventado, nada faltando)',
      pagina.items.map((item) => item.slug).join(',') === ESPERADOS.map((item) => item.slug).join(','),
      pagina.items.map((item) => item.slug).join(','),
    );
  }
}

console.log('\n2. a paginação vai no caminho — página 0 omitida, página N enviada');
{
  // `page=2` (0-based) com `size=20` está além do fim da lista de 3: o duplo devolve a página
  // pedida, VAZIA e com `total: 3` — é o que separa "não tenho material" de "esta página está
  // além do fim", e é o que prova que o parâmetro FOI enviado (sem ele viriam 3 itens).
  const resultado = await listarMateriaisProprios(COOKIE_ATIVA, 2);
  verificar('a leitura de uma página além do fim tem sucesso (200, não erro)', resultado.ok === true);
  if (resultado.ok) {
    verificar('a página devolvida é a pedida', resultado.dados.page === 2, String(resultado.dados.page));
    verificar('a página além do fim vem vazia', resultado.dados.items.length === 0, String(resultado.dados.items.length));
    verificar(
      '`total` continua sendo o da consulta inteira (3) — o cliente não inventa metadado',
      resultado.dados.total === 3,
      String(resultado.dados.total),
    );
  }
}

console.log('\n3. o conjunto vazio é 200, nunca erro (RNF004) — é dele que a tela tira o primeiro passo');
{
  const resultado = await listarMateriaisProprios(COOKIE_VAZIA, 0);
  verificar('conta sem material → sucesso', resultado.ok === true);
  if (resultado.ok) {
    verificar('`items: []` e `total: 0`', resultado.dados.items.length === 0 && resultado.dados.total === 0);
  }
}

/* ------------------------------------------------------- 4. as respostas de erro → `Falha` */

console.log('\n4. cada resposta de erro vira a `Falha` que a página traduz no estado do §15.3');
{
  const semSessao = await listarMateriaisProprios('', 0);
  verificar(
    'sem cookie de sessão → 401 classificado como `http` com o `ProblemDetail` do contrato',
    semSessao.ok === false &&
      semSessao.falha.motivo === 'http' &&
      semSessao.falha.status === 401 &&
      semSessao.falha.problem?.type === 'urn:scipath:erro:nao_autenticado',
    JSON.stringify(semSessao).slice(0, 160),
  );

  const recusada = await listarMateriaisProprios(COOKIE_NEGADA, 0);
  verificar(
    '403 → `http` 403 com `acesso_negado` (a tela mostra `não autorizado`, não "falha de conexão")',
    recusada.ok === false &&
      recusada.falha.motivo === 'http' &&
      recusada.falha.status === 403 &&
      recusada.falha.problem?.type === 'urn:scipath:erro:acesso_negado',
    JSON.stringify(recusada).slice(0, 160),
  );

  const servidor = await listarMateriaisProprios(COOKIE_FALHA, 0);
  verificar(
    '500 → `http` 500 (o estado é `erro de rede/servidor`, com "tentar de novo")',
    servidor.ok === false && servidor.falha.motivo === 'http' && servidor.falha.status === 500,
    JSON.stringify(servidor).slice(0, 160),
  );
}

/* ------------------------------------------------------------------ 5. ler um material próprio */

console.log('\n5. `obterMaterialProprio` — a leitura que pré-preenche a edição (E5.7)');
{
  const retirado = await obterMaterialProprio(COOKIE_ATIVA, 'resumo-de-grafos');
  verificar('lê um material RETIRADO — o detalhe público responde 404 para ele (RN002)', retirado.ok === true);
  if (retirado.ok) {
    verificar('o estado devolvido é RETIRADO', retirado.dados.estado === 'RETIRADO', retirado.dados.estado);
    verificar('o detalhe traz os cinco campos que a edição pré-preenche', Boolean(
      retirado.dados.titulo && retirado.dados.descricao && retirado.dados.categoria?.slug &&
        retirado.dados.tipo?.slug && retirado.dados.licenca?.slug,
    ));
  }

  const excluido = await obterMaterialProprio(COOKIE_ATIVA, 'lista-de-exercicios-de-calculo');
  verificar(
    'a LEITURA vale em `EXCLUIDO` (só as AÇÕES exigem material vivo — ADR-0011 §2–§3)',
    excluido.ok === true && excluido.dados.estado === 'EXCLUIDO',
    excluido.ok ? excluido.dados.estado : JSON.stringify(excluido.falha),
  );

  const inexistente = await obterMaterialProprio(COOKIE_ATIVA, 'material-de-exemplo');
  verificar(
    'material inexistente → 404 com o corpo do 404 público (não 403: o status não é oráculo)',
    inexistente.ok === false &&
      inexistente.falha.motivo === 'http' &&
      inexistente.falha.status === 404 &&
      inexistente.falha.problem?.type === 'urn:scipath:erro:nao_encontrado',
    JSON.stringify(inexistente).slice(0, 160),
  );

  const codificado = await obterMaterialProprio(COOKIE_ATIVA, 'a/b c');
  verificar(
    'slug com caractere especial vai CODIFICADO no caminho (não vira outro endereço)',
    codificado.ok === false && codificado.falha.motivo === 'http' && codificado.falha.status === 404,
    JSON.stringify(codificado).slice(0, 120),
  );
}

/* --------------------------------------------- 6. os dois motivos de falha que não são HTTP */

console.log('\n6. os motivos `rede` e `resposta` — o que a tela distingue de "o servidor respondeu errado"');
{
  // Uma base que responde 200 com HTML: é o caso "houve resposta 2xx, mas não é o JSON do
  // contrato" — a tela precisa dizer isso em vez de mostrar sucesso vazio.
  const html = createServer((_req, res) => {
    res.statusCode = 200;
    res.setHeader('content-type', 'text/html; charset=utf-8');
    res.end('<!doctype html><html><body>não é o contrato</body></html>');
  });
  await new Promise((resolve) => html.listen(0, '127.0.0.1', resolve));
  const portaHtml = html.address().port;

  const baseBoa = process.env.API_BASE_URL;
  process.env.API_BASE_URL = `http://127.0.0.1:${portaHtml}/api`;
  const corpoErrado = await listarMateriaisProprios(COOKIE_ATIVA, 0);
  verificar(
    '2xx com corpo que não é o JSON do contrato → motivo `resposta`',
    corpoErrado.ok === false && corpoErrado.falha.motivo === 'resposta',
    JSON.stringify(corpoErrado).slice(0, 120),
  );

  // Porta fechada: não houve resposta nenhuma.
  process.env.API_BASE_URL = 'http://127.0.0.1:1/api';
  const semResposta = await listarMateriaisProprios(COOKIE_ATIVA, 0);
  verificar(
    'sem resposta (porta fechada) → motivo `rede`',
    semResposta.ok === false && semResposta.falha.motivo === 'rede',
    JSON.stringify(semResposta).slice(0, 120),
  );

  process.env.API_BASE_URL = baseBoa;
  html.close();
  await new Promise((resolve) => html.close(resolve));
}

/* ----------------------------------------------------------------------------- resumo */

console.log(`\n== ${total - falhas}/${total} verificações PASS · ${falhas} FALHA(s) ==`);
if (falhas > 0) {
  console.log('FAIL');
  process.exit(1);
}
console.log('PASS — as duas leituras do dono pedem o que o contrato diz e classificam cada resposta.');
