package com.scipath.conta;

import com.scipath.autor.PerfilPublico;
import com.scipath.error.ConflitoDeUnicidadeException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cadastro (RF008, RN009, CA004) — a regra de negócio que o card E3.2 entrega.
 *
 * <p><b>Uma transação, dois registros.</b> Conta e perfil nascem juntos
 * ({@code @Transactional}): um cadastro que gravasse a conta e falhasse no perfil deixaria
 * uma identidade sem identidade pública — o cliente receberia erro e a linha ficaria no
 * banco. É o critério 5 do card, e por isso não há escrita antes da última validação.
 *
 * <p><b>A ordem das checagens.</b> A validação de forma é do Bean Validation, antes do
 * serviço (422 no {@code ProblemDetail} padrão). Aqui sobra o que o banco também sabe, mas
 * que o formulário precisa ouvir antes: e-mail já cadastrado vira <b>409</b> com o campo
 * culpado (critério 2). A consulta <b>não</b> substitui a {@code UNIQUE account_email_unico}
 * — se dois cadastros idênticos correrem juntos, o banco recusa o segundo e o
 * {@code DuplicateKeyException} sai como o mesmo 409
 * ({@link com.scipath.error.ApiExceptionHandler}). O e-mail chega já normalizado pelo DTO
 * ({@link CadastroContaRequisicao}).
 *
 * <p><b>A senha.</b> Vira hash bcrypt custo 12 aqui (ADR-0003 (a)) e o texto puro morre com
 * a requisição: não é gravado, não é registrado em log e não entra em nenhum DTO de resposta
 * (RNF009). O custo 12 vem do {@link PasswordEncoder} injetado — a
 * {@link com.scipath.config.ConfiguracaoDeSenha} é o único lugar que o escolhe.
 *
 * <p><b>O slug.</b> Derivado do nome público ({@link GeradorDeSlug}) e garantido único
 * contra a tabela ({@link PerfilRepositorio#slugsEmUsoComPrefixo}): a URL canônica do perfil
 * não pode apontar para duas pessoas (RN009).
 *
 * <p><b>A resposta sai do banco, não da entrada.</b> Depois de gravar, conta e perfil são
 * <b>reliídos</b> e é o que o SQL devolveu que vai para o cliente — assim o 201 não pode
 * afirmar um estado que a tabela não tem (é a mesma leitura que o teste de integração faz,
 * do outro lado da fronteira).
 */
@Service
public class CadastroService {

    /** Limite de tentativas de sufixo de slug — defensivo; colidir 1000 vezes não é estado real. */
    private static final int TENTATIVAS_DE_SUFIXO = 1000;

    private final ContaRepositorio contas;
    private final PerfilRepositorio perfis;
    private final PasswordEncoder codificador;

    public CadastroService(ContaRepositorio contas, PerfilRepositorio perfis, PasswordEncoder codificador) {
        this.contas = contas;
        this.perfis = perfis;
        this.codificador = codificador;
    }

    /**
     * Cria a conta e o perfil inicial e devolve o estado que o contrato publica no 201, junto
     * do {@code id} da conta criada (de que a abertura da sessão precisa — o cadastro já sai
     * autenticado, contrato v0.4).
     *
     * @param requisicao corpo do cadastro já validado e com o e-mail normalizado
     * @throws ConflitoDeUnicidadeException e-mail já cadastrado (409)
     */
    @Transactional
    public ResultadoDeCadastro cadastrar(CadastroContaRequisicao requisicao) {
        String email = requisicao.email();

        if (contas.buscarPorEmail(email).isPresent()) {
            throw new ConflitoDeUnicidadeException("email", "já está em uso",
                    "Já existe conta com este e-mail.");
        }

        // bcrypt custo 12 (ADR-0003 (a)). Esta variável é o hash; o texto puro não é guardado
        // em campo de instância, não é registrado em log e não sai em resposta (RNF009).
        String senhaHash = codificador.encode(requisicao.senha());

        UUID contaId = contas.inserir(email, senhaHash, EstadoConta.PENDENTE_VERIFICACAO);
        perfis.inserir(contaId, requisicao.nomePublico(), slugUnico(requisicao.nomePublico()));

        Conta conta = contas.buscarPorEmail(email).orElseThrow(() -> new IllegalStateException(
                "conta recém-gravada não foi encontrada — a transação do cadastro não se completou"));
        Perfil perfil = perfis.buscarPorConta(contaId).orElseThrow(() -> new IllegalStateException(
                "perfil recém-gravado não foi encontrado — a transação do cadastro não se completou"));

        return new ResultadoDeCadastro(new SessaoAtualResposta(true, conta.estado(),
                new PerfilPublico(perfil.slug(), perfil.nomePublico(), perfil.biografia(),
                        perfil.afiliacaoDeclarada(), perfil.areasInteresse(), perfil.linksExternos())),
                contaId);
    }

    /**
     * Slug livre para o nome público: a base derivada ou, se ela já pertencer a outro perfil,
     * a primeira variante numérica livre ({@code maria-silva-2}, {@code maria-silva-3}, ...).
     *
     * <p>A escolha é feita sobre a lista de slugs já em uso, trazida por <b>uma</b> consulta
     * com o prefixo da base — não é uma consulta por tentativa. A busca é determinística: o
     * mesmo nome gera sempre a primeira variante livre, e não um valor aleatório que
     * ninguém conseguiria prever nem reproduzir.
     */
    private String slugUnico(String nomePublico) {
        String base = GeradorDeSlug.derivar(nomePublico);
        Set<String> emUso = new HashSet<>(perfis.slugsEmUsoComPrefixo(base));

        if (!emUso.contains(base)) {
            return base;
        }
        for (int ordinal = 2; ordinal <= TENTATIVAS_DE_SUFIXO; ordinal++) {
            String candidato = GeradorDeSlug.comSufixo(base, ordinal);
            if (!emUso.contains(candidato)) {
                return candidato;
            }
        }
        throw new IllegalStateException("não foi possível derivar um slug livre para o nome informado");
    }
}
