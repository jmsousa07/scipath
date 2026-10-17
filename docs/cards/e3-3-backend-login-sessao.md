# E3.3 — Backend: login, logout e sessão

- **Perfil:** backend · **Depende de:** E3.2 · **Status:** pronto para execução

## Contexto

O cadastro existe (E3.2); falta a sessão. O ADR-0003 (e) fixa cookie `httpOnly` + `Secure` +
`SameSite=Lax`, sessão **mantida no servidor** (revogável), sem JWT, expiração de 12 h de
inatividade e 7 dias absoluta, escritas protegidas por CSRF, e frontend/API na mesma origem
(proxy `/api` no Next em desenvolvimento). O contrato v0.4 (E3.1) declara onde a sessão é
persistida — **implemente o que o contrato diz**, não escolha de novo.

## Escopo

1. Login pelo endpoint do contrato: credenciais conferidas com bcrypt; credenciais inválidas →
   **401** sem indicar qual campo errou; conta `SUSPENSO` → recusa explícita e registrada.
2. Sessão em cookie com os atributos do ADR-0003, criada no login e **invalidada no logout**
   (inclusive no servidor: o cookie reapresentado depois do logout não autentica).
3. Estado da sessão corrente pelo endpoint do contrato (usado pelo frontend; devolve o perfil
   público, nunca e-mail, hash ou token).
4. **Autorização no servidor** (RNF010): rota privada sem sessão → 401; sessão de outra conta
   tentando operar sobre recurso alheio → 403/404 conforme o contrato — esconder botão no
   frontend não é controle.
5. Expiração aplicada de verdade: teste que provoca a expiração (tempo configurável no perfil de
   teste) e verifica recusa após o prazo.
6. CSRF exigido nas escritas; nenhuma senha, hash ou token em log.
7. Nenhum processo deixado vivo ao terminar; portas 8080/3000 livres.

## Fora de escopo

Verificação de e-mail e recuperação (E3.4), editar perfil (E3.5), telas (E3.6/E3.7),
`spring-boot:run` como prova (proibido em card de implementação — AGENTS.md).

## Critérios de aceite

1. Testes de integração colados: login válido → 200 + `Set-Cookie` com `HttpOnly`, `SameSite=Lax`,
   `Path=/`; senha errada → 401; e-mail inexistente → 401 com o **mesmo** corpo.
2. Teste: cookie reapresentado após logout → 401.
3. Teste: rota privada sem cookie → 401; com cookie de outra conta → 403/404 conforme contrato (CA007).
4. Teste: sessão expirada → 401 com o código de erro do contrato.
5. Teste negativo: nenhum cookie de sessão é aceito como válido por valor forjado/alterado.
6. `make test` verde e saída de log sem senha/hash/token (grepe a evidência e cole o resultado).

## Evidência esperada

Saída dos testes com os `Set-Cookie` colados (só cabeçalho, sem valor de sessão real), o grep de
segredo no log de execução e o `git diff` dos arquivos de configuração de segurança.
Renomeie o arquivo de evidência com o id do card (regra P1).

## Rastreabilidade

RF009, RNF009, RNF010, RNF012, RNF013, CA004, CA007; ADR-0003 (e), (j); DRS §8.2.2, §10.3.2, §15.3.
