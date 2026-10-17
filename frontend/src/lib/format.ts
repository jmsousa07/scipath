/**
 * Formatação de dado para exibição (datas, hoje).
 *
 * Fuso fixado em UTC, idioma em pt-BR, de propósito: o HTML da listagem é renderizado
 * no SERVIDOR e o mesmo texto pode ser reusado na navegação do cliente. Se o fuso
 * viesse da máquina, o texto mudaria entre ambientes (defeito clássico de divergência
 * de hidratação) e duas pessoas veriam datas diferentes para o mesmo material. O
 * contrato entrega instante em UTC (`date-time` com sufixo `Z`), então UTC é o fuso que
 * corresponde ao dado. Fuso por visitante é decisão de produto que o contrato não prevê.
 */

const DATA_UTC = new Intl.DateTimeFormat('pt-BR', {
  day: '2-digit',
  month: 'short',
  year: 'numeric',
  timeZone: 'UTC',
});

/**
 * Data legível de um instante ISO. Instante que não faz sentido como data (contrato
 * violado) devolve o próprio texto recebido: mostrar o valor cru é honesto; mostrar
 * "Invalid Date" ou vazio esconderia o defeito de quem emitiu.
 */
export function dataCurta(instanteIso: string): string {
  const data = new Date(instanteIso);
  if (Number.isNaN(data.getTime())) return instanteIso;
  return DATA_UTC.format(data);
}
