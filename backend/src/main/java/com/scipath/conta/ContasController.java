package com.scipath.conta;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cadastro de conta — {@code POST /api/contas} da tag {@code identidade} do contrato v0.4
 * (RF008, RN009, CA004). É o primeiro endpoint de escrita do produto e a única superfície
 * de identidade que responde sem sessão.
 *
 * <p>O controller não decide nada: ele declara a rota, exige um corpo válido
 * ({@code @Valid}) e delega ao {@link CadastroService}. O 201 vem daqui porque é o status
 * publicado no contrato para a criação — o corpo é {@link SessaoAtualResposta}, o schema
 * {@code SessaoAtual}, como o contrato exige.
 *
 * <p><b>Erros saem todos do tratamento único</b> ({@code ApiExceptionHandler}): 422
 * {@code validacao_campo} para campo de corpo inválido (e-mail fora do formato, senha fora
 * do intervalo), 409 {@code conflito_de_unicidade} para e-mail já cadastrado, 400
 * {@code validacao} para corpo ilegível e 415 para {@code Content-Type} não aceito. Nenhuma
 * mensagem repete o e-mail enviado nem qualquer dado de conta (RNF013).
 *
 * <p><b>Ainda não implementado neste card:</b> o {@code Set-Cookie} da sessão e a exigência
 * de {@code X-CSRF-Token} — ambos são o E3.3 (a sessão é tabela nova, migração de outro
 * card). O corpo de sucesso já é o do contrato.
 */
@RestController
@RequestMapping("/api")
public class ContasController {

    private final CadastroService cadastro;

    public ContasController(CadastroService cadastro) {
        this.cadastro = cadastro;
    }

    @PostMapping("/contas")
    @ResponseStatus(HttpStatus.CREATED)
    public SessaoAtualResposta criar(@Valid @RequestBody CadastroContaRequisicao requisicao) {
        return cadastro.cadastrar(requisicao);
    }
}
