package com.scipath.arquivo;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * O que <b>uma execução</b> do coletor de órfãos fez (card E4.5, RN012).
 *
 * <p>Existe porque "operação de limpeza observável" precisa de um objeto, não de uma frase no
 * javadoc: o RN012 pede a contagem do que foi removido, e o card pede um <b>identificador de
 * execução</b> que permita dizer qual varredura removeu o quê. É este record que o serviço devolve,
 * que o log escreve e que a suíte verifica — uma definição só do resultado, para o número que
 * aparece no log ser o mesmo que o teste mediu.
 *
 * <p><b>As três contagens são do conjunto examinado, não do banco.</b> {@code examinados} é quantos
 * candidatos a janela de segurança devolveu; {@code removidos}, quantos viraram remoção efetiva;
 * {@code falhas}, quantos não puderam ser removidos <i>nesta</i> execução. Como todo candidato
 * termina em um dos dois desfechos, {@code removidos + falhas == examinados} sempre — é o que
 * permite ler o log e saber se a varredura terminou o serviço ou deixou trabalho para a próxima.
 *
 * <p><b>A linha de resumo é parte do contrato de observabilidade.</b> {@link #resumo()} devolve a
 * mesma frase que vai para o log: sem abrir o banco e sem chamar endpoint nenhum, quem opera lê o
 * identificador da execução, as três contagens e a janela aplicada. O prefixo
 * {@value #PREFIXO} é o que torna o rastro <i>filtrável</i> — {@code grep '\[coleta-orfaos\]'} num
 * arquivo de log é a inspeção que o card pede.
 *
 * @param execucaoId     identificador desta execução, gerado no início dela
 * @param janela         janela de segurança aplicada (a mesma em toda a varredura)
 * @param examinados     candidatos devolvidos pela janela
 * @param removidos      registros efetivamente removidos (com o binário, quando havia)
 * @param falhas         candidatos que não puderam ser removidos nesta execução
 * @param itensRemovidos o que foi removido, um item por arquivo
 * @param motivosDeFalha o motivo de cada falha, com a chave — falha silenciosa não é observabilidade
 */
public record RelatorioDeColeta(UUID execucaoId, Duration janela, int examinados, int removidos,
        int falhas, List<ItemRemovido> itensRemovidos, List<String> motivosDeFalha) {

    /** Prefixo das linhas de log da coleta — o que torna o rastro filtrável. */
    public static final String PREFIXO = "[coleta-orfaos]";

    /**
     * Um arquivo removido nesta execução.
     *
     * @param chaveInterna     chave do arquivo no armazenamento
     * @param nomeOriginalSeguro nome original sanitizado, só como rastro de auditoria
     * @param tamanhoBytes     tamanho registrado no recebimento
     * @param binarioRemovido  {@code false} quando o binário já não estava lá (execução anterior
     *                         interrompida entre as duas remoções) — o registro foi removido do
     *                         mesmo jeito, e o log diz qual dos dois casos foi
     */
    public record ItemRemovido(String chaveInterna, String nomeOriginalSeguro, long tamanhoBytes,
            boolean binarioRemovido) {
    }

    /**
     * A linha de resumo da execução: identificador, as três contagens e a janela aplicada.
     *
     * <p>É uma linha só, e de propósito — um resumo de várias linhas se perde no meio do log de uma
     * aplicação real, e o que quem opera procura é justamente "a varredura rodou e removeu N".
     */
    public String resumo() {
        return PREFIXO + " execucao=" + execucaoId + " examinados=" + examinados
                + " removidos=" + removidos + " falhas=" + falhas + " janela=" + janela;
    }

    /** A linha de um item removido — o "o que removeu" do critério do card. */
    public String linhaDe(ItemRemovido item) {
        return PREFIXO + " execucao=" + execucaoId + " removido chave=" + item.chaveInterna()
                + " nome=" + item.nomeOriginalSeguro() + " bytes=" + item.tamanhoBytes()
                + " binario=" + (item.binarioRemovido() ? "removido" : "ausente");
    }

    /** A linha de uma falha, com o motivo já descrito em {@code motivosDeFalha}. */
    public String linhaDeFalha(String motivo) {
        return PREFIXO + " execucao=" + execucaoId + " falha " + motivo;
    }

    /** Todas as linhas desta execução, na ordem em que o log as escreve (resumo por último). */
    public List<String> linhas() {
        List<String> linhas = new ArrayList<>();
        itensRemovidos.forEach(item -> linhas.add(linhaDe(item)));
        motivosDeFalha.forEach(motivo -> linhas.add(linhaDeFalha(motivo)));
        linhas.add(resumo());
        return List.copyOf(linhas);
    }
}
