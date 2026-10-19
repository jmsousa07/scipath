package com.scipath.arquivo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * O guarda da remoção (card E4.3, ADR-0005 §3): o binário só é apagado quando <b>nenhum</b> material
 * aponta para ele.
 *
 * <p>Integração porque a pergunta do guarda é do banco — "existe material para esta chave?" — e
 * responder "existe" com um mock não provaria nada: o defeito que esta classe existe para impedir é
 * exatamente apagar o arquivo de um material vivo (público, retirado ou excluído dentro da janela de
 * retenção), e o que decide isso são as linhas de {@code material}.
 *
 * <p>São três estados de material (mais o caso sem material nenhum): o {@code RETIRADO} continua vivo
 * porque é republicável, e o {@code EXCLUIDO} porque a purga só acontece depois da retenção — quem a
 * fará é a Etapa 5.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("guarda da remoção de arquivo (ADR-0005 §3)")
class RemocaoDeArquivoIntegrationTest {

    /** Marcador próprio desta classe: não colide com a bateria do download ({@code teste-e4-3-}). */
    private static final String PREFIXO = "teste-e4-3b-";

    private static final String CATEGORIA = "computacao-e-tecnologia";
    private static final String TIPO = "nota-de-aula";
    private static final String LICENCA = "cc-by-4.0";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PropriedadesDeArmazenamento propriedades;

    @Autowired
    private StorageService armazenamento;

    @Autowired
    private RemocaoDeArquivoService remocao;

    @TempDir
    Path areaDeApoio;

    @BeforeEach
    void limparAntes() {
        limparDadosDeTeste();
    }

    @AfterEach
    void removerDadosDeTeste() {
        limparDadosDeTeste();
    }

    @Test
    @DisplayName("arquivo sem material apontando é removido, e a segunda chamada responde false sem erro")
    void removeOrfaoEIdempotente() {
        String chave = PREFIXO + "chave-orfa";
        gravarNoArmazenamento(chave, "conteúdo orfão");

        assertThat(remocao.remover(chave))
                .as("arquivo sem material é o que o coletor de órfãos (E4.5) deve remover")
                .isTrue();
        assertThat(arquivoDaChave(chave)).doesNotExist();
        assertThat(remocao.remover(chave))
                .as("a execução repetida do coletor (RN012) não muda o resultado nem falha")
                .isFalse();
        System.out.println("[E4.3] órfão removido: " + chave);
    }

    @Test
    @DisplayName("arquivo de material PUBLICADO, RETIRADO e EXCLUIDO não é removido, e o binário continua lá")
    void recusaRemoverArquivoDeMaterialVivo() {
        List<String> estados = List.of("PUBLICADO", "RETIRADO", "EXCLUIDO");

        for (String estado : estados) {
            String chave = PREFIXO + "chave-" + estado.toLowerCase();
            UUID arquivo = inserirFileAsset(chave);
            inserirMaterial(estado.toLowerCase(), arquivo, estado);
            gravarNoArmazenamento(chave, "conteúdo de material vivo");

            assertThatThrownBy(() -> remocao.remover(chave))
                    .as("material %s continua vivo: o binário não pode ser apagado (ADR-0005 §3)", estado)
                    .isInstanceOf(ArquivoEmUsoPorMaterialException.class)
                    .hasMessageContaining(chave);
            assertThat(arquivoDaChave(chave))
                    .as("nada foi removido: a recusa acontece ANTES da remoção")
                    .exists();
            System.out.println("[E4.3] remoção recusada: chave " + chave + " (material " + estado + ")");
        }
    }

    @Test
    @DisplayName("remover chave sem arquivo no armazenamento responde false: ausência não é erro")
    void removerChaveInexistenteEhFalso() {
        assertThat(remocao.remover(PREFIXO + "chave-que-nunca-existiu")).isFalse();
    }

    // ---------------------------------------------------------------------------- auxiliares

    private Path arquivoDaChave(String chave) {
        return propriedades.diretorio().resolve(chave);
    }

    private void gravarNoArmazenamento(String chave, String conteudo) {
        try {
            Path origem = areaDeApoio.resolve(chave + ".pdf");
            Files.write(origem, conteudo.getBytes(StandardCharsets.UTF_8));
            armazenamento.guardar(chave, origem);
        } catch (IOException falha) {
            throw new UncheckedIOException("não foi possível preparar o arquivo de apoio " + chave, falha);
        }
    }

    private UUID inserirFileAsset(String chave) {
        return jdbc.queryForObject("""
                INSERT INTO file_asset (chave_interna, nome_original_seguro, tipo_detectado,
                                        tamanho_bytes, checksum, estado_processamento)
                VALUES (?, 'teste.pdf', 'application/pdf', 1024, repeat('c', 64), 'VALIDADO')
                RETURNING id
                """, UUID.class, chave);
    }

    private void inserirMaterial(String sufixo, UUID arquivo, String estadoPublicacao) {
        UUID conta = jdbc.queryForObject("""
                INSERT INTO account (email, senha_hash, estado, email_verificado_em)
                VALUES (?, 'hash-de-teste-nao-e-segredo', 'ATIVO', now())
                RETURNING id
                """, UUID.class, PREFIXO + sufixo + "@example.test");
        UUID perfil = jdbc.queryForObject(
                "INSERT INTO profile (account_id, nome_publico, slug) VALUES (?, 'Autor de Remoção', ?) RETURNING id",
                UUID.class, conta, PREFIXO + sufixo);

        jdbc.update("""
                INSERT INTO material (slug, titulo, descricao, profile_id, category_id, material_type_id,
                                      license_id, file_asset_id, estado_publicacao, publicado_em, atualizado_em)
                SELECT ?, 'Material de remoção', 'Material usado para exercitar o guarda de remoção.',
                       ?,
                       (SELECT id FROM category WHERE slug = ?),
                       (SELECT id FROM material_type WHERE slug = ?),
                       (SELECT id FROM license WHERE slug = ?),
                       ?, ?, ?, ?
                """, PREFIXO + sufixo, perfil, CATEGORIA, TIPO, LICENCA, arquivo, estadoPublicacao,
                emUtc(Instant.parse("2026-10-16T12:00:00Z")), emUtc(Instant.parse("2026-10-16T12:00:00Z")));
    }

    private void limparDadosDeTeste() {
        jdbc.update("DELETE FROM material WHERE slug LIKE ?", PREFIXO + "%");
        jdbc.update("DELETE FROM file_asset WHERE chave_interna LIKE ?", PREFIXO + "%");
        jdbc.update("DELETE FROM account WHERE email LIKE ?", PREFIXO + "%");
        Path diretorio = propriedades.diretorio();
        if (!Files.isDirectory(diretorio)) {
            return;
        }
        try (Stream<Path> entradas = Files.list(diretorio)) {
            for (Path arquivo : entradas.filter(caminho -> caminho.getFileName().toString().startsWith(PREFIXO)).toList()) {
                Files.deleteIfExists(arquivo);
            }
        } catch (IOException falha) {
            throw new UncheckedIOException("não foi possível limpar " + diretorio, falha);
        }
    }

    private static OffsetDateTime emUtc(Instant instante) {
        return OffsetDateTime.ofInstant(instante, ZoneOffset.UTC);
    }
}
