package com.scipath.arquivo;

import jakarta.servlet.MultipartConfigElement;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

/**
 * Limites do multipart do servlet, <b>derivados</b> do limite único de upload
 * (ADR-0005 §1: "os três lugares que precisam concordar").
 *
 * <p><b>Onde o limite do produto é aplicado.</b> É {@link RecepcaoDeArquivoService} que mede o
 * arquivo recebido e recusa com 413 e a mensagem do contrato — no servidor, não no cliente
 * (RF013). A medição acontece <i>enquanto</i> o arquivo é gravado na área temporária, então
 * nada acima do limite chega a ser guardado. É essa a garantia do produto, e é ela que o
 * teste exerce.
 *
 * <p><b>Por que estas duas constantes existem então.</b> O {@code MultipartConfigElement}
 * abaixo é a <b>rede de segurança</b> do contêiner: hoje o Tomcat escreve a parte enviada num
 * arquivo temporário antes de a aplicação ver o conteúdo, e sem limite declarado um cliente
 * hostil poderia encher o disco antes de qualquer validação (RNF011). A rede fica
 * <b>acima</b> do limite do produto, com folga, por dois motivos:
 *
 * <ol>
 *   <li>Se a rede estivesse <b>no</b> limite, quem decidisse seria o parser do contêiner, e a
 *       resposta dependeria de ele conseguir engolir o resto do corpo para responder. Quando o
 *       corpo restante passa de {@code maxSwallowSize} o contêiner derruba a conexão e o
 *       cliente não recebe 413 nenhum — vê um erro de rede. Com a rede mais alta, quem recusa
 *       o caso comum (arquivo logo acima do limite) é o serviço, que lê o corpo até o limite,
 *       descarta o que já leu e responde o 413 do contrato com a conexão inteira.</li>
 *   <li>A folga cobre o custo do próprio {@code multipart/form-data}: fronteiras, cabeçalhos
 *       das partes e os metadados que viajam na mesma requisição (título, descrição,
 *       taxonomia). Um limite de requisição <b>igual</b> ao do arquivo recusaria arquivo que
 *       está exatamente no limite.</li>
 * </ol>
 *
 * <p>As duas constantes são derivadas de {@code scipath.upload.max-bytes}; nenhuma delas
 * repete o número do ADR. Consequência registrada: {@code spring.servlet.multipart.*} do
 * {@code application.yml} <b>não</b> é lido — o {@code @Bean} abaixo substitui o do
 * auto-configuração ({@code @ConditionalOnMissingBean}), de propósito, para que o limite de
 * upload tenha uma fonte só.
 */
@Configuration
public class ConfiguracaoDeUpload {

    /**
     * Folga entre o limite do produto e a rede de segurança do contêiner.
     *
     * <p>Não é o limite de upload — é a distância entre "o que o produto aceita" e "o que o
     * contêiner sequer deixa entrar". 1 MB cobre com sobra os metadados de um formulário de
     * publicação e mantém pequeno o que um cliente hostil consegue depositar em disco antes de
     * a aplicação recusar.
     */
    static final DataSize FOLGA_DA_REDE_DE_SEGURANCA = DataSize.ofMegabytes(1);

    /**
     * Limites de multipart do servlet, calculados a partir do limite único de upload.
     *
     * <p>Sem {@code location}: as partes do parser ficam no diretório temporário do sistema, e
     * <b>não</b> na área de recebimento do produto. São coisas diferentes — artefato transitório
     * do parser contra arquivo recebido e registrado —, e misturá-las faria o coletor de órfãos
     * (E4.5) confundir uma com a outra.
     *
     * <p>{@code fileSizeThreshold = 0} é o que manda o parser escrever em disco desde o primeiro
     * byte, em vez de guardar o arquivo inteiro em memória: a máquina tem 954 MB (AGENTS.md) e o
     * limite pode ser de 30 MB por requisição, com mais de uma em voo.
     */
    @Bean
    MultipartConfigElement multipartConfigElement(PropriedadesDeUpload propriedades) {
        long limite = propriedades.limiteEmBytes();
        long folga = FOLGA_DA_REDE_DE_SEGURANCA.toBytes();
        return new MultipartConfigElement(null, limite + folga, limite + 2 * folga, 0);
    }
}
