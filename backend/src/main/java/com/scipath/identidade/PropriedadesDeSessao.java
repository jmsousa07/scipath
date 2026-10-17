package com.scipath.identidade;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Durações da sessão (ADR-0003 (e), contrato v0.4) — o único lugar que decide <b>quanto</b>
 * uma sessão vive.
 *
 * <p>Os dois prazos são independentes e os dois são aplicados no servidor:
 * <ul>
 *   <li>{@code duracaoInatividade} (padrão 12 h) — desliza a cada uso aceito. É o prazo que
 *       protege a máquina esquecida aberta em biblioteca.</li>
 *   <li>{@code duracaoAbsoluta} (padrão 7 dias) — <b>não</b> desliza. É o teto de vida da
 *       sessão mesmo com uso contínuo, e o valor que a cookie anuncia em {@code Max-Age}.</li>
 * </ul>
 *
 * <p>São configuração e não constantes porque o perfil de <b>teste</b> precisa de prazos
 * curtos para provocar a expiração em segundos, e porque operar o produto com outro prazo é
 * ajuste de configuração — não recompilação. O padrão vale para quem não configurar nada, e
 * os valores vêm de {@code scipath.sessao.*} (application.yml; application-test.yml no perfil
 * de teste).
 *
 * <p>Não há {@code @ConfigurationPropertiesScan}: o bean é declarado aqui com
 * {@code @Component}, o que mantém a classe de configuração junto da regra que ela governa.
 */
@Component
@ConfigurationProperties(prefix = "scipath.sessao")
public class PropriedadesDeSessao {

    private Duration duracaoInatividade = Duration.ofHours(12);
    private Duration duracaoAbsoluta = Duration.ofDays(7);

    public Duration getDuracaoInatividade() {
        return duracaoInatividade;
    }

    public void setDuracaoInatividade(Duration duracaoInatividade) {
        this.duracaoInatividade = positiva(duracaoInatividade, "duracao-inatividade");
    }

    public Duration getDuracaoAbsoluta() {
        return duracaoAbsoluta;
    }

    public void setDuracaoAbsoluta(Duration duracaoAbsoluta) {
        this.duracaoAbsoluta = positiva(duracaoAbsoluta, "duracao-absoluta");
    }

    /**
     * Janela mínima entre duas gravações de {@code ultimo_uso_em} — um quarto do prazo de
     * inatividade.
     *
     * <p>Sem isso, toda requisição autenticada viraria um {@code UPDATE}: a sessão deslizaria
     * a cada clique. Com um quarto do prazo, o deslize é imperceptível para o usuário (o
     * prazo só "perde" até 3 h de 12 h no pior caso) e a escrita cai para poucas por dia por
     * usuário ativo. Em teste, com prazos de segundos, a janela acompanha — é a mesma regra.
     */
    public Duration janelaDeToque() {
        return duracaoInatividade.dividedBy(4);
    }

    /** Prazo que a cookie de sessão anuncia em {@code Max-Age} — o teto absoluto, não a inatividade. */
    public Duration duracaoDaCookie() {
        return duracaoAbsoluta;
    }

    private static Duration positiva(Duration valor, String nome) {
        if (valor == null || valor.isZero() || valor.isNegative()) {
            throw new IllegalArgumentException("scipath.sessao." + nome + " deve ser um prazo positivo");
        }
        return valor;
    }
}
