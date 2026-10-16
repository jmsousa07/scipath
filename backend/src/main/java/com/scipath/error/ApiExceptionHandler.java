package com.scipath.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Tratamento único de erro da API: toda resposta 4xx/5xx sai como
 * {@code application/problem+json} no schema {@code ProblemDetail} do contrato
 * (RFC 7807, {@code contracts/openapi.yaml}), com {@code errors[]} apenas em erro de
 * campo. Não existe segundo formato de erro (convenção vinculante do contrato, §14.3).
 *
 * <p>Regras que este arquivo garante:
 * <ul>
 *   <li>{@code type} é um URN estável {@code urn:scipath:erro:<classe>} — o cliente
 *       decide pelo {@code type}, nunca pelo texto.</li>
 *   <li>{@code detail} e {@code errors[].mensagem} são acionáveis e seguros para exibir
 *       (RNF004): dizem o que corrigir, nunca stack trace, SQL, nome de tabela ou
 *       caminho de arquivo (RNF012).</li>
 *   <li>A falha inesperada é a única que perde o detalhe: ele vai para o log
 *       ({@code ERROR}, com a exceção completa) e o cliente recebe uma mensagem
 *       genérica — o inverso disso é vazamento.</li>
 * </ul>
 *
 * <p>Exceções do framework que não estão mapeadas abaixo caem no catch-all e viram
 * {@code 500}; por isso as que têm status próprio previsível (400, 401, 403, 404, 405,
 * 415 e {@link ErrorResponseException} em geral) estão mapeadas explicitamente.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    /** URNs de classe de erro — vocabulário do contrato, alterado só com o contrato. */
    public static final String TIPO_VALIDACAO = "urn:scipath:erro:validacao";
    public static final String TIPO_NAO_ENCONTRADO = "urn:scipath:erro:nao_encontrado";
    public static final String TIPO_NAO_AUTENTICADO = "urn:scipath:erro:nao_autenticado";
    public static final String TIPO_ACESSO_NEGADO = "urn:scipath:erro:acesso_negado";
    public static final String TIPO_METODO_NAO_PERMITIDO = "urn:scipath:erro:metodo_nao_permitido";
    public static final String TIPO_TIPO_NAO_SUPORTADO = "urn:scipath:erro:tipo_nao_suportado";
    public static final String TIPO_INTERNO = "urn:scipath:erro:interno";

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** Mensagem do {@code detail} para 400 de corpo/objeto — igual à do contrato. */
    private static final String DETALHE_VALIDACAO = "A requisição tem campos inválidos. "
            + "Corrija os campos apontados em errors e envie novamente.";

    /** Mensagem do {@code detail} para 500 — a do contrato, sem detalhe interno. */
    private static final String DETALHE_INTERNO = "Não foi possível concluir a operação. "
            + "Tente novamente. Se persistir, informe o horário e o endereço da requisição.";

    // ------------------------------------------------------------------ 400

    /** Corpo de requisição recusado por Bean Validation ({@code @Valid} em DTO). */
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<ProblemDetail> corpoInvalido(BindException ex, HttpServletRequest requisicao) {
        List<ErroDeCampo> erros = new ArrayList<>();
        for (FieldError erro : ex.getBindingResult().getFieldErrors()) {
            erros.add(new ErroDeCampo(erro.getField(), mensagemUtil(erro.getDefaultMessage())));
        }
        for (ObjectError erro : ex.getBindingResult().getGlobalErrors()) {
            erros.add(new ErroDeCampo(erro.getObjectName(), mensagemUtil(erro.getDefaultMessage())));
        }
        return problema(HttpStatus.BAD_REQUEST, TIPO_VALIDACAO, "Requisição inválida",
                DETALHE_VALIDACAO, requisicao, erros);
    }

    /** Parâmetro de consulta/caminho recusado pelo tipo esperado (ex.: {@code ?ativo=xyz}). */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> parametroComTipoErrado(
            MethodArgumentTypeMismatchException ex, HttpServletRequest requisicao) {
        // Booleano é o único tipo do contrato cujo valor de recusa está publicado como
        // exemplo literal ("deve ser true ou false", resposta 400 de /categorias em
        // contracts/openapi.yaml). Repetir o nome da classe Java ("Boolean") seria
        // acionável para quem lê o código e ruído para quem consome a API.
        boolean booleano = Boolean.class.equals(ex.getRequiredType());
        String motivo = booleano
                ? "deve ser true ou false"
                : "deve ser um valor do tipo " + nomeDoTipo(ex.getRequiredType());
        String detalhe = booleano
                ? "O parâmetro '" + ex.getName() + "' deve ser true ou false."
                : "O parâmetro '" + ex.getName() + "' está em formato inválido: corrija e envie novamente.";
        List<ErroDeCampo> erros = List.of(new ErroDeCampo(ex.getName(), motivo));
        return problema(HttpStatus.BAD_REQUEST, TIPO_VALIDACAO, "Requisição inválida",
                detalhe, requisicao, erros);
    }

    private static String nomeDoTipo(Class<?> tipo) {
        return tipo == null ? "esperado" : tipo.getSimpleName();
    }

    /** Parâmetro de consulta obrigatório ausente. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ProblemDetail> parametroObrigatorioAusente(
            MissingServletRequestParameterException ex, HttpServletRequest requisicao) {
        List<ErroDeCampo> erros = List.of(new ErroDeCampo(ex.getParameterName(), "é obrigatório"));
        return problema(HttpStatus.BAD_REQUEST, TIPO_VALIDACAO, "Requisição inválida",
                "Falta o parâmetro obrigatório '" + ex.getParameterName() + "'.", requisicao, erros);
    }

    /** Restrição em parâmetro de método ({@code @RequestParam} com {@code @Min} etc.). */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ProblemDetail> parametroRecusadoPelaValidacao(
            HandlerMethodValidationException ex, HttpServletRequest requisicao) {
        List<ErroDeCampo> erros = new ArrayList<>();
        ex.getAllValidationResults().forEach(resultado -> resultado.getResolvableErrors().forEach(erro ->
                erros.add(new ErroDeCampo(nomeDoParametro(resultado.getMethodParameter().getParameterName()),
                        mensagemUtil(erro.getDefaultMessage())))));
        return problema(HttpStatus.BAD_REQUEST, TIPO_VALIDACAO, "Requisição inválida",
                DETALHE_VALIDACAO, requisicao, erros);
    }

    /** Restrição de Bean Validation fora do corpo (serviço, parâmetro de método). */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> restricaoViolada(
            ConstraintViolationException ex, HttpServletRequest requisicao) {
        List<ErroDeCampo> erros = ex.getConstraintViolations().stream()
                .map(violacao -> new ErroDeCampo(campoDaViolacao(violacao), mensagemUtil(violacao.getMessage())))
                .toList();
        return problema(HttpStatus.BAD_REQUEST, TIPO_VALIDACAO, "Requisição inválida",
                DETALHE_VALIDACAO, requisicao, erros);
    }

    /**
     * Corpo ilegível (JSON malformado, vazio, tipo errado). A mensagem do parser não vai
     * para a resposta: ela expõe a classe alvo e o caminho interno do desserializador.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> corpoIlegivel(
            HttpMessageNotReadableException ex, HttpServletRequest requisicao) {
        log.warn("corpo ilegível em {} {}: {}", requisicao.getMethod(), requisicao.getRequestURI(), ex.getMessage());
        List<ErroDeCampo> erros = List.of(new ErroDeCampo("corpo", "não é um JSON válido para este endpoint"));
        return problema(HttpStatus.BAD_REQUEST, TIPO_VALIDACAO, "Requisição inválida",
                "O corpo da requisição não pôde ser lido. Envie JSON com os campos do endpoint.",
                requisicao, erros);
    }

    // ------------------------------------------------------------- 401/403

    @ExceptionHandler(NaoAutenticadoException.class)
    public ResponseEntity<ProblemDetail> naoAutenticado(
            NaoAutenticadoException ex, HttpServletRequest requisicao) {
        return problema(HttpStatus.UNAUTHORIZED, TIPO_NAO_AUTENTICADO, "Não autenticado",
                mensagemUtil(ex.getMessage(), "Autentique-se para concluir esta operação."), requisicao, null);
    }

    @ExceptionHandler(AcessoNegadoException.class)
    public ResponseEntity<ProblemDetail> acessoNegado(
            AcessoNegadoException ex, HttpServletRequest requisicao) {
        return problema(HttpStatus.FORBIDDEN, TIPO_ACESSO_NEGADO, "Acesso negado",
                mensagemUtil(ex.getMessage(), "Sua conta não tem permissão para esta operação."),
                requisicao, null);
    }

    // ------------------------------------------------------------- 404/405/415

    @ExceptionHandler(NaoEncontradoException.class)
    public ResponseEntity<ProblemDetail> naoEncontrado(
            NaoEncontradoException ex, HttpServletRequest requisicao) {
        return problema(HttpStatus.NOT_FOUND, TIPO_NAO_ENCONTRADO, "Recurso não encontrado",
                mensagemUtil(ex.getMessage(), "O recurso solicitado não foi encontrado."), requisicao, null);
    }

    /** Caminho sem endpoint (antes do catch-all, para não virar 500). */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemDetail> caminhoSemRecurso(
            NoResourceFoundException ex, HttpServletRequest requisicao) {
        return problema(HttpStatus.NOT_FOUND, TIPO_NAO_ENCONTRADO, "Recurso não encontrado",
                "Não existe recurso neste endereço. Confira o método e o caminho.", requisicao, null);
    }

    /**
     * Método HTTP não aceito no endereço. Sem {@code errors[]}: o contrato reserva
     * {@code errors[]} para erro de campo, e método não é campo — a orientação vai no
     * {@code detail}, que é o campo seguro para exibir (RNF004).
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetail> metodoNaoPermitido(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest requisicao) {
        String aceitos = ex.getSupportedMethods() == null || ex.getSupportedMethods().length == 0
                ? "outro método" : String.join(" ou ", ex.getSupportedMethods());
        return problema(HttpStatus.METHOD_NOT_ALLOWED, TIPO_METODO_NAO_PERMITIDO, "Método não permitido",
                "O método " + ex.getMethod() + " não é aceito neste endereço. Use " + aceitos + ".",
                requisicao, null);
    }

    /** {@code Content-Type} não aceito — mesma razão do 405 para não usar {@code errors[]}. */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ProblemDetail> tipoNaoSuportado(
            HttpMediaTypeNotSupportedException ex, HttpServletRequest requisicao) {
        return problema(HttpStatus.UNSUPPORTED_MEDIA_TYPE, TIPO_TIPO_NAO_SUPORTADO,
                "Tipo de conteúdo não suportado",
                "Este endpoint não aceita o Content-Type enviado. Envie o corpo como "
                        + MediaType.APPLICATION_JSON_VALUE + ".", requisicao, null);
    }

    /** Exceções do Spring que já carregam status próprio (ex.: {@code ResponseStatusException}). */
    @ExceptionHandler(ErrorResponseException.class)
    public ResponseEntity<ProblemDetail> comStatusProprio(
            ErrorResponseException ex, HttpServletRequest requisicao) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        HttpStatus efetivo = status == null ? HttpStatus.INTERNAL_SERVER_ERROR : status;
        if (efetivo.is5xxServerError()) {
            return inesperado(ex, requisicao);
        }
        return problema(efetivo, tipoDaClasse(efetivo), efetivo.getReasonPhrase(),
                "A requisição não pôde ser atendida como enviada.", requisicao, null);
    }

    /** URN da classe de erro conforme o status, para não rotular 404 como erro de validação. */
    private static String tipoDaClasse(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST, UNPROCESSABLE_ENTITY -> TIPO_VALIDACAO;
            case UNAUTHORIZED -> TIPO_NAO_AUTENTICADO;
            case FORBIDDEN -> TIPO_ACESSO_NEGADO;
            case NOT_FOUND -> TIPO_NAO_ENCONTRADO;
            case METHOD_NOT_ALLOWED -> TIPO_METODO_NAO_PERMITIDO;
            case UNSUPPORTED_MEDIA_TYPE -> TIPO_TIPO_NAO_SUPORTADO;
            case INTERNAL_SERVER_ERROR, BAD_GATEWAY, SERVICE_UNAVAILABLE, GATEWAY_TIMEOUT -> TIPO_INTERNO;
            default -> TIPO_INTERNO;
        };
    }

    // ------------------------------------------------------------------ 500

    /**
     * Qualquer falha não prevista. O detalhe técnico fica no log; a resposta é genérica
     * (RNF012) e o {@code type} é o do contrato.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> inesperado(Exception ex, HttpServletRequest requisicao) {
        log.error("falha inesperada em {} {}", requisicao.getMethod(), requisicao.getRequestURI(), ex);
        return problema(HttpStatus.INTERNAL_SERVER_ERROR, TIPO_INTERNO, "Erro interno",
                DETALHE_INTERNO, requisicao, null);
    }

    // -------------------------------------------------------------- interno

    private ResponseEntity<ProblemDetail> problema(HttpStatus status, String tipo, String titulo,
            String detalhe, HttpServletRequest requisicao, List<ErroDeCampo> erros) {
        ProblemDetail corpo = ProblemDetail.forStatusAndDetail(status, detalhe);
        corpo.setType(URI.create(tipo));
        corpo.setTitle(titulo);
        corpo.setInstance(URI.create(requisicao.getRequestURI()));
        if (erros != null && !erros.isEmpty()) {
            corpo.setProperty("errors", erros);
        }
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(corpo);
    }

    private static String mensagemUtil(String mensagem) {
        return mensagemUtil(mensagem, "valor inválido");
    }

    private static String mensagemUtil(String mensagem, String padrao) {
        return mensagem == null || mensagem.isBlank() ? padrao : mensagem;
    }

    private static String nomeDoParametro(String nome) {
        return nome == null || nome.isBlank() ? "parâmetro" : nome;
    }

    /** Último nó do caminho da violação: {@code metodo.arg0.ativo} vira {@code ativo}. */
    private static String campoDaViolacao(ConstraintViolation<?> violacao) {
        String caminho = violacao.getPropertyPath() == null ? "" : violacao.getPropertyPath().toString();
        int ultimoPonto = caminho.lastIndexOf('.');
        String campo = ultimoPonto >= 0 ? caminho.substring(ultimoPonto + 1) : caminho;
        return nomeDoParametro(campo);
    }
}
