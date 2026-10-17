package com.scipath.token;

import com.scipath.identidade.IdentidadeAutenticada;
import com.scipath.identidade.SessaoAutenticada;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Verificação de e-mail — {@code POST /verificacao-email/reenvios} e
 * {@code POST /verificacao-email/confirmacoes} do contrato v0.4 (ADR-0003 (c), (g) e (h)).
 *
 * <p><b>As duas rotas exigem coisas diferentes, e é o contrato que decide.</b> O reenvio exige
 * <b>sessão</b> (o destinatário é o e-mail da própria conta — não há e-mail no corpo, e é isso
 * que impede o endpoint de virar consulta de existência de conta) e, sendo escrita, o
 * {@code X-CSRF-Token}: sem sessão a resposta é 401 e, havendo sessão sem o cabeçalho, 403 —
 * ambos antes de qualquer regra de negócio, pelo {@code InterceptorDeIdentidade}. A confirmação
 * <b>não exige nem uma coisa nem outra</b>: quem clica no link pode estar em outro navegador,
 * sem cookie nenhum, e o segredo vai no corpo.
 *
 * <p>O controller é fino de propósito: ele declara a rota, o status e o corpo, e delega. O 429
 * do cooldown, o 422 do token recusado e o 500 da falha de envio nascem do serviço e saem pelo
 * tratamento único de erro — não há aqui um segundo lugar montando resposta de erro.
 */
@RestController
@RequestMapping("/api")
public class VerificacaoEmailController {

    private final VerificacaoDeEmailService verificacao;

    public VerificacaoEmailController(VerificacaoDeEmailService verificacao) {
        this.verificacao = verificacao;
    }

    /**
     * Reenvia a confirmação para o e-mail da conta da sessão. <b>202</b> com os dois instantes do
     * cooldown (contrato v0.4), <b>429</b> dentro dele e <b>500</b> se o canal recusar a mensagem.
     */
    @PostMapping("/verificacao-email/reenvios")
    public ResponseEntity<ReenvioDeVerificacaoResposta> reenviar(
            @IdentidadeAutenticada SessaoAutenticada sessao) {
        return ResponseEntity.accepted().body(verificacao.reenviar(sessao.contaId()));
    }

    /**
     * Confirma o e-mail e ativa a conta. <b>204</b> sem corpo; token inválido, expirado ou já
     * consumido → <b>422</b> com o mesmo corpo nos três casos.
     */
    @PostMapping("/verificacao-email/confirmacoes")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmar(@Valid @RequestBody ConfirmacaoDeTokenRequisicao requisicao) {
        verificacao.confirmar(requisicao.token());
    }
}
