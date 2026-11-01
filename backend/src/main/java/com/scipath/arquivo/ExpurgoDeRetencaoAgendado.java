package com.scipath.arquivo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Disparo diário, configurável, da rotina de expurgo de retenção (E5.5). */
@Component
@ConditionalOnProperty(name = "scipath.expurgo.agendado", havingValue = "true", matchIfMissing = true)
public class ExpurgoDeRetencaoAgendado {

    private static final Logger log = LoggerFactory.getLogger(ExpurgoDeRetencaoAgendado.class);
    private final ExpurgoDeRetencaoService expurgo;

    public ExpurgoDeRetencaoAgendado(ExpurgoDeRetencaoService expurgo) {
        this.expurgo = expurgo;
    }

    @Scheduled(cron = "${scipath.expurgo.agendamento}")
    public void executar() {
        try {
            expurgo.expurgar();
        } catch (RuntimeException falha) {
            log.error("{} a execução agendada não chegou ao fim: {}", RelatorioDeExpurgo.PREFIXO,
                    falha.toString(), falha);
        }
    }
}
