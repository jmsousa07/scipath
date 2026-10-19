package com.scipath.material;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Derivação do {@code slug} público do material a partir do título (RN009, RNF025).
 *
 * <p>O {@code slug} é gerado <b>no servidor</b> — o contrato v0.5 não o aceita no corpo da
 * publicação — e é a chave estável da URL pública ({@code /materiais/{slug}}, {@code urlPublica}
 * do 201). Ele nasce uma vez, no <code>INSERT</code>, e nada nesta etapa o reescreve: a edição de
 * metadados da Etapa 5 pode mudar título e descrição sem trocar o endereço do material já
 * publicado.
 *
 * <p><b>A regra de transformação é a mesma do slug de perfil</b> ({@code GeradorDeSlug}, E3.2):
 * decomposição canônica (NFD), remoção do acento, caixa rebaixada com {@link Locale#ROOT} (para o
 * resultado não depender do locale da máquina — o mesmo cuidado da busca por texto, ADR-0008) e
 * troca de tudo que não é letra ou dígito por hífen. As duas classes não se conhecem porque os
 * <i>recortes</i> diferem: o perfil tem lista de slugs reservados (o primeiro segmento das rotas
 * do produto, {@code SlugsReservados}) e o material não precisa dela — o slug do material é o
 * segundo segmento de {@code /materiais/{slug}}, e nenhuma rota do produto mora ali. Copiar a
 * transformação, e não a lista, é o que mantém cada uma decidindo o que é dela.
 *
 * <p>Nome sem nenhum caractere aproveitável (título "!!!") não pode virar slug vazio: o formato do
 * contrato exige ao menos um caractere, e o {@code CHECK material_slug_formato} do V1 recusaria a
 * linha. Nesse caso a base é {@link #SLUG_DE_RESERVA}, como no perfil.
 */
final class SlugDeMaterial {

    /** Mesmo teto do {@code SlugRecurso} do contrato ({@code maxLength: 120}) e do CHECK do V1. */
    static final int COMPRIMENTO_MAXIMO = 120;

    /** Base usada quando o título não gera nenhum caractere aproveitável. */
    static final String SLUG_DE_RESERVA = "material";

    private SlugDeMaterial() {
    }

    /**
     * Slug do título, já no formato do contrato ({@code ^[a-z0-9]+(-[a-z0-9]+)*$}).
     *
     * <p>É determinística: o mesmo título produz sempre o mesmo slug, e é por isso que a
     * unicidade pode ser resolvida por sufixo numérico ({@link #comSufixo}) sem inventar nada
     * que dependa de relógio ou de sorteio na primeira tentativa.
     */
    static String derivar(String titulo) {
        String semAcento = Normalizer
                .normalize(titulo == null ? "" : titulo, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");

        String slug = semAcento.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+", "")
                .replaceAll("-+$", "");

        return slug.isEmpty() ? SLUG_DE_RESERVA : limitar(slug);
    }

    /**
     * Candidato com sufixo numérico ({@code introducao-a-algoritmos-2}), para quando o slug base
     * já pertence a outro material. O resultado continua dentro do teto de comprimento e sem
     * hífen no fim (o formato proíbe).
     */
    static String comSufixo(String base, int ordinal) {
        String sufixo = "-" + ordinal;
        String encurtada = base.length() + sufixo.length() > COMPRIMENTO_MAXIMO
                ? limitar(base.substring(0, COMPRIMENTO_MAXIMO - sufixo.length()))
                : base;
        return encurtada + sufixo;
    }

    /** Corta no teto sem deixar o slug terminar em hífen (o formato do contrato proíbe). */
    private static String limitar(String slug) {
        if (slug.length() <= COMPRIMENTO_MAXIMO) {
            return slug;
        }
        return slug.substring(0, COMPRIMENTO_MAXIMO).replaceAll("-+$", "");
    }
}
