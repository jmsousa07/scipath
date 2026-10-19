package com.scipath.arquivo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Executa a coleta <b>uma vez, na subida</b>, quando {@code scipath.coleta.ao-iniciar=true}
 * (card E4.5, item 6 do escopo).
 *
 * <p><b>Existe para haver um comando manual de verificação.</b> O agendamento diário é a forma de
 * disparo do produto, mas ele não serve para quem precisa <i>ver</i> a coleta agora — esperar até as
 * 03:30 não é verificação. Esta classe dá ao operador o caminho de uma passada só:
 *
 * <pre>{@code
 * make coleta:orfaos
 * }</pre>
 *
 * <p>que sobe a aplicação com a propriedade ligada e <b>sem servidor web</b>
 * ({@code --spring.main.web-application-type=none}): a varredura roda na subida, o resultado sai no
 * log com o prefixo {@code [coleta-orfaos]} e o processo termina. Nenhuma porta é aberta, nenhum
 * agendador fica esperando, nenhum console de administração é criado (interface de administração
 * está fora do escopo do MVP).
 *
 * <p><b>Desligado por padrão.</b> Quem sobe a API normalmente não quer uma varredura a cada
 * reinício — e uma varredura que ninguém pediu é uma operação de limpeza a mais, não a menos. A
 * propriedade é lida uma vez, na construção: ligá-la é decisão de subida, não estado mutável.
 */
@Component
public class ColetaDeOrfaosAoIniciar implements ApplicationRunner {

    private final ColetorDeOrfaosService coletor;
    private final boolean habilitado;

    public ColetaDeOrfaosAoIniciar(ColetorDeOrfaosService coletor,
            @Value("${scipath.coleta.ao-iniciar:false}") boolean habilitado) {
        this.coletor = coletor;
        this.habilitado = habilitado;
    }

    /** Roda a varredura quando habilitado; não faz nada quando não está — nem log de "pulei". */
    @Override
    public void run(ApplicationArguments args) {
        if (habilitado) {
            coletor.coletar();
        }
    }
}
