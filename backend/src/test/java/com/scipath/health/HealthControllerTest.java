package com.scipath.health;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@DisplayName("GET /api/health")
class HealthControllerTest {

    private static MockMvc mockMvcRespondendo(HealthResponse resposta) {
        HealthService service = mock(HealthService.class);
        when(service.check()).thenReturn(resposta);
        return MockMvcBuilders.standaloneSetup(new HealthController(service)).build();
    }

    @Test
    @DisplayName("responde o JSON do contrato: status, version e database")
    void respondeJsonDoContratoComBancoNoAr() throws Exception {
        mockMvcRespondendo(new HealthResponse(HealthService.UP, "0.1.0", HealthService.UP))
                .perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.version").value("0.1.0"))
                .andExpect(jsonPath("$.database").value("UP"));
    }

    @Test
    @DisplayName("continua devolvendo 200 com o banco fora, marcando database DOWN")
    void continuaRespondendoComBancoFora() throws Exception {
        mockMvcRespondendo(new HealthResponse(HealthService.DEGRADED, "0.1.0", HealthService.DOWN))
                .perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.database").value("DOWN"))
                .andExpect(jsonPath("$.status").value("DEGRADED"));
    }
}
