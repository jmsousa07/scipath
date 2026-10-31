import type { Metadata } from 'next';
import type { ReactNode } from 'react';
import './globals.css';

export const metadata: Metadata = {
  title: 'SciPath — design system',
  description:
    'Rota de demonstração do SciPath: escala tipográfica e paleta do contrato visual (Design System 2.0 "Malha").',
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="pt-BR">
      <body>{children}</body>
    </html>
  );
}
