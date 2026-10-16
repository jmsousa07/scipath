#!/usr/bin/env node
/**
 * Gera `src/styles/tokens.css` a partir do contrato visual executável
 * `assets/design_system_2.html` (fonte primária — DESIGN_SYSTEM.md §0).
 *
 * Regra do card E1.6: os valores devem ser EXTRAÍDOS do arquivo, nunca redigitados.
 * Este script lê o `<style>` do DS2 e emite:
 *   1. o bloco `:root` (paleta --glue-*, tokens semânticos --sc-*, raios, motion, sombras, fontes);
 *   2. a escala tipográfica real (.sc-h1 … .sc-h6, .sc-body*, .sc-caption, .sc-label, .sc-mono).
 *
 * O arquivo gerado NÃO deve ser editado à mão: rode `npm run tokens`.
 * Uso: node scripts/extract-tokens.mjs [--check]   (--check falha se o gerado estiver desatualizado)
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const frontendDir = path.resolve(here, '..');
const repoRoot = path.resolve(frontendDir, '..');

const SOURCE_REL = 'assets/design_system_2.html';
const OUT_REL = 'src/styles/tokens.css';
const SOURCE = path.join(repoRoot, SOURCE_REL);
const OUT = path.join(frontendDir, OUT_REL);

const html = fs.readFileSync(SOURCE, 'utf8');

/** Extrai o primeiro bloco `:root { … }` do arquivo. */
function extractRootBlock(source, sourceLabel) {
  const start = source.indexOf(':root {');
  if (start === -1) throw new Error(`:root não encontrado em ${sourceLabel}`);
  const end = source.indexOf('\n}', start);
  if (end === -1) throw new Error(`fim do :root não encontrado em ${sourceLabel}`);
  const decls = source
    .slice(start + ':root {'.length, end)
    .split('\n')
    .map((line) => line.replace(/\s+$/, ''))
    .filter((line) => line.trim() !== '');
  return decls;
}

function lineOf(source, index) {
  return source.slice(0, index).split('\n').length;
}

/** Extrai uma regra CSS de seletor simples, preservando o texto original. */
function extractRule(source, selector) {
  const re = new RegExp(`^[\\t ]*\\${selector}\\s*\\{[^}]*\\}`, 'm');
  const match = source.match(re);
  if (!match) throw new Error(`regra ${selector} não encontrada em ${SOURCE_REL}`);
  return { text: match[0].trim(), line: lineOf(source, match.index) };
}

const TYPE_SCALE = [
  '.sc-h1',
  '.sc-h2',
  '.sc-h3',
  '.sc-h4',
  '.sc-h5',
  '.sc-h6',
  '.sc-body-lg',
  '.sc-body',
  '.sc-body-sm',
  '.sc-caption',
  '.sc-label',
  '.sc-mono',
];

const rootDecls = extractRootBlock(html, SOURCE_REL);
const scale = TYPE_SCALE.map((selector) => ({ selector, ...extractRule(html, selector) }));

const header = `/* ============================================================================
   tokens.css — CONTRATO VISUAL DO SCIPATH (camada de tokens)
   ----------------------------------------------------------------------------
   ARQUIVO GERADO. Não edite à mão: rode \`npm run tokens\` (frontend/).
   Fonte primária: ${SOURCE_REL}
   Gerador:       frontend/scripts/extract-tokens.mjs
   Regra:         DESIGN_SYSTEM.md §0.1 — nenhum valor mágico em componente.
                  Todo hex, raio, sombra, easing e tamanho de tipo vive AQUI.
   ============================================================================ */

/* ---------------------------------------------------------------------------
   1. Tokens (bloco :root extraído de ${SOURCE_REL})
   --------------------------------------------------------------------------- */
:root {`;

const rootBody = rootDecls.map((line) => `  ${line.trim()}`).join('\n');

const scaleComment = scale
  .map(({ selector, line }) => `     ${selector.padEnd(12)} ${SOURCE_REL}:${line}`)
  .join('\n');

const scaleCss = scale.map(({ text }) => `  ${text}`).join('\n');

const generated = `${header}
${rootBody}
}

/* ---------------------------------------------------------------------------
   2. Escala tipográfica (regras extraídas verbatim do DS2)
${scaleComment}
   --------------------------------------------------------------------------- */
${scaleCss}

/* ---------------------------------------------------------------------------
   3. Papéis ausentes no DS2 — pendência declarada em DESIGN_SYSTEM.md §3/§7.2
   \`.sc-h5\`, \`.sc-h6\`, \`.sc-label\` e \`.sc-mono\` TÊM definição no DS2 (extraídas
   acima) e por isso não são redefinidas aqui.
   --------------------------------------------------------------------------- */
`;

if (process.argv.includes('--check')) {
  const current = fs.existsSync(OUT) ? fs.readFileSync(OUT, 'utf8') : '';
  if (current !== generated) {
    console.error(`tokens.css desatualizado em relação a ${SOURCE_REL}. Rode: npm run tokens`);
    process.exit(1);
  }
  console.log(`tokens.css atualizado (${rootDecls.length} declarações :root, ${scale.length} classes de tipo).`);
  process.exit(0);
}

fs.mkdirSync(path.dirname(OUT), { recursive: true });
fs.writeFileSync(OUT, generated);
console.log(`gerado ${OUT_REL}`);
console.log(`  :root            ${rootDecls.length} declarações`);
console.log(`  escala de tipo   ${scale.length} classes`);