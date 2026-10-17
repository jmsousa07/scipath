package com.scipath.email;

import com.scipath.token.TipoDeToken;

/**
 * A mensagem que sai pelo canal de e-mail: destinatário, assunto e o <b>link</b> com o token
 * (ADR-0003 (c), (d) e (f)).
 *
 * <p><b>O link, e nada além do link.</b> O token em claro tem de existir em algum lugar para o
 * usuário clicar — e esse lugar é aqui. O que o record <b>não</b> carrega é o hash do token, o
 * identificador interno da conta ou qualquer outro dado da identidade: o outbox publica o que
 * este record tem, e o ADR-0003 (f) admite, no outbox, exatamente o link. Corpo de e-mail com
 * texto adicional seria dado a mais em claro; a mensagem é o essencial.
 *
 * <p>O {@link TipoDeToken} viaja junto porque é ele que diz <b>qual</b> link é (confirmação de
 * e-mail ou recuperação de acesso) — o canal não precisa adivinhar pelo destinatário, e a
 * implementação de outbox registra o tipo sem ter de interpretar a URL.
 *
 * <p>Nenhum {@code toString()} deste record é registrado em log (RNF009): o {@code link} contém
 * o token em claro.
 */
public record MensagemDeEmail(
        String destinatario,
        TipoDeToken tipo,
        String assunto,
        String link) {
}
