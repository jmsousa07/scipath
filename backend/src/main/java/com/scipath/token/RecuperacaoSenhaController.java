package com.scipath.token;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Recuperação de acesso — {@code POST /recuperacao-senha/solicitacoes} e
 * {@code POST /recuperacao-senha/confirmacoes} do contrato v0.4 (RF010, ADR-0003 (d)).
 *
 * <p><b>Nenhuma das duas rotas exige sessão</b> — o segredo é o token, não a cookie: quem
 * esqueceu a senha não está autenticado, e o link pode ser aberto em outro navegador. Por isso
 * o controller não declara {@link com.scipath.identidade.IdentidadeAutenticada} em nenhum
 * parâmetro, e o interceptor não responde 401 aqui.
 *
 * <p><b>A solicitação não exige {@code X-CSRF-Token}</b>, e é uma escolha lida do contrato, não
 * um esquecimento: o parâmetro {@code TokenCsrf} do contrato enumera as escritas que o exigem
 * (cadastro, login, logout, editar perfil e reenvio de verificação) e o único 403 publicado para
 * este fluxo seria um caminho de recusa que o contrato não descreve — o frontend não o trata e a
 * página de recuperação viraria beco sem saída em navegador sem a cookie {@code XSRF-TOKEN}. O
 * que o CSRF protegeria aqui é o envio de um e-mail de recuperação a um usuário que não pediu —
 * abuso de baixo impacto, no mesmo nível do que a recuperação já aceita de quem tem o endereço —
 * e o contrato decidiu não pagar esse preço. A confirmação também não o exige (o segredo vai no
 * corpo). Ver {@code InterceptorDeIdentidade#ESCRITAS_SEM_CSRF}.
 *
 * <p>A solicitação responde <b>202 sem corpo</b> exista ou não a conta, e a confirmação responde
 * <b>204 sem corpo</b> — nenhuma das duas devolve dado que distinga uma conta da outra
 * (RNF013).
 */
@RestController
@RequestMapping("/api")
public class RecuperacaoSenhaController {

    private final RecuperacaoDeAcessoService recuperacao;

    public RecuperacaoSenhaController(RecuperacaoDeAcessoService recuperacao) {
        this.recuperacao = recuperacao;
    }

    /** Pede o link de recuperação. <b>202 sempre</b>, com ou sem conta (RNF013). */
    @PostMapping("/recuperacao-senha/solicitacoes")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void solicitar(@Valid @RequestBody RecuperacaoSolicitacaoRequisicao requisicao) {
        recuperacao.solicitar(requisicao.email());
    }

    /**
     * Redefine a senha com o token do e-mail e invalida as sessões abertas da conta
     * (RNF012). <b>204</b> sem corpo; token recusado ou senha fora do intervalo → <b>422</b>.
     */
    @PostMapping("/recuperacao-senha/confirmacoes")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmar(@Valid @RequestBody RecuperacaoConfirmacaoRequisicao requisicao) {
        recuperacao.confirmar(requisicao.token(), requisicao.novaSenha());
    }
}
