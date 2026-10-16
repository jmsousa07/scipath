package com.scipath.material;

import java.util.Arrays;

/**
 * Critério de ordenação da listagem pública — o enum {@code [recentes, titulo]} do parâmetro
 * {@code ordem} no contrato v0.3 (DRS §12.2).
 *
 * <p>Dois critérios, ambos com ordem total (data + desempate, título + desempate): sem ordem
 * total o PostgreSQL não promete nada entre páginas e o cliente veria item repetido ou
 * sumido ao paginar (RN013). Não existe — nem pode ser inventado — critério de "relevância"
 * ou de popularidade: destaque exigiria regra explícita de seleção (§12.2, §12.4).
 *
 * <p>O {@code slug} do enum é o valor aceito no parâmetro de consulta. Ele é a única fonte
 * do vocabulário: o contrato publica os mesmos dois valores, e um valor novo entra no
 * contrato antes de entrar aqui (AGENTS.md regra 1).
 */
public enum OrdemMateriais {

    /** Padrão do contrato: publicado_em decrescente, desempate estável por id. */
    RECENTES("recentes"),

    /** Título crescente, com o mesmo desempate por id. */
    TITULO("titulo");

    private final String slug;

    OrdemMateriais(String slug) {
        this.slug = slug;
    }

    public String slug() {
        return slug;
    }

    /**
     * Converte o valor do parâmetro de consulta no critério correspondente.
     *
     * <p>O caminho HTTP não chega aqui com valor inválido: o controller recusa o que não é
     * {@code recentes} nem {@code titulo} com 400 antes do serviço (parâmetro inválido →
     * 400 é o que o contrato publica). A exceção existe para o serviço não aceitar em
     * silêncio um critério que o repositório não sabe traduzir em ordenação — o pior
     * resultado possível seria devolver a lista numa ordem que ninguém pediu.
     */
    public static OrdemMateriais doParametro(String valor) {
        return Arrays.stream(values())
                .filter(ordem -> ordem.slug.equals(valor))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("ordem de listagem desconhecida: " + valor));
    }
}
