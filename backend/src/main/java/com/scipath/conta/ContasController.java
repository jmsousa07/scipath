package com.scipath.conta;

import com.scipath.identidade.CookiesDeSessao;
import com.scipath.identidade.SessaoService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cadastro de conta — {@code POST /api/contas} da tag {@code identidade} do contrato v0.4
 * (RF008, RN009, CA004). É a única superfície de identidade que responde sem sessão.
 *
 * <p>O controller não decide nada: ele declara a rota, exige um corpo válido
 * ({@code @Valid}) e delega ao {@link CadastroService}. O 201 vem daqui porque é o status
 * publicado no contrato para a criação — o corpo é {@link SessaoAtualResposta}, o schema
 * {@code SessaoAtual}, como o contrato exige.
 *
 * <p><b>O cadastro já sai autenticado</b> (contrato v0.4, DRS §7.2): depois de gravar conta e
 * perfil, o controller abre a sessão e escreve as duas cookies. Sem isso o usuário recém
 * cadastrado precisaria entrar logo em seguida, e o corpo {@code autenticado: true} seria
 * mentira. A sessão é aberta <b>depois</b> da transação do cadastro: o que falha não deixa
 * sessão de conta que não existe.
 *
 * <p><b>CSRF mesmo sem sessão.</b> Esta escrita exige o cabeçalho {@code X-CSRF-Token} — a
 * checagem é do {@code InterceptorDeIdentidade} e vale antes de existir sessão. O cliente
 * obtém o token na cookie {@code XSRF-TOKEN} da primeira leitura do app
 * ({@code GET /api/sessoes/atual}).
 *
 * <p><b>Erros saem todos do tratamento único</b> ({@code ApiExceptionHandler}): 422
 * {@code validacao_campo} para campo de corpo inválido (e-mail fora do formato, senha fora
 * do intervalo), 409 {@code conflito_de_unicidade} para e-mail já cadastrado, 403
 * {@code acesso_negado} para escrita sem o token CSRF, 400 {@code validacao} para corpo
 * ilegível e 415 para {@code Content-Type} não aceito. Nenhuma mensagem repete o e-mail
 * enviado nem qualquer dado de conta (RNF013).
 */
@RestController
@RequestMapping("/api")
public class ContasController {

    private final CadastroService cadastro;
    private final SessaoService sessoes;
    private final CookiesDeSessao cookies;

    public ContasController(CadastroService cadastro, SessaoService sessoes, CookiesDeSessao cookies) {
        this.cadastro = cadastro;
        this.sessoes = sessoes;
        this.cookies = cookies;
    }

    @PostMapping("/contas")
    public ResponseEntity<SessaoAtualResposta> criar(@Valid @RequestBody CadastroContaRequisicao requisicao,
            HttpServletResponse resposta) {
        ResultadoDeCadastro resultado = cadastro.cadastrar(requisicao);
        cookies.abrir(resposta, sessoes.abrir(resultado.contaId()));
        return ResponseEntity.status(HttpStatus.CREATED).body(resultado.estado());
    }
}
