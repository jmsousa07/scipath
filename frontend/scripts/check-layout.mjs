#!/usr/bin/env node
/**
 * Verificação de layout do card E1.6, critério 3: "layout sem rolagem horizontal em
 * 375 px e 1440 px (checagem por dimensões de container)". A conferência VISUAL é do
 * card de QA; aqui a checagem é estática e reprodutível, sobre o CSS que o projeto
 * declara. Não substitui o olho humano — reduz o espaço onde ele precisa olhar.
 *
 * Regras:
 *   1. largura fixa (`width` / `min-width` em px) maior que o viewport de 375 px só é
 *      aceitável dentro de um media query com `min-width` >= 376 px;
 *   2. `grid-template-columns` com `fr` "cru" (sem `minmax(0,…)`) tem min-content
 *      automático e pode estourar: proibido na camada de projeto; se vier do DS2,
 *      precisa existir override do mesmo seletor em `layout.css`;
 *   3. `overflow-x: hidden` é proibido — esconder o overflow esconde o defeito;
 *   4. matemática de container em 375 px e 1440 px: resolve o cascade para os
 *      seletores de largura da casca e reporta a largura útil de conteúdo.
 *
 * Uso: node scripts/check-layout.mjs
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const frontendDir = path.resolve(here, '..');
const SRC = path.join(frontendDir, 'src');

const VIEWPORTS = [375, 1440];
const MOBILE_MIN_CONTENT = 320; // 375 - padding confortável dos dois lados
const ROOT_FONT_PX = 16;

const cssFiles = [
  'styles/tokens.css',
  'styles/fonts.css',
  'styles/base.css',
  'styles/design-system.css',
  'styles/layout.css',
  'app/globals.css',
];

const read = (relPath) => fs.readFileSync(path.join(SRC, relPath), 'utf8');

/** Mantém os números de linha: comentário vira espaço, quebra de linha permanece. */
const stripComments = (text) => text.replace(/\/\*[\s\S]*?\*\//g, (m) => m.replace(/[^\n]/g, ' '));

function matchingBrace(text, openIndex) {
  let depth = 0;
  for (let i = openIndex; i < text.length; i += 1) {
    if (text[i] === '{') depth += 1;
    else if (text[i] === '}') {
      depth -= 1;
      if (depth === 0) return i;
    }
  }
  return text.length - 1;
}

/** Parser mínimo: regras com a pilha de contextos (@media) onde vivem. */
function parseCss(text, file) {
  const clean = stripComments(text);
  const lineAt = (index) => clean.slice(0, index).split('\n').length;
  const rules = [];
  const contexts = [];
  let preludeStart = 0;
  let i = 0;

  while (i < clean.length) {
    const ch = clean[i];
    if (ch === '}') {
      contexts.pop();
      i += 1;
      preludeStart = i;
      continue;
    }
    if (ch === ';') {
      i += 1;
      preludeStart = i;
      continue;
    }
    if (ch === '{') {
      const prelude = clean.slice(preludeStart, i).trim().replace(/\s+/g, ' ');
      if (/^@(media|supports|container)\b/.test(prelude)) {
        contexts.push(prelude);
        i += 1;
        preludeStart = i;
        continue;
      }
      const end = matchingBrace(clean, i);
      if (!prelude.startsWith('@')) {
        rules.push({
          file,
          selector: prelude,
          body: clean.slice(i + 1, end),
          line: lineAt(preludeStart),
          contexts: [...contexts],
        });
      }
      i = end + 1;
      preludeStart = i;
      continue;
    }
    i += 1;
  }
  return rules;
}

const declarations = (body) =>
  body
    .split(';')
    .map((part) => part.trim())
    .filter(Boolean)
    .map((part) => {
      const at = part.indexOf(':');
      return at === -1 ? null : { prop: part.slice(0, at).trim(), value: part.slice(at + 1).trim() };
    })
    .filter(Boolean);

const allRules = cssFiles.flatMap((file) => parseCss(read(file), file));

const failures = [];
const notes = [];

// --- 1. largura fixa maior que o viewport ----------------------------------
const toPx = (value) => {
  const match = value.match(/^(-?[\d.]+)(px|rem)$/);
  if (!match) return null;
  return Number(match[1]) * (match[2] === 'rem' ? ROOT_FONT_PX : 1);
};

const minWidthOf = (context) => {
  const match = context.match(/min-width:\s*([\d.]+)(px|rem)/);
  return match ? toPx(`${match[1]}${match[2]}`) : null;
};

let fixedCount = 0;
for (const rule of allRules) {
  for (const { prop, value } of declarations(rule.body)) {
    if (prop !== 'width' && prop !== 'min-width') continue;
    const px = toPx(value);
    if (px === null) continue;
    fixedCount += 1;
    if (px <= 375) continue;
    const guard = rule.contexts.map(minWidthOf).find((min) => min !== null && min >= 376);
    if (guard === undefined) {
      failures.push(
        `[1] ${rule.file}:${rule.line}  ${prop}:${value} em "${rule.selector}" — largura fixa maior ` +
          `que o viewport de 375 px sem media query de proteção`,
      );
    }
  }
}

// --- 2. `fr` cru em grid-template-columns ----------------------------------
const bareFr = (value) => {
  const withoutMinmax = value.replace(/minmax\([^)]*\)/g, '');
  return /\d*\.?\d*fr\b/.test(withoutMinmax);
};

const overridesInLayout = new Map();
for (const rule of allRules) {
  if (rule.file !== 'styles/layout.css') continue;
  for (const { prop, value } of declarations(rule.body)) {
    if (prop !== 'grid-template-columns') continue;
    for (const selector of rule.selector.split(',').map((s) => s.trim())) {
      overridesInLayout.set(selector, value.includes('minmax('));
    }
  }
}

for (const rule of allRules) {
  for (const { prop, value } of declarations(rule.body)) {
    if (prop !== 'grid-template-columns' || !bareFr(value)) continue;
    if (rule.file === 'styles/layout.css') {
      failures.push(`[2] ${rule.file}:${rule.line}  ${prop}:${value} em "${rule.selector}" — \`fr\` cru na camada de projeto`);
      continue;
    }
    const selectors = rule.selector.split(',').map((s) => s.trim());
    const guarded = selectors.every((selector) => overridesInLayout.get(selector) === true);
    if (!guarded) {
      failures.push(
        `[2] ${rule.file}:${rule.line}  ${prop}:${value} em "${rule.selector}" — veio do DS2 com \`fr\` cru ` +
          `e não há override com minmax(0,…) em layout.css`,
      );
    } else {
      notes.push(`[2] ${rule.file}:${rule.line} \`${rule.selector}\` tem \`fr\` cru no DS2, coberto por override em layout.css`);
    }
  }
}

// --- 3. overflow-x: hidden -------------------------------------------------
const overflowHits = [];
for (const rule of allRules) {
  for (const { prop, value } of declarations(rule.body)) {
    if (prop === 'overflow-x' && /hidden|clip/.test(value)) {
      overflowHits.push(`[3] ${rule.file}:${rule.line}  ${prop}:${value} em "${rule.selector}"`);
    }
  }
}
if (overflowHits.length > 0) failures.push(...overflowHits);

// --- 4. matemática de container -------------------------------------------
function contextApplies(context, viewport) {
  const conditions = context.match(/\(([^)]+)\)/g) ?? [];
  return conditions.every((raw) => {
    const cond = raw.slice(1, -1);
    const match = cond.match(/(min|max)-width:\s*([\d.]+)(px|rem)/);
    if (!match) return true;
    const px = toPx(`${match[2]}${match[3]}`);
    return match[1] === 'min' ? viewport >= px : viewport <= px;
  });
}

function resolve(selector, viewport, properties) {
  const resolved = {};
  for (const rule of allRules) {
    if (!rule.selector.split(',').map((s) => s.trim()).includes(selector)) continue;
    if (!rule.contexts.every((context) => contextApplies(context, viewport))) continue;
    for (const { prop, value } of declarations(rule.body)) {
      if (!properties.includes(prop)) continue;
      resolved[prop] = { value, file: rule.file, line: rule.line };
    }
  }
  return resolved;
}

function horizontalPadding(resolved) {
  if (resolved['padding-left'] || resolved['padding-right']) {
    return [resolved['padding-left']?.value ?? '0', resolved['padding-right']?.value ?? '0'];
  }
  if (!resolved.padding) return ['0', '0'];
  const parts = resolved.padding.value.split(/\s+/);
  if (parts.length === 1) return [parts[0], parts[0]];
  if (parts.length === 2) return [parts[0], parts[1]];
  if (parts.length === 3) return [parts[1], parts[1]];
  return [parts[3], parts[1]];
}

const containerReport = [];
const CONTAINERS = ['.sc-page', '.sc-header', '.sc-footer'];
for (const viewport of VIEWPORTS) {
  for (const selector of CONTAINERS) {
    const resolved = resolve(selector, viewport, [
      'padding',
      'padding-left',
      'padding-right',
      'grid-template-columns',
    ]);
    const [left, right] = horizontalPadding(resolved);
    const leftPx = toPx(left) ?? 0;
    const rightPx = toPx(right) ?? 0;
    const content = viewport - leftPx - rightPx;
    const source = resolved.padding ? `${resolved.padding.file}:${resolved.padding.line}` : 'padrão 0';
    containerReport.push(
      `    ${String(viewport).padStart(4)} px · ${selector.padEnd(11)} padding ${left} / ${right}` +
        ` → conteúdo ${content} px (${source})`,
    );
    if (content < MOBILE_MIN_CONTENT) {
      failures.push(
        `[4] ${selector} em ${viewport} px sobra ${content} px de conteúdo (mínimo aceitável ${MOBILE_MIN_CONTENT} px)`,
      );
    }
  }
}

// --- relatório -------------------------------------------------------------
console.log('== check:layout — rolagem horizontal e container ==');
console.log(
  `regras analisadas: ${allRules.length} · larguras fixas em px: ${fixedCount} (todas <= 375 px, exceto as reportadas)`,
);
console.log('matemática de container:');
console.log(containerReport.join('\n'));
if (notes.length > 0) {
  console.log('\navisos (não bloqueiam):');
  console.log(notes.map((n) => `    ${n}`).join('\n'));
}
if (failures.length > 0) {
  console.log(`\nFALHA (${failures.length}):`);
  console.log(failures.map((f) => `    ${f}`).join('\n'));
  process.exit(1);
}
console.log('\nPASS  1) largura fixa > 375 px só sob media query de proteção');
console.log('PASS  2) nenhum `fr` cru sem minmax(0,…)');
console.log('PASS  3) nenhum `overflow-x: hidden` escondendo overflow');
console.log('PASS  4) conteúdo >= 320 px em 375 px e 1440 px');