package com.scipath.identidade;

import com.scipath.conta.EstadoConta;
import com.scipath.error.AcessoNegadoException;
import com.scipath.error.NaoAutenticadoException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A sessão de identidade: abrir, resolver e revogar (ADR-0003 (e), contrato v0.4).
 *
 * <p><b>Onde os prazos são julgados.</b> O repositório devolve os instantes gravados; quem
 * decide se a sessão ainda vale é este serviço, e num só lugar. Os dois prazos são
 * independentes:
 * <ul>
 *   <li><b>Limite absoluto</b> ({@code expira_em}) — não se estende. Passado, a sessão morre
 *       mesmo com uso recente.</li>
 *   <li><b>Inatividade</b> ({@code ultimo_uso_em + duracaoInatividade}) — desliza a cada uso
 *       aceito. Passado, a sessão morre mesmo longe do limite absoluto.</li>
 * </ul>
 * A ordem das duas checagens é indiferente para o resultado (qualquer uma que falhe recusa),
 * mas a mensagem é sempre a mesma: <b>401 {@code nao_autenticado}</b>, sem dizer qual prazo
 * venceu — o cliente não ganha nada com isso e o contrato não distingue os casos.
 *
 * <p><b>Conta suspensa não mantém sessão.</b> O schema {@code SessaoAtual} do contrato
 * enumera só {@code PENDENTE_VERIFICACAO} e {@code ATIVO} e diz por quê: "conta suspensa não
 * mantém sessão (o login a recusa com 403)". A suspensão pode acontecer <i>durante</i> a
 * sessão (é o estado que o ADR-0003 (b) reserva para abuso), e por isso o estado é lido junto
 * com a sessão, a cada requisição — não no momento em que ela foi aberta. A sessão de uma
 * conta suspensa simplesmente não resolve: 401 na rota privada, {@code autenticado: false} em
 * {@code GET /sessoes/atual}.
 *
 * <p><b>Nada de token em log</b> (RNF009): o valor da cookie não é registrado, nem no sucesso
 * nem na recusa. O log de uma sessão recusada cita, no máximo, o {@code id} da sessão.
 */
@Service
public class SessaoService {

    private static final Logger log = LoggerFactory.getLogger(SessaoService.class);

    private final SessaoRepositorio sessoes;
    private final TokensOpacos tokens;
    private final PropriedadesDeSessao propriedades;

    public SessaoService(SessaoRepositorio sessoes, TokensOpacos tokens, PropriedadesDeSessao propriedades) {
        this.sessoes = sessoes;
        this.tokens = tokens;
        this.propriedades = propriedades;
    }

    /**
     * Abre a sessão de uma conta que já provou identidade (login ou cadastro) e devolve os
     * valores que vão para as cookies.
     *
     * <p>O token nasce aqui e só existe em claro nesta resposta: o banco recebe o SHA-256
     * dele. Quem chama tem a obrigação de escrever as duas cookies — a de sessão
     * ({@code HttpOnly}) e a de CSRF (legível pelo frontend) — no <b>mesmo</b> momento, senão
     * o cliente fica com uma sessão que não consegue usar (a escrita exige o token CSRF).
     *
     * <p>O limite absoluto é calculado <b>uma</b> vez, no nascimento: renovar a sessão é abrir
     * outra (o que o login faz), nunca esticar esta.
     */
    @Transactional
    public SessaoAberta abrir(UUID contaId) {
        Instant agora = Instant.now();
        Instant expiraEm = agora.plus(propriedades.getDuracaoAbsoluta());
        String token = tokens.gerar();
        String csrf = tokens.gerar();
        sessoes.inserir(contaId, TokensOpacos.hash(token), csrf, agora, expiraEm);
        return new SessaoAberta(token, csrf, expiraEm);
    }

    /**
     * Sessão da cookie apresentada, se ela ainda valer — o ponto único por onde toda
     * requisição autenticada passa.
     *
     * <p>Devolve {@link Optional#empty()} para cookie ausente, valor forjado ou alterado (o
     * hash não encontra linha: <b>não existe</b> sessão montável a partir de valor inventado),
     * sessão revogada, prazo vencido ou conta suspensa. Os quatro casos são indistinguíveis
     * para quem está do outro lado — é o mesmo 401, sem oráculo de qual deles foi.
     *
     * <p>O uso aceito desloca {@code ultimo_uso_em}, mas não a cada requisição: só quando já
     * passou {@link PropriedadesDeSessao#janelaDeToque()}. Sem essa janela, toda leitura
     * autenticada viraria escrita.
     */
    @Transactional
    public Optional<SessaoResolvida> resolver(String tokenDaCookie) {
        if (tokenDaCookie == null || tokenDaCookie.isBlank()) {
            return Optional.empty();
        }
        Optional<SessaoResolvida> encontrada = sessoes.buscarResolvida(TokensOpacos.hash(tokenDaCookie));
        if (encontrada.isEmpty()) {
            return Optional.empty();
        }
        SessaoResolvida sessao = encontrada.get();
        Instant agora = Instant.now();

        if (!agora.isBefore(sessao.expiraEm())) {
            log.info("sessão recusada pelo limite absoluto (sessao={})", sessao.sessaoId());
            return Optional.empty();
        }
        if (agora.isAfter(sessao.ultimoUsoEm().plus(propriedades.getDuracaoInatividade()))) {
            log.info("sessão recusada por inatividade (sessao={})", sessao.sessaoId());
            return Optional.empty();
        }
        if (sessao.estadoConta() == EstadoConta.SUSPENSO) {
            log.warn("sessão recusada: conta suspensa (conta={})", sessao.contaId());
            return Optional.empty();
        }
        if (agora.isAfter(sessao.ultimoUsoEm().plus(propriedades.janelaDeToque()))) {
            sessoes.registrarUso(sessao.sessaoId(), agora);
        }
        return Optional.of(sessao);
    }

    /**
     * Revoga a sessão da cookie apresentada — no servidor, não só no cliente.
     *
     * <p>Apagar a linha é o que faz o valor reapresentado depois do logout não autenticar
     * nada: não há como o cliente "guardar" uma sessão que o servidor não conhece mais. Não
     * é erro revogar o que já não existia — o logout é idempotente por contrato.
     *
     * @return {@code true} quando havia sessão viva e ela foi revogada
     */
    @Transactional
    public boolean encerrar(String tokenDaCookie) {
        if (tokenDaCookie == null || tokenDaCookie.isBlank()) {
            return false;
        }
        return sessoes.revogarPorTokenHash(TokensOpacos.hash(tokenDaCookie)) > 0;
    }

    /**
     * Encerra <b>todas</b> as sessões abertas de uma conta — o que a recuperação de acesso
     * (E3.4) chama depois de trocar a senha (RNF012).
     *
     * <p><b>Por que isto é do serviço de sessão, e não de quem trocou a senha.</b> A regra "o que
     * é uma sessão viva" mora neste pacote; uma segunda classe apagando linha de {@code sessao}
     * seria um segundo dono da mesma tabela. Quem troca a senha precisa de um efeito —
     * "nenhuma cookie anterior autentica mais" —, e o efeito é este método.
     *
     * <p>Não altera a sessão corrente nem distingue uma da outra: a decisão da recuperação de
     * acesso é derrubar <b>todas</b>, inclusive a de quem está trocando a senha (o segredo ali é
     * o token do e-mail, não a cookie). Quem trocou a senha entra de novo.
     *
     * @return quantas sessões foram encerradas (0 = nenhuma estava aberta)
     */
    @Transactional
    public int encerrarTodasDaConta(UUID contaId) {
        return sessoes.revogarPorConta(contaId);
    }
}
