/**
 * Marca do SciPath: o SVG do DS2 (`assets/design_system_2.html`, cabeçalho e rodapé),
 * com as cores trocadas por tokens semânticos — nenhum hex no componente (§0.1).
 * Decorativa por definição: o nome acessível vem do link que a contém.
 */
export function BrandMark({ strokeToken = 'var(--sc-ink)' }: { strokeToken?: string }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" aria-hidden="true" focusable="false">
      <circle cx="5" cy="6" r="2.4" fill="var(--sc-blue)" />
      <circle cx="19" cy="7" r="2.4" fill="var(--sc-red)" />
      <circle cx="12" cy="15.5" r="2.6" fill="var(--sc-purple)" />
      <circle cx="4.5" cy="18" r="1.8" fill="var(--sc-yellow)" />
      <path
        d="M5 6 12 15.5M19 7 12 15.5M4.5 18 12 15.5"
        stroke={strokeToken}
        strokeWidth="1.1"
        strokeLinecap="round"
        opacity="0.55"
      />
    </svg>
  );
}