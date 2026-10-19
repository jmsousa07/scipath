package com.scipath.arquivo;

import com.scipath.arquivo.RelatorioDeColeta.ItemRemovido;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Coletor de arquivos órfãos (card E4.5, RN008, RN012, ADR-0005 §2 e §3).
 *
 * <p><b>O problema que ele fecha.</b> O RN008 admite que a transação de publicação falhe: o arquivo
 * já movido para o armazenamento fica sem material vivo. O E4.4 marca esse arquivo como
 * {@code ORFAO} e a publicação nunca o serve, mas sem coleta ele vira lixo permanente e invisível —
 * disco ocupado por um binário que nada alcança. Esta classe é a rotina que o remove.
 *
 * <p><b>A ordem das duas remoções é o coração do card, e ela é binário → registro.</b> A razão é o
 * que sobra se o processo morrer no meio:
 *
 * <ul>
 *   <li><b>binário primeiro</b> — se a execução for interrompida depois da remoção do arquivo, a
 *       linha de {@code file_asset} continua lá, ainda sem material, e a execução seguinte a
 *       encontra e termina o serviço. O estado intermediário é <i>visível</i>: o banco descreve um
 *       arquivo que o disco já não tem, e é exatamente isso que faz a retomada funcionar;</li>
 *   <li><b>registro primeiro seria irreversível</b> — apagar a linha e morrer antes de apagar o
 *       binário deixaria um arquivo em disco que <i>nenhum</i> registro conhece: nem o coletor o
 *       encontraria (ele parte do banco), nem ninguém saberia que ele existe. Lixo permanente é
 *       precisamente o que o RN012 proíbe.</li>
 * </ul>
 *
 * <p><b>Nunca apaga arquivo de material vivo.</b> A regra não é reimplementada aqui: quem a aplica é
 * {@link RemocaoDeArquivoService}, que consulta o vínculo antes de tocar no armazenamento, e ela
 * vale para <b>qualquer</b> estado de publicação — {@code PUBLICADO}, {@code RETIRADO} (republicável)
 * e {@code EXCLUIDO} dentro da janela de retenção. A consulta de candidatos já exclui quem tem
 * material; o guarda é a segunda barreira, para o caso de um material aparecer entre a leitura e a
 * remoção. E o {@code DELETE} do registro repete a condição pela terceira vez
 * ({@link ColetaDeOrfaosRepositorio}) — três camadas para uma regra que, se falhar uma vez, destrói
 * o arquivo de alguém.
 *
 * <p><b>Idempotente.</b> Rodar duas vezes seguidas não muda o resultado da segunda: o que a primeira
 * removeu não existe mais, então a segunda examina zero candidatos, remove zero e devolve zero
 * falhas. Isso não é sorte do acaso — é o efeito de a remoção ser por chave e por linha, e de o
 * armazenamento responder {@code false} (não erro) quando não há o que remover (RN012).
 *
 * <p><b>Falha em um arquivo não derruba a varredura.</b> Cada candidato é tratado isoladamente: uma
 * exceção — vínculo que apareceu no meio, disco que recusou, registro que sumiu — é contada como
 * falha, registrada no log com a chave e o motivo, e a varredura segue para o próximo. A execução
 * seguinte tenta de novo, porque a linha continua lá, sem material. É o que o card pede por
 * "interrupção durante a remoção não deixa o sistema em estado pior — a execução seguinte termina o
 * serviço".
 *
 * <p><b>Sem transação, de propósito.</b> Não há {@code @Transactional} nesta classe, e a ausência é
 * uma decisão: a remoção do binário não é transacional — um arquivo apagado do disco não volta com
 * rollback —, então uma transação que abrangesse a varredura inteira daria uma garantia falsa (o
 * banco desfaria o que o disco já não pode desfazer) e ainda manteria locks abertos durante toda a
 * passagem. Cada arquivo é uma unidade de trabalho independente, e o que sobrevive à falha é
 * exatamente o que a próxima execução precisa encontrar.
 *
 * <p><b>Observável</b> (§RN012). Cada execução tem identificador próprio, e o
 * {@link RelatorioDeColeta} devolvido é o mesmo objeto que o log escreve: o resumo com as contagens
 * de examinados, removidos e falhas, uma linha por arquivo removido (com a chave, o nome original e
 * o tamanho) e uma linha por falha (com o motivo). Nada disso exige abrir o banco ou chamar um
 * endpoint — {@code grep '\[coleta-orfaos\]'} no log é a inspeção completa.
 */
@Service
public class ColetorDeOrfaosService {

    private static final Logger log = LoggerFactory.getLogger(ColetorDeOrfaosService.class);

    private final ColetaDeOrfaosRepositorio repositorio;
    private final RemocaoDeArquivoService remocao;
    private final PropriedadesDeColeta propriedades;

    public ColetorDeOrfaosService(ColetaDeOrfaosRepositorio repositorio, RemocaoDeArquivoService remocao,
            PropriedadesDeColeta propriedades) {
        this.repositorio = repositorio;
        this.remocao = remocao;
        this.propriedades = propriedades;
    }

    /**
     * Executa uma varredura completa: encontra os órfãos mais antigos que a janela de segurança e
     * remove, de cada um, o binário e o registro — nesta ordem.
     *
     * <p>Não lança por falha de um arquivo (o motivo vai para o relatório e para o log); só uma
     * falha da própria varredura — não conseguir ler os candidatos — interrompe, porque nesse caso
     * não houve varredura nenhuma para relatar.
     *
     * @return o relatório desta execução: identificador, contagens e o detalhe do que saiu
     */
    public RelatorioDeColeta coletar() {
        UUID execucaoId = UUID.randomUUID();
        Duration janela = propriedades.getJanela();

        List<OrfaoCandidato> candidatos = repositorio.candidatos(janela);
        List<ItemRemovido> removidos = new ArrayList<>();
        List<String> falhas = new ArrayList<>();

        for (OrfaoCandidato candidato : candidatos) {
            try {
                boolean binarioRemovido = remover(candidato);
                removidos.add(new ItemRemovido(candidato.chaveInterna(), candidato.nomeOriginalSeguro(),
                        candidato.tamanhoBytes(), binarioRemovido));
            } catch (RuntimeException falha) {
                falhas.add("chave=" + candidato.chaveInterna() + " motivo=" + motivoDe(falha));
            }
        }

        // O relatório é montado com o que ACONTECEU (as listas acima), não com o que se pretendia
        // fazer: as contagens são o tamanho das listas, e não podem divergir do detalhe.
        RelatorioDeColeta relatorio = new RelatorioDeColeta(execucaoId, janela, candidatos.size(),
                removidos.size(), falhas.size(), List.copyOf(removidos), List.copyOf(falhas));
        registrar(relatorio);
        return relatorio;
    }

    /**
     * Remove um órfão: o binário e, só então, o registro.
     *
     * <p><b>O binário que já não está lá não é falha.</b> {@code RemocaoDeArquivoService.remover}
     * devolve {@code false} quando não há arquivo sob a chave, e é esse retorno que torna a retomada
     * possível: uma execução anterior interrompida entre as duas remoções deixa a linha sem o
     * binário, e esta execução conclui o serviço em vez de travar no que já foi feito.
     *
     * <p><b>Zero linhas removidas é falha</b>, e vale dizer por quê: significa que a linha deixou de
     * ser um órfão entre a leitura e a escrita (ganhou material ou virou {@code PURGADO}). Nesse
     * caso o binário já saiu do disco e o registro ficou — o desfecho <i>não</i> é silencioso, ele é
     * contado e registrado, para que a próxima varredura e quem opera saibam que há uma linha para
     * investigar. Nenhum caminho do produto liga um material a um {@code file_asset} antigo (a
     * publicação sempre grava o material apontando para a linha que acabou de receber), então este
     * desfecho é um sinal de defeito, não um estado normal — e por isso ele aparece.
     *
     * @throws RuntimeException qualquer falha da remoção; quem chama conta e segue
     */
    private boolean remover(OrfaoCandidato candidato) {
        boolean binarioRemovido = remocao.remover(candidato.chaveInterna());
        int linhas = repositorio.removerRegistro(candidato.id());
        if (linhas != 1) {
            throw new IllegalStateException("o registro do arquivo não pôde ser removido: a linha"
                    + " deixou de ser órfã entre a leitura e a remoção (material apontando para ela"
                    + " ou estado PURGADO) — o binário já foi removido do armazenamento");
        }
        // Quando o binário já não estava lá, o registro sai do mesmo jeito e o relatório marca o
        // item com `binario=ausente` — a retomada de uma execução interrompida fica visível no log
        // sem virar uma segunda linha de mensagem.
        return binarioRemovido;
    }

    /**
     * O motivo de uma falha, em uma linha.
     *
     * <p><b>A causa raiz, não a embrulhada.</b> Uma falha do banco chega aqui dentro de um
     * {@code DataAccessException} cuja mensagem carrega a instrução SQL inteira — três linhas de
     * {@code DELETE} no meio do resumo da coleta. O que quem lê o log precisa é do motivo real
     * ("falha forçada no DELETE", "chave duplicada"), e não do texto do SQL que a aplicação já
     * conhece: a causa mais profunda é a que diz o que deu errado. Espaços em branco viram um
     * espaço só, para a linha continuar sendo uma linha — o formato que o
     * {@code grep '\[coleta-orfaos\]'} do card pressupõe.
     */
    private static String motivoDe(RuntimeException falha) {
        Throwable causa = falha;
        while (causa.getCause() != null && causa.getCause() != causa) {
            causa = causa.getCause();
        }
        String mensagem = causa.getMessage() == null || causa.getMessage().isBlank()
                ? "(sem mensagem)"
                : causa.getMessage().replaceAll("\\s+", " ").trim();
        return causa.getClass().getSimpleName() + ": " + mensagem;
    }

    /**
     * Escreve no log o que o relatório diz — as mesmas linhas, na mesma ordem, com o resumo por
     * último. O relatório é a fonte, e não uma segunda montagem de mensagem: assim o que o teste
     * mede e o que o operador lê não podem divergir.
     */
    private static void registrar(RelatorioDeColeta relatorio) {
        relatorio.itensRemovidos().forEach(item -> log.info("{}", relatorio.linhaDe(item)));
        relatorio.motivosDeFalha().forEach(motivo -> log.warn("{}", relatorio.linhaDeFalha(motivo)));
        log.info("{}", relatorio.resumo());
    }
}
