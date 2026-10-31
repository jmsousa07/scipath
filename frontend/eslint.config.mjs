import nextVitals from 'eslint-config-next/core-web-vitals';

/**
 * ESLint 9 (flat config) — Next.js 16 + TypeScript.
 * O preset `core-web-vitals` já traz o parser TS, as regras de React/Next,
 * `eslint-plugin-import` e `jsx-a11y`. Nada de `.eslintrc` legado.
 */
const config = [
  ...nextVitals,
  {
    ignores: ['node_modules/**', '.next/**', 'out/**', 'next-env.d.ts'],
  },
];

export default config;
