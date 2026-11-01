package com.scipath.arquivo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Executa uma única passada de expurgo na subida quando habilitado (alvo make expurgo:retencao). */
@Component
public class ExpurgoDeRetencaoAoIniciar implements ApplicationRunner {

    private final ExpurgoDeRetencaoService expurgo;
    private final boolean habilitado;

    public ExpurgoDeRetencaoAoIniciar(ExpurgoDeRetencaoService expurgo,
            @Value("${scipath.expurgo.ao-iniciar:false}") boolean habilitado) {
        this.expurgo = expurgo;
        this.habilitado = habilitado;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (habilitado) {
            expurgo.expurgar();
        }
    }
}
