package com.scipath.material;

/**
 * Resultado de uma publicação — o schema {@code MaterialPublicado} do contrato v0.5 (RF012,
 * RF019): o {@code slug} gerado no servidor, o {@code estado} inicial e o {@code urlPublica} da
 * página do material.
 *
 * <p><b>Nada de infraestrutura aqui.</b> Chave do armazenamento, caminho em disco, checksum e
 * identificadores internos ({@code id} de material, de arquivo, de conta) não existem neste
 * record — o contrato não os publica (RF014, RNF013) e um objeto de resposta que os carregasse
 * seria o caminho mais curto para eles aparecerem no JSON por acidente.
 *
 * <p>Os três valores vêm da <b>linha gravada</b> (o {@code INSERT} devolve {@code slug} e
 * {@code estado_publicacao} por {@code RETURNING}), e não do que o serviço achava que ia gravar:
 * o 201 não pode afirmar um estado que a tabela não tem (RN013).
 */
public record MaterialPublicadoResposta(String slug, String estado, String urlPublica) {

    /**
     * Caminho da página pública do material no frontend (RNF025). É <b>derivado do slug</b> e o
     * contrato é explícito: o cliente navega para ele, o contrato não inventa host nem expõe rota
     * de API.
     */
    static final String ROTA_DA_PAGINA = "/materiais/";

    /**
     * Resposta a partir do que o banco confirmou ter gravado: o {@code slug} e o
     * {@code estado_publicacao} vêm da linha, e o caminho público deriva do slug.
     */
    static MaterialPublicadoResposta de(MaterialGravado gravado) {
        return new MaterialPublicadoResposta(gravado.slug(), gravado.estado().name(),
                ROTA_DA_PAGINA + gravado.slug());
    }
}
