package com.scipath.material;

import java.time.Instant;

/**
 * O detalhe de um material público <b>lido do banco</b> — a linha do material com o arquivo, antes
 * de virar resposta (RF005, RF014).
 *
 * <p><b>Não é schema do contrato.</b> É a projeção interna que o {@link MateriaisService} recebe do
 * repositório para montar o {@link MaterialDetalheResposta}: os campos que o contrato publica
 * <b>mais</b> o que ele nunca publica e a decisão do serviço precisa — o estado do
 * {@code file_asset} e a chave do armazenamento, que decidem a disponibilidade do arquivo.
 *
 * <p><b>Por que a decisão não mora no repositório.</b> O repositório responde "o que o SQL devolve
 * para este material público" (a elegibilidade de RN002, que já é dele desde a Etapa 2). "O arquivo
 * pode ser servido agora" é outra pergunta, e ela não se responde em SQL: depende do
 * armazenamento. Separá-las é o que permite ler a linha sem consultar disco, e consultar disco
 * (uma vez, no serviço) sem esconder o efeito dentro do mapeamento de uma consulta.
 *
 * @param slug             identificador público do material
 * @param titulo           título (RN004)
 * @param descricao        descrição (RN004)
 * @param autor            autor público — slug e nome, nada privado (RF007, RNF013)
 * @param categoria        categoria com o slug estável da taxonomia (ADR-0006)
 * @param tipo             tipo de material com o slug estável da taxonomia
 * @param licenca          licença com o texto de referência, como o detalhe publica (RN006)
 * @param estadoPublicacao estado do material no banco; nesta superfície só chega {@code PUBLICADO}
 *                         (RN002) — o campo existe porque a resposta o publica, não para escolher
 * @param publicadoEm      instante da publicação
 * @param atualizadoEm     instante da última atualização
 * @param arquivo          o arquivo do material, lido como o download o lê ({@link ArquivoDoMaterial})
 */
public record DetalheDoMaterial(
        String slug,
        String titulo,
        String descricao,
        AutorResumoResposta autor,
        CategoriaReferencia categoria,
        TipoMaterialReferencia tipo,
        LicencaDetalheResposta licenca,
        String estadoPublicacao,
        Instant publicadoEm,
        Instant atualizadoEm,
        ArquivoDoMaterial arquivo) {
}
