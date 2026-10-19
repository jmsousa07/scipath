package com.scipath.material;

import static com.scipath.material.ApoioDePublicacao.PREFIXO;
import static com.scipath.material.ApoioDePublicacao.camposValidos;
import static com.scipath.material.ApoioDePublicacao.corpo;
import static com.scipath.material.ApoioDePublicacao.emailDe;
import static com.scipath.material.ApoioDePublicacao.pdf;
import static com.scipath.material.ApoioDePublicacao.tipoDoErro;
import static org.assertj.core.api.Assertions.assertThat;

import com.scipath.arquivo.PropriedadesDeArmazenamento;
import com.scipath.arquivo.PropriedadesDeUpload;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * O que a publicação deixa para trás quando a gravação do material <b>falha depois do
 * recebimento</b> (card E4.4, RN008, ADR-0005 §2).
 *
 * <p><b>A falha é provocada no lugar certo.</b> Um gatilho no banco — criado e derrubado dentro
 * do próprio caso — faz o {@code INSERT} em {@code material} explodir. É a única maneira de o
 * passo 3 do RN008 falhar por <i>falha real do banco</i>, e não por um dublê: o serviço inteiro
 * roda, o arquivo é validado, é movido para o armazenamento definitivo e é a <b>gravação</b> que
 * não acontece — exatamente a situação que o RN008 §4 descreve.
 *
 * <p><b>O que se espera depois da falha</b> (as três afirmações que o caso mede):
 * <ol>
 *   <li><b>Nada de material.</b> A linha não existe e o material não é público — a falha não
 *       publica meio material.</li>
 *   <li><b>O arquivo é {@code ORFAO}.</b> O binário existe no armazenamento definitivo e nenhum
 *       material aponta para ele: é essa marcação que o coletor do E4.5 procura, e sem ela o
 *       registro continuaria dizendo {@code VALIDADO} — estado de arquivo que a publicação pode
 *       servir.</li>
 *   <li><b>A área temporária fica vazia</b> (RN012): o arquivo foi movido, não copiado.</li>
 * </ol>
 *
 * <p>O gatilho é derrubado em {@code finally}: deixá-lo vivo faria <b>todo</b> material do banco
 * de teste deixar de ser gravado, e as outras baterias passariam a falhar por causa deste card.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("falha na gravação do material: rollback e arquivo órfão (RN008)")
class RollbackDaPublicacaoIntegrationTest {

    /** Função + gatilho que forçam a falha do passo 3 do RN008. */
    private static final String FUNCAO_DA_FALHA = """
            CREATE OR REPLACE FUNCTION teste_e4_4_falha_ao_gravar() RETURNS trigger AS $$
            BEGIN
                RAISE EXCEPTION 'falha forcada pelo teste do E4.4 (rollback do RN008)';
            END;
            $$ LANGUAGE plpgsql
            """;

    private static final String GATILHO_DA_FALHA = """
            CREATE TRIGGER teste_e4_4_explosao BEFORE INSERT ON material
                FOR EACH ROW EXECUTE FUNCTION teste_e4_4_falha_ao_gravar()
            """;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PropriedadesDeUpload upload;

    @Autowired
    private PropriedadesDeArmazenamento armazenamento;

    private ApoioDePublicacao apoio;

    @BeforeEach
    void preparar() {
        apoio = new ApoioDePublicacao(rest, jdbc, upload, armazenamento);
        apoio.limpar();
        derrubarGatilho();
    }

    @AfterEach
    void limpar() {
        derrubarGatilho();
        apoio.limpar();
    }

    @Test
    @DisplayName("falha ao gravar o material: 500, nada público, arquivo marcado ORFAO e temporária vazia")
    void falhaNaGravacaoMarcaOArquivoComoOrfao() {
        var sessao = apoio.autorAtivo("rollback");
        String nome = PREFIXO + "rollback.pdf";
        byte[] conteudo = pdf(1024);

        armarGatilho();
        ResponseEntity<String> resposta = apoio.publicarValido(sessao, conteudo, nome);

        System.out.println("[E4.4] 500 gravação forçada a falhar -> " + resposta.getBody());
        assertThat(resposta.getStatusCode().value()).isEqualTo(500);
        assertThat(tipoDoErro(resposta)).isEqualTo("urn:scipath:erro:interno");
        assertThat(resposta.getBody())
                .as("RNF012: a resposta não entrega caminho, chave nem mensagem do banco")
                .doesNotContain("falha forcada").doesNotContain("teste_e4_4")
                .doesNotContain(apoio.armazenamentoDefinitivo().toString());

        // 1. Nenhum material — nem linha, nem detalhe público.
        assertThat(apoio.totalDeMateriais(camposValidos().get("titulo")))
                .as("a transação da gravação foi desfeita por inteiro")
                .isZero();
        assertThat(apoio.ler(null, "/api/materiais/ordenacao-por-comparacao-na-pratica").getStatusCode().value())
                .as("nada foi publicado: o slug nem chegou a existir")
                .isEqualTo(404);
        assertThat(apoio.materiaisDoAutor(emailDe("rollback"))).isEmpty();

        // 2. O arquivo ficou registrado, com o binário no armazenamento definitivo, marcado ORFAO.
        Map<String, Object> arquivo = apoio.linhaDoArquivo(nome);
        String chave = (String) arquivo.get("chave_interna");
        System.out.println("[E4.4] file_asset " + chave + " -> " + arquivo.get("estado_processamento"));
        assertThat(arquivo.get("estado_processamento"))
                .as("ADR-0005 §2: arquivo sem material é ORFAO, não VALIDADO")
                .isEqualTo("ORFAO");
        assertThat(apoio.arquivoArmazenado(chave))
                .as("o movimento já tinha acontecido: é por isso que a marcação é necessária")
                .isTrue();
        assertThat(apoio.arquivosNoArmazenamento()).hasSize(1);

        // 3. RN012: a área temporária não guarda o arquivo movido.
        assertThat(apoio.arquivosNaAreaTemporaria()).isEmpty();
    }

    @Test
    @DisplayName("depois da falha o sistema não fica travado: nova publicação usa o slug livre e não adota o órfão")
    void novaPublicacaoDepoisDaFalha() {
        var sessao = apoio.autorAtivo("retomada");

        armarGatilho();
        ResponseEntity<String> falha = apoio.publicarValido(sessao, pdf(512), PREFIXO + "retomada-um.pdf");
        assertThat(falha.getStatusCode().value()).isEqualTo(500);
        derrubarGatilho();

        ResponseEntity<String> sucesso = apoio.publicarValido(sessao, pdf(768), PREFIXO + "retomada-dois.pdf");

        System.out.println("[E4.4] 201 depois da falha -> " + sucesso.getBody());
        assertThat(sucesso.getStatusCode().value()).isEqualTo(201);
        assertThat(corpo(sucesso).get("slug").asText())
                .as("o slug do material que não nasceu voltou a estar livre — o rollback não deixou resíduo")
                .isEqualTo("ordenacao-por-comparacao-na-pratica");

        // O órfão continua órfão: a publicação nova grava o arquivo dela, não adota o anterior.
        assertThat(apoio.linhaDoArquivo(PREFIXO + "retomada-um.pdf").get("estado_processamento"))
                .as("o arquivo da tentativa falha continua disponível para o coletor (E4.5)")
                .isEqualTo("ORFAO");
        assertThat(apoio.linhaDoArquivo(PREFIXO + "retomada-dois.pdf").get("estado_processamento"))
                .as("o arquivo do material publicado segue intacto")
                .isEqualTo("VALIDADO");
        assertThat(apoio.materiaisDoAutor(emailDe("retomada"))).hasSize(1);
    }

    // ------------------------------------------------------------------ gatilho

    private void armarGatilho() {
        jdbc.execute(FUNCAO_DA_FALHA);
        jdbc.execute(GATILHO_DA_FALHA);
    }

    private void derrubarGatilho() {
        jdbc.execute("DROP TRIGGER IF EXISTS teste_e4_4_explosao ON material");
        jdbc.execute("DROP FUNCTION IF EXISTS teste_e4_4_falha_ao_gravar()");
    }
}
