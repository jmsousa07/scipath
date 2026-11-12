package com.scipath.conta;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Escrita e leitura da tabela {@code account} (V1). SQL explícito com {@link JdbcClient},
 * como o resto do projeto — o cadastro não precisa de ORM para três colunas.
 *
 * <p><b>Nada de migração aqui.</b> O card E3.2 não cria schema: {@code account} já existe no
 * {@code V1__modelo_mvp.sql} com as invariantes do ADR-0003 ({@code account_email_unico},
 * {@code account_email_normalizado}, {@code account_estado_valido}) e são elas que garantem
 * o que o serviço promete — a checagem em {@link #buscarPorEmail} é conveniência para a
 * mensagem do formulário, não a garantia (RNF011: a regra vale no banco, não só na tela).
 *
 * <p><b>Nenhum valor vindo da requisição entra na string do SQL</b>: o e-mail é parâmetro
 * ligado ({@code :email}), e o hash chega pronto do serviço — nunca a senha em texto puro.
 */
@Repository
public class ContaRepositorio {

    private static final String CAMPOS = "id, email, senha_hash, estado, email_verificado_em";

    private final JdbcClient jdbc;

    public ContaRepositorio(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Grava a conta e devolve o {@code id} gerado pelo banco.
     *
     * <p>{@code email_verificado_em} fica nulo (default da coluna): a conta nasce
     * {@code PENDENTE_VERIFICACAO} e é {@code email_verificado_em IS NULL} que diz isso de
     * forma durável. O serviço já normalizou o e-mail; gravar valor fora do formato faria o
     * {@code CHECK account_email_normalizado} recusar a linha, que é o comportamento
     * desejado (falha alto, não grava identidade ambígua).
     *
     * @param email     e-mail já normalizado (minúsculas, sem espaços)
     * @param senhaHash hash bcrypt custo 12 da senha — nunca a senha
     * @param estado    estado inicial da conta (o cadastro usa
     *                  {@link EstadoConta#PENDENTE_VERIFICACAO})
     */
    public UUID inserir(String email, String senhaHash, EstadoConta estado) {
        return jdbc.sql("INSERT INTO account (email, senha_hash, estado)"
                        + " VALUES (:email, :senhaHash, :estado) RETURNING id")
                .param("email", email)
                .param("senhaHash", senhaHash)
                .param("estado", estado.name())
                .query(UUID.class)
                .single();
    }

    /**
     * Conta identificada pelo e-mail normalizado, se existir.
     *
     * <p>A busca é pela coluna como ela está gravada (minúsculas, sem espaços) e o parâmetro
     * chega normalizado — é isso que faz {@code Maria.Silva@exemplo.invalid} e
     * {@code maria.silva@exemplo.invalid} serem a <b>mesma</b> identidade (RN009). A coluna é
     * {@code UNIQUE}, então a consulta é uma leitura por índice e devolve no máximo uma linha.
     */
    public Optional<Conta> buscarPorEmail(String email) {
        return jdbc.sql("SELECT " + CAMPOS + " FROM account WHERE email = :email")
                .param("email", email)
                .query(ContaRepositorio::conta)
                .optional();
    }

    /**
     * Conta pelo identificador — o caminho de quem <b>já sabe</b> de quem é a operação.
     *
     * <p>É o que o reenvio de verificação usa (E3.4): a sessão autenticada entrega o
     * {@code contaId} e não carrega endereço nenhum (o schema {@code SessaoAtual} do contrato não
     * tem e-mail — RNF013), então o destinatário da mensagem só pode vir da linha da conta. É
     * também a razão de o endereço nunca ser aceito do corpo da requisição naquele endpoint.
     */
    public Optional<Conta> buscarPorId(UUID id) {
        return jdbc.sql("SELECT " + CAMPOS + " FROM account WHERE id = :id")
                .param("id", id)
                .query(ContaRepositorio::conta)
                .optional();
    }

    /**
     * Registra a verificação do e-mail e leva a conta a {@code ATIVO} — o estado que a publicação
     * exige (ADR-0003 (b)).
     *
     * <p>Duas guardas escritas na própria instrução, e não no serviço, porque são invariantes da
     * linha: {@code COALESCE} preserva o <b>primeiro</b> instante de verificação (confirmar de
     * novo não reescreve a data de quem já era verificado) e o {@code CASE} só promove conta em
     * {@code PENDENTE_VERIFICACAO} — uma conta {@code SUSPENSO} que confirme o e-mail
     * <b>continua</b> suspensa. Confirmação de e-mail não é caminho para desfazer suspensão.
     *
     * @return linhas afetadas (0 quando o {@code id} não existe — invariante quebrada em outro lugar)
     */
    public int marcarEmailVerificado(UUID contaId, Instant agora) {
        return jdbc.sql("UPDATE account"
                        + " SET email_verificado_em = COALESCE(email_verificado_em, :agora),"
                        + "     estado = CASE WHEN estado = 'PENDENTE_VERIFICACAO' THEN 'ATIVO' ELSE estado END,"
                        + "     atualizado_em = :agora"
                        + " WHERE id = :contaId")
                .param("contaId", contaId)
                .param("agora", OffsetDateTime.ofInstant(agora, java.time.ZoneOffset.UTC))
                .update();
    }

    /**
     * Troca o hash da senha (recuperação de acesso, RF010).
     *
     * <p>O parâmetro é o <b>hash</b> bcrypt 12, nunca a senha: quem codifica é o serviço, com o
     * codificador único do projeto ({@link com.scipath.config.ConfiguracaoDeSenha}), e o texto
     * puro não chega a este método (RNF009). {@code atualizado_em} acompanha toda escrita na
     * linha (comentário do V1: não há trigger, a regra fica no serviço).
     */
    public int atualizarSenhaHash(UUID contaId, String senhaHash, Instant agora) {
        return jdbc.sql("UPDATE account SET senha_hash = :senhaHash, atualizado_em = :agora WHERE id = :contaId")
                .param("contaId", contaId)
                .param("senhaHash", senhaHash)
                .param("agora", OffsetDateTime.ofInstant(agora, java.time.ZoneOffset.UTC))
                .update();
    }

    /** Linha de {@code account} no record interno — o mapeamento fica em um lugar só. */
    private static Conta conta(java.sql.ResultSet rs, int linha) throws java.sql.SQLException {
        OffsetDateTime verificadoEm = rs.getObject("email_verificado_em", OffsetDateTime.class);
        return new Conta(
                rs.getObject("id", UUID.class),
                rs.getString("email"),
                rs.getString("senha_hash"),
                EstadoConta.valueOf(rs.getString("estado")),
                verificadoEm == null ? null : verificadoEm.toInstant());
    }
}
