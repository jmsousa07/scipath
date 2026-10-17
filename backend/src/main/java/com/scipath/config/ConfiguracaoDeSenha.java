package com.scipath.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Codificador de senha do MVP: bcrypt com <b>custo 12</b> (ADR-0003 (a), ratificado pelo
 * dono em 2026-10-17).
 *
 * <p>É um {@code @Bean} e não um {@code new} dentro do serviço porque o algoritmo é decisão
 * de produto registrada em ADR, não detalhe de um método: quando o login (E3.3), os tokens
 * (E3.4) e a recuperação de senha (E3.4) precisarem conferir ou gerar hash, eles têm de
 * usar <b>este</b> codificador. Duas instâncias com custos diferentes dariam hashes
 * incompatíveis com a mesma aparência de funcionamento.
 *
 * <p>O custo é constante e explícito: {@code BCryptPasswordEncoder(12)} grava o custo no
 * próprio hash ({@code $2a$12$...}), então o que está no banco é auditável sem consultar
 * código. Migrar para Argon2id exige ADR novo — não é troca silenciosa de configuração.
 *
 * <p>A dependência é {@code spring-security-crypto}, não {@code spring-boot-starter-security}:
 * só o hash é requisito aqui. A cadeia de filtros de autenticação, a sessão e o CSRF são o
 * E3.3 e ligá-los agora mudaria o comportamento de rotas que não têm nada a ver com
 * identidade (§14.2).
 */
@Configuration
public class ConfiguracaoDeSenha {

    /** Custo do bcrypt fixado pelo ADR-0003 (a). */
    static final int CUSTO_BCRYPT = 12;

    @Bean
    public PasswordEncoder codificadorDeSenha() {
        return new BCryptPasswordEncoder(CUSTO_BCRYPT);
    }
}
