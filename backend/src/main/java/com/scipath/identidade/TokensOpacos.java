package com.scipath.identidade;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/**
 * Geração e hash dos valores opacos que viajam em cookie (token de sessão e token CSRF).
 *
 * <p><b>O token de sessão é credencial</b>: 32 bytes de {@link SecureRandom} (256 bits),
 * codificados em Base64 URL-safe sem preenchimento, e o banco guarda apenas o SHA-256 dele.
 * Quem ler a tabela não consegue montar a cookie de ninguém, e o valor em claro existe só na
 * resposta que o criou — nunca em log (RNF009). O padrão é o mesmo que o ADR-0003 (c) fixa
 * para o token de verificação de e-mail.
 *
 * <p>O hash é <b>sem sal</b> de propósito: o valor de entrada já tem 256 bits de entropia,
 * então tabela pré-computada e força bruta são inviáveis, e o sal não traria ganho nenhum —
 * o que o sal protege é segredo de baixa entropia (senha), e senha é bcrypt (ADR-0003 (a)).
 *
 * <p>A comparação de dois valores iguais (o cabeçalho CSRF contra a cookie) é feita com
 * {@link MessageDigest#isEqual}, que não para no primeiro byte diferente: comparar com
 * {@code equals} deixa o tempo de resposta contar quantos bytes iniciais acertaram.
 */
@Component
public class TokensOpacos {

    /** 32 bytes = 256 bits, como o token de verificação do ADR-0003 (c). */
    private static final int BYTES = 32;

    private final SecureRandom aleatorio = new SecureRandom();

    /** Token novo, opaco, URL-safe (sem {@code +}, {@code /} ou {@code =} para caber em cookie). */
    public String gerar() {
        byte[] valor = new byte[BYTES];
        aleatorio.nextBytes(valor);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(valor);
    }

    /** SHA-256 do valor, em hexadecimal minúsculo — o que vai para a coluna. */
    public static String hash(String valor) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(valor.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 é obrigatório em qualquer JVM desde sempre: ausência é ambiente quebrado.
            throw new IllegalStateException("SHA-256 indisponível nesta JVM", e);
        }
    }

    /** Igualdade em tempo constante — para token que veio do cliente. */
    public static boolean iguais(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
