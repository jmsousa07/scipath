package com.scipath.arquivo;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

/**
 * Parâmetros do coletor de arquivos órfãos (card E4.5, ADR-0005 §3).
 *
 * <p><b>Nada aqui é constante no código.</b> A janela de segurança e o horário da coleta são
 * configuração, como o limite de upload (E4.2) e o diretório de armazenamento (E4.3): trocar o
 * número é uma linha de {@code .env} ou de {@code application.yml}, não um card. O §5.4 pede
 * exatamente isso para os parâmetros do sistema, e um coletor com prazo cravado no código é um
 * coletor que ninguém consegue ajustar sem recompilar.
 *
 * <p><b>A configuração é conferida na subida, não confiada.</b> Dois valores errados aqui não dão
 * erro nenhum — eles produzem um coletor que <i>parece</i> funcionar:
 *
 * <ul>
 *   <li><b>janela vazia ou negativa</b> — apagaria arquivo sem janela de segurança alguma, o
 *       oposto do que o RN012 pede ("operações de limpeza devem ser seguras"); é recusada com o
 *       motivo escrito;</li>
 *   <li><b>expressão de agendamento inválida</b> — o agendador do Spring não reclama: ele
 *       simplesmente nunca dispara, e o coletor fica declarado e morto ao mesmo tempo. O
 *       {@link CronExpression#parse} transforma isso em falha de subida, que é quando alguém
 *       pode consertar.</li>
 * </ul>
 */
@Component
@ConfigurationProperties(prefix = "scipath.coleta")
public class PropriedadesDeColeta {

    /**
     * Janela de segurança: só é coletado o arquivo sem material <b>mais antigo</b> que isto.
     *
     * <p>O padrão é {@code 24h} e é conservador de propósito. A marcação de órfão (E4.4) acontece
     * dentro da própria requisição de publicação que falhou, então qualquer órfão mais velho que
     * uma requisição já está morto — a janela não existe para esperar "algo acontecer", e sim para
     * não apagar durante uma publicação em voo nem por causa de um relógio torto entre a aplicação
     * e o banco. Um dia inteiro é folga suficiente e continua muito abaixo dos 7 dias de retenção
     * do material {@code EXCLUIDO} (ADR-0005 §3), então as duas rotinas nunca competem.
     */
    private Duration janela = Duration.ofHours(24);

    /**
     * Expressão de agendamento do Spring (6 campos, com segundos) da coleta diária. O padrão roda
     * todo dia às 03:30 — horário de baixo uso, o mesmo tipo de escolha que o ADR-0005 §3 descreve
     * como "coletor diário".
     */
    private String agendamento = "0 30 3 * * *";

    /**
     * Executa a coleta <b>uma vez na subida</b> e segue. É o que dá ao operador um comando manual
     * de verificação ({@code make coleta:orfaos}) sem um segundo processo, sem cron e sem console
     * de administração (fora de escopo no MVP). Desligado por padrão: quem sobe a API normalmente
     * não quer uma varredura a cada reinício.
     */
    private boolean aoIniciar = false;

    public Duration getJanela() {
        return janela;
    }

    /**
     * @throws IllegalArgumentException se o prazo for vazio ou negativo — um coletor sem janela de
     *         segurança é uma operação de limpeza destrutiva, e o RN012 exige o contrário
     */
    public void setJanela(Duration janela) {
        if (janela == null || janela.isZero() || janela.isNegative()) {
            throw new IllegalArgumentException("scipath.coleta.janela precisa ser um prazo positivo"
                    + " (ex.: 24h): um coletor sem janela de segurança apagaria arquivo sem margem"
                    + " nenhuma (RN012)");
        }
        this.janela = janela;
    }

    public String getAgendamento() {
        return agendamento;
    }

    /**
     * @throws IllegalArgumentException se a expressão não for um cron válido do Spring — um
     *         agendamento inválido é um coletor que nunca roda, e falhar em silêncio é o que o
     *         card proíbe ("agendamento declarado, não silencioso")
     */
    public void setAgendamento(String agendamento) {
        if (agendamento == null || agendamento.isBlank()) {
            throw new IllegalArgumentException("scipath.coleta.agendamento não pode ficar vazio");
        }
        try {
            CronExpression.parse(agendamento);
        } catch (IllegalArgumentException invalida) {
            throw new IllegalArgumentException("scipath.coleta.agendamento não é uma expressão de"
                    + " agendamento válida (6 campos, com segundos — ex.: '0 30 3 * * *'): "
                    + agendamento, invalida);
        }
        this.agendamento = agendamento;
    }

    public boolean isAoIniciar() {
        return aoIniciar;
    }

    public void setAoIniciar(boolean aoIniciar) {
        this.aoIniciar = aoIniciar;
    }
}
