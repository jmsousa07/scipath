#!/usr/bin/env node
/**
 * Verificação do card E1.7, critério 1: "cada um dos 13 estados é alcançável e
 * verificável — com a evidência colada".
 *
 * Como: depois de `npm run build`, procura no HTML DE PRODUÇÃO da rota de
 * demonstração (`/_estados`) o marcador `data-state="<estado>"` de cada um dos sete
 * estados de página e dos seis estados de formulário do DRS §15.3. Não é a promessa
 * do componente: é o HTML que o servidor entrega.
 *
 * Confere também o que o card exige junto com os estados:
 *   - a rota está fora de índice (`noindex`), por ser página de verificação;
 *   - a página se declara demonstração (`data-aviso="demonstracao"`), para o
 *     conteúdo de exemplo não ser lido como acervo real (§20.1);
 *   - nenhuma mensagem de estado é só a palavra "erro" (RNF004);
 *   - ACESSIBILIDADE, no HTML e não na intenção (RNF006): todo campo tem
 *     `<label for>`; todo `aria-describedby` aponta para um id que existe; todo
 *     controle marcado como inválido aponta para a mensagem de erro dele;
 *   - cada família de primitiva do critério 3 aparece no HTML.
 *
 * A conferência de FOCO, teclado e leitura de tela é humana e cabe ao card de QA:
 * este script mede presença e estrutura, não substitui o olho nem o teclado.
 *
 * Uso: node scripts/check-states.mjs
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const frontendDir = path.resolve(here, '..');
const APP_DIR = path.join(frontendDir, '.next', 'server', 'app');

const ESTADOS_PAGINA = [
  'loading',
  'success',
  'empty',
  'network-error',
  'not-found',
  'unauthorized',
  'session-expired',
];

const ESTADOS_FORMULARIO = [
  'invalid-field',
  'submitting',
  'recoverable-failure',
  'success',
  'duplicate-attempt',
  'invalid-file',
];

/** Primitivas que o critério 3 exige: marcação/classe presente no HTML entregue. */
const PRIMITIVAS = [
  ['botão (variante)', 'sc-btn--primary'],
  ['botão desabilitado', 'sc-btn--dis'],
  ['botão em andamento (indicador)', 'sc-spin'],
  ['campo de texto', 'sc-field__label'],
  ['campo de arquivo', 'type="file"'],
  ['card', 'sc-card__copy'],
  ['badge', 'sc-badge--green'],
  ['chip', 'sc-chip__dot'],
  ['tabela com cabeçalho', '<th scope="col">'],
  ['paginação com página atual', 'aria-current="page"'],
];

function walk(dir) {
  const out = [];
  if (!fs.existsSync(dir)) return out;
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) out.push(...walk(full));
    else out.push(full);
  }
  return out;
}

console.log('== check:states — os 13 estados do DRS §15.3 no HTML de produção ==');

const htmls = walk(APP_DIR).filter((arquivo) => arquivo.endsWith('.html'));
if (htmls.length === 0) {
  console.error(`FALHA nenhum HTML em ${path.relative(frontendDir, APP_DIR)}.`);
  console.error('      Rode `npm run build` antes de `npm run check:states`.');
  process.exit(1);
}

const alvo = htmls.find((arquivo) => fs.readFileSync(arquivo, 'utf8').includes('data-page="estados"'));
if (!alvo) {
  console.error('FALHA a rota de demonstração `/_estados` não tem HTML gerado.');
  console.error(`      HTMLs encontrados: ${htmls.map((h) => path.relative(frontendDir, h)).join(', ')}`);
  process.exit(1);
}

const html = fs.readFileSync(alvo, 'utf8');
console.log(`arquivo verificado: ${path.relative(frontendDir, alvo)} (${html.length} bytes)\n`);

let falhas = 0;

function verificar(titulo, condicao, detalhe) {
  if (condicao) {
    console.log(`PASS  ${titulo}`);
  } else {
    console.log(`FALHA ${titulo}${detalhe ? `  — ${detalhe}` : ''}`);
    falhas += 1;
  }
}

const atributos = (nome) => [...html.matchAll(new RegExp(`${nome}="([^"]*)"`, 'g'))].map((m) => m[1]);
const ids = new Set([...html.matchAll(/ id="([^"]*)"/g)].map((m) => m[1]));

console.log('estados de página (7):');
for (const estado of ESTADOS_PAGINA) {
  verificar(`  ${estado}`, html.includes(`data-state="${estado}"`), 'marcador data-state ausente');
}

console.log('\nestados de formulário (6):');
for (const estado of ESTADOS_FORMULARIO) {
  verificar(`  ${estado}`, html.includes(`data-state="${estado}"`), 'marcador data-state ausente');
}

console.log('\ncondições que acompanham os estados:');
verificar('página de verificação fora de índice (noindex)', /<meta name="robots" content="noindex/i.test(html));
verificar('a página se declara demonstração (§20.1)', html.includes('data-aviso="demonstracao"'));
verificar('nenhuma mensagem de estado é só a palavra "erro"', !/>\s*(Erro|ERRO|erro)\s*</.test(html));

console.log('\nacessibilidade medida no HTML (RNF006, RNF007):');
const idsDeCampo = [...html.matchAll(/<input id="([^"]*)"/g)].map((m) => m[1]);
const rotulos = new Set(atributos('for'));
const semRotulo = idsDeCampo.filter((id) => !rotulos.has(id));
verificar(
  `todo campo tem rótulo associado (<label for>) — ${idsDeCampo.length} campos`,
  semRotulo.length === 0,
  semRotulo.join(', '),
);

const descritos = atributos('aria-describedby')
  .flatMap((valor) => valor.split(/\s+/))
  .filter(Boolean);
const orfaos = descritos.filter((id) => !ids.has(id));
verificar(
  `todo aria-describedby aponta para um id existente — ${descritos.length} referências`,
  orfaos.length === 0,
  orfaos.join(', '),
);

const invalidos = [...html.matchAll(/aria-invalid="true"[^>]*aria-describedby="([^"]*)"/g)].map((m) => m[1]);
const invalidosComErro = invalidos.filter((valor) => valor.split(/\s+/).some((id) => id.endsWith('-erro')));
verificar(
  `todo controle inválido aponta para a sua mensagem de erro — ${invalidos.length} controles`,
  invalidos.length > 0 && invalidosComErro.length === invalidos.length,
  `${invalidos.length - invalidosComErro.length} sem referência à mensagem`,
);

const semTextoAlternativo = [...html.matchAll(/<i class="sc-(spin|chip__dot|badge--pulse)[^"]*"([^>]*)>/g)]
  .filter((m) => !m[2].includes('aria-hidden'))
  .map((m) => m[1]);
verificar(
  'indicador decorativo é aria-hidden (não vira ruído no leitor de tela)',
  semTextoAlternativo.length === 0,
  semTextoAlternativo.join(', '),
);

console.log('\nprimitivas do critério 3:');
for (const [nome, marcador] of PRIMITIVAS) {
  verificar(`  ${nome}`, html.includes(marcador), `marcador ausente: ${marcador}`);
}

console.log(
  `\nresumo: ${ESTADOS_PAGINA.length + ESTADOS_FORMULARIO.length} estados exigidos · ` +
    `${PRIMITIVAS.length} primitivas · ${falhas === 0 ? 'todos presentes' : `${falhas} ausentes`}`,
);
process.exit(falhas === 0 ? 0 : 1);
