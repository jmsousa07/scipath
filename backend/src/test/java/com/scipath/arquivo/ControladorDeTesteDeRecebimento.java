package com.scipath.arquivo;

import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Borda HTTP <b>de teste</b> do recebimento de arquivo — o adaptador mais fino possível entre
 * {@code multipart/form-data} e {@link RecepcaoDeArquivoService}.
 *
 * <p><b>Por que existe.</b> O card E4.2 entrega a <i>recepção e validação</i> do arquivo; o
 * endpoint do contrato que a usa é o {@code POST /materiais}, e ele é do E4.4 (metadados,
 * taxonomia, autor da sessão e gravação do material na mesma transação). O critério de aceite
 * deste card, porém, fala em respostas HTTP — 415 para PDF renomeado, 413 acima do limite, 400
 * para campo ausente —, e classe de exceção não é resposta: só o caminho completo do MVC prova
 * que o {@code ProblemDetail} sai com o status, o {@code type} e o {@code detail} certos,
 * passando pelo contêiner e pelo tratamento único de erro.
 *
 * <p>A alternativa seria um {@code @TestConfiguration} com este controller dentro: mais limpo de
 * ler e mais caro de rodar — contexto Spring novo, e a máquina tem 954 MB (AGENTS.md, higiene de
 * recursos). Aqui a classe é um {@code @RestController} de {@code src/test} que a varredura de
 * componentes do contexto <b>já subido</b> enxerga (as classes de teste estão no classpath),
 * com {@code @Profile("test")} para que ela nunca exista fora do perfil de teste. Nada disso vai
 * para o artefato: {@code src/test} não entra no build de produção, e o caminho
 * ({@code /api/teste/...}) não colide com nenhum do contrato.
 *
 * <p><b>O que ele responde de propósito.</b> Só metadado do arquivo — nunca o caminho em disco
 * nem a chave interna (RF014: o contrato não publica nem um nem outro). A resposta prova que o
 * arquivo foi validado e registrado, sem ser uma segunda fronteira de API.
 */
@RestController
@Profile("test")
@RequestMapping(ControladorDeTesteDeRecebimento.CAMINHO)
class ControladorDeTesteDeRecebimento {

    static final String CAMINHO = "/api/teste/recebimento-de-arquivo";

    private final RecepcaoDeArquivoService recepcao;

    ControladorDeTesteDeRecebimento(RecepcaoDeArquivoService recepcao) {
        this.recepcao = recepcao;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    RespostaDeRecebimento receber(@RequestPart("arquivo") MultipartFile arquivo) {
        ArquivoRecebido recebido = recepcao.receber(arquivo);
        return new RespostaDeRecebimento(recebido.nomeOriginalSeguro(), recebido.tipoDetectado(),
                recebido.tamanhoBytes(), recebido.checksum());
    }

    /**
     * Metadados do arquivo recebido que a suíte pode conferir. Nem o {@code id} interno do
     * {@code file_asset}, nem a chave de storage, nem o caminho em disco aparecem — a suíte lê os
     * dois direto do banco e do sistema de arquivos, como qualquer consumidor faria pela
     * fronteira real (RF014).
     */
    record RespostaDeRecebimento(String nomeOriginalSeguro, String tipoDetectado,
            long tamanhoBytes, String checksum) {
    }
}
