package com.scipath.material;

import java.time.Instant;

/**
 * Material do próprio dono — schema {@code MaterialProprio} do contrato v0.6 (tag {@code
 * gerenciamento}, RF015). É o <b>mesmo</b> schema no item da lista
 * ({@code GET /api/perfil/atual/materiais}) e no detalhe
 * ({@code GET /api/perfil/atual/materiais/{slug}}): o dono precisa dos mesmos campos nos dois, e
 * dois schemas para a mesma coisa divergiriam com o tempo (ADR-0011 §2).
 *
 * <p><b>Sem {@code autor}.</b> A autoria é sempre o perfil da sessão (RN001); devolver o dono como
 * autor de si mesmo seria um campo com um valor só. É também por isso que o contrato não reusa
 * {@link MaterialResumoResposta}/{@link MaterialDetalheResposta} aqui: os dois carregam {@code
 * autor} e declaram {@code estado} apenas como {@code PUBLICADO}.
 *
 * <p><b>Os três estados aparecem</b> — {@code PUBLICADO}, {@code RETIRADO} e {@code EXCLUIDO}
 * (RF015, PAGE_BLUEPRINTS §12): esta é a visão do dono, não a do público.
 *
 * <p><b>Nenhum dado privado</b> (RNF013): fora do schema ficam e-mail de login, hash de senha,
 * token, identificador de conta, vínculo de perfil e chave de armazenamento (RNF014). A licença
 * continua explícita, com o que ela permite ({@link LicencaReferencia}, RN006) — estar público não
 * significa domínio público, e a explicação não some porque quem lê é o dono.
 *
 * <p>{@code publicadoEm} é o instante da publicação e <b>não</b> muda por edição nem por
 * republicação (ADR-0011 §4); {@code atualizadoEm} é o instante da última escrita e é a chave da
 * ordenação da lista do dono ({@code atualizadoEm} DESC).
 */
public record MaterialProprioResposta(
        String slug,
        String titulo,
        String descricao,
        CategoriaReferencia categoria,
        TipoMaterialReferencia tipo,
        LicencaReferencia licenca,
        ArquivoMaterialResposta arquivo,
        String estado,
        Instant publicadoEm,
        Instant atualizadoEm) {
}
