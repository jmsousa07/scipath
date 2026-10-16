import type { NextConfig } from 'next';

/**
 * Sem `next dev` como evidência (AGENTS.md — higiene de recursos): o artefato é o
 * build de produção. `output: 'standalone'` mantém o build reprodutível em máquina
 * pequena e é o que a Etapa 6 (hardening) vai empacotar.
 */
const nextConfig: NextConfig = {
  reactStrictMode: true,
  poweredByHeader: false,
  output: 'standalone',
  typedRoutes: true,
};

export default nextConfig;