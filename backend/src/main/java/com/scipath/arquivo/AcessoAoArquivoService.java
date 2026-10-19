package com.scipath.arquivo;

import com.scipath.error.IntegridadeDeArquivoException;
import com.scipath.material.ArquivoDoMaterial;
import com.scipath.material.MateriaisService;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/**
 * Decide e entrega o arquivo de um material (RF014, RN002, RN003, RN008) — o que
 * {@code GET /materiais/{slug}/arquivo} faz antes de escrever um único byte.
 *
 * <p>A ordem importa, e é esta:
 *
 * <ol>
 *   <li><b>Visibilidade, no servidor.</b> O material é resolvido pelo slug com a <b>mesma</b> regra
 *       de material público da listagem e do detalhe (RN002, RN003). Não ser público — inexistente,
 *       {@code RETIRADO}, {@code EXCLUIDO}, conta não ativa — é <b>404 do serviço de material</b>,
 *       indistinguível, e nada mais acontece: nem o armazenamento é consultado.</li>
 *   <li><b>Integridade, antes de servir</b> (RN008). O estado do {@code file_asset} precisa ser
 *       {@code VALIDADO}, o arquivo precisa existir no armazenamento e o SHA-256 do que está em
 *       disco precisa bater com o registrado no recebimento (E4.2). Qualquer uma das três falhas
 *       <b>não serve o arquivo</b>: sobe {@link IntegridadeDeArquivoException} e o tratamento único
 *       de erro devolve o 500 padrão e registra o incidente.</li>
 *   <li><b>Entrega.</b> Só depois das duas, o conteúdo é oferecido ao controller.</li>
 * </ol>
 *
 * <p><b>Por que a checagem lê o arquivo inteiro.</b> É o preço de conferir o conteúdo: um checksum
 * não se verifica sem passar pelos bytes. A leitura é em blocos de 8 KB (nunca o arquivo inteiro em
 * memória — o limite é de 30 MB e a máquina tem 954 MB, AGENTS.md), e acontece <b>antes</b> de
 * qualquer byte ir para a resposta, para que uma divergência não produza um PDF truncado que o
 * navegador salvaria como se fosse o material.
 *
 * <p><b>A janela entre conferir e servir.</b> São duas leituras do mesmo arquivo, e entre elas o
 * binário poderia, em tese, mudar por fora da aplicação. Não há escrita concorrente prevista (o
 * arquivo promovido não é reescrito; a remoção passa pelo guarda do {@link RemocaoDeArquivoService}),
 * e eliminar a janela exigiria manter o arquivo em memória ou servi-lo por um descritor aberto —
 * custo desproporcional para o MVP. Fica registrado como limite conhecido, não como garantia que
 * não existe.
 */
@Service
public class AcessoAoArquivoService {

    /** Bloco de leitura: 8 KB, o mesmo do recebimento — irrelevante para a memória da máquina. */
    private static final int TAMANHO_DO_BLOCO = 8 * 1024;

    private final MateriaisService materiais;
    private final StorageService armazenamento;

    public AcessoAoArquivoService(MateriaisService materiais, StorageService armazenamento) {
        this.materiais = materiais;
        this.armazenamento = armazenamento;
    }

    /**
     * Resolve, confere e devolve o arquivo do material público.
     *
     * @param slug slug já validado no controller (formato e tamanho do contrato)
     * @throws com.scipath.error.NaoEncontradoException    o material não é público — mesmo 404 de
     *                                                     inexistente (RN002, RN003)
     * @throws IntegridadeDeArquivoException o conteúdo armazenado não confere com o registrado, ou
     *                                       não está no estado que permite servi-lo (RN008)
     */
    public ArquivoServivel buscarPorSlug(String slug) {
        ArquivoDoMaterial arquivo = materiais.arquivoPorSlug(slug);

        if (!DisponibilidadeDeArquivo.ESTADO_VALIDADO.equals(arquivo.estadoProcessamento())) {
            throw incidente(arquivo, "o arquivo não está VALIDADO e sim " + arquivo.estadoProcessamento());
        }

        Resource conteudo = armazenamento.localizar(arquivo.chaveInterna())
                .orElseThrow(() -> incidente(arquivo, "não há arquivo no armazenamento sob a chave registrada"));

        String noArmazenamento = sha256(conteudo, arquivo);
        if (!arquivo.checksum().equalsIgnoreCase(noArmazenamento)) {
            throw incidente(arquivo, "o checksum do arquivo não confere com o registrado"
                    + " (registrado " + arquivo.checksum() + ", no armazenamento " + noArmazenamento + ")");
        }

        return new ArquivoServivel(arquivo.nomeOriginalSeguro(), arquivo.tamanhoBytes(), conteudo);
    }

    /**
     * SHA-256 do conteúdo armazenado. Lê em blocos, fechando o stream — o arquivo é grande e o
     * descritor não pode vazar entre requisições.
     */
    private static String sha256(Resource conteudo, ArquivoDoMaterial arquivo) {
        try (InputStream entrada = conteudo.getInputStream()) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bloco = new byte[TAMANHO_DO_BLOCO];
            int lidos;
            while ((lidos = entrada.read(bloco)) != -1) {
                digest.update(bloco, 0, lidos);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException falha) {
            // Falha de leitura do armazenamento é tanto incidente de integridade quanto qualquer
            // divergência: o arquivo registrado não pôde ser entregue como está registrado.
            throw incidente(arquivo, "o arquivo não pôde ser lido do armazenamento: " + falha.getMessage());
        } catch (NoSuchAlgorithmException impossivel) {
            // SHA-256 é obrigatório em toda JVM: se faltar, o ambiente é que está quebrado. Seguir
            // sem conferir serviria conteúdo não verificado em silêncio (RN008).
            throw new IllegalStateException("SHA-256 indisponível nesta JVM", impossivel);
        }
    }

    /**
     * Incidente de integridade (RN008) — a mensagem leva o material culpado e o motivo ao
     * <b>log</b>; a resposta ao cliente é o 500 genérico do tratamento único de erro, sem caminho,
     * chave nem checksum (RNF012, RNF014).
     */
    private static IntegridadeDeArquivoException incidente(ArquivoDoMaterial arquivo, String motivo) {
        return new IntegridadeDeArquivoException("incidente de integridade no arquivo do material '"
                + arquivo.slug() + "' (chave " + arquivo.chaveInterna() + "): " + motivo);
    }
}
