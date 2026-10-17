package com.scipath.conta;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Slugs reservados e o desvio da derivação (RN009, card E3.5) — teste de unidade puro, sem
 * Spring e sem banco.
 *
 * <p>O que ele protege é uma <b>invariante da lista</b>: reservar uma palavra só faz sentido se
 * ela for um slug válido do contrato (senão a regra nunca casa e vira decoração), e o desvio na
 * derivação existe para o cadastro não criar um slug que a edição de perfil recusaria — o dono
 * ficaria sem poder salvar o próprio perfil com o identificador que já tem.
 */
@DisplayName("slugs reservados e derivação")
class SlugsReservadosTest {

    /** Formato de slug do contrato, o mesmo de {@code profile_slug_formato} no V1. */
    private static final String FORMATO = "^[a-z0-9]+(-[a-z0-9]+)*$";

    @Test
    @DisplayName("todo valor reservado é um slug válido — a reserva é expressável no formato do contrato")
    void reservadosEstaoNoFormatoDoContrato() {
        assertThat(SlugsReservados.valores()).isNotEmpty();
        assertThat(SlugsReservados.valores())
                .allSatisfy(valor -> assertThat(valor).as("reservado '%s'", valor).matches(FORMATO));
    }

    @Test
    @DisplayName("a reserva é exata: palavra que apenas começa com um valor reservado continua livre")
    void reservaEhComparacaoExata() {
        assertThat(SlugsReservados.contem("api")).isTrue();
        assertThat(SlugsReservados.contem("perfil")).isTrue();

        assertThat(SlugsReservados.contem("apicultura")).as("não é prefixo").isFalse();
        assertThat(SlugsReservados.contem("api-v2")).isFalse();
        assertThat(SlugsReservados.contem("perfis")).isFalse();
        assertThat(SlugsReservados.contem(null)).isFalse();
    }

    @Test
    @DisplayName("nome que geraria um slug reservado desvia para a primeira variante numérica")
    void nomeReservadoDesviaParaVariante() {
        assertThat(GeradorDeSlug.derivar("Api")).isEqualTo("api-2");
        assertThat(GeradorDeSlug.derivar("Perfil")).isEqualTo("perfil-2");
        assertThat(GeradorDeSlug.derivar("Explorar")).isEqualTo("explorar-2");
        assertThat(GeradorDeSlug.derivar("  CATEGORIA  ")).isEqualTo("categoria-2");

        // O que não é reservado continua exatamente como era: o desvio não toca o caso comum.
        assertThat(GeradorDeSlug.derivar("João Mateus")).isEqualTo("joao-mateus");
        assertThat(GeradorDeSlug.derivar("Apicultura")).isEqualTo("apicultura");

        assertThat(List.of("api-2", "perfil-2", "explorar-2", "categoria-2"))
                .allSatisfy(valor -> assertThat(SlugsReservados.contem(valor)).isFalse());
    }
}
