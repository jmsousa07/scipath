package com.scipath.email;

/**
 * Porta de saída do canal de e-mail — a fronteira entre a regra de identidade e o <b>meio</b>
 * pelo qual o link sai (ADR-0003 (f)). O mesmo padrão que o {@code StorageService} terá para
 * arquivo: o domínio depende de uma interface, nunca de um cliente SMTP.
 *
 * <p><b>Por que a interface existe agora, e não quando o SMTP chegar.</b> O ADR-0003 (f) fixa
 * esta interface no domínio <i>junto</i> com a implementação local de desenvolvimento, e o
 * motivo é verificável: sem ela, o token de verificação só poderia ser exercitado com um
 * servidor de e-mail no ar — e não há nenhum nesta máquina (nem Docker para um mail-catcher).
 * Com ela, a implementação de desenvolvimento grava a mensagem em <b>outbox</b> e o fluxo
 * inteiro fica verificável de ponta a ponta; em produção, o provedor SMTP definido no
 * ADR-0006/0007 é outra implementação da <b>mesma</b> interface, sem tocar em nenhum serviço
 * do domínio.
 *
 * <p><b>Uma mensagem, um envio.</b> A chamada é síncrona de propósito: o contrato v0.4 publica
 * <b>500</b> para falha de envio no reenvio de verificação e o ADR-0003 (g) exige que, falhando,
 * nada seja marcado como enviado. É o retorno da chamada que dá essa garantia — em um envio
 * assíncrono, "falhou" chegaria depois de a resposta já ter afirmado 202.
 *
 * <p><b>Nenhum token em log</b> (RNF009): a implementação só pode gravar a mensagem no canal
 * (outbox, SMTP); o valor que viaja não é registrado, nem no sucesso nem na falha.
 */
public interface EmailSender {

    /**
     * Entrega a mensagem pelo canal.
     *
     * @throws FalhaDeEnvioDeEmailException o canal recusou a mensagem — nada foi entregue
     */
    void enviar(MensagemDeEmail mensagem);
}
