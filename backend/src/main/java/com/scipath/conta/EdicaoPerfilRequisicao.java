package com.scipath.conta;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Corpo da edição do próprio perfil — schema {@code EdicaoPerfil} do contrato v0.4 (RF011).
 *
 * <p><b>Não existe campo de identificador.</b> Nem conta, nem perfil, nem sessão: a
 * propriedade vem da sessão (ADR-0003 (j), CA007) e o schema do contrato não publica nenhum
 * campo que a nomeie. Aqui isso é levado a sério — o corpo é desserializado de forma
 * <b>estrita</b> ({@link CorpoEstrito}): um {@code perfilId}, um {@code contaId}, um
 * {@code email} ou qualquer outro campo fora do schema <b>não</b> é ignorado em silêncio, é
 * recusado como corpo que o servidor não processa como veio (400 {@code validacao} do
 * contrato). Ignorar seria aceitar a tentativa e responder 200 — e o cliente acharia que o
 * identificador foi usado para alguma coisa.
 *
 * <p><b>Opcional vazio vale como não informado.</b> É o que o contrato fixa ("campo opcional
 * enviado vazio é aceito e vale como não informado — o requisito pede que opcionais possam
 * ficar vazios, não que precisem sair da requisição"). A normalização acontece no construtor
 * compacto, <i>antes</i> da validação: texto só de espaços vira ausente, item em branco sai da
 * lista, lista que esvazia vira ausente. Assim o serviço grava {@code NULL} na coluna e a
 * coluna {@code NULL} é o mesmo estado de um perfil que nunca passou por aqui (RN004).
 *
 * <p><b>O slug é validado em três dimensões</b> (RN009): formato do contrato
 * ({@code ^[a-z0-9]+(-[a-z0-9]+)*$}), tamanho (3..120) e reserva ({@link SlugLivre}). Fora do
 * formato ou do tamanho, ou reservado, a resposta é <b>422</b> {@code validacao_campo} com o
 * campo {@code slug} em {@code errors[]} — o mesmo 422 que o formulário já sabe ler. Slug bem
 * formado que pertence a outra conta é <b>409</b>, e essa decisão é do serviço
 * ({@link PerfilService}), porque depende do que está no banco.
 */
@JsonDeserialize(using = EdicaoPerfilRequisicao.CorpoEstrito.class)
public record EdicaoPerfilRequisicao(

        @NotBlank(message = "informe o nome público")
        @Size(max = 120, message = "deve ter 120 caracteres ou menos")
        String nomePublico,

        @NotBlank(message = "informe o identificador público")
        @Size(min = 3, max = 120, message = "deve ter entre 3 e 120 caracteres")
        @Pattern(regexp = FORMATO_DE_SLUG,
                message = "deve ser um slug (minúsculas, dígitos e hífen), como maria-silva")
        @SlugLivre
        String slug,

        @Size(max = 600, message = "deve ter 600 caracteres ou menos")
        String biografia,

        @Size(max = 300, message = "deve ter 300 caracteres ou menos")
        String afiliacaoDeclarada,

        @Size(max = 20, message = "deve ter 20 itens ou menos")
        List<@Size(max = 60, message = "cada item deve ter 60 caracteres ou menos") String> areasInteresse,

        @Size(max = 20, message = "deve ter 20 itens ou menos")
        List<@Size(max = 300, message = "cada item deve ter 300 caracteres ou menos") String> linksExternos) {

    /** Formato de slug do contrato — o mesmo de {@code SlugRecurso} e do V1 ({@code profile_slug_formato}). */
    static final String FORMATO_DE_SLUG = "^[a-z0-9]+(-[a-z0-9]+)*$";

    public EdicaoPerfilRequisicao {
        nomePublico = textoOuAusente(nomePublico);
        slug = textoOuAusente(slug);
        biografia = textoOuAusente(biografia);
        afiliacaoDeclarada = textoOuAusente(afiliacaoDeclarada);
        areasInteresse = itensLimpos(areasInteresse);
        linksExternos = itensLimpos(linksExternos);
    }

    /** Texto sem conteúdo (vazio ou só espaços) é ausente — não string vazia na coluna. */
    private static String textoOuAusente(String valor) {
        if (valor == null) {
            return null;
        }
        String limpo = valor.trim();
        return limpo.isEmpty() ? null : limpo;
    }

    /**
     * Lista de texto livre: cada item é aparado, item em branco sai e lista que esvazia vira
     * ausente. {@code null} continua ausente.
     */
    private static List<String> itensLimpos(List<String> valores) {
        if (valores == null) {
            return null;
        }
        List<String> limpos = new ArrayList<>(valores.size());
        for (String valor : valores) {
            String limpo = textoOuAusente(valor);
            if (limpo != null) {
                limpos.add(limpo);
            }
        }
        return limpos.isEmpty() ? null : List.copyOf(limpos);
    }

    /**
     * Desserialização <b>estrita</b> do corpo: o schema {@code EdicaoPerfil} é fechado.
     *
     * <p>Campo que não existe no schema — em especial qualquer identificador de conta ou de
     * perfil, que o contrato não publica — não é ignorado: a requisição é recusada com o
     * <b>400</b> {@code validacao} do contrato ("corpo que o servidor não processa como veio").
     * É o que torna a ausência do campo uma <i>fronteira</i> e não uma esperança: quem tentar
     * mandar {@code perfilId} não recebe 200 e não sai achando que o identificador valeu.
     *
     * <p>O valor bruto vai para o log do tratamento único de erro, junto com a requisição —
     * nunca para a resposta, que é genérica (RNF012). Tipo inesperado (número no lugar de
     * texto, objeto no lugar de lista) é o mesmo caso: o servidor não processa como veio.
     *
     * <p>O corpo é montado campo a campo de propósito: é aqui que a lista de campos aceitos
     * vive, e é ela que fecha o schema. Um campo novo no contrato entra nesta lista <b>e</b> no
     * record — as duas coisas no mesmo arquivo, para não existir schema em dois lugares.
     */
    static final class CorpoEstrito extends JsonDeserializer<EdicaoPerfilRequisicao> {

        /** Os seis campos do schema {@code EdicaoPerfil} — e só eles. */
        private static final Set<String> CAMPOS = Set.of("nomePublico", "slug", "biografia",
                "afiliacaoDeclarada", "areasInteresse", "linksExternos");

        @Override
        public EdicaoPerfilRequisicao deserialize(JsonParser parser, DeserializationContext contexto)
                throws IOException {
            JsonNode corpo = parser.readValueAsTree();
            if (corpo == null || !corpo.isObject()) {
                throw JsonMappingException.from(parser, "o corpo da edição de perfil deve ser um objeto JSON");
            }
            for (Iterator<String> nomes = corpo.fieldNames(); nomes.hasNext(); ) {
                String nome = nomes.next();
                if (!CAMPOS.contains(nome)) {
                    throw JsonMappingException.from(parser, "campo fora do schema EdicaoPerfil: " + nome);
                }
            }
            return new EdicaoPerfilRequisicao(
                    texto(corpo, "nomePublico", parser),
                    texto(corpo, "slug", parser),
                    texto(corpo, "biografia", parser),
                    texto(corpo, "afiliacaoDeclarada", parser),
                    itens(corpo, "areasInteresse", parser),
                    itens(corpo, "linksExternos", parser));
        }

        /** Campo de texto; ausente e {@code null} viram "não informado". */
        private static String texto(JsonNode corpo, String campo, JsonParser parser) throws JsonMappingException {
            JsonNode valor = corpo.get(campo);
            if (valor == null || valor.isNull()) {
                return null;
            }
            if (!valor.isTextual()) {
                throw JsonMappingException.from(parser, "o campo " + campo + " deve ser texto");
            }
            return valor.asText();
        }

        /** Campo de lista de texto; ausente e {@code null} viram "não informado". */
        private static List<String> itens(JsonNode corpo, String campo, JsonParser parser) throws JsonMappingException {
            JsonNode valor = corpo.get(campo);
            if (valor == null || valor.isNull()) {
                return null;
            }
            if (!valor.isArray()) {
                throw JsonMappingException.from(parser, "o campo " + campo + " deve ser uma lista de texto");
            }
            List<String> itens = new ArrayList<>();
            for (JsonNode item : valor) {
                if (!item.isTextual()) {
                    throw JsonMappingException.from(parser, "o campo " + campo + " deve ser uma lista de texto");
                }
                itens.add(item.asText());
            }
            return itens;
        }
    }
}
