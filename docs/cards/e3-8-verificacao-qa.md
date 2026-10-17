# E3.8 — Verificação independente da Etapa 3

- **Perfil:** qa · **Depende de:** Integração da Etapa 3 · **Status:** pronto para execução
- Este card **não** escreve código de produto: mede e reporta (§5).

## Objetivo

Verificar identidade e sessão no `main` integrado, com o harness do E1.8 e os **casos negativos** —
que é onde autenticação quebra de verdade.

## Escopo

1. Rodar o harness contra a Etapa 3 e registrar em `docs/qa/e3-identidade-<data>.log`, com veredito
   por critério (atende / não atende / não verificado).
2. **CA004** ponta a ponta: criar conta, autenticar, encerrar sessão; credenciais nunca expostas em
   resposta, HTML, cabeçalho ou log.
3. Negativos obrigatórios de autorização (RNF010, CA007):
   - editar perfil de outra conta manipulando o identificador na requisição → recusa;
   - rota privada sem sessão → 401; com cookie forjado/alterado → 401;
   - cookie reapresentado **depois do logout** → 401; sessão expirada → 401 com o erro do contrato.
4. Anti-enumeração: cadastro com e-mail já existente → 409; recuperação com e-mail existente e
   inexistente → **resposta indistinguível**; reenvio no cooldown → 429.
5. Vazamento de dado privado: varrer respostas e HTML públicos por e-mail, hash de senha, token e
   identificador interno (RNF013). Confirmar no banco que a senha é bcrypt custo 12.
6. Verificação de e-mail: fluxo completo pelo outbox de desenvolvimento; confirmar que o token é de
   uso único, expira e que **nenhum token entra em log**.
7. Acessibilidade e responsividade das telas de cadastro, login e editar perfil: teclado, foco
   visível, 375 px e 1440 px, `prefers-reduced-motion` (RNF002, RNF006–RNF008, CA009).
8. Transparência: sem verificação, a interface explica o que falta e oferece reenvio (ADR-0003 (h));
   instituição aparece como autodeclarada (RN011, CA012).
9. Desempenho percebido (RNF020): tempo das rotas de identidade medido e registrado — número e
   máquina, sem meta inventada.

## Fora de escopo

Corrigir o que falhar (vira card) e verificar publicação (Etapa 4).

## Critérios de aceite

1. Cada item do escopo com veredito explícito.
2. Todo "não atende" reproduzível (comando + saída + esperado).
3. Lista explícita do que **não** foi verificado — nunca "tudo ok" sem lista.

## Evidência esperada

`docs/qa/e3-identidade-<data>.log` + artefatos brutos (respostas HTTP, HTML coletado, conteúdo do
outbox com token mascarado).

## Rastreabilidade

RF008–RF011, RN009, RN011, RNF002, RNF009–RNF013, RNF017, RNF020, CA004, CA007, CA009, CA012;
ADR-0003; DRS §15.3, §17.5, §21.4.
