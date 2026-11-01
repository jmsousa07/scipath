/**
 * Registra o carregador de `.ts` (`scripts/ts-loader.mjs`) no processo do Node — card E1.6-f1.
 *
 * Uso: `node --import ./scripts/ts-registrar.mjs scripts/<bateria>.mjs`
 * (é o que os scripts `check:erros-sessao`, `check:perfil-erros` e `check:publicar-erros`
 * do `package.json` fazem).
 *
 * `--import` e `module.register` existem desde o Node 20.6; o `engines.node` do projeto é
 * `>=20.9.0`, então o mecanismo cabe na faixa declarada. Se algum Node mais antigo for
 * usado, a falha é explícita e nomeada — não reprovação silenciosa.
 */
import { register } from 'node:module';

if (typeof register !== 'function') {
  console.error(
    `FALHA este Node (${process.version}) não tem \`module.register\` (existe desde o 20.6).`,
  );
  console.error('      Confira a faixa declarada em frontend/package.json (engines.node) — ou `make doctor`.');
  process.exit(1);
}

register('./ts-loader.mjs', import.meta.url);