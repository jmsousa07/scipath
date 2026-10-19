package com.scipath.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Liga o agendador de tarefas do Spring (card E4.5, ADR-0005 §3).
 *
 * <p><b>Por que aqui, e não em {@code SciPathApplication}.</b> O agendamento é uma capacidade que
 * existe por causa de uma rotina só — a coleta de arquivos órfãos — e é o ADR-0005 §3 que a exige
 * ("o coletor de limpeza roda diariamente"). Deixá-la declarada junto da sua razão mantém a classe
 * de aplicação sendo o que ela é (a porta de entrada) e evita que um {@code @Enable*} novo entre sem
 * que ninguém veja de onde veio a necessidade.
 *
 * <p><b>O que esta anotação liga, exatamente.</b> Sem {@code @EnableScheduling}, um método com
 * {@code @Scheduled} é apenas um método comum: nada o chama, nada avisa, e a rotina existe no
 * código sem nunca rodar. É o modo de falha mais silencioso desta entrega, e por isso ele é
 * declarado aqui, num arquivo com nome próprio, em vez de depender de alguém lembrar.
 *
 * <p>A única tarefa agendada do MVP é {@code ColetorDeOrfaosAgendado}, e ela se desliga por
 * configuração ({@code scipath.coleta.agendado}) — a suíte de testes desliga, para não depender de
 * relógio.
 */
@Configuration
@EnableScheduling
public class ConfiguracaoDeAgendamento {
}
