package com.scipath.arquivo;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Onde mora o arquivo publicado — o <b>armazenamento definitivo</b> (ADR-0005 §2, RF014).
 *
 * <p><b>A configuração é conferida, não confiada.</b> O ADR-0005 §2 exige arquivo fora de
 * diretório público e o §14.4 exige segredo/endereço de storage só em configuração. Uma
 * configuração errada aqui não dá erro nenhum — ela simplesmente passa a servir o arquivo
 * por endereço direto, sem visibilidade e sem autorização (RF014, RNF014). Por isso o valor
 * é validado na subida: diretório vazio é recusado, e diretório sob raiz servida
 * ({@link RaizesServidas}) é recusado com o motivo escrito no erro. Quem opera lê o erro e
 * move a configuração; o arquivo nunca é publicado por acidente.
 *
 * <p><b>Diferente da área temporária de recebimento.</b> {@code scipath.upload.diretorio-temporario}
 * (E4.2) é onde o arquivo fica entre ser validado e ser publicado, e o que chega lá ainda não
 * virou material nenhum. Este diretório é o destino do passo 2 da ordem do RN008 — o arquivo
 * que já passou por validação e que o material vai referenciar. Os dois são configurações
 * separadas de propósito: apagar a área temporária não pode apagar material vivo, e o coletor
 * de órfãos (E4.5) precisa saber qual é qual.
 *
 * <p>O padrão aponta para fora do repositório ({@code ~/.scipath/arquivos}), como o outbox de
 * e-mail (E3.4) e a área temporária (E4.2): nada versionado, nada servido.
 */
@Component
@ConfigurationProperties(prefix = "scipath.storage")
public class PropriedadesDeArmazenamento {

    /** Diretório definitivo do arquivo, já validado contra raiz servida. */
    private String diretorio = System.getProperty("user.home") + "/.scipath/arquivos";

    /** Diretório normalizado e absoluto — a forma com que o armazenamento resolve a chave. */
    public Path diretorio() {
        return Path.of(diretorio).toAbsolutePath().normalize();
    }

    public String getDiretorio() {
        return diretorio;
    }

    /**
     * @throws IllegalArgumentException se o valor for vazio ou cair sob uma raiz servida — melhor
     *         recusar a subida do que servir o arquivo por endereço direto (ADR-0005 §2, RF014)
     */
    public void setDiretorio(String diretorio) {
        if (diretorio == null || diretorio.isBlank()) {
            throw new IllegalArgumentException("scipath.storage.diretorio não pode ficar vazio");
        }
        Path caminho = Path.of(diretorio).toAbsolutePath().normalize();
        if (RaizesServidas.contem(caminho)) {
            throw new IllegalArgumentException("scipath.storage.diretorio não pode ficar sob uma raiz"
                    + " servida (ADR-0005 §2: o arquivo é entregue pela API, nunca por endereço"
                    + " direto): " + caminho);
        }
        this.diretorio = diretorio;
    }
}
