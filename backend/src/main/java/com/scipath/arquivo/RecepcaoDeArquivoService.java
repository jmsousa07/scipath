package com.scipath.arquivo;

import com.scipath.error.ArquivoMuitoGrandeException;
import com.scipath.error.CampoObrigatorioAusenteException;
import com.scipath.error.FormatoDeArquivoNaoAceitoException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Recebe o arquivo enviado e o transforma em um arquivo <b>comprovadamente aceitável</b>
 * (RF013, RN008, ADR-0005 §1 e §2) — o passo 1 da ordem obrigatória da publicação.
 *
 * <p>O que este serviço garante, e como:
 *
 * <ol>
 *   <li><b>Formato por conteúdo, não por extensão.</b> Três conferências independentes:
 *       extensão {@code .pdf} do nome <i>limpo</i>, {@code Content-Type} declarado da parte e
 *       assinatura {@code %PDF-} nos primeiros bytes do arquivo <b>já gravado</b>. A extensão
 *       nunca decide sozinha (ADR-0005 §1: PDF renomeado de outra coisa tem de ser recusado).</li>
 *   <li><b>Tamanho aplicado no servidor.</b> O arquivo é medido enquanto é gravado e a
 *       gravação para no limite configurado — nada acima dele chega ao disco. O limite vem de
 *       {@link PropriedadesDeUpload} (nunca de um literal) e a recusa é 413 com a mensagem que
 *       nomeia o valor corrente (RNF004).</li>
 *   <li><b>Nome físico do sistema.</b> O arquivo é gravado sob uma chave UUID em
 *       {@code CREATE_NEW} (nunca sobrescreve), na área temporária configurada. O nome do
 *       usuário só é guardado como metadado limpo; nada no caminho deriva dele.</li>
 *   <li><b>Área temporária.</b> O recebimento acontece fora de qualquer diretório servido e o
 *       arquivo <b>não</b> é publicado por nenhum endpoint — quem o serve é o E4.3, e só depois
 *       de o material estar público. O passo 2 (mover para o storage definitivo) é do E4.3 e o
 *       material é do E4.4: aqui nada é publicado.</li>
 *   <li><b>Integridade registrada.</b> O SHA-256 é calculado durante a gravação (o mesmo passe
 *       de bytes que mede o tamanho) e vai para {@code file_asset} junto da chave, do nome
 *       seguro, do tipo detectado e do tamanho (ADR-0005 §2).</li>
 * </ol>
 *
 * <p><b>Nada sobrevive à recusa.</b> Qualquer caminho que não termine em arquivo válido apaga o
 * que já foi gravado — inclusive a falha de banco depois da validação, para que um arquivo sem
 * linha não fique para o coletor de limpeza (RN012, "nenhuma cópia temporária sobrevive à
 * operação"). Só o caminho de sucesso deixa arquivo e linha.
 */
@Service
public class RecepcaoDeArquivoService {

    /** Tipo detectado de tudo que este serviço aceita: o único formato do MVP (ADR-0005 §1). */
    static final String TIPO_PDF = "application/pdf";

    /** Campo do formulário de publicação que carrega o arquivo (contrato v0.5). */
    static final String CAMPO_ARQUIVO = "arquivo";

    /**
     * Assinatura de um PDF: os cinco primeiros bytes de todo arquivo PDF válido, em qualquer
     * versão e em qualquer gerador. É a conferência que a extensão não substitui — e é
     * aplicada ao arquivo <b>gravado</b>, não ao fluxo de entrada, para que o que foi
     * registrado no banco seja exatamente o que foi conferido.
     */
    private static final byte[] ASSINATURA_PDF = {'%', 'P', 'D', 'F', '-'};

    /** Bloco de cópia: 8 KB, do tamanho do buffer de disco e irrelevante para a memória da máquina. */
    private static final int TAMANHO_DO_BLOCO = 8 * 1024;

    private static final Logger log = LoggerFactory.getLogger(RecepcaoDeArquivoService.class);

    private final PropriedadesDeUpload propriedades;
    private final ArquivosRepositorio repositorio;

    public RecepcaoDeArquivoService(PropriedadesDeUpload propriedades, ArquivosRepositorio repositorio) {
        this.propriedades = propriedades;
        this.repositorio = repositorio;
    }

    /**
     * Recebe, valida e registra o arquivo enviado.
     *
     * @param arquivo parte do formulário com o arquivo (contrato v0.5: {@code arquivo})
     * @return o arquivo validado, com chave, metadados e checksum, ainda na área temporária
     * @throws CampoObrigatorioAusenteException  se a parte não veio ou veio vazia (400)
     * @throws FormatoDeArquivoNaoAceitoException se o conteúdo não é PDF (415)
     * @throws ArquivoMuitoGrandeException        se o tamanho passa do limite configurado (413)
     */
    public ArquivoRecebido receber(MultipartFile arquivo) {
        exigirArquivo(arquivo);

        String nomeOriginalSeguro = NomesDeArquivo.sanitizar(arquivo.getOriginalFilename());
        exigirExtensaoPdf(nomeOriginalSeguro);
        exigirTipoDeclaradoPdf(arquivo.getContentType());

        long limite = propriedades.limiteEmBytes();
        // Recusa antecipada pelo tamanho declarado: evita abrir arquivo em disco para uma
        // requisição que o próprio cliente já sabe que passou do limite. Não é a garantia — o
        // tamanho declarado pode mentir; quem mede de verdade é a gravação, logo abaixo.
        if (arquivo.getSize() > limite) {
            throw new ArquivoMuitoGrandeException(limite);
        }

        String chaveInterna = UUID.randomUUID().toString();
        Path areaTemporaria = areaTemporaria();
        Path destino = areaTemporaria.resolve(chaveInterna);

        Medicao medicao = gravar(arquivo, destino, limite);
        if (!assinaturaPdfPresente(destino)) {
            apagar(destino);
            throw new FormatoDeArquivoNaoAceitoException(
                    "o conteúdo gravado não começa com a assinatura %PDF- (" + destino.getFileName() + ")");
        }

        try {
            UUID id = repositorio.registrar(chaveInterna, nomeOriginalSeguro, TIPO_PDF,
                    medicao.tamanhoBytes(), medicao.checksum());
            return new ArquivoRecebido(id, chaveInterna, nomeOriginalSeguro, TIPO_PDF,
                    medicao.tamanhoBytes(), medicao.checksum(), destino);
        } catch (RuntimeException falha) {
            // Arquivo sem linha é lixo que o coletor teria de descobrir sozinho; apagar aqui
            // mantém a promessa de que só o sucesso deixa rastro.
            apagar(destino);
            throw falha;
        }
    }

    /** Arquivo presente e com conteúdo — parte ausente é 400, não 415 (contrato v0.5). */
    private static void exigirArquivo(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new CampoObrigatorioAusenteException(CAMPO_ARQUIVO);
        }
    }

    private static void exigirExtensaoPdf(String nomeSeguro) {
        if (!nomeSeguro.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new FormatoDeArquivoNaoAceitoException("a extensão do nome enviado não é .pdf");
        }
    }

    /**
     * O {@code Content-Type} declarado da parte tem de ser {@code application/pdf}.
     *
     * <p>Parâmetros do cabeçalho (como {@code charset}) são ignorados; o que não passa é tipo
     * diferente, tipo ausente e o genérico {@code application/octet-stream} — declarar "não sei
     * o que é isto" não é declarar PDF, e o contrato só aceita PDF (RF013).
     */
    private static void exigirTipoDeclaradoPdf(String tipoDeclarado) {
        if (tipoDeclarado == null || tipoDeclarado.isBlank()) {
            throw new FormatoDeArquivoNaoAceitoException("a parte não declarou Content-Type");
        }
        String tipo = tipoDeclarado.split(";")[0].strip().toLowerCase(Locale.ROOT);
        if (!TIPO_PDF.equals(tipo)) {
            throw new FormatoDeArquivoNaoAceitoException("o Content-Type declarado foi '" + tipo + "'");
        }
    }

    /**
     * Grava o recebido na área temporária, medindo tamanho e calculando SHA-256 no mesmo passe.
     *
     * <p>A gravação é interrompida no instante em que o total passa do limite (antes de escrever
     * o bloco que estourou): o que fica em disco nunca é maior que o limite, e o
     * {@code ArquivoMuitoGrandeException} sobe com o tamanho já apagado pela chamada.
     *
     * <p>{@code CREATE_NEW} é deliberado: a chave é nova a cada recebimento, e se por qualquer
     * motivo ela já existisse, sobrescrever apagaria o arquivo de outra requisição. Falhar alto
     * aqui é a escolha certa.
     */
    private static Medicao gravar(MultipartFile arquivo, Path destino, long limite) {
        MessageDigest digest = sha256();
        long total = 0;
        try (InputStream entrada = arquivo.getInputStream();
                var saida = new DigestOutputStream(
                        Files.newOutputStream(destino, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE),
                        digest)) {
            byte[] bloco = new byte[TAMANHO_DO_BLOCO];
            int lidos;
            while ((lidos = entrada.read(bloco)) != -1) {
                total += lidos;
                if (total > limite) {
                    apagar(destino);
                    throw new ArquivoMuitoGrandeException(limite);
                }
                saida.write(bloco, 0, lidos);
            }
        } catch (IOException falha) {
            // Falha de disco/servidor: não é erro do cliente (500 no catch-all, com a causa no
            // log). A área temporária não pode ficar com o que sobrou (RN012).
            apagar(destino);
            throw new UncheckedIOException("falha ao gravar o arquivo recebido na área temporária", falha);
        }
        return new Medicao(total, HexFormat.of().formatHex(digest.digest()));
    }

    /** Os primeiros bytes do arquivo gravado são a assinatura de PDF? */
    private static boolean assinaturaPdfPresente(Path arquivo) {
        try (InputStream entrada = Files.newInputStream(arquivo)) {
            return Arrays.equals(entrada.readNBytes(ASSINATURA_PDF.length), ASSINATURA_PDF);
        } catch (IOException falha) {
            apagar(arquivo);
            throw new UncheckedIOException("falha ao conferir o arquivo recebido", falha);
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossivel) {
            // SHA-256 é obrigatório em toda JVM (JLS/JCA): se faltar, o ambiente é que está
            // quebrado, e seguir sem checksum quebraria a integridade do RN008 em silêncio.
            throw new IllegalStateException("SHA-256 indisponível nesta JVM", impossivel);
        }
    }

    /** Área temporária de recebimento, criada se ainda não existir. */
    private Path areaTemporaria() {
        Path area = propriedades.diretorioTemporario();
        try {
            Files.createDirectories(area);
        } catch (IOException falha) {
            throw new UncheckedIOException("falha ao preparar a área temporária de recebimento " + area, falha);
        }
        return area;
    }

    /** Apaga o arquivo, se ele existir, sem mascarar a falha que motivou a limpeza. */
    private static void apagar(Path arquivo) {
        try {
            Files.deleteIfExists(arquivo);
        } catch (IOException falha) {
            // A limpeza é melhor esforço: o erro que interessa é o que trouxe o fluxo até aqui, e
            // perder a causa original por causa da limpeza seria pior. O que sobrar é órfão sem
            // linha, que o coletor (E4.5) trata.
            log.warn("não foi possível apagar o arquivo temporário {}: {}", arquivo, falha.getMessage());
        }
    }

    /** Tamanho medido e checksum do arquivo gravado — o resultado de um passe de gravação. */
    private record Medicao(long tamanhoBytes, String checksum) {
    }
}
