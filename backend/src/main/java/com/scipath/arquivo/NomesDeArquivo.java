package com.scipath.arquivo;

import java.util.Locale;

/**
 * Nome original do arquivo enviado, reduzido ao que pode ser guardado como <b>metadado</b>
 * (RF013, ADR-0005 §2).
 *
 * <p><b>O nome original nunca decide caminho.</b> O arquivo recebido é gravado sob uma chave
 * interna gerada pelo sistema (UUID) e o nome do usuário vai para {@code file_asset} como
 * texto, só para a tela mostrar ao autor qual arquivo ele enviou. Ainda assim ele é limpo
 * aqui, e não na hora de exibir, por dois motivos concretos:
 *
 * <ul>
 *   <li><b>Traversal.</b> {@code ../../etc/passwd.pdf} não pode virar um caminho relativo em
 *       nenhum lugar do sistema. Tirar o diretório do nome e neutralizar {@code ..} garante que
 *       o valor guardado não tenha como ser interpretado como caminho nem por engano.</li>
 *   <li><b>Cabeçalho de download.</b> O nome guardado é o que o E4.3 devolve em
 *       {@code Content-Disposition}: nome com quebra de linha, aspa ou caractere de controle
 *       quebraria o cabeçalho ou dividiria a resposta em duas (injeção de cabeçalho). Limpar na
 *       entrada resolve os dois usos de uma vez.</li>
 * </ul>
 *
 * <p>A limpeza é determinística e acontece <b>antes</b> das conferências de formato: é o nome
 * limpo — o que de fato fica guardado — que o serviço exige terminado em {@code .pdf}.
 */
final class NomesDeArquivo {

    /** Nome usado quando o cliente não manda nome nenhum (ou manda algo que vira nada). */
    static final String NOME_PADRAO = "arquivo.pdf";

    /**
     * Teto do nome guardado. Bem abaixo do limite de qualquer sistema de arquivos e de
     * cabeçalho HTTP; o suficiente para um nome legível, longe o bastante para não guardar
     * entrada gigante do cliente só porque ele quis.
     */
    static final int LIMITE_DE_CARACTERES = 120;

    private NomesDeArquivo() {
    }

    /**
     * Nome original limpo: só o último componente, sem diretório, sem {@code ..}, sem
     * caracteres de controle e sem os reservados do sistema de arquivos, com tamanho limitado e
     * terminado em {@code .pdf} quando já terminava.
     *
     * <p>A extensão é preservada na truncagem de propósito: cortar o nome no meio poderia
     * virar "relatorio-de-fisica" sem {@code .pdf} e o valor guardado deixaria de descrever o
     * arquivo que foi aceito.
     */
    static String sanitizar(String nomeOriginal) {
        if (nomeOriginal == null || nomeOriginal.isBlank()) {
            return NOME_PADRAO;
        }

        // 1. Só o último componente: o separador das duas convenções é tratado aqui porque a
        //    entrada pode vir de outro sistema (Windows manda "C:\docs\nota.pdf").
        String nome = nomeOriginal.replace('\\', '/');
        int ultimaBarra = nome.lastIndexOf('/');
        if (ultimaBarra >= 0) {
            nome = nome.substring(ultimaBarra + 1);
        }

        // 2. Pontos consecutivos neutralizados: ".." deixa de existir como sequência, e com
        //    isso o valor guardado não é um caminho relativo em sistema nenhum.
        nome = nome.replaceAll("\\.{2,}", "_");

        // 3. Controle (inclui \n, \r, \t e NUL) e reservados do sistema de arquivos.
        nome = nome.replaceAll("[\\p{Cntrl}]", "").replaceAll("[<>:\"|?*]", "_");

        // 4. Espaços nas pontas e nome que ficou vazio depois da limpeza ("..", "...", "/"): um
        //    nome só de pontos e sublinhados não identifica arquivo nenhum para quem o lê.
        nome = nome.strip();
        if (nome.isBlank() || nome.matches("[._]+")) {
            return NOME_PADRAO;
        }

        // 5. Tamanho, preservando a extensão quando ela existe.
        if (nome.length() > LIMITE_DE_CARACTERES) {
            String sufixo = nome.toLowerCase(Locale.ROOT).endsWith(".pdf") ? ".pdf" : "";
            nome = nome.substring(0, LIMITE_DE_CARACTERES - sufixo.length()) + sufixo;
        }
        return nome;
    }
}
