package com.scipath.material;

import java.util.Map;
import java.util.Set;

/**
 * Estado de publicação do material (RF019, ADR-0005 §3) e a <b>única</b> fonte do estado inicial
 * e das transições válidas.
 *
 * <p><b>Por que um enum e não uma constante de {@code String}.</b> O nome da coluna
 * ({@code material.estado_publicacao}) tem o {@code CHECK material_estado_valido} do V1, mas o
 * banco só diz <i>quais valores existem</i> — não diz qual é o estado de nascimento nem para onde
 * cada estado pode ir. Isso é regra de negócio, e regra de negócio repetida em três lugares
 * (aqui, no serviço de publicação, e amanhã na retirada da Etapa 5) é como os três divergem sem
 * ninguém notar. Quem escreve estado consulta esta classe.
 *
 * <p><b>O estado nasce {@link #PUBLICADO} e o cliente não escolhe</b> (RF019, contrato v0.5: o
 * cliente envia metadados, taxonomia e arquivo — não envia estado). Um material em
 * {@code PUBLICADO} é elegível à busca e ao acesso ao arquivo (RN002); os outros dois saem do
 * público:
 *
 * <ul>
 *   <li>{@code RETIRADO} — fora do público e <b>reversível</b>: o autor pode republicar (V1,
 *       comentário de {@code estado_publicacao}). Retirar e republicar são a Etapa 5; as
 *       transições ficam declaradas aqui para que a Etapa 5 não invente uma segunda regra.</li>
 *   <li>{@code EXCLUIDO} — exclusão lógica: metadados retidos para auditoria, binário purgado
 *       depois da janela de retenção (ADR-0005 §3). É estado terminal: não há saída de
 *       {@code EXCLUIDO} neste MVP.</li>
 * </ul>
 *
 * <p>A transição de {@code EXCLUIDO} para os outros dois <b>não existe</b> — e é de propósito:
 * declarar uma saída que ninguém implementou seria prometer, na máquina de estados, um caminho
 * que o produto não tem.
 */
public enum EstadoPublicacao {

    /** Elegível à descoberta e ao acesso ao arquivo (RN002). É o estado de nascimento (RF019). */
    PUBLICADO,

    /** Fora do público, reversível: o autor pode republicar (Etapa 5). */
    RETIRADO,

    /** Fora do público, definitivo no MVP: binário purgado após a retenção (ADR-0005 §3). */
    EXCLUIDO;

    /**
     * Estado de todo material no instante em que a publicação conclui (RF019). Um só lugar
     * declara isso: o serviço de publicação não escreve {@code "PUBLICADO"} em lugar nenhum.
     */
    public static final EstadoPublicacao INICIAL = PUBLICADO;

    /**
     * Transições válidas, por origem. Chaves e destinos são do próprio enum — não há como um
     * estado existir no banco e faltar aqui sem que este arquivo mude.
     *
     * <p>{@code EXCLUIDO} aponta para o conjunto vazio (estado terminal); {@code RETIRADO} volta
     * a {@code PUBLICADO} (republicação) e vai a {@code EXCLUIDO}. Valores do V1, do contrato e
     * do DRS §8.3: nenhuma transição aqui foi inventada para "deixar pronto".
     */
    private static final Map<EstadoPublicacao, Set<EstadoPublicacao>> TRANSICOES = Map.of(
            PUBLICADO, Set.of(RETIRADO, EXCLUIDO),
            RETIRADO, Set.of(PUBLICADO, EXCLUIDO),
            EXCLUIDO, Set.of());

    /**
     * O material pode ir deste estado para {@code destino}?
     *
     * <p>Transição para o <b>mesmo</b> estado é {@code false}: ela não é uma transição, e dizer
     * "sim" faria o chamador tratar repetição como mudança (e disparar escrita à toa). Quem
     * precisa ignorar a repetição compara os estados antes de perguntar.
     */
    public boolean podeTransicionarPara(EstadoPublicacao destino) {
        return destino != null && TRANSICOES.getOrDefault(this, Set.of()).contains(destino);
    }

    /** Os destinos válidos deste estado — para a mensagem de recusa e para a Etapa 5. */
    public Set<EstadoPublicacao> transicoesValidas() {
        return TRANSICOES.getOrDefault(this, Set.of());
    }
}
