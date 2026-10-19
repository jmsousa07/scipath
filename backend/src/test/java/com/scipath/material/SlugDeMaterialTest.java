package com.scipath.material;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Derivação do {@code slug} do material (card E4.4, RN009, RNF025) — teste de unidade, sem
 * contexto Spring: a transformação é pura e é o endereço público do material, então ela precisa
 * de caso próprio, e não só do caso feliz da integração.
 */
@DisplayName("slug de material derivado do título (RN009)")
class SlugDeMaterialTest {

    @ParameterizedTest(name = "\"{0}\" -> \"{1}\"")
    @CsvSource({
            "Introdução a algoritmos de ordenação, introducao-a-algoritmos-de-ordenacao",
            "Física quântica aplicada à computação, fisica-quantica-aplicada-a-computacao",
            "Árvores AVL: balanceamento & rotação, arvores-avl-balanceamento-rotacao",
            "Guia 100% prático (v2), guia-100-pratico-v2",
            "Já é slug-2026, ja-e-slug-2026",
            "C++ e C#, c-e-c",
            "Título sem caractere aproveitável !!!, titulo-sem-caractere-aproveitavel",
    })
    @DisplayName("acento, pontuação e símbolo viram hífen; o resultado é o formato do contrato")
    void derivacaoLimpaTitulo(String titulo, String esperado) {
        String slug = SlugDeMaterial.derivar(titulo);

        assertThat(slug).isEqualTo(esperado);
        assertThat(slug).as("formato do SlugRecurso do contrato")
                .matches("^[a-z0-9]+(-[a-z0-9]+)*$");
    }

    @Test
    @DisplayName("é determinística: o mesmo título produz sempre o mesmo slug")
    void derivacaoDeterministica() {
        String titulo = "Probabilidade e estatística para computação";

        assertThat(SlugDeMaterial.derivar(titulo)).isEqualTo(SlugDeMaterial.derivar(titulo))
                .isEqualTo("probabilidade-e-estatistica-para-computacao");
    }

    @ParameterizedTest(name = "\"{0}\" -> \"{1}\"")
    @CsvSource({
            "!!!, material",
            "'   ', material",
            "---, material",
            "42%&*, 42",
    })
    @DisplayName("título sem caractere aproveitável não gera slug vazio (CHECK material_slug_formato)")
    void tituloSemAproveitavelUsaBaseDeReserva(String titulo, String esperado) {
        assertThat(SlugDeMaterial.derivar(titulo))
                .isEqualTo(esperado)
                .matches("^[a-z0-9]+(-[a-z0-9]+)*$");
    }

    @Test
    @DisplayName("título nulo ou vazio cai na base de reserva, não em exceção")
    void tituloAusenteUsaBaseDeReserva() {
        assertThat(SlugDeMaterial.derivar(null)).isEqualTo(SlugDeMaterial.SLUG_DE_RESERVA);
        assertThat(SlugDeMaterial.derivar("")).isEqualTo(SlugDeMaterial.SLUG_DE_RESERVA);
    }

    @Test
    @DisplayName("título longo é cortado no teto do contrato e sem hífen no fim")
    void tituloLongoRespeitaOTeto() {
        String slug = SlugDeMaterial.derivar("palavra ".repeat(40));

        assertThat(slug.length()).isLessThanOrEqualTo(SlugDeMaterial.COMPRIMENTO_MAXIMO);
        assertThat(slug).matches("^[a-z0-9]+(-[a-z0-9]+)*$");
    }

    @Test
    @DisplayName("sufixo de unicidade mantém o formato e não estoura o teto")
    void sufixoMantemFormatoETeto() {
        assertThat(SlugDeMaterial.comSufixo("ordenacao-por-comparacao", 2))
                .isEqualTo("ordenacao-por-comparacao-2");

        String longo = SlugDeMaterial.derivar("palavra ".repeat(40));
        String comSufixo = SlugDeMaterial.comSufixo(longo, 12);
        assertThat(comSufixo.length()).isLessThanOrEqualTo(SlugDeMaterial.COMPRIMENTO_MAXIMO);
        assertThat(comSufixo).endsWith("-12").matches("^[a-z0-9]+(-[a-z0-9]+)*$");
    }
}
