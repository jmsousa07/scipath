#!/usr/bin/env node
/**
 * card E4.6, critério 2: o HTML ENTREGUE de `/painel/publicar` traz os campos mínimos do RF012,
 * o rótulo do arquivo, a confirmação de direito e as listas de categoria, tipo e licença vindas
 * da API — tudo presente no DOCUMENTO SERVIDO, sem depender de hidratação (RNF023).
 *
 * Como: pede a página ao servidor de produção que SAIU DO BUILD (`node .next/standalone/server.js`,
 * ver `docs/qa/e4-6-evidencia.sh`) e confere o documento que o servidor entregou. Não é a
 * promessa do componente: é o HTML que chega ao navegador — o mesmo que `curl` mostra.
 *
 * Por que contra um servidor: a página lê a SESSÃO da requisição no servidor (o cookie
 * `HttpOnly` é reenviado à API, `lib/sessao/servidor.ts`) E busca as três listas de taxonomia
 * na API. As duas coisas dependem da requisição; não existe `.html` pronto no disco para conferir.
 *
 * A prova mais forte daqui é a §3: as `<option>` de cada `select` são comparadas, uma a uma,
 * com o que a API devolveu na MESMA origem. Isso é o que separa "lista que veio da API" de
 * "lista codificada na tela" (RF018) — uma asserção de que os conjuntos são IGUAIS, não de que
 * "alguma coisa apareceu".
 *
 * Os três pedidos, e o que cada um prova:
 *   1. cookie de conta ATIVA → o formulário completo, com as três listas;
 *   2. cookie de conta PENDENTE → o bloqueio por verificação (§11): explicação + reenvio, SEM
 *      formulário (preencher um formulário que o servidor recusaria não é caminho);
 *   3. SEM cookie → estado `sessão expirada` com o destino preservado (`/entrar?proximo=…`).
 *
 * Dado privado (RNF013): o duplo de teste (`docs/qa/e4-6-stub-api.mjs`) serve na MESMA resposta
 * campos que o contrato NÃO tem — e-mail de login, id de conta, hash de senha e token — e o
 * script exige que nenhuma dessas strings apareça em nenhum dos documentos.
 *
 * Uso:
 *   node scripts/check-publicar.mjs                      # http://localhost:3000
 *   node scripts/check-publicar.mjs --url http://127.0.0.1:3100
 */
const BASE = (() => {
  const indice = process.argv.indexOf('--url');
  const valor = indice >= 0 ? process.argv[indice + 1] : undefined;
  return (valor ?? 'http://localhost:3000').replace(/\/+$/, '');
})();

/** Cookie de sessão como o contrato o publica; o VALOR é o que distingue o estado da conta. */
const COOKIE = 'SCIPATH_SESSAO';
/** Dados que o contrato NÃO tem e a tela não pode exibir (servidos de propósito pelo duplo). */
const PRIVADOS = ['maria.silva@exemplo.br', 'contaId', 'senhaHash', 'tokenSessao', '$2a$12$', 'opaca-de-exemplo'];

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

async function pedir(caminho, valorDaCookie) {
  const cabecalhos = valorDaCookie ? { cookie: `${COOKIE}=${valorDaCookie}` } : {};
  const resposta = await fetch(`${BASE}${caminho}`, { headers: cabecalhos });
  const bruto = await resposta.text();
  // O React separa trechos de texto interpolado com `<!-- -->` no HTML do servidor. O marcador é
  // detalhe da serialização, não conteúdo: sem removê-lo, uma asserção sobre a frase que a pessoa
  // lê reprovaria por causa dele.
  return { status: resposta.status, html: bruto.replace(/<!-- -->/g, '') };
}

/** Itens de uma página da API na MESMA origem (pelo proxy `/api` do Next). */
async function itensDaApi(caminho) {
  const resposta = await fetch(`${BASE}/api/${caminho}?size=100`, { headers: { accept: 'application/json' } });
  if (!resposta.ok) throw new Error(`API ${caminho} respondeu ${resposta.status}`);
  const corpo = await resposta.json();
  return corpo.items ?? [];
}

const contem = (html, texto) => html.includes(texto);
const idsDe = (html) => new Set([...html.matchAll(/ id="([^"]*)"/g)].map((m) => m[1]));
const rotulosDe = (html) => [...html.matchAll(/<label[^>]*for="([^"]*)"/g)].map((m) => m[1]);
const tituloDe = (html) => /<title[^>]*>([^<]*)<\/title>/.exec(html)?.[1] ?? '';

/** Fragmento do controle que tem `name="<nome>"`. */
function controle(html, nome) {
  const marca = html.indexOf(`name="${nome}"`);
  if (marca < 0) return '';
  const inicio = html.lastIndexOf('<', marca);
  const fim = html.indexOf('>', marca);
  return inicio >= 0 && fim > inicio ? html.slice(inicio, fim + 1) : '';
}

/** Os `value` das `<option>` do `select` de nome `<nome>` (sem o placeholder vazio). */
function opcoesDe(html, nome) {
  const marca = html.indexOf(`name="${nome}"`);
  if (marca < 0) return null;
  const inicio = html.indexOf('>', marca);
  const fim = html.indexOf('</select>', inicio);
  if (inicio < 0 || fim < 0) return null;
  const corpo = html.slice(inicio, fim);
  return [...corpo.matchAll(/<option[^>]*value="([^"]*)"/g)].map((m) => m[1]).filter((valor) => valor !== '');
}

/** Texto de um `<textarea name="…">…</textarea>` (vazio quando não há conteúdo digitado). */
function textarea(html, nome) {
  const marca = html.indexOf(`name="${nome}"`);
  if (marca < 0) return null;
  const inicio = html.indexOf('>', marca);
  const fim = html.indexOf('</textarea>', inicio);
  return inicio >= 0 && fim > inicio ? html.slice(inicio + 1, fim) : null;
}

const iguaisComoConjunto = (a, b) => {
  const A = [...new Set(a)].sort();
  const B = [...new Set(b)].sort();
  return A.length === B.length && A.every((valor, indice) => valor === B[indice]);
};

console.log(`== check:publicar — HTML ENTREGUE de /painel/publicar em ${BASE} ==\n`);

/* --------------------------------------------------- 1. conta ATIVA: o formulário */

console.log('1. GET /painel/publicar com conta ATIVA — o formulário completo, no documento servido');
const { status, html } = await pedir('/painel/publicar', 'ativa');
{
  verificar('HTTP 200', status === 200, String(status));
  verificar('documento com conteúdo (não vazio)', html.length > 6000, `${html.length} bytes`);
  verificar('título da página', tituloDe(html).includes('Publicar material'), tituloDe(html));
  verificar('área privada fora de índice (naoindex)', /<meta name="robots"[^>]*noindex/.test(html));

  console.log('\n1a. campos mínimos do RF012 — presentes no HTML, sem depender de hidratação');
  const campos = [
    ['titulo', '<input', 'título'],
    ['descricao', '<textarea', 'descrição'],
    ['categoriaSlug', '<select', 'categoria'],
    ['tipoSlug', '<select', 'tipo de material'],
    ['licencaSlug', '<select', 'licença'],
    ['arquivo', '<input', 'arquivo'],
    ['declaracaoDireito', '<input', 'confirmação de direito'],
  ];
  for (const [nome, tag, papel] of campos) {
    const marca = controle(html, nome);
    verificar(`campo "${nome}" (${papel}) no HTML`, marca.startsWith(tag), marca.slice(0, 80) || 'ausente');
  }
  verificar('o arquivo é um `<input type="file">` com aceite de PDF', /type="file"/.test(controle(html, 'arquivo')) && /accept="[^"]*application\/pdf/.test(controle(html, 'arquivo')), controle(html, 'arquivo'));
  verificar(
    'a confirmação de direito é uma CAIXA DE MARCAÇÃO obrigatória',
    /type="checkbox"/.test(controle(html, 'declaracaoDireito')) && /required/.test(controle(html, 'declaracaoDireito')),
    controle(html, 'declaracaoDireito'),
  );
  verificar(
    'o rótulo da confirmação fala em direito de compartilhar (RN007, explícito)',
    /direito de compartilhar/i.test(html),
  );

  console.log('\n1b. rótulos associados a cada controle (RNF006)');
  const rotulos = rotulosDe(html);
  const ids = idsDe(html);
  for (const [id, , papel] of [
    ['publicacao-titulo-material', '', 'título'],
    ['publicacao-descricao', '', 'descrição'],
    ['publicacao-categoria', '', 'categoria'],
    ['publicacao-tipo', '', 'tipo'],
    ['publicacao-licenca', '', 'licença'],
    ['publicacao-arquivo', '', 'arquivo'],
    ['publicacao-declaracao', '', 'declaração'],
  ]) {
    verificar(`"${papel}" tem <label for> apontando para o controle`, rotulos.includes(id) && ids.has(id), id);
  }
  verificar(
    'toda mensagem ligada por aria-describedby aponta para id existente',
    [...html.matchAll(/aria-describedby="([^"]*)"/g)]
      .flatMap((m) => m[1].split(/\s+/))
      .every((alvo) => ids.has(alvo)),
  );

  console.log('\n1c. as três listas vêm da API — comparação item a item (RF018)');
  const [categorias, tipos, licencas] = await Promise.all([
    itensDaApi('categorias'),
    itensDaApi('tipos-material'),
    itensDaApi('licencas'),
  ]);
  verificar('a API devolveu categorias, tipos e licenças', categorias.length > 0 && tipos.length > 0 && licencas.length > 0, `${categorias.length}/${tipos.length}/${licencas.length}`);

  const comparacoes = [
    ['categoriaSlug', categorias, 'categorias'],
    ['tipoSlug', tipos, 'tipos de material'],
    ['licencaSlug', licencas, 'licenças'],
  ];
  for (const [nome, itens, papel] of comparacoes) {
    const noHtml = opcoesDe(html, nome);
    verificar(
      `as opções de ${papel} são EXATAMENTE as da API (nada codificado na tela)`,
      noHtml !== null && iguaisComoConjunto(noHtml, itens.map((item) => item.slug)),
      `HTML=[${noHtml?.join(',')}] API=[${itens.map((i) => i.slug).join(',')}]`,
    );
  }

  console.log('\n1d. a licença é explicada em linguagem simples, com o texto da API (RF018, RN006)');
  const semExplicacao = licencas.filter((licenca) => !contem(html, licenca.descricaoCurta));
  verificar(
    'a explicação de CADA licença (descricaoCurta) está no documento entregue',
    semExplicacao.length === 0,
    semExplicacao.map((l) => l.slug).join(','),
  );
  verificar('nenhuma licença vem pré-selecionada', !/selected/.test(controle(html, 'licencaSlug')) && !/data-licenca-escolhida/.test(html));
  verificar(
    'o bloco que explica as licenças está marcado no documento',
    contem(html, 'data-bloco="licencas-explicadas"'),
  );

  console.log('\n1e. o limite do arquivo e o aceite de PDF são ditos na tela (ADR-0005)');
  verificar(
    'o campo de arquivo diz o limite configurado (MB ou KB) e o formato PDF',
    /PDF/.test(html) && /\d+(?:[.,]\d+)?\s*(?:MB|KB)\b/.test(html),
  );

  console.log('\n1f. nenhum dado fictício e nenhum campo privado (§20.1, RNF013)');
  verificar(
    'nenhum material de exemplo aparece na tela',
    !contem(html, 'Introdução a algoritmos de ordenação') && !/material de exemplo/i.test(html),
  );
  verificar('nenhum dado privado no HTML entregue', PRIVADOS.every((agulha) => !contem(html, agulha)), PRIVADOS.filter((a) => contem(html, a)).join(','));
  verificar('o textoarea da descrição chega VAZIO (não há dado inventado)', textarea(html, 'descricao') === '');
}

/* --------------------------------- 2. conta PENDENTE: bloqueio por verificação */

console.log('\n2. GET /painel/publicar com conta PENDENTE_VERIFICACAO — bloqueio explicado, com reenvio');
{
  const pendente = await pedir('/painel/publicar', 'pendente');
  verificar('HTTP 200', pendente.status === 200, String(pendente.status));
  verificar(
    'a página marca o bloqueio por verificação (§11)',
    contem(pendente.html, 'data-aviso="publicacao-bloqueada-por-verificacao"'),
  );
  verificar(
    'o estado da conta está dito no documento (não é suposição do cliente)',
    contem(pendente.html, 'data-estado-conta="PENDENTE_VERIFICACAO"'),
  );
  verificar('a explicação diz o que falta: confirmar o e-mail para publicar', /Confirme o seu e-mail para publicar/i.test(pendente.html));
  verificar('o reenvio da confirmação está oferecido na própria tela', /Reenviar e-mail de confirmação/.test(pendente.html));
  verificar(
    'o formulário NÃO é entregue para quem não pode publicar',
    !contem(pendente.html, 'name="arquivo"') && !contem(pendente.html, 'name="declaracaoDireito"'),
  );
  verificar('nenhum dado privado no documento', PRIVADOS.every((agulha) => !contem(pendente.html, agulha)));
}

/* --------------------------------------- 3. sem sessão: destino preservado */

console.log('\n3. GET /painel/publicar sem cookie — estado de sessão com o destino preservado');
{
  const anonimo = await pedir('/painel/publicar');
  verificar('HTTP 200', anonimo.status === 200, String(anonimo.status));
  verificar('marca o estado de sessão (§15.3)', contem(anonimo.html, 'data-state="session-expired"'));
  verificar(
    'o caminho de entrada preserva o destino da publicação',
    contem(anonimo.html, 'href="/entrar?proximo=%2Fpainel%2Fpublicar"') || contem(anonimo.html, 'href="/entrar?proximo=/painel/publicar"'),
  );
  verificar('nenhum formulário de publicação no documento anônimo', !contem(anonimo.html, 'name="declaracaoDireito"'));
}

console.log(`\n== ${total - falhas}/${total} verificações PASS · ${falhas} FALHA(s) ==`);
if (falhas > 0) {
  console.log('FAIL');
  process.exit(1);
}
console.log('PASS — o HTML entregue de /painel/publicar atende o §11 e o RF018.');
