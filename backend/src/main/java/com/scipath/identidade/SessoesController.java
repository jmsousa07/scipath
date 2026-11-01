package com.scipath.identidade;

import com.scipath.conta.SessaoAtualResposta;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sessão de identidade — {@code POST /sessoes}, {@code GET /sessoes/atual} e
 * {@code DELETE /sessoes/atual} do contrato v0.5 (RF009, CA004).
 *
 * <p>Os três endpoints são finos de propósito: conferir credenciais é
 * {@link AutenticacaoService}, abrir/resolver/revogar é {@link SessaoService} e escrever
 * cookie é {@link CookiesDeSessao}. O controller compõe e declara a rota.
 *
 * <p><b>Login</b> (200): corpo {@code SessaoAtual} e as duas cookies. A sessão anterior, se o
 * cliente apresentou uma válida, é revogada antes — a rotação de sessão no login é o que
 * impede que uma sessão obtida antes de autenticar continue valendo depois (session
 * fixation). O contrato descreve isso como "reemite a sessão anterior, se havia".
 *
 * <p><b>Estado da sessão</b> (200 sempre): {@code autenticado: false} para o visitante não é
 * erro, é o estado normal (RNF004), e por isso o endpoint recebe a identidade como
 * {@code Optional}. O corpo nunca traz e-mail, hash, token nem identificador de conta
 * (RNF013) — nem para o dono.
 *
 * <p><b>Logout</b> (204 sempre): idempotente por contrato. Sem sessão viva, a resposta
 * continua 204 — sair não depende de a sessão ainda estar viva, senão a tela mostraria erro
 * por um logout que já aconteceu (§15.3). A escrita exige o {@code X-CSRF-Token}, checado pelo
 * interceptor.
 */
@RestController
@RequestMapping("/api")
public class SessoesController {

    private final AutenticacaoService autenticacao;
    private final SessaoService sessoes;
    private final CookiesDeSessao cookies;

    public SessoesController(AutenticacaoService autenticacao, SessaoService sessoes, CookiesDeSessao cookies) {
        this.autenticacao = autenticacao;
        this.sessoes = sessoes;
        this.cookies = cookies;
    }

    @PostMapping("/sessoes")
    public SessaoAtualResposta abrir(@Valid @RequestBody CredenciaisRequisicao credenciais,
            HttpServletRequest requisicao, HttpServletResponse resposta) {
        LoginAutenticado login = autenticacao.autenticar(credenciais);

        // Rotação: a sessão que o cliente já apresentava morre aqui. Só depois a nova nasce.
        sessoes.encerrar(CookiesDeSessao.ler(requisicao, CookiesDeSessao.COOKIE_DE_SESSAO).orElse(null));

        cookies.abrir(resposta, sessoes.abrir(login.contaId()));
        return login.estado();
    }

    @GetMapping("/sessoes/atual")
    public SessaoAtualResposta atual(@IdentidadeAutenticada Optional<SessaoAutenticada> sessao) {
        return sessao.map(identidade -> new SessaoAtualResposta(true, identidade.estadoConta(), identidade.perfil()))
                .orElseGet(() -> new SessaoAtualResposta(false, null, null));
    }

    @DeleteMapping("/sessoes/atual")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void encerrar(HttpServletRequest requisicao, HttpServletResponse resposta) {
        sessoes.encerrar(CookiesDeSessao.ler(requisicao, CookiesDeSessao.COOKIE_DE_SESSAO).orElse(null));
        cookies.encerrar(resposta);
    }
}
