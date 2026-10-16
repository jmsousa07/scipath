package com.scipath.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * O tratamento único de erro está realmente ligado na aplicação (o teste standalone
 * prova o formato, não o wiring): aqui o contexto sobe de verdade e as requisições
 * atravessam o MVC completo, incluindo o caminho que o Spring resolve sozinho —
 * endereço inexistente e método não aceito, onde sem {@code ApiExceptionHandler}
 * nasceria um segundo formato de erro.
 *
 * <p>Mesma configuração de contexto de {@code SciPathApplicationTests} de propósito:
 * o Spring reusa o contexto já subido, então este teste não custa uma subida a mais
 * (a máquina tem 954 MB — AGENTS.md). O nome termina em {@code Test} porque é o padrão
 * que o surefire executa; sufixo {@code IT} ficaria de fora de {@code make test}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("erro na aplicação de verdade")
class ApiExceptionHandlerIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    @DisplayName("endereço inexistente responde 404 no formato único do contrato")
    void enderecoInexistenteRespondeProblemDetail() {
        ResponseEntity<String> resposta = rest.getForEntity("/api/nao-existe", String.class);

        assertThat(resposta.getStatusCode().value()).isEqualTo(404);
        assertThat(resposta.getHeaders().getContentType())
                .isNotNull()
                .as("4xx sem handler cairia no JSON de erro padrão do Spring")
                .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(resposta.getBody())
                .contains("\"type\":\"urn:scipath:erro:nao_encontrado\"")
                .contains("\"status\":404")
                .contains("\"instance\":\"/api/nao-existe\"");
        System.out.println("[E1.4] 404 endereço inexistente -> " + resposta.getBody());
    }

    @Test
    @DisplayName("método não aceito responde 405 no formato único do contrato")
    void metodoNaoAceitoRespondeProblemDetail() {
        HttpHeaders cabecalhos = new HttpHeaders();
        cabecalhos.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> resposta = rest.postForEntity("/api/health", new HttpEntity<>("{}", cabecalhos),
                String.class);

        assertThat(resposta.getStatusCode().value()).isEqualTo(405);
        assertThat(resposta.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(resposta.getBody()).contains("\"type\":\"urn:scipath:erro:metodo_nao_permitido\"");
        System.out.println("[E1.4] 405 método não aceito -> " + resposta.getBody());
    }
}
