package com.scipath.arquivo;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Remove os binários de materiais excluídos após a janela configurada, mantendo os metadados. */
@Service
public class ExpurgoDeRetencaoService {

    private static final Logger log = LoggerFactory.getLogger(ExpurgoDeRetencaoService.class);

    private final ExpurgoDeRetencaoRepositorio repositorio;
    private final StorageService armazenamento;
    private final PropriedadesDeExpurgo propriedades;

    public ExpurgoDeRetencaoService(ExpurgoDeRetencaoRepositorio repositorio, StorageService armazenamento,
            PropriedadesDeExpurgo propriedades) {
        this.repositorio = repositorio;
        this.armazenamento = armazenamento;
        this.propriedades = propriedades;
    }

    /** Uma passada: falha em um arquivo é contada e não interrompe os demais candidatos. */
    public RelatorioDeExpurgo expurgar() {
        UUID execucaoId = UUID.randomUUID();
        Duration janela = propriedades.getJanela();
        List<ExpurgoDeRetencaoCandidato> candidatos;
        try {
            candidatos = repositorio.candidatos(janela);
        } catch (RuntimeException falha) {
            RelatorioDeExpurgo relatorio = new RelatorioDeExpurgo(execucaoId, propriedades.janelaFormatada(),
                    0, 0, 1, List.of(), List.of("varredura motivo=" + motivoDe(falha)));
            registrar(relatorio);
            throw falha;
        }

        List<RelatorioDeExpurgo.ItemPurgado> purgados = new ArrayList<>();
        List<String> falhas = new ArrayList<>();
        for (ExpurgoDeRetencaoCandidato candidato : candidatos) {
            try {
                // Não passar pelo RemocaoDeArquivoService: seu guarda recusa todo arquivo que ainda
                // tenha material associado, inclusive EXCLUIDO. Este serviço só recebe EXCLUIDO fora
                // da retenção; o coletor de órfãos exige NOT EXISTS(material), portanto os conjuntos
                // de arquivos que as duas rotinas removem são disjuntos.
                boolean binarioRemovido = armazenamento.remover(candidato.chaveInterna());
                int atualizados = repositorio.marcarPurgado(candidato.id(), janela);
                if (atualizados != 1) {
                    throw new IllegalStateException("o file_asset deixou de ser elegível durante o expurgo");
                }
                purgados.add(new RelatorioDeExpurgo.ItemPurgado(candidato.chaveInterna(),
                        candidato.nomeOriginalSeguro(), candidato.tamanhoBytes(), binarioRemovido));
            } catch (RuntimeException falha) {
                falhas.add("chave=" + candidato.chaveInterna() + " motivo=" + motivoDe(falha));
            }
        }

        RelatorioDeExpurgo relatorio = new RelatorioDeExpurgo(execucaoId, propriedades.janelaFormatada(),
                candidatos.size(), purgados.size(), falhas.size(), List.copyOf(purgados), List.copyOf(falhas));
        registrar(relatorio);
        return relatorio;
    }

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

    private static void registrar(RelatorioDeExpurgo relatorio) {
        relatorio.itensPurgados().forEach(item -> log.info("{}", relatorio.linhaDe(item)));
        relatorio.motivosDeFalha().forEach(motivo -> log.warn("{}", relatorio.linhaDeFalha(motivo)));
        log.info("{}", relatorio.resumo());
    }
}
