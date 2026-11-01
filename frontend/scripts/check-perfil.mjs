#!/usr/bin/env node
/**
 * card E3.7, critérios 2, 5 (parte verificável) e 6: o HTML ENTREGUE de `/painel` e
 * `/painel/perfil` contém o conteúdo principal, os campos e a marca de autodeclaração — e
 * NENHUM dado privado.
 *
 * Como: pede as páginas ao servidor de produção que SAIU DO BUILD
 * (`node .next/standalone/server.js`, ver `docs/qa/e3-7-evidencia.sh`) e confere o documento que
 * o servidor entregou. Não é a promessa do componente: é o HTML que chega ao navegador — o mesmo
 * que um `curl` mostra (o critério do card pede o HTML de cada página por `curl`).
 *
 * Por que contra um servidor: as duas rotas leem a SESSÃO DA REQUISIÇÃO no servidor (o cookie
 * `HttpOnly` é reenviado à API, `lib/sessao/servidor.ts`) para entregar o formulário já
 * preenchido. O documento depende do cookie que vai na requisição; não existe `.html` pronto no
 * disco para conferir.
 *
 * Os três pedidos de cada rota, e o que cada um prova:
 *   1. SEM cookie                     → estado `sessão expirada` com o destino preservado
 *      (`/entrar?proximo=…`), que é o desfecho do `anonimo` — inclusive para quem nunca entrou;
 *   2. cookie de conta PENDENTE        → o conteúdo da área/da edição, com a verificação
 *      pendente e o caminho de reenvio;
 *   3. cookie de conta ATIVA           → o outro estado de conta, para o HTML não afirmar a
 *      pendência de quem já confirmou o e-mail.
 *
 * Dado privado (RNF013): o duplo de teste (`docs/qa/e3-7-stub-api.mjs`) serve na MESMA resposta
 * campos que o contrato não tem — e-mail de login, id de conta, hash de senha e token — e o
 * script exige que nenhuma dessas strings apareça em nenhum dos documentos. É a prova de que a
 * tela escreve campo por campo em vez de despejar o objeto da API.
 *
 * Uso:
 *   node scripts/check-perfil.mjs                      # http://localhost:3000
 *   node scripts/check-perfil.mjs --url http://127.0.0.1:3100
 */
const BASE = (() => {
  const indice = process.argv.indexOf('--url');
  const valor = indice >= 0 ? process.argv[indice + 1] : undefined;
  return (valor ?? 'http://localhost:3000').replace(/\/+$/, '');
})();

/** Cookie de sessão como o contrato o publica; o VALOR é o que distingue o estado da conta. */
const COOKIE = 'SCIPATH_SESSAO';
/** Dados que o contrato NÃO tem e a tela não pode exibir (servidos de propósito pelo duplo). */
const PRIVADOS = [
  'maria.silva@exemplo.br',
  'contaId',
  'senhaHash',
  'tokenSessao',
  '$2a$12$',
  'opaca-de-exemplo',
];

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
  // O React separa trechos de texto interpolado com `<!-- -->` no HTML do servidor
  // ("Declarado por <!-- -->João Souza"). O marcador é detalhe da serialização, não conteúdo:
  // sem removê-lo, uma asserção sobre a frase que a pessoa lê reprovaria por causa dele.
  return { status: resposta.status, html: bruto.replace(/<!-- -->/g, '') };
}

const idsDe = (html) => new Set([...html.matchAll(/ id="([^"]*)"/g)].map((m) => m[1]));
const rotulosDe = (html) => [...html.matchAll(/<label[^>]*for="([^"]*)"/g)].map((m) => m[1]);
const estadosDe = (html) => [...new Set([...html.matchAll(/data-state="([^"]*)"/g)].map((m) => m[1]))].sort();
const tituloDe = (html) => /<title[^>]*>([^<]*)<\/title>/.exec(html)?.[1] ?? '';

/** Fragmento do controle que tem `name="<nome>"`, para inspecionar o campo entregue. */
function campo(html, nome) {
  const marca = html.indexOf(`name="${nome}"`);
  if (marca < 0) return '';
  const inicio = html.lastIndexOf('<', marca);
  const fim = html.indexOf('>', marca);
  return inicio >= 0 && fim > inicio ? html.slice(inicio, fim + 1) : '';
}

/** Conteúdo de um `<textarea name="…">…</textarea>`. */
function textarea(html, nome) {
  const marca = html.indexOf(`name="${nome}"`);
  if (marca < 0) return null;
  const inicio = html.indexOf('>', marca);
  const fim = html.indexOf('</textarea>', inicio);
  return inicio >= 0 && fim > inicio ? html.slice(inicio + 1, fim) : null;
}

function contem(html, texto) {
  return html.includes(texto);
}

console.log(`== check:perfil — HTML ENTREGUE de /painel e /painel/perfil em ${BASE} ==\n`);

/* ------------------------------------------------------------------ /painel */

console.log('1. GET /painel SEM cookie — estado "sessão necessária" com destino preservado');
{
  const { status, html } = await pedir('/painel');
  const estados = estadosDe(html);
  verificar('HTTP 200', status === 200, String(status));
  verificar('documento com conteúdo (não vazio)', html.length > 4000, `${html.length} bytes`);
  verificar(
    'marca o estado de sessão expirada/não autenticada',
    estados.includes('session-expired'),
    JSON.stringify(estados),
  );
  verificar(
    'o caminho de entrada preserva o destino (/entrar?proximo=/painel)',
    contem(html, 'href="/entrar?proximo=%2Fpainel"') || contem(html, 'href="/entrar?proximo=/painel"'),
  );
  verificar('não mostra o conteúdo da área (sem formulário de perfil)', !contem(html, 'name="nomePublico"'));
  verificar('não vaza dado privado', !PRIVADOS.some((p) => contem(html, p)));
}

console.log('\n2. GET /painel com cookie de conta PENDENTE_VERIFICACAO');
{
  const { status, html } = await pedir('/painel', 'pendente');
  verificar('HTTP 200', status === 200, String(status));
  verificar('título do documento', tituloDe(html).includes('Área do usuário'), tituloDe(html));
  verificar('mostra o nome público da conta', contem(html, 'João Souza'));
  verificar(
    'marca o estado da conta como pendente no HTML',
    contem(html, 'data-estado-conta="PENDENTE_VERIFICACAO"'),
  );
  verificar('diz o que a conta JÁ pode fazer (navegar, buscar)', contem(html, 'navega'));
  verificar(
    'diz o que a pendência bloqueia (publicar material)',
    /publicar material/i.test(html) && /exige|bloquead/i.test(html),
  );
  verificar(
    'oferece o caminho para reenviar o e-mail (ADR-0003 (h))',
    contem(html, 'Reenviar e-mail de confirmação') && contem(html, 'data-aviso="reenvio-de-verificacao"'),
  );
  verificar('linka a edição do perfil', contem(html, 'href="/painel/perfil"'));
  verificar('linka a descoberta de materiais (tela que existe)', contem(html, 'href="/explorar"'));
  verificar(
    'aponta para a tela de publicação, que existe desde o E4.6 (/painel/publicar)',
    contem(html, 'href="/painel/publicar"'),
  );
  verificar(
    'aponta para a listagem de materiais próprios, que existe desde o E5.6 (/painel/materiais)',
    contem(html, 'href="/painel/materiais"'),
  );
  verificar(
    'NÃO afirma mais que não existem telas de publicação (frase falsa do E4.6 removida)',
    !/não existem telas de publica/i.test(html),
  );
  verificar(
    'o cartão de publicação avisa que publicar exige e-mail confirmado (ADR-0003 (b))',
    contem(html, 'data-acao="publicar"') && /Publicar exige[\s\S]{0,120}e-mail confirmado/.test(html),
  );
  // Critério 6 do card E5.6: as frases que NEGAVAM a listagem saíram (eram duas: a do lead e a
  // do parágrafo abaixo dos cartões). Negar tela existente e prometer tela inexistente são o
  // MESMO defeito — o §21.5 vale nos dois sentidos.
  verificar(
    'NÃO afirma mais que a listagem dos materiais próprios não existe (o que deixou de ser verdade no E5.6)',
    !/listagem dos (seus )?próprios materiais[\s\S]{0,160}ainda não existe/i.test(html),
  );
  verificar(
    'e o cartão de gerenciamento entrou na área do usuário, com o link para a listagem',
    contem(html, 'data-acao="materiais-proprios"') && /Ver os meus materiais/.test(html),
  );
  verificar(
    'mostra a afiliação como declaração (RN011)',
    contem(html, 'Afiliação declarada') && contem(html, 'Declarado por João Souza'),
  );
  verificar('diz que não verifica vínculo institucional', /não verifica vínculo institucional/i.test(html));
  verificar(
    'não usa selo/marca de verificado',
    !/verificado<\/|selo de verifica|instituição verificada/i.test(html),
  );
  verificar('diz que dado privado não aparece (RNF013)', contem(html, 'RNF013') || /E-mail de login, senha/.test(html));
  verificar('página privada fora de índice (RNF024, CA011)', contem(html, 'noindex'));
  verificar('não vaza dado privado', !PRIVADOS.some((p) => contem(html, p)));
}

console.log('\n3. GET /painel com cookie de conta ATIVO — o outro estado de conta');
{
  const { status, html } = await pedir('/painel', 'ativa');
  verificar('HTTP 200', status === 200, String(status));
  verificar('marca o estado da conta como ativa', contem(html, 'data-estado-conta="ATIVO"'));
  verificar('explica que o que a plataforma verifica é o e-mail, e nada além', contem(html, 'nada além'));
  verificar('linka o perfil público no endereço estável', contem(html, 'href="/u/joao-souza"'));
  // Critério 2 do card E4.10: com a conta ATIVO, o HTML entregue aponta para a publicação e
  // não carrega mais a afirmação de que essa tela não existe.
  verificar(
    'aponta para a tela de publicação, que existe (/painel/publicar)',
    contem(html, 'href="/painel/publicar"'),
  );
  verificar(
    'NÃO contém a frase que negava a tela de publicação',
    !/não existem telas de publica/i.test(html),
  );
  verificar(
    'o cartão de publicação convida a publicar (conta ativa, sem aviso de pendência)',
    contem(html, 'data-acao="publicar"') && /Envie um PDF/.test(html) && !/Publicar exige/.test(html),
  );
  verificar(
    'NÃO mostra o bloco de reenvio (conta já confirmada)',
    !contem(html, 'data-aviso="reenvio-de-verificacao"'),
  );
  verificar('não vaza dado privado', !PRIVADOS.some((p) => contem(html, p)));
}

/* ------------------------------------------------------------ /painel/perfil */

console.log('\n4. GET /painel/perfil SEM cookie — estado "sessão necessária"');
{
  const { status, html } = await pedir('/painel/perfil');
  verificar('HTTP 200', status === 200, String(status));
  verificar(
    'marca o estado de sessão expirada',
    estadosDe(html).includes('session-expired'),
    JSON.stringify(estadosDe(html)),
  );
  verificar(
    'preserva o destino da edição no login',
    contem(html, 'href="/entrar?proximo=%2Fpainel%2Fperfil"') || contem(html, 'href="/entrar?proximo=/painel/perfil"'),
  );
  verificar('NÃO entrega formulário de edição para visitante', !contem(html, 'name="slug"'));
  verificar('não vaza dado privado', !PRIVADOS.some((p) => contem(html, p)));
}

console.log('\n5. GET /painel/perfil com sessão — campos do RF011 no HTML entregue, sem JS');
{
  const { status, html } = await pedir('/painel/perfil', 'pendente');
  const ids = idsDe(html);
  const rotulos = rotulosDe(html);

  verificar('HTTP 200', status === 200, String(status));
  verificar('título do documento', tituloDe(html).includes('Editar perfil'), tituloDe(html));

  // Os seis campos do RF011, um a um, com rótulo associado e valor carregado.
  const campos = [
    ['nomePublico', 'perfil-nome'],
    ['slug', 'perfil-slug'],
    ['biografia', 'perfil-biografia'],
    ['afiliacaoDeclarada', 'perfil-afiliacao'],
    ['areasInteresse', 'perfil-areas'],
    ['linksExternos', 'perfil-links'],
  ];
  for (const [nome, id] of campos) {
    verificar(`campo ${nome} entregue no HTML`, campo(html, nome) !== '' || textarea(html, nome) !== null);
    verificar(`campo ${nome} tem id estável (${id}) e rótulo associado`, ids.has(id) && rotulos.includes(id));
  }

  verificar(
    'os rótulos dizem o que é cada campo, com os opcionais marcados',
    contem(html, 'Nome público') &&
      contem(html, 'Identificador público (slug)') &&
      contem(html, 'Biografia curta') &&
      contem(html, 'Instituição / afiliação declarada (opcional)') &&
      contem(html, 'Áreas de interesse (opcional)') &&
      contem(html, 'Site pessoal ou perfil acadêmico (opcional)'),
  );

  // Valores carregados da API (o duplo devolve o perfil completo): sem JS, o formulário já vem
  // com o que a pessoa escreveu — e o opcional vazio vem vazio, sem quebrar a tela.
  verificar('valor atual do nome público carregado', contem(campo(html, 'nomePublico'), 'value="João Souza"'));
  verificar('valor atual do slug carregado', contem(campo(html, 'slug'), 'value="joao-souza"'));
  verificar('biografia carregada no textarea', contem(textarea(html, 'biografia') ?? '', 'Estudante de'));
  verificar('afiliação carregada no textarea', contem(textarea(html, 'afiliacaoDeclarada') ?? '', 'Universidade'));
  verificar('áreas carregadas, uma por linha', contem(textarea(html, 'areasInteresse') ?? '', 'algoritmos'));
  verificar(
    'campo opcional vazio não quebra a tela (links vazios = textarea vazia)',
    (textarea(html, 'linksExternos') ?? null) !== null,
  );

  verificar('botão de envio no HTML', /type="submit"[^>]*>[\s\S]{0,200}Salvar perfil/.test(html));

  verificar(
    'a instituição aparece como AUTODECLARADA na tela (RN011, CA012)',
    contem(html, 'data-aviso="afiliacao-autodeclarada"') &&
      contem(html, 'autodeclarações de quem escreve o perfil'),
    'marcador data-aviso ausente',
  );
  verificar(
    'a frase da afiliação é atribuída a quem a escreveu',
    contem(html, 'Afiliação declarada') && contem(html, 'Declarado por'),
  );
  verificar(
    'diz que não existe selo, medalha ou marca de verificado',
    /não existe selo, medalha ou marca/i.test(html) || /não existe selo, medalha/i.test(html),
  );

  verificar('prévia do perfil público no HTML', contem(html, 'data-previa="perfil"'));
  verificar('prévia mostra o endereço estável com o slug', contem(html, '/u/joao-souza'));
  verificar(
    'prévia avisa que o perfil público só abre com e-mail confirmado',
    /passa a responder a visitantes quando a confirmação do e-mail for concluída/i.test(html),
  );
  verificar('página privada fora de índice (RNF024, CA011)', contem(html, 'noindex'));
  verificar('não vaza dado privado', !PRIVADOS.some((p) => contem(html, p)));
}

console.log('\n6. GET /painel/perfil com conta ATIVA — a prévia passa a ser pública');
{
  const { html } = await pedir('/painel/perfil', 'ativa');
  verificar(
    'a prévia diz que o endereço está aberto a visitantes',
    /aberto a visitantes, sem sessão/i.test(html),
  );
  verificar('e linka o perfil público no fim da página', contem(html, 'href="/u/joao-souza"'));
  verificar('não vaza dado privado', !PRIVADOS.some((p) => contem(html, p)));
}

console.log('\n7. Os estados do §15.3 presentes nos documentos entregues');
{
  const semSessao = await pedir('/painel');
  const semSessaoEdicao = await pedir('/painel/perfil');
  const comSessao = await pedir('/painel', 'pendente');
  const edicao = await pedir('/painel/perfil', 'pendente');

  verificar(
    'a área do usuário marca o estado da conta (sucesso da leitura de sessão) no HTML',
    contem(comSessao.html, 'data-estado-conta="PENDENTE_VERIFICACAO"'),
  );
  verificar(
    'sem sessão, os DOIS caminhos entregam o estado de sessão expirada',
    estadosDe(semSessao.html).includes('session-expired') &&
      estadosDe(semSessaoEdicao.html).includes('session-expired'),
  );
  verificar(
    'com sessão, nenhum dos dois entrega estado de erro',
    !estadosDe(comSessao.html).concat(estadosDe(edicao.html)).some((e) => e === 'network-error'),
  );
  verificar(
    'o formulário de edição anuncia o envio em andamento (form com aria-busy no HTML)',
    /<form[^>]*aria-busy=/.test(edicao.html),
    'sem aria-busy no form entregue',
  );
  verificar(
    'nenhuma das páginas depende de conteúdo escondido atrás de JavaScript para o texto principal',
    comSessao.html.includes('João Souza') && edicao.html.includes('Salvar perfil'),
  );
}

console.log(`\nresumo: ${total} verificações · ${falhas === 0 ? 'todas passaram' : `${falhas} falharam`}`);
process.exit(falhas === 0 ? 0 : 1);
