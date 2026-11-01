package com.scipath.arquivo;

import java.util.UUID;

/** Arquivo ligado a material EXCLUIDO cujo prazo de retenção terminou. */
public record ExpurgoDeRetencaoCandidato(UUID id, String chaveInterna, String nomeOriginalSeguro,
        long tamanhoBytes) {
}
