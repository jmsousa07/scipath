package com.scipath.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.scipath.conta.ContaRepositorio;
import com.scipath.email.EmailSender;
import com.scipath.email.EmailSenderDeOutbox;
import com.scipath.email.FalhaDeEnvioDeEmailException;
import com.scipath.email.MensagemDeEmail;
import com.scipath.email.ModelosDeEmail;
import com.scipath.email.PropriedadesDeEmail;
import com.scipath.error.ValidacaoDeCampoException;
import com.scipath.identidade.SessaoService;
import com.scipath.identidade.TokensOpacos;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Falha de envio (RNF017, ADR-0003 (g) e (h)): <b>o que o produto NÃO pode marcar como enviado</b>
 * quando o canal de e-mail está quebrado.
 *
 * <p><b>Como a falha é simulada, e por que assim.</b> O canal quebrado é um {@link EmailSender} de
 * teste — um lambda que sempre lança — passado ao serviço <b>construído à mão</b> com os
 * repositórios de verdade (PostgreSQL local, {@code scipath_test}). A alternativa seria registrar
 * um bean de substituição no contexto Spring e a suíte passaria a subir um <b>segundo</b> contexto
 * — memória que esta máquina (954 MB, AGENTS.md) não comporta em troca de nada: o que o critério
 * exige é o <b>estado do banco</b> depois da falha, e o banco aqui é o de verdade.
 *
 * <p><b>O caminho HTTP da falha também está coberto</b>, e é dito onde: o contrato publica
 * <b>500</b> para falha de envio no reenvio de verificação, e o mapeamento
 * {@code FalhaDeEnvioDeEmailException → 500 interno} é provado em
 * {@code com.scipath.error.ApiExceptionHandlerTest} (mesma forma de erro, sem detalhe interno).
 * Aqui se prova o outro lado da mesma promessa: a exceção sai do serviço <b>sem nada gravado</b>.
 *
 * <p>As contas são de fixture, criadas por SQL direto: este teste não passa pelo cadastro de
 * propósito — o cadastro real emitiria um token de verdade, e o cenário precisa começar em
 * "nada emitido".
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("falha de envio de e-mail: nada é marcado como enviado (RNF017)")
class FalhaDeEnvioDeEmailIntegrationTest extends ApoioDeTestesDeToken {

    /** Canal que sempre falha — a "simulação" do critério 7, explícita e no teste. */
    private static final EmailSender CANAL_QUEBRADO = mensagem -> {
        throw new FalhaDeEnvioDeEmailException("canal de e-mail de teste indisponível", null);
    };

    @Autowired
    private TokenRepositorio tokens;

    @Autowired
    private ContaRepositorio contas;

    @Autowired
    private TokensOpacos opacos;

    @Autowired
    private ModelosDeEmail modelos;

    @Autowired
    private PropriedadesDeToken propriedadesDeToken;

    @Autowired
    private SessaoService sessoes;

    @Autowired
    private PasswordEncoder codificador;

    @Test
    @DisplayName("envio inicial que falha não ativa a conta e não deixa token nem mensagem")
    void falhaNoEnvioInicialNaoAtivaAConta() {
        String email = PREFIXO + "envio-inicial" + DOMINIO;
        UUID conta = contaDeFixture(email);

        VerificacaoDeEmailService servico = new VerificacaoDeEmailService(emissorComCanalQuebrado(), contas,
                propriedadesDeToken);

        // Não lança: o cadastro segue respondendo 201 (ADR-0003 (g) — a conta existe, o e-mail não saiu).
        servico.enviarConfirmacaoInicial(conta, email);

        assertThat(linhasDeToken(email, TipoDeToken.CONFIRMACAO_EMAIL))
                .as("nada marcado como enviado: nenhuma linha de token")
                .isZero();
        assertThat(mensagensPara(email)).isEmpty();
        assertThat(estadoDaConta(email)).isEqualTo("PENDENTE_VERIFICACAO");
        assertThat(verificadoEmDaConta(email)).isNull();
        System.out.println("[E3.4] falha no envio inicial -> conta segue PENDENTE_VERIFICACAO, 0 tokens, 0 mensagens");
    }

    @Test
    @DisplayName("reenvio que falha propaga a falha do canal (500 no contrato) sem marcar envio")
    void falhaNoReenvioPropagaSemMarcarEnvio() {
        String email = PREFIXO + "reenvio-quebrado" + DOMINIO;
        UUID conta = contaDeFixture(email);
        EmissorDeTokens emissor = emissorComCanalQuebrado();
        VerificacaoDeEmailService servico = new VerificacaoDeEmailService(emissor, contas, propriedadesDeToken);

        assertThatThrownBy(() -> servico.reenviar(conta))
                .as("o contrato publica 500 para falha de envio no reenvio; a exceção é o que o "
                        + "tratamento único de erro converte nisso")
                .isInstanceOf(FalhaDeEnvioDeEmailException.class);

        assertThat(linhasDeToken(email, TipoDeToken.CONFIRMACAO_EMAIL)).isZero();
        assertThat(mensagensPara(email)).isEmpty();
        assertThat(emissor.ultimaEmissao(conta, TipoDeToken.CONFIRMACAO_EMAIL))
                .as("sem emissão gravada não há cooldown: a tela pode oferecer o reenvio na hora")
                .isEmpty();
        assertThat(estadoDaConta(email)).isEqualTo("PENDENTE_VERIFICACAO");
        System.out.println("[E3.4] falha no reenvio -> exceção propagada, 0 tokens, cooldown não iniciado");
    }

    @Test
    @DisplayName("solicitação de recuperação que falha continua 202 e não troca senha nem emite token")
    void falhaNaRecuperacaoNaoMudaAResposta() {
        String email = PREFIXO + "recuperacao-quebrada" + DOMINIO;
        contaDeFixture(email);
        RecuperacaoDeAcessoService servico = new RecuperacaoDeAcessoService(emissorComCanalQuebrado(), contas,
                sessoes, codificador, propriedadesDeToken);

        // Não lança: a resposta de 202 não pode mudar por causa de falha interna (RNF013).
        servico.solicitar(email);

        assertThat(linhasDeToken(email, TipoDeToken.RECUPERACAO_SENHA)).isZero();
        assertThat(mensagensPara(email)).isEmpty();
        assertThat(codificador.matches(SENHA, senhaHashDaConta(email)))
                .as("nenhuma senha é trocada por uma solicitação que falhou")
                .isTrue();
        assertThatThrownBy(() -> servico.confirmar("token-que-nunca-existiu", SENHA_NOVA))
                .as("sem token emitido, confirmar continua dando a recusa única de token")
                .isInstanceOf(ValidacaoDeCampoException.class);
        System.out.println("[E3.4] falha na recuperação -> resposta inalterada, 0 tokens, senha intacta");
    }

    @Test
    @DisplayName("a mensagem da falha do outbox real não carrega token, link nem destinatário (RNF009)")
    void falhaDoOutboxRealNaoVazaToken() throws IOException {
        Path diretorio = Path.of(propriedadesDeEmail.getOutbox().getDiretorio());
        Files.createDirectories(diretorio);
        Path barreira = diretorio.resolve("barreira-e3-4");
        Files.writeString(barreira, "arquivo comum no lugar onde o teste espera poder criar um diretório");

        PropriedadesDeEmail inalancaveis = new PropriedadesDeEmail();
        inalancaveis.getOutbox().setDiretorio(barreira.resolve("dentro").toString());

        String email = PREFIXO + "vazamento" + DOMINIO;
        String token = "token-de-teste-que-nao-pode-aparecer-em-log";
        try {
            EmailSenderDeOutbox canal = new EmailSenderDeOutbox(inalancaveis);
            assertThatThrownBy(() -> canal.enviar(new MensagemDeEmail(email, TipoDeToken.CONFIRMACAO_EMAIL,
                    "Confirme seu e-mail no SciPath", "http://localhost:3000/verificar-email#token=" + token)))
                    .isInstanceOf(FalhaDeEnvioDeEmailException.class)
                    .hasMessageContaining("outbox de desenvolvimento")
                    .hasMessageNotContaining(token)
                    .hasMessageNotContaining(email)
                    .hasMessageNotContaining("token=");
            System.out.println("[E3.4] falha do outbox real -> mensagem sem token, sem link e sem destinatário");
        } finally {
            Files.deleteIfExists(barreira);
        }
    }

    // -------------------------------------------------------------- utilidades

    private EmissorDeTokens emissorComCanalQuebrado() {
        return new EmissorDeTokens(tokens, opacos, CANAL_QUEBRADO, modelos, propriedadesDeToken);
    }

    /** Conta de fixture: existe para o token ter dono (FK do V1). Sem perfil — este teste não navega. */
    private UUID contaDeFixture(String email) {
        jdbc.update("INSERT INTO account (email, senha_hash, estado) VALUES (?, ?, 'PENDENTE_VERIFICACAO')",
                email, codificador.encode(SENHA));
        return contaId(email);
    }
}
