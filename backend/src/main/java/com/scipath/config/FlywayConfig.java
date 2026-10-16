package com.scipath.config;

import org.flywaydb.core.api.FlywayException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlywayConfig {

    private static final Logger log = LoggerFactory.getLogger(FlywayConfig.class);

    /**
     * Estratégia tolerante: se o banco não estiver acessível na subida, a migração
     * registra o aviso e a API continua de pé — {@code /health} mostra
     * {@code database=DOWN} e o operador sobe o Postgres e reinicia quando quiser.
     *
     * <p>Sem isso, o Flyway derruba o contexto e o critério 4 do card F0.1
     * ("a API continua respondendo com o banco parado") não seria atendido.
     */
    @Bean
    public FlywayMigrationStrategy tolerantFlywayMigration() {
        return flyway -> {
            try {
                flyway.migrate();
            } catch (FlywayException e) {
                log.warn("Flyway não aplicou migrações nesta subida: {}", e.getMessage());
            }
        };
    }
}
