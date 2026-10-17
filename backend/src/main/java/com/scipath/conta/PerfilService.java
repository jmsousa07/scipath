package com.scipath.conta;

import com.scipath.autor.PerfilPublico;
import com.scipath.error.ConflitoDeUnicidadeException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Edição dos campos públicos do próprio perfil (RF011, RN009, RN011) — a regra de negócio do
 * card E3.5.
 *
 * <p><b>A propriedade vem da sessão.</b> O serviço recebe o {@code perfilId} que o
 * {@code InterceptorDeIdentidade} resolveu a partir da cookie (ADR-0003 (j), CA007) e o
 * {@code UPDATE} é por esse id: não há caminho em que o corpo da requisição escolha a linha
 * alterada. A assinatura é a garantia — não existe parâmetro de identificador vindo do
 * cliente, então não existe ramo de código que possa obedecer a um.
 *
 * <p><b>Unicidade em duas partes, como no cadastro.</b> A consulta
 * ({@link PerfilRepositorio#slugEmUsoPorOutro}) existe para o formulário poder apontar o campo
 * culpado com <b>409</b> antes de tentar gravar; a {@code UNIQUE profile_slug_unico} continua
 * sendo o juiz final — se duas edições concorrentes escolherem o mesmo slug, o banco recusa a
 * segunda e o {@code DuplicateKeyException} sai como o mesmo 409
 * ({@link com.scipath.error.ApiExceptionHandler}). A consulta <b>não</b> substitui a
 * constraint. Repetir o próprio slug é permitido: o que colide é o slug de <i>outra</i> conta.
 *
 * <p><b>A resposta sai do banco, não da entrada.</b> Depois de gravar, o perfil é relido e é o
 * que o SQL devolveu que vai para o cliente (RN013) — o 200 não pode afirmar um estado que a
 * tabela não tem. A projeção é {@link PerfilPublico}, o mesmo schema do perfil público do
 * autor: um só lugar descreve os campos públicos, e nenhum deles é dado de conta (RNF013).
 *
 * <p><b>Editar não exige e-mail verificado</b> (ADR-0003 (b)): conta
 * {@code PENDENTE_VERIFICACAO} edita o próprio perfil normalmente. Quem exige {@code ATIVO} é a
 * publicação, e quem exige sessão válida é o interceptor — este serviço não repete nenhuma das
 * duas regras.
 */
@Service
public class PerfilService {

    private final PerfilRepositorio perfis;

    public PerfilService(PerfilRepositorio perfis) {
        this.perfis = perfis;
    }

    /**
     * Substitui os campos públicos do perfil da própria conta e devolve o perfil como ficou
     * gravado.
     *
     * <p>O conjunto é substituído, não mesclado (contrato v0.4): campo ausente ou vazio vira
     * {@code NULL} na coluna, que é o estado de "não informado" — o mesmo de um perfil que
     * nunca passou pela edição.
     *
     * @param perfilId  perfil da sessão — a única origem aceitável da propriedade
     * @param requisicao corpo já validado e normalizado (opcional vazio = ausente)
     * @throws ConflitoDeUnicidadeException o slug já pertence a outro perfil (409)
     */
    @Transactional
    public PerfilPublico editar(UUID perfilId, EdicaoPerfilRequisicao requisicao) {
        if (perfis.slugEmUsoPorOutro(requisicao.slug(), perfilId)) {
            throw new ConflitoDeUnicidadeException("slug", "já está em uso",
                    "Este identificador público já está em uso.");
        }

        perfis.atualizar(perfilId, requisicao.nomePublico(), requisicao.slug(),
                requisicao.biografia(), requisicao.afiliacaoDeclarada(),
                requisicao.areasInteresse(), requisicao.linksExternos());

        return perfis.buscarPublicoPorId(perfilId).orElseThrow(() -> new IllegalStateException(
                "perfil recém-editado não foi encontrado — a transação da edição não se completou"));
    }
}
