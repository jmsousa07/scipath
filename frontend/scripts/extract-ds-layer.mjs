#!/usr/bin/env node
/**
 * Gera `src/styles/design-system.css`: a camada de componentes do SciPath, copiada
 * VERBATIM de `assets/design_system_2.html` (o contrato visual executável).
 *
 * Por que extrair em vez de transcrever: transcrever convida ao erro e ao valor inventado.
 * O script falha se um seletor listado não existir mais no DS2 — assim a camada não
 * "envelhece em silêncio" quando o contrato visual muda.
 *
 * Fronteira deliberada: este arquivo contém SÓ o que o DS2 define. Regra de projeto
 * (breakpoint, container, adaptação mobile) mora em `src/styles/layout.css`.
 * Uso: node scripts/extract-ds-layer.mjs [--check]
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const frontendDir = path.resolve(here, '..');
const repoRoot = path.resolve(frontendDir, '..');

const SOURCE_REL = 'assets/design_system_2.html';
const OUT_REL = 'src/styles/design-system.css';
const SOURCE = path.join(repoRoot, SOURCE_REL);
const OUT = path.join(frontendDir, OUT_REL);

const html = fs.readFileSync(SOURCE, 'utf8');

// Concatena todos os <style> preservando a posição de cada um no HTML, para que o
// comentário de origem cite a LINHA DO ARQUIVO (e não a linha do CSS concatenado).
let css = '';
const segments = [];
for (const match of html.matchAll(/<style>([\s\S]*?)<\/style>/g)) {
  const start = css.length;
  css += match[1] + '\n';
  segments.push({ start, end: start + match[1].length, htmlStart: match.index + '<style>'.length });
}

const lineOf = (index) => {
  const segment = segments.find((s) => index >= s.start && index < s.end) ?? segments[0];
  const htmlIndex = segment.htmlStart + (index - segment.start);
  return html.slice(0, htmlIndex).split('\n').length;
};
const escapeRe = (text) => text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

/** Extrai um bloco com chaves balanceadas a partir do índice de abertura. */
function balancedFrom(start) {
  let depth = 0;
  for (let i = start; i < css.length; i += 1) {
    if (css[i] === '{') depth += 1;
    if (css[i] === '}') {
      depth -= 1;
      if (depth === 0) return { text: css.slice(start, i + 1), end: i + 1 };
    }
  }
  throw new Error('bloco sem fechamento no CSS de origem');
}

function extractRule(selector) {
  const re = new RegExp(`^[\\t ]*${escapeRe(selector)}\\s*\\{`, 'm');
  const match = re.exec(css);
  if (!match) throw new Error(`seletor não encontrado no DS2: ${selector}`);
  const open = css.indexOf('{', match.index);
  const { text } = balancedFrom(open);
  return { selector, text: `${match[0].replace(/\s*\{$/, '')} ${text}`.trim(), line: lineOf(match.index) };
}

function extractAtRule(head) {
  const re = new RegExp(`^[\\t ]*${escapeRe(head)}\\s*\\{`, 'm');
  const match = re.exec(css);
  if (!match) throw new Error(`regra não encontrada no DS2: ${head}`);
  const open = css.indexOf('{', match.index);
  const { text } = balancedFrom(open);
  return { selector: head, text: `${head} ${text}`.trim(), line: lineOf(match.index) };
}

const GROUPS = [
  {
    title: 'Fundação e fundações de acessibilidade',
    selectors: ['main, .main-content', '.sc-root, .sc-page-root', '.sc-visually-hidden', '.sc-svg-defs'],
  },
  {
    title: 'Estrutura de página (container, seções)',
    selectors: [
      '.sc-main',
      '.sc-page',
      '.sc-page--tight',
      '.sc-page--dark',
      '.sc-page--dark .sc-sec__title, .sc-page--dark h2, .sc-page--dark h3',
      '.sc-page--dark .sc-sec__lead',
      '.sc-sec__head',
      '.sc-sec__num',
      '.sc-sec__num i',
      '.sc-sec__num::after',
      '.sc-sec__title',
      '.sc-sec__title em, .sc-sec__title .sc-grad',
      '.sc-sec__lead',
      '.sc-subhead',
      '.sc-subhead__note',
      '.sc-subhead::before',
    ],
  },
  {
    title: 'Cabeçalho (fixo, com busca)',
    selectors: [
      '.sc-header',
      '.sc-header.is-scrolled',
      '.sc-header__brand',
      '.sc-header__brand svg',
      '.sc-header__word',
      '.sc-header__word i',
      '.sc-header__nav',
      '.sc-header__link',
      '.sc-header__link:hover',
      '.sc-header__link::after',
      '.sc-header__link:hover::after, .sc-header__link[aria-current="true"]::after',
      '.sc-header__spacer',
      '.sc-header__search',
      '.sc-header__kbd',
    ],
  },
  {
    title: 'Busca e campos',
    selectors: [
      '.sc-search',
      '.sc-search input',
      '.sc-search__hint',
      '.sc-field',
      '.sc-field__label',
      '.sc-field__box',
      '.sc-field__box:focus-within',
      '.sc-field input, .sc-field textarea',
      '.sc-field--float',
      '.sc-field--float label',
      '.sc-field--float input:focus + label, .sc-field--float input:not(:placeholder-shown) + label',
      '.sc-field--error .sc-field__box',
      '.sc-field--error .sc-field__msg',
      '.sc-field--ok .sc-field__box',
      '.sc-field__msg',
      '.sc-field--mono input',
    ],
  },
  {
    title: 'Botões',
    selectors: [
      '.sc-btn',
      '.sc-btn:active',
      '.sc-btn::after',
      '.sc-btn:hover::after',
      '.sc-btn--primary',
      '.sc-btn--primary:hover',
      '.sc-btn--blue',
      '.sc-btn--blue:hover',
      '.sc-btn--ghost',
      '.sc-btn--ghost:hover',
      '.sc-btn--text',
      '.sc-btn--text:hover',
      '.sc-btn--sm',
      '.sc-btn--dis',
      '.sc-btn--lg',
    ],
  },
  {
    title: 'Cards e superfícies (E1.7)',
    selectors: [
      '.sc-card',
      '.sc-card--pad',
      '.sc-card--hoverable:hover',
      '.sc-card__eyebrow',
      '.sc-card__title',
      '.sc-card__copy',
      '.sc-helper',
    ],
  },
  {
    title: 'Sinais: badges e chips (E1.7)',
    selectors: [
      '.sc-badges',
      '.sc-badge',
      '.sc-badge--blue',
      '.sc-badge--red',
      '.sc-badge--green',
      '.sc-badge--yellow',
      '.sc-badge--purple',
      '.sc-badge--dark',
      '.sc-badge--pulse i',
      '.sc-chip',
      '.sc-chip__dot',
      '.sc-chip--red .sc-chip__dot',
      '.sc-chip--green .sc-chip__dot',
      '.sc-chip--yellow .sc-chip__dot',
    ],
  },
  {
    title: 'Indicador de atividade em botão (E1.7)',
    selectors: ['.sc-spin'],
  },
  {
    title: 'Rodapé',
    selectors: [
      '.sc-footer',
      '.sc-footer__inner',
      '.sc-footer__brand',
      '.sc-footer__brand svg',
      '.sc-footer__copy',
      '.sc-footer__title',
      '.sc-footer__link',
      '.sc-footer__link:hover',
      '.sc-footer__bottom',
    ],
  },
];

const atRules = [
  { title: 'Movimento: brilho do botão', rule: extractAtRule('@keyframes scSheen') },
  { title: 'Movimento: flutuação dos chips (E1.7)', rule: extractAtRule('@keyframes scFloat') },
  { title: 'Movimento: pulso do badge (E1.7)', rule: extractAtRule('@keyframes scPulse') },
  { title: 'Movimento: giro do indicador de atividade (E1.7)', rule: extractAtRule('@keyframes scSpin') },
  {
    title: 'Movimento: preferência do usuário (RNF005)',
    rule: extractAtRule('@media (prefers-reduced-motion: reduce)'),
  },
];

const extracted = GROUPS.map((group) => ({
  ...group,
  rules: group.selectors.map(extractRule),
}));

const body = [
  ...extracted.map(
    (group) =>
      `/* ---------------------------------------------------------------------------\n   ${group.title}\n   --------------------------------------------------------------------------- */\n${group.rules
        .map((r) => `${r.text}\n/* ${SOURCE_REL}:${r.line} */`)
        .join('\n')}`,
  ),
  ...atRules.map(
    (item) =>
      `/* ---------------------------------------------------------------------------\n   ${item.title}\n   --------------------------------------------------------------------------- */\n${item.rule.text}\n/* ${SOURCE_REL}:${item.rule.line} */`,
  ),
].join('\n\n');

const generated = `/* ============================================================================
   design-system.css — CAMADA DE COMPONENTES DO CONTRATO VISUAL
   ----------------------------------------------------------------------------
   ARQUIVO GERADO. Não edite à mão: rode \`npm run tokens\` (frontend/).
   Gerador:  frontend/scripts/extract-ds-layer.mjs
   Origem:   ${SOURCE_REL} (extração verbatim; cada regra cita a linha de origem)
   Conteúdo: apenas o que o DS2 define. As regras de PROJETO (breakpoints, container,
             adaptação para 375 px) vivem em src/styles/layout.css.
   Valores:  nenhum valor é inventado aqui — se o DS2 mudar, rode o gerador
             (\`npm run tokens\`) e o diff aparece no git.
   ============================================================================ */

${body}
`;

if (process.argv.includes('--check')) {
  const current = fs.existsSync(OUT) ? fs.readFileSync(OUT, 'utf8') : '';
  if (current !== generated) {
    console.error(`design-system.css desatualizado em relação a ${SOURCE_REL}. Rode: npm run tokens`);
    process.exit(1);
  }
  const total = extracted.reduce((sum, g) => sum + g.rules.length, 0) + atRules.length;
  console.log(`design-system.css atualizado (${total} regras extraídas do DS2).`);
  process.exit(0);
}

fs.mkdirSync(path.dirname(OUT), { recursive: true });
fs.writeFileSync(OUT, generated);
const total = extracted.reduce((sum, g) => sum + g.rules.length, 0) + atRules.length;
console.log(`gerado ${OUT_REL}: ${total} regras do DS2`);
for (const group of extracted) {
  console.log(`  ${group.title.padEnd(42)} ${group.rules.length} regras`);
}
for (const item of atRules) console.log(`  ${item.title.padEnd(42)} 1 regra`);