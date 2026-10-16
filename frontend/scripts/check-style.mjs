#!/usr/bin/env node
/**
 * Verificação de estilo do card E1.6, critério 2: "nenhum hex/px de cor solto em
 * componente". É um grep com regra explícita — o resultado é evidência colável.
 *
 * A) HEX fora da camada de tokens: nenhum arquivo de `src/` pode declarar cor em hex,
 *    exceto os que existem justamente para guardá-la (`tokens.css` gerado do DS2) e a
 *    camada de componentes extraída do DS2 (`design-system.css`, que traz os literais
 *    do próprio contrato visual — `#fff` em `.sc-footer` é do DS2, não nosso).
 * B) HEX e PX em componente (`src/app/**`, `src/components/**`): zero ocorrências.
 *    Componente usa classe do DS, utilitário do Tailwind ou `var(--token)`.
 * C) `var(--x)` sem definição: toda variável usada precisa existir em `tokens.css`
 *    ou no `@theme` de `globals.css`. Pega erro de digitação de token.
 *
 * Uso: node scripts/check-style.mjs
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const frontendDir = path.resolve(here, '..');
const SRC = path.join(frontendDir, 'src');

/** Camadas que guardam valor de estilo por definição. */
const TOKEN_LAYERS = new Set(['tokens.css', 'fonts.css', 'design-system.css']);

const EXT = new Set(['.ts', '.tsx', '.css']);

function walk(dir) {
  const out = [];
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) out.push(...walk(full));
    else if (EXT.has(path.extname(entry.name))) out.push(full);
  }
  return out;
}

const rel = (file) => path.relative(frontendDir, file);

/**
 * Remove comentários preservando a contagem de linhas. Documentação cita `#fff` e
 * `var(--sc-*)` como exemplo — isso não é valor solto em componente e não pode
 * virar falso positivo.
 */
function stripComments(text, file) {
  let out = text.replace(/\/\*[\s\S]*?\*\//g, (m) => m.replace(/[^\n]/g, ' '));
  if (/\.tsx?$/.test(file)) {
    out = out.replace(/(^|[^:])\/\/[^\n]*/g, (m, prefix) => prefix + ' '.repeat(m.length - prefix.length));
  }
  return out;
}

const files = walk(SRC).map((file) => ({
  file,
  text: stripComments(fs.readFileSync(file, 'utf8'), file),
}));

const HEX = /#[0-9a-fA-F]{3,8}\b/g;
const PX = /\b\d+(?:\.\d+)?px\b/g;
const VAR_USE = /var\(\s*(--[a-zA-Z0-9-]+)/g;
const VAR_DEF = /(--[a-zA-Z0-9-]+)\s*:/g;

const isTokenLayer = ({ file }) => TOKEN_LAYERS.has(path.basename(file));
const isComponent = ({ file }) => /[\\/]src[\\/](app|components)[\\/]/.test(file);

function scan(predicate, pattern) {
  const hits = [];
  for (const entry of files) {
    if (!predicate(entry)) continue;
    const lines = entry.text.split('\n');
    lines.forEach((line, index) => {
      const found = line.match(pattern);
      if (found) hits.push({ file: rel(entry.file), line: index + 1, text: line.trim(), found });
    });
  }
  return hits;
}

function report(title, hits, note) {
  if (hits.length === 0) {
    console.log(`PASS  ${title} (0 ocorrências)`);
    return true;
  }
  console.log(`FALHA ${title} (${hits.length} ocorrências)`);
  for (const hit of hits) {
    console.log(`      ${hit.file}:${hit.line}  ${hit.found.join(', ')}`);
    console.log(`        ${hit.text}`);
  }
  if (note) console.log(`      ${note}`);
  return false;
}

// --- A) hex fora da camada de tokens ---------------------------------------
const hexOutsideTokens = scan((entry) => !isTokenLayer(entry), HEX);

// --- B) hex e px em componente --------------------------------------------
const hexInComponents = scan(isComponent, HEX);
const pxInComponents = scan(isComponent, PX);

// --- C) tokens usados x definidos -----------------------------------------
const defined = new Set();
for (const entry of files) {
  if (path.basename(entry.file) !== 'tokens.css' && path.basename(entry.file) !== 'globals.css') continue;
  for (const match of entry.text.matchAll(VAR_DEF)) defined.add(match[1]);
}
// variáveis criadas por classe utilitária do @theme também contam
const used = new Map();
for (const entry of files) {
  entry.text.split('\n').forEach((line, index) => {
    for (const match of line.matchAll(VAR_USE)) {
      if (defined.has(match[1])) continue;
      const key = match[1];
      if (!used.has(key)) used.set(key, []);
      used.get(key).push(`${rel(entry.file)}:${index + 1}`);
    }
  });
}

console.log('== check:style — valores de estilo fora do lugar ==');
const results = [
  report(
    'A) hex color fora de tokens.css/fonts.css/design-system.css',
    hexOutsideTokens,
    'Cor por token (var(--sc-*)) em vez de literal.',
  ),
  report('B1) hex color em componente (src/app, src/components)', hexInComponents),
  report('B2) valor em px em componente (src/app, src/components)', pxInComponents),
];

if (used.size === 0) {
  console.log('PASS  C) var(--token) sem definição (0 ocorrências)');
} else {
  console.log(`FALHA C) var(--token) sem definição (${used.size} tokens)`);
  for (const [token, where] of used) console.log(`      ${token}  ← ${where.join(', ')}`);
  results.push(false);
}

console.log(
  `\nresumo: ${files.length} arquivos varridos · hex fora de tokens: ${hexOutsideTokens.length} · ` +
    `hex em componente: ${hexInComponents.length} · px em componente: ${pxInComponents.length} · ` +
    `tokens não definidos: ${used.size}`,
);
process.exit(results.every(Boolean) ? 0 : 1);