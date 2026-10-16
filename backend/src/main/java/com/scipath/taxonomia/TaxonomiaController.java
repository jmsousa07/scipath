package com.scipath.taxonomia;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Leitura pública da taxonomia de referência (RF018) — os três endpoints que o contrato
 * v0.2 publica na tag {@code taxonomia}: {@code GET /api/categorias},
 * {@code GET /api/tipos-material} e {@code GET /api/licencas}.
 *
 * <p>São leituras puras de dados de referência: sem autenticação, sem escrita e sem
 * regra além do filtro de estado ativo. Quem publica material escolhe entre os itens
 * devolvidos aqui — o {@code slug} é o valor que o cliente guarda e reenvia (ADR-0006).
 *
 * <p>Os três endpoints compartilham o mesmo trio de parâmetros de consulta, exatamente
 * como o contrato os declara ({@code FiltroAtivo}, {@code NumeroPagina},
 * {@code TamanhoPagina}); por isso os três métodos têm a mesma assinatura de paginação.
 *
 * <p>O teto de {@code size} é <b>recusado</b> (400), não truncado: um pedido de 5000 itens
 * que devolvesse 100 em silêncio faria o cliente paginar sobre um total que ele não
 * consegue alcançar (RNF021).
 */
@RestController
@RequestMapping("/api")
@Validated
public class TaxonomiaController {

    /** Teto por requisição, igual ao {@code maximum: 100} do contrato (RNF021). */
    static final int TAMANHO_MAXIMO = 100;

    /** Página padrão do contrato: lista de referência é curta, 20 cabe tudo em uma chamada. */
    static final int TAMANHO_PADRAO = 20;

    private final TaxonomiaService taxonomia;

    public TaxonomiaController(TaxonomiaService taxonomia) {
        this.taxonomia = taxonomia;
    }

    @GetMapping("/categorias")
    public Pagina<CategoriaResposta> categorias(
            @RequestParam(name = "ativo", required = false) Boolean ativo,
            @RequestParam(name = "page", defaultValue = "0") @Min(value = 0, message = "deve ser 0 ou mais") int page,
            @RequestParam(name = "size", defaultValue = "" + TAMANHO_PADRAO)
            @Min(value = 1, message = "deve ser 1 ou mais")
            @Max(value = TAMANHO_MAXIMO, message = "deve ser " + TAMANHO_MAXIMO + " ou menos") int size) {
        return taxonomia.listarCategorias(ativo, page, size);
    }

    @GetMapping("/tipos-material")
    public Pagina<TipoMaterialResposta> tiposMaterial(
            @RequestParam(name = "ativo", required = false) Boolean ativo,
            @RequestParam(name = "page", defaultValue = "0") @Min(value = 0, message = "deve ser 0 ou mais") int page,
            @RequestParam(name = "size", defaultValue = "" + TAMANHO_PADRAO)
            @Min(value = 1, message = "deve ser 1 ou mais")
            @Max(value = TAMANHO_MAXIMO, message = "deve ser " + TAMANHO_MAXIMO + " ou menos") int size) {
        return taxonomia.listarTiposMaterial(ativo, page, size);
    }

    @GetMapping("/licencas")
    public Pagina<LicencaResposta> licencas(
            @RequestParam(name = "ativo", required = false) Boolean ativo,
            @RequestParam(name = "page", defaultValue = "0") @Min(value = 0, message = "deve ser 0 ou mais") int page,
            @RequestParam(name = "size", defaultValue = "" + TAMANHO_PADRAO)
            @Min(value = 1, message = "deve ser 1 ou mais")
            @Max(value = TAMANHO_MAXIMO, message = "deve ser " + TAMANHO_MAXIMO + " ou menos") int size) {
        return taxonomia.listarLicencas(ativo, page, size);
    }
}
