package com.scipath.arquivo;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * Implementação <b>local</b> do {@link StorageService} — disco da própria máquina, atrás do
 * diretório configurado (ADR-0005 §2, "implementação local agora").
 *
 * <p><b>O caminho é a chave dentro do diretório, e nada mais.</b> {@code <diretorio>/<chave>}: um
 * segmento, decidido inteiramente pela chave gerada pelo sistema. Nenhum componente vem do nome
 * enviado pelo usuário (ele nem é parâmetro desta classe), e a chave é conferida antes de virar
 * caminho — chave com separador, com {@code ..} ou vazia é recusada, para que nem um defeito de
 * quem chama tenha como escapar do diretório.
 *
 * <p><b>Sem extensão e sem subdiretório.</b> O arquivo guardado não tem nome legível (é a chave, um
 * identificador opaco) e não é organizado em subárvores: o MVP não tem requisito de volume que
 * justifique um esquema de distribuição, e um esquema inventado agora seria configuração a mais
 * para manter e para o coletor de órfãos (E4.5) atravessar. O nome legível existe em
 * {@code file_asset.nome_original_seguro}, que é metadado — não endereço.
 *
 * <p><b>{@code Files.move}</b> porque a operação é um movimento: a área temporária de recebimento
 * fica vazia quando o arquivo é promovido (RN012, "nenhuma cópia temporária sobrevive à
 * operação"). O diretório é criado sob demanda — o primeiro arquivo publicado cria a árvore.
 *
 * <p>Ao trocar por MinIO/S3 (ADR-0007), esta classe é a única que muda: {@code guardar} vira um
 * {@code putObject} com a chave como object key, {@code localizar} vira uma referência ao objeto e
 * {@code remover}, um {@code deleteObject}. Nenhum serviço do domínio é tocado.
 */
@Component
public class ArmazenamentoLocal implements StorageService {

    private final PropriedadesDeArmazenamento propriedades;

    public ArmazenamentoLocal(PropriedadesDeArmazenamento propriedades) {
        this.propriedades = propriedades;
    }

    @Override
    public void guardar(String chaveInterna, Path origem) {
        Path destino = caminhoDe(chaveInterna);
        try {
            Files.createDirectories(destino.getParent());
            if (Files.exists(destino)) {
                // Checagem explícita além do Files.move: em sistema de arquivos POSIX o rename(2)
                // sobrescreve em silêncio, e sobrescrever aqui apagaria o arquivo de outro material.
                throw new ArquivoJaArmazenadoException("a chave " + chaveInterna
                        + " já tem arquivo no armazenamento: " + destino);
            }
            Files.move(origem, destino);
        } catch (IOException falha) {
            throw new UncheckedIOException(
                    "falha ao mover o arquivo para o armazenamento definitivo sob a chave " + chaveInterna, falha);
        }
    }

    @Override
    public Optional<Resource> localizar(String chaveInterna) {
        Path arquivo = caminhoDe(chaveInterna);
        // isRegularFile, e não exists: um diretório no lugar do arquivo não é conteúdo a servir.
        return Files.isRegularFile(arquivo) ? Optional.of(new FileSystemResource(arquivo)) : Optional.empty();
    }

    @Override
    public boolean remover(String chaveInterna) {
        try {
            return Files.deleteIfExists(caminhoDe(chaveInterna));
        } catch (IOException falha) {
            throw new UncheckedIOException(
                    "falha ao remover o arquivo armazenado sob a chave " + chaveInterna, falha);
        }
    }

    /**
     * Caminho do arquivo sob a chave — <b>a chave decide o caminho</b>, e ela é conferida antes de
     * ser usada como nome de arquivo.
     *
     * <p>A chave vem do sistema e hoje é um UUID; a conferência é estrutural (um só segmento, sem
     * {@code ..}, sem controle) e não de formato, porque impor "UUID" aqui seria fixar na interface
     * uma decisão que é do adaptador — o dia do object key do S3 tem outra forma.
     *
     * @throws IllegalArgumentException chave vazia, com separador de caminho, com {@code ..} ou
     *                                  com caractere de controle
     */
    Path caminhoDe(String chaveInterna) {
        return propriedades.diretorio().resolve(chaveValida(chaveInterna));
    }

    /** A chave tem de ser um único segmento de nome de arquivo — nada que escape do diretório. */
    static String chaveValida(String chaveInterna) {
        if (chaveInterna == null || chaveInterna.isBlank()) {
            throw new IllegalArgumentException("a chave interna do arquivo não pode ficar vazia");
        }
        boolean invalida = chaveInterna.contains("/")
                || chaveInterna.contains("\\")
                || chaveInterna.contains("..")
                || chaveInterna.equals(".")
                || chaveInterna.chars().anyMatch(Character::isISOControl);
        if (invalida) {
            throw new IllegalArgumentException(
                    "chave interna inválida para o armazenamento (ela nomeia um arquivo, não um caminho): "
                            + chaveInterna);
        }
        return chaveInterna;
    }
}
