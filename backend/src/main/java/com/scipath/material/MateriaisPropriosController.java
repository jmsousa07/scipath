package com.scipath.material;

import com.scipath.identidade.IdentidadeAutenticada;
import com.scipath.identidade.SessaoAutenticada;
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
 * Gerenciamento do material próprio — a leitura da tag {@code gerenciamento} do contrato v0.6
 * (RF015): {@code GET /api/perfil/atual/materiais}, a lista dos materiais da conta autenticada, e
 * {@code GET /api/perfil/atual/materiais/{slug}}, a leitura de um deles em qualquer estado.
 *
 * <p><b>Por que um controller separado do {@link MateriaisController}.</b> Aquele é do
 * <b>público</b> (descoberta, detalhe e publicação sob {@code /materiais}); este é do <b>dono</b>,
 * sob {@code /perfil/atual/materiais}. As duas superfícies respondem perguntas diferentes sobre a
 * mesma tabela — uma esconde {@code RETIRADO}/{@code EXCLUIDO} (RN002), a outra os mostra (RF015) —
 * e juntá-las num só mapa de rotas seria o jeito mais rápido de alguém "uniformizar" a elegibilidade
 * e a lista do dono passar a filtrar o que ele acabou de excluir. O caminho também é o do ADR-0011 §1:
 * {@code /materiais/proprios} colidiria com {@code GET /materiais/{slug}} e tornaria inalcançável,
 * no público, um material cujo slug fosse {@code proprios}.
 *
 * <p><b>A identidade é declarada, não verificada aqui.</b> Os dois métodos recebem
 * {@code SessaoAutenticada} concreto por {@link IdentidadeAutenticada}, e é isso que faz o 401 sair
 * <b>antes de qualquer regra de negócio</b> — o interceptor resolve a cookie e o resolvedor recusa a
 * requisição sem sessão. Nenhum identificador de conta, de perfil ou de autor existe nos parâmetros:
 * não há requisição, bem formada ou não, que peça o material de outra pessoa (RN001, CA007).
 *
 * <p><b>Leitura não exige {@code X-CSRF-Token}.</b> O CSRF protege <i>escrita</i>; a lista e o
 * detalhe são {@code GET}, e o contrato v0.6 é explícito ("nenhuma escrita acontece aqui, então não
 * há {@code X-CSRF-Token} a exigir"). Exigir o cabeçalho numa leitura quebraria a tela que só abre a
 * página.
 *
 * <p><b>Validação de entrada no servidor (RNF011, §14.3).</b> {@code page}/{@code size} fora da
 * faixa do contrato e slug fora do formato são <b>400</b> no {@code ProblemDetail} padrão, com o
 * parâmetro culpado em {@code errors[]}. O teto de {@code size} é <b>recusado, nunca truncado</b>
 * (RNF021): devolver 100 itens a quem pediu 5000 faria o cliente paginar sobre um total que não
 * alcança. Slug bem formado que não é do dono é <b>404</b>, e não 400 — são erros diferentes: um diz
 * "o endereço está malformado", o outro diz "esse material não é seu" (e diz isso sem distinguir do
 * que não existe, ADR-0011 §3).
 */
@RestController
@RequestMapping("/api")
@Validated
public class MateriaisPropriosController {

    /** Teto por requisição, igual ao {@code maximum: 100} do contrato (RNF021). */
    static final int TAMANHO_MAXIMO = 100;

    /** Itens por página do contrato, quando {@code size} não é informado. */
    static final int TAMANHO_PADRAO = 20;

    /** Teto de comprimento do slug no contrato ({@code SlugRecurso}, {@code maxLength: 120}). */
    static final int COMPRIMENTO_MAXIMO_SLUG = 120;

    /** Formato de slug do contrato — o mesmo padrão de {@code SlugRecurso} e do detalhe público. */
    private static final String FORMATO_SLUG = "^[a-z0-9]+(-[a-z0-9]+)*$";

    private final MateriaisPropriosService proprios;

    public MateriaisPropriosController(MateriaisPropriosService proprios) {
        this.proprios = proprios;
    }

    /**
     * Lista paginada dos materiais da conta autenticada (RF015), com os três estados.
     *
     * <p>Sem sessão → <b>401</b> (do interceptor). {@code page} negativo, {@code size} menor que 1 ou
     * maior que {@value #TAMANHO_MAXIMO} → <b>400</b>. Perfil sem material → <b>200</b> com
     * {@code items: []}.
     */
    @GetMapping("/perfil/atual/materiais")
    public Pagina<MaterialProprioResposta> materiais(
            @IdentidadeAutenticada SessaoAutenticada sessao,
            @RequestParam(name = "page", defaultValue = "0")
            @Min(value = 0, message = "deve ser 0 ou mais") int page,
            @RequestParam(name = "size", defaultValue = "" + TAMANHO_PADRAO)
            @Min(value = 1, message = "deve ser 1 ou mais")
            @Max(value = TAMANHO_MAXIMO, message = "deve ser " + TAMANHO_MAXIMO + " ou menos") int size) {
        return proprios.listar(sessao.perfilId(), page, size);
    }

    /**
     * Lê um material do dono, em qualquer estado (RF015) — é a leitura que pré-preenche a edição.
     *
     * <p>O {@code slug} é validado aqui e não no serviço, como no detalhe público: valor fora do
     * formato é <b>400</b> ("o endereço está malformado"), enquanto slug bem formado que não é do
     * dono é <b>404</b>, indistinguível do inexistente. Os dois recortes são os do {@code
     * SlugRecurso} do contrato: formato de slug e no máximo 120 caracteres.
     */
    @GetMapping("/perfil/atual/materiais/{slug}")
    public MaterialProprioResposta material(
            @IdentidadeAutenticada SessaoAutenticada sessao,
            @PathVariable("slug")
            @Pattern(regexp = FORMATO_SLUG,
                    message = "deve ser um slug (minúsculas, dígitos e hífen), como introducao-a-algoritmos-de-ordenacao")
            @Size(max = COMPRIMENTO_MAXIMO_SLUG,
                    message = "deve ter no máximo " + COMPRIMENTO_MAXIMO_SLUG + " caracteres")
            String slug) {
        return proprios.ler(sessao.perfilId(), slug);
    }
}
