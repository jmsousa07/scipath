package com.scipath.conta;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Derivação do slug inicial a partir do nome público (RF008, RN009) — teste de unidade puro,
 * sem Spring e sem banco.
 *
 * <p>O formato exigido ({@code ^[a-z0-9]+(-[a-z0-9]+)*$}) é o do banco
 * ({@code profile_slug_formato} do V1): um valor fora dele não entra. Por isso os casos aqui
 * são os que decidem entre "grava" e "erro de constraint" — acento, caixa, pontuação e nome
 * sem caractere aproveitável.
 */
@DisplayName("derivação de slug do nome público")
class GeradorDeSlugTest {

    @Test
    @DisplayName("nome com acento, caixa e pontuação vira slug ASCII em minúsculas")
    void nomeCompostoViraSlug() {
        assertThat(GeradorDeSlug.derivar("João  Mateus  de Sousa")).isEqualTo("joao-mateus-de-sousa");
        assertThat(GeradorDeSlug.derivar("Ciência da Computação")).isEqualTo("ciencia-da-computacao");
        assertThat(GeradorDeSlug.derivar("Ana-Maria (IFCE)")).isEqualTo("ana-maria-ifce");
        assertThat(GeradorDeSlug.derivar("  Espaços  nas  pontas  ")).isEqualTo("espacos-nas-pontas");
    }

    @Test
    @DisplayName("nome sem caractere aproveitável usa a base de reserva, nunca slug vazio")
    void nomeImprestavelUsaReserva() {
        assertThat(GeradorDeSlug.derivar("!!! ???")).isEqualTo(GeradorDeSlug.SLUG_DE_RESERVA);
        assertThat(GeradorDeSlug.derivar("Ω")).isEqualTo(GeradorDeSlug.SLUG_DE_RESERVA);
        assertThat(GeradorDeSlug.derivar(null)).isEqualTo(GeradorDeSlug.SLUG_DE_RESERVA);
    }

    @Test
    @DisplayName("nome longo é cortado no teto de 120 sem terminar em hífen")
    void nomeLongoRespeitaOTeto() {
        String derivado = GeradorDeSlug.derivar("a ".repeat(200) + "b");
        assertThat(derivado).hasSizeLessThanOrEqualTo(GeradorDeSlug.COMPRIMENTO_MAXIMO);
        assertThat(derivado).matches("^[a-z0-9]+(-[a-z0-9]+)*$");
    }

    @Test
    @DisplayName("sufixo numérico continua dentro do formato e do teto")
    void sufixoRespeitaFormatoETeto() {
        assertThat(GeradorDeSlug.comSufixo("maria-silva", 2)).isEqualTo("maria-silva-2");
        assertThat(GeradorDeSlug.comSufixo("a".repeat(200), 2))
                .hasSizeLessThanOrEqualTo(GeradorDeSlug.COMPRIMENTO_MAXIMO)
                .matches("^[a-z0-9]+(-[a-z0-9]+)*$");
    }
}
