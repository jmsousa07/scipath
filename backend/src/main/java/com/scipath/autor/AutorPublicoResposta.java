package com.scipath.autor;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.scipath.material.MaterialResumoResposta;
import com.scipath.taxonomia.Pagina;
import java.util.List;

/**
 * Perfil público do autor — schema {@code AutorPublico} do contrato v0.3 (RF006).
 *
 * <p><b>Nada de conta.</b> Este record é a fronteira pública da identidade, e é por isso que
 * e-mail de login, hash de senha, token e {@code account_id} não existem nele: o DTO é o que
 * impede o vazamento por engano, não uma regra que alguém precisa lembrar de aplicar
 * (RNF013; {@code account} e {@code profile} são tabelas separadas exatamente por isso).
 *
 * <p><b>Ausente é informação (§13.2).</b> {@code biografia} e {@code afiliacaoDeclarada} são
 * opcionais no contrato: quando o autor não informou, o campo <b>não sai</b> ({@code NON_NULL}),
 * em vez de sair vazio — string vazia na tela sugere campo quebrado. Já
 * {@code areasInteresse} e {@code linksExternos} são {@code required} no schema, então saem
 * sempre, e o autor sem eles responde lista vazia.
 *
 * <p><b>{@code afiliacaoDeclarada} é declaração do autor, nunca vínculo verificado</b>
 * (RN011, §13.1). O nome do campo carrega isso a tiracolo de propósito: renomeá-lo para algo
 * como "instituição" faria a API afirmar o que a plataforma não confere. Nenhum campo deste
 * schema é marca de revisão, certificação, curadoria ou verificação científica.
 *
 * <p>{@code materiais} é a página padrão do contrato ({@link Pagina}, RNF021) — não uma lista
 * solta: é o mesmo envelope de todas as listagens, com {@code page}, {@code size} e
 * {@code total}, para o cliente paginar sem inventar contagem. Os itens são
 * {@link MaterialResumoResposta} — o item de material da descoberta (schema
 * {@code MaterialResumo}) —, então o slug de categoria que a página do autor publica é
 * literalmente o mesmo valor que {@code GET /api/materiais?categoria=} aceita (RN013, RF007).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AutorPublicoResposta(
        String slug,
        String nomePublico,
        String biografia,
        String afiliacaoDeclarada,
        List<String> areasInteresse,
        List<String> linksExternos,
        Pagina<MaterialResumoResposta> materiais) {
}
