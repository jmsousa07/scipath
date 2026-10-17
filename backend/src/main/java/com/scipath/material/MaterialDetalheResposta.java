package com.scipath.material;

import java.time.Instant;

/**
 * Detalhe público de um material — schema {@code MaterialDetalhe} do contrato v0.3 (RF005):
 * o que o visitante precisa para entender o material e confiar no que está vendo.
 *
 * <p><b>Autoria.</b> {@code autor} é o {@link AutorResumoResposta} — slug e nome público, os
 * mesmos da listagem. Nome e slug bastam para creditar e linkar o perfil (RF007); e-mail,
 * id de conta, hash de senha e token não existem neste schema (RNF013).
 *
 * <p><b>Licença.</b> Vem sempre com a explicação do que permite (RN006) — ver
 * {@link LicencaDetalheResposta}; o detalhe é onde o texto de referência da licença tem
 * espaço, e é por isso que ele publica {@code identificador} e {@code urlReferencia} que a
 * listagem deixa de fora.
 *
 * <p><b>Estado.</b> {@code estado} é sempre {@code PUBLICADO}: é a única condição que chega
 * aqui, porque a consulta só devolve material PUBLICADO de conta ATIVA (RN002, CA008) e
 * material RETIRADO, EXCLUIDO ou de conta não ativa responde 404 — indistinguível de
 * inexistente, que é o que não deixa vazar a existência do que saiu do público. Não existe
 * enum de estados de publicação neste ponto do código: criá-lo seria vocabulário da Etapa 3
 * (publicação, retirada, exclusão), onde ele nasce com uso real.
 *
 * <p><b>Transparência (RN010, RN011, CA012).</b> Nenhum campo deste schema — nem aqui, nem no
 * contrato — sugere revisão, certificação, curadoria ou verificação científica: o que o
 * visitante lê é o que o autor declarou.
 *
 * <p>{@code atualizadoEm} é <b>sempre</b> publicado neste schema (o contrato o lista em
 * {@code required}), inclusive quando coincide com {@code publicadoEm} — ao contrário do item
 * de listagem, que o omite. No detalhe a data de última atualização é informação do próprio
 * material, não repetição de rótulo de card.
 *
 * <p>Sem acesso ao arquivo: a Etapa 2 não serve arquivo, e publicar campo de download sem
 * implementação seria fronteira contra ficção (DRS §21.1, RF014 na Etapa 4).
 */
public record MaterialDetalheResposta(
        String slug,
        String titulo,
        String descricao,
        AutorResumoResposta autor,
        CategoriaReferencia categoria,
        TipoMaterialReferencia tipo,
        LicencaDetalheResposta licenca,
        String estado,
        Instant publicadoEm,
        Instant atualizadoEm) {
}
