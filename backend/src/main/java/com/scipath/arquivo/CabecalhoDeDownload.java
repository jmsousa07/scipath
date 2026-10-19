package com.scipath.arquivo;

import java.nio.charset.StandardCharsets;

/**
 * Monta o {@code Content-Disposition} do download (contrato v0.5, RF014) — {@code attachment} com o
 * nome original sanitizado do arquivo, para o navegador salvar em vez de abrir no visualizador
 * embutido.
 *
 * <p><b>Por que não é {@code "attachment; filename=\"" + nome + "\""}.</b> Valor de cabeçalho HTTP
 * não é texto livre: fora do ASCII ele precisa de codificação, e um nome como
 * {@code introdução-à-algoritmos.pdf} — o caso normal neste público — ou quebraria a resposta ou
 * chegaria embaralhado. O RFC 6266 resolve isso com <b>dois</b> parâmetros: o {@code filename}
 * clássico (que só aceita ASCII, para clientes antigos) e o {@code filename*} codificado em
 * percentual com o charset declarado (RFC 5987), que é o que os navegadores atuais usam.
 *
 * <p><b>O nome já vem limpo do recebimento</b> (E4.2, {@link NomesDeArquivo}): sem aspas, sem
 * contrabarra e sem caractere de controle — a classe de injeção de cabeçalho que dividiria a
 * resposta em duas. Aqui o trabalho é só de codificação; nenhuma sanitização nova, e nenhum valor de
 * usuário é confiado: o que chega já passou pela limpeza e é usado como dado, nunca como formato.
 *
 * <p><b>O que este cabeçalho NÃO carrega:</b> caminho, chave de armazenamento, checksum — o nome
 * sugerido é o do arquivo que o autor enviou, não onde ele mora (RF014, RNF014).
 */
final class CabecalhoDeDownload {

    /** Formato do parâmetro codificado: charset do valor declarado, e o valor percentual. */
    private static final String CHARSET_DO_VALOR = "UTF-8''";

    private CabecalhoDeDownload() {
    }

    /** {@code attachment} com o nome sugerido, codificado de forma que o navegador salve certo. */
    static String para(String nomeSeguro) {
        String simples = apenasAscii(nomeSeguro);
        StringBuilder cabecalho = new StringBuilder("attachment; filename=\"").append(simples).append('"');
        if (!simples.equals(nomeSeguro)) {
            cabecalho.append("; filename*=").append(CHARSET_DO_VALOR).append(percentual(nomeSeguro));
        }
        return cabecalho.toString();
    }

    /**
     * Versão ASCII do nome, para o parâmetro clássico: caractere fora de {@code 0x20–0x7E} vira
     * {@code _}. O nome perde acento nesta cópia — é o que um cliente que não entende
     * {@code filename*} recebe, e é preferível a um cabeçalho malformado.
     */
    private static String apenasAscii(String nome) {
        StringBuilder ascii = new StringBuilder(nome.length());
        for (char caractere : nome.toCharArray()) {
            ascii.append(caractere >= 0x20 && caractere <= 0x7E ? caractere : '_');
        }
        return ascii.toString();
    }

    /**
     * Codificação percentual do RFC 5987: bytes UTF-8 do nome, escapando tudo que não é
     * {@code attr-char}.
     *
     * <p>Não usa {@code URLEncoder}: ele é codificação de formulário e escreveria espaço como
     * {@code +}, que em {@code filename*} significa o caractere {@code +}, não espaço — o navegador
     * salvaria um arquivo com o nome errado, e "errado" aqui é o do arquivo do autor.
     */
    private static String percentual(String nome) {
        StringBuilder codificado = new StringBuilder(nome.length());
        for (byte valor : nome.getBytes(StandardCharsets.UTF_8)) {
            int byteSemSinal = valor & 0xFF;
            if (ehNaoEscapado(byteSemSinal)) {
                codificado.append((char) byteSemSinal);
            } else {
                codificado.append('%').append(String.format("%02X", byteSemSinal));
            }
        }
        return codificado.toString();
    }

    /** {@code attr-char} do RFC 5987 — o conjunto que pode aparecer cru no valor codificado. */
    private static boolean ehNaoEscapado(int octeto) {
        boolean letraOuDigito = (octeto >= 'a' && octeto <= 'z')
                || (octeto >= 'A' && octeto <= 'Z')
                || (octeto >= '0' && octeto <= '9');
        return letraOuDigito || switch (octeto) {
            case '!', '#', '$', '&', '+', '-', '.', '^', '_', '`', '|', '~' -> true;
            default -> false;
        };
    }
}
