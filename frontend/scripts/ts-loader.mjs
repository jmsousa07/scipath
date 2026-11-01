/**
 * Carregador ESM que ensina o Node a ler `.ts` — SEM depender do *type stripping* nativo.
 *
 * Por que existe (card E1.6-f1): os scripts de verificação (`check:erros-sessao`,
 * `check:perfil-erros`, `check:publicar-erros`) importam a lógica real de
 * `src/lib/sessao/*.ts`. O Node só remove tipos nativamente quando é COMPILADO com
 * suporte a TypeScript (`process.features.typescript === true`, a partir do 23) — e o
 * pacote `nodejs` do Ubuntu entrega o Node 22 com esse suporte DESLIGADO: sem flag,
 * `ERR_UNKNOWN_FILE_EXTENSION`; com `--experimental-strip-types`, `ERR_NO_TYPESCRIPT`.
 * O resultado, num clone novo, era o `npm run verify` abortar e o `check:states`
 * devolver 0/13 — reprovação silenciosa por versão de runtime.
 *
 * Aqui a transpilação usa o `typescript` que o projeto JÁ tem (devDependency): só apaga
 * os tipos (`transpileModule`), não faz checagem de tipos — quem checa tipos é o
 * `npm run typecheck` (`tsc --noEmit`), não este carregador.
 *
 * Não é dependência nova: `--import` e `module.register` existem desde o Node 20.6, e o
 * `engines.node` do projeto (>=20.9.0, o piso do Next 16) segue valendo.
 */
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import ts from 'typescript';

export async function load(url, context, nextLoad) {
  if (!url.endsWith('.ts')) return nextLoad(url, context);

  const arquivo = fileURLToPath(url);
  const fonte = await readFile(arquivo, 'utf8');
  const { outputText, diagnostics } = ts.transpileModule(fonte, {
    fileName: arquivo,
    reportDiagnostics: true,
    compilerOptions: {
      target: ts.ScriptTarget.ES2022,
      module: ts.ModuleKind.ESNext,
      moduleResolution: ts.ModuleResolutionKind.Bundler,
      isolatedModules: true,
      verbatimModuleSyntax: true,
      sourceMap: false,
      inlineSourceMap: true,
    },
  });

  const erro = (diagnostics ?? []).find((d) => d.category === ts.DiagnosticCategory.Error);
  if (erro) {
    const texto = ts.flattenDiagnosticMessageText(erro.messageText, ' ');
    throw new SyntaxError(`não deu para transpilar ${arquivo}: ${texto}`);
  }

  return { format: 'module', source: outputText, shortCircuit: true };
}