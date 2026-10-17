package com.scipath.conta;

import java.util.List;

/**
 * Slugs que nenhum perfil pode assumir (RN009, card E3.5 — "reservados mínimos").
 *
 * <p>O slug do perfil é a chave da URL pública ({@code /u/{slug}} em PAGE_BLUEPRINTS, o mesmo
 * valor aceito em {@code /autores/{slug}}), e o contrato não impõe nenhuma palavra proibida
 * além do formato. O que esta lista guarda é o <b>mínimo</b> que a própria aplicação já ocupa:
 * o primeiro segmento de cada rota publicada (páginas e API). Sem isso, um perfil poderia
 * tomar o endereço de uma rota do produto e a URL canônica do autor passaria a ser ambígua.
 *
 * <p><b>Dois pontos de entrada, uma lista.</b> O slug chega ao sistema por dois caminhos — o
 * derivado do nome no cadastro ({@link GeradorDeSlug}) e o informado na edição de perfil
 * ({@link EdicaoPerfilRequisicao}) —, e os dois consultam esta classe. Se só a edição
 * consultasse, o cadastro poderia <i>gerar</i> um slug reservado (nome "Api" → {@code api}) e
 * o dono ficaria sem poder salvar o próprio perfil com o slug que já tem.
 *
 * <p>A lista é fechada e curta de propósito: crescer aqui é decidir que uma palavra deixa de
 * ser um slug legítimo, e isso vale como mudança de contrato (AGENTS.md regra 1).
 */
final class SlugsReservados {

    /**
     * Primeiro segmento de cada rota publicada: as páginas de {@code PAGE_BLUEPRINTS.md} e os
     * prefixos da API do contrato v0.4. Ordem fixa (lista, não conjunto) para o texto derivado
     * ser determinístico e reproduzível na evidência.
     */
    private static final List<String> VALORES = List.of(
            "api",
            "autores",
            "cadastro",
            "categoria",
            "categorias",
            "contas",
            "entrar",
            "explorar",
            "health",
            "licencas",
            "materiais",
            "painel",
            "perfil",
            "privacidade",
            "recuperacao-senha",
            "sessoes",
            "termos",
            "tipos",
            "verificacao-email");

    private SlugsReservados() {
    }

    /** O slug já pertence a uma rota da aplicação? Comparação exata — o formato é o do contrato. */
    static boolean contem(String slug) {
        return slug != null && VALORES.contains(slug);
    }

    /** Os valores reservados, na ordem da declaração — usado pelo teste e pela validação. */
    static List<String> valores() {
        return VALORES;
    }
}
