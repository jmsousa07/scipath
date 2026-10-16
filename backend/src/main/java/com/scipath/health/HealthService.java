package com.scipath.health;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Mede o estado da API e do banco para o endpoint de saúde.
 *
 * <p>O banco é tratado como dependência opcional: a indisponibilidade vira um
 * campo {@code DOWN} no JSON, nunca uma exceção que derrube a resposta.
 */
@Service
public class HealthService {

    public static final String UP = "UP";
    public static final String DOWN = "DOWN";
    public static final String DEGRADED = "DEGRADED";

    private static final Logger log = LoggerFactory.getLogger(HealthService.class);

    private final DataSource dataSource;
    private final String version;

    public HealthService(DataSource dataSource, @Value("${scipath.version}") String version) {
        this.dataSource = dataSource;
        this.version = version;
    }

    public HealthResponse check() {
        boolean bancoNoAr = databaseIsUp();
        return new HealthResponse(bancoNoAr ? UP : DEGRADED, version, bancoNoAr ? UP : DOWN);
    }

    private boolean databaseIsUp() {
        try (Connection conexao = dataSource.getConnection()) {
            return conexao.isValid(1);
        } catch (SQLException | RuntimeException e) {
            log.warn("banco indisponível para o /health: {}", e.getMessage());
            return false;
        }
    }
}
