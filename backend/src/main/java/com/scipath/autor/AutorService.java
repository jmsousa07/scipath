package com.scipath.autor;

import com.scipath.error.NaoEncontradoException;
import com.scipath.material.MateriaisService;
import org.springframework.stereotype.Service;

/**
 * Regra de leitura do perfil público do autor (RF006) — a composição que o contrato publica
 * em {@code GET /api/autores/{slug}}: identidade pública <b>e</b> a página dos materiais
 * publicados dele, na mesma resposta.
 *
 * <p><b>A ordem das duas leituras é a regra.</b> A identidade vem primeiro e é ela que decide
 * o 404: se o perfil não é público (inexistente ou conta não ativa), a página de materiais
 * nem é consultada — não existe caminho em que o autor responda "não sou público" e ainda
 * assim devolva a lista dele. Depois do 200, "sem material publicado" <b>não</b> é erro: é
 * 200 com {@code items: []} (o card E2.5 é explícito, e é o que separa "autor sem acervo" de
 * "perfil que não existe").
 *
 * <p><b>O 404 é indistinguível de propósito</b> (RN002, CA008), e pelo mesmo motivo do
 * detalhe do material: o detalhe é {@code null} porque o texto vem do padrão único de
 * {@link com.scipath.error.ApiExceptionHandler} — uma mensagem própria aqui poderia divergir
 * do handler e, pior, diferenciar "não existe" de "não é público".
 *
 * <p>A página de materiais é pedida ao {@link MateriaisService}: é ele que monta o envelope
 * ({@code items/page/size/total}) e conhece a elegibilidade pública. O perfil do autor não
 * tem uma segunda definição de "material público" — se tivesse, a lista desta tela e a de
 * {@code GET /api/materiais} poderiam divergir sem ninguém notar (RN013).
 */
@Service
public class AutorService {

    private final AutorRepositorio repositorio;
    private final MateriaisService materiais;

    public AutorService(AutorRepositorio repositorio, MateriaisService materiais) {
        this.repositorio = repositorio;
        this.materiais = materiais;
    }

    /**
     * Perfil público do autor e a página pedida dos materiais publicados dele.
     *
     * <p>A identidade sai sempre completa; {@code page} e {@code size} paginam apenas os
     * materiais (contrato v0.3).
     *
     * @param slug    slug já validado no controller (formato e tamanho do contrato)
     * @param pagina  página dos materiais, começando em 0
     * @param tamanho itens por página, já limitado pelo teto do contrato
     * @throws NaoEncontradoException perfil inexistente ou de conta não ativa
     */
    public AutorPublicoResposta perfil(String slug, int pagina, int tamanho) {
        PerfilPublico perfil = repositorio.buscarPerfilVisivelPorSlug(slug)
                .orElseThrow(() -> new NaoEncontradoException(null));

        return new AutorPublicoResposta(
                perfil.slug(),
                perfil.nomePublico(),
                perfil.biografia(),
                perfil.afiliacaoDeclarada(),
                perfil.areasInteresse(),
                perfil.linksExternos(),
                materiais.listarDoAutor(perfil.slug(), pagina, tamanho));
    }
}
