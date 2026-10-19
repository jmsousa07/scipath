package com.scipath.arquivo;

import com.scipath.error.ArquivoMuitoGrandeException;
import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

/**
 * Limites e endereços do recebimento de arquivo (RF013, RN008, ADR-0005 §1 e §2).
 *
 * <p><b>Por que o limite é configuração e não constante.</b> O ADR-0005 §1 fixou 30 MB para o
 * MVP e disse explicitamente que o valor <b>não é constante</b>: trocá-lo tem de ser uma linha
 * de {@code .env}, não um card. O risco que motiva isso é conhecido — apresentações com muitas
 * imagens e artigos digitalizados passam de 30 MB com frequência, e o público do DRS (§3.2)
 * publica exatamente slides e artigos. Um limite cravado no código transformaria uma decisão de
 * operação (recusar material legítimo ou aceitar arquivo maior) numa recompilação.
 *
 * <p><b>Um só número.</b> {@code scipath.upload.max-bytes} é a única fonte do limite: os limites
 * do multipart do servlet são calculados a partir dele em {@link ConfiguracaoDeUpload}, e a
 * mensagem do 413 é escrita a partir dele por {@link ArquivoMuitoGrandeException}. Nenhum outro
 * ponto do código conhece o número.
 *
 * <p>Como o {@code .env} da máquina se chama {@code SCIPATH_UPLOAD_MAX_BYTES}, o valor aceita a
 * forma legível do Spring ({@code 30MB}) e o número puro em bytes.
 */
@Component
@ConfigurationProperties(prefix = "scipath.upload")
public class PropriedadesDeUpload {

    /** Limite de upload corrente; o padrão do ADR-0005 §1 (30 MB) está em {@code application.yml}. */
    private DataSize maxBytes = DataSize.ofMegabytes(30);

    /**
     * Área temporária de recebimento — onde o arquivo fica entre a validação (aqui) e a
     * promoção para o storage definitivo (E4.3).
     *
     * <p>O padrão aponta para fora do repositório ({@code ~/.scipath/upload-temporario}), como o
     * outbox de e-mail do E3.4, e por isso não é — nem pode ser — um diretório servido: nada em
     * {@code resources/static} ou {@code frontend/public} alcança este caminho (RF013, RN008).
     */
    private String diretorioTemporario = System.getProperty("user.home") + "/.scipath/upload-temporario";

    /** Limite corrente em bytes — a forma que o serviço compara e que a mensagem do 413 usa. */
    public long limiteEmBytes() {
        return maxBytes.toBytes();
    }

    /** Área temporária de recebimento, pronta para uso. */
    public Path diretorioTemporario() {
        return Path.of(diretorioTemporario);
    }

    public DataSize getMaxBytes() {
        return maxBytes;
    }

    public void setMaxBytes(DataSize maxBytes) {
        if (maxBytes == null || maxBytes.isNegative() || maxBytes.toBytes() == 0) {
            throw new IllegalArgumentException("scipath.upload.max-bytes deve ser positivo");
        }
        this.maxBytes = maxBytes;
    }

    public String getDiretorioTemporario() {
        return diretorioTemporario;
    }

    public void setDiretorioTemporario(String diretorioTemporario) {
        if (diretorioTemporario == null || diretorioTemporario.isBlank()) {
            throw new IllegalArgumentException("scipath.upload.diretorio-temporario não pode ficar vazio");
        }
        this.diretorioTemporario = diretorioTemporario;
    }
}
