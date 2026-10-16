package com.scipath.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tratamento único de erro, testado no nível do MVC (MockMvc standalone: sem contexto
 * Spring, sem banco — é teste de contrato, não de integração). O controller aqui dentro
 * é só fixture: existe porque a Etapa 1 ainda não tem endpoint de escrita, e o formato de
 * erro precisa ser verificado antes de existir quem o use.
 *
 * <p>O 500 é o caso mais importante: a exceção de fixture carrega SQL e caminho de
 * arquivo na mensagem, e o teste exige que nada disso apareça na resposta (RNF012).
 */
@DisplayName("tratamento único de erro (ProblemDetail)")
class ApiExceptionHandlerTest {

    private static MockMvc mvc() {
        LocalValidatorFactoryBean validador = new LocalValidatorFactoryBean();
        validador.afterPropertiesSet();
        return MockMvcBuilders
                .standaloneSetup(new ControladorDeFixture())
                .setControllerAdvice(new ApiExceptionHandler())
                .setValidator(validador)
                .build();
    }

    /** DTO de fixture: as mensagens são as que o cliente deve ler (RNF004). */
    record MaterialDeFixture(@NotBlank(message = "informe o título do material") String titulo,
            @Min(value = 1, message = "deve ser 1 ou mais") int paginas) {
    }

    @RestController
    static class ControladorDeFixture {

        @PostMapping("/fixture/material")
        Map<String, Object> publicar(@Valid @RequestBody MaterialDeFixture material) {
            return Map.of("titulo", material.titulo(), "paginas", material.paginas());
        }

        @GetMapping("/fixture/pagina")
        String pagina(@RequestParam @Min(value = 1, message = "deve ser 1 ou mais") int pagina) {
            return "pagina " + pagina;
        }

        @GetMapping("/fixture/quebra")
        String quebra() {
            throw new IllegalStateException("duplicate key value violates unique constraint \"account_email_key\" "
                    + "— SQL: SELECT * FROM account WHERE senha = 'segredo' — /srv/scipath/config/segredo.yml");
        }

        @GetMapping("/fixture/nao-encontrado")
        String naoEncontrado() {
            throw new NaoEncontradoException("O material 'introducao-a-fisica' não existe ou foi retirado.");
        }

        @GetMapping("/fixture/nao-autenticado")
        String naoAutenticado() {
            throw new NaoAutenticadoException("Entre na sua conta para publicar um material.");
        }

        @GetMapping("/fixture/acesso-negado")
        String acessoNegado() {
            throw new AcessoNegadoException("Este material pertence a outro autor.");
        }
    }

    @Test
    @DisplayName("corpo inválido responde 400 com errors[] apontando cada campo")
    void corpoInvalidoResponde400ComErrors() throws Exception {
        MvcResult resultado = mvc().perform(post("/fixture/material")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titulo\":\"  \",\"paginas\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(ApiExceptionHandler.TIPO_VALIDACAO))
                .andExpect(jsonPath("$.title").value("Requisição inválida"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/fixture/material"))
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[?(@.campo == 'titulo')].mensagem")
                        .value("informe o título do material"))
                .andExpect(jsonPath("$.errors[?(@.campo == 'paginas')].mensagem")
                        .value("deve ser 1 ou mais"))
                .andReturn();

        System.out.println("[E1.4] 400 corpo inválido -> " + resultado.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("corpo válido continua passando (o 400 acima não é efeito colateral)")
    void corpoValidoPassa() throws Exception {
        mvc().perform(post("/fixture/material")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titulo\":\"Notas de Cálculo I\",\"paginas\":42}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value("Notas de Cálculo I"));
    }

    @Test
    @DisplayName("parâmetro de consulta com tipo errado responde 400 com errors[] do campo")
    void parametroComTipoErradoResponde400() throws Exception {
        MvcResult resultado = mvc().perform(get("/fixture/pagina").param("pagina", "xyz"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(ApiExceptionHandler.TIPO_VALIDACAO))
                .andExpect(jsonPath("$.errors[0].campo").value("pagina"))
                .andReturn();

        System.out.println("[E1.4] 400 parâmetro com tipo errado -> " + resultado.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("parâmetro de consulta fora da regra responde 400 com errors[] do campo")
    void parametroForaDaRegraResponde400() throws Exception {
        MvcResult resultado = mvc().perform(get("/fixture/pagina").param("pagina", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(ApiExceptionHandler.TIPO_VALIDACAO))
                .andExpect(jsonPath("$.errors[0].campo").value("pagina"))
                .andExpect(jsonPath("$.errors[0].mensagem").value("deve ser 1 ou mais"))
                .andReturn();

        System.out.println("[E1.4] 400 parâmetro fora da regra -> " + resultado.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("JSON malformado responde 400 sem repetir a mensagem do parser")
    void jsonMalformadoResponde400() throws Exception {
        MvcResult resultado = mvc().perform(post("/fixture/material")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titulo\": \"Notas\", "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(ApiExceptionHandler.TIPO_VALIDACAO))
                .andExpect(jsonPath("$.errors[0].campo").value("corpo"))
                .andReturn();

        String corpo = resultado.getResponse().getContentAsString();
        assertThat(corpo).doesNotContain("MaterialDeFixture");
        assertThat(corpo).doesNotContain("Jackson");
        System.out.println("[E1.4] 400 JSON malformado -> " + corpo);
    }

    @Test
    @DisplayName("erro inesperado responde 500 sem stack trace, SQL ou caminho de arquivo")
    void erroInesperadoResponde500SemDetalheInterno() throws Exception {
        MvcResult resultado = mvc().perform(get("/fixture/quebra"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(ApiExceptionHandler.TIPO_INTERNO))
                .andExpect(jsonPath("$.title").value("Erro interno"))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.instance").value("/fixture/quebra"))
                .andExpect(jsonPath("$.detail")
                        .value(org.hamcrest.Matchers.containsString("Tente novamente")))
                .andReturn();

        String corpo = resultado.getResponse().getContentAsString();
        assertThat(corpo)
                .as("RNF012: a resposta não carrega detalhe interno")
                .doesNotContain("SELECT").doesNotContain("account_email_key")
                .doesNotContain("/srv/scipath").doesNotContain("IllegalStateException")
                .doesNotContain("at com.scipath").doesNotContain("\"errors\"");
        System.out.println("[E1.4] 500 erro inesperado -> " + corpo);
    }

    @Test
    @DisplayName("não encontrado, não autenticado e acesso negado usam a mesma forma")
    void classesDeErroRestantes() throws Exception {
        MvcResult resultado404 = mvc().perform(get("/fixture/nao-encontrado"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value(ApiExceptionHandler.TIPO_NAO_ENCONTRADO))
                .andExpect(jsonPath("$.detail").value("O material 'introducao-a-fisica' não existe ou foi retirado."))
                .andReturn();

        MvcResult resultado401 = mvc().perform(get("/fixture/nao-autenticado"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.type").value(ApiExceptionHandler.TIPO_NAO_AUTENTICADO))
                .andExpect(jsonPath("$.detail").value("Entre na sua conta para publicar um material."))
                .andReturn();

        MvcResult resultado403 = mvc().perform(get("/fixture/acesso-negado"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(ApiExceptionHandler.TIPO_ACESSO_NEGADO))
                .andExpect(jsonPath("$.detail").value("Este material pertence a outro autor."))
                .andReturn();

        System.out.println("[E1.4] 404 -> " + resultado404.getResponse().getContentAsString());
        System.out.println("[E1.4] 401 -> " + resultado401.getResponse().getContentAsString());
        System.out.println("[E1.4] 403 -> " + resultado403.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("método não aceito responde 405 na mesma forma (sem cair no catch-all)")
    void metodoNaoAceitoResponde405() throws Exception {
        MvcResult resultado = mvc().perform(get("/fixture/material"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(ApiExceptionHandler.TIPO_METODO_NAO_PERMITIDO))
                .andExpect(jsonPath("$.errors").doesNotExist())
                .andReturn();

        System.out.println("[E1.4] 405 -> " + resultado.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("Content-Type não aceito responde 415 na mesma forma")
    void tipoNaoSuportadoResponde415() throws Exception {
        MvcResult resultado = mvc().perform(post("/fixture/material")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("titulo=Notas"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(ApiExceptionHandler.TIPO_TIPO_NAO_SUPORTADO))
                .andExpect(jsonPath("$.errors").doesNotExist())
                .andReturn();

        System.out.println("[E1.4] 415 -> " + resultado.getResponse().getContentAsString());
    }
}
