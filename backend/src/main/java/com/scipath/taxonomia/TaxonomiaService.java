package com.scipath.taxonomia;

import org.springframework.stereotype.Service;

/**
 * Regra de leitura da taxonomia de referência (RF018). O serviço existe por um motivo
 * só: o significado do filtro {@code ativo} e a montagem do envelope são decisão de
 * negócio, não de transporte — o controller não deve decidir o que "ativo omitido"
 * quer dizer.
 */
@Service
public class TaxonomiaService {

    private final TaxonomiaRepositorio repositorio;

    public TaxonomiaService(TaxonomiaRepositorio repositorio) {
        this.repositorio = repositorio;
    }

    /**
     * O parâmetro de estado ativo tem três casos, não dois (contrato v0.2):
     * <ul>
     *   <li>omitido ({@code null}) — só itens ativos: é o que o formulário de publicação
     *       e os filtros de busca podem oferecer;</li>
     *   <li>{@code true} — igual ao omitido. Um cliente que manda o default do próprio
     *       contrato não pode receber resultado diferente de quem não manda nada;</li>
     *   <li>{@code false} — inclui os desativados. Serve para a curadoria conferir o que
     *       já saiu do ar (ADR-0006: desativar item de taxonomia é migração, não tela).</li>
     * </ul>
     */
    public Pagina<CategoriaResposta> listarCategorias(Boolean ativo, int pagina, int tamanho) {
        return envelopar(repositorio.listarCategorias(incluiDesativados(ativo), pagina, tamanho), pagina, tamanho);
    }

    public Pagina<TipoMaterialResposta> listarTiposMaterial(Boolean ativo, int pagina, int tamanho) {
        return envelopar(repositorio.listarTiposMaterial(incluiDesativados(ativo), pagina, tamanho), pagina, tamanho);
    }

    public Pagina<LicencaResposta> listarLicencas(Boolean ativo, int pagina, int tamanho) {
        return envelopar(repositorio.listarLicencas(incluiDesativados(ativo), pagina, tamanho), pagina, tamanho);
    }

    private static boolean incluiDesativados(Boolean ativo) {
        // Só o `false` explícito amplia a consulta. Tratar `null` como `false` faria a
        // listagem pública oferecer categoria desativada para publicar material.
        return Boolean.FALSE.equals(ativo);
    }

    private static <T> Pagina<T> envelopar(ResultadoPaginado<T> resultado, int pagina, int tamanho) {
        return new Pagina<>(resultado.itens(), pagina, tamanho, resultado.total());
    }
}
