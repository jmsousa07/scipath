package com.scipath.arquivo;

import org.springframework.stereotype.Component;

/**
 * Decide se o arquivo de um material <b>pode ser servido agora</b> (RF014) — a pergunta que o
 * detalhe público responde no campo {@code arquivo.disponivel} <b>sem revelar o motivo</b>
 * (RNF013, RNF014).
 *
 * <p><b>Por que existe uma classe para isto.</b> "Servível" é uma condição só, e ela é a mesma do
 * download: o {@code file_asset} precisa estar {@link #ESTADO_VALIDADO} e o binário precisa estar
 * no armazenamento. Duas cópias dessa conjunção divergiriam sem aviso — o detalhe anunciaria um
 * arquivo que o download recusa, ou esconderia um que ele serve. Aqui a condição é escrita uma vez
 * e os dois caminhos perguntam a ela; o download acrescenta, depois, a conferência de integridade
 * (o SHA-256, E4.3), que é cara demais para a leitura do detalhe e que só o download precisa.
 *
 * <p><b>É uma leitura, não uma promessa.</b> O resultado vale para o instante da resposta: o
 * binário pode ser purgado pelo coletor (E4.5) entre o detalhe e o clique, e é por isso que a tela
 * trata {@code false} como "não anuncie" e o download continua sendo a única autoridade sobre
 * servir o conteúdo. {@code true} não é garantia de 200 no futuro — é ausência de motivo conhecido
 * para não servir agora.
 *
 * <p><b>O {@code false} é mudo de propósito.</b> Quem lê a resposta não descobre se o arquivo nunca
 * foi promovido, se foi purgado ou se o registro está em outro estado: o motivo fica no servidor,
 * como o caminho e a chave (RNF014).
 */
@Component
public class DisponibilidadeDeArquivo {

    /**
     * Único estado de {@code file_asset} que pode ser servido (RN008): conteúdo já conferido no
     * recebimento. Morava em {@link AcessoAoArquivoService} e mudou de lugar quando o detalhe
     * passou a fazer a mesma pergunta — uma constante e um dono (o download a referencia daqui).
     */
    public static final String ESTADO_VALIDADO = "VALIDADO";

    private final StorageService armazenamento;

    public DisponibilidadeDeArquivo(StorageService armazenamento) {
        this.armazenamento = armazenamento;
    }

    /**
     * O arquivo pode ser servido agora?
     *
     * <p>As duas condições, na ordem em que são baratas: o estado do registro (já lido do banco) e a
     * presença do binário no armazenamento (uma consulta ao disco, não uma leitura do conteúdo — o
     * tamanho do arquivo não influencia o custo desta resposta).
     *
     * @param estadoProcessamento estado do {@code file_asset} ({@code VALIDADO} é o único servível)
     * @param chaveInterna        chave do arquivo no armazenamento; decide o caminho e nunca é
     *                            publicada
     */
    public boolean podeServir(String estadoProcessamento, String chaveInterna) {
        return ESTADO_VALIDADO.equals(estadoProcessamento)
                && armazenamento.localizar(chaveInterna).isPresent();
    }
}
