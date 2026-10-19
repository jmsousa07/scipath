package com.scipath.arquivo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * O disparo <b>declarado</b> da coleta de órfãos (card E4.5, item 6 do escopo; ADR-0005 §3).
 *
 * <p><b>Por que o agendamento é uma classe, e não um {@code @Scheduled} escondido no serviço.</b> A
 * regra de coleta ({@link ColetorDeOrfaosService}) precisa ser exercitável <i>quando alguém quiser</i>
 * — pela suíte, pelo comando manual de verificação, e por qualquer outra rotina que venha a
 * precisar dela. Amarrar o horário à regra faria "coletar agora" depender de mexer no relógio.
 * Separadas, o horário é configuração ({@code scipath.coleta.agendamento}) e a regra é código
 * chamável.
 *
 * <p><b>O agendamento é explícito e visível</b>: o horário está em {@code application.yml} (padrão
 * diário às 03:30), é conferido na subida ({@link PropriedadesDeColeta} recusa expressão inválida em
 * vez de deixar o agendador em silêncio para sempre) e o resultado de cada execução sai no log com
 * o prefixo {@code [coleta-orfaos]} — o card proíbe agendamento silencioso, e um coletor que roda
 * sem deixar rastro seria indistinguível de um coletor que não roda.
 *
 * <p><b>Desligável por configuração</b> ({@code scipath.coleta.agendado}). A suíte de testes desliga
 * — um disparo de relógio real no meio de um teste é exatamente a fonte de resultado não
 * reproduzível que o §17.5 recusa —, e a ausência da propriedade deixa o agendamento <b>ligado</b>
 * (o padrão de quem sobe a API é o coletor rodando).
 */
@Component
@ConditionalOnProperty(name = "scipath.coleta.agendado", havingValue = "true", matchIfMissing = true)
public class ColetorDeOrfaosAgendado {

    private static final Logger log = LoggerFactory.getLogger(ColetorDeOrfaosAgendado.class);

    private final ColetorDeOrfaosService coletor;

    public ColetorDeOrfaosAgendado(ColetorDeOrfaosService coletor) {
        this.coletor = coletor;
    }

    /**
     * Roda a varredura no horário configurado.
     *
     * <p>A falha é registrada e engolida de propósito: o agendador do Spring continua disparando
     * depois de uma exceção, mas uma exceção que sobe daqui vira um stack trace sem contexto. O que
     * importa — qual execução falhou e por quê — já foi escrito pelo serviço; esta linha diz que a
     * varredura <i>não</i> chegou ao fim, para a próxima execução não ser a única chance.
     */
    @Scheduled(cron = "${scipath.coleta.agendamento}")
    public void coletar() {
        try {
            coletor.coletar();
        } catch (RuntimeException falha) {
            log.error("{} a varredura agendada não chegou ao fim: {}", RelatorioDeColeta.PREFIXO,
                    falha.toString(), falha);
        }
    }
}
