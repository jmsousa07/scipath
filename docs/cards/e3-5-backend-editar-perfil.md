# E3.5 — Backend: editar perfil próprio

- **Perfil:** backend · **Depende de:** E3.2, E3.3 · **Status:** pronto para execução

## Contexto

RF011: o usuário autenticado edita os **campos públicos** do próprio perfil. RN011: instituição é
autodeclarada e **não** pode aparecer como verificada. O perfil público já é consumido pela Etapa 2
(`/autores/{slug}`, contrato v0.3) — a edição precisa refletir ali, e nada privado pode surgir.

## Escopo

1. Edição pelo endpoint do contrato v0.4: nome público, slug, biografia curta, instituição
   (opcional), áreas de interesse (opcional), site (opcional). Campos opcionais aceitam vazio.
2. **Propriedade vem da sessão**, nunca do payload (ADR-0003 (j)): alterar identificador na
   requisição não pode editar o perfil de outra conta — deve dar 403/404 conforme o contrato.
3. Slug **único e validado** (formato, tamanho, reservados mínimos); colisão → 409 do contrato.
4. Instituição/afiliação exibida como **autodeclarada**, sem selo, marca ou texto que sugira
   verificação (RN010, RN011, CA012).
5. E-mail de login, hash e tokens **nunca** no payload de perfil — nem público nem privado (RNF013).
6. O perfil público da Etapa 2 continua respondendo igual depois da edição (nada de rota nova).

## Fora de escopo

Trocar e-mail ou senha (recuperação é E3.4), exclusão de conta (fora do MVP), telas (E3.7),
alterar as telas/rotas públicas da Etapa 2.

## Critérios de aceite

1. Teste: edição pelo dono → 200 e o perfil público passa a mostrar o valor novo.
2. Teste de autorização (CA007): requisição com identificador de outra conta → recusa, e o perfil
   alheio **não** muda no banco.
3. Teste: slug duplicado → 409; slug inválido → 422.
4. Teste: campos opcionais vazios → 200 e página pública sem "undefined"/vazio quebrado.
5. Teste que assere a **ausência** de e-mail/hash/token na resposta pública e na privada.
6. Teste negativo: requisição fora da interface (sem cookie) → 401.
7. `make test` verde, saída colada.

## Evidência esperada

Saída dos testes com os payloads antes/depois e a consulta ao perfil público comprovando o reflexo.
Renomeie o arquivo de evidência com o id do card (regra P1).

## Rastreabilidade

RF011, RN009, RN010, RN011, RNF010, RNF013, CA003, CA007, CA012; ADR-0003 (i), (j);
DRS §8.2.4, §9.0.9, §9.0.11, §16.
