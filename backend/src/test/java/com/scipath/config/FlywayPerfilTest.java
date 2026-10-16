package com.scipath.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.scipath.SciPathApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * ADR-0004, ajuste 3: a estratégia tolerante de migração vale só em {@code dev}/{@code test};
 * fora deles a aplicação <b>não sobe</b> quando a migração falha.
 *
 * <p>Estes testes usam um banco inalcançável: o caminho exercitado na subida é o mesmo —
 * uma {@code FlywayException} vinda do Flyway —, e assim a prova não depende de deixar
 * migração quebrada em disco nem de sujar o banco de teste. O cenário literal do critério
 * de aceite (arquivo {@code V__quebrada.sql} + perfil {@code prod}) roda na evidência do
 * card, {@code docs/qa/e1-4-evidencia.sh}, com a saída colada no log.
 *
 * <p>O perfil é passado por argumento de linha de comando porque a suíte roda com
 * {@code spring.profiles.active=test} (ver surefire no {@code pom.xml}): o argumento tem
 * precedência máxima e substitui o perfil herdado, que é o que este teste precisa para
 * medir o comportamento de {@code prod} de verdade.
 */
@DisplayName("Flyway por perfil (ADR-0004, ajuste 3)")
class FlywayPerfilTest {

    private static final String BANCO_INALCANCAVEL =
            "--spring.datasource.url=jdbc:postgresql://127.0.0.1:5499/scipath_inexistente";

    @Test
    @DisplayName("a estratégia tolerante só é registrada em dev e test")
    void estrategiaToleranteSoEmDevETest() {
        assertThat(temEstrategiaTolerante("dev"))
                .as("dev tolera banco fora na subida")
                .isTrue();
        assertThat(temEstrategiaTolerante("test"))
                .as("test tolera banco fora na subida")
                .isTrue();
        assertThat(temEstrategiaTolerante("prod"))
                .as("prod sem a estratégia tolerante = fail-fast do Spring Boot")
                .isFalse();
    }

    @Test
    @DisplayName("perfil prod: migração que falha impede a aplicação de subir")
    void prodNaoSobeQuandoMigracaoFalha() {
        assertThatThrownBy(() -> aplicacaoComPerfil("prod").run(argumentosDoPerfil("prod")))
                .as("fail-fast: nunca servir com schema desatualizado (ADR-0004)")
                .hasStackTraceContaining("org.flywaydb.core");
    }

    @Test
    @DisplayName("perfil dev: mesma falha vira aviso e a API sobe")
    void devSobeMesmoComMigracaoFalhando() {
        try (ConfigurableApplicationContext contexto = aplicacaoComPerfil("dev").run(argumentosDoPerfil("dev"))) {
            assertThat(contexto.getBeanNamesForType(FlywayMigrationStrategy.class))
                    .as("dev mantém a estratégia tolerante")
                    .hasSize(1);
        }
    }

    private static boolean temEstrategiaTolerante(String perfil) {
        try (AnnotationConfigApplicationContext contexto = new AnnotationConfigApplicationContext()) {
            contexto.getEnvironment().setActiveProfiles(perfil);
            contexto.register(FlywayConfig.class);
            contexto.refresh();
            return contexto.getBeanNamesForType(FlywayMigrationStrategy.class).length > 0;
        }
    }

    /** Sobe o contexto completo (sem servidor web: aqui interessa a subida, não a porta). */
    private static SpringApplication aplicacaoComPerfil(String perfil) {
        SpringApplication aplicacao = new SpringApplication(SciPathApplication.class);
        aplicacao.setWebApplicationType(WebApplicationType.NONE);
        return aplicacao;
    }

    private static String[] argumentosDoPerfil(String perfil) {
        return new String[] {BANCO_INALCANCAVEL, "--spring.profiles.active=" + perfil};
    }
}
