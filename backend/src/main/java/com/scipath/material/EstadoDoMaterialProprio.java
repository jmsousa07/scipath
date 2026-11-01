package com.scipath.material;

import com.scipath.conta.EstadoConta;
import java.util.UUID;

/** Estado da linha e da conta, lidos sob bloqueio para validar uma transição sem corrida. */
record EstadoDoMaterialProprio(UUID perfilId, EstadoPublicacao estadoPublicacao, EstadoConta estadoConta) {
}
