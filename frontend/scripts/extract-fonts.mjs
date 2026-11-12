#!/usr/bin/env node
/**
 * Copia o subconjunto LATINO dos arquivos `.woff2` dos templates locais para
 * `public/fonts/` e gera `src/styles/fonts.css` com `@font-face` próprio.
 *
 * Escopo (card E1.6, item 3): fontes locais, sem CDN. As famílias carregadas são
 * exatamente as das pilhas de DESIGN_SYSTEM.md §1: Google Sans Display, Google Sans Flex,
 * Google Sans, Google Sans Text e Roboto Mono.
 * Ficam de fora:
 *   - 'Product Sans' — não aparece em nenhuma pilha do contrato;
 *   - 'Google Symbols' — só existe o subset de ícones (sem intervalo latino) e o card
 *     não usa fonte de ícone (ícones são SVG inline; `--font-symbol` segue no token).
 *
 * Também extrai o texto de `unicode-range` do CSS de origem, para não redigitar.
 * Uso: node scripts/extract-fonts.mjs [--check]
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const frontendDir = path.resolve(here, '..');
const repoRoot = path.resolve(frontendDir, '..');

const SRC_DIR = path.join(repoRoot, 'assets/fonts/google-sans/source');
const OUT_FONTS_DIR = path.join(frontendDir, 'public/fonts');
const OUT_CSS = path.join(frontendDir, 'src/styles/fonts.css');

const WANTED = new Set([
  'Google Sans Display',
  'Google Sans Flex',
  'Google Sans',
  'Google Sans Text',
  'Roboto Mono',
]);
const LATIN_MARKER = 'U+0000-00FF';

const slug = (value) =>
  value
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/(^-|-$)/g, '');

const cssFiles = fs
  .readdirSync(SRC_DIR)
  .filter((name) => /^css2_.*\.css$/.test(name))
  .sort();

const faces = [];
for (const cssFile of cssFiles) {
  const css = fs.readFileSync(path.join(SRC_DIR, cssFile), 'utf8');
  for (const match of css.matchAll(/@font-face\s*\{([\s\S]*?)\}/g)) {
    const block = match[1];
    const pick = (re) => {
      const found = block.match(re);
      return found ? found[1].trim() : null;
    };
    const family = pick(/font-family:\s*'([^']+)'/);
    const style = pick(/font-style:\s*([^;]+);/);
    const weight = pick(/font-weight:\s*([^;]+);/);
    const src = pick(/url\("([^"]+)"\)/);
    const unicodeRange = pick(/unicode-range:\s*([^;]+);/);
    if (!family || !src) continue;
    if (!WANTED.has(family)) continue;
    if (!unicodeRange || !unicodeRange.includes(LATIN_MARKER)) continue;
    faces.push({ cssFile, family, style, weight, src, unicodeRange });
  }
}

if (faces.length === 0) {
  console.error('nenhum @font-face latino encontrado — o caminho dos assets mudou?');
  process.exit(1);
}

// Um mesmo arquivo pode servir a vários pesos (fontes variáveis): copia uma vez só.
const bySrc = new Map();
for (const face of faces) {
  if (bySrc.has(face.src)) continue;
  const hash = (face.src.match(/_([0-9a-f]{12})\.woff2$/) || [, 'semhash'])[1].slice(0, 8);
  bySrc.set(face.src, `${slug(face.family)}-${hash}.woff2`);
}

const blocks = faces.map((face) => {
  const fileName = bySrc.get(face.src);
  const weight = face.weight === '1 1000' ? '400 1000' : face.weight;
  return [
    '/* ' + face.family + ' · ' + face.style + ' · ' + face.weight + ' (' + face.cssFile + ') */',
    '@font-face {',
    `  font-family: '${face.family}';`,
    `  font-style: ${face.style};`,
    `  font-weight: ${weight};`,
    '  font-display: swap;',
    `  src: url('/fonts/${fileName}') format('woff2');`,
    `  unicode-range: ${face.unicodeRange};`,
    '}',
  ].join('\n');
});

const totalBytes = [...bySrc.keys()].reduce(
  (sum, src) => sum + fs.statSync(path.join(SRC_DIR, src)).size,
  0,
);

const generated = `/* ============================================================================
   fonts.css — @font-face LOCAL (sem CDN de fontes)
   ----------------------------------------------------------------------------
   ARQUIVO GERADO. Não edite à mão: rode \`npm run fonts\` (frontend/).
   Gerador: frontend/scripts/extract-fonts.mjs
   Origem:  assets/fonts/google-sans/source/css2_*.css (subset de fontes licenciado)
   Escopo:  subconjunto LATINO (unicode-range com ${LATIN_MARKER}) de
            ${[...new Set(faces.map((f) => f.family))].sort().join(', ')}.
   Volume:  ${bySrc.size} arquivos .woff2, ${(totalBytes / 1024).toFixed(0)} KB, ${faces.length} declarações @font-face.
   Todas com \`font-display: swap\`; as pilhas de fallback vivem em tokens.css
   (DESIGN_SYSTEM.md §1).
   ============================================================================ */

${blocks.join('\n\n')}
`;

if (process.argv.includes('--check')) {
  const current = fs.existsSync(OUT_CSS) ? fs.readFileSync(OUT_CSS, 'utf8') : '';
  const missing = [...bySrc.values()].filter(
    (name) => !fs.existsSync(path.join(OUT_FONTS_DIR, name)),
  );
  if (current !== generated || missing.length > 0) {
    console.error('fonts.css/public/fonts desatualizados. Rode: npm run fonts');
    if (missing.length) console.error(`  ausentes: ${missing.join(', ')}`);
    process.exit(1);
  }
  console.log(`fonts.css atualizado (${bySrc.size} arquivos, ${faces.length} @font-face).`);
  process.exit(0);
}

fs.mkdirSync(OUT_FONTS_DIR, { recursive: true });
for (const [src, name] of bySrc) {
  fs.copyFileSync(path.join(SRC_DIR, src), path.join(OUT_FONTS_DIR, name));
}
fs.mkdirSync(path.dirname(OUT_CSS), { recursive: true });
fs.writeFileSync(OUT_CSS, generated);

console.log(`copiados ${bySrc.size} arquivos para public/fonts/ (${(totalBytes / 1024).toFixed(0)} KB)`);
console.log(`gerado src/styles/fonts.css com ${faces.length} declarações @font-face`);
for (const face of faces) {
  console.log(`  ${face.family.padEnd(20)} ${face.style.padEnd(6)} ${face.weight.padEnd(8)} -> ${bySrc.get(face.src)}`);
}