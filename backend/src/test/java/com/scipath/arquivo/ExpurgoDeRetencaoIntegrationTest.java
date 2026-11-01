package com.scipath.arquivo;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DisplayName("expurgo de retenção de materiais excluídos (E5.5)")
class ExpurgoDeRetencaoIntegrationTest {

    private static final String PREFIXO = "teste-e5-5-";
    private static final String CATEGORIA = "computacao-e-tecnologia";
    private static final String TIPO = "nota-de-aula";
    private static final String LICENCA = "cc-by-4.0";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PropriedadesDeArmazenamento propriedadesDeArmazenamento;

    @Autowired
    private StorageService storage;

    @Autowired
    private ExpurgoDeRetencaoService expurgo;

    @Autowired
    private ExpurgoDeRetencaoAoIniciar aoIniciar;

    @Autowired
    private ApplicationContext contexto;

    @TempDir
    Path areaDeApoio;

    @BeforeEach
    void limparAntes() {
        limparDadosDeTeste();
    }

    @AfterEach
    void limparDepois() {
        limparDadosDeTeste();
    }

    @Test
    @DisplayName("só purga EXCLUIDO anterior à janela; preserva estados vivos, recentes e metadados")
    void purgaSomenteExcluidoVencido() {
        String vencido = PREFIXO + "excluido-8-dias";
        String ausente = PREFIXO + "sem-binario";
        String recente = PREFIXO + "excluido-1-dia";
        String publicado = PREFIXO + "publicado";
        String retirado = PREFIXO + "retirado";

        UUID idVencido = inserirArquivo(vencido);
        UUID idAusente = inserirArquivo(ausente);
        UUID idRecente = inserirArquivo(recente);
        UUID idPublicado = inserirArquivo(publicado);
        UUID idRetirado = inserirArquivo(retirado);
        gravarBinario(vencido, "binário vencido");
        gravarBinario(recente, "binário recente");
        gravarBinario(publicado, "binário publicado");
        gravarBinario(retirado, "binário retirado");

        inserirMaterial("excluido-8-dias", idVencido, "EXCLUIDO", "8 days");
        inserirMaterial("sem-binario", idAusente, "EXCLUIDO", "8 days");
        inserirMaterial("excluido-1-dia", idRecente, "EXCLUIDO", "1 day");
        inserirMaterial("publicado", idPublicado, "PUBLICADO", "8 days");
        inserirMaterial("retirado", idRetirado, "RETIRADO", "8 days");

        RelatorioDeExpurgo primeira = expurgo.expurgar();

        assertThat(primeira.examinados()).isEqualTo(2);
        assertThat(primeira.purgados()).isEqualTo(2);
        assertThat(primeira.falhas()).isZero();
        assertThat(primeira.execucaoId()).isNotNull();
        assertThat(primeira.janela()).isEqualTo("7d");
        assertThat(primeira.itensPurgados()).extracting(RelatorioDeExpurgo.ItemPurgado::chaveInterna)
                .containsExactlyInAnyOrder(vencido, ausente);
        assertThat(primeira.itensPurgados()).filteredOn(item -> item.chaveInterna().equals(ausente))
                .singleElement().satisfies(item -> assertThat(item.binarioRemovido()).isFalse());
        assertThat(primeira.resumo()).contains("[expurgo-retencao]")
                .contains("examinados=2 purgados=2 falhas=0 janela=7d");
        assertThat(primeira.linhas()).anySatisfy(linha ->
                assertThat(linha).contains("purgado chave=" + vencido));

        assertThat(binarioExiste(vencido)).isFalse();
        assertThat(estadoDoArquivo(idVencido)).isEqualTo("PURGADO");
        assertThat(estadoDoArquivo(idAusente)).isEqualTo("PURGADO");
        for (String chave : List.of(recente, publicado, retirado)) {
            assertThat(binarioExiste(chave)).as("binário %s fica intacto", chave).isTrue();
            assertThat(estadoDoArquivo(registroId(chave))).as("file_asset %s fica intacto", chave)
                    .isEqualTo("VALIDADO");
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM material WHERE slug LIKE ?", Integer.class,
                PREFIXO + "%")).isEqualTo(5);

        RelatorioDeExpurgo segunda = expurgo.expurgar();
        assertThat(segunda.examinados()).isZero();
        assertThat(segunda.purgados()).isZero();
        assertThat(segunda.falhas()).isZero();
        assertThat(segunda.execucaoId()).isNotEqualTo(primeira.execucaoId());
        System.out.println(primeira.linhaDe(primeira.itensPurgados().get(0)));
        System.out.println(primeira.resumo());
        System.out.println(segunda.resumo());
    }

    @Test
    @DisplayName("falha de storage em um arquivo não impede os demais; próxima passada conclui a retomada")
    void falhaNoStorageEhContadaEProximaExecucaoRetoma() throws IOException {
        String bloqueado = PREFIXO + "storage-indisponivel";
        String livre1 = PREFIXO + "livre-1";
        String livre2 = PREFIXO + "livre-2";
        UUID idBloqueado = inserirArquivo(bloqueado);
        UUID idLivre1 = inserirArquivo(livre1);
        UUID idLivre2 = inserirArquivo(livre2);
        inserirMaterial("storage-indisponivel", idBloqueado, "EXCLUIDO", "8 days");
        inserirMaterial("livre-1", idLivre1, "EXCLUIDO", "8 days");
        inserirMaterial("livre-2", idLivre2, "EXCLUIDO", "8 days");
        gravarBinario(livre1, "binário livre 1");
        gravarBinario(livre2, "binário livre 2");

        Path caminhoBloqueado = propriedadesDeArmazenamento.diretorio().resolve(bloqueado);
        Files.createDirectories(caminhoBloqueado);
        Files.writeString(caminhoBloqueado.resolve("impede-remocao"), "ocupado", StandardCharsets.UTF_8);

        RelatorioDeExpurgo primeira = expurgo.expurgar();

        assertThat(primeira.examinados()).isEqualTo(3);
        assertThat(primeira.purgados()).isEqualTo(2);
        assertThat(primeira.falhas()).isEqualTo(1);
        assertThat(primeira.motivosDeFalha()).singleElement().asString().contains("chave=" + bloqueado);
        assertThat(estadoDoArquivo(idBloqueado)).isEqualTo("VALIDADO");
        assertThat(estadoDoArquivo(idLivre1)).isEqualTo("PURGADO");
        assertThat(estadoDoArquivo(idLivre2)).isEqualTo("PURGADO");
        assertThat(binarioExiste(livre1)).isFalse();
        assertThat(binarioExiste(livre2)).isFalse();

        Files.delete(caminhoBloqueado.resolve("impede-remocao"));
        Files.delete(caminhoBloqueado);
        RelatorioDeExpurgo segunda = expurgo.expurgar();

        assertThat(segunda.examinados()).isEqualTo(1);
        assertThat(segunda.purgados()).isEqualTo(1);
        assertThat(segunda.falhas()).isZero();
        assertThat(segunda.itensPurgados()).singleElement()
                .satisfies(item -> assertThat(item.binarioRemovido()).isFalse());
        assertThat(estadoDoArquivo(idBloqueado)).isEqualTo("PURGADO");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM material WHERE slug LIKE ?", Integer.class,
                PREFIXO + "%")).isEqualTo(3);
        System.out.println(segunda.linhaDe(segunda.itensPurgados().get(0)));
        System.out.println(segunda.resumo());
    }

    @Test
    @DisplayName("agendamento fica desligado em teste e passada na subida respeita a propriedade")
    void agendamentoETarefaAoIniciarRespeitamConfiguracao() {
        assertThat(contexto.getBeansOfType(ExpurgoDeRetencaoAgendado.class)).isEmpty();
        String chave = PREFIXO + "ao-iniciar";
        UUID arquivo = inserirArquivo(chave);
        inserirMaterial("ao-iniciar", arquivo, "EXCLUIDO", "8 days");
        gravarBinario(chave, "binário para testar runner");

        new ExpurgoDeRetencaoAoIniciar(expurgo, false).run(null);
        assertThat(estadoDoArquivo(arquivo)).isEqualTo("VALIDADO");
        aoIniciar.run(null);
        assertThat(estadoDoArquivo(arquivo)).isEqualTo("VALIDADO");

        new ExpurgoDeRetencaoAoIniciar(expurgo, true).run(null);
        assertThat(estadoDoArquivo(arquivo)).isEqualTo("PURGADO");
        assertThat(binarioExiste(chave)).isFalse();
        System.out.println("[E5.5] ao-iniciar=false preserva e true executa uma passada");
    }

    private UUID inserirArquivo(String chave) {
        return jdbc.queryForObject("""
                INSERT INTO file_asset (chave_interna, nome_original_seguro, tipo_detectado,
                                        tamanho_bytes, checksum, estado_processamento)
                VALUES (?, ?, 'application/pdf', 1024, repeat('d', 64), 'VALIDADO')
                RETURNING id
                """, UUID.class, chave, chave + ".pdf");
    }

    private void inserirMaterial(String sufixo, UUID arquivo, String estado, String idade) {
        UUID conta = jdbc.queryForObject("""
                INSERT INTO account (email, senha_hash, estado, email_verificado_em)
                VALUES (?, 'hash-de-teste-nao-e-segredo', 'ATIVO', now())
                RETURNING id
                """, UUID.class, PREFIXO + sufixo + "@example.test");
        UUID perfil = jdbc.queryForObject("""
                INSERT INTO profile (account_id, nome_publico, slug)
                VALUES (?, 'Autor de expurgo', ?)
                RETURNING id
                """, UUID.class, conta, PREFIXO + sufixo);

        jdbc.update("""
                INSERT INTO material (slug, titulo, descricao, profile_id, category_id, material_type_id,
                                      license_id, file_asset_id, estado_publicacao, publicado_em,
                                      atualizado_em, excluido_em)
                SELECT ?, 'Material de teste', 'Registro para a rotina E5.5.', ?,
                       (SELECT id FROM category WHERE slug = ?),
                       (SELECT id FROM material_type WHERE slug = ?),
                       (SELECT id FROM license WHERE slug = ?), ?, ?, now(), now(),
                       CASE WHEN ? = 'EXCLUIDO' THEN now() - (?::interval) ELSE NULL END
                """, PREFIXO + sufixo, perfil, CATEGORIA, TIPO, LICENCA, arquivo, estado, estado, idade);
    }

    private void gravarBinario(String chave, String conteudo) {
        try {
            Path origem = areaDeApoio.resolve(chave + ".pdf");
            Files.writeString(origem, conteudo, StandardCharsets.UTF_8);
            storage.guardar(chave, origem);
        } catch (IOException falha) {
            throw new UncheckedIOException("não foi possível preparar o arquivo " + chave, falha);
        }
    }

    private boolean binarioExiste(String chave) {
        return Files.isRegularFile(propriedadesDeArmazenamento.diretorio().resolve(chave));
    }

    private String estadoDoArquivo(UUID id) {
        return jdbc.queryForObject("SELECT estado_processamento FROM file_asset WHERE id = ?", String.class, id);
    }

    private UUID registroId(String chave) {
        return jdbc.queryForObject("SELECT id FROM file_asset WHERE chave_interna = ?", UUID.class, chave);
    }

    private void limparDadosDeTeste() {
        List<String> chaves = jdbc.queryForList("SELECT chave_interna FROM file_asset WHERE chave_interna LIKE ?",
                String.class, PREFIXO + "%");
        jdbc.update("DELETE FROM material WHERE slug LIKE ?", PREFIXO + "%");
        jdbc.update("DELETE FROM file_asset WHERE chave_interna LIKE ?", PREFIXO + "%");
        jdbc.update("DELETE FROM profile WHERE slug LIKE ?", PREFIXO + "%");
        jdbc.update("DELETE FROM account WHERE email LIKE ?", PREFIXO + "%");
        Path diretorio = propriedadesDeArmazenamento.diretorio();
        for (String chave : chaves) {
            apagarRecursivo(diretorio.resolve(chave));
        }
        if (Files.isDirectory(diretorio)) {
            try (Stream<Path> entradas = Files.list(diretorio)) {
                entradas.filter(caminho -> caminho.getFileName().toString().startsWith(PREFIXO))
                        .forEach(this::apagarRecursivo);
            } catch (IOException falha) {
                throw new UncheckedIOException("não foi possível limpar " + diretorio, falha);
            }
        }
    }

    private void apagarRecursivo(Path caminho) {
        if (!Files.exists(caminho)) {
            return;
        }
        try (Stream<Path> caminhos = Files.walk(caminho)) {
            for (Path item : caminhos.sorted((a, b) -> b.compareTo(a)).toList()) {
                Files.deleteIfExists(item);
            }
        } catch (IOException falha) {
            throw new UncheckedIOException("não foi possível limpar " + caminho, falha);
        }
    }
}
