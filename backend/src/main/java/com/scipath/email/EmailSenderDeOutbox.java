package com.scipath.email;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * Implementação local do canal de e-mail: em vez de entregar, <b>grava em um outbox</b> de
 * desenvolvimento (ADR-0003 (f)).
 *
 * <p><b>É isto que torna o fluxo verificável sem servidor de e-mail.</b> Não há SMTP nesta
 * máquina e não há Docker para um mail-catcher (ADR-0003, contexto). O outbox é o canal de
 * desenvolvimento e o que a evidência cola: `make email:outbox` mostra o último item, e a
 * suíte de integração lê o mesmo arquivo para pegar o token que o cadastro gerou — sem
 * interceptar objeto interno nenhum e sem mock do serviço.
 *
 * <p><b>Uma linha por mensagem (JSON Lines).</b> A linha carrega destinatário, tipo, assunto, o
 * link (que contém o token em claro — é o único lugar onde ele pode estar fora da mensagem,
 * ADR-0003 (f)) e o instante. <b>Não</b> carrega o hash do token nem o {@code account_id}: o
 * hash não serve ao desenvolvedor que vai clicar no link, e identificador interno de conta não
 * tem por que sair do banco (RNF013).
 *
 * <p><b>A escrita é {@code O_APPEND} em um arquivo só</b>, e não um arquivo por mensagem: o
 * diagnóstico é "o último item" (uma linha no fim), a ordem é a do tempo e dois processos locais
 * não embaralham as linhas (append de linha curta é atômico no Linux). Arquivo por mensagem
 * exigiria ordenar por nome de arquivo para saber o que é "último".
 *
 * <p><b>Token nunca em log</b> (RNF009): esta classe não registra a linha que grava. A falha de
 * escrita vira {@link FalhaDeEnvioDeEmailException} com a descrição do canal — sem o link, sem
 * o token e sem o destinatário —, como o ADR-0003 (g) exige.
 *
 * <p>Em produção, o provedor SMTP do ADR-0006/0007 é outra implementação desta interface; este
 * bean simplesmente deixa de ser usado. O perfil de teste aponta o diretório para
 * {@code backend/target/} (application-test.yml), de forma que a suíte não escreve no outbox de
 * quem desenvolve.
 */
@Component
public class EmailSenderDeOutbox implements EmailSender {

    /** Nome do arquivo de outbox dentro do diretório configurado. */
    public static final String ARQUIVO = "outbox.jsonl";

    private final PropriedadesDeEmail propriedades;
    private final ObjectMapper json = new ObjectMapper();

    public EmailSenderDeOutbox(PropriedadesDeEmail propriedades) {
        this.propriedades = propriedades;
    }

    @Override
    public void enviar(MensagemDeEmail mensagem) {
        Path diretorio = Path.of(propriedades.getOutbox().getDiretorio());
        String linha = serializar(mensagem) + System.lineSeparator();
        try {
            Files.createDirectories(diretorio);
            Files.writeString(diretorio.resolve(ARQUIVO), linha, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException falha) {
            // A causa técnica fica na exceção (e no log do tratamento único de erro); o que a
            // mensagem NÃO pode ter é o token, o link ou o destinatário (RNF009).
            throw new FalhaDeEnvioDeEmailException(
                    "não foi possível gravar a mensagem no outbox de desenvolvimento em "
                            + diretorio.toAbsolutePath(), falha);
        }
    }

    private String serializar(MensagemDeEmail mensagem) {
        LinhaDoOutbox linha = new LinhaDoOutbox(mensagem.destinatario(), mensagem.tipo().name(),
                mensagem.assunto(), mensagem.link(), Instant.now().toString());
        try {
            return json.writeValueAsString(linha);
        } catch (JsonProcessingException falha) {
            throw new FalhaDeEnvioDeEmailException(
                    "não foi possível montar a mensagem para o outbox de desenvolvimento", falha);
        }
    }

    /**
     * O item do outbox, como ele aparece no arquivo — o que o comando de diagnóstico mostra e o
     * que a suíte de integração lê.
     *
     * <p>Record próprio (e não a {@link MensagemDeEmail}) porque o outbox tem um campo a mais: o
     * instante em que a mensagem foi emitida. É ele que deixa a evidência mostrar <b>quando</b> o
     * link saiu, sem depender do relógio de quem lê.
     */
    public record LinhaDoOutbox(
            String destinatario,
            String tipo,
            String assunto,
            String link,
            String criadoEm) {
    }
}
