package com.scipath.arquivo;

import com.scipath.material.MateriaisService;
import org.springframework.stereotype.Service;

/**
 * Remove o binário de um arquivo — a porta de remoção que o coletor de órfãos (E4.5) e a purga da
 * Etapa 5 vão usar, com o guarda do ADR-0005 §3.
 *
 * <p><b>A regra que dá sentido à classe: nunca apagar arquivo referenciado por material vivo.</b>
 * Ela não pode morar no {@link StorageService} — o armazenamento não conhece material nenhum, e
 * dar-lhe essa consulta seria pedir que o adaptador de disco soubesse de domínio (e, no dia do S3,
 * que o cliente de objetos consultasse o banco). Ela mora aqui: <b>uma</b> pergunta ao serviço de
 * material antes de qualquer remoção, e a remoção só acontece se a resposta for "nenhum material
 * aponta para esta chave".
 *
 * <p><b>Quem ainda é "vivo".</b> Qualquer material, em qualquer estado de publicação — inclusive
 * {@code RETIRADO} (republicável) e {@code EXCLUIDO} dentro da janela de retenção. É a leitura
 * conservadora de propósito: recusar a remoção de um binário que a Etapa 5 purgaria custa espaço em
 * disco; apagar um que ainda é alcançável destrói dado que tem dono. O erro é escolhido pelo lado
 * reversível.
 *
 * <p><b>Não mexe no banco.</b> Apagar a linha de {@code file_asset} — e a ordem de fazê-lo em
 * relação ao binário — é decisão de quem chama (o coletor, E4.5): esta classe responde por uma coisa
 * só, e é essa a razão de ela existir separada do coletor.
 */
@Service
public class RemocaoDeArquivoService {

    private final MateriaisService materiais;
    private final StorageService armazenamento;

    public RemocaoDeArquivoService(MateriaisService materiais, StorageService armazenamento) {
        this.materiais = materiais;
        this.armazenamento = armazenamento;
    }

    /**
     * Remove o binário sob a chave, <b>se nenhum material o referenciar</b>.
     *
     * @param chaveInterna chave do arquivo no armazenamento
     * @return {@code true} quando havia arquivo e ele foi removido; {@code false} quando não havia
     *         (idempotente: repetir a chamada não é erro nem efeito — é o que o coletor do RN012
     *         precisa na segunda execução)
     * @throws ArquivoEmUsoPorMaterialException há material apontando para esta chave; nada foi
     *                                          removido (ADR-0005 §3)
     */
    public boolean remover(String chaveInterna) {
        if (materiais.existeMaterialParaArquivo(chaveInterna)) {
            throw new ArquivoEmUsoPorMaterialException("o arquivo sob a chave " + chaveInterna
                    + " é referenciado por material e não pode ser removido (ADR-0005 §3)");
        }
        return armazenamento.remover(chaveInterna);
    }
}
