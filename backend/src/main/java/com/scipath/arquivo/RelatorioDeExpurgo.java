package com.scipath.arquivo;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Resultado auditável de uma execução da rotina de retenção (E5.5, RN012). */
public record RelatorioDeExpurgo(UUID execucaoId, String janela, int examinados, int purgados, int falhas,
        List<ItemPurgado> itensPurgados, List<String> motivosDeFalha) {

    public static final String PREFIXO = "[expurgo-retencao]";

    public record ItemPurgado(String chaveInterna, String nomeOriginalSeguro, long tamanhoBytes,
            boolean binarioRemovido) {
    }

    public String resumo() {
        return PREFIXO + " execucao=" + execucaoId + " examinados=" + examinados
                + " purgados=" + purgados + " falhas=" + falhas + " janela=" + janela;
    }

    public String linhaDe(ItemPurgado item) {
        return PREFIXO + " execucao=" + execucaoId + " purgado chave=" + item.chaveInterna()
                + " nome=" + item.nomeOriginalSeguro() + " bytes=" + item.tamanhoBytes()
                + " binario=" + (item.binarioRemovido() ? "removido" : "ausente");
    }

    public String linhaDeFalha(String motivo) {
        return PREFIXO + " execucao=" + execucaoId + " falha " + motivo;
    }

    public List<String> linhas() {
        List<String> linhas = new ArrayList<>();
        itensPurgados.forEach(item -> linhas.add(linhaDe(item)));
        motivosDeFalha.forEach(motivo -> linhas.add(linhaDeFalha(motivo)));
        linhas.add(resumo());
        return List.copyOf(linhas);
    }
}
