package com.scipath.config;

import org.flywaydb.core.api.FlywayException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Política de migração por perfil (ADR-0004, ajuste 3).
 *
 * <p><b>dev/test — tolerante:</b> se o banco não estiver acessível na subida, a migração
 * registra o aviso e a API continua de pé — {@code /health} mostra {@code database=DOWN}
 * e o operador sobe o Postgres e reinicia quando quiser. Sem isso, o Flyway derruba o
 * contexto e o critério "a API continua respondendo com o banco parado" não seria
 * atendido no dia a dia.
 *
 * <p><b>prod (e qualquer perfil que não seja dev/test) — fail-fast:</b> nenhum
 * {@link FlywayMigrationStrategy} é registrado, então vale o padrão do Spring Boot, que
 * propaga a {@code FlywayException} e impede a aplicação de subir. Servir com schema
 * desatualizado em silêncio é o risco que o ADR-0004 recusa; aqui a falha é barulhenta
 * na subida, que é quando alguém pode consertar.
 *
 * <p>Não existe perfil padrão em {@code application.yml} de propósito: sem perfil
 * explícito vale o comportamento seguro (fail-fast). Rodando local, use
 * {@code SPRING_PROFILES_ACTIVE=dev} — o alvo {@code make api:dev} já exporta isso.
 */
@Configuration
public class FlywayConfig {

    private static final Logger log = LoggerFactory.getLogger(FlywayConfig.class);

    @Bean
    @Profile({"dev", "test"})
    public FlywayMigrationStrategy tolerantFlywayMigration() {
        return flyway -> {
            try {
                flyway.migrate();
            } catch (FlywayException e) {
                log.warn("Flyway não aplicou migrações nesta subida (perfil tolerante): {}", e.getMessage());
            }
        };
    }
}
