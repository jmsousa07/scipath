package com.scipath.token;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Prazos do token de uso único (ADR-0003 (c) e (d)) — o único lugar que decide <b>quanto</b>
 * um token vive e de quanto é o cooldown entre dois reenvios.
 *
 * <ul>
 *   <li>{@code validadeConfirmacaoEmail} — 24 h (ADR-0003 (c)). Depois disso o link do e-mail
 *       morre, mesmo sem nunca ter sido usado: um convite de verificação esquecido na caixa de
 *       entrada não pode valer para sempre.</li>
 *   <li>{@code validadeRecuperacaoSenha} — 30 min (ADR-0003 (d)). Mais curto de propósito: é o
 *       token que troca senha, e a janela de quem tem o e-mail é o tempo que o dono leva para
 *       perceber que não pediu nada.</li>
 *   <li>{@code cooldownDeReenvio} — 60 s (ADR-0003 (c)). Vale para o reenvio de verificação
 *       (que responde 429 dentro da janela) e para a <b>solicitação de recuperação</b> (que
 *       dentro da janela simplesmente não gera mensagem e continua respondendo 202 — o
 *       contrato explica por que ali não pode haver 429: um código que só aparecesse para
 *       conta existente seria oráculo de existência, RNF013).</li>
 * </ul>
 *
 * <p>São configuração e não constantes por um motivo já exercitado no E3.3: o perfil de
 * <b>teste</b> precisa de prazos curtos para provocar expiração e cooldown em segundos, e a
 * mesma regra vale com outro número (application-test.yml). O padrão abaixo é o do ADR e vale
 * para quem não configurar nada.
 */
@Component
@ConfigurationProperties(prefix = "scipath.token")
public class PropriedadesDeToken {

    private Duration validadeConfirmacaoEmail = Duration.ofHours(24);
    private Duration validadeRecuperacaoSenha = Duration.ofMinutes(30);
    private Duration cooldownDeReenvio = Duration.ofSeconds(60);

    public Duration getValidadeConfirmacaoEmail() {
        return validadeConfirmacaoEmail;
    }

    public void setValidadeConfirmacaoEmail(Duration validadeConfirmacaoEmail) {
        this.validadeConfirmacaoEmail = positiva(validadeConfirmacaoEmail, "validade-confirmacao-email");
    }

    public Duration getValidadeRecuperacaoSenha() {
        return validadeRecuperacaoSenha;
    }

    public void setValidadeRecuperacaoSenha(Duration validadeRecuperacaoSenha) {
        this.validadeRecuperacaoSenha = positiva(validadeRecuperacaoSenha, "validade-recuperacao-senha");
    }

    public Duration getCooldownDeReenvio() {
        return cooldownDeReenvio;
    }

    public void setCooldownDeReenvio(Duration cooldownDeReenvio) {
        this.cooldownDeReenvio = positiva(cooldownDeReenvio, "cooldown-reenvio");
    }

    /**
     * Validade do tipo pedido — a única tradução de {@link TipoDeToken} para prazo.
     *
     * <p>Existe para que o serviço não repita um {@code switch} sobre o tipo em cada ponto de
     * decisão, e para que acrescentar um terceiro tipo seja uma mudança com um lugar só.
     */
    public Duration validadeDe(TipoDeToken tipo) {
        return switch (tipo) {
            case CONFIRMACAO_EMAIL -> validadeConfirmacaoEmail;
            case RECUPERACAO_SENHA -> validadeRecuperacaoSenha;
        };
    }

    private static Duration positiva(Duration valor, String chave) {
        if (valor == null || valor.isZero() || valor.isNegative()) {
            throw new IllegalArgumentException("scipath.token." + chave + " deve ser um prazo positivo");
        }
        return valor;
    }
}
