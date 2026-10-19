package com.scipath.arquivo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.scipath.error.ArquivoMuitoGrandeException;
import com.scipath.error.CampoObrigatorioAusenteException;
import com.scipath.error.FormatoDeArquivoNaoAceitoException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

/**
 * Recepção e validação do arquivo, no nível do serviço: sem contexto Spring, com repositório
 * dublê e uma área temporária de verdade ({@code @TempDir}).
 *
 * <p>É aqui que ficam as decisões que o HTTP não distingue:
 *
 * <ul>
 *   <li><b>o limite vem da configuração.</b> O mesmo par de bytes, submetido duas vezes com
 *       {@link PropriedadesDeUpload} diferentes (1 KB e 1 MB), é recusado e aceito — e a recusa
 *       nomeia o valor <i>configurado</i>. Um número cravado no código não passaria nos dois
 *       casos; por isso este teste é a prova do critério "nenhum valor de limite literal";</li>
 *   <li><b>o arquivo físico é a chave UUID</b>, nunca o nome do usuário — o teste confere o nome
 *       do arquivo em disco e o que o repositório recebeu como metadado;</li>
 *   <li><b>o SHA-256 é do conteúdo recebido</b>, comparado com o cálculo independente do
 *       teste;</li>
 *   <li><b>nada sobrevive à recusa</b> (RN012): depois de cada recusa a área temporária está
 *       vazia e o repositório não foi chamado.</li>
 * </ul>
 *
 * <p>O banco de verdade fica no teste de integração ({@code RecepcaoDeArquivoIntegrationTest});
 * aqui o repositório é dublê para que a asserção seja sobre o que o serviço <b>entrega</b> a ele.
 */
@DisplayName("recepção e validação do arquivo enviado")
class RecepcaoDeArquivoServiceTest {

    /** Cabeçalho de PDF de mentira com corpo preenchível — o que o serviço exige é a assinatura. */
    private static final String ASSINATURA = "%PDF-1.7\n";

    @TempDir
    Path areaTemporaria;

    private ArquivosRepositorio repositorio;

    @BeforeEach
    void preparar() {
        repositorio = mock(ArquivosRepositorio.class);
        when(repositorio.registrar(anyString(), anyString(), anyString(), anyLong(), anyString()))
                .thenReturn(UUID.randomUUID());
    }

    // ---------------------------------------------------------------- caminho de sucesso

    @Test
    @DisplayName("PDF válido é aceito: chave UUID, metadado limpo, tamanho medido e SHA-256 do conteúdo")
    void pdfValidoAceito() throws IOException {
        byte[] conteudo = pdfComCorpo(1024);
        String nomeEnviado = "teste-e4-2-nota-de-aula.pdf";
        RecepcaoDeArquivoService servico = servicoComLimite(DataSize.ofMegabytes(1));

        ArquivoRecebido recebido = servico.receber(arquivo(conteudo, nomeEnviado, "application/pdf"));

        assertThat(recebido.tipoDetectado()).isEqualTo(RecepcaoDeArquivoService.TIPO_PDF);
        assertThat(recebido.nomeOriginalSeguro()).isEqualTo(nomeEnviado);
        assertThat(recebido.tamanhoBytes()).as("o tamanho é o medido no servidor").isEqualTo(conteudo.length);
        assertThat(recebido.checksum())
                .as("SHA-256 do conteúdo recebido, em hexadecimal")
                .isEqualTo(sha256Hex(conteudo));

        // Nome físico: a chave gerada pelo sistema. Nada no caminho deriva do nome do usuário.
        assertThat(recebido.arquivoTemporario().getFileName().toString()).isEqualTo(recebido.chaveInterna());
        assertThat(UUID.fromString(recebido.chaveInterna())).as("a chave é um UUID").isNotNull();
        assertThat(recebido.arquivoTemporario().getParent()).isEqualTo(areaTemporaria);
        assertThat(Files.readAllBytes(recebido.arquivoTemporario()))
                .as("o que está em disco é exatamente o que foi enviado")
                .isEqualTo(conteudo);
        assertThat(recebido.id()).isNotNull();

        // O que o repositório recebeu é o metadado correto — e nada além dele.
        ArgumentCaptor<String> chave = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> nome = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> tipo = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Long> tamanho = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<String> checksum = ArgumentCaptor.forClass(String.class);
        verify(repositorio).registrar(chave.capture(), nome.capture(), tipo.capture(), tamanho.capture(),
                checksum.capture());
        assertThat(chave.getValue()).isEqualTo(recebido.chaveInterna());
        assertThat(nome.getValue()).isEqualTo(nomeEnviado);
        assertThat(tipo.getValue()).isEqualTo("application/pdf");
        assertThat(tamanho.getValue()).isEqualTo((long) conteudo.length);
        assertThat(checksum.getValue()).isEqualTo(sha256Hex(conteudo));
        verifyNoMoreInteractions(repositorio);
        System.out.println("[E4.2] arquivo aceito -> chave " + recebido.chaveInterna()
                + ", " + recebido.tamanhoBytes() + " bytes, sha256 " + recebido.checksum());
    }

    @Test
    @DisplayName("travessia no nome enviado não muda o caminho físico nem o metadado guardado")
    void travessiaNaoDecideOCaminho() throws IOException {
        byte[] conteudo = pdfComCorpo(64);
        RecepcaoDeArquivoService servico = servicoComLimite(DataSize.ofMegabytes(1));

        ArquivoRecebido recebido = servico.receber(
                arquivo(conteudo, "../../../../etc/teste-e4-2-passwd.pdf", "application/pdf"));

        assertThat(recebido.nomeOriginalSeguro())
                .as("só o último componente, sem '..'")
                .isEqualTo("teste-e4-2-passwd.pdf");
        assertThat(recebido.arquivoTemporario().getFileName().toString()).isEqualTo(recebido.chaveInterna());
        assertThat(recebido.arquivoTemporario().normalize()).startsWith(areaTemporaria);
        assertThat(Files.exists(areaTemporaria.resolve("teste-e4-2-passwd.pdf")))
                .as("nenhum arquivo é criado com o nome do usuário")
                .isFalse();
    }

    // ---------------------------------------- critério 3: o limite vem da configuração

    @Test
    @DisplayName("o MESMO arquivo é recusado com 1 KB configurado e aceito com 1 MB: o limite não é literal")
    void limiteVemDaConfiguracao() {
        byte[] conteudo = pdfComCorpo(2048);

        RecepcaoDeArquivoService comLimiteDeUmQuilobyte = servicoComLimite(DataSize.ofKilobytes(1));
        assertThatThrownBy(() -> comLimiteDeUmQuilobyte.receber(
                arquivo(conteudo, "teste-e4-2-limite.pdf", "application/pdf")))
                .isInstanceOf(ArquivoMuitoGrandeException.class)
                .hasMessage("O arquivo excede o limite de 1 KB.")
                .as("a mensagem nomeia o limite CONFIGURADO, não um número do código");

        RecepcaoDeArquivoService comLimiteDeUmMegabyte = servicoComLimite(DataSize.ofMegabytes(1));
        ArquivoRecebido recebido = comLimiteDeUmMegabyte.receber(
                arquivo(conteudo, "teste-e4-2-limite.pdf", "application/pdf"));

        assertThat(recebido.tamanhoBytes()).isEqualTo(conteudo.length);
        System.out.println("[E4.2] mesmo conteúdo: 1 KB -> 413 ('...limite de 1 KB.'), 1 MB -> aceito");
    }

    @Test
    @DisplayName("o padrão do produto é 30 MB: o limite de produção é o do ADR-0005 §1")
    void padraoDoProduto() {
        PropriedadesDeUpload padrao = new PropriedadesDeUpload();
        assertThat(padrao.limiteEmBytes()).isEqualTo(30L * 1024 * 1024);
        assertThat(ArquivoMuitoGrandeException.detalhe(padrao.limiteEmBytes()))
                .as("a mensagem do 413 de produção, como o contrato a publica")
                .isEqualTo("O arquivo excede o limite de 30 MB.");
    }

    // ------------------------------------------------------------------ caminhos de recusa

    @Test
    @DisplayName("arquivo renomeado '.pdf' sem assinatura é recusado como 415 e nada fica na área temporária")
    void semAssinaturaRecusado() throws IOException {
        byte[] executavel = "MZ\u0090\u0000conteúdo que não é PDF".getBytes(StandardCharsets.UTF_8);
        RecepcaoDeArquivoService servico = servicoComLimite(DataSize.ofMegabytes(1));

        assertThatThrownBy(() -> servico.receber(
                arquivo(executavel, "teste-e4-2-disfarcado.pdf", "application/pdf")))
                .isInstanceOf(FormatoDeArquivoNaoAceitoException.class)
                .hasMessage(FormatoDeArquivoNaoAceitoException.DETALHE);

        assertThat(arquivosNaAreaTemporaria()).as("a recusa não deixa cópia temporária (RN012)").isEmpty();
        verify(repositorio, never()).registrar(anyString(), anyString(), anyString(), anyLong(), anyString());
        System.out.println("[E4.2] PDF renomeado (sem assinatura) -> recusado, área temporária vazia");
    }

    @Test
    @DisplayName("assinatura fora do início do arquivo não vale: '%PDF-' no meio é recusado")
    void assinaturaNoMeioNaoVale() {
        byte[] conteudo = ("lixo antes do cabeçalho %PDF-1.7\n").getBytes(StandardCharsets.UTF_8);
        RecepcaoDeArquivoService servico = servicoComLimite(DataSize.ofMegabytes(1));

        assertThatThrownBy(() -> servico.receber(arquivo(conteudo, "teste-e4-2-meio.pdf", "application/pdf")))
                .isInstanceOf(FormatoDeArquivoNaoAceitoException.class);
        assertThat(arquivosNaAreaTemporaria()).isEmpty();
    }

    @Test
    @DisplayName("MIME declarado divergente do conteúdo é recusado: PDF de verdade declarado como text/plain")
    void mimeDivergenteRecusado() {
        byte[] conteudo = pdfComCorpo(64);
        RecepcaoDeArquivoService servico = servicoComLimite(DataSize.ofMegabytes(1));

        assertThatThrownBy(() -> servico.receber(arquivo(conteudo, "teste-e4-2-nota.pdf", "text/plain")))
                .isInstanceOf(FormatoDeArquivoNaoAceitoException.class)
                .hasMessage(FormatoDeArquivoNaoAceitoException.DETALHE);

        assertThat(arquivosNaAreaTemporaria()).isEmpty();
        System.out.println("[E4.2] MIME declarado text/plain com conteúdo PDF -> recusado");
    }

    @Test
    @DisplayName("Content-Type ausente ou genérico não é declaração de PDF")
    void semTipoDeclaradoRecusado() {
        byte[] conteudo = pdfComCorpo(64);
        RecepcaoDeArquivoService servico = servicoComLimite(DataSize.ofMegabytes(1));

        for (String tipo : new String[] {null, "", "application/octet-stream", "application/pdf; charset=UTF-8"}) {
            MockMultipartFile arquivo = arquivo(conteudo, "teste-e4-2-nota.pdf", tipo);
            if (tipo == null || !tipo.startsWith("application/pdf")) {
                assertThatThrownBy(() -> servico.receber(arquivo))
                        .as("tipo declarado '%s'", tipo)
                        .isInstanceOf(FormatoDeArquivoNaoAceitoException.class);
            } else {
                assertThat(servico.receber(arquivo).tipoDetectado())
                        .as("parâmetro do Content-Type é ignorado, o tipo é aceito")
                        .isEqualTo("application/pdf");
            }
        }
    }

    @Test
    @DisplayName("extensão diferente de .pdf é recusada mesmo com conteúdo PDF")
    void extensaoErradaRecusada() {
        byte[] conteudo = pdfComCorpo(64);
        RecepcaoDeArquivoService servico = servicoComLimite(DataSize.ofMegabytes(1));

        assertThatThrownBy(() -> servico.receber(arquivo(conteudo, "teste-e4-2-nota.txt", "application/pdf")))
                .isInstanceOf(FormatoDeArquivoNaoAceitoException.class);
        assertThatThrownBy(() -> servico.receber(arquivo(conteudo, "teste-e4-2-nota", "application/pdf")))
                .isInstanceOf(FormatoDeArquivoNaoAceitoException.class);
        assertThat(arquivosNaAreaTemporaria()).isEmpty();
    }

    @Test
    @DisplayName("parte ausente ou vazia é campo obrigatório ausente (400), não formato recusado")
    void parteAusenteOuVazia() {
        RecepcaoDeArquivoService servico = servicoComLimite(DataSize.ofMegabytes(1));

        assertThatThrownBy(() -> servico.receber(null))
                .isInstanceOf(CampoObrigatorioAusenteException.class)
                .hasMessageContaining(RecepcaoDeArquivoService.CAMPO_ARQUIVO)
                .as("parte ausente");
        assertThatThrownBy(() -> servico.receber(arquivo(new byte[0], "teste-e4-2-vazio.pdf", "application/pdf")))
                .isInstanceOf(CampoObrigatorioAusenteException.class)
                .as("parte presente e vazia");
        verify(repositorio, never()).registrar(anyString(), anyString(), anyString(), anyLong(), anyString());
    }

    @Test
    @DisplayName("acima do limite não deixa arquivo parcial em disco: a gravação para no limite")
    void acimaDoLimiteNaoDeixaParcial() {
        byte[] conteudo = pdfComCorpo(4096);
        RecepcaoDeArquivoService servico = servicoComLimite(DataSize.ofKilobytes(1));

        assertThatThrownBy(() -> servico.receber(arquivo(conteudo, "teste-e4-2-grande.pdf", "application/pdf")))
                .isInstanceOf(ArquivoMuitoGrandeException.class)
                .hasMessage("O arquivo excede o limite de 1 KB.");

        assertThat(arquivosNaAreaTemporaria())
                .as("nem o que já havia sido gravado sobrevive (RN012)")
                .isEmpty();
        verify(repositorio, never()).registrar(anyString(), anyString(), anyString(), anyLong(), anyString());
        System.out.println("[E4.2] acima do limite -> 413, área temporária vazia");
    }

    @Test
    @DisplayName("arquivo exatamente no limite é aceito (o limite não é exclusivo por um byte)")
    void exatamenteNoLimiteAceito() {
        byte[] conteudo = pdfComCorpo(1024);
        RecepcaoDeArquivoService servico = servicoComLimite(DataSize.ofKilobytes(1));

        assertThat(conteudo).hasSize(1024);
        assertThat(servico.receber(arquivo(conteudo, "teste-e4-2-exato.pdf", "application/pdf")).tamanhoBytes())
                .isEqualTo(1024);
    }

    @Test
    @DisplayName("falha ao registrar no banco apaga o arquivo já gravado (só o sucesso deixa rastro)")
    void falhaAoRegistrarApagaOArquivo() {
        when(repositorio.registrar(anyString(), anyString(), anyString(), anyLong(), anyString()))
                .thenThrow(new IllegalStateException("banco fora do ar"));
        RecepcaoDeArquivoService servico = servicoComLimite(DataSize.ofMegabytes(1));

        assertThatThrownBy(() -> servico.receber(
                arquivo(pdfComCorpo(64), "teste-e4-2-falha.pdf", "application/pdf")))
                .isInstanceOf(IllegalStateException.class);

        assertThat(arquivosNaAreaTemporaria())
                .as("arquivo sem linha de file_asset viraria órfão invisível para o coletor")
                .isEmpty();
    }

    // ------------------------------------------------------------------------ utilidades

    private RecepcaoDeArquivoService servicoComLimite(DataSize limite) {
        PropriedadesDeUpload propriedades = new PropriedadesDeUpload();
        propriedades.setMaxBytes(limite);
        propriedades.setDiretorioTemporario(areaTemporaria.toString());
        return new RecepcaoDeArquivoService(propriedades, repositorio);
    }

    /** PDF de mentira com o tamanho pedido: assinatura no início e corpo preenchido. */
    private static byte[] pdfComCorpo(int tamanho) {
        byte[] cabecalho = ASSINATURA.getBytes(StandardCharsets.UTF_8);
        byte[] conteudo = new byte[Math.max(tamanho, cabecalho.length)];
        System.arraycopy(cabecalho, 0, conteudo, 0, cabecalho.length);
        for (int i = cabecalho.length; i < conteudo.length; i++) {
            conteudo[i] = (byte) ('a' + (i % 26));
        }
        return conteudo;
    }

    private static MockMultipartFile arquivo(byte[] conteudo, String nome, String tipoDeclarado) {
        return tipoDeclarado == null
                ? new MockMultipartFile("arquivo", nome, null, conteudo)
                : new MockMultipartFile("arquivo", nome, tipoDeclarado, conteudo);
    }

    private List<Path> arquivosNaAreaTemporaria() {
        try (Stream<Path> entradas = Files.list(areaTemporaria)) {
            return entradas.toList();
        } catch (IOException falha) {
            throw new IllegalStateException("não foi possível listar a área temporária de teste", falha);
        }
    }

    /** SHA-256 calculado aqui, de forma independente do serviço. */
    private static String sha256Hex(byte[] conteudo) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(conteudo));
        } catch (Exception impossivel) {
            throw new IllegalStateException(impossivel);
        }
    }
}
