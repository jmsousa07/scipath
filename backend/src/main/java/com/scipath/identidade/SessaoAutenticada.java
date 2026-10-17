package com.scipath.identidade;

import com.scipath.autor.PerfilPublico;
import com.scipath.conta.EstadoConta;
import java.util.UUID;

/**
 * Quem está do outro lado da requisição — o que uma rota privada recebe ao declarar
 * {@code @IdentidadeAutenticada}.
 *
 * <p><b>A propriedade vem daqui, nunca do payload</b> (ADR-0003 (j), CA007): quem opera sobre
 * recurso próprio usa {@link #perfilId()} ou {@link #contaId()} desta identidade, e não um
 * identificador que o cliente mandou no corpo ou no caminho. É por isso que o record não tem
 * campo de sessão: nada aqui pode ser reapresentado para autenticar.
 *
 * <p>O schema {@code SessaoAtual} do contrato não tem e-mail, hash, token nem identificador de
 * conta; o record também não. {@link #perfil()} é o {@code PerfilPublico} compartilhado com o
 * perfil público do autor (E2.5) e com o cadastro (E3.2) — um só lugar descreve os campos
 * públicos.
 */
public record SessaoAutenticada(
        UUID contaId,
        UUID perfilId,
        EstadoConta estadoConta,
        PerfilPublico perfil) {
}
