package com.scipath.material;

import com.scipath.arquivo.DisponibilidadeDeArquivo;
import com.scipath.error.NaoEncontradoException;
import com.scipath.taxonomia.Pagina;
import com.scipath.taxonomia.ResultadoPaginado;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regra de leitura dos materiais do próprio dono (RF015, contrato v0.6, tag {@code gerenciamento}):
 * a lista de {@code GET /api/perfil/atual/materiais} e o detalhe de
 * {@code GET /api/perfil/atual/materiais/{slug}}.
 *
 * <p><b>A propriedade vem da sessão</b> (RN001, CA007): os dois métodos recebem o {@code perfilId}
 * que o interceptor resolveu da cookie, e não existe parâmetro de identificador vindo do cliente —
 * não há caminho em que uma requisição escolha a linha lida. A assinatura é a garantia.
 *
 * <p><b>Autorização no servidor, não na tela</b> (RNF010, RN013): o 404 de material de outra conta
 * sai <b>daqui</b>, e é o mesmo de material inexistente — esconder o botão não protege nada. A
 * decisão de desenho está no ADR-0011 §3: um 403 confirmaria a existência de material
 * {@code RETIRADO}/{@code EXCLUIDO} alheio e faria do status um oráculo de enumeração.
 *
 * <p><b>Nenhum estado é filtrado.</b> A visão do dono traz {@code PUBLICADO}, {@code RETIRADO} e
 * {@code EXCLUIDO} (RF015, ADR-0011 §2), e a leitura de um material próprio vale nos três — é dela
 * que a edição de um {@code RETIRADO} se pré-preenche, já que o detalhe público responde 404 para
 * ele (RN002).
 *
 * <p><b>O serviço existe pela mesma razão do {@link MateriaisService}</b>: o envelope
 * ({@link Pagina}), o 404 e o {@code arquivo.disponivel} são decisão de negócio, não de transporte —
 * o controller não monta {@code items/page/size/total}, não escolhe a exceção do não encontrado e não
 * pergunta ao armazenamento se o binário pode ser servido.
 */
@Service
public class MateriaisPropriosService {

    private final MateriaisPropriosRepositorio repositorio;
    private final DisponibilidadeDeArquivo disponibilidade;
    private final ValidadorDeMetadadosDeMaterial validador;

    public MateriaisPropriosService(MateriaisPropriosRepositorio repositorio,
            DisponibilidadeDeArquivo disponibilidade, ValidadorDeMetadadosDeMaterial validador) {
        this.repositorio = repositorio;
        this.disponibilidade = disponibilidade;
        this.validador = validador;
    }

    /**
     * Página dos materiais do perfil da sessão, na ordem {@code atualizadoEm} DESC com desempate por
     * {@code id} ASC (contrato v0.6, ADR-0011 §2).
     *
     * <p>Perfil sem material é <b>200 com {@code items: []}</b>: é o estado normal de quem ainda não
     * publicou, não um caso de erro (RNF004). O envelope é montado aqui, e não no repositório, pelo
     * mesmo motivo de sempre: um segundo ponto que montasse {@code items/page/size/total} poderia
     * contar diferente do que devolve.
     *
     * @param perfilId {@code profile_id} da sessão (RN001)
     * @param pagina   página pedida, começando em 0
     * @param tamanho  itens por página, já limitado pelo teto do contrato (o controller recusa acima
     *                 de 100 com 400 em vez de truncar — RNF021)
     */
    public Pagina<MaterialProprioResposta> listar(UUID perfilId, int pagina, int tamanho) {
        ResultadoPaginado<MaterialDoDono> resultado = repositorio.listarDoPerfil(perfilId, pagina, tamanho);
        List<MaterialProprioResposta> itens = resultado.itens().stream().map(this::resposta).toList();
        return new Pagina<>(itens, pagina, tamanho, resultado.total());
    }

    /**
     * O material do dono identificado pelo {@code slug}, em qualquer dos três estados.
     *
     * <p><b>O 404 é indistinguível de propósito</b> (ADR-0011 §3, RN002, CA007): material de outra
     * conta e material inexistente chegam aqui como o mesmo vazio do repositório e saem como o mesmo
     * erro — o detalhe é {@code null} porque o texto vai do padrão único de
     * {@link com.scipath.error.ApiExceptionHandler} ("O recurso solicitado não foi encontrado.", o
     * mesmo do 404 público). Duas mensagens diferentes para "não é seu" e "não existe" seriam
     * exatamente a distinção que a regra proíbe.
     *
     * @param perfilId {@code profile_id} da sessão (RN001)
     * @param slug     slug já validado no controller (formato e tamanho do contrato)
     */
    public MaterialProprioResposta ler(UUID perfilId, String slug) {
        return resposta(repositorio.buscarDoPerfil(perfilId, slug)
                .orElseThrow(() -> new NaoEncontradoException(null)));
    }

    /**
     * Edita os metadados editáveis de um material próprio (RF016, contrato v0.6, tag {@code
     * gerenciamento}) e devolve o material <b>como ele ficou no banco</b>.
     *
     * <p><b>A ordem do caminho, e por que ela é esta.</b> Validação primeiro, escrita depois. A
     * validação (presença 400, valor 422, taxonomia resolvida em identificadores) não tem efeito
     * colateral — uma recusa aqui não custa escrita nenhuma —, e é ela que precisa vir antes: se a
     * escrita viesse primeiro, um {@code categoriaSlug} inexistente teria de ser detectado
     * <i>depois</i> de a linha já ter mudado, e o material ficaria alterado por um pedido recusado.
     *
     * <p><b>A propriedade e o estado vivo são decididos na própria escrita</b>
     * ({@link MateriaisPropriosRepositorio#atualizarMetadados}): o {@code WHERE} carrega a posse
     * (RN001) e a lista de estados em que as ações do dono valem (ADR-0011 §2), e o {@code RETURNING}
     * devolve a linha que a instrução escreveu — o único lugar que decide a escrita é também o que
     * monta o corpo de 200. Quando a escrita não alcança linha nenhuma — slug inexistente, material
     * de outra conta ou {@code EXCLUIDO} — o resultado é <b>o mesmo 404 do material inexistente</b>,
     * byte a byte (ADR-0011 §3, CA007): os três casos caem no mesmo caminho porque <b>não há
     * informação diferente para vazar</b>. Um 403 para "não é seu" confirmaria a existência de
     * material {@code RETIRADO}/{@code EXCLUIDO} alheio e faria do código de status um oráculo de
     * enumeração.
     *
     * <p><b>O que a resposta não muda:</b> {@code slug}, a propriedade, o estado, o arquivo e
     * {@code publicadoEm} continuam os que eram (ADR-0011 §4); {@code atualizadoEm} sobe com a
     * escrita. Editar <b>não</b> exige conta {@code ATIVO} (ADR-0003 (b)): manter no público o que
     * já está lá não é publicar — é por isso que este método não consulta o estado da conta.
     *
     * <p><b>A transação cobre a validação e a escrita</b> ({@code @Transactional}): as consultas de
     * taxonomia que resolvem {@code categoriaSlug}, {@code tipoSlug} e {@code licencaSlug} e o
     * {@code UPDATE} veem o mesmo estado do banco — nada que a validação aceitou pode ter mudado
     * antes de a escrita acontecer. A anotação vale porque este método é chamado de <b>fora</b> do
     * bean (o controller): um método anotado chamado de dentro da própria classe não abriria
     * transação nenhuma (a mesma armadilha de proxy documentada em {@code GravacaoDeMaterialService}).
     *
     * @param perfilId  {@code profile_id} da sessão (RN001) — a única origem possível da propriedade
     * @param slug      slug já validado no controller (formato e tamanho do contrato)
     * @param requisicao os cinco campos do schema {@code EdicaoDeMaterial}, <b>crus</b>: quem decide
     *                  presença (400), valor (422) e taxonomia (422) é
     *                  {@link ValidadorDeMetadadosDeMaterial}, a mesma regra da publicação (RF018)
     * @throws NaoEncontradoException material inexistente, de outra conta ou {@code EXCLUIDO} (404)
     */
    @Transactional
    public MaterialProprioResposta editar(UUID perfilId, String slug,
            EdicaoDeMaterialRequisicao requisicao) {
        MetadadosDaPublicacao metadados = validador.validar(requisicao.titulo(), requisicao.descricao(),
                requisicao.categoriaSlug(), requisicao.tipoSlug(), requisicao.licencaSlug());

        return resposta(repositorio.atualizarMetadados(perfilId, slug, metadados)
                .orElseThrow(() -> new NaoEncontradoException(null)));
    }

    /**
     * Monta o schema do contrato a partir da linha. O {@code arquivo.disponivel} é perguntado uma vez
     * por material, a {@link DisponibilidadeDeArquivo} — a <b>mesma</b> condição que o download
     * aplica (registro {@code VALIDADO} e binário no armazenamento).
     *
     * <p><b>O arquivo entra sempre, e o motivo de um indisponível nunca</b> (RF014, RNF014). Um
     * material {@code EXCLUIDO} já purgado (ADR-0005 §3) continua sendo lido e listado — o dono não
     * perde o registro — e o que muda é {@code disponivel: false}, que é informação para a tela não
     * oferecer um link que responderia 404. O nome e o tamanho permanecem: são registro do
     * recebimento.
     */
    private MaterialProprioResposta resposta(MaterialDoDono linha) {
        ArquivoDoMaterial arquivo = linha.arquivo();
        return new MaterialProprioResposta(
                linha.slug(),
                linha.titulo(),
                linha.descricao(),
                linha.categoria(),
                linha.tipo(),
                linha.licenca(),
                new ArquivoMaterialResposta(
                        arquivo.nomeOriginalSeguro(),
                        arquivo.tamanhoBytes(),
                        disponibilidade.podeServir(arquivo.estadoProcessamento(), arquivo.chaveInterna())),
                linha.estadoPublicacao(),
                linha.publicadoEm(),
                linha.atualizadoEm());
    }
}
