/**
 * Leitura de `page` na URL e formato de slug do contrato — módulo PURO, sem `fetch`, sem
 * `process.env` e sem API do Next.
 *
 * Por que existe separado: `/u/[slug]` (card E2.10) e `/categoria/[slug]` (card E2.10) são
 * as duas rotas que paginam uma lista SEM ter filtros de busca. As duas precisam da mesma
 * pergunta respondida de forma idêntica: "o `page` que veio na URL é uma página válida?".
 * Se cada rota improvisasse a sua regra, a mesma URL seria válida numa tela e inválida na
 * outra (RNF003).
 *
 * Convenções, as mesmas de `/explorar` (`lib/explorar/consulta.ts`), de propósito:
 *
 *   - **Página 1-based na URL.** Para quem compartilha o endereço, `page=2` é a segunda
 *     página. A API conta em 0-based (contrato, `NumeroPagina`); a conversão acontece uma
 *     vez, na função que monta a consulta.
 *   - **Forma canônica**: `page=1` não entra na URL — assim existe UM endereço por lista,
 *     e o `canonical` declarado não depende de parâmetro.
 *   - **Valor malformado é recusado, não corrigido em silêncio**: `page=0`, `page=-1`,
 *     `page=2.5` e `page=abc` viram o estado `não encontrado` com o valor recusado à vista.
 *     Descartar o parâmetro mostraria a primeira página para quem pediu outra coisa sem
 *     que a pessoa soubesse (mesma razão do teto de `q` no ADR-0008).
 *   - **Parâmetro repetido** (`?page=2&page=9`) vale o primeiro: escolher é determinístico
 *     e não inventa combinação.
 */

/** Formato de slug do contrato (`SlugRecurso`, `FiltroCategoria`): minúsculas com hífen. */
export const FORMATO_SLUG = /^[a-z0-9]+(-[a-z0-9]+)*$/;

/**
 * O slug da URL tem o formato que o contrato aceita como identificador de recurso?
 *
 * Quem pergunta isso antes de chamar a API é a tela: um slug fora do formato devolve **400**
 * do backend, e o 400 de parâmetro é indistinguível do 400 de outra coisa qualquer. A
 * resposta honesta para `/u/Slug_Com_Maiuscula` é `não encontrado` — o endereço não
 * corresponde a nenhum perfil —, com o mesmo texto de um slug bem formado que não existe.
 */
export function slugValido(valor: string): boolean {
  return FORMATO_SLUG.test(valor);
}

/** O que a tela faz com o `page` que veio na URL. */
export type LeituraPagina =
  | { ok: true; pagina: number }
  | { ok: false; valor: string; esperado: string };

/** Página 1-based pedida pela URL. Ausente = primeira página (a forma canônica). */
export function lerPagina(bruto: string | string[] | undefined): LeituraPagina {
  const texto = Array.isArray(bruto) ? (bruto[0] ?? '') : (bruto ?? '');
  if (texto === '') return { ok: true, pagina: 1 };
  if (!/^[1-9]\d*$/.test(texto)) {
    return { ok: false, valor: texto, esperado: 'número inteiro de página, começando em 1' };
  }
  return { ok: true, pagina: Number(texto) };
}

/**
 * URL da página pedida, na forma canônica. `base` é o endereço da própria rota
 * (`/u/nome-do-autor`, `/categoria/nome-da-categoria`): não há filtro nem busca a
 * preservar nestas telas, então a única variação é o número da página.
 */
export function urlComPagina(base: string, pagina: number): string {
  return pagina > 1 ? `${base}?page=${pagina}` : base;
}
