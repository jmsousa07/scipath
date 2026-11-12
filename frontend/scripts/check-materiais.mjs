#!/usr/bin/env node
/**
 * card E5.6, critérios 2, 3 (parte medida) e 8: o HTML ENTREGUE de `/painel/materiais`, nos sete
 * estados do §15.3, com os quatro quadros de estado da lista — e SEM dado privado.
 *
 * Como: pede a página ao servidor de produção que SAIU DO BUILD
 * (`node .next/standalone/server.js`) e confere o documento que o servidor entregou. Não é a
 * promessa do componente: é o HTML que chega ao navegador — o mesmo que `curl` mostra, sem
 * hidratação (RNF023).
 *
 * Por que contra um servidor: a página lê a SESSÃO da requisição no servidor (o cookie `HttpOnly`
 * é reenviado à API, `lib/sessao/servidor.ts`) E busca a página dos materiais do dono. As duas
 * coisas dependem da requisição; não existe `.html` pronto no disco para conferir.
 *
 * O duplo de teste (`docs/qa/e5-6-stub-api.mjs`) escolhe o cenário pelo VALOR da cookie
 * `SCIPATH_SESSAO`, e é o que permite exercitar os sete estados na mesma execução.
 *
 * A prova mais forte daqui é a §2: os itens do documento são comparados, um a um, com o que a
 * API devolveu na MESMA origem (pelo proxy `/api` do Next). É isso que separa "lista que veio da
 * API" de "lista codificada na tela" (a regra 9 do AGENTS.md — nada de material fictício em
 * tela): o que aparece é EXATAMENTE o que o servidor mandou, nem um item a mais.
 *
 * Uso:
 *   node scripts/check-materiais.mjs                      # http://localhost:3000
 *   node scripts/check-materiais.mjs --url http://127.0.0.1:3100
 */
const BASE = (() => {
  const indice = process.argv.indexOf('--url');
  const valor = indice >= 0 ? process.argv[indice + 1] : undefined;
  return (valor ?? 'http://localhost:3000').replace(/\/+$/, '');
})();

/** Cookie de sessão como o contrato a publica; o VALOR escolhe o cenário do duplo. */
const COOKIE = 'SCIPATH_SESSAO';
/** Dados que o contrato NÃO tem e a tela não pode exibir (servidos de propósito pelo duplo). */
const PRIVADOS = [
  'maria.silva@exemplo.invalid',
  'contaId',
  'senhaHash',
  'tokenSessao',
  '$2a$12$',
  'opaca-de-exemplo',
  'perfilId',
  'chaveArmazenamento',
  'uploads/',
];
/** Canários de conteúdo que NENHUMA tela pode inventar (regra 9 do AGENTS.md). */
const FICTICIOS = ['Material de exemplo', 'material-de-exemplo', 'Lorem ipsum'];

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

/** Itens da lista do dono na MESMA origem (pelo proxy `/api` do Next), com a mesma cookie. */
async function itensDaApi(valorDaCookie) {
  const resposta = await fetch(`${BASE}/api/perfil/atual/materiais`, {
    headers: { cookie: `${COOKIE}=${valorDaCookie}`, accept: 'application/json' },
  });
  if (!resposta.ok) throw new Error(`API respondeu ${resposta.status}`);
  const corpo = await resposta.json();
  return corpo.items ?? [];
}

const contem = (html, texto) => html.includes(texto);
const tituloDe = (html) => /<title[^>]*>([^<]*)<\/title>/.exec(html)?.[1] ?? '';
const estadosDe = (html) => [...new Set([...html.matchAll(/data-state="([^"]*)"/g)].map((m) => m[1]))].sort();

/**
 * Os estados de PÁGINA do §15.3 presentes no documento, na ordem do DRS.
 *
 * Filtrar importa: o cabeçalho (componente de cliente) sai no HTML com `data-state="anonimo"` —
 * a situação do store no servidor, que é o estado do VISITANTE durante a hidratação —, e contar
 * esse marcador como se fosse estado de página faria a asserção "o documento tem UM estado de
 * página" reprovar por um marcador que não é dela.
 */
const ESTADOS_DE_PAGINA = [
  'loading',
  'success',
  'empty',
  'network-error',
  'not-found',
  'unauthorized',
  'session-expired',
];
const estadosDePagina = (html) => ESTADOS_DE_PAGINA.filter((estado) => estadosDe(html).includes(estado));

const idsDe = (html) => new Set([...html.matchAll(/ id="([^"]*)"/g)].map((m) => m[1]));

/**
 * Os blocos de material do documento, na ordem em que aparecem. O corte vai de um
 * `<li data-material=…>` até o PRÓXIMO (ou o fim), e não até `</li>`: dentro de cada material há
 * uma `<ul>` de ações com os seus próprios `<li>`, e cortar no primeiro `</li>` deixaria a maior
 * parte de fora.
 */
function blocosDeMaterial(html) {
  const marcas = [...html.matchAll(/<li data-material="([^"]+)" data-estado="([^"]+)"/g)];
  return marcas.map((marca, indice) => ({
    slug: marca[1],
    estado: marca[2],
    trecho: html.slice(marca.index, marcas[indice + 1]?.index ?? html.length),
  }));
}

/** Marcadores `data-acao` de um trecho, na ordem do documento. */
const acoesDe = (trecho) => [...trecho.matchAll(/data-acao="([^"]+)"/g)].map((m) => m[1]);

console.log(`== check:materiais — HTML ENTREGUE de /painel/materiais em ${BASE} ==\n`);

/* ------------------------------------------------------ 1. os sete estados de página (§15.3) */

console.log('1. os sete estados do §15.3, medidos no documento entregue');
{
  const semSessao = await pedir('/painel/materiais');
  verificar('sem cookie → HTTP 200', semSessao.status === 200, String(semSessao.status));
  verificar(
    'sem cookie → estado `sessão expirada` (§15.3)',
    estadosDe(semSessao.html).includes('session-expired'),
    JSON.stringify(estadosDe(semSessao.html)),
  );
  verificar(
    'sem cookie → o login preserva o destino /painel/materiais',
    contem(semSessao.html, 'href="/entrar?proximo=%2Fpainel%2Fmateriais"') ||
      contem(semSessao.html, 'href="/entrar?proximo=/painel/materiais"'),
  );
  verificar(
    'sem cookie → NÃO entrega lista nenhuma (nada de material no documento do visitante)',
    !contem(semSessao.html, '<li data-material='),
  );
  verificar('página privada fora de índice (RNF024, CA011)', /<meta name="robots"[^>]*noindex/.test(semSessao.html));
  verificar(
    'sem cookie → nenhum dado privado e nenhum material fictício',
    PRIVADOS.every((agulha) => !contem(semSessao.html, agulha)) &&
      FICTICIOS.every((agulha) => !contem(semSessao.html, agulha)),
    PRIVADOS.filter((agulha) => contem(semSessao.html, agulha)).join(','),
  );

  const comErro = await pedir('/painel/materiais', 'falha');
  verificar(
    'leitura que faltou (500) → estado `erro de rede/servidor`, com "tentar de novo"',
    estadosDe(comErro.html).includes('network-error') && /Tentar de novo/.test(comErro.html),
    JSON.stringify(estadosDe(comErro.html)),
  );
  verificar(
    'erro de servidor → a lista NÃO é exibida (não se mostra acervo que não é o real)',
    !contem(comErro.html, '<li data-material='),
  );

  const recusada = await pedir('/painel/materiais', 'negada');
  verificar(
    '403 → estado `não autorizado` (e não "falha de conexão")',
    estadosDe(recusada.html).includes('unauthorized'),
    JSON.stringify(estadosDe(recusada.html)),
  );

  const paginaInvalida = await pedir('/painel/materiais?page=abc', 'ativa');
  verificar(
    '`?page=abc` → estado `não encontrado` (o endereço não forma listagem)',
    estadosDe(paginaInvalida.html).includes('not-found'),
    JSON.stringify(estadosDe(paginaInvalida.html)),
  );
  verificar(
    'a página recusada aparece com o valor e o formato esperado (não é descartada em silêncio)',
    contem(paginaInvalida.html, 'page') &&
      /abc/.test(paginaInvalida.html) &&
      /número inteiro de página/.test(paginaInvalida.html),
  );

  const vazio = await pedir('/painel/materiais', 'vazia');
  verificar('conta sem material → estado `vazio`', estadosDe(vazio.html).includes('empty'), JSON.stringify(estadosDe(vazio.html)));
  verificar(
    'o vazio orienta o PRIMEIRO PASSO (RF015): leva a publicar',
    contem(vazio.html, 'data-aviso="vazio-primeiro-passo"') &&
      contem(vazio.html, 'href="/painel/publicar"'),
  );
  verificar(
    'o vazio diz o estado da conta que ele considerou (ATIVO, com a conta ativa)',
    contem(vazio.html, 'data-estado-conta="ATIVO"'),
  );

  const vazioPendente = await pedir('/painel/materiais', 'pendente');
  verificar(
    'conta pendente e sem material → estado `vazio` com o estado da conta dito',
    estadosDe(vazioPendente.html).includes('empty') &&
      contem(vazioPendente.html, 'data-estado-conta="PENDENTE_VERIFICACAO"'),
  );
  verificar(
    'conta pendente → o primeiro passo é CONFIRMAR O E-MAIL, na área do usuário — e não uma tela que a bloquearia',
    /exige e-mail confirmado/i.test(vazioPendente.html) &&
      contem(vazioPendente.html, 'href="/painel"') &&
      !contem(vazioPendente.html, 'href="/painel/publicar"'),
  );

  const alemDoFim = await pedir('/painel/materiais?page=2', 'ativa');
  verificar(
    'página além do fim → estado `vazio` que diz que a lista termina antes (não é "não há material")',
    estadosDe(alemDoFim.html).includes('empty') && /não há item nesta página/i.test(alemDoFim.html),
  );
  verificar(
    'e oferece o caminho para a última página (que existe, calculada do envelope)',
    contem(alemDoFim.html, 'href="/painel/materiais"'),
  );
}

/* --------------------------------------- 2. sucesso: os quatro quadros de estado da lista */

console.log('\n2. sucesso — a lista do dono é EXATAMENTE a da API (regra 9 do AGENTS.md)');
const { status, html } = await pedir('/painel/materiais', 'ativa');
const itens = await itensDaApi('ativa');
{
  verificar('HTTP 200', status === 200, String(status));
  verificar('documento com conteúdo (não vazio)', html.length > 6000, `${html.length} bytes`);
  verificar('título da página', tituloDe(html).includes('Materiais próprios'), tituloDe(html));
  verificar('área privada fora de índice (RNF024, CA011)', /<meta name="robots"[^>]*noindex/.test(html));
  verificar(
    'o estado de página do documento é `sucesso` — e é o ÚNICO (o grep de QA tem resposta única)',
    estadosDePagina(html).join(',') === 'success',
    JSON.stringify(estadosDePagina(html)),
  );

  const blocos = blocosDeMaterial(html);
  verificar(
    'a API devolveu os três materiais (pré-requisito da comparação)',
    itens.length === 3,
    String(itens.length),
  );
  verificar(
    'o documento tem UM bloco por material da API — nem a mais, nem a menos',
    blocos.length === itens.length,
    `HTML=${blocos.length} API=${itens.length}`,
  );
  verificar(
    'os slugs do documento são EXATAMENTE os da API (nada codificado na tela)',
    blocos.map((bloco) => bloco.slug).join(',') === itens.map((item) => item.slug).join(','),
    `HTML=[${blocos.map((b) => b.slug).join(',')}] API=[${itens.map((i) => i.slug).join(',')}]`,
  );
  verificar(
    'o estado de cada bloco é o que a API informou',
    blocos.every((bloco, indice) => bloco.estado === itens[indice].estado),
    blocos.map((bloco) => `${bloco.slug}:${bloco.estado}`).join(' '),
  );
  verificar(
    'nenhum material fictício no documento (a tela não inventa acervo)',
    FICTICIOS.every((agulha) => !contem(html, agulha)),
    FICTICIOS.filter((agulha) => contem(html, agulha)).join(','),
  );
  verificar(
    'nenhum dado privado no documento (RNF013, RNF014)',
    PRIVADOS.every((agulha) => !contem(html, agulha)),
    PRIVADOS.filter((agulha) => contem(html, agulha)).join(','),
  );

  console.log('\n2a. cada material mostra o que a lista promete: título, tipo, categoria, estado e datas');
  for (const item of itens) {
    const bloco = blocos.find((candidato) => candidato.slug === item.slug);
    verificar(`  ${item.slug}: título no documento`, contem(bloco.trecho, item.titulo), item.titulo);
    verificar(`  ${item.slug}: tipo no documento`, contem(bloco.trecho, item.tipo.nome), item.tipo.nome);
    verificar(
      `  ${item.slug}: categoria no documento`,
      contem(bloco.trecho, item.categoria.nome),
      item.categoria.nome,
    );
    verificar(
      `  ${item.slug}: licença com a explicação da API (RN006)`,
      contem(bloco.trecho, item.licenca.nome) && contem(bloco.trecho, item.licenca.descricaoCurta),
      item.licenca.slug,
    );
    verificar(
      `  ${item.slug}: as duas datas em <time dateTime>`,
      contem(bloco.trecho, `<time dateTime="${item.publicadoEm}"`) ||
        contem(bloco.trecho, `datetime="${item.publicadoEm}"`),
      item.publicadoEm,
    );
    verificar(
      `  ${item.slug}: o estado aparece com a leitura do que ele significa`,
      new RegExp(`data-leitura-do-estado="${item.estado}"`).test(bloco.trecho),
      item.estado,
    );
  }

  console.log('\n2b. quadro `PUBLICADO` — editar, retirar e excluir, com o link da página pública');
  {
    const bloco = blocos.find((candidato) => candidato.estado === 'PUBLICADO');
    const acoes = acoesDe(bloco.trecho);
    verificar(
      'as três ações do estado, na ordem: editar, retirar, excluir',
      acoes.slice(0, 3).join(',') === 'editar,retirar,excluir',
      acoes.join(','),
    );
    verificar(
      'NÃO oferece `republicar` (o material já está no público)',
      !acoes.includes('republicar'),
    );
    verificar(
      'a ação de editar leva à rota de edição por SLUG (RNF025)',
      contem(bloco.trecho, `href="/painel/materiais/${bloco.slug}/editar"`),
    );
    verificar(
      'link para a página pública, porque o material está no ar',
      (bloco.trecho.match(new RegExp(`data-link-publico="${bloco.slug}"`, 'g')) ?? []).length >= 1 &&
        contem(bloco.trecho, `href="/materiais/${bloco.slug}"`),
    );
    verificar(
      'RNF007: a ação diz o que faz também em texto — "Retirar do público"',
      contem(bloco.trecho, 'Retirar do público'),
    );
    verificar(
      'cada ação é nomeada para quem usa leitor de tela (o rótulo sozinho seria ambíguo numa lista)',
      /class="sc-visually-hidden"> — o material|<span class="sc-visually-hidden"> — o material|<span class="sc-visually-hidden"> o material/.test(
        bloco.trecho,
      ),
    );
  }

  console.log('\n2c. quadro `RETIRADO` — repor no público, e sem link para uma página que responde 404');
  {
    const bloco = blocos.find((candidato) => candidato.estado === 'RETIRADO');
    const acoes = acoesDe(bloco.trecho);
    verificar(
      'as três ações do estado, na ordem: editar, repor, excluir',
      acoes.slice(0, 3).join(',') === 'editar,republicar,excluir',
      acoes.join(','),
    );
    verificar('NÃO oferece `retirar` (já está retirado)', !acoes.includes('retirar'));
    verificar(
      'diz que a retirada é REVERSÍVEL, e o que ela fez com o público',
      /reversível/i.test(bloco.trecho) && /não aparece na busca/i.test(bloco.trecho),
    );
    verificar(
      'NÃO linka a página pública (ela responde 404 enquanto durar a retirada — link quebrado não é promessa)',
      !contem(bloco.trecho, `data-link-publico="${bloco.slug}"`) &&
        !new RegExp(`href="/materiais/${bloco.slug}"`).test(bloco.trecho),
    );
    verificar('a ação de repor é dita em texto: "Repor no público"', contem(bloco.trecho, 'Repor no público'));
  }

  console.log('\n2d. quadro `EXCLUIDO` — NENHUMA ação (estado terminal, ADR-0011 §2 e §6)');
  {
    const bloco = blocos.find((candidato) => candidato.estado === 'EXCLUIDO');
    verificar(
      'nenhum botão de ação no material excluído',
      acoesDe(bloco.trecho).length === 0,
      acoesDe(bloco.trecho).join(','),
    );
    verificar(
      'a ausência de ação é EXPLICADA, não uma linha muda',
      contem(bloco.trecho, `data-sem-acao="${bloco.slug}"`) &&
        /estado terminal/i.test(bloco.trecho) &&
        /não existe restauração/i.test(bloco.trecho),
    );
    verificar(
      'NÃO linka a página pública nem a edição',
      !new RegExp(`href="/materiais/${bloco.slug}"`).test(bloco.trecho) &&
        !contem(bloco.trecho, `/painel/materiais/${bloco.slug}/editar`),
    );
    verificar(
      'diz o que aconteceu com o arquivo (a retenção o remove em 7 dias)',
      contem(bloco.trecho, 'data-arquivo="removido"') && /retenção de 7 dias/i.test(bloco.trecho),
    );
  }

  console.log('\n2e. a confirmação da exclusão está no documento entregue (RF017, ADR-0011 §7)');
  {
    const comDialogo = blocos.filter((bloco) => bloco.estado !== 'EXCLUIDO');
    const semDialogo = blocos.filter((bloco) => bloco.estado === 'EXCLUIDO');
    verificar(
      'os materiais com ação de excluir trazem o diálogo de confirmação',
      comDialogo.every((bloco) => contem(bloco.trecho, 'data-dialogo="exclusao"')),
    );
    verificar(
      'o material excluído NÃO traz diálogo (não há exclusão a confirmar)',
      semDialogo.every((bloco) => !contem(bloco.trecho, 'data-dialogo="exclusao"')),
    );
    verificar(
      'o diálogo diz que NÃO HÁ COMO DESFAZER, em texto',
      contem(html, 'Não há como desfazer'),
    );
    verificar(
      'o diálogo diz que o arquivo deixa de estar disponível em 7 dias',
      /deixa de estar disponível imediatamente e é removido do armazenamento em 7 dias/.test(html),
    );
    verificar(
      'o diálogo oferece a alternativa reversível (retirar) em vez de só proibir',
      /use .{0,8}Retirar do público/.test(html),
    );
    verificar(
      'o diálogo tem nome acessível (aria-labelledby apontando para um título que existe)',
      [...html.matchAll(/<dialog[^>]*aria-labelledby="([^"]+)"/g)].every((m) => idsDe(html).has(m[1])),
    );
    verificar(
      'retirar e repor NÃO pedem confirmação (são reversíveis): só a exclusão abre diálogo',
      (html.match(/data-dialogo="exclusao"/g) ?? []).length === comDialogo.length,
      `${(html.match(/data-dialogo="exclusao"/g) ?? []).length} diálogos para ${comDialogo.length} materiais vivos`,
    );
  }

  console.log('\n2f. acessibilidade medida no documento entregue (RNF006, RNF007)');
  {
    const descritos = [...html.matchAll(/aria-describedby="([^"]*)"/g)]
      .flatMap((m) => m[1].split(/\s+/))
      .filter(Boolean);
    verificar(
      `todo aria-describedby aponta para um id existente — ${descritos.length} referências`,
      descritos.every((alvo) => idsDe(html).has(alvo)),
      descritos.filter((alvo) => !idsDe(html).has(alvo)).join(','),
    );
    verificar(
      'o estado do material é dito em TEXTO (o badge não é o único portador — RNF007)',
      itens.every((item) => contem(html, item.estado)) && /Publicado|Retirado|Excluído/.test(html),
    );
    verificar(
      'a contagem é anunciada em texto e em região `status`',
      /role="status"/.test(html) && /\d+ materiais seus|1 material seu/.test(html),
    );
  }
}

console.log(`\n== ${total - falhas}/${total} verificações PASS · ${falhas} FALHA(s) ==`);
if (falhas > 0) {
  console.log('FAIL');
  process.exit(1);
}
console.log('PASS — o HTML entregue de /painel/materiais atende os sete estados, os quatro quadros e o RNF013.');
