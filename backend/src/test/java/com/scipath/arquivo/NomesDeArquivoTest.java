package com.scipath.arquivo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Nome original do arquivo enviado (RF013): o valor guardado como metadado não pode ter
 * como ser interpretado como caminho, nem quebrar o cabeçalho de download do E4.3.
 *
 * <p>Teste de unidade puro — a limpeza é função de uma string só, sem banco e sem contexto
 * Spring (954 MB, AGENTS.md).
 */
@DisplayName("nome original do arquivo recebido")
class NomesDeArquivoTest {

    @Test
    @DisplayName("travessia de diretório é neutralizada: separador e '..' não sobrevivem")
    void travessiaNeutralizada() {
        // Cada entrada tem de virar um nome sem diretório e sem ".." — em qualquer convenção.
        List<String> entradas = List.of(
                "../../etc/passwd.pdf",
                "..\\..\\Windows\\nota.pdf",
                "/etc/scipath/nota.pdf",
                "C:/Users/test-user/Downloads/nota-de-aula.pdf",
                "./nota.pdf",
                "nota..pdf",
                "....//nota.pdf",
                "a/b/../c/nota.pdf");

        for (String entrada : entradas) {
            String limpo = NomesDeArquivo.sanitizar(entrada);
            assertThat(limpo)
                    .as("entrada '%s' -> '%s'", entrada, limpo)
                    .doesNotContain("/").doesNotContain("\\").doesNotContain("..");
            assertThat(limpo).as("o nome limpo de '%s' não pode ficar vazio", entrada).isNotBlank();
            System.out.println("[E4.2] sanitizar(\"" + entrada + "\") -> \"" + limpo + "\"");
        }
    }

    @Test
    @DisplayName("só o último componente do caminho é guardado")
    void soOUltimoComponente() {
        assertThat(NomesDeArquivo.sanitizar("../../etc/passwd.pdf")).isEqualTo("passwd.pdf");
        assertThat(NomesDeArquivo.sanitizar("C:/Users/test-user/Downloads/nota-de-aula.pdf")).isEqualTo("nota-de-aula.pdf");
        assertThat(NomesDeArquivo.sanitizar("/tmp/relatorio.pdf")).isEqualTo("relatorio.pdf");
    }

    @Test
    @DisplayName("pontos consecutivos internos viram '_': o valor guardado nunca é um caminho relativo")
    void pontosConsecutivosViramSublinhado() {
        assertThat(NomesDeArquivo.sanitizar("nota..pdf")).isEqualTo("nota_pdf");
        assertThat(NomesDeArquivo.sanitizar("....pdf")).isEqualTo("_pdf");
    }

    @Test
    @DisplayName("caractere de controle e reservado do sistema de arquivos não passam para o metadado")
    void controleEReservados() {
        String limpo = NomesDeArquivo.sanitizar("relat\u0000orio\nfinal.pdf");

        assertThat(limpo).isEqualTo("relatoriofinal.pdf");
        assertThat(limpo).as("nenhum caractere de controle sobrevive").matches("[^\\p{Cntrl}]*");
        // O nome guardado é o que o E4.3 devolve em Content-Disposition: sem eles, não há como
        // quebrar o cabeçalho nem apontar para outro arquivo.
        assertThat(NomesDeArquivo.sanitizar("nota\"quebra\n.pdf")).doesNotContain("\\", "\"", "\n");
    }

    @Test
    @DisplayName("nome ausente, vazio ou só de separadores vira o nome padrão")
    void nomeAusenteViraPadrao() {
        assertThat(NomesDeArquivo.sanitizar(null)).isEqualTo(NomesDeArquivo.NOME_PADRAO);
        assertThat(NomesDeArquivo.sanitizar("   ")).isEqualTo(NomesDeArquivo.NOME_PADRAO);
        assertThat(NomesDeArquivo.sanitizar("../")).isEqualTo(NomesDeArquivo.NOME_PADRAO);
        assertThat(NomesDeArquivo.sanitizar("...")).isEqualTo(NomesDeArquivo.NOME_PADRAO);
    }

    @Test
    @DisplayName("nome longo é truncado preservando a extensão .pdf")
    void nomeLongoTruncado() {
        String enorme = "n".repeat(400) + ".pdf";
        String limpo = NomesDeArquivo.sanitizar(enorme);

        assertThat(limpo).hasSize(NomesDeArquivo.LIMITE_DE_CARACTERES).endsWith(".pdf");
        assertThat(NomesDeArquivo.sanitizar("n".repeat(400))).hasSize(NomesDeArquivo.LIMITE_DE_CARACTERES);
    }
}
