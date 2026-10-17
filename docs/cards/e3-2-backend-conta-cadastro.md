# E3.2 — Backend: modelo de conta/perfil e cadastro

- **Perfil:** backend · **Depende de:** E3.1 · **Status:** pronto para execução

## Contexto

Primeira fatia da identidade: o que existe no banco e o cadastro. O contrato v0.4 (E3.1) é a
fronteira; o **ADR-0003** fixa bcrypt 12, os estados da conta e a unicidade de e-mail e slug.

## Escopo

1. **Migração nova e aditiva** (`V4__conta_perfil.sql`, idempotente/reversível como as anteriores —
   ver README das migrações): conta (identificador de login normalizado e único, hash de senha,
   estado `PENDENTE_VERIFICACAO`/`ATIVO`/`SUSPENSO`, datas, `email_verificado_em`) e perfil
   (nome público, slug **único**, biografia curta, instituição autodeclarada, áreas de interesse,
   site, todos opcionais exceto nome e slug).
2. Cadastro pelo endpoint do contrato v0.4: e-mail validado e **normalizado** (trim + caixa),
   senha com bcrypt custo 12, conta criada em `PENDENTE_VERIFICACAO`, perfil inicial com slug
   derivado do nome e garantido único.
3. E-mail já cadastrado → **409** do contrato; nenhuma mensagem revela dado além do necessário.
4. Senha, hash e token **nunca** aparecem em log nem em resposta.
5. Nada é persistido se a validação falhar: nenhuma conta parcial (transação).

## Fora de escopo

Login/sessão (E3.3), tokens de verificação e recuperação (E3.4), editar perfil (E3.5), telas
(E3.6/E3.7). Não criar verificação de e-mail aqui — só o estado inicial da conta.

## Critérios de aceite

1. Teste de integração: cadastro válido → conta em `PENDENTE_VERIFICACAO` + perfil com slug único.
2. Teste: e-mail duplicado (inclusive com caixa/espaço diferentes) → 409 e **uma** conta no banco.
3. Teste: a coluna de senha no banco **não** contém o texto puro (comparação com o valor enviado) e
   o algoritmo registrado é bcrypt com custo 12.
4. Teste de validação: e-mail inválido e senha curta → 422 no `ProblemDetail` padrão.
5. Teste que assere a **ausência** de e-mail/hash/token nos payloads de resposta.
6. Suíte completa verde (`make test`), executada como a evidência registra.

## Evidência esperada

Saída dos testes colada; `\d conta` e `\d perfil` do banco de teste; a linha de versão da migração
aplicada. Renomeie o arquivo de evidência com o id do card (regra P1).

## Rastreabilidade

RF008, RN009, RNF009, RNF011, RNF012, RNF013, CA004; ADR-0003 (a), (b), (i); DRS §8.2.1, §7.2.
