/**
 * Destino preservado entre a página que recusou e a tela de acesso (DRS §15.3: 401 →
 * login com o destino preservado).
 *
 * Por que a validação existe e não é preciosismo: `?proximo=` vem da BARRA DE ENDEREÇOS,
 * ou seja, de qualquer pessoa. Aceitar o valor cru (`https://outro-site`, `//outro-site`)
 * transformaria a tela de login num redirecionador aberto — quem montasse o link
 * `…/entrar?proximo=https://malicioso` usaria o nosso domínio para levar a pessoa a outro
 * lugar depois de ela digitar a senha. Só caminho INTERNO passa: um `/` inicial e nada de
 * `//` (que o navegador interpreta como outro host).
 *
 * Módulo puro, usado no SERVIDOR (a página lê `searchParams`) — a leitura fica lá para o
 * formulário e o aviso de sessão expirada saírem prontos no HTML entregue, sem depender
 * de JavaScript.
 */

/** Caminho interno válido, ou `undefined` quando o valor aponta para fora do site. */
export function caminhoInterno(bruto: string | string[] | undefined | null): string | undefined {
  const valor = Array.isArray(bruto) ? bruto[0] : bruto;
  if (!valor) return undefined;
  if (!valor.startsWith('/') || valor.startsWith('//')) return undefined;
  return valor;
}
