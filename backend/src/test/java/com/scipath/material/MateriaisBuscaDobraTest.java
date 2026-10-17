package com.scipath.material;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guarda contra a única falha silenciosa que a busca tem (ADR-0008): o índice
 * ({@code V3__busca_texto_material.sql}) e a consulta ({@link MateriaisRepositorio}) precisam
 * aplicar <b>a mesma</b> dobra de acento. Se um lado mudar sem o outro, nada quebra e nenhum
 * erro aparece — o planejador simplesmente deixa de reconhecer o índice e a busca passa a
 * varrer a tabela. Por isso o mapa é verificado aqui, e o {@code EXPLAIN} da evidência do card
 * verifica o casamento de verdade, no banco.
 *
 * <p>Teste de unidade, sem contexto Spring e sem banco: é rápido e roda em qualquer máquina.
 */
@DisplayName("dobra de acento da busca: consulta e índice não podem divergir (ADR-0008)")
class MateriaisBuscaDobraTest {

    /** Migração que cria a configuração e o índice — lida do classpath, como o Flyway lê. */
    private static final String MIGRACAO = "/db/migration/V3__busca_texto_material.sql";

    @Test
    @DisplayName("os dois mapas têm o mesmo comprimento — translate() falha alto se divergirem")
    void mapasTemOMesmoComprimento() {
        assertThat(MateriaisRepositorio.ACENTOS)
                .as("translate() exige mapas de comprimento igual; divergir derruba toda busca com erro")
                .hasSameSizeAs(MateriaisRepositorio.SEM_ACENTOS)
                .hasSize(54);
    }

    @Test
    @DisplayName("o mapa de origem só tem caractere acentuado e o de destino só tem ASCII")
    void mapasSaoDeAcentoParaAscii() {
        assertThat(MateriaisRepositorio.ACENTOS.chars().boxed().toList())
                .as("o mapa de origem não pode conter caractere sem acento")
                .allSatisfy(caractere -> assertThat(caractere)
                        .as("'%s' não é acentuado", (char) (int) caractere)
                        .isGreaterThan(127));

        assertThat(MateriaisRepositorio.SEM_ACENTOS.chars().boxed().toList())
                .as("o mapa de destino tem de ser ASCII (o analisador do tsvector rebaixa ASCII de forma determinística)")
                .allSatisfy(caractere -> assertThat(caractere)
                        .as("'%s' não é ASCII", (char) (int) caractere)
                        .isLessThan(128));
    }

    @Test
    @DisplayName("a migração do índice usa exatamente o mesmo mapa da consulta")
    void migracaoUsaOMesmoMapa() {
        String sql = sqlExecutavel(lerMigracao());

        assertThat(sql)
                .as("o índice da busca é criado por esta migração")
                .contains("CREATE INDEX material_busca_idx ON material USING GIN");
        assertThat(sql)
                .as("a dobra de acento do índice tem de ser a MESMA da consulta (MateriaisRepositorio)")
                .contains("'" + MateriaisRepositorio.ACENTOS + "'")
                .contains("'" + MateriaisRepositorio.SEM_ACENTOS + "'");
        assertThat(sql)
                .as("a configuração de busca é nomeada, nunca a default do servidor")
                .contains("to_tsvector('portuguese'");
        assertThat(sql)
                .as("o escopo do índice é título + descrição, como o contrato promete")
                .contains("titulo || ' ' || descricao");
        assertThat(sql)
                .as("a migração não pode depender de extensão (ADR-0008: quebraria make db:migrate:test)")
                .doesNotContain("CREATE EXTENSION");
    }

    /**
     * O SQL da migração sem as linhas de comentário. A distinção importa: os comentários do
     * arquivo <b>citam</b> `CREATE EXTENSION` justamente para explicar por que a alternativa foi
     * descartada — o que o teste precisa garantir é que nenhum <b>comando</b> a execute.
     */
    private static String sqlExecutavel(String migracao) {
        return migracao.lines()
                .filter(linha -> !linha.strip().startsWith("--"))
                .reduce("", (acumulado, linha) -> acumulado + linha + "\n");
    }

    private static String lerMigracao() {
        try (InputStream arquivo = MateriaisBuscaDobraTest.class.getResourceAsStream(MIGRACAO)) {
            assertThat(arquivo).as("migração %s no classpath", MIGRACAO).isNotNull();
            return new String(arquivo.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new AssertionError("não foi possível ler " + MIGRACAO, e);
        }
    }

    @Test
    @DisplayName("o predicado da consulta é o documentado no ADR: plainto_tsquery, configuração nomeada e a mesma dobra")
    void predicadoDaConsultaSegueODocumentado() {
        String predicado = MateriaisRepositorio.BUSCA;

        assertThat(predicado)
                .as("escopo do contrato: título + descrição, nada além")
                .contains("m.titulo || ' ' || m.descricao")
                .as("configuração nomeada, nunca a default do servidor")
                .contains("to_tsvector('portuguese'")
                .as("o termo entra ligado, nunca concatenado na string")
                .contains(":busca")
                .as("a dobra do lado da consulta é a mesma do índice")
                .contains("'" + MateriaisRepositorio.ACENTOS + "'")
                .contains("'" + MateriaisRepositorio.SEM_ACENTOS + "'");

        // `to_tsquery` interpretaria a entrada como EXPRESSÃO (&, |, !, parênteses são operadores) —
        // o contrato exige que caracteres especiais sejam texto (§12.1). A única ocorrência
        // admissível da substring "to_tsquery" é dentro de "plainto_tsquery".
        assertThat(predicado.split("to_tsquery", -1).length - 1)
                .as("o predicado usa plainto_tsquery e nunca to_tsquery")
                .isEqualTo(1);
        assertThat(predicado).contains("plainto_tsquery");
    }
}
