#!/usr/bin/env node
/**
 * card E3.6, critério 2: o HTML ENTREGUE das telas de acesso contém o formulário e os
 * campos principais — verificável sem JavaScript.
 *
 * Como: pede as páginas ao servidor de produção que SAIU DO BUILD (`node
 * .next/standalone/server.js`, ver `docs/qa/e3-6-*.sh`) e confere o documento que o
 * servidor entregou. Não é a promessa do componente: é o HTML que chega ao navegador —
 * e o mesmo que um `curl` mostra (o critério pede o HTML de cada página por `curl`).
 *
 * Por que contra um servidor e não contra `.next/server/app/*.html`: `/entrar` lê
 * `?proximo=` e `?motivo=` no servidor (para o aviso de sessão expirada sair no HTML) e
 * por isso é renderizada por requisição — não existe `.html` pronto no disco. O alvo
 * certo, aqui, é o documento servido.
 *
 * Uso:
 *   node scripts/check-sessao.mjs                      # http://localhost:3000
 *   node scripts/check-sessao.mjs --url http://127.0.0.1:3100
 */
const BASE = (() => {
  const indice = process.argv.indexOf('--url');
  const valor = indice >= 0 ? process.argv[indice + 1] : undefined;
  return (valor ?? 'http://localhost:3000').replace(/\/+$/, '');
})();

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

function idsDe(html) {
  return new Set([...html.matchAll(/ id="([^"]*)"/g)].map((m) => m[1]));
}

/** Texto do `<title>` do documento entregue. */
function tituloDe(html) {
  return /<title[^>]*>([^<]*)<\/title>/.exec(html)?.[1] ?? '';
}

function atributosDe(html, seletorAtributo) {
  return [...html.matchAll(new RegExp(`${seletorAtributo}="([^"]*)"`, 'g'))].map((m) => m[1]);
}

/** Fragmento do `<input …>` que tem `name="<nome>"`, para inspecionar o controle. */
function campo(html, nome) {
  const marca = html.indexOf(`name="${nome}"`);
  if (marca < 0) return '';
  const inicio = html.lastIndexOf('<input', marca);
  const fim = html.indexOf('>', marca);
  return inicio >= 0 && fim > inicio ? html.slice(inicio, fim + 1) : '';
}

async function buscar(caminho) {
  const resposta = await fetch(`${BASE}${caminho}`, {
    headers: { accept: 'text/html' },
    redirect: 'manual',
  });
  const html = await resposta.text();
  return { status: resposta.status, html };
}

/**
 * Conferências comuns às duas telas: formulário real, rótulo associado a cada controle
 * (CA009/RNF006), ajuda apontada por `aria-describedby` existente e nenhuma credencial
 * dentro do documento (RNF009).
 */
function conferirFormulario(titulo, html, camposEsperados) {
  console.log(`\n${titulo}`);
  verificar('documento HTML completo entregue', html.startsWith('<!DOCTYPE html>'), html.slice(0, 40));
  verificar('contém <form method="post">', /<form[^>]*method="post"/.test(html));

  const ids = idsDe(html);
  const rotulos = new Set(atributosDe(html, 'for'));

  for (const esperado of camposEsperados) {
    const controle = campo(html, esperado.nome);
    verificar(`campo ${esperado.nome} presente (type=${esperado.tipo})`, controle !== '', 'input ausente no HTML');
    if (controle === '') continue;
    verificar(`  ${esperado.nome}: type="${esperado.tipo}"`, new RegExp(`type="${esperado.tipo}"`).test(controle), controle);
    verificar(`  ${esperado.nome}: marcado como obrigatório`, /required/.test(controle), controle);
    const id = /id="([^"]*)"/.exec(controle)?.[1];
    verificar(`  ${esperado.nome}: rótulo associado (<label for>)`, id !== undefined && rotulos.has(id), `id=${id}`);
    if (esperado.tipo === 'password') {
      verificar(`  ${esperado.nome}: não leva valor no HTML entregue`, /value=""/.test(controle) || !/value=/.test(controle), controle);
    }
  }

  const descritos = atributosDe(html, 'aria-describedby')
    .flatMap((valor) => valor.split(/\s+/))
    .filter(Boolean);
  const orfaos = descritos.filter((id) => !ids.has(id));
  verificar(
    `todo aria-describedby aponta para id existente (${descritos.length} referências)`,
    orfaos.length === 0,
    orfaos.join(', '),
  );

  verificar(
    'a página diz que a senha não é guardada em texto',
    /nem é guardada|guarda só o hash/.test(html),
    'nenhuma das duas telas oferece a frase sobre hash da senha',
  );
}

console.log('== check:sessao — HTML entregue das telas de acesso (card E3.6) ==');
console.log(`base: ${BASE}\n`);

// --- 1. /entrar (visitante) -------------------------------------------------
const entrar = await buscar('/entrar');
verificar(`GET /entrar responde 200 (recebeu ${entrar.status})`, entrar.status === 200);
verificar('<title> identifica a tela', tituloDe(entrar.html).startsWith('Entrar'), tituloDe(entrar.html));
verificar('canonical estável para /entrar', /rel="canonical" href="[^"]*\/entrar"/.test(entrar.html));
verificar('cabeçalho entrega os caminhos de entrada (sem JS)', entrar.html.includes('href="/entrar"') && entrar.html.includes('href="/cadastro"'));
verificar('cabeçalho marcado como anônimo no HTML entregue', entrar.html.includes('data-state="anonimo"'));
conferirFormulario('formulário de login no HTML entregue:', entrar.html, [
  { nome: 'email', tipo: 'email' },
  { nome: 'senha', tipo: 'password' },
]);
verificar('aponta o caminho do cadastro', /href="\/cadastro"/.test(entrar.html));

// --- 2. /entrar?motivo=sessao-expirada&proximo=… ----------------------------
const expirada = await buscar('/entrar?motivo=sessao-expirada&proximo=/painel/perfil');
verificar(`\nGET /entrar?motivo=sessao-expirada responde 200 (recebeu ${expirada.status})`, expirada.status === 200);
verificar('estado "sessão expirada" sai no HTML do servidor (sem JS)', expirada.html.includes('data-state="session-expired"'));
verificar('o destino pedido é preservado e exibido', expirada.html.includes('/painel/perfil'));
verificar('o formulário continua na tela', expirada.html.includes('name="senha"'));

// --- 3. destino externo é recusado (open redirect) --------------------------
// O valor cru aparece no documento? Sim, dentro do payload do roteador do próprio Next
// (a URL pedida, como o navegador a enviou) — isso não é destino. O que precisa NÃO
// existir é o valor virando link ou aparecendo como "destino guardado".
const externo = await buscar('/entrar?motivo=sessao-expirada&proximo=//outro-host/x');
verificar(
  'proximo externo (//outro-host) não vira link nem destino guardado',
  !/href="\/\/outro-host/.test(externo.html) && !externo.html.includes('Destino guardado'),
  'o valor foi aceito como destino pelo servidor',
);
verificar('a tela ainda mostra a sessão expirada, sem destino', externo.html.includes('data-state="session-expired"'));

// --- 4. /cadastro -----------------------------------------------------------
const cadastro = await buscar('/cadastro');
verificar(`\nGET /cadastro responde 200 (recebeu ${cadastro.status})`, cadastro.status === 200);
verificar('<title> identifica a tela', tituloDe(cadastro.html).startsWith('Criar conta'), tituloDe(cadastro.html));
verificar('canonical estável para /cadastro', /rel="canonical" href="[^"]*\/cadastro"/.test(cadastro.html));
verificar(
  'o aviso de que publicar exige e-mail confirmado está ANTES do formulário',
  cadastro.html.indexOf('Depois de confirmar o e-mail') > 0 &&
    cadastro.html.indexOf('Depois de confirmar o e-mail') < cadastro.html.indexOf('name="nomePublico"'),
);
verificar('o aviso diz o que libera na hora (navegar, buscar, editar perfil)', /Na hora:/.test(cadastro.html));
verificar('o aviso diz a validade do link (24 h)', /24 h/.test(cadastro.html));
verificar('a tela aponta termos de uso e privacidade', cadastro.html.includes('href="/termos"') && cadastro.html.includes('href="/privacidade"'));
conferirFormulario('formulário de cadastro no HTML entregue:', cadastro.html, [
  { nome: 'nomePublico', tipo: 'text' },
  { nome: 'email', tipo: 'email' },
  { nome: 'senha', tipo: 'password' },
  { nome: 'senhaConfirmacao', tipo: 'password' },
]);

console.log(`\nresumo: ${total} verificações · ${falhas === 0 ? 'todas passaram' : `${falhas} falharam`}`);
process.exit(falhas === 0 ? 0 : 1);
