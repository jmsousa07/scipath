package com.scipath.conta;

import com.scipath.autor.PerfilPublico;
import com.scipath.identidade.IdentidadeAutenticada;
import com.scipath.identidade.SessaoAutenticada;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Perfil do próprio usuário — {@code PUT /api/perfil/atual} da tag {@code identidade} do
 * contrato v0.4 (RF011, RN009, RN011).
 *
 * <p><b>A rota é a única superfície de escrita do perfil, e ela não tem identificador.</b> O
 * caminho é {@code /perfil/atual} — "o perfil de quem está falando" — e o corpo é
 * {@link EdicaoPerfilRequisicao}, que não tem campo de conta nem de perfil. Quem edita é o
 * {@code perfilId} da sessão, entregue pelo {@link IdentidadeAutenticada} (ADR-0003 (j),
 * CA007): não existe requisição, bem formada ou não, que faça este endpoint escrever na linha
 * de outra conta.
 *
 * <p><b>O parâmetro exige identidade</b> — é {@code SessaoAutenticada} concreto, e não
 * {@code Optional}. É isso que faz o {@code InterceptorDeIdentidade} responder <b>401</b> a uma
 * escrita sem sessão, antes da checagem de CSRF: numa requisição sem cookie, o contrato publica
 * 401, não 403. Com sessão e sem o cabeçalho {@code X-CSRF-Token}, a resposta é <b>403</b> —
 * as duas recusas saem do tratamento único, não daqui.
 *
 * <p><b>O controller não decide nada:</b> declara a rota, exige corpo válido ({@code @Valid}) e
 * delega ao {@link PerfilService}. O 200 é o status publicado no contrato e o corpo é o
 * {@link PerfilPublico} atualizado — o mesmo schema do perfil público do autor, que continua
 * sendo lido em {@code GET /api/autores/{slug}}, sem rota nova (o reflexo da edição é
 * consequência de a leitura ser do banco, não de um segundo endpoint).
 *
 * <p><b>Erros:</b> 422 {@code validacao_campo} para campo de corpo recusado (slug fora do
 * formato, fora do tamanho, reservado, nome público ausente), 409 {@code conflito_de_unicidade}
 * para slug de outra conta, 400 {@code validacao} para corpo que o servidor não consegue
 * processar como veio — inclusive campo fora do schema, como um {@code perfilId} —, 401 sem
 * sessão, 403 sem CSRF, 415 para {@code Content-Type} não aceito. Nenhuma mensagem repete dado
 * do perfil nem de conta (RNF013).
 */
@RestController
@RequestMapping("/api")
public class PerfilController {

    private final PerfilService perfis;

    public PerfilController(PerfilService perfis) {
        this.perfis = perfis;
    }

    @PutMapping("/perfil/atual")
    public PerfilPublico editar(@IdentidadeAutenticada SessaoAutenticada identidade,
            @Valid @RequestBody EdicaoPerfilRequisicao requisicao) {
        return perfis.editar(identidade.perfilId(), requisicao);
    }
}
