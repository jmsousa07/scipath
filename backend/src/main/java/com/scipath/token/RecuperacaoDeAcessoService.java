package com.scipath.token;

import com.scipath.conta.Conta;
import com.scipath.conta.ContaRepositorio;
import com.scipath.email.FalhaDeEnvioDeEmailException;
import com.scipath.identidade.SessaoService;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recuperação de acesso (RF010, ADR-0003 (d); contrato v0.4, {@code /recuperacao-senha/*}).
 *
 * <p><b>O fluxo é desenhado para não revelar quem tem conta.</b> As duas respostas —
 * solicitação e confirmação — têm de ser indistinguíveis para quem não tem o link:
 * <ul>
 *   <li>A solicitação responde <b>202 sempre</b>, com corpo vazio, para e-mail existente e
 *       inexistente. Quando não há conta, nada é gerado e nada é enviado; a única coisa que muda
 *       é o que ficou no log do servidor (sem endereço, sem token).</li>
 *   <li>Falha de envio <b>não</b> muda a resposta (ao contrário do reenvio de verificação, que
 *       responde 500): um erro que só aparecesse quando a conta existe seria o mesmo oráculo. O
 *       erro é registrado sem token e a resposta continua 202 (RNF013, RNF017).</li>
 *   <li>Dentro da janela de reenvio (os mesmos 60 s do ADR-0003 (c)) o pedido simplesmente
 *       <b>não gera mensagem nova</b> e continua respondendo 202 — o contrato escolhe isso em vez
 *       do 429 de propósito: um cooldown que só valesse para conta existente seria exatamente a
 *       distinção que a resposta evita.</li>
 * </ul>
 *
 * <p><b>E o destinatário é o do banco, não o da requisição.</b> O corpo traz um e-mail só para
 * dizer <i>qual</i> conta recuperar; o endereço em que a mensagem é entregue é o
 * {@code account.email} da linha encontrada. Enviar para um endereço vindo do corpo seria abrir
 * o canal de e-mail da plataforma para endereço arbitrário.
 *
 * <p><b>A confirmação invalida as sessões da conta</b> (RNF012): quem trocou a senha por
 * recuperação não pode continuar logado por uma cookie obtida antes — e o cookie antigo também
 * não pode ser reapresentado depois. É a razão de a sessão viver em tabela (V4).
 *
 * <p><b>Limite declarado:</b> o tempo de resposta <b>não</b> é equalizado artificialmente entre
 * conta existente e inexistente. A diferença residual é uma gravação de token e uma do outbox
 * (sub-milissegundo aqui), e um atraso sintético esconderia a diferença ao custo de segurar uma
 * thread — pior no servidor real, onde o envio passa a ser rede. O que a resposta garante é o
 * status, o corpo e a ausência de qualquer dado distinguível (RNF013); o canal de latência fica
 * registrado como risco, não silenciado.
 */
@Service
public class RecuperacaoDeAcessoService {

    private static final Logger log = LoggerFactory.getLogger(RecuperacaoDeAcessoService.class);

    private final EmissorDeTokens emissor;
    private final ContaRepositorio contas;
    private final SessaoService sessoes;
    private final PasswordEncoder codificador;
    private final PropriedadesDeToken propriedades;

    public RecuperacaoDeAcessoService(EmissorDeTokens emissor, ContaRepositorio contas, SessaoService sessoes,
            PasswordEncoder codificador, PropriedadesDeToken propriedades) {
        this.emissor = emissor;
        this.contas = contas;
        this.sessoes = sessoes;
        this.codificador = codificador;
        this.propriedades = propriedades;
    }

    /**
     * Inicia a recuperação: se existir conta com o e-mail, emite e envia o token de uso único
     * (validade de 30 min). O retorno é {@code void} de propósito — <b>não há nada a devolver</b>
     * que possa diferir entre conta existente e inexistente.
     */
    public void solicitar(String email) {
        Optional<Conta> encontrada = contas.buscarPorEmail(email);
        if (encontrada.isEmpty()) {
            // Sem endereço no log (RNF013): o registro diz que nada foi enviado, e não para quem.
            log.info("pedido de recuperação para e-mail sem conta: nada foi enviado, resposta inalterada");
            return;
        }

        Conta conta = encontrada.get();
        Instant agora = Instant.now();
        Optional<Instant> ultima = emissor.ultimaEmissao(conta.id(), TipoDeToken.RECUPERACAO_SENHA);
        if (ultima.isPresent() && agora.isBefore(ultima.get().plus(propriedades.getCooldownDeReenvio()))) {
            log.info("pedido de recuperação dentro da janela de reenvio: nenhuma mensagem nova (conta={})",
                    conta.id());
            return;
        }

        try {
            emissor.emitir(conta.id(), TipoDeToken.RECUPERACAO_SENHA, conta.email());
            log.info("recuperação de acesso solicitada e emitida (conta={})", conta.id());
        } catch (FalhaDeEnvioDeEmailException falha) {
            // A resposta continua 202: mudá-la aqui revelaria a existência da conta (RNF013).
            log.error("falha ao enviar o link de recuperação (conta={}): a resposta segue 202 — {}",
                    conta.id(), falha.getMessage());
        }
    }

    /**
     * Conclui a recuperação: consome o token, troca a senha (bcrypt 12) e encerra as sessões
     * abertas da conta.
     *
     * <p>As três escritas em uma transação: trocar a senha sem derrubar as sessões deixaria um
     * invasor logado com a senha que o dono acabou de trocar, e derrubar as sessões sem trocar a
     * senha deixaria o dono fora sem o remédio. Um {@code token} recusado
     * ({@link com.scipath.error.ValidacaoDeCampoException}) para antes de qualquer escrita.
     */
    @Transactional
    public void confirmar(String token, String novaSenha) {
        UUID contaId = emissor.consumir(token, TipoDeToken.RECUPERACAO_SENHA);

        // bcrypt custo 12, o mesmo codificador do cadastro (ADR-0003 (a)): o texto puro morre
        // com a requisição e nunca entra em log (RNF009).
        contas.atualizarSenhaHash(contaId, codificador.encode(novaSenha), Instant.now());

        int encerradas = sessoes.encerrarTodasDaConta(contaId);
        log.info("senha trocada por recuperação e sessões encerradas: {} sessão(ões) (conta={})",
                encerradas, contaId);
    }
}
