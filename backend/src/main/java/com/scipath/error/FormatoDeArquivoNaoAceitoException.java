package com.scipath.error;

/**
 * Arquivo que não é PDF — <b>415</b> {@code urn:scipath:erro:tipo_nao_suportado} (RF013,
 * ADR-0005 §1).
 *
 * <p>É a recusa de conteúdo, não de {@code Content-Type} da requisição: PDF renomeado, MIME
 * declarado que não corresponde ao conteúdo, extensão que não é {@code .pdf}. A {@code detail}
 * publicada é sempre a do contrato — "O arquivo enviado não é um PDF válido." — e o motivo
 * concreto (qual das três conferências falhou) fica só no log: dizer ao cliente o que o
 * servidor detectou no arquivo é ruído para quem enviou e informação útil para quem sonda
 * (RNF011, RNF012).
 */
public class FormatoDeArquivoNaoAceitoException extends RuntimeException {

    /** Mensagem publicada no {@code detail} — a do contrato v0.5 (resposta 415 da publicação). */
    public static final String DETALHE = "O arquivo enviado não é um PDF válido.";

    /** Motivo técnico da recusa, para o log — nunca para a resposta. */
    private final String motivo;

    public FormatoDeArquivoNaoAceitoException(String motivo) {
        super(DETALHE);
        this.motivo = motivo;
    }

    /** Qual conferência recusou o arquivo (extensão, tipo declarado ou assinatura). */
    public String motivo() {
        return motivo;
    }
}
