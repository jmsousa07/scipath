#!/usr/bin/env node
/**
 * `make doctor` — acusa Node **fora da faixa que o projeto declara** (card E1.6-f1).
 *
 * A faixa é lida de `frontend/package.json` (`engines.node`): uma só fonte de verdade, sem
 * número repetido aqui. O defeito que este script fecha existia porque a declaração
 * (`>=20.9.0`), a documentação (`docs/rodar-em-outra-maquina.md`) e o que os scripts de
 * verificação de fato exigiam apontavam para lados diferentes — e o `make doctor` só
 * IMPRIMIA a versão, sem conferir nada: um clone novo com Node incompatível só descobria
 * o problema no meio do `npm run verify`, como reprovação silenciosa.
 *
 * Comparador: as formas que um campo `engines.node` de fato usa — `*`/`x`, `^`, `~`,
 * operadores (`>=`, `>`, `<=`, `<`, `=`), junção por espaço (E) e por `||` (OU). Faixa que
 * este script não souber interpretar é tratada como FALHA (exit 1), nunca como aprovação
 * silenciosa — o instrumento prefere acusar demais a aprovar de menos.
 *
 * Uso: node infra/check-node-version.mjs   (ou: make doctor)
 */
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const aqui = path.dirname(fileURLToPath(import.meta.url));
const packageJson = path.resolve(aqui, '..', 'frontend', 'package.json');

const FAIXA_NAO_RECONHECIDA = 2;

const declarada = JSON.parse(readFileSync(packageJson, 'utf8')).engines?.node;
if (typeof declarada !== 'string' || declarada.trim() === '') {
  console.error(`FALHA frontend/package.json não declara engines.node (esperado a faixa suportada).`);
  process.exit(FAIXA_NAO_RECONHECIDA);
}

/** `20.9.0` / `v20.9.0` → [20, 9, 0]; `20.9` → [20, 9, 0]; `20` → [20, 0, 0]. */
function tripla(bruto) {
  const numeros = String(bruto).replace(/^v/, '').split('.').map((n) => Number.parseInt(n, 10));
  if (numeros.length === 0 || numeros.some(Number.isNaN)) return undefined;
  while (numeros.length < 3) numeros.push(0);
  return numeros.slice(0, 3);
}

function comparar(a, b) {
  for (let i = 0; i < 3; i += 1) {
    if (a[i] !== b[i]) return a[i] < b[i] ? -1 : 1;
  }
  return 0;
}

const OPERADORES = ['>=', '<=', '>', '<', '='];

/** Uma cláusula do tipo `>=20.9.0` → predicado. `undefined` = não reconhecida. */
function predicado(clausula) {
  const texto = clausula.trim();
  if (texto === '' || texto === '*' || texto === 'x') return () => true;

  const operador = OPERADORES.find((o) => texto.startsWith(o));
  if (operador) {
    const alvo = tripla(texto.slice(operador.length).trim());
    if (!alvo) return undefined;
    if (operador === '>=') return (v) => comparar(v, alvo) >= 0;
    if (operador === '<=') return (v) => comparar(v, alvo) <= 0;
    if (operador === '>') return (v) => comparar(v, alvo) > 0;
    if (operador === '<') return (v) => comparar(v, alvo) < 0;
    return (v) => comparar(v, alvo) === 0;
  }

  const curinga = texto.startsWith('^');
  const aproximado = texto.startsWith('~');
  if (curinga || aproximado) {
    const piso = tripla(texto.slice(1));
    if (!piso) return undefined;
    // `^1.2.3` → [1.2.3, 2.0.0); `^0.2.3` → [0.2.3, 0.3.0); `^0.0.3` → [0.0.3, 0.0.4)
    // `~1.2.3` → [1.2.3, 1.3.0); `~1.2` → [1.2.0, 1.3.0); `~1` → [1.0.0, 2.0.0)
    let teto;
    if (aproximado) {
      teto = piso[0] === 0 && texto.split('.').length < 2 ? [1, 0, 0] : [piso[0], piso[1] + 1, 0];
    } else if (piso[0] > 0) teto = [piso[0] + 1, 0, 0];
    else if (piso[1] > 0) teto = [0, piso[1] + 1, 0];
    else teto = [0, 0, piso[2] + 1];
    return (v) => comparar(v, piso) >= 0 && comparar(v, teto) < 0;
  }

  const exata = tripla(texto);
  if (!exata) return undefined;
  return (v) => comparar(v, exata) === 0;
}

/** Faixa completa (AND por espaço, OR por `||`). `undefined` = não reconhecida. */
function aceita(faixa) {
  const alternativas = faixa.split('||');
  const conjuncoes = [];
  for (const alternativa of alternativas) {
    const clausulas = alternativa.trim().split(/\s+/).filter(Boolean);
    if (clausulas.length === 0) continue;
    const predicados = clausulas.map(predicado);
    if (predicados.some((p) => p === undefined)) return undefined;
    conjuncoes.push(predicados);
  }
  if (conjuncoes.length === 0) return undefined;
  return (v) => conjuncoes.some((predicados) => predicados.every((p) => p(v)));
}

const encontrada = tripla(process.versions.node);
const satisfaz = aceita(declarada);

console.log(`Node ${process.version} · faixa declarada em frontend/package.json: engines.node "${declarada}"`);

if (satisfaz === undefined) {
  console.error(`FALHA não sei interpretar a faixa declarada ("${declarada}").`);
  console.error('      Ajuste este comparador ou a declaração — e deixe as duas concordando.');
  process.exit(FAIXA_NAO_RECONHECIDA);
}

if (satisfaz(encontrada)) {
  console.log(`Node: DENTRO da faixa declarada (${process.version} satisfaz "${declarada}")`);
  process.exit(0);
}

console.error(`Node: FORA DA FAIXA — ${process.version} NÃO satisfaz "${declarada}".`);
console.error('      Instale um Node na faixa declarada antes de rodar o gate:');
console.error('      os scripts de verificação (npm run verify) reprovam por runtime fora da faixa.');
process.exit(1);