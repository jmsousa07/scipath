import type { Metadata } from 'next';
import { SiteFooter } from '@/components/SiteFooter';
import { SiteHeader } from '@/components/SiteHeader';
import { AvisoDeVerificacao } from '@/components/sessao/AvisoDeVerificacao';
import './globals.css';

/**
 * URL canônica do site. Em produção vem de `NEXT_PUBLIC_SITE_URL` (ver `.env.example`);
 * o padrão local evita que o build quebre por variável ausente — nunca "adivinha" um
 * domínio de produção.
 */
const siteUrl = process.env.NEXT_PUBLIC_SITE_URL ?? 'http://localhost:3000';

export const metadata: Metadata = {
  metadataBase: new URL(siteUrl),
  title: {
    default: 'SciPath — plataforma pública de divulgação científica',
    template: '%s · SciPath',
  },
  description:
    'Materiais de estudo e divulgação científica publicados por estudantes, professores e pesquisadores, encontrados por busca e filtros explícitos, com autoria e licença visíveis.',
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="pt-BR">
      <body className="sc-shell">
        <a className="sc-skip-link" href="#conteudo">
          Pular para o conteúdo
        </a>
        <SiteHeader />
        <main className="sc-main" id="conteudo">
          {/*
            Aviso persistente de verificação de e-mail pendente (ADR-0003 (h)): fica no
            layout porque a pendência acompanha a pessoa em todas as páginas e o caminho
            de reenvio não pode depender de ela encontrar a tela certa. Ele não renderiza
            nada quando não há sessão ou quando a conta já está verificada.
          */}
          <AvisoDeVerificacao />
          {children}
        </main>
        <SiteFooter />
      </body>
    </html>
  );
}