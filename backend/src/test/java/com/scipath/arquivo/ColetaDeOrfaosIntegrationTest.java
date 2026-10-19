package com.scipath.arquivo;

import static org.assertj.core.api.Assertions.assertThat;

import com.scipath.arquivo.RelatorioDeColeta.ItemRemovido;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
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
 * O coletor de arquivos órfãos (card E4.5, RN008, RN012, ADR-0005 §2 e §3).
 *
 * <p>Integração porque as duas perguntas do coletor são do banco e do disco ao mesmo tempo — "esta
 * linha de {@code file_asset} tem material?" e "este binário ainda está lá?" —, e responder
 * qualquer uma delas com um duble não provaria nada: o defeito que esta classe existe para impedir
 * é apagar o arquivo de um material vivo, e o que decide isso são as linhas de {@code material}.
 *
 * <p><b>A janela de segurança é do perfil de teste: 1 hora</b>
 * ({@code src/test/resources/application-test.yml}). É o mesmo valor de produção lido pela mesma
 * linha de código, com outro número — os órfãos desta suíte têm 3 horas (coletáveis) e um tem agora
 * (não coletável). Com a janela cravada em 24 h no código, os de 3 horas não seriam coletados e
 * esta classe reprovaria.
 *
 * <p><b>Falha forçada com gatilho real no banco</b> (como no E4.4): a interrupção no meio é
 * provocada por um {@code TRIGGER ... BEFORE DELETE} que recusa a remoção de uma chave marcada, e
 * derrubado depois — não por um duble do repositório, que provaria o duble.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("coletor de arquivos órfãos (E4.5)")
class ColetaDeOrfaosIntegrationTest {

    /** Marcador próprio desta classe: não colide com as baterias do E4.2/E4.3/E4.4. */
    private static final String PREFIXO = "teste-e4-5-";

    /** Sufixo da chave que o gatilho do teste bloqueia — o único que falha na primeira execução. */
    private static final String CHAVE_BLOQUEADA = PREFIXO + "bloqueada";

    private static final String CATEGORIA = "computacao-e-tecnologia";
    private static final String TIPO = "nota-de-aula";
    private static final String LICENCA = "cc-by-4.0";

    /** Idade dos órfãos coletáveis: 3 h, contra a janela de 1 h do perfil de teste. */
    private static final String IDADE_COLETAVEL = "3 hours";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PropriedadesDeArmazenamento armazenamento;

    @Autowired
    private StorageService storage;

    @Autowired
    private PropriedadesDeColeta propriedades;

    @Autowired
    private ColetorDeOrfaosService coletor;

    /** O bean do contexto: prova que a classe está fiada como {@code ApplicationRunner} na subida. */
    @Autowired
    private ColetaDeOrfaosAoIniciar coletaAoIniciar;

    @TempDir
    Path areaDeApoio;

    @BeforeEach
    void limparAntes() {
        limparDadosDeTeste();
    }

    @AfterEach
    void removerDadosDeTeste() {
        derrubarGatilho();
        limparDadosDeTeste();
    }

    // ------------------------------------------------------------------ o caso central

    @Test
    @DisplayName("órfão mais antigo que a janela é removido: o binário e o registro, e o relatório conta o que saiu")
    void orfaosAntigosSaoRemovidos() {
        String primeiro = PREFIXO + "orfaos-1";
        String segundo = PREFIXO + "orfaos-2";
        UUID linhaDoPrimeiro = inserirArquivo(primeiro, "VALIDADO", IDADE_COLETAVEL);
        UUID linhaDoSegundo = inserirArquivo(segundo, "ORFAO", IDADE_COLETAVEL);
        gravarBinario(primeiro, "conteúdo órfão 1");
        gravarBinario(segundo, "conteúdo órfão 2");

        RelatorioDeColeta relatorio = coletor.coletar();

        assertThat(relatorio.examinados()).as("os dois órfãos são candidatos").isEqualTo(2);
        assertThat(relatorio.removidos()).isEqualTo(2);
        assertThat(relatorio.falhas()).isZero();
        assertThat(relatorio.itensRemovidos()).extracting(ItemRemovido::chaveInterna)
                .containsExactlyInAnyOrder(primeiro, segundo);
        assertThat(relatorio.itensRemovidos()).allSatisfy(item ->
                assertThat(item.binarioRemovido()).as("havia binário para remover").isTrue());

        assertThat(binarioExiste(primeiro)).as("o binário saiu do armazenamento").isFalse();
        assertThat(binarioExiste(segundo)).isFalse();
        assertThat(registro(linhaDoPrimeiro)).as("a linha saiu do banco").isEmpty();
        assertThat(registro(linhaDoSegundo)).isEmpty();

        // A observabilidade do card: o identificador da execução e as três contagens numa linha,
        // sem abrir o banco. `janela=PT1H` é a janela CONFIGURADA no perfil de teste.
        assertThat(relatorio.resumo())
                .isEqualTo(RelatorioDeColeta.PREFIXO + " execucao=" + relatorio.execucaoId()
                        + " examinados=2 removidos=2 falhas=0 janela=PT1H");
        assertThat(relatorio.linhas()).anySatisfy(linha ->
                assertThat(linha).contains("removido chave=" + primeiro).contains("binario=removido"));
        System.out.println("[E4.5] " + relatorio.linhaDe(relatorio.itensRemovidos().get(0)));
        System.out.println("[E4.5] " + relatorio.resumo());
    }

    @Test
    @DisplayName("a segunda execução não muda nada: zero examinados, zero removidos, zero falhas")
    void segundaExecucaoEhSemEfeito() {
        String chave = PREFIXO + "idempotente";
        UUID linha = inserirArquivo(chave, "ORFAO", IDADE_COLETAVEL);
        gravarBinario(chave, "conteúdo órfão");

        RelatorioDeColeta primeira = coletor.coletar();
        assertThat(primeira.examinados()).isEqualTo(1);
        assertThat(primeira.removidos()).isEqualTo(1);

        RelatorioDeColeta segunda = coletor.coletar();

        assertThat(segunda.examinados()).as("nada sobrou para examinar").isZero();
        assertThat(segunda.removidos()).isZero();
        assertThat(segunda.falhas()).isZero();
        assertThat(segunda.execucaoId()).as("cada execução tem o seu identificador")
                .isNotEqualTo(primeira.execucaoId());
        assertThat(registro(linha)).isEmpty();
        assertThat(binarioExiste(chave)).isFalse();
        System.out.println("[E4.5] " + segunda.resumo());
    }

    // ------------------------------------------------------------------ o que nunca pode ser apagado

    @Test
    @DisplayName("arquivo de material vivo não é tocado: PUBLICADO, RETIRADO e EXCLUIDO ficam intactos")
    void arquivoDeMaterialVivoNuncaEhApagado() {
        List<String> estados = List.of("PUBLICADO", "RETIRADO", "EXCLUIDO");

        for (String estado : estados) {
            String sufixo = estado.toLowerCase();
            String chave = PREFIXO + "vivo-" + sufixo;
            UUID linha = inserirArquivo(chave, "VALIDADO", IDADE_COLETAVEL);
            gravarBinario(chave, "conteúdo de material vivo");
            // Publicado ontem: dentro dos 7 dias de retenção do EXCLUIDO (ADR-0005 §3), que é o
            // caso que o card nomeia — a purga do excluído é da Etapa 5, não daqui.
            inserirMaterial(sufixo, linha, estado, "1 day");
        }

        RelatorioDeColeta relatorio = coletor.coletar();

        assertThat(relatorio.examinados()).as("nenhum arquivo de material vivo é candidato").isZero();
        assertThat(relatorio.removidos()).isZero();
        for (String estado : estados) {
            String chave = PREFIXO + "vivo-" + estado.toLowerCase();
            assertThat(binarioExiste(chave)).as("o binário de material %s continua lá", estado).isTrue();
            assertThat(registroDaChave(chave)).as("o registro de material %s continua lá", estado).isPresent();
        }
        System.out.println("[E4.5] nenhum dos 3 estados de material vivo foi tocado: " + relatorio.resumo());
    }

    @Test
    @DisplayName("material EXCLUIDO fora dos 7 dias de retenção também não é coletado: a purga é da Etapa 5")
    void excluidoForaDaRetencaoNaoEhColetadoAqui() {
        String chave = PREFIXO + "excluido-antigo";
        UUID linha = inserirArquivo(chave, "VALIDADO", IDADE_COLETAVEL);
        gravarBinario(chave, "conteúdo de material excluído há muito tempo");
        inserirMaterial("excluido-antigo", linha, "EXCLUIDO", "30 days");

        RelatorioDeColeta relatorio = coletor.coletar();

        assertThat(relatorio.examinados())
                .as("o guarda é 'existe material', em qualquer estado — a purga do EXCLUIDO é da Etapa 5")
                .isZero();
        assertThat(binarioExiste(chave)).isTrue();
        assertThat(registro(linha)).isPresent();
        System.out.println("[E4.5] EXCLUIDO de 30 dias preservado (RF017 é da Etapa 5): " + relatorio.resumo());
    }

    // ------------------------------------------------------------------ a janela de segurança

    @Test
    @DisplayName("órfão dentro da janela de segurança não é coletado")
    void orfaoDentroDaJanelaNaoEhColetado() {
        String chave = PREFIXO + "recente";
        UUID linha = inserirArquivo(chave, "ORFAO", "0 seconds");
        gravarBinario(chave, "conteúdo órfão recente");

        RelatorioDeColeta relatorio = coletor.coletar();

        assertThat(relatorio.examinados())
                .as("recebido agora: a janela de segurança (%s) ainda não passou", propriedades.getJanela())
                .isZero();
        assertThat(binarioExiste(chave)).as("o binário continua no armazenamento").isTrue();
        assertThat(registro(linha)).as("o registro continua no banco").isPresent();
        System.out.println("[E4.5] órfão recente preservado pela janela " + propriedades.getJanela()
                + ": " + relatorio.resumo());
    }

    // ------------------------------------------------------------------ interrupção e retomada

    @Test
    @DisplayName("interrupção no meio: uma falha não derruba a varredura e a execução seguinte termina o serviço")
    void falhaNoMeioNaoPioraOEstadoERetomada() {
        String bloqueada = CHAVE_BLOQUEADA;
        String livre1 = PREFIXO + "livre-1";
        String livre2 = PREFIXO + "livre-2";
        UUID linhaBloqueada = inserirArquivo(bloqueada, "ORFAO", IDADE_COLETAVEL);
        inserirArquivo(livre1, "ORFAO", IDADE_COLETAVEL);
        inserirArquivo(livre2, "ORFAO", IDADE_COLETAVEL);
        gravarBinario(bloqueada, "conteúdo bloqueado");
        gravarBinario(livre1, "conteúdo livre 1");
        gravarBinario(livre2, "conteúdo livre 2");

        // A interrupção é REAL: o banco recusa a remoção do registro da chave bloqueada, como se o
        // processo tivesse morrido entre apagar o binário e apagar a linha.
        criarGatilho();

        RelatorioDeColeta primeira = coletor.coletar();

        assertThat(primeira.examinados()).isEqualTo(3);
        assertThat(primeira.removidos()).as("os outros dois não foram arrastados pela falha")
                .isEqualTo(2);
        assertThat(primeira.falhas()).isEqualTo(1);
        assertThat(primeira.motivosDeFalha()).singleElement().asString()
                .contains("chave=" + bloqueada).contains("motivo=");
        assertThat(binarioExiste(livre1)).isFalse();
        assertThat(binarioExiste(livre2)).isFalse();
        assertThat(registroDaChave(livre1)).isEmpty();
        assertThat(registroDaChave(livre2)).isEmpty();

        // O estado depois da interrupção: o binário da bloqueada já saiu (a ordem é binário →
        // registro), a linha continua lá, sem material. Nada ficou invisível — o banco ainda
        // descreve o arquivo, e é isso que permite a retomada.
        assertThat(binarioExiste(bloqueada)).as("o binário já tinha sido removido").isFalse();
        assertThat(registro(linhaBloqueada)).as("a linha continua no banco, sem material").isPresent();
        System.out.println("[E4.5] " + primeira.linhaDeFalha(primeira.motivosDeFalha().get(0)));

        derrubarGatilho();

        RelatorioDeColeta segunda = coletor.coletar();

        assertThat(segunda.examinados()).as("a execução seguinte encontra o que sobrou").isEqualTo(1);
        assertThat(segunda.removidos()).isEqualTo(1);
        assertThat(segunda.falhas()).isZero();
        assertThat(segunda.itensRemovidos()).singleElement()
                .satisfies(item -> assertThat(item.binarioRemovido())
                        .as("o binário já não estava lá: a retomada conclui o serviço").isFalse());
        assertThat(registro(linhaBloqueada)).isEmpty();
        System.out.println("[E4.5] " + segunda.resumo());
    }

    @Test
    @DisplayName("registro sem binário (execução anterior interrompida) é concluído, e o log diz binario=ausente")
    void retomaExecucaoInterrompidaEntreAsDuasRemocoes() {
        String chave = PREFIXO + "sem-binario";
        UUID linha = inserirArquivo(chave, "ORFAO", IDADE_COLETAVEL);
        // Nenhum binário gravado: é o estado exato em que uma execução interrompida deixa o sistema.

        RelatorioDeColeta relatorio = coletor.coletar();

        assertThat(relatorio.examinados()).isEqualTo(1);
        assertThat(relatorio.removidos()).isEqualTo(1);
        assertThat(relatorio.falhas()).isZero();
        assertThat(relatorio.linhaDe(relatorio.itensRemovidos().get(0))).contains("binario=ausente");
        assertThat(registro(linha)).isEmpty();
        System.out.println("[E4.5] " + relatorio.linhaDe(relatorio.itensRemovidos().get(0)));
    }

    // ------------------------------------------------------------------ o comando manual de verificação

    @Test
    @DisplayName("a coleta na subida (make coleta:orfaos) só roda quando a propriedade está ligada")
    void coletaAoIniciarRespeitaAPropriedade() {
        String chave = PREFIXO + "ao-iniciar";
        UUID linha = inserirArquivo(chave, "ORFAO", IDADE_COLETAVEL);
        gravarBinario(chave, "conteúdo órfão");

        new ColetaDeOrfaosAoIniciar(coletor, false).run(null);

        assertThat(registro(linha)).as("desligada por padrão: a subida não varre nada").isPresent();
        assertThat(binarioExiste(chave)).isTrue();

        // O bean do contexto (perfil de teste: scipath.coleta.ao-iniciar=false) tem o mesmo
        // comportamento: é a fiação real que o Spring usaria na subida.
        coletaAoIniciar.run(null);
        assertThat(registro(linha)).as("o bean do contexto está desligado no perfil de teste").isPresent();

        new ColetaDeOrfaosAoIniciar(coletor, true).run(null);

        assertThat(registro(linha)).as("ligada: a passada única da subida remove o órfão").isEmpty();
        assertThat(binarioExiste(chave)).isFalse();
        System.out.println("[E4.5] coleta na subida: desligada não varre; ligada remove o órfão " + chave);
    }

    // ---------------------------------------------------------------------------- auxiliares

    /** Uma linha de {@code file_asset} sem material, com o {@code enviado_em} recuado no tempo. */
    private UUID inserirArquivo(String chave, String estado, String idade) {
        return jdbc.queryForObject("""
                INSERT INTO file_asset (chave_interna, nome_original_seguro, tipo_detectado,
                                        tamanho_bytes, checksum, estado_processamento, enviado_em)
                VALUES (?, ?, 'application/pdf', 1024, repeat('c', 64), ?, now() - (?::interval))
                RETURNING id
                """, UUID.class, chave, chave + ".pdf", estado, idade);
    }

    /** Material vivo apontando para o arquivo — o que o coletor não pode tocar, em qualquer estado. */
    private void inserirMaterial(String sufixo, UUID arquivo, String estadoPublicacao, String idade) {
        UUID conta = jdbc.queryForObject("""
                INSERT INTO account (email, senha_hash, estado, email_verificado_em)
                VALUES (?, 'hash-de-teste-nao-e-segredo', 'ATIVO', now())
                RETURNING id
                """, UUID.class, PREFIXO + sufixo + "@example.test");
        UUID perfil = jdbc.queryForObject(
                "INSERT INTO profile (account_id, nome_publico, slug) VALUES (?, 'Autor de Coleta', ?)"
                        + " RETURNING id",
                UUID.class, conta, PREFIXO + sufixo);

        jdbc.update("""
                INSERT INTO material (slug, titulo, descricao, profile_id, category_id, material_type_id,
                                      license_id, file_asset_id, estado_publicacao, publicado_em, atualizado_em)
                SELECT ?, 'Material de coleta', 'Material usado para exercitar o guarda do coletor.',
                       ?,
                       (SELECT id FROM category WHERE slug = ?),
                       (SELECT id FROM material_type WHERE slug = ?),
                       (SELECT id FROM license WHERE slug = ?),
                       ?, ?, now() - (?::interval), now() - (?::interval)
                """, PREFIXO + sufixo, perfil, CATEGORIA, TIPO, LICENCA, arquivo, estadoPublicacao,
                idade, idade);
    }

    private void gravarBinario(String chave, String conteudo) {
        try {
            Path origem = areaDeApoio.resolve(chave + ".pdf");
            Files.write(origem, conteudo.getBytes(StandardCharsets.UTF_8));
            storage.guardar(chave, origem);
        } catch (IOException falha) {
            throw new UncheckedIOException("não foi possível preparar o arquivo de apoio " + chave, falha);
        }
    }

    private boolean binarioExiste(String chave) {
        return Files.isRegularFile(armazenamento.diretorio().resolve(chave));
    }

    /** A chave da linha de {@code file_asset} pelo {@code id}, quando ela ainda existe. */
    private Optional<String> registro(UUID id) {
        return jdbc.query("SELECT chave_interna FROM file_asset WHERE id = ?",
                (rs, linha) -> rs.getString(1), id).stream().findFirst();
    }

    /** A chave da linha de {@code file_asset} pela própria chave, quando ela ainda existe. */
    private Optional<String> registroDaChave(String chave) {
        return jdbc.query("SELECT chave_interna FROM file_asset WHERE chave_interna = ?",
                (rs, linha) -> rs.getString(1), chave).stream().findFirst();
    }

    /**
     * O gatilho que provoca a falha real: recusa o {@code DELETE} da chave marcada, como o banco
     * recusaria por qualquer motivo de verdade. Criado e derrubado dentro do caso — a suíte não
     * deixa gatilho nenhum para trás.
     */
    private void criarGatilho() {
        jdbc.execute("CREATE OR REPLACE FUNCTION teste_e4_5_bloqueio() RETURNS trigger AS $fn$"
                + " BEGIN IF OLD.chave_interna = '" + CHAVE_BLOQUEADA + "' THEN"
                + " RAISE EXCEPTION 'falha forçada no DELETE de file_asset (teste E4.5)';"
                + " END IF; RETURN OLD; END; $fn$ LANGUAGE plpgsql");
        jdbc.execute("CREATE TRIGGER teste_e4_5_bloqueio BEFORE DELETE ON file_asset"
                + " FOR EACH ROW EXECUTE FUNCTION teste_e4_5_bloqueio()");
    }

    private void derrubarGatilho() {
        jdbc.execute("DROP TRIGGER IF EXISTS teste_e4_5_bloqueio ON file_asset");
        jdbc.execute("DROP FUNCTION IF EXISTS teste_e4_5_bloqueio()");
    }

    /**
     * Remove só o que esta suíte criou, na ordem que as chaves estrangeiras exigem
     * ({@code material} → {@code file_asset} → {@code profile} → {@code account}), e apaga os
     * binários pela chave registrada — inclusive os que a suíte criou e a coleta já removeu.
     */
    private void limparDadosDeTeste() {
        List<String> chaves = jdbc.queryForList("SELECT chave_interna FROM file_asset"
                + " WHERE chave_interna LIKE ?", String.class, PREFIXO + "%");
        jdbc.update("DELETE FROM material WHERE slug LIKE ?", PREFIXO + "%");
        jdbc.update("DELETE FROM file_asset WHERE chave_interna LIKE ?", PREFIXO + "%");
        jdbc.update("DELETE FROM profile WHERE slug LIKE ?", PREFIXO + "%");
        jdbc.update("DELETE FROM account WHERE email LIKE ?", PREFIXO + "%");
        for (String chave : chaves) {
            apagar(armazenamento.diretorio().resolve(chave));
        }
        Path diretorio = armazenamento.diretorio();
        if (!Files.isDirectory(diretorio)) {
            return;
        }
        try (Stream<Path> entradas = Files.list(diretorio)) {
            for (Path arquivo : entradas
                    .filter(caminho -> caminho.getFileName().toString().startsWith(PREFIXO)).toList()) {
                apagar(arquivo);
            }
        } catch (IOException falha) {
            throw new UncheckedIOException("não foi possível limpar " + diretorio, falha);
        }
    }

    private static void apagar(Path arquivo) {
        try {
            Files.deleteIfExists(arquivo);
        } catch (IOException falha) {
            throw new UncheckedIOException("não foi possível apagar " + arquivo, falha);
        }
    }
}
