package com.scipath.arquivo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Configuração do armazenamento definitivo (card E4.3, ADR-0005 §2): o diretório é conferido na
 * subida, e um valor que serviria o arquivo por endereço direto é recusado.
 *
 * <p>Teste de unidade — sem contexto Spring, sem banco, sem disco: o objeto sob teste é a
 * configuração, e o que se prova é a decisão (aceita/recusa) e a normalização do caminho.
 *
 * <p><b>Por que recusar faz parte do comportamento, e não só do teste do card.</b> O critério 2 do
 * card pede a prova de que o diretório de storage não está sob raiz servida. A prova mais forte não
 * é uma asserção sobre o valor de hoje: é o valor <b>não poder</b> ser raiz servida — quem operar o
 * sistema e apontar o armazenamento para {@code resources/static} recebe um erro na subida, com o
 * motivo escrito, em vez de o arquivo passar a ser entregue pelo contêiner sem visibilidade nem
 * autorização (RF014, RNF014).
 */
@DisplayName("configuração do armazenamento definitivo (ADR-0005 §2)")
class PropriedadesDeArmazenamentoTest {

    @Test
    @DisplayName("o padrão fica fora do repositório e fora de qualquer raiz servida")
    void padraoFicaForaDoRepositorioEForaDeRaizServida() {
        Path padrao = new PropriedadesDeArmazenamento().diretorio();

        assertThat(padrao.isAbsolute()).as("caminho normalizado e absoluto").isTrue();
        assertThat(RaizesServidas.contem(padrao))
                .as("o padrão não pode cair sob raiz servida: %s", padrao)
                .isFalse();

        // O cwd do fork do surefire é o módulo `backend/`; a raiz do repositório é o pai dele.
        Path raizDoRepositorio = Path.of("..").toAbsolutePath().normalize();
        assertThat(padrao.startsWith(raizDoRepositorio))
                .as("o arquivo não mora dentro da árvore versionada: %s", padrao)
                .isFalse();

        System.out.println("[E4.3] diretório padrão do armazenamento: " + padrao);
    }

    @Test
    @DisplayName("diretório sob raiz servida é recusado com o motivo, em vez de virar endereço direto")
    void raizServidaRecusada() {
        List<String> servidos = List.of(
                "src/main/resources/static/arquivos",
                "src/main/resources/public/arquivos",
                "src/main/resources/qualquer-coisa",
                "src/main/resources/META-INF/resources/arquivos",
                "target/classes/static/arquivos",
                "target/classes/public/arquivos",
                "target/classes/resources/arquivos",
                "resources/static/arquivos",
                "frontend/public/arquivos",
                "frontend/.next/static/arquivos",
                "/srv/app/backend/src/main/resources/static");

        PropriedadesDeArmazenamento propriedades = new PropriedadesDeArmazenamento();
        for (String caminho : servidos) {
            assertThatThrownBy(() -> propriedades.setDiretorio(caminho))
                    .as("'%s' tem de ser recusado (ADR-0005 §2)", caminho)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("raiz servida");
            System.out.println("[E4.3] recusado: " + caminho);
        }
    }

    @Test
    @DisplayName("valor vazio é recusado: sem diretório não há onde guardar o arquivo publicado")
    void vazioRecusado() {
        PropriedadesDeArmazenamento propriedades = new PropriedadesDeArmazenamento();

        assertThatThrownBy(() -> propriedades.setDiretorio(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("não pode ficar vazio");
        assertThatThrownBy(() -> propriedades.setDiretorio("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("não pode ficar vazio");
    }

    @Test
    @DisplayName("a conferência é por SEGMENTO de caminho, não por substring: 'resources-static' não é raiz servida")
    void conferenciaPorSegmentoNaoPorSubstring() {
        List<String> aceitos = List.of(
                "target/armazenamento-de-teste",
                "/var/lib/scipath/arquivos",
                "/srv/dados/resources-static/arquivos",
                "/srv/dados/frontend-publico/arquivos",
                "/srv/dados/publico/statico/arquivos");

        PropriedadesDeArmazenamento propriedades = new PropriedadesDeArmazenamento();
        for (String caminho : aceitos) {
            propriedades.setDiretorio(caminho);
            Path esperado = Path.of(caminho).toAbsolutePath().normalize();
            assertThat(propriedades.diretorio())
                    .as("'%s' é um diretório comum e tem de ser aceito", caminho)
                    .isEqualTo(esperado);
            System.out.println("[E4.3] aceito: " + caminho + " -> " + esperado);
        }
    }
}
