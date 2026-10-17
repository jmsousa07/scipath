package com.scipath.token;

import com.scipath.conta.Conta;
import com.scipath.conta.ContaRepositorio;
import com.scipath.email.FalhaDeEnvioDeEmailException;
import com.scipath.error.ReenvioEmCooldownException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verificação de e-mail: envio, reenvio com cooldown e confirmação (ADR-0003 (c), (g) e (h);
 * contrato v0.4, RF008/CA004).
 *
 * <p><b>Três caminhos, três comportamentos diferentes diante da mesma falha de canal.</b> É a
 * parte deste serviço que mais precisa de justificativa, porque a diferença é deliberada:
 * <ul>
 *   <li><b>Envio inicial (dentro do cadastro, {@link #enviarConfirmacaoInicial}).</b> A falha é
 *       registrada <b>sem token</b> e absorvida: a conta já existe, o contrato não promete que o
 *       e-mail sai no 201, e devolver erro aqui deixaria o usuário com uma conta criada e uma
 *       sessão possivelmente aberta sem que ele consiga nem entrar (a segunda tentativa
 *       responderia 409). O ADR-0003 (g) descreve exatamente este estado: conta em
 *       {@code PENDENTE_VERIFICACAO}, publicação bloqueada e a interface oferecendo reenvio
 *       (ADR-0003 (h)) — o reenvio existe na hora, porque nada foi marcado como enviado.</li>
 *   <li><b>Reenvio ({@link #reenviar}).</b> A falha <b>propaga</b> e vira <b>500</b>: o contrato
 *       publica 500 para falha de envio neste endpoint e a tela oferece o reenvio de novo
 *       (ADR-0003 (h)). Aqui não há estado a preservar — o pedido é o próprio envio —, e
 *       engolir a falha faria a tela dizer "enviado" sobre um canal quebrado.</li>
 *   <li><b>Confirmação ({@link #confirmar}).</b> Não envia nada: consome o token, marca
 *       {@code email_verificado_em} e leva a conta a {@code ATIVO}.</li>
 * </ul>
 *
 * <p><b>O cooldown é lido do banco, não de memória.</b> Dentro de 60 s da última emissão
 * (ADR-0003 (c)) o reenvio responde <b>429</b> com {@code Retry-After} — e o número de segundos
 * vem da mesma diferença de instantes que o 202 anuncia em {@code podeReenviarEm}. Em memória,
 * duas instâncias (ou um reinício) dariam janelas diferentes para a mesma conta.
 *
 * <p><b>A confirmação também vale para quem já está verificado</b>, e isso é do contrato: o
 * endpoint não distingue conta já verificada de recém-verificada, e um link usado depois da
 * primeira confirmação não é um erro a inventar. O que ele <b>não</b> faz é mover o estado para
 * trás: {@code email_verificado_em} guarda o primeiro instante (a conta não "desverifica") e o
 * estado nunca sai de {@code SUSPENSO} por causa de uma confirmação
 * ({@link ContaRepositorio#marcarEmailVerificado}).
 */
@Service
public class VerificacaoDeEmailService {

    private static final Logger log = LoggerFactory.getLogger(VerificacaoDeEmailService.class);

    private final EmissorDeTokens emissor;
    private final ContaRepositorio contas;
    private final PropriedadesDeToken propriedades;

    public VerificacaoDeEmailService(EmissorDeTokens emissor, ContaRepositorio contas,
            PropriedadesDeToken propriedades) {
        this.emissor = emissor;
        this.contas = contas;
        this.propriedades = propriedades;
    }

    /**
     * Emite e envia a confirmação de uma conta recém-criada. Chamado pelo cadastro, <b>depois</b>
     * de a transação da conta e do perfil ter fechado: um erro de canal não pode desfazer a
     * criação, nem abrir sessão a partir de uma conta que não existe.
     *
     * <p>Falha de envio aqui não é exceção para o cliente — é log de erro <b>sem token</b>
     * (RNF017) e a conta segue em {@code PENDENTE_VERIFICACAO}.
     */
    public void enviarConfirmacaoInicial(UUID contaId, String destinatario) {
        try {
            emissor.emitir(contaId, TipoDeToken.CONFIRMACAO_EMAIL, destinatario);
            log.info("e-mail de confirmação emitido no cadastro (conta={})", contaId);
        } catch (FalhaDeEnvioDeEmailException falha) {
            log.error("falha ao enviar o e-mail de confirmação (conta={}): a conta segue PENDENTE_VERIFICACAO"
                    + " e o reenvio está disponível — {}", contaId, falha.getMessage());
        }
    }

    /**
     * Reenvia a confirmação para o e-mail da <b>própria</b> conta da sessão.
     *
     * <p>O destinatário é lido do banco a partir do {@code contaId} da sessão: o contrato não
     * aceita e-mail no corpo (isso faria do endpoint uma consulta de existência de conta,
     * RNF013), e a sessão não carrega endereço nenhum — então a única fonte é a linha da conta.
     *
     * @throws ReenvioEmCooldownException reenvio pedido antes do fim do cooldown (429)
     * @throws FalhaDeEnvioDeEmailException o canal recusou a mensagem (500, nada marcado como enviado)
     */
    public ReenvioDeVerificacaoResposta reenviar(UUID contaId) {
        Conta conta = contas.buscarPorId(contaId).orElseThrow(() -> new IllegalStateException(
                "conta da sessão não foi encontrada — a sessão existe para uma conta apagada"));

        Instant agora = Instant.now();
        Duration cooldown = propriedades.getCooldownDeReenvio();
        Optional<Instant> ultima = emissor.ultimaEmissao(contaId, TipoDeToken.CONFIRMACAO_EMAIL);
        if (ultima.isPresent()) {
            Instant liberadoEm = ultima.get().plus(cooldown);
            if (agora.isBefore(liberadoEm)) {
                throw new ReenvioEmCooldownException(segundosAte(agora, liberadoEm));
            }
        }

        EmissorDeTokens.Emissao emissao = emissor.emitir(contaId, TipoDeToken.CONFIRMACAO_EMAIL, conta.email());
        log.info("verificação de e-mail reenviada (conta={})", contaId);
        return new ReenvioDeVerificacaoResposta(emissao.criadoEm(), emissao.criadoEm().plus(cooldown));
    }

    /**
     * Consome o token e ativa a conta — o estado que a publicação exige (ADR-0003 (b)).
     *
     * <p>Uma transação para as duas escritas (marcar o token como consumido e ativar a conta): o
     * pior estado possível aqui seria um token queimado sem a conta verificada, porque o usuário
     * ficaria sem o link e sem o estado que o link prometia. Com a transação, ou as duas
     * acontecem ou nenhuma — e a segunda tentativa ainda encontra o token válido.
     *
     * @throws com.scipath.error.ValidacaoDeCampoException token inválido, expirado ou já consumido
     */
    @Transactional
    public void confirmar(String token) {
        UUID contaId = emissor.consumir(token, TipoDeToken.CONFIRMACAO_EMAIL);
        contas.marcarEmailVerificado(contaId, Instant.now());
        log.info("e-mail confirmado; conta ativada (conta={})", contaId);
    }

    /** Segundos até liberar, arredondados <b>para cima</b> — {@code Retry-After} não pode subestimar. */
    private static int segundosAte(Instant agora, Instant liberadoEm) {
        return (int) Math.max(1, Math.ceil(Duration.between(agora, liberadoEm).toMillis() / 1000.0));
    }
}
