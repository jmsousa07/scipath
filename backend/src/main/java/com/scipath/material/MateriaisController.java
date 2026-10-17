package com.scipath.material;

import com.scipath.taxonomia.Pagina;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Descoberta pública de material (tag {@code descoberta} do contrato v0.3): {@code GET
 * /api/materiais}, a listagem paginada que o visitante sem conta usa para encontrar material
 * (RF002, RF004), e {@code GET /api/materiais/{slug}}, o detalhe de um material (RF005).
 *
 * <p>Os parâmetros da listagem são os do contrato — {@code categoria}, {@code tipo}, {@code
 * ordem}, {@code page}, {@code size}. O {@code q} (busca por texto, RF003) <b>não</b> é
 * declarado aqui: ele é a fronteira do card E2.3, e um parâmetro aceito e ignorado em silêncio
 * seria pior que a ausência dele — o cliente acharia que filtrou.
 *
 * <p>Validação de entrada no servidor (RNF011, §14.3): slug fora do formato e {@code ordem}
 * fora do enum são recusados com <b>400</b> no {@code ProblemDetail} padrão, com o parâmetro
 * culpado em {@code errors[]} — recusa explícita, nunca correção silenciosa. Já {@code slug}
 * bem formado que não existe <b>não</b> é erro na listagem: devolve 200 com lista vazia
 * (RNF004). No detalhe, o mesmo slug bem formado e inexistente responde <b>404</b>, porque ali
 * o recurso pedido é um só — e o 404 é o mesmo de material fora do público (RN002, CA008).
 *
 * <p>O teto de {@code size} é recusado (400), não truncado: um pedido de 5000 itens que
 * devolvesse 100 em silêncio faria o cliente paginar sobre um total que ele não alcança
 * (RNF021). É a mesma decisão que o {@code TaxonomiaController} já toma no mesmo contrato.
 */
@RestController
@RequestMapping("/api")
@Validated
public class MateriaisController {

    /** Teto por requisição, igual ao {@code maximum: 100} do contrato (RNF021). */
    static final int TAMANHO_MAXIMO = 100;

    /** Itens por página do contrato, quando {@code size} não é informado. */
    static final int TAMANHO_PADRAO = 20;

    /** Teto de comprimento do slug no contrato ({@code SlugRecurso}, {@code maxLength: 120}). */
    static final int COMPRIMENTO_MAXIMO_SLUG = 120;

    /** Formato de slug do contrato — o mesmo padrão de {@code FiltroCategoria}/{@code FiltroTipo}. */
    private static final String FORMATO_SLUG = "^[a-z0-9]+(-[a-z0-9]+)*$";

    /** Valores do enum {@code ordem} no contrato (§12.2). */
    private static final String ORDENS_VALIDAS = "recentes|titulo";

    private final MateriaisService materiais;

    public MateriaisController(MateriaisService materiais) {
        this.materiais = materiais;
    }

    @GetMapping("/materiais")
    public Pagina<MaterialResumoResposta> materiais(
            @RequestParam(name = "categoria", required = false)
            @Pattern(regexp = FORMATO_SLUG,
                    message = "deve ser um slug de categoria (minúsculas, dígitos e hífen), como computacao-e-tecnologia")
            String categoria,
            @RequestParam(name = "tipo", required = false)
            @Pattern(regexp = FORMATO_SLUG,
                    message = "deve ser um slug de tipo de material (minúsculas, dígitos e hífen), como nota-de-aula")
            String tipo,
            @RequestParam(name = "ordem", defaultValue = "recentes")
            @Pattern(regexp = ORDENS_VALIDAS, message = "deve ser 'recentes' ou 'titulo'")
            String ordem,
            @RequestParam(name = "page", defaultValue = "0")
            @Min(value = 0, message = "deve ser 0 ou mais") int page,
            @RequestParam(name = "size", defaultValue = "" + TAMANHO_PADRAO)
            @Min(value = 1, message = "deve ser 1 ou mais")
            @Max(value = TAMANHO_MAXIMO, message = "deve ser " + TAMANHO_MAXIMO + " ou menos") int size) {
        return materiais.listar(categoria, tipo, ordem, page, size);
    }

    /**
     * Detalhe do material público (RF005).
     *
     * <p>O {@code slug} é validado aqui e não no serviço: valor fora do formato do contrato é
     * <b>400</b> ("o endereço está malformado"), enquanto slug bem formado que não é público é
     * <b>404</b> ("esse material não existe para você") — são erros diferentes, e misturá-los
     * faria o cliente tratar entrada inválida como recurso ausente. Os dois recortes são os do
     * {@code SlugRecurso} do contrato: formato de slug e no máximo 120 caracteres.
     */
    @GetMapping("/materiais/{slug}")
    public MaterialDetalheResposta material(
            @PathVariable("slug")
            @Pattern(regexp = FORMATO_SLUG,
                    message = "deve ser um slug (minúsculas, dígitos e hífen), como introducao-a-algoritmos-de-ordenacao")
            @Size(max = COMPRIMENTO_MAXIMO_SLUG,
                    message = "deve ter no máximo " + COMPRIMENTO_MAXIMO_SLUG + " caracteres")
            String slug) {
        return materiais.buscarPorSlug(slug);
    }
}
