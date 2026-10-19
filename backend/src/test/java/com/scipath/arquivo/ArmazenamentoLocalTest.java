package com.scipath.arquivo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;

/**
 * O armazenamento local (card E4.3, ADR-0005 §2): guardar por chave, localizar por chave, remover
 * por chave — e nada além da chave decide o caminho.
 *
 * <p>Teste de unidade sobre disco de verdade, em diretório temporário do JUnit: o que se prova aqui é
 * o <b>contrato da porta</b> (chave decide o nome físico, movimento não deixa cópia, chave inválida
 * não escapa do diretório, remoção é idempotente), sem contexto Spring e sem banco.
 */
@DisplayName("armazenamento local do arquivo (ADR-0005 §2)")
class ArmazenamentoLocalTest {

    @TempDir
    Path base;

    /** Área de apoio onde o "recebido" é criado antes de ser promovido, como a do E4.2. */
    @TempDir
    Path recebimento;

    private PropriedadesDeArmazenamento propriedades;
    private ArmazenamentoLocal armazenamento;

    @BeforeEach
    void preparar() {
        propriedades = new PropriedadesDeArmazenamento();
        // Diretório de teste: fora de raiz servida (a configuração recusaria o contrário) e sob o
        // temp do JUnit, que é limpo ao fim da classe.
        propriedades.setDiretorio(base.toString());
        armazenamento = new ArmazenamentoLocal(propriedades);
    }

    @Test
    @DisplayName("guardar move o arquivo para <diretorio>/<chave>: a origem deixa de existir e nada fica duplicado")
    void guardarMoveSobAChave() throws IOException {
        byte[] conteudo = "%PDF-1.7 conteúdo de teste".getBytes(StandardCharsets.UTF_8);
        Path origem = arquivoEm(recebimento, "nota-de-aula.pdf", conteudo);

        armazenamento.guardar("chave-um", origem);

        Path guardado = base.resolve("chave-um");
        assertThat(origem).as("é um movimento, não uma cópia (RN012)").doesNotExist();
        assertThat(guardado).exists();
        assertThat(Files.readAllBytes(guardado)).isEqualTo(conteudo);
        assertThat(conteudosDe(base))
                .as("o diretório tem exatamente o arquivo da chave")
                .containsExactly("chave-um");
        System.out.println("[E4.3] guardado " + origem.getFileName() + " -> " + guardado);
    }

    @Test
    @DisplayName("o nome original nunca participa do caminho: o arquivo fica sob a chave, não sob o nome enviado")
    void nomeOriginalNaoDecideOCaminho() throws IOException {
        Path origem = arquivoEm(recebimento, "introdução..aos;;algoritmos.pdf", "conteúdo".getBytes(StandardCharsets.UTF_8));

        armazenamento.guardar("123e4567-e89b-12d3-a456-426614174000", origem);

        assertThat(base.resolve("123e4567-e89b-12d3-a456-426614174000")).exists();
        assertThat(conteudosDe(base))
                .as("nenhum componente do caminho deriva do nome do cliente (RF013, ADR-0005 §2)")
                .containsExactly("123e4567-e89b-12d3-a456-426614174000");
    }

    @Test
    @DisplayName("guardar duas vezes sob a MESMA chave falha alto e não sobrescreve o arquivo existente")
    void chaveOcupadaNaoSobrescreve() throws IOException {
        byte[] primeiro = "primeiro".getBytes(StandardCharsets.UTF_8);
        armazenamento.guardar("chave-ocupada", arquivoEm(recebimento, "primeiro.pdf", primeiro));

        byte[] segundo = "segundo".getBytes(StandardCharsets.UTF_8);
        Path segundoArquivo = arquivoEm(recebimento, "segundo.pdf", segundo);

        assertThatThrownBy(() -> armazenamento.guardar("chave-ocupada", segundoArquivo))
                .as("sobrescrever apagaria o arquivo de outro material (ADR-0005 §3)")
                .isInstanceOf(ArquivoJaArmazenadoException.class)
                .hasMessageContaining("chave-ocupada");
        assertThat(Files.readAllBytes(base.resolve("chave-ocupada")))
                .as("o conteúdo anterior continua intacto")
                .isEqualTo(primeiro);
    }

    @Test
    @DisplayName("localizar devolve o conteúdo da chave e vazio quando não há arquivo sob ela")
    void localizarPorChave() throws IOException {
        byte[] conteudo = "%PDF-1.7".getBytes(StandardCharsets.UTF_8);
        armazenamento.guardar("chave-dois", arquivoEm(recebimento, "x.pdf", conteudo));

        Optional<Resource> encontrado = armazenamento.localizar("chave-dois");
        assertThat(encontrado).isPresent();
        assertThat(encontrado.orElseThrow().contentLength()).isEqualTo(conteudo.length);
        try (var entrada = encontrado.orElseThrow().getInputStream()) {
            assertThat(entrada.readAllBytes()).isEqualTo(conteudo);
        }

        assertThat(armazenamento.localizar("chave-que-nao-existe"))
                .as("ausência é estado previsto, não erro")
                .isEmpty();
    }

    @Test
    @DisplayName("remover apaga o arquivo e é idempotente: a segunda chamada responde false, sem erro")
    void removerEhIdempotente() throws IOException {
        armazenamento.guardar("chave-tres", arquivoEm(recebimento, "y.pdf", "conteúdo".getBytes(StandardCharsets.UTF_8)));

        assertThat(armazenamento.remover("chave-tres")).as("havia arquivo: removeu").isTrue();
        assertThat(base.resolve("chave-tres")).doesNotExist();
        assertThat(armazenamento.remover("chave-tres"))
                .as("a segunda execução do coletor (RN012) não tem o que remover e não é erro")
                .isFalse();
    }

    @Test
    @DisplayName("chave com separador, '..', vazia ou de controle é recusada: ela nomeia um arquivo, não um caminho")
    void chaveInvalidaRecusada() {
        List<String> invalidas = List.of("../fora", "a/b", "a\\b", "..", ".", "", "   ", "chave\nde-teste");

        for (String chave : invalidas) {
            assertThatThrownBy(() -> armazenamento.localizar(chave))
                    .as("chave '%s' não pode virar caminho", chave)
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> armazenamento.remover(chave))
                    .as("chave '%s' não pode virar caminho", chave)
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(conteudosDe(base)).as("nada foi criado pelas chaves recusadas").isEmpty();
    }

    @Test
    @DisplayName("o diretório do armazenamento é criado sob demanda, no primeiro arquivo guardado")
    void diretorioCriadoSobDemanda() throws IOException {
        Path novaBase = base.resolve("ainda-nao-existe");
        PropriedadesDeArmazenamento outra = new PropriedadesDeArmazenamento();
        outra.setDiretorio(novaBase.toString());
        assertThat(novaBase).doesNotExist();

        new ArmazenamentoLocal(outra).guardar("chave-quatro", arquivoEm(recebimento, "z.pdf", "%PDF-1.7".getBytes(StandardCharsets.UTF_8)));

        assertThat(novaBase.resolve("chave-quatro")).exists();
        System.out.println("[E4.3] diretório criado no primeiro uso: " + novaBase);
    }

    @Test
    @DisplayName("falha de movimento sobe como UncheckedIOException, não como sucesso silencioso")
    void origemInexistenteFalha() {
        Path origem = recebimento.resolve("nao-existe.pdf");

        assertThatThrownBy(() -> armazenamento.guardar("chave-cinco", origem))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("chave-cinco");
        assertThat(conteudosDe(base)).isEmpty();
    }

    private static Path arquivoEm(Path diretorio, String nome, byte[] conteudo) throws IOException {
        Path arquivo = diretorio.resolve(nome);
        Files.write(arquivo, conteudo);
        return arquivo;
    }

    private static List<String> conteudosDe(Path diretorio) {
        if (!Files.exists(diretorio)) {
            return List.of();
        }
        try (Stream<Path> entradas = Files.list(diretorio)) {
            return entradas.map(caminho -> caminho.getFileName().toString()).sorted().toList();
        } catch (IOException falha) {
            throw new IllegalStateException("não foi possível listar " + diretorio, falha);
        }
    }
}
