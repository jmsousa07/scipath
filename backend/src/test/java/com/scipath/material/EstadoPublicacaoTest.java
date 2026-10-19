package com.scipath.material;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * O estado de publicação e as transições válidas (card E4.4, item 3; RF019, ADR-0005 §3) — teste
 * de unidade, sem contexto Spring.
 *
 * <p>A publicação usa duas coisas daqui: o <b>estado de nascimento</b> ({@link
 * EstadoPublicacao#INICIAL}) e o <b>valor aceito</b> de um {@code estado} que o cliente mande. A
 * máquina de transições inteira é exercitada agora, mesmo com a Etapa 5 (retirar, excluir) fora do
 * escopo, porque é ela que o card manda ter <b>num só lugar</b>: uma transição declarada aqui e
 * não usada ainda é uma decisão registrada; uma transição inventada na Etapa 5 seria uma segunda
 * regra.
 */
@DisplayName("estado de publicação e transições (RF019)")
class EstadoPublicacaoTest {

    @Test
    @DisplayName("o material nasce PUBLICADO e é o único estado inicial declarado")
    void estadoInicialEhPublicado() {
        assertThat(EstadoPublicacao.INICIAL).isEqualTo(EstadoPublicacao.PUBLICADO);
        assertThat(EstadoPublicacao.values())
                .as("o enum cobre exatamente os valores do CHECK material_estado_valido (V1)")
                .containsExactlyInAnyOrder(EstadoPublicacao.PUBLICADO, EstadoPublicacao.RETIRADO,
                        EstadoPublicacao.EXCLUIDO);
    }

    @Test
    @DisplayName("a coluna do V1 só admite os nomes do enum — o mapeamento é direto, sem tradução")
    void nomesDoEnumSaoOsValoresDoBanco() {
        assertThat(EstadoPublicacao.valueOf("PUBLICADO")).isEqualTo(EstadoPublicacao.INICIAL);
        assertThat(EstadoPublicacao.PUBLICADO.name()).isEqualTo("PUBLICADO");
    }

    @Test
    @DisplayName("PUBLICADO pode ser retirado e excluído; não pode ir para si mesmo")
    void transicoesDePublicado() {
        assertThat(EstadoPublicacao.PUBLICADO.transicoesValidas())
                .containsExactlyInAnyOrder(EstadoPublicacao.RETIRADO, EstadoPublicacao.EXCLUIDO);
        assertThat(EstadoPublicacao.PUBLICADO.podeTransicionarPara(EstadoPublicacao.RETIRADO)).isTrue();
        assertThat(EstadoPublicacao.PUBLICADO.podeTransicionarPara(EstadoPublicacao.EXCLUIDO)).isTrue();
        assertThat(EstadoPublicacao.PUBLICADO.podeTransicionarPara(EstadoPublicacao.PUBLICADO))
                .as("repetir o mesmo estado não é transição: quem compara antes evita escrita à toa")
                .isFalse();
    }

    @Test
    @DisplayName("RETIRADO é reversível: volta a PUBLICADO ou vai a EXCLUIDO (republicação é a Etapa 5)")
    void transicoesDeRetirado() {
        assertThat(EstadoPublicacao.RETIRADO.transicoesValidas())
                .containsExactlyInAnyOrder(EstadoPublicacao.PUBLICADO, EstadoPublicacao.EXCLUIDO);
    }

    @Test
    @DisplayName("EXCLUIDO é terminal no MVP: nenhuma saída declarada (a máquina não promete o que não existe)")
    void excluidoEhTerminal() {
        assertThat(EstadoPublicacao.EXCLUIDO.transicoesValidas()).isEmpty();
        for (EstadoPublicacao destino : EstadoPublicacao.values()) {
            assertThat(EstadoPublicacao.EXCLUIDO.podeTransicionarPara(destino)).isFalse();
        }
    }

    @ParameterizedTest
    @EnumSource(EstadoPublicacao.class)
    @DisplayName("estado arbitrário (nulo) nunca é transição válida")
    void destinoNuloNaoEhTransicao(EstadoPublicacao origem) {
        assertThat(origem.podeTransicionarPara(null)).isFalse();
    }
}
