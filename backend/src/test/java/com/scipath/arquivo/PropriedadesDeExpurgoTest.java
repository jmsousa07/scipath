package com.scipath.arquivo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("configuração do expurgo de retenção (E5.5)")
class PropriedadesDeExpurgoTest {

    @Test
    @DisplayName("padrão de retenção é sete dias e pode ser configurado")
    void janelaPadraoEConfiguravel() {
        PropriedadesDeExpurgo propriedades = new PropriedadesDeExpurgo();

        assertThat(propriedades.getJanela()).isEqualTo(Duration.ofDays(7));
        assertThat(propriedades.janelaFormatada()).isEqualTo("7d");

        propriedades.setJanela(Duration.ofHours(12));
        assertThat(propriedades.janelaFormatada()).isEqualTo("12h");
    }

    @Test
    @DisplayName("janela nula, zero ou negativa é recusada")
    void janelaInvalidaEhRecusada() {
        PropriedadesDeExpurgo propriedades = new PropriedadesDeExpurgo();

        assertThatThrownBy(() -> propriedades.setJanela(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> propriedades.setJanela(Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> propriedades.setJanela(Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("cron de seis campos é validado e expressão inválida é recusada")
    void cronEhValidado() {
        PropriedadesDeExpurgo propriedades = new PropriedadesDeExpurgo();
        propriedades.setAgendamento("0 30 3 * * *");

        assertThat(propriedades.getAgendamento()).isEqualTo("0 30 3 * * *");
        assertThatThrownBy(() -> propriedades.setAgendamento("todo dia de manhã"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("scipath.expurgo.agendamento");
        assertThatThrownBy(() -> propriedades.setAgendamento(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
