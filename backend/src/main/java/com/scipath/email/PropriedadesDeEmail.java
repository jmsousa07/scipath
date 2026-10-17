package com.scipath.email;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuração do canal de e-mail: onde a implementação de desenvolvimento grava o outbox e
 * para onde apontam os links das mensagens (ADR-0003 (c), (d) e (f)).
 *
 * <p><b>Por que é configuração e não constante.</b> Três razões concretas:
 * <ul>
 *   <li>o outbox é uma pasta de trabalho — apontá-la para lugar nenhum deve ser ajuste de
 *       ambiente ({@code SCIPATH_OUTBOX_DIR}), não recompilação;</li>
 *   <li>a <b>página</b> do frontend que recebe o link não existe no contrato (o contrato diz
 *       que o link aponta para uma página do frontend, e a rota é das telas — E3.6/E3.7).
 *       Com o endereço em configuração, a tela não obriga a mexer no backend;</li>
 *   <li>o perfil de teste aponta o outbox para {@code backend/target/}, e a suíte lê o arquivo
 *       de lá — sem tocar em nada do desenvolvedor.</li>
 * </ul>
 *
 * <p><b>O token viaja no fragmento da URL ({@code #token=...}), e isso é decisão.</b> O
 * contrato proíbe o token no <b>caminho</b> da API porque caminho entra em log de acesso
 * (RNF009) — e o link do e-mail, se levasse o token na query string, cairia no log de acesso do
 * servidor do frontend. O fragmento é resolvido pelo navegador e <b>nunca é enviado ao
 * servidor</b>: não entra em log, não entra em {@code Referer}, não vaza por histórico de proxy.
 * A página lê {@code location.hash} e manda o token no <b>corpo</b> da confirmação, como o
 * contrato exige.
 *
 * <p>O marcador {@code {token}} é obrigatório nos dois endereços: template sem ele geraria um
 * link que ninguém consegue confirmar — falha que precisa estourar na subida, não na primeira
 * conta criada.
 */
@Component
@ConfigurationProperties(prefix = "scipath.email")
public class PropriedadesDeEmail {

    private Outbox outbox = new Outbox();
    private Link link = new Link();

    public Outbox getOutbox() {
        return outbox;
    }

    public void setOutbox(Outbox outbox) {
        this.outbox = outbox == null ? new Outbox() : outbox;
    }

    public Link getLink() {
        return link;
    }

    public void setLink(Link link) {
        this.link = link == null ? new Link() : link;
    }

    /** Onde a implementação de desenvolvimento grava as mensagens que deveriam sair. */
    public static class Outbox {

        /** Pasta do outbox. Padrão em {@code application.yml} — fora do repositório. */
        private String diretorio = System.getProperty("user.home") + "/.scipath/outbox";

        public String getDiretorio() {
            return diretorio;
        }

        public void setDiretorio(String diretorio) {
            if (diretorio == null || diretorio.isBlank()) {
                throw new IllegalArgumentException("scipath.email.outbox.diretorio não pode ficar vazio");
            }
            this.diretorio = diretorio;
        }
    }

    /** Endereços das páginas do frontend que recebem o token no fragmento da URL. */
    public static class Link {

        private String confirmacaoEmail;
        private String recuperacaoSenha;

        public String getConfirmacaoEmail() {
            return confirmacaoEmail;
        }

        public void setConfirmacaoEmail(String confirmacaoEmail) {
            this.confirmacaoEmail = exigirMarcador(confirmacaoEmail, "link.confirmacao-email");
        }

        public String getRecuperacaoSenha() {
            return recuperacaoSenha;
        }

        public void setRecuperacaoSenha(String recuperacaoSenha) {
            this.recuperacaoSenha = exigirMarcador(recuperacaoSenha, "link.recuperacao-senha");
        }

        /**
         * Endereço final do link, com o token no fragmento.
         *
         * <p>A troca é feita sobre um template que <b>tem</b> de conter {@code {token}} — um
         * literal, não expressão de linguagem nenhuma: o token nunca entra na string do SQL, em
         * {@code SpEL} ou em expressão de log.
         */
        public String montar(String endereco, String token) {
            return endereco.replace(MARCADOR, token);
        }

        private static String exigirMarcador(String endereco, String chave) {
            if (endereco == null || !endereco.contains(MARCADOR)) {
                throw new IllegalArgumentException("scipath.email." + chave
                        + " deve apontar para uma página do frontend e conter o marcador " + MARCADOR
                        + " — é ele que vira o token no fragmento da URL");
            }
            return endereco;
        }
    }

    /** Marca, no template configurado, o ponto em que o token entra. */
    static final String MARCADOR = "{token}";
}
