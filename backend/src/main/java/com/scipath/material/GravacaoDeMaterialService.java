package com.scipath.material;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Passo 3 da ordem obrigatória do RN008: gravar o material <b>na mesma transação</b>, depois de o
 * arquivo já ter sido recebido, validado (E4.2) e promovido para o armazenamento definitivo (E4.3).
 *
 * <p><b>Por que esta classe existe separada do serviço de publicação.</b> {@code @Transactional} é
 * aplicado por proxy: um método anotado chamado de dentro da própria classe <b>não</b> abre
 * transação nenhuma — a anotação ficaria ali sem efeito, que é pior que não existir, porque
 * alguém confiaria nela. A fronteira transacional é, portanto, um bean próprio. Ela cobre a
 * geração do slug e a gravação do material: ou a linha existe inteira, apontando para um
 * {@code file_asset} válido, ou nada foi gravado.
 *
 * <p>Hoje a transação envolve uma instrução só (o {@code INSERT} que devolve a linha por
 * {@code RETURNING}); a fronteira declarada é o que garante que a Etapa 5 — que reaproveita este
 * caminho para republicar e editar metadados — herde a mesma garantia sem ter de reabrir a
 * decisão.
 *
 * <p><b>A falha sobe, e quem a trata é o serviço de publicação.</b> Aqui nada é mascarado: uma
 * falha de banco sai como erro (500 do contrato, com o motivo só no log) e o arquivo já promovido
 * é marcado órfão por quem chamou (ADR-0005 §2). Engolir a exceção aqui deixaria um material não
 * gravado passando por publicado.
 */
@Service
public class GravacaoDeMaterialService {

    /**
     * Quantos sufixos numéricos são tentados antes de desistir ({@code titulo},
     * {@code titulo-2}, ...). O limite existe para o laço não ser infinito num cenário absurdo
     * (milhares de materiais homônimos); ele é <b>alto</b> de propósito: o slug é a URL do
     * material e desistir cedo demais transformaria "mais um trabalho com o mesmo título" em erro
     * de servidor.
     */
    static final int TENTATIVAS_DE_SLUG = 50;

    private final PublicacaoDeMaterialRepositorio repositorio;

    public GravacaoDeMaterialService(PublicacaoDeMaterialRepositorio repositorio) {
        this.repositorio = repositorio;
    }

    /**
     * Grava o material do arquivo informado e devolve o que o banco confirmou.
     *
     * <p><b>O slug é gerado aqui, no servidor, e é estável</b> (RN009, RNF025): o cliente não o
     * envia e nada nesta etapa o reescreve. O candidato base é determinístico a partir do título;
     * quando ele já pertence a outro material, o próximo é o mesmo slug com sufixo numérico —
     * a mesma estratégia do slug de perfil (E3.2), para que o identificador continue legível
     * ({@code introducao-a-algoritmos-2}) em vez de virar um valor aleatório.
     *
     * <p><b>A unicidade não depende do laço, e o laço não depende de sorte.</b> O {@code INSERT}
     * usa {@code ON CONFLICT (slug) DO NOTHING} e devolve vazio quando o slug já existia — inclusive
     * quando ele passou a existir <i>entre</i> a consulta e a gravação, por outra requisição. Nesse
     * caso o laço simplesmente tenta o próximo sufixo, <b>na mesma transação</b>: nada é gravado
     * pela tentativa perdedora, e o resultado final é uma linha com slug único.
     *
     * @param perfilId  perfil da sessão (a autoria, jamais um campo do formulário)
     * @param metadados metadados validados e taxonomia já resolvida em identificadores
     * @param arquivoId {@code file_asset.id} já registrado e promovido
     * @throws IllegalStateException nenhum candidato livre dentro de {@link #TENTATIVAS_DE_SLUG}
     */
    @Transactional
    public MaterialPublicadoResposta gravar(UUID perfilId, MetadadosDaPublicacao metadados, UUID arquivoId) {
        String base = SlugDeMaterial.derivar(metadados.titulo());

        for (int tentativa = 0; tentativa < TENTATIVAS_DE_SLUG; tentativa++) {
            String candidato = tentativa == 0 ? base : SlugDeMaterial.comSufixo(base, tentativa + 1);
            Optional<MaterialGravado> gravado = repositorio.inserir(candidato, metadados, perfilId, arquivoId);
            if (gravado.isPresent()) {
                return MaterialPublicadoResposta.de(gravado.get());
            }
        }

        throw new IllegalStateException("nenhum slug livre para o material em " + TENTATIVAS_DE_SLUG
                + " tentativas a partir da base '" + base + "'");
    }
}
