/**
 * Paleta do design system (seção 02 de assets/design_system_2.html).
 * O valor de cada amostra vem de var(--token) — o hex mora só em tokens.css.
 */

type Amostra = {
  token: string;
  papel: string;
};

const SEMANTICAS: Amostra[] = [
  { token: '--sc-blue', papel: 'primary — nó de conhecimento' },
  { token: '--sc-red', papel: 'accent — pré-requisito / alerta' },
  { token: '--sc-yellow', papel: 'accent 2 — curadoria / destaque' },
  { token: '--sc-green', papel: 'feedback — concluído' },
  { token: '--sc-purple', papel: 'decorative — trilhas' },
  { token: '--sc-ink', papel: 'texto principal' },
  { token: '--sc-ink-soft', papel: 'texto secundário / lead' },
  { token: '--sc-ink-muted', papel: 'texto muted / caption' },
  { token: '--sc-line', papel: 'borda / divisor' },
  { token: '--sc-line-strong', papel: 'borda forte / input' },
  { token: '--sc-surface', papel: 'canvas / card' },
  { token: '--sc-surface-2', papel: 'surface sutil / zebra' },
  { token: '--sc-surface-3', papel: 'hover de fundo / track' },
];

const GLUE: Amostra[] = [
  ...['0', '10', '20', '50', '100', '200', '300', '400', '500', '600', '700', '800', '900', '1000', '1100', '1200'].map(
    (n) => ({ token: `--glue-grey-${n}`, papel: 'neutral' }),
  ),
  ...['50', '100', '300', '500', '600', '700', '800', '900'].map((n) => ({
    token: `--glue-blue-${n}`,
    papel: 'blue',
  })),
  ...['100', '300', '500', '700', '900'].map((n) => ({ token: `--glue-green-${n}`, papel: 'green' })),
  { token: '--glue-yellow-100', papel: 'yellow' },
  ...['50', '100', '300', '500', '700', '900'].map((n) => ({ token: `--glue-red-${n}`, papel: 'red' })),
  ...['100', '900'].map((n) => ({ token: `--glue-purple-${n}`, papel: 'purple' })),
];

function Grade({ amostras }: { amostras: Amostra[] }) {
  return (
    <ul className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
      {amostras.map((a) => (
        <li
          key={a.token}
          className="overflow-hidden rounded-sc border border-sc-line bg-sc-surface"
        >
          <div
            className="h-16 border-b border-sc-line"
            style={{ background: `var(${a.token})` }}
            aria-hidden="true"
          />
          <div className="p-2.5">
            <span className="block font-mono text-code break-words text-sc-ink-soft">{a.token}</span>
            <span className="mt-1 block font-text text-helper text-sc-ink-muted">{a.papel}</span>
          </div>
        </li>
      ))}
    </ul>
  );
}

export function Paleta() {
  return (
    <section aria-labelledby="paleta-titulo" className="border-t border-sc-line py-12">
      <span className="font-mono text-label text-sc-ink-muted">02 cores &amp; superfícies</span>
      <h2 id="paleta-titulo" className="mt-2 font-display font-medium text-h3">
        Uma paleta de papel científico
      </h2>
      <p className="mt-3 max-w-2xl font-text text-body-sm text-sc-ink-muted">
        Os hexadecimais existem apenas em <code className="font-mono text-code">tokens.css</code>.
        Cada amostra abaixo pinta com <code className="font-mono text-code">var(--token)</code>.
      </p>

      <h3 className="mt-8 font-primary font-medium text-h5">Tokens semânticos</h3>
      <div className="mt-4">
        <Grade amostras={SEMANTICAS} />
      </div>

      <h3 className="mt-10 font-primary font-medium text-h5">Rampa Glue (Google Research)</h3>
      <div className="mt-4">
        <Grade amostras={GLUE} />
      </div>
    </section>
  );
}
