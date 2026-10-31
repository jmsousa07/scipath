import type { NextConfig } from 'next';

const nextConfig: NextConfig = {
  // O dev server sobe em localhost; acessar por 127.0.0.1 (ex.: captura
  // automatizada, curl) faz o Next tratar a origem como cross-origin e bloquear
  // HMR e fontes internas. Liberar os dois hosts mantém o console limpo.
  allowedDevOrigins: ['127.0.0.1', 'localhost'],

  // O repositório já tem um AGENTS.md na raiz como contrato único de todos os
  // agentes; o bloco que o `next dev` injeta em frontend/AGENTS.md seria ruído
  // regenerado a cada arranque. Desligado de propósito.
  agentRules: false,
};

export default nextConfig;
