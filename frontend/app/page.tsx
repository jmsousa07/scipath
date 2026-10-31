import { EscalaTipografica } from '@/components/EscalaTipografica';
import { Paleta } from '@/components/Paleta';

export default function Home() {
  return (
    <div className="min-h-screen bg-sc-surface">
      <header className="border-b border-sc-line">
        <div className="mx-auto flex max-w-[1200px] flex-wrap items-baseline gap-x-4 gap-y-1 px-4 py-6 sm:px-6">
          <span className="font-display text-h6 font-medium tracking-tight">
            Sci<span className="text-sc-blue">Path</span>
          </span>
          <span className="font-mono text-code text-sc-ink-muted">
            F0.2 · esqueleto do frontend · tokens do design system
          </span>
        </div>
      </header>

      <main className="mx-auto max-w-[1200px] px-4 sm:px-6">
        <section className="py-12" aria-labelledby="abertura">
          <h1 id="abertura" className="max-w-3xl font-display font-medium text-h1">
            Conhecimento científico estruturado e interconectado
          </h1>
          <p className="mt-4 max-w-2xl font-text text-body-lg text-sc-ink-soft">
            Esta rota existe para provar que o contrato visual já está de pé antes de qualquer
            componente de domínio: a tipografia carrega local e a paleta inteira vem de{' '}
            <code className="font-mono text-code">tokens.css</code>. Nada de API, nada de autenticação
            — só os tokens.
          </p>
        </section>

        <EscalaTipografica />
        <Paleta />
      </main>

      <footer className="border-t border-sc-line">
        <div className="mx-auto max-w-[1200px] px-4 py-8 sm:px-6">
          <p className="font-mono text-code text-sc-ink-muted">
            SciPath · design system 2.0 “Malha” · Google Sans Flex / Text / Display + Roboto Mono (locais)
          </p>
        </div>
      </footer>
    </div>
  );
}
