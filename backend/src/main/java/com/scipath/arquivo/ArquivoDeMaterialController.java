package com.scipath.arquivo;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/materiais/{slug}/arquivo} — o download do arquivo de um material público
 * (RF014, contrato v0.5).
 *
 * <p><b>O arquivo é servido pelo servidor, nunca por endereço direto.</b> Esta rota é o único
 * caminho pelo qual o conteúdo sai, e ela passa pela decisão de visibilidade de
 * {@link AcessoAoArquivoService}. O binário mora fora de qualquer raiz servida (ADR-0005 §2) e a
 * resposta não carrega caminho, chave de armazenamento nem checksum (RF014, RNF014): o cliente
 * recebe o arquivo e o nome sugerido para salvá-lo, nada mais.
 *
 * <p><b>O 404 é o mesmo para tudo que não é público</b> — inexistente, {@code RETIRADO},
 * {@code EXCLUIDO}, conta não ativa —, indistinguível, decidido no servidor (RN002, RN003, CA008).
 * Slug malformado é outra coisa: é <b>400</b> do contrato (o endereço está errado, não o recurso
 * ausente), com os mesmos recortes do {@code SlugRecurso} que o detalhe já usa — formato de slug e
 * no máximo 120 caracteres. Misturar os dois faria o cliente tratar entrada inválida como material
 * que não existe.
 *
 * <p>Não exige sessão: é a leitura pública do arquivo publicado, e é GET, logo fora do CSRF das
 * escritas (E3.3).
 */
@RestController
@RequestMapping("/api/materiais/{slug}/arquivo")
@Validated
public class ArquivoDeMaterialController {

    /** Formato de slug do contrato — o mesmo padrão do detalhe ({@code MateriaisController}). */
    private static final String FORMATO_SLUG = "^[a-z0-9]+(-[a-z0-9]+)*$";

    /** Teto de comprimento do slug no contrato ({@code SlugRecurso}, {@code maxLength: 120}). */
    static final int COMPRIMENTO_MAXIMO_SLUG = 120;

    private final AcessoAoArquivoService acesso;

    public ArquivoDeMaterialController(AcessoAoArquivoService acesso) {
        this.acesso = acesso;
    }

    /**
     * Entrega o arquivo do material público.
     *
     * <p>O conteúdo vai como {@link Resource}: o Spring o transmite em blocos e fecha o stream, sem
     * que a aplicação carregue o arquivo inteiro em memória — o limite é de 30 MB e a máquina tem
     * 954 MB (AGENTS.md, higiene de recursos).
     *
     * <p>{@code Content-Type} é declarado aqui e não com {@code produces}: o contrato não publica um
     * {@code 406}, e um {@code Accept} que não fosse {@code application/pdf} passaria a recusar um
     * download que o servidor sabe responder.
     */
    @GetMapping
    public ResponseEntity<Resource> arquivo(
            @PathVariable("slug")
            @Pattern(regexp = FORMATO_SLUG,
                    message = "deve ser um slug (minúsculas, dígitos e hífen), como introducao-a-algoritmos-de-ordenacao")
            @Size(max = COMPRIMENTO_MAXIMO_SLUG,
                    message = "deve ter no máximo " + COMPRIMENTO_MAXIMO_SLUG + " caracteres")
            String slug) {
        ArquivoServivel servivel = acesso.buscarPorSlug(slug);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                // Tamanho do registro do recebimento: a integridade já foi conferida contra o
                // checksum, então o conteúdo servido é o que foi medido (RN008).
                .contentLength(servivel.tamanhoBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, CabecalhoDeDownload.para(servivel.nomeOriginal()))
                .body(servivel.conteudo());
    }
}
