package com.scipath.error;

/**
 * Arquivo acima do limite de upload — <b>413</b> {@code urn:scipath:erro:arquivo_muito_grande}
 * (RF013, ADR-0005 §1, RNF004).
 *
 * <p>A exceção carrega o <b>valor</b> do limite porque a mensagem do contrato o nomeia:
 * "O arquivo excede o limite de X MB." O número entra por dois caminhos — a checagem do serviço
 * (que compara o tamanho recebido com o valor configurado) e a recusa do próprio contêiner
 * (que derruba o multipart no limite declarado em {@code MultipartConfigElement}); nos dois o
 * texto tem de ser o mesmo, e é por isso que o formato da mensagem mora aqui e não em cada
 * lugar que a lança.
 *
 * <p>Sem {@code errors[]}: o arquivo não é campo de corpo, e o motivo cabe no {@code detail}.
 */
public class ArquivoMuitoGrandeException extends RuntimeException {

    private static final String PREFIXO = "O arquivo excede o limite de ";

    private final long limiteEmBytes;

    public ArquivoMuitoGrandeException(long limiteEmBytes) {
        super(detalhe(limiteEmBytes));
        this.limiteEmBytes = limiteEmBytes;
    }

    /** Limite que foi excedido, em bytes — o valor configurado, nunca um literal do código. */
    public long limiteEmBytes() {
        return limiteEmBytes;
    }

    /**
     * Mensagem do contrato para um limite em bytes.
     *
     * <p>A unidade acompanha a configuração em vez de ser sempre "MB": o valor é do operador
     * ({@code SCIPATH_UPLOAD_MAX_BYTES}) e pode chegar em KB ou em bytes crus — publicar
     * "0 MB" para um limite de 512 KB seria uma mensagem que mente (RNF004).
     */
    public static String detalhe(long limiteEmBytes) {
        return PREFIXO + descricaoDoLimite(limiteEmBytes) + ".";
    }

    private static String descricaoDoLimite(long bytes) {
        long megabyte = 1024L * 1024L;
        if (bytes % megabyte == 0) {
            return (bytes / megabyte) + " MB";
        }
        if (bytes % 1024L == 0) {
            return (bytes / 1024L) + " KB";
        }
        return bytes + " bytes";
    }
}
