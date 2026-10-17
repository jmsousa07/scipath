package com.scipath.material;

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
