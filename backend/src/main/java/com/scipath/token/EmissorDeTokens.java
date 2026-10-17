package com.scipath.token;

import com.scipath.email.EmailSender;
import com.scipath.email.MensagemDeEmail;
import com.scipath.email.ModelosDeEmail;
import com.scipath.error.ValidacaoDeCampoException;
import com.scipath.identidade.TokensOpacos;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Emite e consome o token de uso único (ADR-0003 (c) e (d)) — o ponto único das duas regras
 * que os dois fluxos (verificação de e-mail e recuperação de acesso) têm em comum.
 *
 * <p><b>A ordem: gerar, <i>enviar</i>, gravar.</b> A linha da tabela é gravada depois de a
 * mensagem sair, e é deliberado. O ADR-0003 (g) exige que uma falha de envio deixe a conta em
 * {@code PENDENTE_VERIFICACAO} e <b>nada marcado como enviado</b> — com esta ordem, "marcado"
 * é exatamente a existência da linha, e uma falha de canal não deixa rastro nenhum: nenhum
 * token que ninguém recebeu, nenhum cooldown de um e-mail que não saiu (o reenvio continua
 * disponível na hora, como o ADR-0003 (h) quer). A ordem inversa (gravar, enviar, apagar no
 * erro) precisaria de transação em volta de uma chamada de rede — o que segura uma conexão do
 * banco durante todo o envio — para chegar ao mesmo estado.
 *
 * <p>O risco remanescente é o inverso e é menor: se a gravação falhar depois do envio, existe um
 * link no e-mail que não corresponde a nenhuma linha — e um token desconhecido é <b>recusado</b>
 * (o consumo não encontra nada). Falha fechada.
 *
 * <p><b>A validade nasce de um instante só.</b> {@code criadoEm} e {@code expiraEm} são
 * calculados juntos aqui e gravados como vieram; o {@code CHECK} do V1 e a evidência podem
 * mostrar a duração aplicada sem depender do relógio do banco (mesmo padrão da {@code sessao},
 * V4).
 *
 * <p><b>Token nunca em log</b> (RNF009): esta classe registra no máximo o tipo e o
 * {@code id} interno da conta, jamais o valor gerado, o hash ou o destinatário.
 */
@Component
public class EmissorDeTokens {

    /** Texto único da recusa por token — inválido, expirado e já consumido respondem isto. */
    public static final String MOTIVO_DE_TOKEN_INVALIDO = "token inválido ou expirado";

    /** Campo do corpo que o 422 aponta — o schema do contrato tem um só: {@code token}. */
    public static final String CAMPO_DO_TOKEN = "token";

    private final TokenRepositorio tokens;
    private final TokensOpacos opacos;
    private final EmailSender email;
    private final ModelosDeEmail modelos;
    private final PropriedadesDeToken propriedades;

    public EmissorDeTokens(TokenRepositorio tokens, TokensOpacos opacos, EmailSender email,
            ModelosDeEmail modelos, PropriedadesDeToken propriedades) {
        this.tokens = tokens;
        this.opacos = opacos;
        this.email = email;
        this.modelos = modelos;
        this.propriedades = propriedades;
    }

    /**
     * Gera o token, entrega a mensagem e só então grava o hash.
     *
     * @param contaId      conta dona do token (a FK do V1 — token sem dono não existe)
     * @param tipo         qual dos dois fluxos; decide validade e modelo de mensagem
     * @param destinatario endereço da conta, lido do banco ou da requisição por quem chama
     * @return os instantes aplicados: o emissor do chamador precisa do {@code criadoEm} para
     *         responder quando o próximo reenvio é aceito
     * @throws com.scipath.email.FalhaDeEnvioDeEmailException a mensagem não saiu — nada foi gravado
     */
    public Emissao emitir(UUID contaId, TipoDeToken tipo, String destinatario) {
        Instant criadoEm = Instant.now();
        Instant expiraEm = criadoEm.plus(propriedades.validadeDe(tipo));

        // O valor em claro nasce aqui e vive só nesta chamada: ele vai para a mensagem e o que
        // a tabela recebe é o SHA-256 (RNF009, ADR-0003 (c)).
        String token = opacos.gerar();
        MensagemDeEmail mensagem = modelos.para(tipo, destinatario, token);
        email.enviar(mensagem);

        tokens.inserir(contaId, TokensOpacos.hash(token), tipo, criadoEm, expiraEm);
        return new Emissao(criadoEm, expiraEm);
    }

    /** Instante da última emissão daquele tipo — base do cooldown de reenvio. */
    public Optional<Instant> ultimaEmissao(UUID contaId, TipoDeToken tipo) {
        return tokens.ultimaEmissao(contaId, tipo);
    }

    /**
     * Consome o token (uso único, no banco) e devolve a conta dona.
     *
     * <p>Um só ponto de recusa para os dois fluxos: inválido, expirado e já consumido são o
     * mesmo {@link Optional#empty()} no repositório e portanto a mesma exceção aqui — 422
     * {@code validacao_campo} no campo {@code token}, com o texto do contrato. Não há caminho em
     * que um dos fluxos responda algo mais específico: a mensagem é uma constante, não um texto
     * montado por caso.
     *
     * @throws ValidacaoDeCampoException token que não vale (422, sem distinguir o motivo)
     */
    public UUID consumir(String token, TipoDeToken tipo) {
        return tokens.consumir(TokensOpacos.hash(token), tipo, Instant.now())
                .orElseThrow(() -> new ValidacaoDeCampoException(CAMPO_DO_TOKEN, MOTIVO_DE_TOKEN_INVALIDO));
    }

    /**
     * O que a emissão produziu: quando o token nasceu e quando ele morre.
     *
     * <p>Os dois instantes — e não o token — é o que o chamador precisa: o valor em claro só
     * interessa à mensagem, e nenhuma assinatura deste pacote o devolve.
     */
    public record Emissao(Instant criadoEm, Instant expiraEm) {
    }
}
