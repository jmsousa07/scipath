package com.scipath.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Integração contra o PostgreSQL <b>real</b> (ADR-0004 ajuste 5, §17.2).
 *
 * <p>Duas coisas são provadas aqui, e a segunda é a razão de o teste existir:
 *
 * <ol>
 *   <li><b>As migrações Flyway são aplicadas</b> num banco de verdade — o histórico
 *       registra V1 e V2 com sucesso e as oito tabelas do DRS §11 estão lá. Sem banco, o
 *       contexto nem sobe (o Hikari do perfil {@code test} falha na inicialização), então
 *       este teste não passa vazio.
 *   <li><b>As invariantes de integridade que o E1.2/E1.3 provaram à mão agora são
 *       automáticas:</b> categoria inexistente é recusada pela FK (RN005) e estado de
 *       publicação fora do enum é recusado pelo CHECK (ADR-0005 §3). Era evidência em
 *       script; passa a ser teste que a suíte roda toda vez.
 * </ol>
 *
 * <p><b>Roda contra {@code scipath_test}, nunca contra {@code scipath}.</b> O datasource
 * vem de {@code src/test/resources/application-test.yml} (perfil {@code test} ativado pelo
 * surefire) e o alvo {@code make test} não carrega o {@code .env}. O primeiro teste
 * confere isso explicitamente: se alguém apontar a suíte para o banco de desenvolvimento,
 * ele falha em vez de esconder o problema.
 *
 * <p>Mesma configuração de contexto de {@code SciPathApplicationTests}: o Spring reusa o
 * contexto já subido e o teste não custa uma segunda subida (máquina de 954 MB — AGENTS.md).
 *
 * <p>As tentativas de INSERT inválido rodam dentro de uma transação que é revertida: o
 * banco de teste não fica sujo e cada execução parte do mesmo estado.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("integração com o Postgres real (scipath_test)")
class MigracaoIntegracaoTest {

    private static final String BANCO_DE_TESTE = "scipath_test";

    private static final List<String> TABELAS_DO_MODELO = List.of(
            "account", "verification_token", "profile",
            "category", "material_type", "license", "file_asset", "material");

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Environment ambiente;

    @Autowired
    private PlatformTransactionManager gerenciadorDeTransacao;

    private JdbcTemplate jdbc() {
        return new JdbcTemplate(dataSource);
    }

    @Test
    @DisplayName("o datasource aponta para scipath_test, não para o banco de desenvolvimento")
    void rodaContraOBancoDeTeste() {
        String url = ambiente.getProperty("spring.datasource.url");
        assertThat(url)
                .as("a suíte precisa falar com o banco de teste; application-test.yml é a fonte")
                .isNotNull()
                .endsWith("/" + BANCO_DE_TESTE);

        String bancoAtual = jdbc().queryForObject("SELECT current_database()", String.class);
        assertThat(bancoAtual).isEqualTo(BANCO_DE_TESTE);
        System.out.println("[E1.5] datasource da suíte -> " + url);
    }

    @Test
    @DisplayName("as migrações V1 e V2 foram aplicadas com sucesso")
    void migracoesAplicadas() {
        JdbcTemplate jdbc = jdbc();

        List<String> versoes = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = true ORDER BY installed_rank",
                String.class);
        assertThat(versoes)
                .as("histórico do Flyway: V1 (modelo) e V2 (seed) aplicadas")
                .contains("1", "2");

        List<String> tabelas = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
                String.class);
        assertThat(tabelas)
                .as("as oito tabelas do DRS §11 existem no banco de teste")
                .containsAll(TABELAS_DO_MODELO);

        Integer comCheckDeEstado = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.table_constraints "
                        + "WHERE table_name = 'material' AND constraint_name = 'material_estado_valido'",
                Integer.class);
        assertThat(comCheckDeEstado)
                .as("o CHECK do estado de publicação veio na migração, não foi perdido")
                .isEqualTo(1);

        System.out.println("[E1.5] migrações aplicadas em " + BANCO_DE_TESTE + " -> versões " + versoes);
    }

    @Test
    @DisplayName("a taxonomia da seed V2 está carregada")
    void seedDaTaxonomiaCarregada() {
        JdbcTemplate jdbc = jdbc();

        Integer categorias = jdbc.queryForObject("SELECT count(*) FROM category", Integer.class);
        Integer tipos = jdbc.queryForObject("SELECT count(*) FROM material_type", Integer.class);
        Integer licencas = jdbc.queryForObject("SELECT count(*) FROM license", Integer.class);
        assertThat(categorias).as("13 categorias da seed (ADR-0006)").isGreaterThanOrEqualTo(13);
        assertThat(tipos).as("6 tipos de material da seed").isGreaterThanOrEqualTo(6);
        assertThat(licencas).as("7 licenças da seed").isGreaterThanOrEqualTo(7);

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM category WHERE slug = 'computacao-e-tecnologia' AND ativo", Integer.class))
                .as("categoria semeada presente e ativa")
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM material_type WHERE slug = 'nota-de-aula' AND ativo", Integer.class))
                .as("tipo de material semeado presente e ativo")
                .isEqualTo(1);

        System.out.println("[E1.5] seed em " + BANCO_DE_TESTE + " -> "
                + categorias + " categorias, " + tipos + " tipos, " + licencas + " licenças");
    }

    @Test
    @DisplayName("categoria inexistente é recusada pela FK (RN005)")
    void categoriaInexistenteRecusadaPelaFk() {
        TransactionTemplate transacao = new TransactionTemplate(gerenciadorDeTransacao);
        JdbcTemplate jdbc = jdbc();
        String sufixo = UUID.randomUUID().toString().substring(0, 8);

        assertThatThrownBy(() -> transacao.execute(status -> {
            inserirDependenciasValidas(jdbc, sufixo);
            // category_id que não existe em category: a FK tem de recusar.
            jdbc.update("INSERT INTO material (slug, titulo, descricao, profile_id, category_id,"
                            + " material_type_id, license_id, file_asset_id, estado_publicacao)"
                            + " SELECT ?, 'Titulo', 'Descricao', p.id, '" + UUID.randomUUID() + "',"
                            + " (SELECT id FROM material_type WHERE slug = 'nota-de-aula'),"
                            + " (SELECT id FROM license WHERE slug = 'cc-by-4.0'), f.id, 'PUBLICADO'"
                            + " FROM profile p, file_asset f WHERE p.slug = ? AND f.chave_interna = ?",
                    "material-fk-" + sufixo, "perfil-" + sufixo, "chave-" + sufixo);
            return null;
        }))
                .as("sem categoria válida não existe material (RN005)")
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause()
                .hasMessageContaining("material_category_id_fkey");

        System.out.println("[E1.5] categoria inexistente -> recusada pela FK material_category_id_fkey");
    }

    @Test
    @DisplayName("estado de publicação fora do enum é recusado pelo CHECK (ADR-0005 §3)")
    void estadoForaDoEnumRecusadoPeloCheck() {
        TransactionTemplate transacao = new TransactionTemplate(gerenciadorDeTransacao);
        JdbcTemplate jdbc = jdbc();
        String sufixo = UUID.randomUUID().toString().substring(0, 8);

        assertThatThrownBy(() -> transacao.execute(status -> {
            inserirDependenciasValidas(jdbc, sufixo);
            // categoria válida (semeada), mas estado fora de PUBLICADO|RETIRADO|EXCLUIDO.
            jdbc.update("INSERT INTO material (slug, titulo, descricao, profile_id, category_id,"
                            + " material_type_id, license_id, file_asset_id, estado_publicacao)"
                            + " SELECT ?, 'Titulo', 'Descricao', p.id,"
                            + " (SELECT id FROM category WHERE slug = 'computacao-e-tecnologia'),"
                            + " (SELECT id FROM material_type WHERE slug = 'nota-de-aula'),"
                            + " (SELECT id FROM license WHERE slug = 'cc-by-4.0'), f.id, 'EM_REVISAO'"
                            + " FROM profile p, file_asset f WHERE p.slug = ? AND f.chave_interna = ?",
                    "material-check-" + sufixo, "perfil-" + sufixo, "chave-" + sufixo);
            return null;
        }))
                .as("estado fora do enum não entra (ADR-0005 §3)")
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause()
                .hasMessageContaining("material_estado_valido");

        System.out.println("[E1.5] estado fora do enum -> recusado pelo CHECK material_estado_valido");
    }

    /** Cria conta, perfil e arquivo válidos dentro da transação corrente (revertidos no fim). */
    private static void inserirDependenciasValidas(JdbcTemplate jdbc, String sufixo) {
        jdbc.update("INSERT INTO account (email, senha_hash, estado, email_verificado_em)"
                        + " VALUES (?, 'hash-de-teste-nao-e-segredo', 'ATIVO', now())",
                "integracao-" + sufixo + "@example.test");
        jdbc.update("INSERT INTO profile (account_id, nome_publico, slug)"
                        + " SELECT id, 'Perfil de integração', ? FROM account WHERE email = ?",
                "perfil-" + sufixo, "integracao-" + sufixo + "@example.test");
        jdbc.update("INSERT INTO file_asset (chave_interna, nome_original_seguro, tipo_detectado,"
                        + " tamanho_bytes, checksum, estado_processamento)"
                        + " VALUES (?, 'amostra.pdf', 'application/pdf', 1024, repeat('a', 64), 'VALIDADO')",
                "chave-" + sufixo);
    }
}