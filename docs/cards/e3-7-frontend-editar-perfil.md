# E3.7 — Frontend: editar perfil próprio e área do usuário

- **Perfil:** frontend · **Depende de:** E3.1 · **Status:** pronto para execução

## Contexto

Páginas autenticadas restantes da Etapa 3 (§15.2): **Editar perfil** (alterar informações públicas
próprias) e **Área do usuário** (resumir ações principais e acesso ao que é do próprio usuário).
RF011 e RN011: instituição é autodeclarada, jamais apresentada como verificada.

## Escopo

1. Página **Editar perfil** com todos os campos do RF011: nome público, slug, biografia curta,
   instituição/afiliação (opcional), áreas de interesse (opcional), site (opcional). Campos
   opcionais podem ficar vazios sem quebrar a tela.
2. Página **Área do usuário**: identidade, estado da conta (**verificação de e-mail pendente** vs
   concluída) e o que a conta ainda não pode fazer por falta de verificação — com o caminho para
   reenviar o e-mail (ADR-0003 (h)). Nada de guardar espaço para funcionalidade de etapa futura
   (§21.5: não deixar "pronto" o que não é do MVP); a área aponta para as telas que existem.
3. Feedback de validação do **slug** (formato, já em uso → 409 do contrato) com mensagem associada
   ao campo, e dos demais 422.
4. Texto de instituição sempre com a marca de **autodeclarada** (RN011, CA012) — nenhum selo,
   medalha ou "verificado".
5. Estados obrigatórios do §15.3 (carregando, sucesso, erro de rede, não autorizado, **sessão
   expirada**) e de formulário (campo inválido, envio em andamento, falha recuperável, sucesso,
   tentativa duplicada), de verdade.
6. Nenhum dado privado (e-mail de login, token) renderizado na tela ou embutido no HTML.

## Fora de escopo

Cadastro/login/logout (E3.6), telas de publicação (Etapa 4), listar materiais próprios (Etapa 5),
`screenshot`/navegador como prova neste card (é do card de QA).

## Critérios de aceite

1. `next build` + lint + typecheck verdes, log colado.
2. HTML entregue de cada página com o conteúdo principal e os campos (sem JS).
3. Lista dos estados do §15.3 exercitados, com o que provocou cada um (inclusive sessão expirada e
   409 de slug).
4. Nenhum estilo solto (tokens do E1.6); sem hex/px hardcoded em componente.
5. Teclado: percurso do formulário com foco visível e erro associado ao campo.
6. Verificação na tela de que instituição aparece como autodeclarada.

## Evidência esperada

Log de build/lint/typecheck, a lista de estados com o gatilho de cada um, o HTML entregue e o trecho
que mostra a marca de autodeclarada. Renomeie o arquivo de evidência com o id do card (regra P1).

## Rastreabilidade

RF011, RN010, RN011, RNF001, RNF002, RNF013, CA003, CA009, CA012; ADR-0003 (h), (i);
DRS §8.2.4, §9.0.11, §15.2, §15.3.
