package com.scipath.material;

import com.scipath.arquivo.ArquivoRecebido;
import com.scipath.arquivo.ArquivosRepositorio;
import com.scipath.arquivo.RecepcaoDeArquivoService;
import com.scipath.arquivo.StorageService;
import com.scipath.conta.EstadoConta;
import com.scipath.error.AcessoNegadoException;
import com.scipath.error.CampoObrigatorioAusenteException;
import com.scipath.error.ValidacaoDeCampoException;
import com.scipath.identidade.SessaoAutenticada;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Publicação de material próprio (RF012, RF018, RF019, RN008) — o caminho de {@code POST
 * /api/materiais} do contrato v0.5.
 *
 * <p>A ordem do caminho é a do ADR-0005 §2, e cada passo tem um dono:
 *
 * <ol>
 *   <li><b>Autorização e metadados.</b> Conta {@code ATIVO} (ADR-0003 (b)) e metadados válidos
 *       antes de tocar no arquivo: recusar 403/422 não pode custar disco nem storage. A ordem
 *       obrigatória do ADR-0005 §2 governa o <b>arquivo</b> (receber → validar → mover → gravar) e
 *       continua intacta; o que vem antes dela é o que <i>não tem efeito colateral</i>.</li>
 *   <li><b>Receber e validar o arquivo</b> — {@link RecepcaoDeArquivoService} (E4.2): conteúdo,
 *       limite e checksum, com a linha de {@code file_asset} nascendo já {@code VALIDADO}.</li>
 *   <li><b>Mover para o armazenamento definitivo</b> sob a chave interna — {@link StorageService}
 *       (E4.3). É um movimento: a área temporária fica vazia (RN012).</li>
 *   <li><b>Gravar o material na mesma transação</b> — {@link GravacaoDeMaterialService}.</li>
 * </ol>
 *
 * <p><b>O que "gravar {@code file_asset} + {@code material} na mesma transação" significa aqui</b>
 * (card E4.4, item 2). A linha de {@code file_asset} nasce no <b>recebimento</b> (E4.2), em
 * transação própria, já {@code VALIDADO} — é o que a suíte do E4.2 prova e o que a leitura do
 * arquivo (E4.3) consulta. Ela não é regravada nesta publicação: regravar a mesma linha seria
 * outra operação, com outro risco, e a publicação não a pede. O que a transação daqui garante é
 * <b>o par material↔arquivo</b>: o {@code material} só passa a existir apontando para um
 * {@code file_asset} {@code VALIDADO}, e se ele não for gravado, o arquivo vira {@code ORFAO} em
 * vez de ficar servível pela metade. É o efeito que o card pede — falha não deixa material
 * público incompleto <b>nem</b> arquivo validado servido por engano (RN008 §4).
 *
 * <p><b>A autoria vem da sessão.</b> O {@code profile_id} gravado é o {@code perfilId} de
 * {@link SessaoAutenticada} — o formulário não tem campo de autor, e não há caminho de código que
 * leia um (ADR-0003 (j), RF012).
 *
 * <p><b>Estado inicial {@link EstadoPublicacao#INICIAL}</b> (RF019). O cliente não define estado: se
 * ele enviar um {@code estado}, só o próprio estado inicial é aceito (coincide com a decisão do
 * servidor) e qualquer outro valor é <b>recusado com 422</b> — requisição arbitrária não define
 * estado.
 *
 * <p><b>Falha depois do movimento marca o arquivo como órfão</b> (ADR-0005 §2, RN008). O arquivo
 * já foi promovido e o material não existe: o registro em {@code file_asset} passa a
 * {@code ORFAO} (o estado que o coletor do E4.5 procura) e <b>nenhum material público fica
 * incompleto</b> — a transação não gravou linha nenhuma. A marcação acontece <b>fora</b> da
 * transação (é justamente o que precisa sobreviver ao rollback dela) e é melhor esforço: se ela
 * própria falhar, a falha original continua sendo a que sobe, e o registro órfão sem material
 * continua sendo encontrável pelo coletor — que procura exatamente isso.
 */
@Service
public class PublicacaoDeMaterialService {

    /** Teto do título no contrato ({@code PublicacaoMaterial.titulo.maxLength}). */
    static final int TITULO_MAXIMO = 200;

    /** Teto da descrição no contrato ({@code PublicacaoMaterial.descricao.maxLength}). */
    static final int DESCRICAO_MAXIMA = 2000;

    /** Formato de slug do contrato, o mesmo de {@code FiltroCategoria} e de {@code SlugRecurso}. */
    static final String FORMATO_SLUG = "^[a-z0-9]+(-[a-z0-9]+)*$";

    /** Formato do slug de licença: o do contrato admite ponto ({@code cc-by-4.0}). */
    static final String FORMATO_SLUG_DE_LICENCA = "^[a-z0-9]+(-[a-z0-9.]+)*$";

    /**
     * Detalhe do 403 para conta sem e-mail confirmado — <b>o texto do contrato</b> (exemplo
     * {@code emailNaoVerificado} de {@code ErroAcessoNegado}). Diz o que falta, para a tela
     * oferecer o reenvio de verificação em vez de um erro cru (ADR-0003 (h)).
     */
    static final String DETALHE_EMAIL_NAO_VERIFICADO = "Confirme seu e-mail para publicar material.";

    private static final Logger log = LoggerFactory.getLogger(PublicacaoDeMaterialService.class);

    private final RecepcaoDeArquivoService recepcao;
    private final StorageService armazenamento;
    private final ArquivosRepositorio arquivos;
    private final PublicacaoDeMaterialRepositorio repositorio;
    private final GravacaoDeMaterialService gravacao;

    public PublicacaoDeMaterialService(RecepcaoDeArquivoService recepcao, StorageService armazenamento,
            ArquivosRepositorio arquivos, PublicacaoDeMaterialRepositorio repositorio,
            GravacaoDeMaterialService gravacao) {
        this.recepcao = recepcao;
        this.armazenamento = armazenamento;
        this.arquivos = arquivos;
        this.repositorio = repositorio;
        this.gravacao = gravacao;
    }

    /**
     * Publica o material da conta autenticada e devolve o que o banco confirmou ter gravado.
     *
     * @param sessao    identidade da sessão — a única origem da autoria
     * @param requisicao formulário cru (a validação é aqui)
     * @param arquivo   parte {@code arquivo} do {@code multipart/form-data}; ausente é 400
     * @throws AcessoNegadoException         conta sem e-mail confirmado (403)
     * @throws CampoObrigatorioAusenteException campo obrigatório ausente no formulário (400)
     * @throws ValidacaoDeCampoException     campo de valor inválido (422)
     * @throws com.scipath.error.FormatoDeArquivoNaoAceitoException arquivo que não é PDF (415)
     * @throws com.scipath.error.ArquivoMuitoGrandeException        arquivo acima do limite (413)
     */
    public MaterialPublicadoResposta publicar(SessaoAutenticada sessao,
            PublicacaoDeMaterialRequisicao requisicao, MultipartFile arquivo) {
        exigirContaAtiva(sessao);
        MetadadosDaPublicacao metadados = validar(requisicao);

        // Passo 1 do RN008: receber e validar (E4.2). A recusa aqui não deixa rastro — nem linha,
        // nem arquivo — e por isso acontece fora do bloco que marca órfão.
        ArquivoRecebido recebido = recepcao.receber(arquivo);

        try {
            // Passo 2: mover para o storage definitivo sob a chave interna (E4.3).
            armazenamento.guardar(recebido.chaveInterna(), recebido.arquivoTemporario());
            // Passo 3: gravar o material na MESMA transação (RN008, ADR-0005 §2).
            return gravacao.gravar(sessao.perfilId(), metadados, recebido.id());
        } catch (RuntimeException falha) {
            // Daqui para baixo o arquivo existe registrado e nenhum material aponta para ele:
            // é órfão, e é isto que o deixa encontrável pelo coletor (E4.5).
            liberar(recebido, falha);
            throw falha;
        }
    }

    // ------------------------------------------------------------------ autorização

    /**
     * Publicar exige conta <b>{@code ATIVO}</b> — verificação de e-mail concluída (ADR-0003 (b)).
     *
     * <p>A checagem é "não é {@code ATIVO}" e não "é {@code PENDENTE_VERIFICACAO}" de propósito:
     * hoje só a conta pendente chega aqui com sessão (conta {@code SUSPENSO} não mantém sessão —
     * {@code SessaoService}), e escrever a condição pelo lado que falta mantém a regra correta se
     * um estado novo aparecer no {@code CHECK account_estado_valido}.
     *
     * <p>É a <b>primeira</b> coisa do caminho: quem ainda não confirmou o e-mail recebe 403 sem que
     * metadado algum seja validado e sem que nada seja gravado (o critério do card exige conferir
     * no banco que nenhum material nasceu).
     */
    private static void exigirContaAtiva(SessaoAutenticada sessao) {
        if (sessao.estadoConta() != EstadoConta.ATIVO) {
            throw new AcessoNegadoException(DETALHE_EMAIL_NAO_VERIFICADO);
        }
    }

    // ------------------------------------------------------------------ metadados

    /**
     * Valida os campos do RF012 e resolve a taxonomia em identificadores (RF018).
     *
     * <p>A ordem dentro daqui é a mesma em todo o resto do projeto: <b>presença</b> primeiro (o
     * campo que não veio é 400), depois <b>valor</b> (o campo que veio errado é 422). Sem separar as
     * duas, um formulário sem {@code titulo} sairia como 422 e o cliente trataria como erro de
     * campo preenchível — que é exatamente o erro que o contrato v0.5 separou em dois códigos.
     */
    private MetadadosDaPublicacao validar(PublicacaoDeMaterialRequisicao requisicao) {
        String titulo = exigirTexto(requisicao.titulo(), "titulo", TITULO_MAXIMO);
        String descricao = exigirTexto(requisicao.descricao(), "descricao", DESCRICAO_MAXIMA);
        exigirDeclaracaoDeDireito(requisicao.declaracaoDireito());
        exigirEstadoInicial(requisicao.estado());

        String categoriaSlug = exigirSlug(requisicao.categoriaSlug(), "categoriaSlug", FORMATO_SLUG,
                "deve ser um slug de categoria (minúsculas, dígitos e hífen), como computacao-e-tecnologia");
        String tipoSlug = exigirSlug(requisicao.tipoSlug(), "tipoSlug", FORMATO_SLUG,
                "deve ser um slug de tipo de material (minúsculas, dígitos e hífen), como nota-de-aula");
        String licencaSlug = exigirSlug(requisicao.licencaSlug(), "licencaSlug", FORMATO_SLUG_DE_LICENCA,
                "deve ser um slug de licença (minúsculas, dígitos, hífen e ponto), como cc-by-4.0");

        return new MetadadosDaPublicacao(titulo, descricao,
                exigirIdAtivo(repositorio.categoriaAtiva(categoriaSlug), "categoriaSlug",
                        "categoria inexistente ou inativa"),
                exigirIdAtivo(repositorio.tipoAtivo(tipoSlug), "tipoSlug",
                        "tipo de material inexistente ou inativo"),
                exigirIdAtivo(repositorio.licencaAtiva(licencaSlug), "licencaSlug",
                        "licença inexistente ou inativa"));
    }

    /**
     * Texto obrigatório, já normalizado nas pontas.
     *
     * <p>Ausente (<b>null</b>) é <b>400</b>: a parte não veio no formulário e o servidor não
     * conseguiu ler o pedido como veio. Presente e em branco é <b>422</b>: o campo veio com valor
     * inválido — e é o que o formulário precisa para apontar o campo. Espaço nas pontas é ruído do
     * campo, não conteúdo: o valor é gravado sem ele.
     */
    private static String exigirTexto(String valor, String campo, int tamanhoMaximo) {
        if (valor == null) {
            throw new CampoObrigatorioAusenteException(campo);
        }
        String normalizado = valor.strip();
        if (normalizado.isEmpty()) {
            throw new ValidacaoDeCampoException(campo, "não pode ficar em branco");
        }
        if (normalizado.length() > tamanhoMaximo) {
            throw new ValidacaoDeCampoException(campo,
                    "deve ter " + tamanhoMaximo + " caracteres ou menos");
        }
        return normalizado;
    }

    /**
     * {@code declaracaoDireito} é obrigatório e precisa ser {@code true} (RF012): é a confirmação
     * de que o autor tem o direito de compartilhar o material.
     *
     * <p><b>Ausente é o mesmo estado de {@code false}</b> — o direito não foi declarado — e o
     * contrato fixa <b>422</b> para os dois casos, com a mensagem do exemplo {@code
     * direitoNaoDeclarado} ("é preciso confirmar que você tem o direito de compartilhar o
     * material"). A parte existir ou não no formulário não muda o que falta ao pedido, e por isso
     * aqui não se aplica o 400 de campo ausente: o que está ausente é a <i>declaração</i>, e o campo
     * existe no schema.
     *
     * <p>Valor que não é booleano é 422 com o mesmo campo e o motivo do formato — aceitar "sim" ou
     * "1" em silêncio seria inventar um vocabulário que o contrato não tem.
     */
    private static void exigirDeclaracaoDeDireito(String declaracao) {
        if (declaracao == null || declaracao.isBlank()) {
            throw new ValidacaoDeCampoException("declaracaoDireito",
                    "é preciso confirmar que você tem o direito de compartilhar o material");
        }
        if (!"true".equalsIgnoreCase(declaracao.strip())) {
            if ("false".equalsIgnoreCase(declaracao.strip())) {
                throw new ValidacaoDeCampoException("declaracaoDireito",
                        "é preciso confirmar que você tem o direito de compartilhar o material");
            }
            throw new ValidacaoDeCampoException("declaracaoDireito", "deve ser true ou false");
        }
    }

    /**
     * O estado do material é decidido pelo servidor (RF019) e por um só lugar
     * ({@link EstadoPublicacao#INICIAL}).
     *
     * <p>Ausente — o caso normal, e o único previsto pelo schema {@code PublicacaoMaterial} — vale o
     * estado inicial. Presente e igual ao inicial é aceito: o cliente repetiu a decisão do servidor
     * e isso não muda nada. <b>Qualquer outro valor é recusado com 422</b> no campo {@code estado}:
     * um {@code EXCLUIDO}, um {@code RETIRADO} ou um {@code RASCUNHO} vindo do formulário não vira
     * estado — requisição arbitrária não define estado, e o material <b>nunca</b> nasce fora do
     * público por causa de um campo que o contrato não publica.
     */
    private static void exigirEstadoInicial(String estado) {
        if (estado == null || estado.isBlank()) {
            return;
        }
        if (!EstadoPublicacao.INICIAL.name().equals(estado.strip())) {
            throw new ValidacaoDeCampoException("estado",
                    "o estado do material é definido pelo servidor: a publicação nasce "
                            + EstadoPublicacao.INICIAL);
        }
    }

    /** Slug obrigatório, no formato do contrato — presença é 400, formato é 422. */
    private static String exigirSlug(String valor, String campo, String formato, String motivoDoFormato) {
        if (valor == null) {
            throw new CampoObrigatorioAusenteException(campo);
        }
        String normalizado = valor.strip();
        if (!normalizado.matches(formato)) {
            throw new ValidacaoDeCampoException(campo, motivoDoFormato);
        }
        return normalizado;
    }

    /**
     * O identificador interno do item de referência, ou <b>422</b> no campo (RF018).
     *
     * <p>Inexistente e inativo caem no mesmo erro, e de propósito: para quem publica, os dois
     * significam "esta escolha não está disponível" — e a leitura pública da taxonomia já mostra
     * apenas os itens ativos.
     */
    private static UUID exigirIdAtivo(Optional<UUID> id, String campo, String motivo) {
        return id.orElseThrow(() -> new ValidacaoDeCampoException(campo, motivo));
    }

    // ------------------------------------------------------------------ falha depois do movimento

    /**
     * Desfaz o que sobreviveu à falha: a área temporária e o arquivo sem material.
     *
     * <p>A marcação é feita <b>fora de qualquer transação</b> (este método roda depois de a
     * transação da gravação ter feito rollback, e o serviço não é transacional): é o efeito que
     * precisa sobreviver ao rollback. Deixar o registro como {@code VALIDADO} sem material seria
     * dizer que há um material publicado apontando para ele.
     *
     * <p>A ordem importa: apagar o resto temporário primeiro (depois de um movimento bem sucedido
     * não há o que apagar — {@code deleteIfExists} é no-op) e marcar o órfão em seguida. Uma falha
     * na própria marcação <b>não</b> esconde a original: ela é registrada no log com a chave, e a
     * exceção que sobe continua sendo a que interrompeu a publicação.
     */
    private void liberar(ArquivoRecebido recebido, RuntimeException falha) {
        apagarTemporario(recebido.arquivoTemporario());
        try {
            arquivos.marcarOrfao(recebido.chaveInterna());
        } catch (RuntimeException falhaAoMarcar) {
            log.error("não foi possível marcar como órfão o arquivo '{}' (o coletor ainda o encontra"
                    + " pela ausência de material)", recebido.chaveInterna(), falhaAoMarcar);
        }
        log.error("publicação de material interrompida depois de receber o arquivo '{}' (chave {}): {}",
                recebido.nomeOriginalSeguro(), recebido.chaveInterna(), falha.toString());
    }

    /**
     * Apaga o arquivo temporário, se ele ainda existir, sem mascarar a falha que trouxe o fluxo
     * até aqui — mesma decisão do recebimento (E4.2): a limpeza é melhor esforço, e perder a causa
     * original por causa dela seria pior. Depois de um movimento bem sucedido não há o que apagar:
     * o arquivo está no armazenamento definitivo, sob a chave interna.
     */
    private static void apagarTemporario(Path arquivo) {
        try {
            Files.deleteIfExists(arquivo);
        } catch (IOException falha) {
            log.warn("não foi possível apagar o arquivo temporário {}: {}", arquivo, falha.getMessage());
        }
    }
}
