package com.scipath.arquivo;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

/** Configuração da rotina de retenção de binários de materiais excluídos (E5.5). */
@Component
@ConfigurationProperties(prefix = "scipath.expurgo")
public class PropriedadesDeExpurgo {

    private Duration janela = Duration.ofDays(7);
    private String agendamento = "0 45 3 * * *";

    public Duration getJanela() {
        return janela;
    }

    /** Recusa uma janela sem prazo de segurança. */
    public void setJanela(Duration janela) {
        if (janela == null || janela.isZero() || janela.isNegative()) {
            throw new IllegalArgumentException("scipath.expurgo.janela precisa ser um prazo positivo"
                    + " (ex.: 7d)");
        }
        this.janela = janela;
    }

    public String getAgendamento() {
        return agendamento;
    }

    /** Falha na subida em vez de deixar um agendamento inválido sem disparar. */
    public void setAgendamento(String agendamento) {
        if (agendamento == null || agendamento.isBlank()) {
            throw new IllegalArgumentException("scipath.expurgo.agendamento não pode ficar vazio");
        }
        try {
            CronExpression.parse(agendamento);
        } catch (IllegalArgumentException invalida) {
            throw new IllegalArgumentException("scipath.expurgo.agendamento não é uma expressão de"
                    + " agendamento válida (6 campos, com segundos — ex.: '0 45 3 * * *'): "
                    + agendamento, invalida);
        }
        this.agendamento = agendamento;
    }

    /** Forma curta e estável para a linha de auditoria (`7d`, `24h`, `30m` ou `45s`). */
    public String janelaFormatada() {
        long segundos = janela.getSeconds();
        if (segundos % 86_400 == 0 && janela.getNano() == 0) {
            return segundos / 86_400 + "d";
        }
        if (segundos % 3_600 == 0 && janela.getNano() == 0) {
            return segundos / 3_600 + "h";
        }
        if (segundos % 60 == 0 && janela.getNano() == 0) {
            return segundos / 60 + "m";
        }
        if (janela.getNano() == 0) {
            return segundos + "s";
        }
        return janela.toString();
    }
}
