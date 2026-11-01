package com.scipath.material;

/**
 * Corpo do {@code PUT /api/perfil/atual/materiais/{slug}} — schema {@code EdicaoDeMaterial} do
 * contrato v0.6 (RF016, ADR-0011 §4).
 *
 * <p><b>Sem Bean Validation, e de propósito.</b> O contrato separa <b>400</b> (campo obrigatório
 * ausente — "o servidor não conseguiu ler o pedido como veio") de <b>422</b> (campo presente com
 * valor inválido). Bean Validation só sabe o segundo: um {@code @NotBlank} num campo ausente
 * devolve 422 e apaga a distinção que a tela usa para decidir entre "pedido incompleto" e "corrija
 * este campo". Por isso o record carrega os valores <b>crus</b>, e quem decide é
 * {@link ValidadorDeMetadadosDeMaterial} — a mesma regra que a publicação aplica (RF018).
 *
 * <p><b>O corpo é aberto a campos que o schema não publica, e é o contrato que o diz.</b> O
 * contrato v0.6 é explícito: {@code autorSlug}, {@code contaId} e {@code perfilId} "são
 * <i>ignorados</i> — não há campo para eles" (§4, RN001). Ignorar é a resposta certa e a mais
 * forte: a autoria e a propriedade vêm da sessão, e <b>não existe caminho de código que leia um
 * identificador do corpo</b> — este record não tem o campo, então o Jackson descarta o que não está
 * declarado. É o inverso deliberado de {@code EdicaoPerfilRequisicao} (E3.5), que recusa com 400
 * um campo fora do schema: lá o contrato da v0.4 publica essa recusa; aqui o da v0.6 publica o
 * ignorar, e inventar uma recusa que o cliente não sabe ler seria contrariar a fronteira
 * (AGENTS.md regra 1).
 *
 * <p><b>O que este corpo não tem, e não é omissão:</b> {@code slug} (a URL canônica pública,
 * RNF025 — editar metadados não troca o endereço), {@code estado} (é o {@code PATCH}, E5.4),
 * o arquivo (trocar o binário é outra operação) e {@code declaracaoDireito} (pertence ao ato de
 * publicar, RF012: a autoria do registro não muda).
 */
public record EdicaoDeMaterialRequisicao(
        String titulo,
        String descricao,
        String categoriaSlug,
        String tipoSlug,
        String licencaSlug) {
}
