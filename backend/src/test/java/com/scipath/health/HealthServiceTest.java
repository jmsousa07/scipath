package com.scipath.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Saúde da API")
class HealthServiceTest {

    private static DataSource dataSourceMock() {
        return mock(DataSource.class);
    }

    @Test
    @DisplayName("banco no ar devolve status UP e database UP")
    void bancoNoArDevolveStatusUpEDatabaseUp() throws Exception {
        DataSource dataSource = dataSourceMock();
        Connection conexao = mock(Connection.class);
        when(conexao.isValid(1)).thenReturn(true);
        when(dataSource.getConnection()).thenReturn(conexao);

        HealthResponse resposta = new HealthService(dataSource, "0.1.0").check();

        assertThat(resposta.status()).isEqualTo(HealthService.UP);
        assertThat(resposta.database()).isEqualTo(HealthService.UP);
        assertThat(resposta.version()).isEqualTo("0.1.0");
    }

    @Test
    @DisplayName("banco fora do ar devolve database DOWN e status DEGRADED, sem estourar exceção")
    void bancoForaDoArDevolveDatabaseDown() throws Exception {
        DataSource dataSource = dataSourceMock();
        when(dataSource.getConnection()).thenThrow(new SQLException("Connection refused"));

        HealthResponse resposta = new HealthService(dataSource, "0.1.0").check();

        assertThat(resposta.database()).isEqualTo(HealthService.DOWN);
        assertThat(resposta.status()).isEqualTo(HealthService.DEGRADED);
    }
}
