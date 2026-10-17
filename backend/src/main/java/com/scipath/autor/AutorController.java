package com.scipath.autor;

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
 * Perfil público do autor (RF006) — {@code GET /api/autores/{slug}}, a tela que fecha a
 * navegação material → autor → categoria (RF007): o slug que o material publica em
 * {@code autor.slug} é o valor desta rota, e o slug de categoria que ela devolve é o valor
 * que {@code GET /api/materiais?categoria=} aceita (RN013).
 *
 * <p>A rota é da tag {@code descoberta} do contrato v0.3, somente-leitura e sem autenticação:
 * visitante sem conta é quem navega o acervo.
 *
 * <p>Validação de entrada no servidor (RNF011, §14.3): slug fora do formato, {@code page}
 * negativo e {@code size} fora de 1..100 são recusados com <b>400</b> no
 * {@code ProblemDetail} padrão, com o parâmetro culpado em {@code errors[]} — recusa
 * explícita, nunca correção silenciosa. Slug bem formado que não é público (inexistente ou
 * de conta não ativa) responde <b>404</b>, indistinguível entre os casos (RN002, CA008).
 *
 * <p>O teto de {@code size} é recusado, não truncado, como nos outros endpoints paginados do
 * contrato (RNF021): devolver 100 itens para um pedido de 5000 faria o cliente paginar sobre
 * um total que ele não alcança. As constantes abaixo são as do contrato —
 * {@code TamanhoPagina} e {@code SlugRecurso} —, não as de outro controller: os mesmos
 * números aparecem em {@code MateriaisController} e {@code TaxonomiaController} porque os
 * três derivam do mesmo contrato, e é isso que os mantém iguais sem acoplamento entre as
 * telas.
 */
@RestController
@RequestMapping("/api")
@Validated
public class AutorController {

    /** Teto por requisição, igual ao {@code maximum: 100} do contrato (RNF021). */
    static final int TAMANHO_MAXIMO = 100;

    /** Itens por página do contrato, quando {@code size} não é informado. */
    static final int TAMANHO_PADRAO = 20;

    /** Teto de comprimento do slug no contrato ({@code SlugRecurso}, {@code maxLength: 120}). */
    static final int COMPRIMENTO_MAXIMO_SLUG = 120;

    /** Formato de slug do contrato — o mesmo de {@code SlugRecurso} e dos filtros da listagem. */
    private static final String FORMATO_SLUG = "^[a-z0-9]+(-[a-z0-9]+)*$";

    private final AutorService autores;

    public AutorController(AutorService autores) {
        this.autores = autores;
    }

    @GetMapping("/autores/{slug}")
    public AutorPublicoResposta autor(
            @PathVariable("slug")
            @Pattern(regexp = FORMATO_SLUG,
                    message = "deve ser um slug (minúsculas, dígitos e hífen), como maria-silva")
            @Size(max = COMPRIMENTO_MAXIMO_SLUG,
                    message = "deve ter no máximo " + COMPRIMENTO_MAXIMO_SLUG + " caracteres")
            String slug,
            @RequestParam(name = "page", defaultValue = "0")
            @Min(value = 0, message = "deve ser 0 ou mais") int page,
            @RequestParam(name = "size", defaultValue = "" + TAMANHO_PADRAO)
            @Min(value = 1, message = "deve ser 1 ou mais")
            @Max(value = TAMANHO_MAXIMO, message = "deve ser " + TAMANHO_MAXIMO + " ou menos") int size) {
        return autores.perfil(slug, page, size);
    }
}
