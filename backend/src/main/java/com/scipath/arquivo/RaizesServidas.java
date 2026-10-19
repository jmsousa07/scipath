package com.scipath.arquivo;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reconhece um caminho que cai <b>dentro de uma raiz servida</b> — um diretório de onde o
 * servidor entrega arquivo estático, ou de onde o build do frontend publica os seus.
 *
 * <p><b>Por que isto existe no código, e não só no teste.</b> O ADR-0005 §2 fixa que o
 * arquivo publicado mora <i>fora</i> de diretório público e é entregue por endpoint que
 * verifica visibilidade e autorização (RN002, RN003, RF014). Um arquivo sob
 * {@code resources/static} ou {@code frontend/public} seria servido pelo contêiner
 * <b>sem</b> passar por essa decisão — o endereço direto venceria a regra. O critério do
 * card pede um teste que prove que o diretório de storage não está em raiz servida; a
 * prova mais forte é a configuração ser <b>recusada na subida</b> quando estiver — falha
 * alta e imediata, em vez de vazamento silencioso que só aparece em auditoria.
 *
 * <p><b>É um reconhecedor estrutural, não uma lista mágica de caminhos absolutos.</b> O
 * diretório é comparado por <i>sequência de segmentos</i>, porque o valor configurado é
 * relativo à máquina de quem opera e pode estar em qualquer lugar do disco: o que não pode
 * é um trecho do caminho significar "esta é uma raiz servida". Comparar por prefixo de
 * segmentos evita o falso positivo de substring — {@code resources-static/} não é
 * {@code resources/static/}.
 *
 * <p><b>Conservador de propósito.</b> Recusar um caminho que apenas <i>parece</i> raiz
 * servida custa uma linha de configuração a quem opera; aceitar um que é de verdade custa
 * o vazamento do arquivo. O erro é escolhido pelo lado barato.
 */
final class RaizesServidas {

    /**
     * Sequências de segmentos que caracterizam raiz servida. Cada marcador é comparado
     * como sequência <b>contígua</b> de segmentos do caminho:
     *
     * <ul>
     *   <li>{@code src/main/resources} — a árvore de recursos do próprio backend;</li>
     *   <li>{@code resources/static}, {@code resources/public},
     *       {@code resources/META-INF/resources} — as raízes estáticas padrão do Spring Boot,
     *       reconhecidas também fora de {@code src/main} (diretório de saída, outro projeto);</li>
     *   <li>{@code classes/static}, {@code classes/public}, {@code classes/resources},
     *       {@code classes/META-INF/resources} — as mesmas raízes <b>no classpath compilado</b>
     *       ({@code target/classes}), que é de onde o servidor as serve em execução. Um arquivo sob
     *       {@code target/classes/static} seria entregue por endereço direto exatamente como um sob
     *       {@code src/main/resources/static} — a diferença é só qual cópia o servidor lê;</li>
     *   <li>{@code frontend/public} e {@code frontend/static} — de onde o Next entrega
     *       arquivo público; {@code frontend/.next} — a saída de build, que também é servida.</li>
     * </ul>
     */
    private static final List<List<String>> MARCADORES = List.of(
            List.of("src", "main", "resources"),
            List.of("resources", "static"),
            List.of("resources", "public"),
            List.of("resources", "META-INF", "resources"),
            List.of("classes", "static"),
            List.of("classes", "public"),
            List.of("classes", "resources"),
            List.of("classes", "META-INF", "resources"),
            List.of("frontend", "public"),
            List.of("frontend", "static"),
            List.of("frontend", ".next"));

    private RaizesServidas() {
    }

    /**
     * O caminho (ele próprio ou um ancestral) está sob uma raiz servida?
     *
     * @param caminho caminho de diretório, absoluto ou relativo — é normalizado aqui para
     *                que {@code ./target/../frontend/public} também seja reconhecido
     */
    static boolean contem(Path caminho) {
        List<String> segmentos = segmentosDe(caminho);
        for (List<String> marcador : MARCADORES) {
            if (contemSequencia(segmentos, marcador)) {
                return true;
            }
        }
        return false;
    }

    /** Segmentos do caminho normalizado, sem a raiz ({@code /} não é segmento). */
    private static List<String> segmentosDe(Path caminho) {
        List<String> segmentos = new ArrayList<>();
        for (Path parte : caminho.toAbsolutePath().normalize()) {
            segmentos.add(parte.toString());
        }
        return segmentos;
    }

    /** O marcador aparece <b>em sequência</b> dentro dos segmentos? */
    private static boolean contemSequencia(List<String> segmentos, List<String> marcador) {
        for (int inicio = 0; inicio + marcador.size() <= segmentos.size(); inicio++) {
            if (segmentos.subList(inicio, inicio + marcador.size()).equals(marcador)) {
                return true;
            }
        }
        return false;
    }
}
