package com.scipath.material;

import com.scipath.error.NaoEncontradoException;
import com.scipath.taxonomia.Pagina;
import com.scipath.taxonomia.ResultadoPaginado;
import org.springframework.stereotype.Service;

/**
 * Regra de leitura da listagem pública de material (RF002, RF003, RF004).
 *
 * <p>O serviço existe para o envelope, o vocabulário de ordenação e o que é um termo de busca
 * serem decisão de negócio, não de transporte: o controller não decide o que {@code ordem}
 * omitida significa, nem o que um {@code q} em branco significa, nem monta
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
     * @param busca     termo de busca por texto (RF003), ou {@code null}/vazio (sem busca)
     * @param categoria {@code slug} de categoria, ou {@code null} (sem filtro)
     * @param tipo      {@code slug} de tipo de material, ou {@code null} (sem filtro)
     * @param ordem     valor do parâmetro {@code ordem} já validado pelo controller; o padrão
     *                  {@code recentes} é do contrato e chega aqui como default do parâmetro
     */
    public Pagina<MaterialResumoResposta> listar(String busca, String categoria, String tipo,
            String ordem, int pagina, int tamanho) {
        ResultadoPaginado<MaterialResumoResposta> resultado = repositorio.listarPublicados(
                termoDeBusca(busca), categoria, tipo, OrdemMateriais.doParametro(ordem), pagina, tamanho);
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

    /**
     * Arquivo do material público identificado pelo {@code slug} (RF014) — a leitura que o download
     * (E4.3) faz antes de conferir a integridade e servir o conteúdo.
     *
     * <p><b>O 404 continua indistinguível, e pelo mesmo motivo do detalhe.</b> Material
     * inexistente, {@code RETIRADO}, {@code EXCLUIDO} e material de conta não ativa chegam aqui como
     * o mesmo vazio do repositório e saem como o mesmo erro padrão — sem detalhe próprio, sem
     * separar "não existe" de "não é público", sem dizer se havia arquivo. É a decisão de
     * visibilidade <b>no servidor</b> (RN002, RN003): quem pede o arquivo não descobre, pela
     * resposta, que o material existe fora do público.
     *
     * @param slug slug já validado no controller (formato e tamanho do contrato)
     */
    public ArquivoDoMaterial arquivoPorSlug(String slug) {
        return repositorio.buscarArquivoPublicadoPorSlug(slug)
                .orElseThrow(() -> new NaoEncontradoException(null));
    }

    /**
     * Há material apontando para o arquivo desta chave? É o guarda da remoção (ADR-0005 §3): quem
     * apaga o binário — o coletor de órfãos (E4.5) ou a purga da Etapa 5 — pergunta antes, e a
     * resposta não depende de estado de publicação (material {@code RETIRADO} e {@code EXCLUIDO}
     * dentro da janela continuam vivos).
     */
    public boolean existeMaterialParaArquivo(String chaveInterna) {
        return repositorio.existeMaterialParaArquivo(chaveInterna);
    }

    /**
     * Página dos materiais publicados de um autor (RF006, E2.5) — a lista que a página do
     * perfil público publica no campo {@code materiais}.
     *
     * <p>O envelope é montado aqui, e não no serviço do perfil do autor: {@link Pagina} e a
     * projeção pública de material são deste pacote, e quem pede a página recebe
     * {@code items/page/size/total} prontos. Um segundo ponto que montasse o envelope poderia
     * contar diferente do que devolve.
     *
     * <p>Autor sem material publicado é <b>200 com {@code items: []}</b> — o caminho normal,
     * não um caso de erro (o card E2.5 é explícito: {@code total: 0}, nunca 404). Quem
     * responde 404 é o perfil que não é público, e essa decisão é do serviço do autor, antes
     * de chegar aqui.
     *
     * @param autorSlug {@code slug} do perfil, já validado no controller e já visível
     * @param pagina    página dos materiais do autor, começando em 0
     * @param tamanho   itens por página, já limitado pelo teto do contrato
     */
    public Pagina<MaterialResumoResposta> listarDoAutor(String autorSlug, int pagina, int tamanho) {
        ResultadoPaginado<MaterialResumoResposta> resultado =
                repositorio.listarPublicadosDoAutor(autorSlug, pagina, tamanho);
        return new Pagina<>(resultado.itens(), pagina, tamanho, resultado.total());
    }

    /**
     * Normaliza o termo de busca: ausente, vazio ou só espaço é <b>ausência de busca</b>, e a
     * resposta é a listagem padrão — o contrato é explícito ("ausente ou vazia não é erro",
     * RF003) e o DRS repete ("busca vazia não gera erro; retorna estado inicial ou listagem
     * padrão"). Não é 400 nem 200 vazio: quem digitou nada não filtrou nada.
     *
     * <p>Espaço nas pontas é removido porque é ruído do campo de busca, não conteúdo: o
     * {@code plainto_tsquery} o descartaria de qualquer forma, e normalizar aqui mantém a
     * decisão em um lugar só (o repositório recebe {@code null} ou termo útil).
     */
    private static String termoDeBusca(String busca) {
        return busca == null || busca.isBlank() ? null : busca.trim();
    }
}
