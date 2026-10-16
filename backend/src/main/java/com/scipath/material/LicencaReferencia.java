package com.scipath.material;

/**
 * Licença do material na descoberta — schema {@code LicencaRef} do contrato v0.3.
 *
 * <p>{@code descricaoCurta} é <b>obrigatória</b> (RN006): é ela que impede a leitura errada
 * de que estar público significa domínio público (DRS §13.2). A listagem nunca publica só o
 * nome da licença.
 *
 * <p>O schema do contrato admite também {@code identificador} e {@code urlReferencia}. Eles
 * ficam fora desta resposta porque o item da listagem os usaria só em link secundário: a
 * listagem publica o que o card publica (nome + o que a licença permite), e o texto de
 * referência da licença entra no detalhe (E2.4), onde há espaço para ele. Publicar campo
 * que a tela não usa seria fronteira contra ficção (DRS §21.1), o mesmo critério que a
 * Etapa 2 usou para não publicar o acesso ao arquivo.
 */
public record LicencaReferencia(String slug, String nome, String descricaoCurta) {
}
