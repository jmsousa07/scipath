package com.scipath.material;

import com.scipath.identidade.IdentidadeAutenticada;
import com.scipath.identidade.SessaoAutenticada;
import com.scipath.taxonomia.Pagina;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Descoberta pública de material (tag {@code descoberta} do contrato v0.3): {@code GET
 * /api/materiais}, a listagem paginada que o visitante sem conta usa para encontrar material
 * (RF002, RF003, RF004), e {@code GET /api/materiais/{slug}}, o detalhe de um material (RF005).
 * Publica também o material próprio (tag {@code publicacao} do contrato v0.5):
 * {@code POST /api/materiais}, que recebe metadados, taxonomia e arquivo na <b>mesma</b>
 * requisição {@code multipart/form-data} (RF012, RN008) — a regra de negócio inteira está em
 * {@link PublicacaoDeMaterialService}, e o que fica aqui é só a tradução do formulário em
 * parâmetros e do resultado em {@code 201}.
 *
 * <p>As duas tags convivem no mesmo controller porque compartilham o caminho
 * ({@code /materiais}): ler a lista e publicar nela são o mesmo endereço no contrato, e separar em
 * dois controllers com o mesmo {@code @RequestMapping} só criaria dois lugares para o mesmo mapa
 * de rotas. O que <b>não</b> acontece aqui é decisão de negócio: validação de campo, taxonomia,
 * transação e estado inicial são do serviço.
 *
 * <p>Os parâmetros da listagem são os do contrato — {@code q}, {@code categoria}, {@code tipo},
 * {@code ordem}, {@code page}, {@code size} — e todos são <b>declarados</b>: parâmetro aceito
 * e ignorado em silêncio seria pior que a ausência dele (o cliente acharia que filtrou).
 *
 * <p>A busca por texto (RF003) é declarada como {@code q}, com o teto de 200 caracteres do
 * contrato. Ela <b>não</b> é filtrada por regex: o conteúdo do termo é livre (o DRS exige que
 * caracteres especiais sejam "tratados com segurança") e quem decide o que o termo significa
 * é o repositório, com {@code plainto_tsquery} — operador de busca não existe para o usuário
 * (§12.1, ADR-0008). Termo que não encontra nada é 200 com lista vazia, nunca 404.
 *
 * <p>Validação de entrada no servidor (RNF011, §14.3): slug fora do formato, {@code ordem}
 * fora do enum e {@code q} maior que o teto são recusados com <b>400</b> no
 * {@code ProblemDetail} padrão, com o parâmetro culpado em {@code errors[]} — recusa
 * explícita, nunca correção silenciosa. Já {@code slug} bem formado que não existe <b>não</b>
 * é erro na listagem: devolve 200 com lista vazia (RNF004). No detalhe, o mesmo slug bem
 * formado e inexistente responde <b>404</b>, porque ali o recurso pedido é um só — e é o
 * mesmo 404 de material fora do público (RN002, CA008).
 *
 * <p>O teto de {@code size} é recusado (400), não truncado: um pedido de 5000 itens que
 * devolvesse 100 em silêncio faria o cliente paginar sobre um total que ele não alcança
 * (RNF021). É a mesma decisão que o {@code TaxonomiaController} já toma no mesmo contrato. O
 * mesmo vale para {@code q}: um termo cortado em 200 caracteres mudaria o resultado sem o
 * cliente saber.
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

    /** Teto do termo de busca, igual ao {@code maxLength: 200} do contrato (RF003). */
    static final int BUSCA_MAXIMA = 200;

    private final MateriaisService materiais;
    private final PublicacaoDeMaterialService publicacao;

    public MateriaisController(MateriaisService materiais, PublicacaoDeMaterialService publicacao) {
        this.materiais = materiais;
        this.publicacao = publicacao;
    }

    @GetMapping("/materiais")
    public Pagina<MaterialResumoResposta> materiais(
            @RequestParam(name = "q", required = false)
            @Size(max = BUSCA_MAXIMA, message = "deve ter " + BUSCA_MAXIMA + " caracteres ou menos")
            String q,
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
        return materiais.listar(q, categoria, tipo, ordem, page, size);
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

    /**
     * Publica um material da conta autenticada (RF012, RF018, RF019; contrato v0.5, tag
     * {@code publicacao}).
     *
     * <p><b>Por que os campos de texto são parâmetros e não um DTO anotado.</b> O corpo é
     * {@code multipart/form-data} e a validação de campo aqui é <i>de negócio</i>, não de anotação:
     * taxonomia inexistente, {@code declaracaoDireito} não confirmada e estado arbitrário dependem
     * de consulta ao banco e de regra do domínio (§8.3.7, §8.3.8). O contrato também separa
     * <b>400</b> (campo ausente no formulário) de <b>422</b> (campo presente com valor inválido), e
     * essa distinção precisa dos valores crus: por isso os parâmetros <b>não</b> são
     * {@code required} no binding — quem decide a ausência é o serviço, com o mesmo
     * {@code ProblemDetail} 400 que o resto do projeto usa para campo obrigatório ausente
     * ({@code CampoObrigatorioAusenteException}), em vez de um 400 de parâmetro de consulta.
     *
     * <p><b>O autor não está aqui.</b> Não há parâmetro de autor, de conta nem de perfil: a
     * identidade chega por {@link IdentidadeAutenticada} — resolvida da cookie pelo interceptor —
     * e é dela que sai o {@code profile_id} gravado (ADR-0003 (j)). Um campo {@code autor} enviado
     * no formulário não encontra destino nenhum.
     *
     * <p><b>{@code estado} também aparece, mas para ser recusado.</b> Ele não é campo do schema do
     * contrato; o serviço só o aceita igual ao estado inicial e recusa qualquer outro valor com
     * 422 (RF019 — o material nasce {@code PUBLICADO} e o cliente não escolhe).
     *
     * <p>O 401 sem sessão e o 403 sem {@code X-CSRF-Token} são do interceptor, que roda antes
     * deste método: a última rota de escrita do MVP herda exatamente a mesma proteção das outras.
     *
     * @return 201 com o {@code slug} gerado no servidor, o estado inicial e a {@code urlPublica}
     */
    @PostMapping(path = "/materiais", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MaterialPublicadoResposta> publicarMaterial(
            @IdentidadeAutenticada SessaoAutenticada sessao,
            @RequestParam(name = "titulo", required = false) String titulo,
            @RequestParam(name = "descricao", required = false) String descricao,
            @RequestParam(name = "categoriaSlug", required = false) String categoriaSlug,
            @RequestParam(name = "tipoSlug", required = false) String tipoSlug,
            @RequestParam(name = "licencaSlug", required = false) String licencaSlug,
            @RequestParam(name = "declaracaoDireito", required = false) String declaracaoDireito,
            @RequestParam(name = "estado", required = false) String estado,
            @RequestPart(name = "arquivo", required = false) MultipartFile arquivo) {
        MaterialPublicadoResposta publicado = publicacao.publicar(sessao,
                new PublicacaoDeMaterialRequisicao(titulo, descricao, categoriaSlug, tipoSlug,
                        licencaSlug, declaracaoDireito, estado),
                arquivo);
        return ResponseEntity.status(HttpStatus.CREATED).body(publicado);
    }
}
