package com.scipath.material;

import com.scipath.error.CampoObrigatorioAusenteException;
import com.scipath.error.ValidacaoDeCampoException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Validação dos cinco metadados de um material — título, descrição, categoria, tipo e licença — e
 * resolução da taxonomia em identificadores internos (RF012, RF016, RF018).
 *
 * <p><b>Por que existe uma classe só para isto.</b> Os dois atos que escrevem metadados — publicar
 * (RF012) e editar (RF016) — recusam <b>as mesmas cinco coisas</b> e com as mesmas mensagens: o
 * contrato v0.6 manda o `PUT` responder "o mesmo vocabulário da publicação (RF018)". Com a regra
 * escrita duas vezes, a primeira divergência apareceria sem ninguém notar: a edição passaria a
 * aceitar o que a publicação recusa (ou o contrário), e a tela receberia dois vocabulários para o
 * mesmo campo. Aqui a regra é escrita uma vez e os dois atos perguntam a ela.
 *
 * <p><b>Presença é 400; valor é 422</b> — a separação que o contrato publica e que a tela usa para
 * decidir entre "o pedido chegou incompleto" e "corrija este campo". Guardo os valores crus
 * (nenhuma Bean Validation no DTO) para poder distinguir <i>ausente</i> de <i>presente e inválido</i>
 * antes de decidir: um {@code @NotBlank} no record só sabe reprovar campo vazio, e reprovaria com
 * 422 um pedido que o contrato quer ver como 400.
 *
 * <p><b>A taxonomia vem do banco, não de uma lista no código</b> (ADR-0006): slug inexistente
 * <b>ou inativo</b> não tem identificador que o serviço possa inventar, e cai no 422 do campo. A
 * consulta é a de {@link PublicacaoDeMaterialRepositorio} — a tradução slug→id é a mesma pergunta
 * para os dois atos, e continua no mesmo lugar.
 */
@Component
public class ValidadorDeMetadadosDeMaterial {

    /** Teto do título no contrato ({@code titulo.maxLength}), igual na publicação e na edição. */
    static final int TITULO_MAXIMO = 200;

    /** Teto da descrição no contrato ({@code descricao.maxLength}), igual nos dois atos. */
    static final int DESCRICAO_MAXIMA = 2000;

    /** Formato de slug do contrato, o mesmo de {@code FiltroCategoria} e de {@code SlugRecurso}. */
    static final String FORMATO_SLUG = "^[a-z0-9]+(-[a-z0-9]+)*$";

    /** Formato do slug de licença: o do contrato admite ponto ({@code cc-by-4.0}). */
    static final String FORMATO_SLUG_DE_LICENCA = "^[a-z0-9]+(-[a-z0-9.]+)*$";

    private final PublicacaoDeMaterialRepositorio referencias;

    public ValidadorDeMetadadosDeMaterial(PublicacaoDeMaterialRepositorio referencias) {
        this.referencias = referencias;
    }

    /**
     * Valida os cinco campos e resolve a taxonomia (RF018).
     *
     * <p>A ordem é <b>presença</b> primeiro (o campo que não veio é 400), depois <b>valor</b> (o
     * campo que veio errado é 422), e por último a taxonomia, que é a única que consulta o banco.
     * Sem separar presença de valor, um formulário sem {@code titulo} sairia como 422 e o cliente
     * trataria como erro de campo preenchível — que é exatamente o erro que o contrato separou em
     * dois códigos.
     *
     * @param titulo        título cru do pedido ({@code null} é ausente)
     * @param descricao     descrição crua do pedido ({@code null} é ausente)
     * @param categoriaSlug slug da categoria, já no formato estável da taxonomia (ADR-0006)
     * @param tipoSlug      slug do tipo de material
     * @param licencaSlug   slug da licença — nunca escolhida pelo servidor (RN006)
     * @throws CampoObrigatorioAusenteException campo obrigatório ausente (400)
     * @throws ValidacaoDeCampoException       campo presente com valor inválido (422)
     */
    public MetadadosDaPublicacao validar(String titulo, String descricao, String categoriaSlug,
            String tipoSlug, String licencaSlug) {
        String tituloValidado = exigirTexto(titulo, "titulo", TITULO_MAXIMO);
        String descricaoValidada = exigirTexto(descricao, "descricao", DESCRICAO_MAXIMA);

        String categoria = exigirSlug(categoriaSlug, "categoriaSlug", FORMATO_SLUG,
                "deve ser um slug de categoria (minúsculas, dígitos e hífen), como computacao-e-tecnologia");
        String tipo = exigirSlug(tipoSlug, "tipoSlug", FORMATO_SLUG,
                "deve ser um slug de tipo de material (minúsculas, dígitos e hífen), como nota-de-aula");
        String licenca = exigirSlug(licencaSlug, "licencaSlug", FORMATO_SLUG_DE_LICENCA,
                "deve ser um slug de licença (minúsculas, dígitos, hífen e ponto), como cc-by-4.0");

        return new MetadadosDaPublicacao(tituloValidado, descricaoValidada,
                exigirIdAtivo(referencias.categoriaAtiva(categoria), "categoriaSlug",
                        "categoria inexistente ou inativa"),
                exigirIdAtivo(referencias.tipoAtivo(tipo), "tipoSlug",
                        "tipo de material inexistente ou inativo"),
                exigirIdAtivo(referencias.licencaAtiva(licenca), "licencaSlug",
                        "licença inexistente ou inativa"));
    }

    /**
     * Texto obrigatório, já normalizado nas pontas.
     *
     * <p>Ausente (<b>null</b>) é <b>400</b>: a parte não veio no pedido e o servidor não conseguiu
     * lê-lo como veio. Presente e em branco é <b>422</b>: o campo veio com valor inválido — e é o
     * que a tela precisa para apontar o campo. Espaço nas pontas é ruído do campo, não conteúdo: o
     * valor é gravado sem ele.
     */
    private static String exigirTexto(String valor, String campo, int tamanhoMaximo) {
        if (valor == null) {
            throw new CampoObrigatorioAusenteException(campo);
        }
        String normalizado = valor.strip();
        if (normalizado.isEmpty()) {
            throw new ValidacaoDeCampoException(campo, "não pode ficar em branco");
        }
        if (normalizado.length() > tamanhoMaximo) {
            throw new ValidacaoDeCampoException(campo,
                    "deve ter " + tamanhoMaximo + " caracteres ou menos");
        }
        return normalizado;
    }

    /** Slug obrigatório, no formato do contrato — presença é 400, formato é 422. */
    private static String exigirSlug(String valor, String campo, String formato, String motivoDoFormato) {
        if (valor == null) {
            throw new CampoObrigatorioAusenteException(campo);
        }
        String normalizado = valor.strip();
        if (!normalizado.matches(formato)) {
            throw new ValidacaoDeCampoException(campo, motivoDoFormato);
        }
        return normalizado;
    }

    /**
     * O identificador interno do item de referência, ou <b>422</b> no campo (RF018).
     *
     * <p>Inexistente e inativo caem no mesmo erro, e de propósito: para quem escreve os metadados,
     * os dois significam "esta escolha não está disponível" — e a leitura pública da taxonomia já
     * mostra apenas os itens ativos.
     */
    private static UUID exigirIdAtivo(Optional<UUID> id, String campo, String motivo) {
        return id.orElseThrow(() -> new ValidacaoDeCampoException(campo, motivo));
    }
}
