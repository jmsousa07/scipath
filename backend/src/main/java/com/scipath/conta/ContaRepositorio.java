package com.scipath.conta;

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
     * chega normalizado — é isso que faz {@code Maria.Silva@Exemplo.BR} e
     * {@code maria.silva@exemplo.br} serem a <b>mesma</b> identidade (RN009). A coluna é
     * {@code UNIQUE}, então a consulta é uma leitura por índice e devolve no máximo uma linha.
     */
    public Optional<Conta> buscarPorEmail(String email) {
        return jdbc.sql("SELECT " + CAMPOS + " FROM account WHERE email = :email")
                .param("email", email)
                .query((rs, linha) -> {
                    OffsetDateTime verificadoEm = rs.getObject("email_verificado_em", OffsetDateTime.class);
                    return new Conta(
                            rs.getObject("id", UUID.class),
                            rs.getString("email"),
                            rs.getString("senha_hash"),
                            EstadoConta.valueOf(rs.getString("estado")),
                            verificadoEm == null ? null : verificadoEm.toInstant());
                })
                .optional();
    }
}
