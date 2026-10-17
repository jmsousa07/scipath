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
import com.scipath.email.FalhaDeEnvioDeEmailException;
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
 * é só fixture: ele existe desde a Etapa 1 (quando ainda não havia endpoint de escrita) e
 * continua sendo o único jeito de exercitar classes de erro sem depender de um fluxo
 * real — o 422 de campo de corpo e o 400 de parâmetro são do vocabulário do contrato, não
 * de uma tela específica. O cadastro (E3.2) tem os seus próprios testes de integração,
 * contra o banco.
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

        /** Cooldown de reenvio (E3.4): o pedido está correto, só chegou cedo. */
        @GetMapping("/fixture/reenvio-em-cooldown")
        String reenvioEmCooldown() {
            throw new ReenvioEmCooldownException(42);
        }

        /** Campo de corpo recusado por regra do serviço — o token de confirmação (E3.4). */
        @GetMapping("/fixture/token-recusado")
        String tokenRecusado() {
            throw new ValidacaoDeCampoException("token", "token inválido ou expirado");
        }

        /** Canal de e-mail fora do ar: o que o contrato publica como 500 no reenvio (E3.4). */
        @GetMapping("/fixture/canal-de-email-quebrado")
        String canalDeEmailQuebrado() {
            throw new FalhaDeEnvioDeEmailException(
                    "não foi possível gravar a mensagem no outbox de desenvolvimento em /home/exemplo/outbox",
                    new java.io.IOException("No space left on device"));
        }
    }

    @Test
    @DisplayName("corpo inválido responde 422 com errors[] apontando cada campo")
    void corpoInvalidoResponde422ComErrors() throws Exception {
        MvcResult resultado = mvc().perform(post("/fixture/material")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"titulo\":\"  \",\"paginas\":0}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(ApiExceptionHandler.TIPO_VALIDACAO_CAMPO))
                .andExpect(jsonPath("$.title").value("Campo inválido"))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.instance").value("/fixture/material"))
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[?(@.campo == 'titulo')].mensagem")
                        .value("informe o título do material"))
                .andExpect(jsonPath("$.errors[?(@.campo == 'paginas')].mensagem")
                        .value("deve ser 1 ou mais"))
                .andReturn();

        System.out.println("[E3.2] 422 corpo inválido -> " + resultado.getResponse().getContentAsString());
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

    @Test
    @DisplayName("reenvio em cooldown responde 429 com Retry-After e sem errors[] (E3.4)")
    void reenvioEmCooldownResponde429ComRetryAfter() throws Exception {
        MvcResult resultado = mvc().perform(get("/fixture/reenvio-em-cooldown"))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Retry-After", "42"))
                .andExpect(jsonPath("$.type").value(ApiExceptionHandler.TIPO_REENVIO_EM_COOLDOWN))
                .andExpect(jsonPath("$.title").value("Reenvio em cooldown"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.detail").value("Aguarde antes de pedir um novo e-mail de verificação."))
                .andExpect(jsonPath("$.instance").value("/fixture/reenvio-em-cooldown"))
                .andExpect(jsonPath("$.errors").doesNotExist())
                .andReturn();

        System.out.println("[E3.4] 429 cooldown -> Retry-After: "
                + resultado.getResponse().getHeader("Retry-After") + " | "
                + resultado.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("campo recusado por regra do serviço responde 422 validacao_campo com o campo (E3.4)")
    void campoRecusadoPelaRegraResponde422() throws Exception {
        MvcResult resultado = mvc().perform(get("/fixture/token-recusado"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(ApiExceptionHandler.TIPO_VALIDACAO_CAMPO))
                .andExpect(jsonPath("$.title").value("Campo inválido"))
                .andExpect(jsonPath("$.detail").value("Um ou mais campos do corpo não foram aceitos."))
                .andExpect(jsonPath("$.errors[0].campo").value("token"))
                .andExpect(jsonPath("$.errors[0].mensagem").value("token inválido ou expirado"))
                .andReturn();

        System.out.println("[E3.4] 422 token recusado -> " + resultado.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("falha do canal de e-mail responde 500 sem detalhe interno (E3.4, RNF017)")
    void falhaDeEnvioResponde500() throws Exception {
        MvcResult resultado = mvc().perform(get("/fixture/canal-de-email-quebrado"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.type").value(ApiExceptionHandler.TIPO_INTERNO))
                .andExpect(jsonPath("$.detail")
                        .value(org.hamcrest.Matchers.containsString("Tente novamente")))
                .andReturn();

        String corpo = resultado.getResponse().getContentAsString();
        assertThat(corpo)
                .as("RNF012/RNF009: a resposta de falha não carrega caminho interno nem a causa")
                .doesNotContain("outbox").doesNotContain("/home/exemplo")
                .doesNotContain("No space left on device")
                .doesNotContain("FalhaDeEnvioDeEmailException");
        System.out.println("[E3.4] 500 canal de e-mail quebrado -> " + corpo);
    }
}
