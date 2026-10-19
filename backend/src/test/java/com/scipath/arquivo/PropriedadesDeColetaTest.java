package com.scipath.arquivo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A configuração do coletor de órfãos (card E4.5).
 *
 * <p>Unidade, e não integração, porque o que está em jogo aqui é o que a configuração <b>recusa</b>
 * — e recusar é o que impede o modo de falha mais silencioso desta entrega. Um coletor com janela
 * zero apaga sem margem nenhuma; um agendamento inválido deixa a rotina declarada e morta, sem
 * nenhum erro. Os dois casos são erros de subida, e é isso que esta classe verifica sem subir
 * contexto nenhum.
 */
@DisplayName("configuração do coletor de órfãos (E4.5)")
class PropriedadesDeColetaTest {

    @Test
    @DisplayName("os padrões são conservadores: janela de um dia, coleta diária às 03:30, sem varredura na subida")
    void padroes() {
        PropriedadesDeColeta propriedades = new PropriedadesDeColeta();

        assertThat(propriedades.getJanela()).isEqualTo(Duration.ofHours(24));
        assertThat(propriedades.getAgendamento()).isEqualTo("0 30 3 * * *");
        assertThat(propriedades.isAoIniciar()).isFalse();
    }

    @Test
    @DisplayName("janela vazia ou negativa é recusada na subida: limpeza sem margem não é limpeza segura")
    void janelaSemMargemEhRecusada() {
        assertThatThrownBy(() -> new PropriedadesDeColeta().setJanela(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("scipath.coleta.janela");
        assertThatThrownBy(() -> new PropriedadesDeColeta().setJanela(Duration.ofHours(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("RN012");
        assertThatThrownBy(() -> new PropriedadesDeColeta().setJanela(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("agendamento inválido é recusado na subida: um coletor que nunca roda não pode passar em silêncio")
    void agendamentoInvalidoEhRecusado() {
        assertThatThrownBy(() -> new PropriedadesDeColeta().setAgendamento("todo dia de manhã"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("scipath.coleta.agendamento");
        assertThatThrownBy(() -> new PropriedadesDeColeta().setAgendamento("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("valores válidos são aceitos, inclusive os do perfil de teste")
    void valoresValidosSaoAceitos() {
        PropriedadesDeColeta propriedades = new PropriedadesDeColeta();
        propriedades.setJanela(Duration.ofHours(1));
        propriedades.setAgendamento("0 0 4 * * MON-FRI");
        propriedades.setAoIniciar(true);

        assertThat(propriedades.getJanela()).isEqualTo(Duration.ofHours(1));
        assertThat(propriedades.getAgendamento()).isEqualTo("0 0 4 * * MON-FRI");
        assertThat(propriedades.isAoIniciar()).isTrue();
    }
}
