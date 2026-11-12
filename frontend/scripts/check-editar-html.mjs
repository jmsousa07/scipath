#!/usr/bin/env node
/**
 * Bateria do HTML entregue da rota de edição (E5.7, RF016/RF018).
 *
 * Executa contra o build de produção já gerado: inicia o duplo de API publicado em
 * docs/qa/e5-7-stub-api.mjs e `next start`, busca a página por HTTP e verifica o documento
 * entregue sem Chromium. O mapeamento do 422 para o controle é coberto por
 * check-editar-erros.mjs; o clique/teclado e os screenshots pertencem ao card de QA.
 */
import { spawn } from 'node:child_process';
import { rmSync } from 'node:fs';
import { setTimeout as sleep } from 'node:timers/promises';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const frontend = resolve(fileURLToPath(new URL('..', import.meta.url)));
const repo = resolve(frontend, '..');
const stubPath = resolve(repo, 'docs/qa/e5-7-stub-api.mjs');
const nextBin = resolve(frontend, 'node_modules/next/dist/bin/next');
const apiPort = 8199;
const webPort = 3180;
const apiOrigin = `http://127.0.0.1:${apiPort}`;
const webOrigin = `http://127.0.0.1:${webPort}`;
const children = [];
let total = 0;
let failures = 0;

function verify(label, condition, detail = '') {
  total += 1;
  if (condition) {
    console.log(`PASS  ${label}`);
    return;
  }
  failures += 1;
  console.log(`FALHA ${label}${detail ? ` — ${detail}` : ''}`);
}

function start(command, args, options) {
  const child = spawn(command, args, { stdio: 'ignore', ...options });
  children.push(child);
  return child;
}

async function aguardar(url, { cookie } = {}) {
  const limite = Date.now() + 30000;
  let ultimaFalha = 'sem resposta';
  while (Date.now() < limite) {
    try {
      const resposta = await fetch(url, { headers: cookie ? { cookie } : {} });
      return resposta;
    } catch (erro) {
      ultimaFalha = erro.message;
      await sleep(250);
    }
  }
  throw new Error(`servidor não iniciou em 30 s (${ultimaFalha})`);
}

async function documento(cookie) {
  const resposta = await fetch(`${webOrigin}/painel/materiais/resumo-de-grafos/editar`, {
    headers: cookie ? { cookie } : {},
  });
  return { resposta, html: await resposta.text() };
}

try {
  console.log('== check:editar-html — rota privada /painel/materiais/[slug]/editar ==\n');
  // Clear only the taxonomy fetch cache so failure and inactive-option scenarios are repeatable.
  rmSync(resolve(frontend, '.next/cache/fetch-cache'), { recursive: true, force: true });
  start(process.execPath, [stubPath, String(apiPort)]);
  await aguardar(`${apiOrigin}/api/sessoes/atual`);

  start(process.execPath, [nextBin, 'start', '--hostname', '127.0.0.1', '--port', String(webPort)], {
    cwd: frontend,
    env: { ...process.env, API_BASE_URL: `${apiOrigin}/api` },
  });
  await aguardar(`${webOrigin}/painel/materiais/resumo-de-grafos/editar`);

  // As listas de taxonomia têm cache de uma hora por decisão do cliente. Exercite a falha antes
  // da primeira resposta bem-sucedida, e a categoria desativada como primeira resposta cacheada.
  await fetch(`${apiOrigin}/__test/scenario?value=taxonomia-falha`);
  const taxonomiaFalha = await documento('SCIPATH_SESSAO=ativa');
  verify('falha ao ler listas da API não abre selects vazios',
    taxonomiaFalha.html.includes('Não foi possível carregar as listas de categoria, tipo e licença') &&
    !taxonomiaFalha.html.includes('id="edicao-titulo-material"'),
    `falha=${taxonomiaFalha.html.includes('Não foi possível carregar as listas de categoria, tipo e licença')}; formulario=${taxonomiaFalha.html.includes('id="edicao-titulo-material"')}`);

  await fetch(`${apiOrigin}/__test/scenario?value=semlinks`);
  const semLista = await documento('SCIPATH_SESSAO=ativa');
  verify('opção atual fora da taxonomia ativa segue selecionável com aviso',
    semLista.html.includes('Computação e Tecnologia (atual — fora da lista ativa)'),
    semLista.html.slice(Math.max(0, semLista.html.indexOf('Computação') - 80), semLista.html.indexOf('Computação') + 180) || 'texto ausente');
  await fetch(`${apiOrigin}/__test/scenario`);

  const autenticado = await documento('SCIPATH_SESSAO=ativa');
  verify('a rota entrega HTML 200 com sessão confirmada', autenticado.resposta.status === 200, String(autenticado.resposta.status));
  verify('a rota privada sai com robots noindex', /name="robots"[^>]*noindex/i.test(autenticado.html));
  verify('título e descrição chegam pré-preenchidos do material lido',
    /value="Resumo de grafos"/.test(autenticado.html) && /Resumo de percurso em largura/.test(autenticado.html));
  verify('a categoria, tipo e licença vêm da API e aparecem no HTML',
    ['Matemática e Estatística', 'Computação e Tecnologia', 'Nota de aula', 'Todos os direitos reservados', 'Você pode reutilizar e adaptar o material'].every((texto) => autenticado.html.includes(texto)));
  verify('as três escolhas atuais são as opções selecionadas pelo slug devolvido pelo material',
    ['computacao-e-tecnologia', 'slides', 'todos-os-direitos-reservados'].every((slug) =>
      autenticado.html.includes(`value="${slug}" selected=""`)));
  verify('endereço público e arquivo são visíveis, explicados e não campos editáveis',
    autenticado.html.includes('/materiais/resumo-de-grafos') && autenticado.html.includes('resumo-de-grafos.pdf') &&
    autenticado.html.includes('Trocar o arquivo de um material publicado é outra operação') &&
    !/name="(?:slug|arquivo|fileAssetId)"/.test(autenticado.html));
  verify('o HTML não expõe valores privados servidos pelo duplo',
    !['maria.silva@exemplo.invalid', 'senhaHash', 'tokenSessao', 'chaveArmazenamento', 'uploads/2026/09'].some((privado) => autenticado.html.includes(privado)),
    ['maria.silva@exemplo.invalid', 'senhaHash', 'tokenSessao', 'chaveArmazenamento', 'uploads/2026/09'].filter((privado) => autenticado.html.includes(privado)).join(', '));

  const retirado = await documento('SCIPATH_SESSAO=retirado');
  verify('material RETIRADO continua editável e explica por que não há link público',
    retirado.resposta.status === 200 && retirado.html.includes('RETIRADO') &&
    retirado.html.includes('Enquanto o material está retirado, a página pública dele responde') &&
    !retirado.html.includes('data-link-publico="resumo-de-grafos"'));

  const excluido = await documento('SCIPATH_SESSAO=excluido');
  verify('material EXCLUIDO mostra o estado terminal sem abrir formulário',
    excluido.resposta.status === 200 && excluido.html.includes('o estado é terminal') && !excluido.html.includes('id="edicao-titulo-material"'),
    `status=${excluido.resposta.status}; aviso=${excluido.html.includes('o estado é terminal')}; campo=${excluido.html.includes('id="edicao-titulo-material"')}`);

  const anonimo = await documento();
  verify('sem sessão, informa que é necessário entrar e preserva o destino',
    anonimo.resposta.status === 200 && anonimo.html.includes('Entre para editar este material') &&
    anonimo.html.includes(encodeURIComponent('/painel/materiais/resumo-de-grafos/editar')));

  const alheio = await documento('SCIPATH_SESSAO=alheio');
  verify('material de outra conta é tratado como não encontrado',
    alheio.resposta.status === 200 && alheio.html.includes('Este endereço não corresponde a um material seu'));

  console.log(`\n== ${total - failures}/${total} verificações PASS · ${failures} FALHA(s) ==`);
  if (failures > 0) process.exitCode = 1;
} catch (erro) {
  console.error(`ERRO: ${erro.stack ?? erro}`);
  process.exitCode = 1;
} finally {
  for (const child of children.reverse()) {
    if (child.exitCode === null && child.signalCode === null) child.kill('SIGTERM');
  }
  await Promise.all(children.map((child) => new Promise((resolveExit) => {
    if (child.exitCode !== null || child.signalCode !== null) return resolveExit();
    child.once('exit', resolveExit);
    setTimeout(resolveExit, 5000).unref();
  })));
}
