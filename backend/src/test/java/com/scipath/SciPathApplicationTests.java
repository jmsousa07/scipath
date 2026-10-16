package com.scipath;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * A API sobe de verdade (contexto completo, porta aleatória) e o /api/health
 * responde o contrato — inclusive com o banco fora, quando database vira DOWN.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("API de pé")
class SciPathApplicationTests {

    @Autowired
    private TestRestTemplate rest;

    @Test
    @DisplayName("sobe e responde /api/health com os três campos do contrato")
    void sobeERespondeHealth() {
        ResponseEntity<Map> resposta = rest.getForEntity("/api/health", Map.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        // TestRestTemplate desserializa para Map cru; o cast é o preço de não expor DTO de teste.
        @SuppressWarnings("unchecked")
        Map<String, Object> corpo = resposta.getBody();
        assertThat(corpo).containsKeys("status", "version", "database");
        assertThat(corpo.get("status")).isIn("UP", "DEGRADED");
        assertThat(corpo.get("database")).isIn("UP", "DOWN");
        assertThat(corpo.get("version")).isEqualTo("0.1.0");
    }
}
