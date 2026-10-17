package com.scipath.email;

import com.scipath.token.TipoDeToken;
import org.springframework.stereotype.Component;

/**
 * Monta a mensagem de cada tipo de token: assunto e <b>link</b> (ADR-0003 (c) e (d)).
 *
 * <p>Fica no canal, e não no serviço de identidade, porque assunto e endereço de página são
 * decisão de <b>apresentação</b>: o serviço sabe que precisa mandar um token de confirmação
 * para um destinatário; qual é o texto e para onde o clique leva é o que este componente
 * decide. Com isso o serviço não conhece URL nenhuma, e trocar a rota da tela (E3.6/E3.7) não
 * encosta na regra de negócio.
 *
 * <p><b>O token entra no fragmento da URL</b> ({@code #token=...}), nunca na query string — ver
 * {@link PropriedadesDeEmail}: fragmento não é enviado ao servidor e portanto não entra em log
 * de acesso, que é exatamente o que RNF009 proíbe. O link é uma constante por tipo: não há
 * endereço montado a partir de dado da requisição (nada de redirecionamento aberto).
 */
@Component
public class ModelosDeEmail {

    /** Assunto da confirmação de e-mail — texto fixo, sem dado do usuário. */
    static final String ASSUNTO_CONFIRMACAO_EMAIL = "Confirme seu e-mail no SciPath";

    /** Assunto da recuperação de acesso — texto fixo, sem dado do usuário. */
    static final String ASSUNTO_RECUPERACAO_SENHA = "Recuperação de acesso do SciPath";

    private final PropriedadesDeEmail propriedades;

    public ModelosDeEmail(PropriedadesDeEmail propriedades) {
        this.propriedades = propriedades;
    }

    /**
     * Mensagem do tipo pedido, com o token já no fragmento do link.
     *
     * @param tipo         qual dos dois fluxos (o mesmo tipo que a linha do token vai gravar)
     * @param destinatario endereço da conta; o serviço o obtém do banco ou da requisição
     * @param token        valor em claro recém-gerado — existe só nesta mensagem e no outbox
     */
    public MensagemDeEmail para(TipoDeToken tipo, String destinatario, String token) {
        return switch (tipo) {
            case CONFIRMACAO_EMAIL -> new MensagemDeEmail(destinatario, tipo, ASSUNTO_CONFIRMACAO_EMAIL,
                    propriedades.getLink().montar(propriedades.getLink().getConfirmacaoEmail(), token));
            case RECUPERACAO_SENHA -> new MensagemDeEmail(destinatario, tipo, ASSUNTO_RECUPERACAO_SENHA,
                    propriedades.getLink().montar(propriedades.getLink().getRecuperacaoSenha(), token));
        };
    }
}
