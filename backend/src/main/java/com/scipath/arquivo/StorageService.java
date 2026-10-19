package com.scipath.arquivo;

import java.nio.file.Path;
import java.util.Optional;
import org.springframework.core.io.Resource;

/**
 * Porta de saída do armazenamento de arquivo — a fronteira entre a regra de publicação e o
 * <b>meio</b> onde o binário mora (ADR-0005 §2). Mesmo padrão do {@link com.scipath.email.EmailSender}
 * do E3.4: o domínio depende de uma interface e da <b>chave interna</b>, nunca do disco.
 *
 * <p><b>Por que a interface existe agora, e não quando o MinIO/S3 chegar.</b> O ADR-0005 §2 fixa
 * a interface no domínio <b>junto</b> com a implementação local, e o motivo é verificável: sem
 * ela, o download (RF014) e a remoção pelo coletor (RN012) só poderiam ser exercitados contra um
 * serviço de objetos no ar — e não há nenhum nesta máquina (nem Docker para um). Com ela, o fluxo
 * inteiro fica verificável de ponta a ponta; em produção, o backend de objetos definido no
 * ADR-0007 é <b>outra</b> implementação da mesma interface, sem tocar em nenhum serviço do
 * domínio. É literalmente o que o §14.4 pede: trocar o armazenamento é trocar o adaptador.
 *
 * <p><b>A chave decide o caminho; o nome original nunca participa dele</b> (RF013, ADR-0005 §2).
 * Quem chama entrega uma chave gerada pelo sistema e recebe o arquivo por ela; nenhum método desta
 * porta aceita nome de arquivo do cliente, e nenhum devolve o caminho em disco — o único endereço
 * que existe para quem está fora é {@code GET /materiais/{slug}/arquivo}, decidido no servidor.
 *
 * <p><b>A chave é opaca.</b> O domínio não interpreta o seu conteúdo: ele guarda a chave que
 * {@code file_asset} registrou ({@code chave_interna}) e a devolve ao armazenamento. Fixar aqui um
 * formato (UUID, prefixo, diretório) seria vazar para o domínio uma decisão do adaptador — e no dia
 * do S3 a chave vira <i>object key</i> com outra forma, sem que a regra mude uma linha.
 */
public interface StorageService {

    /**
     * Guarda o arquivo no armazenamento definitivo, sob a chave interna — o passo 2 da ordem
     * obrigatória do RN008 (o passo 1, receber e validar, é do E4.2; o 3, gravar os metadados, do
     * E4.4).
     *
     * <p><b>É um movimento, não uma cópia.</b> A origem (a área temporária de recebimento) deixa de
     * existir no sucesso: nenhuma cópia temporária sobrevive à operação (RN012) e o arquivo existe
     * em um só lugar.
     *
     * <p><b>Nunca sobrescreve.</b> A chave é nova a cada recebimento; uma chave já ocupada indica
     * defeito (chave repetida, arquivo de outra requisição) e falha alto, porque sobrescrever
     * apagaria o arquivo de um material vivo sem ninguém saber.
     *
     * @param chaveInterna chave gerada pelo sistema, {@code UNIQUE} em {@code file_asset}
     * @param origem       arquivo recebido e validado, na área temporária
     * @throws ArquivoJaArmazenadoException a chave já tem arquivo no armazenamento
     * @throws java.io.UncheckedIOException falha de disco ao mover
     */
    void guardar(String chaveInterna, Path origem);

    /**
     * Localiza o arquivo armazenado sob a chave, para leitura.
     *
     * <p>Devolve {@link Resource} — e não um {@code Path} — de propósito: quem lê o conteúdo não
     * recebe o caminho em disco, só a possibilidade de abrir o conteúdo e perguntar o tamanho. O
     * endereço físico fica dentro do adaptador, que é o único ponto que pode mudar quando o
     * armazenamento virar objeto remoto.
     *
     * <p>Vazio (<b>não</b> erro) quando não há arquivo sob a chave: ausência é um estado previsto
     * (arquivo nunca promovido, binário purgado pelo coletor), e quem chama decide o que fazer com
     * ela — o download trata como incidente de integridade (RN008), o coletor, como nada a fazer.
     */
    Optional<Resource> localizar(String chaveInterna);

    /**
     * Remove o arquivo armazenado sob a chave (RN012). Idempotente por construção: não haver o que
     * remover é {@code false}, não erro — é assim que a execução repetida do coletor de limpeza
     * (E4.5) não muda o resultado da segunda passada.
     *
     * <p><b>A regra "nunca apagar arquivo de material vivo" (ADR-0005 §3) não mora aqui.</b> Esta
     * porta não conhece material nenhum; quem a aplica é {@link RemocaoDeArquivoService}, que
     * consulta o vínculo antes de chamar. Manter as duas responsabilidades separadas é o que
     * permite exercitar o armazenamento sozinho (E4.5 e Etapa 5 vão precisar dele) e o guarda,
     * sozinho.
     *
     * @return {@code true} quando havia arquivo e ele foi removido; {@code false} quando não havia
     */
    boolean remover(String chaveInterna);
}
