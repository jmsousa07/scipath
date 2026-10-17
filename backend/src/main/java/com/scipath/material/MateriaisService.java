package com.scipath.material;

import com.scipath.error.NaoEncontradoException;
import com.scipath.taxonomia.Pagina;
import com.scipath.taxonomia.ResultadoPaginado;
import org.springframework.stereotype.Service;

/**
 * Regra de leitura da listagem pública de material (RF002, RF004).
 *
 * <p>O serviço existe para o envelope e o vocabulário de ordenação serem decisão de negócio,
 * não de transporte: o controller não decide o que {@code ordem} omitida significa nem monta
 * {@code items/page/size/total}.
 *
 * <p>O envelope é o {@link Pagina} — o mesmo record genérico que a taxonomia já usa, porque no
 * contrato todas as listagens respondem exatamente os mesmos quatro campos (schema
 * {@code Pagina}, RF002/RNF021). Duplicar a classe aqui, ou movê-la de pacote, seria mudança
 * não pedida: reaproveitar o tipo existente mantém uma única definição do envelope.
 */
@Service
public class MateriaisService {

    private final MateriaisRepositorio repositorio;

    public MateriaisService(MateriaisRepositorio repositorio) {
        this.repositorio = repositorio;
    }

    /**
     * @param categoria {@code slug} de categoria, ou {@code null} (sem filtro)
     * @param tipo      {@code slug} de tipo de material, ou {@code null} (sem filtro)
     * @param ordem     valor do parâmetro {@code ordem} já validado pelo controller; o padrão
     *                  {@code recentes} é do contrato e chega aqui como default do parâmetro
     */
    public Pagina<MaterialResumoResposta> listar(String categoria, String tipo, String ordem,
            int pagina, int tamanho) {
        ResultadoPaginado<MaterialResumoResposta> resultado = repositorio.listarPublicados(
                categoria, tipo, OrdemMateriais.doParametro(ordem), pagina, tamanho);
        // Conjunto vazio é 200 com items: [] — montar o envelope vazio é o caminho normal,
        // não um caso de erro (RNF004).
        return new Pagina<>(resultado.itens(), pagina, tamanho, resultado.total());
    }

    /**
     * Detalhe público do material identificado pelo {@code slug} (RF005).
     *
     * <p><b>O 404 é indistinguível de propósito (RN002, CA008).</b> Material inexistente,
     * {@code RETIRADO}, {@code EXCLUIDO} ou de conta não ativa chega aqui como o mesmo vazio do
     * repositório, e sai como o mesmo erro — sem detalhe próprio. O detalhe é {@code null} porque
     * o texto vai do padrão único de {@link com.scipath.error.ApiExceptionHandler} ("O recurso
     * solicitado não foi encontrado.", o mesmo do exemplo de {@code ErroNaoEncontrado} no
     * contrato): duas mensagens diferentes para "não existe" e "não é público" seriam exatamente a
     * distinção que a regra proíbe, e uma constante repetida aqui poderia divergir do handler sem
     * ninguém notar.
     *
     * @param slug slug já validado no controller (formato e tamanho do contrato)
     */
    public MaterialDetalheResposta buscarPorSlug(String slug) {
        return repositorio.buscarPublicadoPorSlug(slug)
                .orElseThrow(() -> new NaoEncontradoException(null));
    }
}
