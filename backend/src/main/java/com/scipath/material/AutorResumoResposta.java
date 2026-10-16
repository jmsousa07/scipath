package com.scipath.material;

/**
 * Identidade pública do autor na listagem — o schema {@code AutorResumo} do contrato v0.3.
 *
 * <p>Só o que a listagem precisa para exibir o crédito de autoria e linkar o perfil
 * (RF002, RF007): o {@code slug} é a chave da rota {@code /autores/{slug}} e
 * {@code nomePublico} é o nome exibido. Não existe e-mail, id de conta, nem qualquer outro
 * dado de identidade de login — o DTO público é o que impede o vazamento por engano
 * (RNF013, contrato v0.3: "Nunca carrega e-mail, conta, hash ou token").
 */
public record AutorResumoResposta(String slug, String nomePublico) {
}
