package com.scipath.conta;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Derivação do {@code slug} inicial do perfil a partir do nome público (RF008, RN009).
 *
 * <p>O contrato é explícito: o {@code slug} <b>não</b> é enviado pelo cliente no cadastro —
 * quem o deriva é o servidor ({@code CadastroConta}, v0.4). O formato exigido é o mesmo
 * {@code profile_slug_formato} do V1 ({@code ^[a-z0-9]+(-[a-z0-9]+)*$}), e é o banco quem
 * recusa um valor fora dele: a derivação aqui não é sugestão, é a única forma de o INSERT
 * passar.
 *
 * <p>A ordem das operações não é detalhe: primeiro a decomposição canônica (NFD) separa
 * letra e acento, depois o acento sai, e só então a caixa cai. Sem a decomposição,
 * "João" viraria "jo-o" e o slug carregaria o acento convertido em hífen. A caixa é
 * dobrada com {@link Locale#ROOT} para o resultado não depender do locale da máquina
 * (o mesmo cuidado que a busca por texto toma, ADR-0008).
 *
 * <p>Nome sem nenhum caractere aproveitável ("!!!", "Ω") não pode virar slug vazio — o
 * formato exige ao menos um caractere. Nesse caso a base é {@link #SLUG_DE_RESERVA} e a
 * unicidade é resolvida pelo serviço, com o mesmo mecanismo dos nomes repetidos.
 */
final class GeradorDeSlug {

    /** Mesmo teto do {@code slug} de perfil no contrato ({@code EdicaoPerfil}, maxLength 120). */
    static final int COMPRIMENTO_MAXIMO = 120;

    /** Base usada quando o nome público não gera nenhum caractere aproveitável. */
    static final String SLUG_DE_RESERVA = "autor";

    private GeradorDeSlug() {
    }

    /**
     * Slug do nome público, já no formato do contrato.
     *
     * @param nomePublico nome como o cliente enviou (o cadastro não o normaliza)
     */
    static String derivar(String nomePublico) {
        String semAcento = Normalizer
                .normalize(nomePublico == null ? "" : nomePublico, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");

        String slug = semAcento.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+", "")
                .replaceAll("-+$", "");

        return slug.isEmpty() ? SLUG_DE_RESERVA : limitar(slug);
    }

    /**
     * Candidato com sufixo numérico ({@code maria-silva-2}), para o caso de o slug base já
     * pertencer a outro perfil. O resultado continua dentro do teto de comprimento.
     */
    static String comSufixo(String base, int ordinal) {
        String sufixo = "-" + ordinal;
        String encurtada = base.length() + sufixo.length() > COMPRIMENTO_MAXIMO
                ? limitar(base.substring(0, COMPRIMENTO_MAXIMO - sufixo.length()))
                : base;
        return encurtada + sufixo;
    }

    /** Corta no teto sem deixar o slug terminar em hífen (o formato proíbe). */
    private static String limitar(String slug) {
        if (slug.length() <= COMPRIMENTO_MAXIMO) {
            return slug;
        }
        return slug.substring(0, COMPRIMENTO_MAXIMO).replaceAll("-+$", "");
    }
}
