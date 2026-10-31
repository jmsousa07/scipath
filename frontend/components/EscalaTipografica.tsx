/**
 * Escala tipográfica do design system (seção 04 de assets/design_system_2.html).
 * Cada linha usa a classe utilitária derivada do token de escala em tokens.css —
 * nenhum tamanho inventado aqui.
 */

type Papel = {
  nome: string;
  papel: string;
  classe: string;
  amostra: string;
  spec: string[];
};

const PAPEIS: Papel[] = [
  {
    nome: 'h1 · text-h1',
    papel: 'display 500',
    classe: 'font-display font-medium text-h1',
    amostra: 'Conhecimento interconectado',
    spec: ['Google Sans Display', '500 · clamp(44→64px)', 'lh 1.04 · ls −0.035em'],
  },
  {
    nome: 'h2 · text-h2',
    papel: 'display 500',
    classe: 'font-display font-medium text-h2',
    amostra: 'Uma malha de conceitos',
    spec: ['Google Sans Display', '500 · clamp(36→52px)', 'lh 1.06 · ls −0.03em'],
  },
  {
    nome: 'h3 · text-h3',
    papel: 'display 500',
    classe: 'font-display font-medium text-h3',
    amostra: 'Trilha de aprendizagem',
    spec: ['Google Sans Display', '500 · 32px', 'lh 1.12 · ls −0.025em'],
  },
  {
    nome: 'h4 · text-h4',
    papel: 'display 500',
    classe: 'font-display font-medium text-h4',
    amostra: 'Pré-requisitos',
    spec: ['Google Sans Display', '500 · 26px', 'lh 1.16 · ls −0.02em'],
  },
  {
    nome: 'h5 · text-h5',
    papel: 'primary 500',
    classe: 'font-primary font-medium text-h5',
    amostra: 'Curadoria de material',
    spec: ['Google Sans Flex', '500 · 20px', 'lh 1.3 · ls −0.01em'],
  },
  {
    nome: 'h6 · text-h6',
    papel: 'primary 500',
    classe: 'font-primary font-medium text-h6',
    amostra: 'Progresso na trilha',
    spec: ['Google Sans Flex', '500 · 17px', 'lh 1.35 · ls 0'],
  },
  {
    nome: 'body-lg · text-body-lg',
    papel: 'text 400',
    classe: 'font-text text-body-lg',
    amostra: 'Cada material declara os conceitos que cobre e os que exige antes.',
    spec: ['Google Sans Text', '400 · 18px', 'lh 1.65 · ls 0'],
  },
  {
    nome: 'body · text-body',
    papel: 'primary 400',
    classe: 'font-primary text-body',
    amostra: 'O grafo de conhecimento mostra o caminho entre dois conceitos.',
    spec: ['Google Sans Flex', '400 · 16px', 'lh 1.55 · ls 0'],
  },
  {
    nome: 'body-sm · text-body-sm',
    papel: 'text 400',
    classe: 'font-text text-body-sm',
    amostra: 'Nota de aula, slides e artigos convivem na mesma trilha.',
    spec: ['Google Sans Text', '400 · 14px', 'lh 1.55 · ls 0.005em'],
  },
  {
    nome: 'caption · text-caption',
    papel: 'text 400',
    classe: 'font-text text-caption text-sc-ink-muted',
    amostra: 'Publicado por estudante · revisado por pesquisador',
    spec: ['Google Sans Text', '400 · 12.5px', 'lh 1.45 · ls 0.01em'],
  },
  {
    nome: 'label · text-label',
    papel: 'primary 500',
    classe: 'font-primary font-medium text-label uppercase text-sc-ink-muted',
    amostra: 'Área do conhecimento',
    spec: ['Google Sans Flex', '500 · 11px', 'lh 1.2 · ls 0.16em · UPPERCASE'],
  },
  {
    nome: 'helper · text-helper',
    papel: 'text 400',
    classe: 'font-text text-helper text-sc-ink-muted',
    amostra: 'Exige Cálculo I e Álgebra Linear',
    spec: ['Google Sans Text', '400 · 12px', 'lh 1.5 · ls 0.01em'],
  },
  {
    nome: 'code · text-code',
    papel: 'mono 400',
    classe: 'font-mono text-code',
    amostra: 'conceito: derivada_parcial',
    spec: ['Roboto Mono', '400 · 12.5px', 'lh 1.6 · ls 0'],
  },
];

export function EscalaTipografica() {
  return (
    <section aria-labelledby="escala-titulo" className="py-12">
      <span className="font-mono text-label text-sc-ink-muted">01 tipografia</span>
      <h2 id="escala-titulo" className="mt-2 font-display font-medium text-h3">
        Escala completa — h1 → h6 e papéis de texto
      </h2>
      <p className="mt-3 max-w-2xl font-text text-body-sm text-sc-ink-muted">
        A mesma escala da tabela de <em>spec</em> do contrato visual, agora como utilitários do
        Tailwind derivados de <code className="font-mono text-code">tokens.css</code>.
      </p>

      <div className="mt-6">
        {PAPEIS.map((p) => (
          <div
            key={p.nome}
            className="grid grid-cols-1 gap-2 border-b border-sc-line py-5 md:grid-cols-[180px_minmax(0,1fr)_170px] md:items-baseline md:gap-5"
          >
            <div>
              <b className="block font-primary font-medium text-body-sm text-sc-ink">{p.nome}</b>
              <span className="font-mono text-code text-sc-ink-muted">{p.papel}</span>
            </div>
            <div className={`min-w-0 ${p.classe}`}>{p.amostra}</div>
            <div className="font-mono text-code leading-relaxed text-sc-ink-soft md:text-right">
              {p.spec.map((linha) => (
                <span key={linha} className="block">
                  {linha}
                </span>
              ))}
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}
