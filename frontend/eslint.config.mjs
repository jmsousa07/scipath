import coreWebVitals from 'eslint-config-next/core-web-vitals';
import nextTypescript from 'eslint-config-next/typescript';

/**
 * Lint do frontend. `next lint` foi removido no Next 16, então `npm run lint` chama o
 * ESLint direto — e `eslint-config-next` 16 já publica flat config (`./core-web-vitals`
 * e `./typescript`), sem precisar de `FlatCompat`.
 */
const config = [
  {
    ignores: ['.next/**', 'node_modules/**', 'next-env.d.ts', 'public/**'],
  },
  ...coreWebVitals,
  ...nextTypescript,
];

export default config;