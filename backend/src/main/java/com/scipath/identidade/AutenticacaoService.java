package com.scipath.identidade;

import com.scipath.autor.PerfilPublico;
import com.scipath.conta.Conta;
import com.scipath.conta.ContaRepositorio;
import com.scipath.conta.EstadoConta;
import com.scipath.conta.PerfilRepositorio;
import com.scipath.conta.SessaoAtualResposta;
import com.scipath.error.AcessoNegadoException;
import com.scipath.error.NaoAutenticadoException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Conferência de credenciais (RF009, CA004) — o que o login faz antes de existir sessão.
 *
 * <p><b>Um único 401, e sempre o mesmo.</b> E-mail inexistente e senha errada produzem
 * exatamente o mesmo corpo: mesmo {@code type}, mesmo {@code detail}, mesmo status. O login
 * não é um serviço de consulta de existência de conta (RNF013) — e a igualdade é garantida
 * pelo texto ser uma constante única, e não por dois caminhos que alguém precise manter
 * iguais à mão.
 *
 * <p><b>O tempo também não conta.</b> Quando o e-mail não existe, o serviço ainda executa
 * <i>uma</i> conferência bcrypt contra um hash fictício gerado na subida. Sem isso, a
 * resposta de e-mail inexistente voltaria em microssegundos e a de senha errada em ~300 ms
 * (o custo do bcrypt 12), e o relógio entregaria o que o corpo da resposta esconde. O hash
 * fictício é gerado <b>uma</b> vez, com o mesmo {@link PasswordEncoder} de
 * {@link com.scipath.config.ConfiguracaoDeSenha} — nenhum segundo codificador nasce aqui.
 *
 * <p><b>A senha nunca é comparada em texto.</b> {@code senhaHash} é o bcrypt 12 gravado pelo
 * cadastro (ADR-0003 (a)); a senha em claro vive só nesta chamada e não entra em log, em
 * exceção nem em DTO de resposta (RNF009).
 *
 * <p><b>Conta suspensa.</b> Com credenciais <i>corretas</i>, a resposta é <b>403</b> com
 * motivo explícito: quem provou ser o dono tem o direito de saber por que não entra (RNF004).
 * Com credenciais erradas, a resposta continua sendo o 401 genérico — a suspensão não pode
 * virar oráculo de existência de conta. A ordem das checagens é o que garante isso: primeiro
 * a senha, depois o estado.
 */
@Service
public class AutenticacaoService {

    private static final Logger log = LoggerFactory.getLogger(AutenticacaoService.class);

    /** O {@code detail} do 401 — o mesmo do contrato, e o mesmo para os dois motivos. */
    static final String DETALHE_CREDENCIAIS_INVALIDAS = "E-mail ou senha inválidos.";

    /** O {@code detail} do 403 de conta suspensa — do contrato, e só para quem provou a senha. */
    static final String DETALHE_CONTA_SUSPENSA = "Esta conta está suspensa e não pode entrar.";

    private final ContaRepositorio contas;
    private final PerfilRepositorio perfis;
    private final PasswordEncoder codificador;

    /** Hash descartável, só para igualar o tempo do caminho "e-mail não existe" (ver o javadoc). */
    private final String hashFicticio;

    public AutenticacaoService(ContaRepositorio contas, PerfilRepositorio perfis, PasswordEncoder codificador) {
        this.contas = contas;
        this.perfis = perfis;
        this.codificador = codificador;
        this.hashFicticio = codificador.encode("senha-ficticia-para-igualar-o-tempo-de-resposta");
    }

    /**
     * Confere as credenciais e devolve quem entrou.
     *
     * @param credenciais corpo do login, já validado e com o e-mail normalizado
     * @throws NaoAutenticadoException e-mail inexistente ou senha errada (401, corpo único)
     * @throws AcessoNegadoException   credenciais corretas em conta {@code SUSPENSO} (403)
     */
    public LoginAutenticado autenticar(CredenciaisRequisicao credenciais) {
        Optional<Conta> encontrada = contas.buscarPorEmail(credenciais.email());
        if (encontrada.isEmpty()) {
            // Conferência descartável: mantém o tempo de resposta igual ao do e-mail que existe.
            codificador.matches(credenciais.senha(), hashFicticio);
            throw credenciaisInvalidas();
        }

        Conta conta = encontrada.get();
        if (!codificador.matches(credenciais.senha(), conta.senhaHash())) {
            throw credenciaisInvalidas();
        }

        if (conta.estado() == EstadoConta.SUSPENSO) {
            // Registrado de propósito (o card pede recusa "explícita e registrada"), sem e-mail,
            // senha, hash nem token: só o id interno da conta, que não é dado pessoal.
            log.warn("login recusado: conta suspensa (conta={})", conta.id());
            throw new AcessoNegadoException(DETALHE_CONTA_SUSPENSA);
        }

        PerfilPublico publico = perfis.buscarPublicoPorConta(conta.id()).orElseThrow(() -> new IllegalStateException(
                "conta autenticada sem perfil — a transação do cadastro não se completou"));
        return new LoginAutenticado(conta.id(), new SessaoAtualResposta(true, conta.estado(), publico));
    }

    private static NaoAutenticadoException credenciaisInvalidas() {
        return new NaoAutenticadoException(DETALHE_CREDENCIAIS_INVALIDAS);
    }
}
